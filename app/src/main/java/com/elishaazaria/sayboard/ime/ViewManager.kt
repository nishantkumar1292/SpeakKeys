package com.elishaazaria.sayboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioDeviceInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import com.elishaazaria.sayboard.AppPrefs
import com.elishaazaria.sayboard.Constants
import com.elishaazaria.sayboard.R
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerState
import com.elishaazaria.sayboard.speakKeysPreferenceModel
import com.elishaazaria.sayboard.theme.DarkSurface
import com.elishaazaria.sayboard.theme.DarkSurfaceVariant
import com.elishaazaria.sayboard.theme.ErrorRed
import com.elishaazaria.sayboard.theme.ListeningBlue
import com.elishaazaria.sayboard.theme.Shapes
import com.elishaazaria.sayboard.utils.AudioDevices
import com.elishaazaria.sayboard.utils.describe
import com.elishaazaria.sayboard.utils.toIcon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class ViewManager(private val ime: Context) : AbstractComposeView(ime) {
    private val prefs by speakKeysPreferenceModel()

    val stateLD = MutableLiveData(STATE_INITIAL)
    val errorMessageLD = MutableLiveData(ime.getString(R.string.mic_info_error))
    val keyboardModeLD = MutableLiveData(KeyboardMode.VOICE)
    val enterActionLabelLD = MutableLiveData(ime.getString(R.string.ime_action_enter))
    val enterActionVisualLD = MutableLiveData(EnterActionVisual.ENTER)
    val recordDevice: MutableLiveData<AudioDeviceInfo?> = MutableLiveData()
    val showDevicesPopupLD = MutableLiveData(false)

    private var listener: Listener? = null

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    @Composable
    override fun Content() {
        val state by stateLD.observeAsState(STATE_INITIAL)
        val errorMessage by errorMessageLD.observeAsState(ime.getString(R.string.mic_info_error))
        val keyboardMode by keyboardModeLD.observeAsState(KeyboardMode.VOICE)
        val enterActionLabel by enterActionLabelLD.observeAsState(ime.getString(R.string.ime_action_enter))
        val enterActionVisual by enterActionVisualLD.observeAsState(EnterActionVisual.ENTER)
        val showDevicesPopup by showDevicesPopupLD.observeAsState(false)

        val height =
            (LocalConfiguration.current.screenHeightDp * when (LocalConfiguration.current.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> prefs.keyboardHeightLandscape.get()
                else -> prefs.keyboardHeightPortrait.get()
            }).toInt().dp

        val controlsEnabled =
            state != STATE_LISTENING &&
                state != STATE_PROCESSING &&
                state != STATE_LIMIT_WARNING

        var devices by remember { mutableStateOf(listOf<AudioDeviceInfo>()) }

        LaunchedEffect(showDevicesPopup) {
            if (showDevicesPopup) {
                devices = AudioDevices.validAudioDevices(ime)
            }
        }

        IMETheme(prefs) {
            val primary = MaterialTheme.colors.primary
            val bg = MaterialTheme.colors.background
            val onBg = MaterialTheme.colors.onBackground

            val stateColor by animateColorAsState(
                targetValue = when (state) {
                    STATE_LISTENING -> ListeningBlue
                    STATE_LIMIT_WARNING, STATE_ERROR -> ErrorRed
                    else -> primary
                },
                animationSpec = tween(300),
                label = "stateColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(bg)
            ) {
                when (keyboardMode) {
                    KeyboardMode.VOICE -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            DragHandle(
                                onClick = { (ime as? InputMethodService)?.requestHideSelf(0) }
                            )

                            SymbolsBar(
                                symbols = VOICE_SYMBOLS,
                                enabled = controlsEnabled,
                                onBg = onBg,
                                onSymbolPress = { listener?.buttonClicked(it) }
                            )

                            MicArea(
                                state = state,
                                errorMessage = errorMessage,
                                stateColor = stateColor,
                                onMicPressStart = { listener?.micPressStart() },
                                onMicPressEnd = { listener?.micPressEnd() },
                                onErrorClick = { listener?.settingsClicked() }
                            )

                            BottomBar(
                                enabled = controlsEnabled,
                                onBg = onBg,
                                actionLabel = enterActionLabel,
                                actionVisual = enterActionVisual,
                                onToggleMode = { listener?.toggleKeyboardMode() },
                                onInsertSpace = { listener?.buttonClicked(" ") },
                                onCursorLeft = { listener?.cursorLeftClicked() },
                                onCursorRight = { listener?.cursorRightClicked() },
                                onBackspace = { listener?.backspaceClicked() },
                                onSettings = { listener?.settingsClicked() },
                                onEnter = { listener?.enterClicked() }
                            )
                        }
                    }

                    KeyboardMode.TYPING -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            DragHandle(
                                onClick = { (ime as? InputMethodService)?.requestHideSelf(0) }
                            )

                            QwertyLayout(
                                onKeyPress = { text -> listener?.buttonClicked(text) },
                                onBackspace = { listener?.backspaceClicked() },
                                onCursorLeft = { listener?.cursorLeftClicked() },
                                onCursorRight = { listener?.cursorRightClicked() },
                                onInsertSpace = { listener?.buttonClicked(" ") },
                                onToggleVoiceMode = { listener?.toggleKeyboardMode() },
                                actionLabel = enterActionLabel,
                                actionVisual = enterActionVisual,
                                onEnter = { listener?.enterClicked() },
                                hapticFeedbackEnabled = prefs.hapticFeedbackEnabled.get(),
                                hapticFeedbackIntensity = prefs.hapticFeedbackIntensity.get(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }

                if (showDevicesPopup) {
                    AudioDevicePopup(
                        devices = devices,
                        onDismiss = { showDevicesPopupLD.postValue(false) },
                        onDeviceSelected = { device ->
                            recordDevice.postValue(device)
                            showDevicesPopupLD.postValue(false)
                        }
                    )
                }
            }
        }
    }

    fun onRecognizerStateChanged(value: RecognizerState) {
        when (value) {
            RecognizerState.CLOSED, RecognizerState.NONE -> stateLD.postValue(STATE_INITIAL)
            RecognizerState.LOADING -> stateLD.postValue(STATE_LOADING)
            RecognizerState.READY -> stateLD.postValue(STATE_READY)
            RecognizerState.IN_RAM -> stateLD.postValue(STATE_PAUSED)
            RecognizerState.ERROR -> stateLD.postValue(STATE_ERROR)
        }
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    enum class KeyboardMode { VOICE, TYPING }
    enum class EnterActionVisual { ENTER, GO, SEARCH, SEND, NEXT, DONE, PREVIOUS }

    interface Listener {
        fun micPressStart()
        fun micPressEnd()
        fun backspaceClicked()
        fun settingsClicked()
        fun buttonClicked(text: String)
        fun toggleKeyboardMode()
        fun cursorLeftClicked()
        fun cursorRightClicked()
        fun enterClicked()
    }

    companion object {
        private val VOICE_SYMBOLS = listOf("?", "!", ",", ".", "\"", "(", ")", "-", ":", ";", "'", "/", "@")

        const val STATE_INITIAL = 0
        const val STATE_LOADING = 1
        const val STATE_READY = 2
        const val STATE_LISTENING = 3
        const val STATE_PAUSED = 4
        const val STATE_ERROR = 5
        const val STATE_PROCESSING = 6
        const val STATE_LIMIT_WARNING = 7
    }
}

@Composable
private fun DragHandle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.38f))
        )
    }
}

