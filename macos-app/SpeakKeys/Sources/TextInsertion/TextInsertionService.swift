import Cocoa

/// Inserts transcribed text into the frontmost application.
/// 1. Tries AX API (works for native macOS apps like Notes, Xcode, TextEdit)
/// 2. Falls back to clipboard + CGEvent Cmd+V (works for Electron apps like Slack)
/// 3. Text is always left on clipboard as ultimate fallback
class TextInsertionService {
    static let shared = TextInsertionService()

    private init() {}

    func insertText(_ text: String) {
        guard !text.isEmpty else { return }

        // Always put text on clipboard (serves as fallback for manual Cmd+V)
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        // Try AX API first (native apps)
        if insertViaAccessibility(text) {
            return
        }

        // Fall back to simulated Cmd+V (Electron apps, etc.)
        simulatePaste()
    }

    private func insertViaAccessibility(_ text: String) -> Bool {
        let systemWide = AXUIElementCreateSystemWide()
        var focusedElement: AnyObject?
        let result = AXUIElementCopyAttributeValue(
            systemWide,
            kAXFocusedUIElementAttribute as CFString,
            &focusedElement
        )
        guard result == .success, let element = focusedElement else {
            return false
        }

        // Check if the element actually supports text insertion
        // by verifying it has the kAXValueAttribute (text fields/areas do)
        var role: AnyObject?
        AXUIElementCopyAttributeValue(element as! AXUIElement, kAXRoleAttribute as CFString, &role)
        let roleStr = role as? String ?? ""

        // Only use AX for known native text roles
        let nativeTextRoles = [
            kAXTextFieldRole, kAXTextAreaRole, kAXComboBoxRole,
            "AXSearchField"
        ]
        guard nativeTextRoles.contains(roleStr) else {
            return false
        }

        let setResult = AXUIElementSetAttributeValue(
            element as! AXUIElement,
            kAXSelectedTextAttribute as CFString,
            text as CFString
        )
        if setResult == .success {
            print("TextInsertionService: inserted via AX API (role: \(roleStr))")
            return true
        }
        return false
    }

    private func simulatePaste() {
        usleep(50_000) // 50ms for clipboard to settle

        let source = CGEventSource(stateID: .privateState)
        let keyDown = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: true)
        keyDown?.flags = .maskCommand
        let keyUp = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: false)
        keyUp?.flags = .maskCommand

        keyDown?.post(tap: .cgSessionEventTap)
        usleep(10_000)
        keyUp?.post(tap: .cgSessionEventTap)
        print("TextInsertionService: inserted via Cmd+V paste")
    }
}
