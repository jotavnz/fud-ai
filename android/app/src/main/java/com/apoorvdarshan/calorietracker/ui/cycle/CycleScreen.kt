package com.apoorvdarshan.calorietracker.ui.cycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.CycleMath
import com.apoorvdarshan.calorietracker.models.CycleMode
import com.apoorvdarshan.calorietracker.models.CyclePeriod
import com.apoorvdarshan.calorietracker.models.CyclePhase
import com.apoorvdarshan.calorietracker.models.CycleSymptoms
import com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.FudGlassTextButton
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Opt-in menstrual cycle tracking: log periods (or import them from Health Connect, where apps
 * like Flo or Samsung Health may share them) and how you feel today. Coach adapts training to
 * the day's symptoms. Everything stays on the device.
 */
@Composable
fun CycleScreen(container: AppContainer, onDismiss: () -> Unit) {
    val repo = container.cycleRepository
    val settings by repo.settings.collectAsState(initial = com.apoorvdarshan.calorietracker.models.CycleSettings())
    val periods by repo.periods.collectAsState(initial = emptyList())
    val symptoms by repo.symptoms.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val todaySymptoms = symptoms.firstOrNull { it.date == today.toString() } ?: CycleSymptoms(date = today.toString())
    val status = remember(periods, settings.mode) { CycleMath.status(periods, settings.mode, today) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importedFormat = stringResource(R.string.cycle_imported)
    val importNone = stringResource(R.string.cycle_import_none)
    val coachPrompt = stringResource(R.string.cycle_coach_prompt)

    suspend fun importFromHealth() {
        val found = container.health.readMenstruationPeriods(Instant.now().minus(400, ChronoUnit.DAYS), Instant.now())
        val added = if (found.isNullOrEmpty()) 0 else repo.importPeriods(found)
        importMessage = if (added == 0) importNone else importedFormat.format(added)
    }
    val permissionLauncher = rememberLauncherForActivityResult(container.health.permissionRequestContract()) { granted ->
        if (granted.containsAll(container.health.cyclePermissions)) scope.launch { importFromHealth() }
        else importMessage = importNone
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.cycle_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.cycle_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pantry_close)) }
                    }
                }
                item {
                    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.cycle_enable), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Switch(checked = settings.enabled, onCheckedChange = { on -> scope.launch { repo.setSettings(settings.copy(enabled = on)) } })
                            }
                            Text(stringResource(R.string.cycle_privacy), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
                if (settings.enabled) {
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CycleMode.entries.forEach { mode ->
                                CycleChip(stringResource(mode.labelRes), mode == settings.mode) {
                                    scope.launch { repo.setSettings(settings.copy(mode = mode)) }
                                }
                            }
                        }
                    }
                    item {
                        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val headline = when {
                                    status.onPeriod -> stringResource(R.string.cycle_on_period)
                                    status.cycleDay != null -> stringResource(R.string.cycle_day, status.cycleDay)
                                    else -> stringResource(R.string.cycle_no_estimate)
                                }
                                Text(headline, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Calorie)
                                status.phase?.let { phase ->
                                    Text(
                                        stringResource(R.string.cycle_phase_line, stringResource(phase.labelRes)) +
                                            if (status.confidence == "low") " · " + stringResource(R.string.cycle_low_confidence) else "",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                }
                                status.nextPeriod?.let {
                                    Text(stringResource(R.string.cycle_next, it.toString()), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FudGlassTextButton(text = stringResource(R.string.cycle_started_today), onClick = { scope.launch { repo.startPeriod() } })
                                    if (status.onPeriod) FudGlassTextButton(text = stringResource(R.string.cycle_ended_today), onClick = { scope.launch { repo.endPeriod() } })
                                }
                            }
                        }
                    }
                    item {
                        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.cycle_today_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.cycle_today_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                fun save(updated: CycleSymptoms) { scope.launch { repo.setSymptoms(updated) } }
                                SymptomRow(stringResource(R.string.cycle_cramps), todaySymptoms.cramps) { save(todaySymptoms.copy(cramps = it)) }
                                SymptomRow(stringResource(R.string.cycle_fatigue), todaySymptoms.fatigue) { save(todaySymptoms.copy(fatigue = it)) }
                                SymptomRow(stringResource(R.string.cycle_headache), todaySymptoms.headache) { save(todaySymptoms.copy(headache = it)) }
                                SymptomRow(stringResource(R.string.cycle_low_mood), todaySymptoms.lowMood) { save(todaySymptoms.copy(lowMood = it)) }
                                SymptomRow(stringResource(R.string.cycle_bloating), todaySymptoms.bloating) { save(todaySymptoms.copy(bloating = it)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.cycle_heavy_flow), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    Switch(checked = todaySymptoms.heavyFlow, onCheckedChange = { save(todaySymptoms.copy(heavyFlow = it)) })
                                }
                                val tips = cycleTips(todaySymptoms)
                                if (tips.isNotEmpty()) {
                                    Text(stringResource(R.string.cycle_tips_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Calorie)
                                    tips.forEach { Text("• " + stringResource(it), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)) }
                                }
                                FudGlassPrimaryButton(text = stringResource(R.string.cycle_ask_coach), onClick = {
                                    onDismiss()
                                    container.navigator.showCoachMessage(coachPrompt)
                                })
                            }
                        }
                    }
                    item {
                        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.cycle_import_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.cycle_import_body), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                FudGlassTextButton(text = stringResource(R.string.cycle_import_action), onClick = {
                                    scope.launch {
                                        if (container.health.hasMenstruationRead()) importFromHealth()
                                        else permissionLauncher.launch(container.health.cyclePermissions)
                                    }
                                })
                                importMessage?.let { Text(it, fontSize = 13.sp, color = AppColors.Calorie) }
                            }
                        }
                    }
                    if (periods.isNotEmpty()) {
                        item { Text(stringResource(R.string.cycle_history), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                        items(periods.take(12), key = { it.id }) { period -> PeriodRow(period) { scope.launch { repo.removePeriod(period.id) } } }
                    }
                    item {
                        Text(stringResource(R.string.cycle_medical_note), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private fun cycleTips(symptoms: CycleSymptoms): List<Int> = buildList {
    if (symptoms.cramps >= 2 || symptoms.heavyFlow) add(R.string.cycle_tip_low_intensity)
    if (symptoms.cramps >= 3) add(R.string.cycle_tip_rest)
    if (symptoms.fatigue >= 2) add(R.string.cycle_tip_fatigue)
    if (symptoms.headache >= 2) add(R.string.cycle_tip_headache)
    if (symptoms.lowMood >= 2) add(R.string.cycle_tip_mood)
    if (symptoms.heavyFlow) add(R.string.cycle_tip_iron)
}

private val CycleMode.labelRes: Int
    get() = when (this) {
        CycleMode.NATURAL -> R.string.cycle_mode_natural
        CycleMode.HORMONAL -> R.string.cycle_mode_hormonal
        CycleMode.PERIMENOPAUSE -> R.string.cycle_mode_perimenopause
        CycleMode.MENOPAUSE -> R.string.cycle_mode_menopause
        CycleMode.PREGNANT -> R.string.cycle_mode_pregnant
    }

private val CyclePhase.labelRes: Int
    get() = when (this) {
        CyclePhase.MENSTRUAL -> R.string.cycle_phase_menstrual
        CyclePhase.FOLLICULAR -> R.string.cycle_phase_follicular
        CyclePhase.OVULATORY -> R.string.cycle_phase_ovulatory
        CyclePhase.LUTEAL -> R.string.cycle_phase_luteal
        CyclePhase.PREMENSTRUAL -> R.string.cycle_phase_premenstrual
    }

@Composable
private fun CycleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun SymptomRow(label: String, level: Int, onLevel: (Int) -> Unit) {
    val levels = listOf(R.string.cycle_level_none, R.string.cycle_level_mild, R.string.cycle_level_moderate, R.string.cycle_level_strong)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            levels.forEachIndexed { index, res -> CycleChip(stringResource(res), index == level) { onLevel(index) } }
        }
    }
}

@Composable
private fun PeriodRow(period: CyclePeriod, onDelete: () -> Unit) {
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(period.start, period.end).joinToString(" → "), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                if (period.source != CyclePeriod.MANUAL) {
                    Text(period.source, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_delete)) }
        }
    }
}
