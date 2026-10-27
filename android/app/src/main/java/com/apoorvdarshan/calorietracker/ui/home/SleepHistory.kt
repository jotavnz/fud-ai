package com.apoorvdarshan.calorietracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.SleepEntry
import com.apoorvdarshan.calorietracker.services.health.healthSourceName
import com.apoorvdarshan.calorietracker.services.sleep.SleepNight
import com.apoorvdarshan.calorietracker.ui.util.clockTimePattern
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Enough = Color(0xFF2E9E5B)
private val Short = Color(0xFFE0A100)
private val VeryShort = Color(0xFFD9483B)

/** Sleep history: the last 30 nights from every source, longest-recorded app per night. */
@Composable
internal fun SleepHistorySection(container: AppContainer) {
    var nights by remember { mutableStateOf<List<SleepNight>?>(null) }
    var need by remember { mutableStateOf(480L) }
    val tick by container.healthRefreshTick.collectAsState()
    val manual by container.prefs.sleepEntries.collectAsState(initial = emptyList())
    LaunchedEffect(tick, manual) {
        nights = runCatching { container.sleepService.history() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
        need = runCatching { container.sleepService.needMinutes() }.getOrDefault(480L)
    }
    val context = LocalContext.current
    val clock = remember(context) { DateTimeFormatter.ofPattern(clockTimePattern(context)).withZone(ZoneId.systemDefault()) }
    val day = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sleep_history_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val list = nights
        when {
            list == null -> Text(stringResource(R.string.sleep_history_loading), color = muted, fontSize = 13.sp)
            list.isEmpty() -> Text(stringResource(R.string.sleep_card_no_data), color = muted, fontSize = 13.sp)
            else -> {
                val average = list.take(7).map { it.minutes }.average().toLong()
                Text(stringResource(R.string.sleep_history_average, hours(average), list.take(7).size), color = muted, fontSize = 13.sp)
                list.forEach { night ->
                    val color = when {
                        night.minutes >= need - 30 -> Enough
                        night.minutes >= need - 90 -> Short
                        else -> VeryShort
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(night.wakeDate.format(day), modifier = Modifier.width(96.dp), fontSize = 13.sp)
                            LinearProgressIndicator(
                                progress = { (night.minutes / need.toFloat()).coerceIn(0f, 1f) },
                                color = color,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(hours(night.minutes), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        val source = if (night.source == SleepEntry.MANUAL) stringResource(R.string.sleep_history_manual)
                        else healthSourceName(night.source) { container.health.appLabel(it) }
                        Text(
                            stringResource(R.string.sleep_history_detail, clock.format(night.start), clock.format(night.end), source),
                            color = muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 96.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The history from Home's sleep card. */
@Composable
internal fun SleepHistoryDialog(container: AppContainer, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                SleepHistorySection(container)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_button_ok)) } }
    )
}

private fun hours(minutes: Long): String = if (minutes % 60 == 0L) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
