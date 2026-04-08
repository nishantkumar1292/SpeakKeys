import SwiftUI

// MARK: - Navigation

enum AppSection: String, CaseIterable, Identifiable {
    case engine = "Engine"
    case general = "General"
    case account = "Account"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .engine: return "waveform"
        case .general: return "gear"
        case .account: return "person.circle"
        }
    }
}

// MARK: - Main View

struct SettingsView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var settings = SettingsManager.shared
    @State private var selectedSection: AppSection? = .engine

    var body: some View {
        NavigationSplitView {
            List(selection: $selectedSection) {
                ForEach(AppSection.allCases) { section in
                    Label(section.rawValue, systemImage: section.icon)
                        .tag(section)
                }
            }
            .navigationSplitViewColumnWidth(min: 160, ideal: 180, max: 220)
            .listStyle(.sidebar)
        } detail: {
            switch selectedSection ?? .engine {
            case .engine:
                EngineSelectionView(settings: settings)
            case .general:
                GeneralSettingsView(settings: settings)
            case .account:
                AccountSettingsView()
                    .environmentObject(appState)
            }
        }
        .frame(minWidth: 700, minHeight: 480)
    }
}

// MARK: - Engine Selection

struct EngineSelectionView: View {
    @ObservedObject var settings: SettingsManager
    @ObservedObject private var authManager = AuthManager.shared
    @State private var expandedEngine: RecognitionEngine?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Recognition Engine")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("Choose your speech recognition provider")
                    .foregroundColor(.secondary)

                Spacer().frame(height: 4)

                Text("AVAILABLE ENGINES")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.secondary)
                    .tracking(1)

                // SpeakKeys Auto
                EngineCard(
                    name: "SpeakKeys Auto",
                    badge: "HOSTED",
                    badgeColor: .blue,
                    icon: "sparkles",
                    isSelected: settings.selectedEngine == .proxied,
                    isAvailable: authManager.isSignedIn,
                    isExpanded: expandedEngine == .proxied,
                    unavailableText: "Requires sign-in",
                    onSelect: {
                        if authManager.isSignedIn {
                            settings.selectedEngine = .proxied
                        }
                    },
                    onToggleExpand: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            expandedEngine = expandedEngine == .proxied ? nil : .proxied
                        }
                    }
                ) {
                    SettingPickerRow(
                        label: "Provider",
                        selection: Binding(
                            get: { settings.proxiedProvider },
                            set: { settings.proxiedProvider = $0 }
                        ),
                        options: [("sarvam", "Sarvam AI"), ("whisper", "OpenAI Whisper")]
                    )
                    SettingTextRow(
                        label: "Endpoint",
                        text: Binding(
                            get: { settings.proxiedEndpoint },
                            set: { settings.proxiedEndpoint = $0 }
                        ),
                        placeholder: "https://..."
                    )
                }

                // Sarvam AI
                EngineCard(
                    name: "Sarvam AI",
                    badge: "BYOK",
                    badgeColor: .purple,
                    icon: "character.textbox",
                    isSelected: settings.selectedEngine == .sarvam,
                    isAvailable: !settings.sarvamApiKey.isEmpty,
                    isExpanded: expandedEngine == .sarvam,
                    unavailableText: "Requires API key",
                    onSelect: {
                        if !settings.sarvamApiKey.isEmpty {
                            settings.selectedEngine = .sarvam
                        } else {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                expandedEngine = .sarvam
                            }
                        }
                    },
                    onToggleExpand: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            expandedEngine = expandedEngine == .sarvam ? nil : .sarvam
                        }
                    }
                ) {
                    SettingSecureRow(
                        label: "API Key",
                        text: Binding(
                            get: { settings.sarvamApiKey },
                            set: { settings.sarvamApiKey = $0 }
                        ),
                        placeholder: "Enter API key"
                    )
                    SettingTextRow(
                        label: "Language",
                        text: Binding(
                            get: { settings.sarvamLanguage },
                            set: { settings.sarvamLanguage = $0 }
                        ),
                        placeholder: "hi-IN"
                    )
                    SettingTextRow(
                        label: "Mode",
                        text: Binding(
                            get: { settings.sarvamMode },
                            set: { settings.sarvamMode = $0 }
                        ),
                        placeholder: "saarika:v2"
                    )
                }

                // OpenAI Whisper
                EngineCard(
                    name: "OpenAI Whisper",
                    badge: "BYOK",
                    badgeColor: .purple,
                    icon: "waveform",
                    isSelected: settings.selectedEngine == .whisper,
                    isAvailable: !settings.openaiApiKey.isEmpty,
                    isExpanded: expandedEngine == .whisper,
                    unavailableText: "Requires API key",
                    onSelect: {
                        if !settings.openaiApiKey.isEmpty {
                            settings.selectedEngine = .whisper
                        } else {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                expandedEngine = .whisper
                            }
                        }
                    },
                    onToggleExpand: {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            expandedEngine = expandedEngine == .whisper ? nil : .whisper
                        }
                    }
                ) {
                    SettingSecureRow(
                        label: "API Key",
                        text: Binding(
                            get: { settings.openaiApiKey },
                            set: { settings.openaiApiKey = $0 }
                        ),
                        placeholder: "Enter API key"
                    )
                    SettingTextRow(
                        label: "Language",
                        text: Binding(
                            get: { settings.whisperLanguage },
                            set: { settings.whisperLanguage = $0 }
                        ),
                        placeholder: "en"
                    )
                    SettingTextRow(
                        label: "Prompt",
                        text: Binding(
                            get: { settings.whisperPrompt },
                            set: { settings.whisperPrompt = $0 }
                        ),
                        placeholder: "Optional prompt"
                    )
                }
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - Engine Card

