package com.elishaazaria.sayboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elishaazaria.sayboard.R

@Composable
public fun GrantPermissionUi(
    mic: State<Boolean>,
    ime: State<Boolean>,
    requestMic: () -> Unit,
    requestIme: () -> Unit
) {
    val brandBlue = Color(0xFF1F5DD7)
    val brandBlueLight = Color(0xFFE7F0FF)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(brandBlueLight, Color.White)))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.setup_title),
                color = brandBlue,
                style = MaterialTheme.typography.h6
            )
            Text(
                text = stringResource(id = R.string.setup_reason),
                textAlign = TextAlign.Center,
                color = Color(0xFF24406B)
            )
            Text(
                text = stringResource(id = R.string.setup_step_1),
                textAlign = TextAlign.Center,
                color = Color(0xFF24406B)
            )
            Text(
                text = stringResource(id = R.string.setup_step_2),
                textAlign = TextAlign.Center,
                color = Color(0xFF24406B)
            )
            Text(
                text = stringResource(id = R.string.setup_data_path),
                textAlign = TextAlign.Center,
                color = Color(0xFF24406B)
            )
            Button(
                onClick = requestMic,
                enabled = !mic.value,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = brandBlue,
                    contentColor = Color.White,
                    disabledBackgroundColor = Color(0xFF9DBAF2),
                    disabledContentColor = Color.White
                )
            ) {
                if (mic.value) {
                    Text(text = stringResource(id = R.string.mic_permission_granted))
                } else {
                    Text(text = stringResource(id = R.string.mic_permission_not_granted))
                }
            }
            Button(
                onClick = requestIme,
                enabled = !ime.value,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = brandBlue,
                    contentColor = Color.White,
                    disabledBackgroundColor = Color(0xFF9DBAF2),
                    disabledContentColor = Color.White
                )
            ) {
                if (ime.value) {
                    Text(text = stringResource(id = R.string.keyboard_enabled))
                } else {
                    Text(text = stringResource(id = R.string.keyboard_not_enabled))
                }
            }
        }
    }
}