@Composable
private fun SymbolsBar(
    symbols: List<String>,
    enabled: Boolean,
    onBg: Color,
    onSymbolPress: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(symbols) { symbol ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(44.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (enabled) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.45f)
                    )
                    .clickable(enabled = enabled) { onSymbolPress(symbol) }
            ) {
                Text(
                    text = symbol,
                    color = onBg.copy(alpha = if (enabled) 1f else 0.35f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MicArea(
    state: Int,
    errorMessage: String,
    stateColor: Color,
    onMicPressStart: () -> Unit,
    onMicPressEnd: () -> Unit,
    onErrorClick: () -> Unit
) {
    val isFilledState =
        state == ViewManager.STATE_LISTENING ||
            state == ViewManager.STATE_LIMIT_WARNING ||
            state == ViewManager.STATE_ERROR
    val shouldPulse =
        state == ViewManager.STATE_LISTENING || state == ViewManager.STATE_LIMIT_WARNING

    val pulseScale = if (shouldPulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        scale
    } else {
        1f
    }

    val icon = when (state) {
        ViewManager.STATE_LOADING, ViewManager.STATE_PROCESSING -> Icons.Default.Settings
        ViewManager.STATE_INITIAL, ViewManager.STATE_READY, ViewManager.STATE_PAUSED -> Icons.Default.MicNone
        ViewManager.STATE_LISTENING, ViewManager.STATE_LIMIT_WARNING -> Icons.Default.Mic
        else -> Icons.Default.MicOff
    }

    val statusText = when (state) {
        ViewManager.STATE_LOADING -> stringResource(id = R.string.mic_info_preparing)
        ViewManager.STATE_INITIAL,
        ViewManager.STATE_READY,
        ViewManager.STATE_PAUSED -> stringResource(id = R.string.mic_info_hold_to_talk)
        ViewManager.STATE_LISTENING -> stringResource(id = R.string.mic_info_release_to_send)
        ViewManager.STATE_LIMIT_WARNING -> stringResource(id = R.string.mic_info_release_soon)
        ViewManager.STATE_PROCESSING -> stringResource(id = R.string.mic_info_processing)
        else -> errorMessage
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center
    ) {
        val micSize = (maxHeight * 0.65f).coerceAtMost(120.dp)
        val micIconSize = micSize * 0.4f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(micSize)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        when {
                            state == ViewManager.STATE_ERROR -> stateColor.copy(alpha = 0.92f)
                            isFilledState -> stateColor
                            else -> stateColor.copy(alpha = 0.15f)
                        }
                    )
                    .border(
                        width = if (isFilledState) 0.dp else 2.dp,
                        color = if (isFilledState) Color.Transparent else stateColor.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onMicPressStart()
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    onMicPressEnd()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFilledState) Color.White else stateColor,
                    modifier = Modifier.size(micIconSize)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusText.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = stateColor.copy(alpha = 0.9f),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .then(
                        if (state == ViewManager.STATE_ERROR) {
                            Modifier.clickable(onClick = onErrorClick)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun BottomBar(
    enabled: Boolean,
    onBg: Color,
    actionLabel: String,
    actionVisual: ViewManager.EnterActionVisual,
    onToggleMode: () -> Unit,
    onInsertSpace: () -> Unit,
    onCursorLeft: () -> Unit,
    onCursorRight: () -> Unit,
    onBackspace: () -> Unit,
    onSettings: () -> Unit,
    onEnter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            icon = Icons.Default.Keyboard,
            enabled = enabled,
            backgroundColor = DarkSurfaceVariant,
            tint = onBg.copy(alpha = if (enabled) 0.7f else 0.35f),
            onClick = onToggleMode
        )

        SpaceBar(
            enabled = enabled,
            onBg = onBg,
            onInsertSpace = onInsertSpace,
            onCursorLeft = onCursorLeft,
            onCursorRight = onCursorRight,
            modifier = Modifier.weight(1f)
        )

        BackspaceButton(
            enabled = enabled,
            onBg = onBg,
            onBackspace = onBackspace
        )

        ControlButton(
            icon = Icons.Default.Settings,
            enabled = enabled,
            backgroundColor = DarkSurfaceVariant,
            tint = onBg.copy(alpha = if (enabled) 0.7f else 0.35f),
            onClick = onSettings
        )

        ActionButton(
            label = actionLabel,
            visual = actionVisual,
            enabled = enabled,
            onBg = onBg,
            onClick = onEnter
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    backgroundColor: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SpaceBar(
    enabled: Boolean,
    onBg: Color,
    onInsertSpace: () -> Unit,
    onCursorLeft: () -> Unit,
    onCursorRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dragStepPx = with(LocalDensity.current) { 18.dp.toPx() }
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onInsertSpace)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f },
                    onDragEnd = { accumulatedDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount

                    while (abs(accumulatedDrag) >= dragStepPx) {
                        if (accumulatedDrag > 0f) {
                            onCursorRight()
                            accumulatedDrag -= dragStepPx
                        } else {
                            onCursorLeft()
                            accumulatedDrag += dragStepPx
                        }
                    }
                }
            }
    ) {
        Text(
            text = "SPACE",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = onBg.copy(alpha = if (enabled) 0.7f else 0.35f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BackspaceButton(
    enabled: Boolean,
    onBg: Color,
    onBackspace: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.45f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        var down = true
                        coroutineScope {
                            val repeatJob = launch {
                                delay(Constants.BackspaceRepeatStartDelay)
                                var repeatDelay = Constants.BackspaceRepeatDelay
                                while (down) {
                                    onBackspace()
                                    delay(repeatDelay)
                                    repeatDelay = (repeatDelay * 85 / 100).coerceAtLeast(20)
                                }
                            }
                            launch {
                                tryAwaitRelease()
                                down = false
                                repeatJob.cancel()
                            }
                        }
                    },
                    onTap = { onBackspace() }
                )
            }
    ) {
        Icon(
            imageVector = Icons.Default.Backspace,
            contentDescription = null,
            tint = onBg.copy(alpha = if (enabled) 0.7f else 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    visual: ViewManager.EnterActionVisual,
    enabled: Boolean,
    onBg: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) DarkSurfaceVariant else DarkSurfaceVariant.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = enterActionIcon(visual),
            contentDescription = label,
            tint = onBg.copy(alpha = if (enabled) 0.78f else 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

internal fun enterActionIcon(visual: ViewManager.EnterActionVisual): ImageVector {
    return when (visual) {
        ViewManager.EnterActionVisual.ENTER -> Icons.Default.KeyboardReturn
        ViewManager.EnterActionVisual.GO -> Icons.Default.ArrowForward
        ViewManager.EnterActionVisual.SEARCH -> Icons.Default.Search
        ViewManager.EnterActionVisual.SEND -> Icons.Default.Send
        ViewManager.EnterActionVisual.NEXT -> Icons.Default.ArrowForward
        ViewManager.EnterActionVisual.DONE -> Icons.Default.Check
        ViewManager.EnterActionVisual.PREVIOUS -> Icons.Default.ArrowBack
    }
}

@Composable
private fun AudioDevicePopup(
    devices: List<AudioDeviceInfo>,
    onDismiss: () -> Unit,
    onDeviceSelected: (AudioDeviceInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = DarkSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.mic_audio_device_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(devices) { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onDeviceSelected(device) }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = device.toIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = device.describe(),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IMETheme(
    prefs: AppPrefs,
    content: @Composable () -> Unit
) {
    val colors = darkColors(
        primary = if (prefs.uiNightForegroundMaterialYou.get()) {
            colorResource(id = R.color.materialYouForeground)
        } else {
            Color(prefs.uiNightForeground.get())
        },
        background = Color(prefs.uiNightBackground.get()),
        surface = DarkSurface,
        onPrimary = Color.White,
        onBackground = Color(0xFFE8E8E8),
        onSurface = Color(0xFFE8E8E8)
    )

    MaterialTheme(
        colors = colors,
        shapes = Shapes,
        content = content
    )
}