struct EngineCard<Settings: View>: View {
    let name: String
    let badge: String
    let badgeColor: Color
    let icon: String
    let isSelected: Bool
    let isAvailable: Bool
    let isExpanded: Bool
    let unavailableText: String
    let onSelect: () -> Void
    let onToggleExpand: () -> Void
    @ViewBuilder let settingsContent: () -> Settings

    var body: some View {
        VStack(spacing: 0) {
            // Main card row
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(isAvailable ? .accentColor : .gray)
                    .frame(width: 32, height: 32)

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(name)
                            .fontWeight(.semibold)

                        Text(badge)
                            .font(.system(size: 10, weight: .semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(badgeColor.opacity(0.15))
                            .foregroundColor(badgeColor)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }

                    if !isAvailable {
                        Text(unavailableText)
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.accentColor)
                        .font(.title3)
                }

                Button(action: onToggleExpand) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundColor(.secondary)
                        .frame(width: 24, height: 24)
                }
                .buttonStyle(.plain)
            }
            .padding(14)
            .contentShape(Rectangle())
            .onTapGesture(perform: onSelect)

            // Expandable settings panel
            if isExpanded {
                Divider()
                    .padding(.horizontal, 14)

                VStack(alignment: .leading, spacing: 10) {
                    settingsContent()
                }
                .padding(14)
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(nsColor: .controlBackgroundColor))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(isSelected ? Color.accentColor : Color.gray.opacity(0.2), lineWidth: isSelected ? 2 : 1)
        )
        .opacity(isAvailable ? 1.0 : 0.7)
    }
}

// MARK: - Setting Rows

struct SettingTextRow: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            TextField(placeholder, text: $text)
                .textFieldStyle(.roundedBorder)
        }
    }
}

struct SettingSecureRow: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            SecureField(placeholder, text: $text)
                .textFieldStyle(.roundedBorder)
        }
    }
}

struct SettingPickerRow: View {
    let label: String
    @Binding var selection: String
    let options: [(String, String)]

    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            Picker("", selection: $selection) {
                ForEach(options, id: \.0) { option in
                    Text(option.1).tag(option.0)
                }
            }
            .labelsHidden()
        }
    }
}

// MARK: - General Settings

struct GeneralSettingsView: View {
    @ObservedObject var settings: SettingsManager

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("General")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("Hotkey and text insertion settings")
                    .foregroundColor(.secondary)

                GroupBox("Hotkey") {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Shortcut")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("Option + Space")
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.gray.opacity(0.15))
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                        Text("Press the hotkey to start/stop recording")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(4)
                }

                GroupBox("Text Insertion") {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("Auto-insert transcribed text", isOn: $settings.autoInsertText)
                        Text("Inserts text at cursor position via clipboard paste")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(4)
                }
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - Account Settings

struct AccountSettingsView: View {
    @EnvironmentObject var appState: AppState
    @ObservedObject private var authManager = AuthManager.shared
    @State private var isSigningIn = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Account")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("Manage your SpeakKeys account")
                    .foregroundColor(.secondary)

                GroupBox {
                    VStack(spacing: 16) {
                        if authManager.isSignedIn {
                            signedInContent
                        } else if !authManager.isConfigured {
                            notConfiguredContent
                        } else {
                            signInContent
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(8)
                }
            }
            .padding(24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    @ViewBuilder
    private var signedInContent: some View {
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
    }

    @ViewBuilder
    private var notConfiguredContent: some View {
        Image(systemName: "exclamationmark.triangle")
            .font(.system(size: 48))
            .foregroundColor(.orange)

        Text("Google Sign-In not configured")
            .font(.headline)

        Text("Add an iOS app in Firebase Console for\n\"com.speakkeys.macos\" and place the\nGoogleService-Info.plist in the app bundle.")
            .foregroundColor(.secondary)
            .font(.caption)
            .multilineTextAlignment(.center)
    }

    @ViewBuilder
    private var signInContent: some View {
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
            Text("Signing in\u{2026}")
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
