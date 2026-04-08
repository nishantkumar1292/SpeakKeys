import SwiftUI

@main
struct SpeakKeysApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState.shared

    private var menuBarImage: NSImage {
        if let icon = NSImage(named: "MenuBarIcon") {
            icon.isTemplate = true
            return icon
        }
        return NSImage(systemSymbolName: "mic", accessibilityDescription: "SpeakKeys")!
    }

    var body: some Scene {
        MenuBarExtra {
            MenuBarView()
                .environmentObject(appState)
        } label: {
            Image(nsImage: menuBarImage)
        }

        Settings {
            SettingsView()
                .environmentObject(appState)
        }
    }
}
