import SwiftUI

struct MenuBarView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Status header
            statusHeader

            Divider()

            // Last transcription
            if !appState.lastTranscription.isEmpty {
                Text(appState.lastTranscription)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(3)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)

                Divider()
            }

            // Actions
            Button(action: { appState.toggleRecording() }) {
                Label(
                    appState.recordingState.isRecording ? "Stop Recording" : "Start Recording",
                    systemImage: appState.recordingState.isRecording ? "stop.circle" : "record.circle"
                )
            }
            .keyboardShortcut("r", modifiers: .command)

            Divider()

            if #available(macOS 14.0, *) {
                SettingsLink {
                    Label("Settings...", systemImage: "gear")
                }
                .keyboardShortcut(",", modifiers: .command)
            } else {
                Button("Settings...") {
                    NSApp.sendAction(Selector(("showSettingsWindow:")), to: nil, from: nil)
                }
                .keyboardShortcut(",", modifiers: .command)
            }

            Divider()

            Button("Quit SpeakKeys") {
                NSApplication.shared.terminate(nil)
            }
            .keyboardShortcut("q", modifiers: .command)
        }
        .padding(4)
    }

    @ViewBuilder
    private var statusHeader: some View {
        HStack {
            Circle()
                .fill(statusColor)
                .frame(width: 8, height: 8)
            Text(statusText)
                .font(.headline)
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    private var statusColor: Color {
        switch appState.recordingState {
        case .idle: return .gray
        case .recording: return .red
        case .processing: return .orange
        case .error: return .red
        }
    }

    private var statusText: String {
        switch appState.recordingState {
        case .idle: return "Ready"
        case .recording: return "Recording..."
        case .processing: return "Processing..."
        case .error(let msg): return "Error: \(msg)"
        }
    }
}
