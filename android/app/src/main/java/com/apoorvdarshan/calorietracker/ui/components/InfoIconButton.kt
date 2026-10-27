package com.apoorvdarshan.calorietracker.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R

/**
 * A small ⓘ button that explains an option in a dialog, so a choice like "Maintain / Recomp"
 * can be understood without leaving the screen. Tapping it never selects the option.
 */
@Composable
fun InfoIconButton(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(36.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.info_button_a11y, title),
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(body, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.info_button_ok)) } }
        )
    }
}
