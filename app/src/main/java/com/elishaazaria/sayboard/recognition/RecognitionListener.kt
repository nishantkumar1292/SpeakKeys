package com.elishaazaria.sayboard.recognition

/**
 * Listener for speech recognition events.
 * Recognition listener interface for speech services.
 */
interface RecognitionListener {
    fun onResult(text: String?)
    fun onFinalResult(text: String?)
    fun onPartialResult(partialText: String?)
    fun onError(e: Exception?)
    fun onTimeout()
}
