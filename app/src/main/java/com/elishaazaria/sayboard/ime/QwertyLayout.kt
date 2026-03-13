package com.elishaazaria.sayboard.ime

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ShiftState { LOWER, SHIFTED, CAPS_LOCK }
private enum class SymbolPage { LETTERS, SYMBOLS_1, SYMBOLS_2 }

private val LETTERS_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
private val LETTERS_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
private val LETTERS_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m")

private val SYMBOLS1_ROW_1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val SYMBOLS1_ROW_2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
private val SYMBOLS1_ROW_3 = listOf("*", "\"", "'", ":", ";", "!", "?")

private val SYMBOLS2_ROW_1 = listOf("~", "`", "|", "^", "<", ">", "{", "}")
private val SYMBOLS2_ROW_2 = listOf("[", "]", "\\", "/", "_", "=")
private val SYMBOLS2_ROW_3 = listOf("€", "£", "¥", "•", "°")

@Composable
fun QwertyLayout(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onCursorLeft: () -> Unit,
    onCursorRight: () -> Unit,
    onInsertSpace: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    actionLabel: String,
    actionVisual: ViewManager.EnterActionVisual,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shiftState by remember { mutableStateOf(ShiftState.LOWER) }
    var symbolPage by remember { mutableStateOf(SymbolPage.LETTERS) }

    val view = LocalView.current
    val hapticFeedback: () -> Unit = {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val onChar: (String) -> Unit = { text ->
        hapticFeedback()
        val output = when {
            symbolPage != SymbolPage.LETTERS -> text
            shiftState == ShiftState.LOWER -> text.lowercase()
            else -> text.uppercase()
        }
        onKeyPress(output)
        if (shiftState == ShiftState.SHIFTED) {
            shiftState = ShiftState.LOWER
        }
    }

    val keyBg = MaterialTheme.colors.onBackground.copy(alpha = 0.08f)
    val specialKeyBg = MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
    val textColor = MaterialTheme.colors.onBackground

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        when (symbolPage) {
            SymbolPage.LETTERS -> {
                // Row 1: q w e r t y u i o p
                KeyRow(weight = 1f) {
                    for (key in LETTERS_ROW_1) {
                        CharKey(key, shiftState != ShiftState.LOWER, keyBg, textColor, onChar)
                    }
                }
                // Row 2: a s d f g h j k l
                KeyRow(weight = 1f) {
                    for (key in LETTERS_ROW_2) {
                        CharKey(key, shiftState != ShiftState.LOWER, keyBg, textColor, onChar)
                    }
                }
                // Row 3: [Shift] z x c v b n m [Backspace]
                KeyRow(weight = 1f) {
                    SpecialKey(
                        label = when (shiftState) {
                            ShiftState.LOWER -> "⇧"
                            ShiftState.SHIFTED -> "⇧"
                            ShiftState.CAPS_LOCK -> "⇪"
                        },
                        bg = if (shiftState != ShiftState.LOWER) MaterialTheme.colors.primary.copy(alpha = 0.25f) else specialKeyBg,
                        textColor = textColor,
                        weight = 1.5f
                    ) {
                        hapticFeedback()
                        shiftState = when (shiftState) {
                            ShiftState.LOWER -> ShiftState.SHIFTED
                            ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
                            ShiftState.CAPS_LOCK -> ShiftState.LOWER
                        }
                    }
                    for (key in LETTERS_ROW_3) {
                        CharKey(key, shiftState != ShiftState.LOWER, keyBg, textColor, onChar)
                    }
                    BackspaceKey(specialKeyBg, textColor, 1.5f, onBackspace, hapticFeedback)
                }
                // Row 4: [?123] [,] [←] [→] [.]
                KeyRow(weight = 1f) {
                    SpecialKey(label = "?123", bg = specialKeyBg, textColor = textColor, weight = 1.5f) {
                        hapticFeedback()
                        symbolPage = SymbolPage.SYMBOLS_1
                    }
                    CharKey(",", false, keyBg, textColor, onChar)
                    CursorKey(Icons.Default.KeyboardArrowLeft, specialKeyBg, textColor, 1f, onCursorLeft, hapticFeedback)
                    CursorKey(Icons.Default.KeyboardArrowRight, specialKeyBg, textColor, 1f, onCursorRight, hapticFeedback)
                    CharKey(".", false, keyBg, textColor, onChar)
                }
                BottomUtilityRow(
                    keyBg = keyBg,
                    specialKeyBg = specialKeyBg,
                    textColor = textColor,
                    actionLabel = actionLabel,
                    actionVisual = actionVisual,
                    onInsertSpace = {
                        hapticFeedback()
                        onInsertSpace()
                    },
                    onToggleVoiceMode = {
                        hapticFeedback()
                        onToggleVoiceMode()
                    },
                    onEnter = {
                        hapticFeedback()
                        onEnter()
                    }
                )
            }
            SymbolPage.SYMBOLS_1 -> {
                KeyRow(weight = 1f) {
                    for (key in SYMBOLS1_ROW_1) {
                        CharKey(key, false, keyBg, textColor, onChar)
                    }
                }
                KeyRow(weight = 1f) {
                    for (key in SYMBOLS1_ROW_2) {
                        CharKey(key, false, keyBg, textColor, onChar)
                    }
                }
                KeyRow(weight = 1f) {
                    SpecialKey(label = "#+=", bg = specialKeyBg, textColor = textColor, weight = 1.5f) {
                        hapticFeedback()
                        symbolPage = SymbolPage.SYMBOLS_2
                    }
                    for (key in SYMBOLS1_ROW_3) {
                        CharKey(key, false, keyBg, textColor, onChar)
                    }
                    BackspaceKey(specialKeyBg, textColor, 1.5f, onBackspace, hapticFeedback)
                }
                KeyRow(weight = 1f) {
                    SpecialKey(label = "ABC", bg = specialKeyBg, textColor = textColor, weight = 1.5f) {
                        hapticFeedback()
                        symbolPage = SymbolPage.LETTERS
                    }
                    CharKey(",", false, keyBg, textColor, onChar)
                    CursorKey(Icons.Default.KeyboardArrowLeft, specialKeyBg, textColor, 1f, onCursorLeft, hapticFeedback)
                    CursorKey(Icons.Default.KeyboardArrowRight, specialKeyBg, textColor, 1f, onCursorRight, hapticFeedback)
                    CharKey(".", false, keyBg, textColor, onChar)
                }
                BottomUtilityRow(
                    keyBg = keyBg,
                    specialKeyBg = specialKeyBg,
                    textColor = textColor,
                    actionLabel = actionLabel,
                    actionVisual = actionVisual,
                    onInsertSpace = {
                        hapticFeedback()
                        onInsertSpace()
                    },
                    onToggleVoiceMode = {
                        hapticFeedback()
                        onToggleVoiceMode()
                    },
                    onEnter = {
                        hapticFeedback()
                        onEnter()
                    }
                )
            }
            SymbolPage.SYMBOLS_2 -> {
                KeyRow(weight = 1f) {
                    for (key in SYMBOLS2_ROW_1) {
                        CharKey(key, false, keyBg, textColor, onChar)
                    }
                }
                KeyRow(weight = 1f) {
                    for (key in SYMBOLS2_ROW_2) {
                        CharKey(key, false, keyBg, textColor, onChar)
                    }
                }
                KeyRow(weight = 1f) {
                    SpecialKey(label = "123", bg = specialKeyBg, textColor = textColor, weight = 1.5f) {
                        hapticFeedback()
                        symbolPage = SymbolPage.SYMBOLS_1
                    }
                    for (key in SYMBOLS2_ROW_3) {
                        CharKey(key, false, keyBg, textColor, onChar)
                    }
                    BackspaceKey(specialKeyBg, textColor, 1.5f, onBackspace, hapticFeedback)
                }
                KeyRow(weight = 1f) {
                    SpecialKey(label = "ABC", bg = specialKeyBg, textColor = textColor, weight = 1.5f) {
                        hapticFeedback()
                        symbolPage = SymbolPage.LETTERS
                    }
                    CharKey(",", false, keyBg, textColor, onChar)
                    CursorKey(Icons.Default.KeyboardArrowLeft, specialKeyBg, textColor, 1f, onCursorLeft, hapticFeedback)
                    CursorKey(Icons.Default.KeyboardArrowRight, specialKeyBg, textColor, 1f, onCursorRight, hapticFeedback)
                    CharKey(".", false, keyBg, textColor, onChar)
                }
                BottomUtilityRow(
                    keyBg = keyBg,
                    specialKeyBg = specialKeyBg,
                    textColor = textColor,
                    actionLabel = actionLabel,
                    actionVisual = actionVisual,
                    onInsertSpace = {
                        hapticFeedback()
                        onInsertSpace()
                    },
                    onToggleVoiceMode = {
                        hapticFeedback()
                        onToggleVoiceMode()
                    },
                    onEnter = {
                        hapticFeedback()
                        onEnter()
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(
    weight: Float,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(weight)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
private fun RowScope.CharKey(
    key: String,
    uppercase: Boolean,
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onChar: (String) -> Unit
) {
    val display = if (uppercase) key.uppercase() else key
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(key) {
                detectTapGestures(onTap = { onChar(key) })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = display,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.SpecialKey(
    label: String,
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    weight: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(label) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.BackspaceKey(
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    weight: Float,
    onBackspace: () -> Unit,
    hapticFeedback: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        hapticFeedback()
                        var down = true
                        coroutineScope {
                            val repeatJob = launch {
                                delay(400)
                                var repeatDelay = 80L
                                while (down) {
                                    onBackspace()
                                    hapticFeedback()
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
                    onTap = {
                        onBackspace()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Backspace,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RowScope.CursorKey(
    icon: ImageVector,
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    weight: Float,
    onClick: () -> Unit,
    hapticFeedback: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        hapticFeedback()
                        var down = true
                        coroutineScope {
                            val repeatJob = launch {
                                delay(400)
                                while (down) {
                                    onClick()
                                    hapticFeedback()
                                    delay(80)
                                }
                            }
                            launch {
                                tryAwaitRelease()
                                down = false
                                repeatJob.cancel()
                            }
                        }
                    },
                    onTap = {
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ColumnScope.BottomUtilityRow(
    keyBg: androidx.compose.ui.graphics.Color,
    specialKeyBg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    actionLabel: String,
    actionVisual: ViewManager.EnterActionVisual,
    onInsertSpace: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onEnter: () -> Unit
) {
    KeyRow(weight = 1f) {
        IconKey(
            icon = Icons.Default.Mic,
            bg = specialKeyBg,
            tint = textColor.copy(alpha = 0.7f),
            weight = 1.4f,
            onClick = onToggleVoiceMode
        )
        SpaceKey(
            bg = keyBg,
            textColor = textColor,
            weight = 4.6f,
            onClick = onInsertSpace
        )
        ActionKey(
            label = actionLabel,
            visual = actionVisual,
            bg = specialKeyBg,
            textColor = textColor,
            weight = 1.4f,
            onClick = onEnter
        )
    }
}

@Composable
private fun RowScope.IconKey(
    icon: ImageVector,
    bg: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    weight: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(icon) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )
    }
}

@Composable
private fun RowScope.SpaceKey(
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    weight: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "space",
            color = textColor.copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RowScope.ActionKey(
    label: String,
    visual: ViewManager.EnterActionVisual,
    bg: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    weight: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .pointerInput(label) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = enterActionIcon(visual),
            contentDescription = label,
            tint = textColor.copy(alpha = 0.75f)
        )
    }
}
