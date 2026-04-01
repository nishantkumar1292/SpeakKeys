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
    }

    func applicationWillTerminate(_ notification: Notification) {
        hotkeyManager?.unregister()
        AudioCaptureManager.shared.stopCapture()
    }
}
