import Cocoa

class AppDelegate: NSObject, NSApplicationDelegate {
    private var hotkeyManager: GlobalHotkeyManager?

    func applicationDidFinishLaunching(_ notification: Notification) {
        hotkeyManager = GlobalHotkeyManager.shared
        hotkeyManager?.register()

        // Request accessibility permission if not granted — this prompts for
        // the exact binary that is running (important for Xcode debug builds)
        let trusted = AccessibilityHelper.requestAccessibilityPermission()
        print("AppDelegate: Accessibility trusted = \(trusted), path = \(Bundle.main.bundlePath)")

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAccessibilityNeeded),
            name: .accessibilityPermissionNeeded,
            object: nil
        )
    }

    @objc private func handleAccessibilityNeeded() {
        // Re-prompt — this shows the system dialog pointing to the current binary
        AccessibilityHelper.requestAccessibilityPermission()
    }

    func applicationWillTerminate(_ notification: Notification) {
        hotkeyManager?.unregister()
        AudioCaptureManager.shared.stopCapture()
    }
}
