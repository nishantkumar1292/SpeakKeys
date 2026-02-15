package com.elishaazaria.sayboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.elishaazaria.sayboard.data.KeepScreenAwakeMode
import com.elishaazaria.sayboard.utils.KeysListSerializer
import com.elishaazaria.sayboard.utils.ModelListSerializer
import com.elishaazaria.sayboard.utils.leftDefaultKeysList
import com.elishaazaria.sayboard.utils.rightDefaultKeysList
import com.elishaazaria.sayboard.utils.topDefaultKeysList
import dev.patrickgold.jetpref.datastore.JetPref
import dev.patrickgold.jetpref.datastore.model.PreferenceModel

// Defining a getter function for easy retrieval of the AppPrefs model.
// You can name this however you want, the convention is <projectName>PreferenceModel
fun speakKeysPreferenceModel() = JetPref.getOrCreatePreferenceModel(AppPrefs::class, ::AppPrefs)

// Defining a preference model for our app prefs
// The name we give here is the file name of the preferences and is saved
// within the app's `jetpref_datastore` directory.
class AppPrefs : PreferenceModel("example-app-preferences") {
    val modelsOrder = custom(
        key = "sl_models_order",
        default = listOf(),
        serializer = ModelListSerializer()
    )

    val logicKeepScreenAwake = enum(
        key = "e_keep_screen_awake",
        default = KeepScreenAwakeMode.NEVER
    )

    val logicListenImmediately = boolean(
        key = "b_listen_immediately",
        default = false
    )

    val logicAutoSwitchBack = boolean(
        key = "b_auto_switch_back_ime",
        default = false
    )

    val logicKeepModelInRam = boolean(
        key = "b_keep_model_in_ram",
        default = false
    )

    val logicAutoCapitalize = boolean(
        key = "b_auto_capitalize",
        default = true
    )

    val logicDefaultIME = string(
        key = "s_default_keyboard",
        default = "",
    )

    val logicReturnToDefaultIME = boolean(
        key = "b_always_return_default_keyboard",
        default = false
    )

    val keyboardHeightPortrait = float(
        key = "f_keyboard_height_portrait",
        default = 0.3f
    )

    val keyboardHeightLandscape = float(
        key = "f_keyboard_height_landscape",
        default = 0.5f
    )

    val keyboardKeysTop = custom(
        key = "sl_keyboard_keys_top",
        default = topDefaultKeysList,
        serializer = KeysListSerializer()
    )

    val keyboardKeysLeft = custom(
        key = "sl_keyboard_keys_left",
        default = leftDefaultKeysList,
        serializer = KeysListSerializer()
    )

    val keyboardKeysRight = custom(
        key = "sl_keyboard_keys_right",
        default = rightDefaultKeysList,
        serializer = KeysListSerializer()
    )

    val uiDayForegroundMaterialYou = boolean(
        key = "b_day_foreground_material_you",
        default = false
    )
    val uiDayForeground = int(
        key = "c_day_foreground_color",
        default = Color(0xFF1F5DD7).toArgb()
    )
    val uiDayBackground = int(
        key = "c_day_background_color",
        default = Color(0xFFFFFFFF).toArgb()
    )

    val uiNightForegroundMaterialYou = boolean(
        key = "b_night_foreground_material_you",
        default = false
    )
    val uiNightForeground = int(
        key = "c_night_foreground_color",
        default = Color(0xFF4A82E8).toArgb()
    )
    val uiNightBackground = int(
        key = "c_night_background_color",
        default = Color(0xFF111C39).toArgb()
    )

    val lastSelectedModelPath = string(
        key = "s_last_selected_model_path",
        default = ""
    )

    // Whisper Cloud settings
    val whisperLanguage = string(
        key = "s_whisper_language",
        default = ""  // Empty = auto-detect
    )

    // Prompt to guide transcription style (e.g., for Romanized Hindi/Hinglish output)
    // A prompt with Romanized Hindi examples helps Whisper output in that style
    val whisperPrompt = string(
        key = "s_whisper_prompt",
        default = "Yeh ek Hindi sentence hai jo Roman script mein likha gaya hai. Main aapko batana chahta hoon ki aaj mausam bahut achha hai."
    )

    // Enable built-in Devanagari to Roman transliteration
    // When enabled, Hindi output like "नमस्ते" becomes "namaste"
    val whisperTransliterateToRoman = boolean(
        key = "b_whisper_transliterate_to_roman",
        default = false
    )

    // API Keys (user-configured at runtime)
    val openaiApiKey = string(
        key = "s_openai_api_key",
        default = ""
    )

    val sarvamApiKey = string(
        key = "s_sarvam_api_key",
        default = ""
    )

    // Sarvam settings
    val sarvamMode = string(
        key = "s_sarvam_mode",
        default = "translit"
    )

    val sarvamLanguage = string(
        key = "s_sarvam_language",
        default = "unknown"
    )
}