// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.elishaazaria.sayboard.ime

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.core.app.ActivityCompat
import com.elishaazaria.sayboard.R
import com.elishaazaria.sayboard.data.KeepScreenAwakeMode
import com.elishaazaria.sayboard.recognition.ModelManager
import com.elishaazaria.sayboard.recognition.auth.AndroidAuthTokenProvider
import com.elishaazaria.sayboard.recognition.preferences.AndroidPreferencesRepository
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerSource
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerState
import com.elishaazaria.sayboard.speakKeysPreferenceModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class IME : InputMethodService(), ModelManager.Listener {
    private val prefs by speakKeysPreferenceModel()

    private var hasMicPermission: Boolean = false

    public val lifecycleOwner = IMELifecycleOwner()
    private lateinit var editorInfo: EditorInfo
    private lateinit var viewManager: ViewManager
    private lateinit var modelManager: ModelManager
    private lateinit var actionManager: ActionManager
    private lateinit var textManager: TextManager

    private var currentRecognizerSource: RecognizerSource? = null
    private var stateFlowJob: Job? = null


    public var enterAction = EditorInfo.IME_ACTION_UNSPECIFIED
        private set
    var enterActionLabel = ""
        private set
    var enterActionVisual = ViewManager.EnterActionVisual.ENTER
        private set

    var isRichTextEditor = true
        private set
    private val uiHandler = Handler(Looper.getMainLooper())
    private var micPressed = false
    private var micProcessing = false
    private val deferredResults = mutableListOf<String>()
    private val holdWarningRunnable = Runnable {
        if (micPressed && modelManager.isRunning) {
            viewManager.stateLD.postValue(ViewManager.STATE_LIMIT_WARNING)
        }
    }
    private val holdAutoStopRunnable = Runnable {
        if (micPressed && modelManager.isRunning) {
            micPressed = false
            startProcessing()
            modelManager.stop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("IME", "@onCreate")

        lifecycleOwner.onCreate()

        viewManager = ViewManager(this)
        viewManager.setListener(viewManagerListener)

        actionManager = ActionManager(this, viewManager)

        checkMicrophonePermission()

        modelManager = ModelManager(this, this, AndroidPreferencesRepository(), AndroidAuthTokenProvider())
        modelManager.initializeFirstLocale(false)

        textManager = TextManager(this, modelManager)

        viewManager.recordDevice.observe(lifecycleOwner) {
            modelManager.recordDevice = it
        }
    }

    /**
     * Called on create and after a configuration change
     */

    override fun onInitializeInterface() {
        Log.d("IME", "@onInitializeInterface")

        checkMicrophonePermission()
    }

    /**
     * Called when switching to a new app (input sink)
     */
    override fun onBindInput() {
        Log.d("IME", "@onBindInput")

        modelManager.reloadModels()
        modelManager.initializeFirstLocale(false)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleOwner.onResume()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleOwner.onPause()
    }

    /**
     * Called when the keyboard is opened (called twice for some reason)
     */
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        Log.d("IME", "@onStartInputView, info: $info, restarting: $restarting")

        checkMicrophonePermission()
        editorInfo = info
        resolveEnterKey()
        isRichTextEditor =
            editorInfo.inputType and InputType.TYPE_MASK_CLASS != EditorInfo.TYPE_NULL ||
                    editorInfo.initialSelStart >= 0 && editorInfo.initialSelEnd >= 0 // based on florisboard code
        textManager.onResume()
        setKeepScreenOn(prefs.logicKeepScreenAwake.get() == KeepScreenAwakeMode.WHEN_OPEN)
        viewManager.enterActionLabelLD.postValue(enterActionLabel)
        viewManager.enterActionVisualLD.postValue(enterActionVisual)
        actionManager.onStartInputView()
    }

    private fun resolveEnterKey() {
        enterAction = EditorInfo.IME_ACTION_UNSPECIFIED
        enterActionLabel = getString(R.string.ime_action_enter)
        enterActionVisual = ViewManager.EnterActionVisual.ENTER

        if (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            return
        }

        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val customLabel = editorInfo.actionLabel?.toString()?.trim().orEmpty()
        val customActionId = when {
            editorInfo.actionId != 0 -> editorInfo.actionId
            action in actionableEditorActions -> action
            else -> EditorInfo.IME_ACTION_UNSPECIFIED
        }

        if (customLabel.isNotEmpty() && customActionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
            enterAction = customActionId
            enterActionLabel = customLabel
            enterActionVisual = toEnterActionVisual(customActionId)
            return
        }

        if (action in actionableEditorActions) {
            enterAction = action
            enterActionVisual = toEnterActionVisual(action)
            enterActionLabel = when (action) {
                EditorInfo.IME_ACTION_GO -> getString(R.string.ime_action_go)
                EditorInfo.IME_ACTION_SEARCH -> getString(R.string.ime_action_search)
                EditorInfo.IME_ACTION_SEND -> getString(R.string.ime_action_send)
                EditorInfo.IME_ACTION_NEXT -> getString(R.string.ime_action_next)
                EditorInfo.IME_ACTION_DONE -> getString(R.string.ime_action_done)
                EditorInfo.IME_ACTION_PREVIOUS -> getString(R.string.ime_action_previous)
                else -> getString(R.string.ime_action_enter)
            }
        }
    }

    /**
     * Called when the keyboard is closed
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        Log.d("IME", "@onFinishInputView. finishedInput: $finishingInput")

        // text input has ended
        micPressed = false
        micProcessing = false
        deferredResults.clear()
        cancelHoldTimers()
        setKeepScreenOn(false)
        modelManager.stop(!prefs.logicKeepModelInRam.get())
        viewManager.enterActionLabelLD.postValue(getString(R.string.ime_action_enter))
        viewManager.enterActionVisualLD.postValue(ViewManager.EnterActionVisual.ENTER)
        if (prefs.logicAutoSwitchBack.get()) {
            // switch back
            actionManager.switchToLastIme(false)
        }
    }

    /**
     * Called the first time the keyboard is opened after a configuration change
     */
    override fun onCreateInputView(): View {
        Log.d("IME", "@onCreateInputView. decorView: ${window?.window?.decorView}")

        lifecycleOwner.attachToDecorView(
            window?.window?.decorView
        )

        return viewManager
    }

    private val viewManagerListener = object : ViewManager.Listener {
        override fun micPressStart() {
            if (micProcessing) return
            if (!hasMicPermission || modelManager.openSettingsOnMic) {
                // errors! open settings
                actionManager.openSettings()
                return
            }
            micPressed = true
            deferredResults.clear()
            if (!modelManager.isRunning) {
                modelManager.start()
                if (prefs.logicKeepScreenAwake.get() == KeepScreenAwakeMode.WHEN_LISTENING)
                    setKeepScreenOn(true)
            }
        }

        override fun micPressEnd() {
            micPressed = false
            cancelHoldTimers()
            if (modelManager.isRunning) {
                startProcessing()
                modelManager.stop()
            }
        }

        override fun backspaceClicked() {
            actionManager.deleteLastChar()
        }

        override fun settingsClicked() {
            actionManager.openSettings()
        }

        override fun buttonClicked(text: String) {
            textManager.onText(text, TextManager.Mode.INSERT)
        }

        override fun toggleKeyboardMode() {
            val current = viewManager.keyboardModeLD.value ?: ViewManager.KeyboardMode.VOICE
            viewManager.keyboardModeLD.postValue(
                if (current == ViewManager.KeyboardMode.VOICE) ViewManager.KeyboardMode.TYPING
                else ViewManager.KeyboardMode.VOICE
            )
        }

        override fun cursorLeftClicked() {
            actionManager.moveCursorLeft()
        }

        override fun cursorRightClicked() {
            actionManager.moveCursorRight()
        }

        override fun enterClicked() {
            actionManager.sendEnter()
        }
    }

    /**
     * Called when the current selection is updated (which happens when we write text, too - we need to make sure there aren't any loops)
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        Log.d("IME", "@onUpdateSelection")

        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        actionManager.updateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        textManager.onUpdateSelection(newSelStart, newSelEnd)
    }

    /**
     * Called when the keyboard process is closed. This happens when the user switches to a different keyboard.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("IME", "@onDestroy")

        micPressed = false
        micProcessing = false
        deferredResults.clear()
        cancelHoldTimers()
        stateFlowJob?.cancel()
        authRetryJob?.cancel()
        lifecycleOwner.onDestroy()
        modelManager.onDestroy()
    }

    val token: IBinder?
        get() {
            val window = myWindow ?: return null
            return window.attributes.token
        }
    val myWindow: Window?
        get() {
            val dialog = window ?: return null
            return dialog.window
        }

    private fun setKeepScreenOn(keepScreenOn: Boolean) {
        val window = myWindow ?: return
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun checkMicrophonePermission() {
        hasMicPermission = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            viewManager.errorMessageLD.postValue(getString(R.string.mic_error_no_permission))
            viewManager.stateLD.postValue(ViewManager.STATE_ERROR)
        }
    }

    override fun onResult(text: String?) {
        val chunk = text?.trim().orEmpty()
        if (chunk.isNotEmpty() && (deferredResults.isEmpty() || deferredResults.last() != chunk)) {
            deferredResults.add(chunk)
        }
    }

    override fun onFinalResult(text: String?) {
        finishProcessing()
        val finalText = mergeDeferredResults(text)
        if (finalText.isEmpty()) return
        textManager.onText(finalText, TextManager.Mode.FINAL)
    }

    override fun onPartialResult(partialText: String?) {
        // Push-to-talk hides partials while holding.
    }

    override fun onStateChanged(state: ModelManager.State) {
        if (state != ModelManager.State.STATE_LISTENING) {
            cancelHoldTimers()
        }
        if (state == ModelManager.State.STATE_STOPPED) {
            stateFlowJob?.cancel()
            viewManager.stateLD.postValue(
                if (micProcessing) ViewManager.STATE_PROCESSING else ViewManager.STATE_READY
            )
        } else {
            viewManager.stateLD.postValue(
                when (state) {
                    ModelManager.State.STATE_INITIAL -> ViewManager.STATE_INITIAL
                    ModelManager.State.STATE_LOADING -> ViewManager.STATE_LOADING
                    ModelManager.State.STATE_READY -> ViewManager.STATE_READY
                    ModelManager.State.STATE_LISTENING -> {
                        if (micPressed) {
                            scheduleHoldTimers()
                            ViewManager.STATE_LISTENING
                        } else {
                            startProcessing()
                            modelManager.stop()
                            ViewManager.STATE_PROCESSING
                        }
                    }
                    ModelManager.State.STATE_PAUSED -> ViewManager.STATE_PAUSED
                    ModelManager.State.STATE_ERROR -> ViewManager.STATE_ERROR
                    else -> TODO()
                }
            )
        }
    }

    override fun onError(type: ModelManager.ErrorType) {
        finishProcessing()
        deferredResults.clear()
        viewManager.errorMessageLD.postValue(
            when (type) {
                ModelManager.ErrorType.MIC_IN_USE -> getString(R.string.mic_error_mic_in_use)
                ModelManager.ErrorType.NO_RECOGNIZERS_INSTALLED -> getString(R.string.mic_error_no_recognizers)
            }
        )
    }

    override fun onError(e: Exception?) {
        finishProcessing()
        deferredResults.clear()
        viewManager.errorMessageLD.postValue(getString(R.string.mic_error_recognizer_error))
        viewManager.stateLD.postValue(ViewManager.STATE_ERROR)
    }

    override fun onRecognizerSource(source: RecognizerSource) {
        stateFlowJob?.cancel()
        authRetryJob?.cancel()
        currentRecognizerSource = source
        stateFlowJob = lifecycleOwner.lifecycleScope.launch {
            source.stateFlow.collect { state ->
                viewManager.onRecognizerStateChanged(state)
                if (state == RecognizerState.ERROR) {
                    viewManager.errorMessageLD.postValue(source.errorMessage)
                    scheduleAuthRetry()
                }
            }
        }
    }

    private var authRetryJob: Job? = null

    private fun scheduleAuthRetry() {
        authRetryJob?.cancel()
        authRetryJob = lifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(5000)
            if (viewManager.stateLD.value == ViewManager.STATE_ERROR && !micPressed && !micProcessing) {
                Log.d("IME", "Auto-retrying initialization after auth error")
                modelManager.initializeFirstLocale(false)
            }
        }
    }

    override fun onTimeout() {
        finishProcessing()
        deferredResults.clear()
        viewManager.stateLD.postValue(ViewManager.STATE_READY)
    }

    private fun scheduleHoldTimers() {
        cancelHoldTimers()
        uiHandler.postDelayed(holdWarningRunnable, HOLD_WARNING_MS)
        uiHandler.postDelayed(holdAutoStopRunnable, HOLD_AUTO_STOP_MS)
    }

    private fun cancelHoldTimers() {
        uiHandler.removeCallbacks(holdWarningRunnable)
        uiHandler.removeCallbacks(holdAutoStopRunnable)
    }

    private fun startProcessing() {
        if (micProcessing) return
        micProcessing = true
        viewManager.stateLD.postValue(ViewManager.STATE_PROCESSING)
    }

    private fun finishProcessing() {
        micPressed = false
        cancelHoldTimers()
        if (!micProcessing) return
        micProcessing = false
        if (prefs.logicKeepScreenAwake.get() == KeepScreenAwakeMode.WHEN_LISTENING) {
            setKeepScreenOn(false)
        }
        viewManager.stateLD.postValue(ViewManager.STATE_READY)
    }

    private fun mergeDeferredResults(finalText: String?): String {
        val parts = deferredResults.toMutableList()
        deferredResults.clear()
        val tail = finalText?.trim().orEmpty()
        if (tail.isNotEmpty() && (parts.isEmpty() || parts.last() != tail)) parts.add(tail)
        if (parts.isEmpty()) return ""
        return if (modelManager.currentRecognizerSourceAddSpaces) {
            parts.joinToString(" ").trim()
        } else {
            parts.joinToString("").trim()
        }
    }

    companion object {
        private const val HOLD_WARNING_MS = 27_000L
        private const val HOLD_AUTO_STOP_MS = 30_000L
        private val actionableEditorActions = intArrayOf(
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS
        )
    }

    private fun toEnterActionVisual(action: Int): ViewManager.EnterActionVisual {
        return when (action) {
            EditorInfo.IME_ACTION_GO -> ViewManager.EnterActionVisual.GO
            EditorInfo.IME_ACTION_SEARCH -> ViewManager.EnterActionVisual.SEARCH
            EditorInfo.IME_ACTION_SEND -> ViewManager.EnterActionVisual.SEND
            EditorInfo.IME_ACTION_NEXT -> ViewManager.EnterActionVisual.NEXT
            EditorInfo.IME_ACTION_DONE -> ViewManager.EnterActionVisual.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> ViewManager.EnterActionVisual.PREVIOUS
            else -> ViewManager.EnterActionVisual.ENTER
        }
    }
}
