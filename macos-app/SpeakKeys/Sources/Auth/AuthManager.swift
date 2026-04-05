import Foundation
import AuthenticationServices
import CryptoKit

/// Manages Google Sign-In via OAuth + Firebase Auth REST API.
///
/// Setup:
/// 1. In Firebase Console, add an iOS app with bundle ID "com.speakkeys.macos"
/// 2. Download GoogleService-Info.plist and add it to the Xcode project bundle
@MainActor
class AuthManager: NSObject, ObservableObject {
    static let shared = AuthManager()

    @Published var isSignedIn = false
    @Published var userEmail: String?
    @Published var displayName: String?

    // Configuration loaded from GoogleService-Info.plist
    private let googleClientId: String
    private let firebaseApiKey: String

    private var firebaseIdToken: String?
    private var firebaseRefreshToken: String?
    private var tokenExpirationDate: Date?
    private var codeVerifier: String?
    private var currentAuthSession: ASWebAuthenticationSession?

    private static let maxTokenRetries = 3

    var isConfigured: Bool {
        !googleClientId.isEmpty && !firebaseApiKey.isEmpty
    }

    /// Reversed client ID used as the OAuth callback URL scheme.
    /// For iOS-type OAuth clients, Google expects this format.
    private var callbackScheme: String {
        googleClientId.components(separatedBy: ".").reversed().joined(separator: ".")
    }

    private var redirectUri: String {
        "\(callbackScheme):/oauth2callback"
    }

    // MARK: - Init

    private override init() {
        if let plistPath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
           let plist = NSDictionary(contentsOfFile: plistPath) {
            self.googleClientId = plist["CLIENT_ID"] as? String ?? ""
            self.firebaseApiKey = plist["API_KEY"] as? String ?? ""
        } else {
            self.googleClientId = ""
            self.firebaseApiKey = ""
        }
        super.init()
        loadStoredTokens()
    }

    // MARK: - Sign In

