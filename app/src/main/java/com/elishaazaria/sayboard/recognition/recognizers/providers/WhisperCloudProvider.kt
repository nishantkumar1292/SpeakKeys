package com.elishaazaria.sayboard.recognition.recognizers.providers

import android.content.Context
import android.util.Log
import com.elishaazaria.sayboard.BuildConfig
import com.elishaazaria.sayboard.data.InstalledModelReference
import com.elishaazaria.sayboard.data.ModelType
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerSource
import com.elishaazaria.sayboard.recognition.recognizers.sources.WhisperCloud
import com.elishaazaria.sayboard.sayboardPreferenceModel
import java.util.Locale

class WhisperCloudProvider(private val context: Context) : RecognizerSourceProvider {
    companion object {
        private const val TAG = "WhisperCloudProvider"
    }

    private val prefs by sayboardPreferenceModel()

    override fun getInstalledModels(): List<InstalledModelReference> {
        // Only show cloud option if API key is configured
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isEmpty()) {
            return emptyList()
        }

        // Return a single "model" representing OpenAI cloud
        return listOf(
            InstalledModelReference(
                path = "whisper://cloud",  // Virtual path
                name = "Whisper Cloud (OpenAI)",
                type = ModelType.WhisperCloud
            )
        )
    }

    override fun recognizerSourceForModel(localModel: InstalledModelReference): RecognizerSource? {
        Log.d(TAG, "recognizerSourceForModel called for: ${localModel.type}")
        if (localModel.type != ModelType.WhisperCloud) return null

        val apiKey = BuildConfig.OPENAI_API_KEY
        Log.d(TAG, "API key length: ${apiKey.length}, empty: ${apiKey.isEmpty()}")
        if (apiKey.isEmpty()) {
            Log.e(TAG, "API key is empty!")
            return null
        }

        // Get preferred language from settings
        val languageCode = prefs.whisperLanguage.get()
        val locale = if (languageCode.isNotEmpty()) {
            Locale(languageCode)
        } else {
            Locale.ROOT  // Auto-detect
        }

        // Get prompt for guiding transcription style (e.g., Hinglish)
        val prompt = prefs.whisperPrompt.get()

        // Get transliteration preference
        val transliterateToRoman = prefs.whisperTransliterateToRoman.get()

        Log.d(TAG, "Creating WhisperCloud with locale: $locale, prompt: $prompt, transliterate: $transliterateToRoman")
        return WhisperCloud(apiKey, locale, prompt, transliterateToRoman)
    }
}
