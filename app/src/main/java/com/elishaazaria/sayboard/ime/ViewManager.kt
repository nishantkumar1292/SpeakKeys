package com.elishaazaria.sayboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.util.Log
import android.view.inputmethod.EditorInfo
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.lightColors
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.elishaazaria.sayboard.AppPrefs
import com.elishaazaria.sayboard.Constants
import com.elishaazaria.sayboard.R
import com.elishaazaria.sayboard.recognition.recognizers.RecognizerState
import com.elishaazaria.sayboard.sayboardPreferenceModel
import com.elishaazaria.sayboard.theme.DarkSurfaceVariant
import com.elishaazaria.sayboard.theme.ErrorRed
import com.elishaazaria.sayboard.theme.ListeningBlue
import com.elishaazaria.sayboard.theme.Shapes
import com.elishaazaria.sayboard.ui.utils.MyIconButton
import com.elishaazaria.sayboard.ui.utils.MyTextButton
import com.elishaazaria.sayboard.utils.AudioDevices
import com.elishaazaria.sayboard.utils.describe
import com.elishaazaria.sayboard.utils.toIcon
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class ViewManager(private val ime: Context) : AbstractComposeView(ime),
    Observer<RecognizerState> {
    private val prefs by sayboardPreferenceModel()
    val stateLD = MutableLiveData(STATE_INITIAL)
    val errorMessageLD = MutableLiveData(R.string.mic_info_error)
    private var listener: Listener? = null
    val recognizerNameLD = MutableLiveData("")
    val enterActionLD = MutableLiveData(EditorInfo.IME_ACTION_UNSPECIFIED)

    val recordDevice: MutableLiveData<AudioDeviceInfo?> = MutableLiveData()

    private var devices: List<AudioDeviceInfo> = listOf()

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterialApi::class)
    @Composable
    override fun Content() {
        val stateS = stateLD.observeAsState()
        val errorMessageS = errorMessageLD.observeAsState(R.string.mic_info_error)
        val recognizerNameS = recognizerNameLD.observeAsState(initial = "")
        val height =
            (LocalConfiguration.current.screenHeightDp * when (LocalConfiguration.current.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> prefs.keyboardHeightLandscape.get()
                else -> prefs.keyboardHeightPortrait.get()
            }).toInt().dp
        var showDevicesPopup by remember { mutableStateOf(false) }
        val recordDeviceS by recordDevice.observeAsState()

        IMETheme(prefs) {
            val primary = MaterialTheme.colors.primary
            val bg = MaterialTheme.colors.background
            val onBg = MaterialTheme.colors.onBackground
            val surface = MaterialTheme.colors.surface
            val isDark = isSystemInDarkTheme()

            // Animated accent color based on state
            val stateColor by animateColorAsState(
                targetValue = when (stateS.value) {
                    STATE_LISTENING -> ListeningBlue
                    STATE_ERROR -> ErrorRed
                    else -> primary
                },
                animationSpec = tween(300),
                label = "stateColor"
            )

            CompositionLocalProvider(
                LocalContentColor provides onBg
            ) {
                // Outer container with rounded top corners
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(bg)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ── Top row: back, custom keys, backspace ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(onClick = { listener?.backClicked() }) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardHide,
                                    contentDescription = null,
                                    tint = onBg.copy(alpha = 0.7f)
                                )
                            }
                            val topKeys by prefs.keyboardKeysTop.observeAsState()
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                for (key in topKeys) {
                                    MyTextButton(onClick = { listener?.buttonClicked(key.text) }) {
                                        Text(
                                            text = key.label,
                                            color = onBg.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(onPress = {
                                            var down = true;
                                            coroutineScope {
                                                val repeatJob = launch {
                                                    delay(Constants.BackspaceRepeatStartDelay)
                                                    while (down) {
                                                        listener?.backspaceClicked()
                                                        delay(Constants.BackspaceRepeatDelay)
                                                    }
                                                }
                                                launch {
                                                    val released = tryAwaitRelease();
                                                    down = false;
                                                    Log.d("ViewManager", "$released")
                                                    repeatJob.cancel()
                                                }
                                            }
                                        }, onTap = {
                                            listener?.backspaceClicked()
                                        })
                                    }
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(onDragStart = {
                                            listener?.backspaceTouchStart(it)
                                        }, onDragCancel = {
                                            listener?.backspaceTouchEnd()
                                        }, onDragEnd = {
                                            listener?.backspaceTouchEnd()
                                        }, onHorizontalDrag = { change, amount ->
                                            listener?.backspaceTouched(change, amount)
                                        })
                                    }
                                    .minimumInteractiveComponentSize()
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val contentAlpha = LocalContentAlpha.current
                                CompositionLocalProvider(LocalContentAlpha provides contentAlpha) {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = null,
                                        tint = onBg.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // ── Middle section: side keys + mic ──
                        Row(modifier = Modifier.weight(1f)) {
                            val leftKeys by prefs.keyboardKeysLeft.observeAsState()
                            FlowColumn(
                                modifier = Modifier.padding(start = 4.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                for (key in leftKeys) {
                                    MyTextButton(onClick = { listener?.buttonClicked(key.text) }) {
                                        Text(
                                            text = key.label,
                                            color = onBg.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // Center: mic button + status
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Mic button with circular background
                                val micButtonSize = 80.dp

                                // Pulse animation for listening state
                                val pulseScale = if (stateS.value == STATE_LISTENING) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
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

                                MyIconButton(
                                    onClick = { listener?.micClick() },
                                    onLongClick = { listener?.micLongClick() },
                                    modifier = Modifier
                                        .size(micButtonSize)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(
                                            stateColor.copy(alpha = if (isDark) 0.15f else 0.1f),
                                            CircleShape
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = stateColor.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = when (stateS.value) {
                                            STATE_INITIAL, STATE_LOADING -> Icons.Default.SettingsVoice
                                            STATE_READY, STATE_PAUSED -> Icons.Default.MicNone
                                            STATE_LISTENING -> Icons.Default.Mic
                                            else -> Icons.Default.MicOff
                                        },
                                        contentDescription = null,
                                        tint = stateColor,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Status text
                                Text(
                                    text = when (stateS.value) {
                                        STATE_INITIAL, STATE_LOADING -> stringResource(id = R.string.mic_info_preparing)
                                        STATE_READY, STATE_PAUSED -> stringResource(id = R.string.mic_info_ready)
                                        STATE_LISTENING -> stringResource(id = R.string.mic_info_recording)
                                        else -> stringResource(id = errorMessageS.value)
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = stateColor.copy(alpha = 0.9f)
                                )
                            }

                            val rightKeys by prefs.keyboardKeysRight.observeAsState()
                            FlowColumn(
                                modifier = Modifier.padding(end = 4.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                for (key in rightKeys) {
                                    MyTextButton(onClick = { listener?.buttonClicked(key.text) }) {
                                        Text(
                                            text = key.label,
                                            color = onBg.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // ── Bottom bar ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            // Settings
                            IconButton(
                                onClick = { listener?.settingsClicked() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = onBg.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Model chip
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isDark) DarkSurfaceVariant
                                        else Color(0xFFE0E0E0)
                                    )
                                    .clickable { listener?.modelClicked() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = recognizerNameS.value,
                                        fontSize = 12.sp,
                                        color = onBg.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Audio device
                            IconButton(
                                onClick = {
                                    devices = AudioDevices.validAudioDevices(ime)
                                    showDevicesPopup = true
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = recordDeviceS?.toIcon()
                                        ?: Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = onBg.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Enter/Return
                            IconButton(
                                onClick = { listener?.returnClicked() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                val enterAction by enterActionLD.observeAsState()
                                Icon(
                                    imageVector = when (enterAction) {
                                        EditorInfo.IME_ACTION_GO -> Icons.Default.ArrowRightAlt
                                        EditorInfo.IME_ACTION_SEARCH -> Icons.Default.Search
                                        EditorInfo.IME_ACTION_SEND -> Icons.Default.Send
                                        EditorInfo.IME_ACTION_NEXT -> Icons.Default.NavigateNext
                                        EditorInfo.IME_ACTION_PREVIOUS -> Icons.Default.NavigateBefore
                                        else -> Icons.Default.KeyboardReturn
                                    },
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // ── Audio device popup overlay ──
                    if (showDevicesPopup) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(0.6f))
                                .clickable { showDevicesPopup = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = if (isDark) DarkSurfaceVariant else Color.White,
                                modifier = Modifier
                                    .fillMaxSize(0.8f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(id = R.string.mic_audio_device_title),
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
                                            Card(
                                                onClick = {
                                                    showDevicesPopup = false
                                                    recordDevice.postValue(device)
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                backgroundColor = if (isDark)
                                                    Color.White.copy(alpha = 0.08f)
                                                else
                                                    Color.Black.copy(alpha = 0.05f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = device.toIcon(),
                                                        contentDescription = null,
                                                        tint = primary
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        device.describe(),
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onChanged(value: RecognizerState) {
        when (value) {
            RecognizerState.CLOSED, RecognizerState.NONE -> stateLD.setValue(STATE_INITIAL)

            RecognizerState.LOADING -> stateLD.setValue(STATE_LOADING)
            RecognizerState.READY -> stateLD.setValue(STATE_READY)
            RecognizerState.IN_RAM -> stateLD.setValue(STATE_PAUSED)
            RecognizerState.ERROR -> stateLD.setValue(STATE_ERROR)
        }
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    interface Listener {
        fun micClick()
        fun micLongClick(): Boolean
        fun backClicked()
        fun backspaceClicked()
        fun backspaceTouchStart(offset: Offset)
        fun backspaceTouched(change: PointerInputChange, dragAmount: Float)
        fun backspaceTouchEnd()
        fun returnClicked()
        fun modelClicked()
        fun settingsClicked()
        fun buttonClicked(text: String)
        fun deviceChanged(device: AudioDeviceInfo)
    }

    companion object {
        const val STATE_INITIAL = 0
        const val STATE_LOADING = 1
        const val STATE_READY = 2 // model loaded, ready to start
        const val STATE_LISTENING = 3
        const val STATE_PAUSED = 4
        const val STATE_ERROR = 5
    }
}

@Composable
fun IMETheme(
    prefs: AppPrefs,
    content: @Composable () -> Unit
) {
    val colors = if (isSystemInDarkTheme()) {
        darkColors(
            background = Color(prefs.uiNightBackground.get()),
            primary = if (prefs.uiNightForegroundMaterialYou.get()) {
                colorResource(id = R.color.materialYouForeground)
            } else {
                Color(prefs.uiNightForeground.get())
            },
        )
    } else {
        lightColors(
            background = Color(prefs.uiDayBackground.get()),
            primary = if (prefs.uiDayForegroundMaterialYou.get()) {
                colorResource(id = R.color.materialYouForeground)
            } else {
                Color(prefs.uiDayForeground.get())
            },
        )
    }

    MaterialTheme(
        colors = colors,
        shapes = Shapes,
        content = content,
    )
}
