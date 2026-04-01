import Foundation

enum RecognitionEngine: String, CaseIterable, Identifiable {
    case whisper = "whisper"
    case sarvam = "sarvam"
    case proxied = "proxied"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .whisper: return "OpenAI Whisper"
        case .sarvam: return "Sarvam AI"
        case .proxied: return "SpeakKeys Cloud"
        }
    }
}

/// UserDefaults-backed settings, mirrors PreferencesRepository from the KMP shared module.
class SettingsManager: ObservableObject {
    static let shared = SettingsManager()

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let selectedEngine = "selectedEngine"
        static let openaiApiKey = "openaiApiKey"
        static let whisperLanguage = "whisperLanguage"
        static let whisperPrompt = "whisperPrompt"
        static let sarvamApiKey = "sarvamApiKey"
        static let sarvamLanguage = "sarvamLanguage"
        static let sarvamMode = "sarvamMode"
        static let proxiedEndpoint = "proxiedEndpoint"
        static let proxiedProvider = "proxiedProvider"
        static let hotkeyModifier = "hotkeyModifier"
        static let hotkeyKeyCode = "hotkeyKeyCode"
        static let autoInsertText = "autoInsertText"
    }

    private init() {
        defaults.register(defaults: [
            Keys.selectedEngine: RecognitionEngine.whisper.rawValue,
            Keys.whisperLanguage: "en",
            Keys.sarvamLanguage: "hi-IN",
            Keys.sarvamMode: "saarika:v2",
            Keys.proxiedEndpoint: "https://asia-south1-speakkeys.cloudfunctions.net/transcribe",
            Keys.proxiedProvider: "sarvam",
            Keys.hotkeyKeyCode: 49, // Space
            Keys.autoInsertText: true,
        ])
    }

    var selectedEngine: RecognitionEngine {
        get { RecognitionEngine(rawValue: defaults.string(forKey: Keys.selectedEngine) ?? "whisper") ?? .whisper }
        set { defaults.set(newValue.rawValue, forKey: Keys.selectedEngine); objectWillChange.send() }
    }

    var openaiApiKey: String {
        get { defaults.string(forKey: Keys.openaiApiKey) ?? "" }
        set { defaults.set(newValue, forKey: Keys.openaiApiKey) }
    }

    var whisperLanguage: String {
        get { defaults.string(forKey: Keys.whisperLanguage) ?? "en" }
        set { defaults.set(newValue, forKey: Keys.whisperLanguage) }
    }

    var whisperPrompt: String {
        get { defaults.string(forKey: Keys.whisperPrompt) ?? "" }
        set { defaults.set(newValue, forKey: Keys.whisperPrompt) }
    }

    var sarvamApiKey: String {
        get { defaults.string(forKey: Keys.sarvamApiKey) ?? "" }
        set { defaults.set(newValue, forKey: Keys.sarvamApiKey) }
    }

    var sarvamLanguage: String {
        get { defaults.string(forKey: Keys.sarvamLanguage) ?? "hi-IN" }
        set { defaults.set(newValue, forKey: Keys.sarvamLanguage) }
    }

    var sarvamMode: String {
        get { defaults.string(forKey: Keys.sarvamMode) ?? "saarika:v2" }
        set { defaults.set(newValue, forKey: Keys.sarvamMode) }
    }

    var proxiedEndpoint: String {
        get { defaults.string(forKey: Keys.proxiedEndpoint) ?? "" }
        set { defaults.set(newValue, forKey: Keys.proxiedEndpoint) }
    }

    var proxiedProvider: String {
        get { defaults.string(forKey: Keys.proxiedProvider) ?? "sarvam" }
        set { defaults.set(newValue, forKey: Keys.proxiedProvider); objectWillChange.send() }
    }

    var autoInsertText: Bool {
        get { defaults.bool(forKey: Keys.autoInsertText) }
        set { defaults.set(newValue, forKey: Keys.autoInsertText) }
    }
}
