import Cocoa

/// Inserts transcribed text into the frontmost application.
///
/// Strategy (modeled after WisprFlow/FluidVoice/FreeFlow):
/// 1. For short text (≤200 UTF-16 units): try CGEvent unicode insertion to target PID
/// 2. For native text fields: try AX API insertion with verification
/// 3. Fallback: clipboard + Cmd+V via postToPid (then global post, then AppleScript menu)
/// 4. Clipboard is always saved before and restored after paste
class TextInsertionService {
    static let shared = TextInsertionService()

    /// PID of the app that had focus when recording started.
    /// Must be set by the caller before `insertText` is invoked.
    var targetPID: pid_t?

    private init() {}

    // MARK: - Public

    /// Capture the PID of the currently focused app. Call this when recording starts,
    /// before any overlay or focus change occurs.
    func captureTargetPID() {
        let systemWide = AXUIElementCreateSystemWide()
        var focusedApp: AnyObject?
        let result = AXUIElementCopyAttributeValue(
            systemWide,
            kAXFocusedUIElementAttribute as CFString,
            &focusedApp
        )
        guard result == .success, let element = focusedApp else {
            // Fallback: use the frontmost app's PID
            targetPID = NSWorkspace.shared.frontmostApplication?.processIdentifier
            print("TextInsertionService: captured frontmost PID = \(targetPID ?? -1)")
            return
        }
        var pid: pid_t = 0
        AXUIElementGetPid(element as! AXUIElement, &pid)
        targetPID = pid > 0 ? pid : NSWorkspace.shared.frontmostApplication?.processIdentifier
        print("TextInsertionService: captured target PID = \(targetPID ?? -1)")
    }

    func insertText(_ text: String) {
        guard !text.isEmpty else { return }

        guard AccessibilityHelper.isAccessibilityGranted else {
            // Still copy to clipboard so user can manually Cmd+V
            copyToClipboard(text)
            print("TextInsertionService: accessibility not granted — text copied to clipboard.")
            NotificationCenter.default.post(name: .accessibilityPermissionNeeded, object: nil)
            return
        }

        let pid = targetPID ?? NSWorkspace.shared.frontmostApplication?.processIdentifier

        // Strategy 1: CGEvent unicode insertion (short text, no clipboard needed)
        if insertViaUnicodeEvent(text, pid: pid) {
            return
        }

        // Strategy 2: AX API with verification (native apps only)
        if insertViaAccessibility(text) {
            return
        }

        // Strategy 3: Clipboard + paste
        insertViaPaste(text, pid: pid)
    }

    // MARK: - Strategy 1: CGEvent Unicode Insertion

    private func insertViaUnicodeEvent(_ text: String, pid: pid_t?) -> Bool {
        let utf16 = Array(text.utf16)
        guard utf16.count <= 200, let pid else { return false }

        let source = CGEventSource(stateID: .hidSystemState)
        guard let keyDown = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: true),
              let keyUp = CGEvent(keyboardEventSource: source, virtualKey: 0, keyDown: false) else {
            return false
        }

        keyDown.keyboardSetUnicodeString(stringLength: utf16.count, unicodeString: utf16)
        keyUp.keyboardSetUnicodeString(stringLength: utf16.count, unicodeString: utf16)

        keyDown.postToPid(pid)
        usleep(2_000)
        keyUp.postToPid(pid)

