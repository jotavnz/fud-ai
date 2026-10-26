package com.apoorvdarshan.calorietracker.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.CoachLogProposal
import com.apoorvdarshan.calorietracker.models.CurrentMealSchedule
import com.apoorvdarshan.calorietracker.models.foodTotals
import com.apoorvdarshan.calorietracker.services.ai.CoachLogService
import com.apoorvdarshan.calorietracker.services.ai.CoachLogTool
import com.apoorvdarshan.calorietracker.ui.components.energyText
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The confirmation card under a Coach reply that proposed logging. Nothing is written until the
 * user taps Log; Edit lets them leave rows out or open a food in Home's review sheet; Cancel
 * dismisses the card. After logging, each food row opens its diary entry for editing.
 */
@Composable
internal fun CoachLogCard(
    proposal: CoachLogProposal,
    busy: Boolean,
    weightMetric: Boolean,
    onLog: () -> Unit,
    onCancel: () -> Unit,
    onToggle: (CoachLogItem) -> Unit,
    onEdit: (CoachLogItem) -> Unit,
    onOpenLogged: (CoachLogItem) -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    var editing by rememberSaveable(proposal.id) { mutableStateOf(false) }
    val ready = proposal.status == CoachLogProposal.Status.READY
    val inEditMode = editing && ready
    val today = remember { LocalDate.now() }

    Column(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .background(AppColors.Calorie.copy(alpha = 0.045f))
            .border(0.6.dp, AppColors.Calorie.copy(alpha = 0.18f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(14.dp))
            Text(
                stringResource(R.string.cl_card_title),
                color = AppColors.Calorie,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (proposal.status == CoachLogProposal.Status.PREPARING) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Calorie)
                Text(stringResource(R.string.cl_card_preparing), color = onSurface.copy(alpha = 0.65f), fontSize = 13.sp)
            }
        }

        proposal.items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = onSurface.copy(alpha = 0.08f))
            LogItemRow(
                item = item,
                proposalStatus = proposal.status,
                editing = inEditMode,
                busy = busy,
                today = today,
                weightMetric = weightMetric,
                onToggle = { onToggle(item) },
                onEdit = { onEdit(item) },
                onOpenLogged = { onOpenLogged(item) }
            )
        }

        val includedFoods = proposal.items.filter {
            it.kind == CoachLogItem.Kind.FOOD && it.included && it.state != CoachLogItem.State.HANDED_OFF &&
                it.state != CoachLogItem.State.FAILED
        }
        if (includedFoods.size > 1 && proposal.status != CoachLogProposal.Status.DISMISSED) {
            val totals = includedFoods.foodTotals()
            Text(
                stringResource(R.string.cl_total, energyText(totals.calories), grams(totals.protein), grams(totals.carbs), grams(totals.fat)),
                color = onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (inEditMode) {
            Text(stringResource(R.string.cl_edit_hint), color = onSurface.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
        }

        Spacer(Modifier.height(2.dp))
        when (proposal.status) {
            CoachLogProposal.Status.PREPARING, CoachLogProposal.Status.READY -> {
                val canLog = ready && !busy && proposal.loggableItems.isNotEmpty()
                val logDescription = stringResource(R.string.cl_action_log_description)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onLog,
                        enabled = canLog,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Calorie, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .semantics { contentDescription = logDescription }
                    ) {
                        if (busy && ready) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(stringResource(R.string.cl_action_log), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = { editing = !editing },
                        enabled = ready && !busy,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                    ) {
                        Text(
                            stringResource(if (inEditMode) R.string.cl_action_done else R.string.cl_action_edit),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                TextButton(
                    onClick = onCancel,
                    enabled = !busy || proposal.status == CoachLogProposal.Status.PREPARING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                ) {
                    Text(stringResource(R.string.cl_action_cancel), color = onSurface.copy(alpha = 0.7f))
                }
            }
            CoachLogProposal.Status.LOGGED -> {
                val dates = proposal.items.filter { it.state == CoachLogItem.State.LOGGED }.map { it.date }.distinct()
                val text = if (dates.isEmpty() || dates == listOf(today.toString())) {
                    stringResource(R.string.cl_logged_today)
                } else {
                    val labels = dates.map { dateLabel(it, today) }
                    stringResource(R.string.cl_logged_on, labels.joinToString(", "))
                }
                Outcome(Icons.Filled.CheckCircle, text, Color(0xFF34C759))
                if (proposal.items.any { it.kind == CoachLogItem.Kind.FOOD && it.state == CoachLogItem.State.LOGGED }) {
                    Text(stringResource(R.string.cl_logged_tap_hint), color = onSurface.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            CoachLogProposal.Status.DISMISSED -> Outcome(Icons.Filled.Close, stringResource(R.string.cl_dismissed), onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun LogItemRow(
    item: CoachLogItem,
    proposalStatus: CoachLogProposal.Status,
    editing: Boolean,
    busy: Boolean,
    today: LocalDate,
    weightMetric: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onOpenLogged: () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val pending = item.state == CoachLogItem.State.PENDING
    val dimmed = (pending && !item.included) || item.state == CoachLogItem.State.HANDED_OFF ||
        item.state == CoachLogItem.State.FAILED || proposalStatus == CoachLogProposal.Status.DISMISSED
    val loggedFood = item.kind == CoachLogItem.Kind.FOOD && item.state == CoachLogItem.State.LOGGED
    val viewDescription = stringResource(R.string.cl_item_view_description, item.displayName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (loggedFood) Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenLogged)
                    .semantics { contentDescription = viewDescription }
                else Modifier
            ),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (editing && pending) {
            val includeDescription = stringResource(R.string.cl_item_include_description, item.displayName)
            Checkbox(
                checked = item.included,
                onCheckedChange = { onToggle() },
                enabled = !busy,
                colors = CheckboxDefaults.colors(checkedColor = AppColors.Calorie),
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = includeDescription }
            )
        } else if (item.state == CoachLogItem.State.LOGGED) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cl_item_logged),
                tint = Color(0xFF34C759),
                modifier = Modifier.padding(top = 2.dp).size(16.dp)
            )
        } else if (item.state == CoachLogItem.State.FAILED) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.padding(top = 2.dp).size(16.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                itemTitle(item),
                color = onSurface.copy(alpha = if (dimmed) 0.45f else 1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp
            )
            itemDetails(item, today, weightMetric).forEach { line ->
                Text(line, color = onSurface.copy(alpha = if (dimmed) 0.4f else 0.65f), fontSize = 13.sp, lineHeight = 17.sp)
            }
            itemStatus(item)?.let { (text, color) ->
                Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
            }
            if (editing && pending && item.kind == CoachLogItem.Kind.FOOD && item.reviewAnalysis() != null) {
                val editDescription = stringResource(R.string.cl_item_edit_description, item.displayName)
                TextButton(
                    onClick = onEdit,
                    enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .semantics { contentDescription = editDescription }
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.cl_open_editor), color = AppColors.Calorie, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun itemTitle(item: CoachLogItem): String = when (item.kind) {
    CoachLogItem.Kind.FOOD, CoachLogItem.Kind.CARDIO -> item.displayName
    CoachLogItem.Kind.WATER -> stringResource(R.string.cl_water)
    CoachLogItem.Kind.WEIGHT -> stringResource(R.string.cl_weight)
    CoachLogItem.Kind.BODY_FAT -> stringResource(R.string.cl_body_fat)
    CoachLogItem.Kind.WORKOUT -> item.name.ifBlank { stringResource(R.string.cl_workout) }
}

@Composable
private fun itemDetails(item: CoachLogItem, today: LocalDate, weightMetric: Boolean): List<String> {
    val lines = mutableListOf<String>()
    val date = dateLabel(item.date, today)
    when (item.kind) {
        CoachLogItem.Kind.FOOD -> {
            val meal = remember(item) {
                CoachLogTool.inferredMeal(item, Instant.now(), ZoneId.systemDefault(), CurrentMealSchedule.value)
            }
            val portion = item.serving ?: item.analysis?.servingSizeGrams?.takeIf { it > 0 }?.let { "${it.roundToInt()} g" }
            lines += listOfNotNull(portion, stringResource(R.string.cl_meal_and_date, stringResource(meal.displayNameRes), date))
                .joinToString(" · ")
            item.effectiveMacros?.let { macros ->
                lines += stringResource(
                    R.string.cl_food_macros,
                    energyText(macros.calories),
                    grams(macros.protein),
                    grams(macros.carbs),
                    grams(macros.fat)
                )
                if (item.analysis == null) lines += stringResource(R.string.cl_estimate_note)
            }
        }
        CoachLogItem.Kind.WATER -> lines += listOf(stringResource(R.string.cl_water_amount, item.waterMl ?: 0), date).joinToString(" · ")
        CoachLogItem.Kind.WEIGHT -> {
            val kg = item.weightKg ?: 0.0
            val value = if (weightMetric) stringResource(R.string.cl_weight_kg, oneDecimal(kg))
            else stringResource(R.string.cl_weight_lb, oneDecimal(kg / 0.45359237))
            lines += "$value · $date"
        }
        CoachLogItem.Kind.BODY_FAT -> lines += "${stringResource(R.string.cl_body_fat_percent, oneDecimal(item.bodyFatPercent ?: 0.0))} · $date"
        CoachLogItem.Kind.WORKOUT -> {
            val exercises = item.workoutDraft?.exercises.orEmpty()
            if (exercises.isNotEmpty()) {
                lines += exercises.joinToString(", ") { exercise ->
                    val sets = exercise.sets.size
                    val minutes = exercise.minutes.takeIf { it.isNotBlank() }
                    exercise.name + when {
                        sets > 0 -> " ($sets×${exercise.sets.first().reps.ifBlank { "?" }})"
                        minutes != null -> " (${minutes} min)"
                        else -> ""
                    }
                }
            } else if (item.description.isNotBlank()) {
                lines += item.description
            }
            lines += date
        }
        CoachLogItem.Kind.CARDIO -> {
            val minutes = item.minutes ?: 0
            lines += (item.caloriesBurned?.let { stringResource(R.string.cl_cardio_minutes_burn, minutes, energyText(it)) }
                ?: stringResource(R.string.cl_cardio_minutes, minutes)) + " · $date"
        }
    }
    return lines
}

@Composable
private fun itemStatus(item: CoachLogItem): Pair<String, Color>? {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    return when (item.state) {
        CoachLogItem.State.LOGGED -> null
        CoachLogItem.State.HANDED_OFF -> stringResource(R.string.cl_item_in_editor) to muted
        CoachLogItem.State.FAILED -> errorText(item.error) to Color(0xFFD32F2F)
        CoachLogItem.State.PENDING -> when {
            item.error != null -> errorText(item.error) to Color(0xFFD32F2F)
            !item.included -> stringResource(R.string.cl_item_left_out) to muted
            else -> null
        }
    }
}

@Composable
private fun errorText(code: String?): String = when (code) {
    CoachLogService.ERROR_ANALYSIS -> stringResource(R.string.cl_item_error_analysis)
    CoachLogService.ERROR_WORKOUT -> stringResource(R.string.cl_item_error_workout)
    null, CoachLogService.ERROR_SAVE -> stringResource(R.string.cl_item_error_save)
    // A workout clarification question from the parser, already in the user's words.
    else -> code
}

@Composable
private fun Outcome(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun dateLabel(iso: String, today: LocalDate): String {
    val day = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    if (day == today) return stringResource(R.string.cl_today)
    val locale = LocalConfiguration.current.locales[0]
    return day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

private fun grams(value: Double): String =
    if (value >= 10) value.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else String.format(Locale.getDefault(), "%.1f", rounded)
}
