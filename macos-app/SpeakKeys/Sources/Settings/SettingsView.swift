import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var settings = SettingsManager.shared
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            GeneralSettingsTab(settings: settings)
                .tabItem { Label("General", systemImage: "gear") }
                .tag(0)

            EngineSettingsTab(settings: settings)
                .tabItem { Label("Engine", systemImage: "waveform") }
                .tag(1)

            AccountSettingsTab()
                .environmentObject(appState)
                .tabItem { Label("Account", systemImage: "person.circle") }
                .tag(2)
        }
        .frame(width: 480, height: 360)
        .padding()
    }
}

// MARK: - General Settings

struct GeneralSettingsTab: View {
    @ObservedObject var settings: SettingsManager

    var body: some View {
        Form {
            Section("Recognition Engine") {
                Picker("Engine", selection: $settings.selectedEngine) {
                    ForEach(RecognitionEngine.allCases) { engine in
                        Text(engine.displayName).tag(engine)
                    }
                }
                .pickerStyle(.radioGroup)
            }

            Section("Hotkey") {
                Text("Option + Space")
                    .foregroundColor(.secondary)
                Text("Press the hotkey to start/stop recording")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Section("Text Insertion") {
                Toggle("Auto-insert transcribed text", isOn: $settings.autoInsertText)
                Text("Inserts text at cursor position via clipboard paste")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
    }
}

// MARK: - Engine Settings

struct EngineSettingsTab: View {
    @ObservedObject var settings: SettingsManager

    var body: some View {
        Form {
            Section("OpenAI Whisper") {
                SecureField("API Key", text: Binding(
                    get: { settings.openaiApiKey },
                    set: { settings.openaiApiKey = $0 }
                ))
                TextField("Language Code", text: Binding(
                    get: { settings.whisperLanguage },
                    set: { settings.whisperLanguage = $0 }
                ))
                TextField("Prompt (optional)", text: Binding(
                    get: { settings.whisperPrompt },
                    set: { settings.whisperPrompt = $0 }
                ))
            }

            Section("Sarvam AI") {
                SecureField("API Key", text: Binding(
                    get: { settings.sarvamApiKey },
                    set: { settings.sarvamApiKey = $0 }
                ))
                TextField("Language", text: Binding(
                    get: { settings.sarvamLanguage },
                    set: { settings.sarvamLanguage = $0 }
                ))
                TextField("Mode", text: Binding(
                    get: { settings.sarvamMode },
                    set: { settings.sarvamMode = $0 }
                ))
            }

            Section("SpeakKeys Cloud (Proxied)") {
                Picker("Provider", selection: Binding(
                    get: { settings.proxiedProvider },
                    set: { settings.proxiedProvider = $0 }
                )) {
                    Text("Sarvam AI").tag("sarvam")
                    Text("OpenAI Whisper").tag("whisper")
                }
                .pickerStyle(.radioGroup)
                TextField("Endpoint URL", text: Binding(
                    get: { settings.proxiedEndpoint },
                    set: { settings.proxiedEndpoint = $0 }
                ))
                Text("Requires sign-in. Uses your subscription.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
    }
}

// MARK: - Account Settings

struct AccountSettingsTab: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject private var authManager = AuthManager.shared
    @State private var isSigningIn = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 16) {
            if authManager.isSignedIn {
                Image(systemName: "person.crop.circle.fill")
                    .font(.system(size: 48))
                    .foregroundColor(.accentColor)

                if let name = authManager.displayName {
                    Text(name).font(.headline)
                }

                if let email = authManager.userEmail {
                    Text(email).foregroundColor(.secondary)
                }

                Button("Sign Out") {
                    authManager.signOut()
                    appState.isSignedIn = false
                    appState.userEmail = nil
                }
            } else if !authManager.isConfigured {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 48))
                    .foregroundColor(.orange)

                Text("Google Sign-In not configured")
                    .font(.headline)

                Text("Add an iOS app in Firebase Console for\n\"com.speakkeys.macos\" and place the\nGoogleService-Info.plist in the app bundle.")
                    .foregroundColor(.secondary)
                    .font(.caption)
                    .multilineTextAlignment(.center)
            } else {
                Image(systemName: "person.crop.circle")
                    .font(.system(size: 48))
                    .foregroundColor(.secondary)

                Text("Not signed in")
                    .font(.headline)

                Text("Sign in to use SpeakKeys Cloud recognition")
                    .foregroundColor(.secondary)
                    .font(.caption)

                if isSigningIn {
                    ProgressView()
                        .controlSize(.small)
                    Text("Signing in…")
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    Button("Sign in with Google") {
                        Task {
                            isSigningIn = true
                            errorMessage = nil
                            let success = await authManager.signInWithGoogle()
                            isSigningIn = false
                            if success {
                                appState.isSignedIn = true
                                appState.userEmail = authManager.userEmail
                            } else {
                                errorMessage = "Sign-in failed. Please try again."
                            }
                        }
                    }
                    .controlSize(.large)
                }

                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }
}
