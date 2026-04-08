import Foundation

/// Native Swift WAV encoder matching the KMP shared WavEncoder.
/// Encodes 16kHz 16-bit mono PCM samples into a WAV byte array.
enum WavEncoder {
    static func encode(samples: [Int16], sampleRate: Int = 16000) -> Data {
        let numSamples = samples.count
        let byteRate = sampleRate * 2  // 16-bit mono
        let dataSize = numSamples * 2
        let fileSize = 36 + dataSize

        var wav = Data(capacity: 44 + dataSize)

        // RIFF header
        wav.append(contentsOf: "RIFF".utf8)
        wav.appendLittleEndianInt32(Int32(fileSize))
        wav.append(contentsOf: "WAVE".utf8)

        // fmt chunk
        wav.append(contentsOf: "fmt ".utf8)
        wav.appendLittleEndianInt32(16)            // chunk size
        wav.appendLittleEndianInt16(1)             // PCM format
        wav.appendLittleEndianInt16(1)             // mono
        wav.appendLittleEndianInt32(Int32(sampleRate))
        wav.appendLittleEndianInt32(Int32(byteRate))
        wav.appendLittleEndianInt16(2)             // block align
        wav.appendLittleEndianInt16(16)            // bits per sample

        // data chunk
        wav.append(contentsOf: "data".utf8)
        wav.appendLittleEndianInt32(Int32(dataSize))

        // audio data
        for sample in samples {
            wav.appendLittleEndianInt16(sample)
        }

        return wav
    }
}

extension Data {
    mutating func appendLittleEndianInt32(_ value: Int32) {
        var v = value.littleEndian
        append(Data(bytes: &v, count: 4))
    }

    mutating func appendLittleEndianInt16(_ value: Int16) {
        var v = value.littleEndian
        append(Data(bytes: &v, count: 2))
    }
}
