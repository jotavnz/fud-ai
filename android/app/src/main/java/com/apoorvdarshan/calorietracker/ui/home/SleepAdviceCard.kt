package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.services.sleep.SleepAdvice
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.FudIconBubble
import com.apoorvdarshan.calorietracker.ui.components.InfoIconButton
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Home's sleep card: last night, and how long and when to sleep tonight, with the reasons. */
@Composable
internal fun SleepAdviceCard(advice: SleepAdvice, onOpenHistory: () -> Unit = {}) {
    val clock = DateTimeFormatter.ofPattern(
        com.apoorvdarshan.calorietracker.ui.util.clockTimePattern(androidx.compose.ui.platform.LocalContext.current)
    )
    FudGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onOpenHistory),
        cornerRadius = 20.dp,
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FudIconBubble(icon = Icons.Outlined.Bedtime, size = 36.dp, iconSize = 19.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.sleep_card_title, hm(advice.recommendedMinutes), advice.bedtime.format(clock)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                advice.lastNight?.let {
                    Text(stringResource(R.string.sleep_card_last_night, hm(it.minutes)), style = MaterialTheme.typography.bodySmall, color = muted)
                } ?: Text(stringResource(R.string.sleep_card_no_data), style = MaterialTheme.typography.bodySmall, color = muted)
                reasonLines(advice, clock).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Text(
                    stringResource(R.string.sleep_history_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            InfoIconButton(title = stringResource(R.string.sleep_info_title), body = stringResource(R.string.sleep_info_body))
        }
    }
}

@Composable
private fun reasonLines(advice: SleepAdvice, clock: DateTimeFormatter): List<String> = advice.reasons.mapNotNull { reason ->
    when (reason) {
        SleepAdvice.Reason.HARD_TRAINING -> stringResource(R.string.sleep_reason_hard_training)
        SleepAdvice.Reason.MODERATE_TRAINING -> stringResource(R.string.sleep_reason_moderate_training)
        SleepAdvice.Reason.SLEEP_DEBT, SleepAdvice.Reason.SMALL_SLEEP_DEBT ->
            advice.weekAverageMinutes?.let { stringResource(R.string.sleep_reason_debt, hm(it)) }
        SleepAdvice.Reason.LATE_HEAVY_MEAL -> {
            val ate = advice.lateMealAt
            val from = advice.digestUntil
            if (ate != null && from != null) stringResource(R.string.sleep_reason_late_meal, ate.format(clock), from.format(clock)) else null
        }
        SleepAdvice.Reason.NO_HISTORY -> null
    }
}

private fun hm(minutes: Long): String = if (minutes % 60 == 0L) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
