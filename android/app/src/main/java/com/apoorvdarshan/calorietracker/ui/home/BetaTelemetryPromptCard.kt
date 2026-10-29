package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.FudGlassTextButton
import com.apoorvdarshan.calorietracker.ui.components.FudIconBubble

/** Home's one-time question about the beta's anonymous usage and error log. */
@Composable
internal fun BetaTelemetryPromptCard(onAnswer: (Boolean) -> Unit) {
    FudGlassSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = 20.dp,
        padding = 14.dp
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                FudIconBubble(icon = Icons.Outlined.BugReport, size = 36.dp, iconSize = 19.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tm_home_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.tm_home_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FudGlassTextButton(
                    text = stringResource(R.string.tm_home_no),
                    onClick = { onAnswer(false) },
                    modifier = Modifier.weight(1f)
                )
                FudGlassPrimaryButton(
                    text = stringResource(R.string.tm_home_yes),
                    onClick = { onAnswer(true) },
                    modifier = Modifier.weight(1f),
                    height = 42.dp
                )
            }
        }
    }
}
