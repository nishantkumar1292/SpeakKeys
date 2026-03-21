package com.elishaazaria.sayboard.ime

import android.util.Log
import com.elishaazaria.sayboard.recognition.ModelManager
import com.elishaazaria.sayboard.recognition.text.TextProcessor
import com.elishaazaria.sayboard.speakKeysPreferenceModel

class TextManager(private val ime: IME, private val modelManager: ModelManager) {
    private val prefs by speakKeysPreferenceModel()

    private var addSpace = false
    private var capitalize = true
    private var firstSinceResume = true

    private var composing = false

    fun onUpdateSelection(
        newSelStart: Int,
        newSelEnd: Int,
    ) {
        if (!composing) {
            if (newSelStart == newSelEnd) { // cursor moved
                checkAddSpaceAndCapitalize()
            }
        }
    }

    fun onText(text: String, mode: Mode) {
        if (text.isEmpty())  // no need to commit empty text
            return
        Log.d(
            TAG,
            "onText. text: $text, mode: $mode, addSpace: $addSpace, firstSinceResume: $firstSinceResume"
        )

        if (text.startsWith(" ")) {
            Log.d(TAG, "Starts with space!")
        }

        if (firstSinceResume) {
            firstSinceResume = false
            checkAddSpaceAndCapitalize()
        }

        val ic = ime.currentInputConnection
        if (ic == null) {
            Log.e(TAG, "currentInputConnection is NULL! Cannot insert text: $text")
            return
        }

        val spacedText = TextProcessor.processText(
            text = text,
            shouldCapitalize = capitalize,
            shouldAddSpace = addSpace,
            autoCapitalizeEnabled = prefs.logicAutoCapitalize.get(),
            recognizerAddsSpaces = modelManager.currentRecognizerSourceAddSpaces
        )

        when (mode) {
            Mode.FINAL, Mode.STANDARD -> {
                // add a space next time. Usually overridden by onUpdateSelection
                addSpace = TextProcessor.addSpaceAfter(
                    spacedText[spacedText.length - 1] // last char
                )
                TextProcessor.capitalizeAfter(
                    spacedText
                )?.let {
                    capitalize = it
                }
                composing = false
                Log.d(TAG, "Committing text: '$spacedText'")
                val success = ic.commitText(spacedText, 1)
                Log.d(TAG, "commitText result: $success")
            }

            Mode.PARTIAL -> {
                composing = true
                Log.d(TAG, "Setting composing text: '$spacedText'")
                ic.setComposingText(spacedText, 1)
            }

            Mode.INSERT -> {                // Manual insert. Don't add a space.
                composing = false
                ic.commitText(text, 1)
            }
        }
    }

    private fun checkAddSpaceAndCapitalize() {
        if (!modelManager.currentRecognizerSourceAddSpaces) {
            addSpace = false
            return
        }
        val cs = ime.currentInputConnection?.getTextBeforeCursor(3, 0)
        if (cs != null) {
            addSpace = cs.isNotEmpty() && TextProcessor.addSpaceAfter(cs[cs.length - 1])

            val value = TextProcessor.capitalizeAfter(cs)
            value?.let {
                capitalize = it
            }
        }
    }

    fun onResume() {
        firstSinceResume = true;
    }

    enum class Mode {
        STANDARD, PARTIAL, FINAL, INSERT
    }

    companion object {
        private const val TAG = "TextManager"
    }
}
