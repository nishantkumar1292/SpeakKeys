import Cocoa

enum AccessibilityHelper {
    /// Check if the app has Accessibility permission, and prompt the user if not.
    @discardableResult
    static func requestAccessibilityPermission() -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue(): true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    static var isAccessibilityGranted: Bool {
        AXIsProcessTrusted()
    }
}
