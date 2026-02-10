package com.elishaazaria.sayboard.recognition.recognizers.providers

import android.content.Context
import com.elishaazaria.sayboard.data.InstalledModelReference
import com.elishaazaria.sayboard.data.ModelType
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerSource
import com.elishaazaria.sayboard.recognition.recognizers.sources.WhisperCloud
import com.elishaazaria.sayboard.sayboardPreferenceModel
import java.util.Locale

class WhisperCloudProvider(private val context: Context) : RecognizerSourceProvider {
    private val prefs by sayboardPreferenceModel()

    override fun getInstalledModels(): List<InstalledModelReference> {
        // Only show cloud option if API key is configured
        val apiKey = prefs.openaiApiKey.get()
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
        if (localModel.type != ModelType.WhisperCloud) return null

        val apiKey = prefs.openaiApiKey.get()
        if (apiKey.isEmpty()) return null

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

        return WhisperCloud(apiKey, locale, prompt, transliterateToRoman)
    }
}