        print("TextInsertionService: inserted via CGEvent unicode to PID \(pid)")
        return true
    }

    // MARK: - Strategy 2: AX API (native apps, with verification)

    private func insertViaAccessibility(_ text: String) -> Bool {
        let systemWide = AXUIElementCreateSystemWide()
        var focusedRef: AnyObject?
        let result = AXUIElementCopyAttributeValue(
            systemWide,
            kAXFocusedUIElementAttribute as CFString,
            &focusedRef
        )
        guard result == .success, let element = focusedRef as! AXUIElement? else {
            return false
        }

        var roleRef: AnyObject?
        AXUIElementCopyAttributeValue(element, kAXRoleAttribute as CFString, &roleRef)
        let role = roleRef as? String ?? ""

        let nativeTextRoles = [kAXTextFieldRole, kAXTextAreaRole, kAXComboBoxRole, "AXSearchField"]
        guard nativeTextRoles.contains(role) else { return false }

        // Read current value before insertion to verify afterward
        var valueBefore: AnyObject?
        AXUIElementCopyAttributeValue(element, kAXValueAttribute as CFString, &valueBefore)
        let textBefore = valueBefore as? String

        let setResult = AXUIElementSetAttributeValue(
            element,
            kAXSelectedTextAttribute as CFString,
            text as CFString
        )
        guard setResult == .success else { return false }

        // Verify the text actually changed (Electron apps return .success but do nothing)
        usleep(10_000) // 10ms for the value to update
        var valueAfter: AnyObject?
        AXUIElementCopyAttributeValue(element, kAXValueAttribute as CFString, &valueAfter)
        let textAfter = valueAfter as? String

        if textAfter != textBefore {
            print("TextInsertionService: inserted via AX API (role: \(role))")
            return true
        }

        print("TextInsertionService: AX API returned success but text unchanged (role: \(role)), falling through to paste")
        return false
    }

    // MARK: - Strategy 3: Clipboard + Paste

    private func insertViaPaste(_ text: String, pid: pid_t?) {
        let pasteboard = NSPasteboard.general
        let snapshot = savePasteboard(pasteboard)
        let changeCountBefore = pasteboard.changeCount

        copyToClipboard(text)
        usleep(50_000) // 50ms for clipboard to settle

        // 3a: postToPid (most reliable for Electron apps)
        if let pid, pasteViaCGEvent(pid: pid) {
            print("TextInsertionService: inserted via Cmd+V postToPid(\(pid))")
            restorePasteboardAfterDelay(snapshot, pasteboard: pasteboard, expectedChangeCount: pasteboard.changeCount)
            return
        }

        // 3b: global CGEvent post
        if pasteViaCGEventGlobal() {
            print("TextInsertionService: inserted via Cmd+V global CGEvent")
            restorePasteboardAfterDelay(snapshot, pasteboard: pasteboard, expectedChangeCount: pasteboard.changeCount)
            return
        }

        // 3c: AppleScript menu-based paste
        pasteViaAppleScriptMenu()
        print("TextInsertionService: inserted via AppleScript Edit>Paste menu")
        restorePasteboardAfterDelay(snapshot, pasteboard: pasteboard, expectedChangeCount: pasteboard.changeCount)
    }

    private func pasteViaCGEvent(pid: pid_t) -> Bool {
        let source = CGEventSource(stateID: .hidSystemState)
        guard let keyDown = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: true),
              let keyUp = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: false) else {
            return false
        }
        keyDown.flags = .maskCommand
        keyUp.flags = .maskCommand

        keyDown.postToPid(pid)
        usleep(10_000)
        keyUp.postToPid(pid)
        return true
    }

    private func pasteViaCGEventGlobal() -> Bool {
        let source = CGEventSource(stateID: .hidSystemState)
        guard let keyDown = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: true),
              let keyUp = CGEvent(keyboardEventSource: source, virtualKey: 9, keyDown: false) else {
            return false
        }
        keyDown.flags = .maskCommand
        keyUp.flags = .maskCommand

        keyDown.post(tap: .cgSessionEventTap)
        usleep(10_000)
        keyUp.post(tap: .cgSessionEventTap)
        return true
    }

    private func pasteViaAppleScriptMenu() {
        let script = NSAppleScript(source: """
            tell application "System Events"
                tell (first process whose frontmost is true)
                    click menu item "Paste" of menu "Edit" of menu bar 1
                end tell
            end tell
        """)
        var error: NSDictionary?
        script?.executeAndReturnError(&error)
        if let error {
            print("TextInsertionService: AppleScript menu paste failed: \(error)")
        }
    }

    // MARK: - Clipboard Helpers

    private func copyToClipboard(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }

    private struct PasteboardSnapshot {
        let items: [[(NSPasteboard.PasteboardType, Data)]]
    }

    private func savePasteboard(_ pasteboard: NSPasteboard) -> PasteboardSnapshot {
        let items = pasteboard.pasteboardItems?.map { item in
            item.types.compactMap { type -> (NSPasteboard.PasteboardType, Data)? in
                guard let data = item.data(forType: type) else { return nil }
                return (type, data)
            }
        } ?? []
        return PasteboardSnapshot(items: items)
    }

    private func restorePasteboardAfterDelay(_ snapshot: PasteboardSnapshot, pasteboard: NSPasteboard, expectedChangeCount: Int) {
        // Restore after a delay to let the paste complete
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // Only restore if clipboard wasn't changed by something else
            guard pasteboard.changeCount == expectedChangeCount else { return }
            pasteboard.clearContents()
            for itemData in snapshot.items {
                let item = NSPasteboardItem()
                for (type, data) in itemData {
                    item.setData(data, forType: type)
                }
                pasteboard.writeObjects([item])
            }
        }
    }
}