    func signInWithGoogle() async -> Bool {
        guard isConfigured else {
            print("AuthManager: Not configured – add GoogleService-Info.plist to the app bundle")
            return false
        }

        let verifier = Self.generateCodeVerifier()
        self.codeVerifier = verifier
        let challenge = Self.generateCodeChallenge(from: verifier)

        var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: googleClientId),
            URLQueryItem(name: "redirect_uri", value: redirectUri),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]

        guard let authURL = components.url else { return false }

        let callbackURL: URL? = await withCheckedContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: callbackScheme
            ) { url, error in
                if let error {
                    print("AuthManager: Auth session error: \(error.localizedDescription)")
                    continuation.resume(returning: nil)
                    return
                }
                continuation.resume(returning: url)
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.currentAuthSession = session
            session.start()
        }
        self.currentAuthSession = nil

        guard let callbackURL,
              let urlComponents = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
              let code = urlComponents.queryItems?.first(where: { $0.name == "code" })?.value else {
            print("AuthManager: No authorization code in callback")
            return false
        }

        guard let googleIdToken = await exchangeCodeForGoogleIdToken(code: code) else {
            return false
        }

        guard let result = await firebaseSignInWithGoogle(idToken: googleIdToken) else {
            return false
        }

        firebaseIdToken = result.idToken
        firebaseRefreshToken = result.refreshToken
        userEmail = result.email
        displayName = result.displayName
        tokenExpirationDate = Date().addingTimeInterval(TimeInterval(result.expiresIn))
        isSignedIn = true
        saveTokens()
        print("AuthManager: Signed in as \(userEmail ?? "unknown")")
        return true
    }

    // MARK: - Sign Out

    func signOut() {
        firebaseIdToken = nil
        firebaseRefreshToken = nil
        tokenExpirationDate = nil
        userEmail = nil
        displayName = nil
        isSignedIn = false
        clearStoredTokens()
        print("AuthManager: Signed out")
    }

    // MARK: - ID Token

    /// Returns a fresh Firebase ID token, refreshing if expired.
    /// Retries with exponential backoff, matching the Android implementation.
    func getIdToken() async -> String? {
        guard isSignedIn else { return nil }

        if let exp = tokenExpirationDate, Date().addingTimeInterval(300) < exp, let token = firebaseIdToken {
            return token
        }

        for attempt in 0..<Self.maxTokenRetries {
            if let token = await refreshFirebaseToken() {
                return token
            }
            if attempt < Self.maxTokenRetries - 1 {
                let delay: UInt64 = 1_000_000_000 * UInt64(1 << attempt)
                print("AuthManager: Refresh failed (attempt \(attempt + 1)), retrying…")
                try? await Task.sleep(nanoseconds: delay)
            }
        }
        print("AuthManager: All \(Self.maxTokenRetries) token attempts exhausted")
        return nil
    }

    // MARK: - Google Token Exchange (PKCE, no client secret needed for iOS-type client)

    private func exchangeCodeForGoogleIdToken(code: String) async -> String? {
        guard let verifier = codeVerifier else { return nil }

        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = [
            "client_id=\(googleClientId.formEncoded)",
            "code=\(code.formEncoded)",
            "code_verifier=\(verifier.formEncoded)",
            "grant_type=authorization_code",
            "redirect_uri=\(redirectUri.formEncoded)",
        ].joined(separator: "&")
        request.httpBody = body.data(using: .utf8)

        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let idToken = json["id_token"] as? String else {
                print("AuthManager: Google token exchange failed – \(String(data: data, encoding: .utf8) ?? "")")
                return nil
            }
            return idToken
        } catch {
            print("AuthManager: Google token exchange error – \(error)")
            return nil
        }
    }

    // MARK: - Firebase Auth REST API

    private struct FirebaseAuthResult {
        let idToken: String
        let refreshToken: String
        let email: String?
        let displayName: String?
        let expiresIn: Int
    }

    /// Exchange a Google ID token for Firebase Auth tokens via the REST API.
    private func firebaseSignInWithGoogle(idToken googleIdToken: String) async -> FirebaseAuthResult? {
        let url = URL(string: "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=\(firebaseApiKey)")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "postBody": "id_token=\(googleIdToken)&providerId=google.com",
            "requestUri": "http://localhost",
            "returnIdToken": true,
            "returnSecureToken": true,
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let idToken = json["idToken"] as? String,
                  let refreshToken = json["refreshToken"] as? String else {
                print("AuthManager: Firebase sign-in failed – \(String(data: data, encoding: .utf8) ?? "")")
                return nil
            }
            return FirebaseAuthResult(
                idToken: idToken,
                refreshToken: refreshToken,
                email: json["email"] as? String,
                displayName: json["displayName"] as? String,
                expiresIn: Int(json["expiresIn"] as? String ?? "3600") ?? 3600
            )
        } catch {
            print("AuthManager: Firebase sign-in error – \(error)")
            return nil
        }
    }

    /// Refresh the Firebase ID token using the stored refresh token.
    private func refreshFirebaseToken() async -> String? {
        guard let refreshToken = firebaseRefreshToken else { return nil }

        let url = URL(string: "https://securetoken.googleapis.com/v1/token?key=\(firebaseApiKey)")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = "grant_type=refresh_token&refresh_token=\(refreshToken.formEncoded)".data(using: .utf8)

        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let newIdToken = json["id_token"] as? String else {
                print("AuthManager: Token refresh failed – \(String(data: data, encoding: .utf8) ?? "")")
                signOut()
                return nil
            }
            firebaseIdToken = newIdToken
            if let rt = json["refresh_token"] as? String { firebaseRefreshToken = rt }
            let expiresIn = Int(json["expires_in"] as? String ?? "3600") ?? 3600
            tokenExpirationDate = Date().addingTimeInterval(TimeInterval(expiresIn))
            saveTokens()
            return newIdToken
        } catch {
            print("AuthManager: Token refresh error – \(error)")
            return nil
        }
    }

    // MARK: - PKCE

    private static func generateCodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    private static func generateCodeChallenge(from verifier: String) -> String {
        let hash = SHA256.hash(data: Data(verifier.utf8))
        return Data(hash).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    // MARK: - Token Persistence

    private enum Keys {
        static let refreshToken = "firebase_refresh_token"
        static let email = "firebase_user_email"
        static let displayName = "firebase_display_name"
        static let expiration = "firebase_token_expiration"
    }

    private func saveTokens() {
        let d = UserDefaults.standard
        d.set(firebaseRefreshToken, forKey: Keys.refreshToken)
        d.set(userEmail, forKey: Keys.email)
        d.set(displayName, forKey: Keys.displayName)
        if let exp = tokenExpirationDate { d.set(exp.timeIntervalSince1970, forKey: Keys.expiration) }
    }

    private func loadStoredTokens() {
        let d = UserDefaults.standard
        guard let rt = d.string(forKey: Keys.refreshToken) else { return }
        firebaseRefreshToken = rt
        userEmail = d.string(forKey: Keys.email)
        displayName = d.string(forKey: Keys.displayName)
        isSignedIn = true
        let exp = d.double(forKey: Keys.expiration)
        if exp > 0 { tokenExpirationDate = Date(timeIntervalSince1970: exp) }
    }

    private func clearStoredTokens() {
        let d = UserDefaults.standard
        d.removeObject(forKey: Keys.refreshToken)
        d.removeObject(forKey: Keys.email)
        d.removeObject(forKey: Keys.displayName)
        d.removeObject(forKey: Keys.expiration)
    }
}

// MARK: - ASWebAuthenticationPresentationContextProviding

extension AuthManager: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        NSApp.keyWindow ?? ASPresentationAnchor()
    }
}

// MARK: - String Helpers

private extension String {
    /// Percent-encodes for application/x-www-form-urlencoded bodies (RFC 3986 unreserved chars).
    var formEncoded: String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return addingPercentEncoding(withAllowedCharacters: allowed) ?? self
    }
}
