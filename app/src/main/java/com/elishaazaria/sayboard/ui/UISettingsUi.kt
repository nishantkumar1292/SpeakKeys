package com.elishaazaria.sayboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elishaazaria.sayboard.R
import com.elishaazaria.sayboard.data.HapticIntensity
import com.elishaazaria.sayboard.speakKeysPreferenceModel
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.ScrollablePreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference

@Composable
fun UISettingsUi() = ScrollablePreferenceLayout(speakKeysPreferenceModel()) {
    PreferenceGroup(title = stringResource(id = R.string.ui_light_theme_header)) {
        SwitchPreference(
            pref = prefs.uiDayForegroundMaterialYou,
            title = stringResource(id = R.string.ui_light_theme_foreground_material_you_title)
        )

        ColorPickerPreference(
            pref = prefs.uiDayForeground,
            title = stringResource(id = R.string.ui_light_theme_foreground_color_title),
            enabled = !prefs.uiDayForegroundMaterialYou.observeAsState().value
        )

        ColorPickerPreference(
            pref = prefs.uiDayBackground,
            title = stringResource(id = R.string.ui_light_theme_background_color_title)
        )
    }
    PreferenceGroup(title = stringResource(id = R.string.ui_dark_theme_header)) {
        SwitchPreference(
            pref = prefs.uiNightForegroundMaterialYou,
            title = stringResource(id = R.string.ui_dark_theme_foreground_material_you_title)
        )

        ColorPickerPreference(
            pref = prefs.uiNightForeground,
            title = stringResource(id = R.string.ui_dark_theme_foreground_color_title),
            enabled = !prefs.uiNightForegroundMaterialYou.observeAsState().value
        )

        ColorPickerPreference(
            pref = prefs.uiNightBackground,
            title = stringResource(id = R.string.ui_dark_theme_background_color_title)
        )
    }
    PreferenceGroup(title = stringResource(id = R.string.ui_haptic_header)) {
        SwitchPreference(
            pref = prefs.hapticFeedbackEnabled,
            title = stringResource(id = R.string.ui_haptic_enabled_title),
            summary = stringResource(id = R.string.ui_haptic_enabled_summary)
        )

        ListPreference(
            listPref = prefs.hapticFeedbackIntensity,
            title = stringResource(id = R.string.ui_haptic_intensity_title),
            entries = HapticIntensity.listEntries()
        )
    }
}
