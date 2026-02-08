package com.elishaazaria.sayboard.recognition.recognizers.providers

import android.content.Context
import com.elishaazaria.sayboard.data.InstalledModelReference
import com.elishaazaria.sayboard.data.ModelType
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerSource

class Providers(context: Context) {
    private val whisperCloudProvider: WhisperCloudProvider
    private val sarvamCloudProvider: SarvamCloudProvider
    private val providers: List<RecognizerSourceProvider>

    init {
        val providersM = mutableListOf<RecognizerSourceProvider>()
        whisperCloudProvider = WhisperCloudProvider(context)
        providersM.add(whisperCloudProvider)
        sarvamCloudProvider = SarvamCloudProvider(context)
        providersM.add(sarvamCloudProvider)
        providers = providersM
    }

    fun recognizerSourceForModel(localModel: InstalledModelReference): RecognizerSource? {
        return when (localModel.type) {
            ModelType.WhisperCloud -> whisperCloudProvider.recognizerSourceForModel(localModel)
            ModelType.SarvamCloud -> sarvamCloudProvider.recognizerSourceForModel(localModel)
        }
    }

    fun installedModels(): Collection<InstalledModelReference> {
        return providers.map { it.getInstalledModels() }.flatten()
    }
}
