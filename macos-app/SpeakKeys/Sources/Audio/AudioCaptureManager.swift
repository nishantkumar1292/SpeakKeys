import AVFoundation
import Foundation

/// Captures microphone audio via AVAudioEngine, resampling to 16kHz mono 16-bit PCM.
class AudioCaptureManager {
    static let shared = AudioCaptureManager()

    private let engine = AVAudioEngine()
    private let targetSampleRate: Double = 16000
    private var isCapturing = false

    typealias AudioCallback = (_ samples: [Int16], _ count: Int) -> Void

    private init() {}

    /// Start capturing audio from the default input device.
    /// The callback fires on a background queue with 16kHz 16-bit PCM chunks.
    func startCapture(onAudio: @escaping AudioCallback) {
        guard !isCapturing else { return }

        let inputNode = engine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)

        guard let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: targetSampleRate,
            channels: 1,
            interleaved: true
        ) else {
            return
        }

        guard let converter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
            return
        }

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: inputFormat) { buffer, _ in
            self.convert(buffer: buffer, converter: converter, targetFormat: targetFormat, onAudio: onAudio)
        }

        do {
            try engine.start()
            isCapturing = true
        } catch {
            print("AudioCaptureManager: Failed to start engine: \(error)")
        }
    }

    func stopCapture() {
        guard isCapturing else { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        isCapturing = false
    }

    private func convert(
        buffer: AVAudioPCMBuffer,
        converter: AVAudioConverter,
        targetFormat: AVAudioFormat,
        onAudio: @escaping AudioCallback
    ) {
        let frameCount = AVAudioFrameCount(
            Double(buffer.frameLength) * targetSampleRate / buffer.format.sampleRate
        )
        guard frameCount > 0 else { return }

        guard let convertedBuffer = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: frameCount) else {
            return
        }

        var error: NSError?
        var inputConsumed = false
        converter.convert(to: convertedBuffer, error: &error) { _, outStatus in
            if inputConsumed {
                outStatus.pointee = .noDataNow
                return nil
            }
            inputConsumed = true
            outStatus.pointee = .haveData
            return buffer
        }

        if let error {
            print("AudioCaptureManager: Conversion error: \(error)")
            return
        }

        guard let int16Data = convertedBuffer.int16ChannelData else { return }
        let count = Int(convertedBuffer.frameLength)
        let samples = Array(UnsafeBufferPointer(start: int16Data.pointee, count: count))
        onAudio(samples, count)
    }
}
