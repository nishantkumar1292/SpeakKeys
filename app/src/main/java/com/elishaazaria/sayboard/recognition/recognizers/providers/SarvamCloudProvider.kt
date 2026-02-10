package com.elishaazaria.sayboard.recognition.recognizers.providers

import android.content.Context
import com.elishaazaria.sayboard.data.InstalledModelReference
import com.elishaazaria.sayboard.data.ModelType
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerSource
import com.elishaazaria.sayboard.recognition.recognizers.sources.SarvamCloud
import com.elishaazaria.sayboard.sayboardPreferenceModel
import java.util.Locale

class SarvamCloudProvider(private val context: Context) : RecognizerSourceProvider {
    private val prefs by sayboardPreferenceModel()

    override fun getInstalledModels(): List<InstalledModelReference> {
        // Only show Sarvam option if API key is configured
        val apiKey = prefs.sarvamApiKey.get()
        if (apiKey.isEmpty()) {
            return emptyList()
        }

        // Return a single "model" representing Sarvam cloud
        return listOf(
            InstalledModelReference(
                path = "sarvam://cloud",  // Virtual path
                name = "Sarvam Cloud (Hinglish)",
                type = ModelType.SarvamCloud
            )
        )
    }

    override fun recognizerSourceForModel(localModel: InstalledModelReference): RecognizerSource? {
        if (localModel.type != ModelType.SarvamCloud) return null

        val apiKey = prefs.sarvamApiKey.get()
        if (apiKey.isEmpty()) return null

        // Sarvam uses en-IN locale for Hinglish display
        val locale = Locale("en", "IN")

        val mode = prefs.sarvamMode.get()
        val languageCode = prefs.sarvamLanguage.get()

        return SarvamCloud(apiKey, locale, mode, languageCode)
    }
}
