import Foundation

/// Manages speech recognition using the KMP shared module's recognizer interfaces.
/// This is a native Swift wrapper that mirrors the KMP Recognizer behavior
/// for cloud-based speech recognition (Whisper, Sarvam, Proxied).
class RecognitionManager {
    static let shared = RecognitionManager()

    var onResult: ((String) -> Void)?
    var onPartialResult: ((String) -> Void)?
    var onError: ((String) -> Void)?

    private let settings = SettingsManager.shared
    private let sampleRate: Float = 16000
    private let maxDuration: Float = 30 // seconds
    private var audioBuffer: [Int16] = []
    private let bufferQueue = DispatchQueue(label: "com.speakkeys.recognition.buffer")

    private init() {}

    func reset() {
        bufferQueue.sync {
            audioBuffer.removeAll()
        }
    }

    func feedAudio(samples: [Int16], count: Int) {
        bufferQueue.sync {
            let maxSamples = Int(maxDuration * sampleRate)
            let remaining = maxSamples - audioBuffer.count
            if remaining > 0 {
                let toAdd = min(count, remaining)
                audioBuffer.append(contentsOf: samples.prefix(toAdd))
            }
        }
    }

    func finalize() {
        let samples: [Int16] = bufferQueue.sync {
            let copy = audioBuffer
            audioBuffer.removeAll()
            return copy
        }

        guard !samples.isEmpty else {
            onResult?("")
            return
        }

        Task {
            await transcribe(samples: samples)
        }
    }

    private func transcribe(samples: [Int16]) async {
        let engine = settings.selectedEngine
        let wavData = WavEncoder.encode(samples: samples, sampleRate: Int(sampleRate))

        do {
            let text: String
            switch engine {
            case .whisper:
                text = try await transcribeWhisper(wavData: wavData)
            case .sarvam:
                text = try await transcribeSarvam(wavData: wavData)
            case .proxied:
                text = try await transcribeProxied(wavData: wavData)
            }
            print("RecognitionManager: transcription result: '\(text)'")
            onResult?(text)
        } catch {
            print("RecognitionManager: transcription error: \(error)")
            onError?(error.localizedDescription)
        }
    }

    private func transcribeWhisper(wavData: Data) async throws -> String {
        let apiKey = settings.openaiApiKey
        guard !apiKey.isEmpty else {
            throw RecognitionError.noApiKey("OpenAI API key not configured")
        }

        let url = URL(string: "https://api.openai.com/v1/audio/transcriptions")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")

        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.appendMultipart(boundary: boundary, name: "file", filename: "audio.wav", mimeType: "audio/wav", data: wavData)
        body.appendMultipart(boundary: boundary, name: "model", value: "whisper-1")

        let lang = settings.whisperLanguage
        if !lang.isEmpty && lang != "und" {
            body.appendMultipart(boundary: boundary, name: "language", value: lang)
        }

        let prompt = settings.whisperPrompt
        if !prompt.isEmpty {
            body.appendMultipart(boundary: boundary, name: "prompt", value: prompt)
        }

        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw RecognitionError.apiError("Whisper API error")
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return (json?["text"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
    }

    private func transcribeSarvam(wavData: Data) async throws -> String {
        let apiKey = settings.sarvamApiKey
        guard !apiKey.isEmpty else {
            throw RecognitionError.noApiKey("Sarvam API key not configured")
        }

        let url = URL(string: "https://api.sarvam.ai/speech-to-text-translate")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "api-subscription-key")

        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.appendMultipart(boundary: boundary, name: "file", filename: "audio.wav", mimeType: "audio/wav", data: wavData)

        let lang = settings.sarvamLanguage
        if !lang.isEmpty {
            body.appendMultipart(boundary: boundary, name: "language_code", value: lang)
        }

        let mode = settings.sarvamMode
        if !mode.isEmpty {
            body.appendMultipart(boundary: boundary, name: "model", value: mode)
        }

        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw RecognitionError.apiError("Sarvam API error")
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        return (json?["transcript"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
    }

    private func transcribeProxied(wavData: Data) async throws -> String {
        guard let token = await AuthManager.shared.getIdToken() else {
            throw RecognitionError.noApiKey("Not signed in. Sign in to use proxied recognition.")
        }

        let url = URL(string: settings.proxiedEndpoint)!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")

        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        body.appendMultipart(boundary: boundary, name: "file", filename: "audio.wav", mimeType: "audio/wav", data: wavData)
        body.appendMultipart(boundary: boundary, name: "provider", value: settings.proxiedProvider)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
            let responseBody = String(data: data, encoding: .utf8) ?? ""
            print("AuthManager: Proxied API error \(statusCode): \(responseBody)")
            throw RecognitionError.apiError("Proxied API error (\(statusCode))")
        }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        // Sarvam returns "transcript", Whisper returns "text"
        let text = (json?["transcript"] as? String) ?? (json?["text"] as? String) ?? ""
        return text.trimmingCharacters(in: .whitespaces)
    }
}

enum RecognitionError: LocalizedError {
    case noApiKey(String)
    case apiError(String)

    var errorDescription: String? {
        switch self {
        case .noApiKey(let msg): return msg
        case .apiError(let msg): return msg
        }
    }
}

// MARK: - Multipart Helpers

extension Data {
    mutating func appendMultipart(boundary: String, name: String, filename: String, mimeType: String, data: Data) {
        append("--\(boundary)\r\n".data(using: .utf8)!)
        append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        append(data)
        append("\r\n".data(using: .utf8)!)
    }

    mutating func appendMultipart(boundary: String, name: String, value: String) {
        append("--\(boundary)\r\n".data(using: .utf8)!)
        append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
        append(value.data(using: .utf8)!)
        append("\r\n".data(using: .utf8)!)
    }
}
