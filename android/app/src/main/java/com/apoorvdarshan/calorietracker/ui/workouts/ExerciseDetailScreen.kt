package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.ui.navigation.BottomNavScrollPadding
import com.apoorvdarshan.calorietracker.data.ExerciseItem
import com.apoorvdarshan.calorietracker.data.ExerciseLocalization
import com.apoorvdarshan.calorietracker.data.ExerciseVisual
import com.apoorvdarshan.calorietracker.ui.workouts.AnimatedExerciseImage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val HERO_HEIGHT = 294.dp

@Composable
fun ExerciseDetailScreen(
    item: ExerciseItem,
    visual: ExerciseVisual,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    progression: ProgressionLinks? = null,
    onOpenExercise: (ExerciseItem) -> Unit = {}
) {
    val colors = workoutsColors()
    var showMetrics by remember { mutableStateOf(false) }

    Column(modifier.testTag("workouts.detail").fillMaxSize().background(colors.background).statusBarsPadding()) {
        // Top bar: back (start) + centered title
        Box(
            Modifier.fillMaxWidth().background(colors.background).padding(vertical = 8.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.accent,
                modifier = Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape).clickable { onBack() }.padding(8.dp)
            )
            Text(
                item.name,
                color = colors.charcoal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 52.dp)
            )
            if (onEdit != null) {
                Text(
                    stringResource(R.string.wk_edit),
                    color = colors.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Pinned hero over scrollable instructions (mirrors the iOS ZStack).
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = BottomNavScrollPadding)
            ) {
                item(key = "herospace") { Spacer(Modifier.height(HERO_HEIGHT)) }
                item(key = "instructions") {
                    InstructionSection(
                        exerciseName = item.name,
                        instructions = item.instructions,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
                if (progression != null && (progression.easier != null || progression.harder != null)) {
                    item(key = "progression") {
                        ProgressionSection(progression, onOpenExercise, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp))
                    }
                }
                if (item.category != "cardio") item(key = "glossary") {
                    GlossarySection(Modifier.padding(horizontal = 20.dp))
                }
                item(key = "pad") { Spacer(Modifier.size(40.dp)) }
            }

            Hero(item = item, visual = visual, showMetrics = showMetrics, onToggle = { showMetrics = !showMetrics })
        }
    }
}

@Composable
private fun Hero(item: ExerciseItem, visual: ExerciseVisual, showMetrics: Boolean, onToggle: () -> Unit) {
    val colors = workoutsColors()
    // Opaque screen background so transparent PNG cutouts don't let
    // instructions show through when this pinned hero covers the scroll.
    Box(
        Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.background)
            .border(0.5.dp, colors.hairline.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
    ) {
        AnimatedExerciseImage(visual, Modifier.fillMaxSize(), fallbackLabel = item.name, showCredit = true)

        AnimatedVisibility(
            visible = showMetrics,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(280)),
            exit = fadeOut(tween(280))
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        AnimatedVisibility(
            visible = showMetrics,
            modifier = Modifier.align(Alignment.TopStart),
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)),
            exit = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(280))
        ) {
            MetricGrid(item, Modifier.padding(start = 24.dp, top = 8.dp, end = 84.dp))
        }

        Icon(
            Icons.Filled.Info,
            contentDescription = stringResource(if (showMetrics) R.string.hide_details else R.string.show_details),
            tint = colors.accent,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.background.copy(alpha = 0.78f))
                .border(0.7.dp, colors.hairline.copy(alpha = 0.42f), CircleShape)
                .clickable { onToggle() }
                .padding(12.dp)
        )
    }
}

