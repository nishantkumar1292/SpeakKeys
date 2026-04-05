import Cocoa
import Carbon.HIToolbox

/// Push-to-talk: hold Option to record, release to stop and insert text.
class GlobalHotkeyManager {
    static let shared = GlobalHotkeyManager()

    private var flagsMonitor: Any?
    private var localFlagsMonitor: Any?
    private var isOptionHeld = false

    private init() {}

    func register() {
        // Global monitor (when app is not focused)
        flagsMonitor = NSEvent.addGlobalMonitorForEvents(matching: .flagsChanged) { [weak self] event in
            self?.handleFlagsChanged(event)
        }

        // Local monitor (when app is focused)
        localFlagsMonitor = NSEvent.addLocalMonitorForEvents(matching: .flagsChanged) { [weak self] event in
            self?.handleFlagsChanged(event)
            return event
        }
    }

    func unregister() {
        if let flagsMonitor {
            NSEvent.removeMonitor(flagsMonitor)
            self.flagsMonitor = nil
        }
        if let localFlagsMonitor {
            NSEvent.removeMonitor(localFlagsMonitor)
            self.localFlagsMonitor = nil
        }
    }

    private func handleFlagsChanged(_ event: NSEvent) {
        let optionPressed = event.modifierFlags.contains(.option)

        if optionPressed && !isOptionHeld {
            // Option key pressed down → start recording
            isOptionHeld = true
            Task { @MainActor in
                let state = AppState.shared.recordingState
                if !state.isRecording {
                    AppState.shared.startRecording()
                }
            }
        } else if !optionPressed && isOptionHeld {
            // Option key released → stop recording
            isOptionHeld = false
            Task { @MainActor in
                if AppState.shared.recordingState.isRecording {
                    AppState.shared.stopRecording()
                }
            }
        }
    }
}
