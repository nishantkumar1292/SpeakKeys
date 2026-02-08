package com.elishaazaria.sayboard.ui

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elishaazaria.sayboard.R
import com.elishaazaria.sayboard.SettingsActivity
import com.elishaazaria.sayboard.recognition.recognizers.providers.Providers
import com.elishaazaria.sayboard.sayboardPreferenceModel
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

class ModelsSettingsUi(private val activity: SettingsActivity) {
    private val prefs by sayboardPreferenceModel()

    private val recognizerSourceProviders = Providers(activity)

    fun onCreate() {
        reloadModels()
    }

    private fun reloadModels() {
        Log.d(TAG, "Reloading Models")
        val currentModels = prefs.modelsOrder.get().toMutableList()
        val installedModels = recognizerSourceProviders.installedModels()
        currentModels.removeAll { it !in installedModels }
        for (model in installedModels) {
            if (model !in currentModels) {
                currentModels.add(model)
            }
        }
        prefs.modelsOrder.set(currentModels)
    }

    @Composable
    fun Content() {
        val modelOrder by prefs.modelsOrder.observeAsState()

        var modelOrderData by remember {
            mutableStateOf(modelOrder)
        }
        modelOrderData = modelOrder

        val state = rememberReorderableLazyListState(onMove = { from, to ->
            prefs.modelsOrder.set(modelOrderData.toMutableList().apply {
                add(to.index, removeAt(from.index))
            })
        })

        LazyColumn(
            state = state.listState,
            modifier = Modifier
                .fillMaxSize()
                .reorderable(state)
                .detectReorderAfterLongPress(state)
        ) {
            items(modelOrderData, { it.path }) { item ->
                ReorderableItem(
                    state, key = item.path, modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) { isDragging ->
                    val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                    Card(
                        modifier = Modifier
                            .shadow(elevation.value)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name
                                        ?: stringResource(id = R.string.models_model_display_name_null),
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = item.type.name,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            if (modelOrderData.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.mic_error_no_recognizers),
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }

    fun onResume() {
        reloadModels()
    }

    companion object {
        private const val TAG = "ModelsSettingUi"
    }
}
