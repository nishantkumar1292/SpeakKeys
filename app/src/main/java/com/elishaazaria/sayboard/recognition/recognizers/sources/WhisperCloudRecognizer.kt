package com.elishaazaria.sayboard.recognition.recognizers.sources

import android.util.Log
import com.elishaazaria.sayboard.recognition.recognizers.Recognizer
import com.elishaazaria.sayboard.utils.DevanagariTransliterator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class WhisperCloudRecognizer(
    private val apiKey: String,
    override val locale: Locale?,
    private val prompt: String = "",
    private val transliterateToRoman: Boolean = false
) : Recognizer {

    companion object {
        private const val TAG = "WhisperCloudRecognizer"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/audio/transcriptions"
    }

    override val sampleRate: Float = 16000f

    // Audio buffer - max 30 seconds
    private val maxBufferSamples = (30 * sampleRate).toInt()
    private val audioBuffer = ShortArray(maxBufferSamples)
    private var bufferPosition = 0

    private var lastResult = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun reset() {
        bufferPosition = 0
        lastResult = ""
    }

    override fun acceptWaveForm(buffer: ShortArray?, nread: Int): Boolean {
        if (buffer == null || nread <= 0) return false

        val samplesToAdd = minOf(nread, maxBufferSamples - bufferPosition)
        if (samplesToAdd > 0) {
            System.arraycopy(buffer, 0, audioBuffer, bufferPosition, samplesToAdd)
            bufferPosition += samplesToAdd
        }

        // Return true if buffer is full (triggers auto-stop)
        return bufferPosition >= maxBufferSamples
    }

    override fun getResult(): String {
        // Whisper doesn't have intermediate results - return empty
        return ""
    }

    override fun getPartialResult(): String {
        // Show "..." to indicate recording is happening
        return if (bufferPosition > sampleRate.toInt() / 2) "..." else ""
    }

    override fun getFinalResult(): String {
        if (bufferPosition == 0) return ""

        Log.d(TAG, "Transcribing ${bufferPosition} samples (${bufferPosition / sampleRate} seconds)")
        transcribe()
        val result = lastResult
        lastResult = ""
        return result
    }

    private fun transcribe() {
        if (apiKey.isEmpty()) {
            Log.e(TAG, "No API key configured")
            return
        }

        val wavBytes = createWavBytes()
        Log.d(TAG, "Created WAV: ${wavBytes.size} bytes")

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "audio.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "whisper-1")
                .apply {
                    // Add language hint if specified
                    locale?.language?.takeIf { it.isNotEmpty() && it != "und" }?.let { lang ->
                        addFormDataPart("language", lang)
                    }
                    // Add prompt if specified (useful for Romanized/Hinglish output)
                    if (prompt.isNotEmpty()) {
                        addFormDataPart("prompt", prompt)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            Log.d(TAG, "Sending request to OpenAI...")
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    var text = json.optString("text", "").trim()

                    // Apply Devanagari to Roman transliteration if enabled
                    if (transliterateToRoman) {
                        text = DevanagariTransliterator.transliterate(text)
                    }

                    lastResult = removeSpaceForLocale(text)
                }
            } else {
                Log.e(TAG, "API error: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
        }

        bufferPosition = 0
    }

    private fun createWavBytes(): ByteArray {
        val numSamples = bufferPosition
        val byteRate = sampleRate.toInt() * 2  // 16-bit mono
        val dataSize = numSamples * 2
        val fileSize = 36 + dataSize

        val wav = ByteArray(44 + dataSize)
        var offset = 0

        // RIFF header
        "RIFF".toByteArray().copyInto(wav, offset); offset += 4
        writeInt(wav, offset, fileSize); offset += 4
        "WAVE".toByteArray().copyInto(wav, offset); offset += 4

        // fmt chunk
        "fmt ".toByteArray().copyInto(wav, offset); offset += 4
        writeInt(wav, offset, 16); offset += 4  // chunk size
        writeShort(wav, offset, 1); offset += 2  // PCM format
        writeShort(wav, offset, 1); offset += 2  // mono
        writeInt(wav, offset, sampleRate.toInt()); offset += 4
        writeInt(wav, offset, byteRate); offset += 4
        writeShort(wav, offset, 2); offset += 2  // block align
        writeShort(wav, offset, 16); offset += 2  // bits per sample

        // data chunk
        "data".toByteArray().copyInto(wav, offset); offset += 4
        writeInt(wav, offset, dataSize); offset += 4

        // audio data
        for (i in 0 until numSamples) {
            writeShort(wav, offset, audioBuffer[i].toInt()); offset += 2
        }

        return wav
    }

    private fun writeInt(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xff).toByte()
        arr[offset + 1] = ((value shr 8) and 0xff).toByte()
        arr[offset + 2] = ((value shr 16) and 0xff).toByte()
        arr[offset + 3] = ((value shr 24) and 0xff).toByte()
    }

    private fun writeShort(arr: ByteArray, offset: Int, value: Int) {
        arr[offset] = (value and 0xff).toByte()
        arr[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
}
