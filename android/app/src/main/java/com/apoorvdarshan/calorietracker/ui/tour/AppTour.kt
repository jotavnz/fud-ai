package com.apoorvdarshan.calorietracker.ui.tour

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import kotlin.math.roundToInt

/** Parts of the app the tour points at; each marks itself with [tourTarget]. */
enum class TourTarget { CALORIE_HERO, MACROS, ADD_FOOD, VOICE, TAB_COACH, TAB_WORKOUTS, TAB_PROGRESS, TAB_SETTINGS }

/** One stop: a highlighted part of the screen (or a centered card when [target] is null). */
data class TourStep(val target: TourTarget?, @StringRes val titleRes: Int, @StringRes val bodyRes: Int)

/** The first-use tour of Home and the tabs, in order. Stops whose part is not on screen are skipped. */
val AppTourSteps = listOf(
    TourStep(null, R.string.tour_welcome_title, R.string.tour_welcome_body),
    TourStep(TourTarget.CALORIE_HERO, R.string.tour_calories_title, R.string.tour_calories_body),
    TourStep(TourTarget.MACROS, R.string.tour_macros_title, R.string.tour_macros_body),
    TourStep(TourTarget.ADD_FOOD, R.string.tour_add_title, R.string.tour_add_body),
    TourStep(TourTarget.VOICE, R.string.tour_voice_title, R.string.tour_voice_body),
    TourStep(TourTarget.TAB_COACH, R.string.tour_coach_title, R.string.tour_coach_body),
    TourStep(TourTarget.TAB_WORKOUTS, R.string.tour_workouts_title, R.string.tour_workouts_body),
    TourStep(TourTarget.TAB_PROGRESS, R.string.tour_progress_title, R.string.tour_progress_body),
    TourStep(TourTarget.TAB_SETTINGS, R.string.tour_settings_title, R.string.tour_settings_body),
    TourStep(null, R.string.tour_done_title, R.string.tour_done_body)
)

/** Where each target sits on screen, and which stop is showing (null = no tour). */
@Stable
class AppTourState {
    internal val bounds = mutableStateMapOf<TourTarget, Rect>()
    var steps by mutableStateOf<List<TourStep>>(emptyList())
        private set
    var index by mutableStateOf<Int?>(null)
        private set

    val active: Boolean get() = index != null

    /** Starts from the first stop, keeping only stops whose part is on screen now. */
    fun start() {
        steps = AppTourSteps.filter { it.target == null || it.target in bounds }
        index = 0
    }

    fun next(): Boolean {
        val i = index ?: return false
        return if (i + 1 < steps.size) {
            index = i + 1
            true
        } else {
            index = null
            false
        }
    }

    fun back() {
        val i = index ?: return
        if (i > 0) index = i - 1
    }

    fun stop() {
        index = null
    }
}

val LocalAppTour = compositionLocalOf<AppTourState?> { null }

/** Marks this element as a tour stop so the tour can highlight it. */
fun Modifier.tourTarget(target: TourTarget): Modifier = composed {
    val tour = LocalAppTour.current ?: return@composed this
    DisposableEffect(tour, target) { onDispose { tour.bounds.remove(target) } }
    onGloballyPositioned { tour.bounds[target] = it.boundsInRoot() }
}

/**
 * Dims the screen except the current stop, with a card that explains it. Every tap is caught
 * here, so the app underneath cannot be used until the tour moves on, is skipped or ends.
 * [onFinish] runs once when the tour ends or is skipped.
 */
@Composable
fun AppTourOverlay(tour: AppTourState, onFinish: () -> Unit) {
    val i = tour.index ?: return
    val step = tour.steps.getOrNull(i) ?: return
    val hole = step.target?.let { tour.bounds[it] }
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val radius = with(density) { 18.dp.toPx() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.66f))
            if (hole != null) {
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(hole.left - pad, hole.top - pad),
                    size = Size(hole.width + pad * 2, hole.height + pad * 2),
                    cornerRadius = CornerRadius(radius),
                    blendMode = BlendMode.Clear
                )
            }
        }

        val screenHeight = with(density) { maxHeight.toPx() }
        var cardHeight by remember(i) { mutableStateOf(0) }
        val margin = with(density) { 16.dp.toPx() }
        // Below the highlight when it sits in the top half, above it otherwise; centered without one.
        val cardY = when {
            hole == null -> ((screenHeight - cardHeight) / 2f)
            hole.center.y < screenHeight / 2f -> hole.bottom + pad + margin
            else -> hole.top - pad - margin - cardHeight
        }.coerceIn(margin, (screenHeight - cardHeight - margin).coerceAtLeast(margin))

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, cardY.roundToInt()) }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            TourCard(
                step = step,
                position = i + 1,
                count = tour.steps.size,
                modifier = Modifier.onSizeChanged { cardHeight = it.height },
                onBack = { tour.back() },
                onNext = { if (!tour.next()) onFinish() },
                onSkip = {
                    tour.stop()
                    onFinish()
                }
            )
        }
    }
}

@Composable
private fun TourCard(
    step: TourStep,
    position: Int,
    count: Int,
    modifier: Modifier,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val last = position == count
    Surface(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.tour_counter, position, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(step.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onSkip) { Text(stringResource(R.string.tour_skip)) }
                Spacer(Modifier.weight(1f))
                if (position > 1) TextButton(onClick = onBack) { Text(stringResource(R.string.tour_back)) }
                Button(onClick = onNext) {
                    Text(stringResource(if (last) R.string.tour_finish else if (position == 1) R.string.tour_start else R.string.tour_next))
                }
            }
        }
    }
}
