import SwiftUI

@main
struct SpeakKeysApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var appState = AppState.shared

    var body: some Scene {
        MenuBarExtra {
            MenuBarView()
                .environmentObject(appState)
        } label: {
            Image(systemName: appState.menuBarIconName)
        }

        Settings {
            SettingsView()
                .environmentObject(appState)
        }
    }
}
