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

/**
 * Recognizer that sends audio to our Firebase Cloud Functions proxy
 * instead of directly to OpenAI/Sarvam. The proxy handles API keys server-side.
 */
class ProxiedCloudRecognizer(
    private val firebaseIdToken: String,
    private val provider: String, // "whisper" or "sarvam"
    override val locale: Locale?,
    private val providerParams: Map<String, String> = emptyMap(),
    private val transliterateToRoman: Boolean = false
) : Recognizer {

    companion object {
        private const val TAG = "ProxiedCloudRecognizer"
        const val PROXY_BASE_URL = "https://asia-south1-speakkeys.cloudfunctions.net"
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 1500L
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

        return bufferPosition >= maxBufferSamples
    }

    override fun getResult(): String = ""

    override fun getPartialResult(): String = ""

    override fun getFinalResult(): String {
        if (bufferPosition == 0) return ""

        Log.d(TAG, "Transcribing ${bufferPosition} samples via proxy ($provider)")
        transcribe()
        val result = lastResult
        lastResult = ""
        return result
    }

    private fun transcribe() {
        val wavBytes = createWavBytes()
        Log.d(TAG, "Created WAV: ${wavBytes.size} bytes")

        for (attempt in 0..MAX_RETRIES) {
            try {
                val bodyBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        "audio.wav",
                        wavBytes.toRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("provider", provider)

                // Add all provider-specific params
                for ((key, value) in providerParams) {
                    if (value.isNotEmpty()) {
                        bodyBuilder.addFormDataPart(key, value)
                    }
                }

                val request = Request.Builder()
                    .url("${PROXY_BASE_URL}/transcribe")
                    .addHeader("Authorization", "Bearer $firebaseIdToken")
                    .post(bodyBuilder.build())
                    .build()

                Log.d(TAG, "Sending request to proxy (attempt ${attempt + 1})...")
                val response = client.newCall(request).execute()

                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            var text = when (provider) {
                                "sarvam" -> json.optString("transcript", "").trim()
                                else -> json.optString("text", "").trim()
                            }

                            if (transliterateToRoman) {
                                text = DevanagariTransliterator.transliterate(text)
                            }

                            lastResult = removeSpaceForLocale(text)
                        }
                        bufferPosition = 0
                        return // Success, no retry needed
                    }
                    response.code == 402 -> {
                        // Quota/access error - don't retry
                        Log.w(TAG, "Access denied: ${response.code}")
                        bufferPosition = 0
                        return
                    }
                    else -> {
                        Log.e(TAG, "Proxy error: ${response.code} (attempt ${attempt + 1}/${MAX_RETRIES + 1})")
                        response.body?.close()
                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(RETRY_DELAY_MS)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription via proxy failed (attempt ${attempt + 1}/${MAX_RETRIES + 1})", e)
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_DELAY_MS)
                }
            }
        }

        Log.e(TAG, "All ${MAX_RETRIES + 1} transcription attempts failed")
        bufferPosition = 0
    }

    private fun createWavBytes(): ByteArray {
        val numSamples = bufferPosition
        val byteRate = sampleRate.toInt() * 2
        val dataSize = numSamples * 2
        val fileSize = 36 + dataSize

        val wav = ByteArray(44 + dataSize)
        var offset = 0

        "RIFF".toByteArray().copyInto(wav, offset); offset += 4
        writeInt(wav, offset, fileSize); offset += 4
        "WAVE".toByteArray().copyInto(wav, offset); offset += 4

        "fmt ".toByteArray().copyInto(wav, offset); offset += 4
        writeInt(wav, offset, 16); offset += 4
        writeShort(wav, offset, 1); offset += 2
        writeShort(wav, offset, 1); offset += 2
        writeInt(wav, offset, sampleRate.toInt()); offset += 4
        writeInt(wav, offset, byteRate); offset += 4
        writeShort(wav, offset, 2); offset += 2
        writeShort(wav, offset, 16); offset += 2

        "data".toByteArray().copyInto(wav, offset); offset += 4
        writeInt(wav, offset, dataSize); offset += 4

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
