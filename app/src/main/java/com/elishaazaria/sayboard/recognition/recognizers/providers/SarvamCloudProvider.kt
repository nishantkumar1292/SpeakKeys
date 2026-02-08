package com.elishaazaria.sayboard.recognition.recognizers.providers

import android.content.Context
import android.util.Log
import com.elishaazaria.sayboard.BuildConfig
import com.elishaazaria.sayboard.data.InstalledModelReference
import com.elishaazaria.sayboard.data.ModelType
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerSource
import com.elishaazaria.sayboard.recognition.recognizers.sources.SarvamCloud
import java.util.Locale

class SarvamCloudProvider(private val context: Context) : RecognizerSourceProvider {
    companion object {
        private const val TAG = "SarvamCloudProvider"
    }

    override fun getInstalledModels(): List<InstalledModelReference> {
        // Only show Sarvam option if API key is configured
        val apiKey = BuildConfig.SARVAM_API_SUBSCRIPTION_KEY
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
        Log.d(TAG, "recognizerSourceForModel called for: ${localModel.type}")
        if (localModel.type != ModelType.SarvamCloud) return null

        val apiKey = BuildConfig.SARVAM_API_SUBSCRIPTION_KEY
        Log.d(TAG, "API key length: ${apiKey.length}, empty: ${apiKey.isEmpty()}")
        if (apiKey.isEmpty()) {
            Log.e(TAG, "API key is empty!")
            return null
        }

        // Sarvam uses en-IN locale for Hinglish display
        val locale = Locale("en", "IN")

        Log.d(TAG, "Creating SarvamCloud with locale: $locale")
        return SarvamCloud(apiKey, locale)
    }
}
