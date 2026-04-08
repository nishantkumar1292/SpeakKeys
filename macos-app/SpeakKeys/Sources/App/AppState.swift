import Foundation
import Combine
import UserNotifications

enum RecordingState: Equatable {
    case idle
    case recording
    case processing
    case error(String)

    var isRecording: Bool {
        if case .recording = self { return true }
        return false
    }
}

@MainActor
class AppState: ObservableObject {
    static let shared = AppState()

    @Published var recordingState: RecordingState = .idle
    @Published var lastTranscription: String = ""
    @Published var isSignedIn: Bool = false
    @Published var userEmail: String?

    var menuBarIconName: String {
        switch recordingState {
        case .idle:
            return "mic"
        case .recording:
            return "mic.fill"
        case .processing:
            return "ellipsis.circle"
        case .error:
            return "exclamationmark.triangle"
        }
    }

    private let audioCaptureManager = AudioCaptureManager.shared
    private let recognitionManager = RecognitionManager.shared
    private let textInsertionService = TextInsertionService.shared

    private init() {
        setupRecognitionCallbacks()
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    func toggleRecording() {
        switch recordingState {
        case .idle, .error:
            startRecording()
        case .recording:
            stopRecording()
        case .processing:
            break
        }
    }

    func startRecording() {
        // Capture which app has focus BEFORE we start (so we can paste back to it)
        textInsertionService.captureTargetPID()

        recognitionManager.reset()
        audioCaptureManager.startCapture { [weak self] samples, count in
            self?.recognitionManager.feedAudio(samples: samples, count: count)
        }
        recordingState = .recording
    }

    func stopRecording() {
        audioCaptureManager.stopCapture()
        recordingState = .processing
        recognitionManager.finalize()
    }

    private func showCopiedNotification(_ text: String) {
        let content = UNMutableNotificationContent()
        content.title = "Copied to clipboard"
        content.body = String(text.prefix(100))
        content.sound = .default
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    private func setupRecognitionCallbacks() {
        recognitionManager.onResult = { [weak self] text in
            Task { @MainActor in
                guard let self else { return }
                self.lastTranscription = text
                self.recordingState = .idle
                self.textInsertionService.insertText(text)
                self.showCopiedNotification(text)
            }
        }

        recognitionManager.onError = { [weak self] error in
            Task { @MainActor in
                self?.recordingState = .error(error)
            }
        }

        recognitionManager.onPartialResult = { [weak self] text in
            Task { @MainActor in
                self?.lastTranscription = text
            }
        }
    }
}
