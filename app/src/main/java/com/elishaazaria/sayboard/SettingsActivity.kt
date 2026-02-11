package com.elishaazaria.sayboard

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.elishaazaria.sayboard.theme.AppTheme
import com.elishaazaria.sayboard.ui.GrantPermissionUi
import com.elishaazaria.sayboard.ui.KeyboardSettingsUi
import com.elishaazaria.sayboard.ui.LogicSettingsUi
import com.elishaazaria.sayboard.ui.ModelsSettingsUi
import com.elishaazaria.sayboard.ui.ApiSettingsUi
import com.elishaazaria.sayboard.ui.UISettingsUi

class SettingsActivity : ComponentActivity() {

    private val micGranted = MutableLiveData<Boolean>(true)
    private val imeGranted = MutableLiveData<Boolean>(true)

    private val modelSettingsUi = ModelsSettingsUi(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        modelSettingsUi.onCreate()

        setContent {
            AppTheme {
                val micGrantedState = micGranted.observeAsState(true)
                val imeGrantedState = imeGranted.observeAsState(true)
                if (micGrantedState.value && imeGrantedState.value) {
                    MainUi()
                } else {
                    GrantPermissionUi(mic = micGrantedState, ime = imeGrantedState, requestMic = {
                        ActivityCompat.requestPermissions(
                            this, arrayOf(
                                Manifest.permission.RECORD_AUDIO
                            ), PERMISSIONS_REQUEST_RECORD_AUDIO
                        )
                    }) {
                        startActivity(Intent("android.settings.INPUT_METHOD_SETTINGS"))
                    }
                }
            }
        }
    }

    @Composable
    private fun MainUi() {
        val tabs = listOf<String>(
            stringResource(id = R.string.title_models),
            stringResource(id = R.string.title_ui),
            stringResource(id = R.string.title_keyboard),
            stringResource(id = R.string.title_logic),
            stringResource(id = R.string.title_api)
        )
        var selectedIndex by remember {
            mutableIntStateOf(0)
        }

        Scaffold(bottomBar = {
            BottomNavigation() {
                tabs.forEachIndexed { index, tab ->
                    BottomNavigationItem(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        icon = {
                            when (index) {
                                0 -> Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null
                                )

                                1 -> Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_color_lens_24),
                                    contentDescription = null
                                )

                                2 -> Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null
                                )

                                3 -> Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )

                                4 -> Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null
                                )
                            }
                        }, label = {
                            Text(text = tab)
                        })
                }
            }
        }) {
            Box(
                modifier = Modifier
                    .padding(it)
                    .statusBarsPadding()
                    .padding(10.dp)
            ) {
                when (selectedIndex) {
                    0 -> modelSettingsUi.Content()
                    1 -> UISettingsUi()
                    2 -> KeyboardSettingsUi()
                    3 -> LogicSettingsUi(this@SettingsActivity)
                    4 -> ApiSettingsUi()
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
