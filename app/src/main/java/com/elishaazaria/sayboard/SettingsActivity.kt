package com.elishaazaria.sayboard

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.elishaazaria.sayboard.auth.AuthManager
import com.elishaazaria.sayboard.data.ModelType
import com.elishaazaria.sayboard.theme.AppTheme
import com.elishaazaria.sayboard.theme.DarkSurface
import com.elishaazaria.sayboard.theme.Primary
import com.elishaazaria.sayboard.ui.AdvancedSettingsActivity
import com.elishaazaria.sayboard.ui.GrantPermissionUi
import com.elishaazaria.sayboard.ui.ModelsSettingsUi
import com.elishaazaria.sayboard.ui.ModelsTabUi
import com.elishaazaria.sayboard.ui.ProfileTabUi
import com.elishaazaria.sayboard.ui.TestTabUi
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private val micGranted = MutableLiveData<Boolean>(true)
    private val imeGranted = MutableLiveData<Boolean>(true)
    private val signedIn = MutableLiveData(AuthManager.isSignedIn)

    private val modelSettingsUi = ModelsSettingsUi(this)
    private val prefs by speakKeysPreferenceModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        modelSettingsUi.onCreate()

        setContent {
            AppTheme(darkTheme = true) {
                val micGrantedState = micGranted.observeAsState(true)
                val imeGrantedState = imeGranted.observeAsState(true)
                if (micGrantedState.value && imeGrantedState.value) {
                    MainUi()
                } else {
                    GrantPermissionUi(
                        mic = micGrantedState,
                        ime = imeGrantedState,
                        requestMic = {
                            ActivityCompat.requestPermissions(
                                this, arrayOf(
                                    Manifest.permission.RECORD_AUDIO
                                ), PERMISSIONS_REQUEST_RECORD_AUDIO
                            )
                        },
                        requestIme = {
                            startActivity(Intent("android.settings.INPUT_METHOD_SETTINGS"))
                        },
                        onSignIn = {
                            lifecycleScope.launch {
                                if (AuthManager.signIn(this@SettingsActivity)) {
                                    signedIn.postValue(true)
                                    modelSettingsUi.onResume()
                                }
                            }
                        },
                        onSkipSignIn = {
                            checkPermissions()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun MainUi() {
        val tabs = listOf(
            stringResource(id = R.string.tab_test),
            stringResource(id = R.string.tab_models),
            stringResource(id = R.string.tab_profile)
        )
        var selectedIndex by remember { mutableIntStateOf(0) }

        Scaffold(bottomBar = {
            BottomNavigation(
                modifier = Modifier.navigationBarsPadding(),
                backgroundColor = DarkSurface,
                contentColor = MaterialTheme.colors.onSurface
            ) {
                tabs.forEachIndexed { index, tab ->
                    BottomNavigationItem(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        selectedContentColor = Primary,
                        unselectedContentColor = Color.Gray,
                        icon = {
                            when (index) {
                                0 -> Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null
                                )
                                1 -> Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )
                                2 -> Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null
                                )
                            }
                        },
                        label = { Text(text = tab) }
                    )
                }
            }
        }) {
            Box(
                modifier = Modifier
                    .padding(it)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                val isSignedIn = signedIn.observeAsState(AuthManager.isSignedIn)
                val selectedEngine by prefs.selectedEngine.observeAsState()
                val sarvamKey by prefs.sarvamApiKey.observeAsState()
                when (selectedIndex) {
                    0 -> {
                        val micOk = micGranted.observeAsState(true).value
                        val imeOk = imeGranted.observeAsState(true).value
                        val isActive = micOk && imeOk && (isSignedIn.value || sarvamKey.isNotEmpty())
                        val modelName = when (selectedEngine) {
                            "sarvam" -> getString(R.string.engine_sarvam) + " (API Key)"
                            else -> getString(R.string.engine_speakkeys_auto) + " (Sarvam)"
                        }
                        val needsAuth = !isSignedIn.value && sarvamKey.isEmpty()
                        TestTabUi(
                            isActive = isActive,
                            needsAuth = needsAuth,
                            currentModelName = modelName,
                            onNavigateToModels = { selectedIndex = 1 }
                        )
                    }
                    1 -> {
                        val whisperLang by prefs.whisperLanguage.observeAsState()
                        val whisperPrmt by prefs.whisperPrompt.observeAsState()
                        val whisperTranslit by prefs.whisperTransliterateToRoman.observeAsState()
                        val sarvamModeVal by prefs.sarvamMode.observeAsState()
                        val sarvamLangVal by prefs.sarvamLanguage.observeAsState()

                        ModelsTabUi(
                            selectedEngine = selectedEngine,
                            isSignedIn = isSignedIn.value,
                            hasSarvamKey = sarvamKey.isNotEmpty(),
                            onSelectEngine = { engine ->
                                prefs.selectedEngine.set(engine)
                                val currentModels = prefs.modelsOrder.get().toMutableList()
                                val priorityTypes = when (engine) {
                                    "sarvam" -> setOf(ModelType.SarvamCloud)
                                    else -> setOf(ModelType.ProxiedSarvamCloud)
                                }
                                val prioritized = currentModels.filter { m -> m.type in priorityTypes }
                                val rest = currentModels.filter { m -> m.type !in priorityTypes }
                                prefs.modelsOrder.set(prioritized + rest)
                                prefs.lastSelectedModelPath.set(
                                    when (engine) {
                                        "sarvam" -> "sarvam://cloud"
                                        else -> "proxied://sarvam"
                                    }
                                )
                            },
                            onNavigateToProfile = { selectedIndex = 2 },
                            whisperLanguage = whisperLang,
                            onWhisperLanguageChange = { prefs.whisperLanguage.set(it) },
                            whisperPrompt = whisperPrmt,
                            onWhisperPromptChange = { prefs.whisperPrompt.set(it) },
                            whisperTransliterate = whisperTranslit,
                            onWhisperTransliterateChange = { prefs.whisperTransliterateToRoman.set(it) },
                            sarvamApiKey = sarvamKey,
                            onSarvamApiKeyChange = { prefs.sarvamApiKey.set(it) },
                            sarvamMode = sarvamModeVal,
                            onSarvamModeChange = { prefs.sarvamMode.set(it) },
                            sarvamLanguage = sarvamLangVal,
                            onSarvamLanguageChange = { prefs.sarvamLanguage.set(it) }
                        )
                    }
                    2 -> {
                        ProfileTabUi(
                            isSignedIn = isSignedIn.value,
                            onSignIn = {
                                lifecycleScope.launch {
                                    if (AuthManager.signIn(this@SettingsActivity)) {
                                        signedIn.postValue(true)
                                        modelSettingsUi.onResume()
                                    }
                                }
                            },
                            onSignOut = {
                                lifecycleScope.launch {
                                    AuthManager.signOut(this@SettingsActivity)
                                    signedIn.postValue(false)
                                    modelSettingsUi.onResume()
                                }
                            },
                            onAdvancedSettings = {
                                startActivity(
                                    Intent(this@SettingsActivity, AdvancedSettingsActivity::class.java)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        micGranted.postValue(Tools.isMicrophonePermissionGranted(this))
        imeGranted.postValue(Tools.isIMEEnabled(this))
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        modelSettingsUi.onResume()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
    }
}