@Composable
private fun MetricGrid(item: ExerciseItem, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The exercises-dataset catalog has no level, force or mechanic; skip "Unspecified" cards.
        val small = listOf(
            Triple(R.string.label_level, item.level, Icons.Filled.BarChart),
            Triple(R.string.label_category, item.category, Icons.Filled.Tag),
            Triple(R.string.label_force, item.force, Icons.Filled.SwapHoriz),
            Triple(R.string.label_mechanic, item.mechanic, Icons.Filled.Settings)
        ).filter { it.second != "Unspecified" }
        small.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (title, value, icon) ->
                    MetricCard(stringResource(title), ExerciseLocalization.label(value), icon, 1, 15.sp, Modifier.weight(1f))
                }
            }
        }
        MetricCard(stringResource(R.string.label_primary), item.primaryMusclesTitle, Icons.Filled.GpsFixed, 2, 14.sp, Modifier.fillMaxWidth())
        MetricCard(stringResource(R.string.label_secondary), item.secondaryMusclesTitle, Icons.Filled.GpsFixed, 3, 13.sp, Modifier.fillMaxWidth())
        MetricCard(stringResource(R.string.label_equipment), ExerciseLocalization.label(item.equipment), Icons.Filled.FitnessCenter, 2, 14.sp, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, valueMaxLines: Int, valueSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val colors = workoutsColors()
    val dark = colors.isDark
    val labelColor = if (dark) colors.accent else colors.secondaryAccent
    val fill = if (dark) colors.background.copy(alpha = 0.55f) else colors.background.copy(alpha = 0.92f)
    val stroke = if (dark) colors.hairline.copy(alpha = 0.32f) else colors.hairline.copy(alpha = 0.45f)
    Column(
        modifier
            .shadow(6.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(0.6.dp, stroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = labelColor, modifier = Modifier.size(11.dp))
            Text(title, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(
            value,
            color = colors.charcoal,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The easier and harder step of this exercise's bodyweight ladder ([com.apoorvdarshan.calorietracker.models.ExerciseProgressions]). */
data class ProgressionLinks(val easier: ExerciseItem?, val harder: ExerciseItem?)

@Composable
private fun ProgressionSection(links: ProgressionLinks, onOpen: (ExerciseItem) -> Unit, modifier: Modifier = Modifier) {
    val colors = workoutsColors()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel.copy(alpha = 0.16f))
            .border(0.5.dp, colors.hairline.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.wk_progression_title), color = colors.charcoal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.wk_progression_hint), color = colors.charcoal.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 20.sp)
        listOfNotNull(
            links.easier?.let { R.string.wk_progression_easier to it },
            links.harder?.let { R.string.wk_progression_harder to it }
        ).forEach { (label, target) ->
            Text(
                stringResource(label, target.name),
                color = colors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpen(target) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

/** Folded "What does it mean?" for beginners: set, rep, rest, effort and weight. */
@Composable
private fun GlossarySection(modifier: Modifier = Modifier) {
    val colors = workoutsColors()
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel.copy(alpha = 0.16f))
            .border(0.5.dp, colors.hairline.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.wk_glossary_title) + if (expanded) "  ▲" else "  ▼",
            color = colors.charcoal,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (expanded) {
            listOf(
                R.string.wk_glossary_set,
                R.string.wk_glossary_rep,
                R.string.wk_glossary_rest,
                R.string.wk_glossary_effort,
                R.string.wk_glossary_weight
            ).forEach { Text(stringResource(it), color = colors.charcoal, fontSize = 14.sp, lineHeight = 20.sp) }
        }
    }
}

@Composable
private fun InstructionSection(
    exerciseName: String,
    instructions: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = workoutsColors()
    val context = LocalContext.current
    val searchSuffix = stringResource(R.string.youtube_exercise_search_suffix)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.FormatListNumbered, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Text(stringResource(R.string.instructions), color = colors.charcoal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(CircleShape).background(colors.secondaryAccent.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text("${instructions.size}", color = colors.secondaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.panel.copy(alpha = 0.16f))
                .border(0.5.dp, colors.hairline.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                .clickable {
                    val query = URLEncoder.encode("$exerciseName $searchSuffix", StandardCharsets.UTF_8.toString())
                    val uri = Uri.parse("https://www.youtube.com/results?search_query=$query")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(colors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayCircle, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.watch_on_youtube),
                    color = colors.charcoal,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.watch_on_youtube_hint),
                    color = colors.mutedText,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = colors.mutedText, modifier = Modifier.size(16.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            instructions.forEachIndexed { index, instruction ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.panel.copy(alpha = 0.16f))
                        .border(0.5.dp, colors.hairline.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        Modifier.size(27.dp).shadow(6.dp, CircleShape, clip = false).clip(CircleShape).background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    Text(
                        instruction,
                        color = colors.charcoal.copy(alpha = 0.86f),
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
