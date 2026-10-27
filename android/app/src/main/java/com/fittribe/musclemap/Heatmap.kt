//
// Heatmap.kt
//
// Kotlin port of the heatmap/highlight model from the MIT-licensed MuscleMap
// Swift package (github.com/melihcolpan/MuscleMap, Copyright (c) 2026 Melih Colpan).
//

package com.fittribe.musclemap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/** Represents the intensity level for a specific muscle (0.0 = none, 1.0 = max). */
data class MuscleIntensity(
    val muscle: Muscle,
    val intensity: Float,
    val side: MuscleSide = MuscleSide.Both,
    /** Optional override color. If null, the heatmap color scale is used. */
    val color: Color? = null,
) {
    constructor(muscle: Muscle, intensity: Double, side: MuscleSide = MuscleSide.Both, color: Color? = null) :
        this(muscle, intensity.toFloat(), side, color)
}

/** Describes how a muscle region should be filled. */
sealed interface MuscleFill {
    data class Solid(val color: Color) : MuscleFill
    data class LinearGradient(
        val colors: List<Color>,
        val start: Offset = Offset(0.5f, 0f),
        val end: Offset = Offset(0.5f, 1f),
    ) : MuscleFill
}

/** A highlighted muscle with a fill and opacity. */
data class MuscleHighlight(
    val muscle: Muscle,
    val fill: MuscleFill,
    val opacity: Float = 1f,
) {
    val color: Color
        get() = when (val f = fill) {
            is MuscleFill.Solid -> f.color
            is MuscleFill.LinearGradient -> f.colors.firstOrNull() ?: Color.Transparent
        }

    companion object {
        fun solid(muscle: Muscle, color: Color, opacity: Float = 1f) =
            MuscleHighlight(muscle, MuscleFill.Solid(color), opacity)
    }
}

/** The interpolation curve applied to intensity values before color mapping. */
sealed interface ColorInterpolation {
    fun apply(t: Float): Float

    data object Linear : ColorInterpolation {
        override fun apply(t: Float) = t.coerceIn(0f, 1f)
    }
    data object EaseIn : ColorInterpolation {
        override fun apply(t: Float) = t.coerceIn(0f, 1f).let { it * it }
    }
    data object EaseOut : ColorInterpolation {
        override fun apply(t: Float) = t.coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
    }
    data object EaseInOut : ColorInterpolation {
        override fun apply(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            return if (c < 0.5f) 2f * c * c
            else 1f - ((-2f * c + 2f).toDouble().pow(2.0) / 2f).toFloat()
        }
    }
    data class Step(val count: Int) : ColorInterpolation {
        override fun apply(t: Float): Float {
            val c = t.coerceIn(0f, 1f)
            if (count <= 0) return c
            val stepped = kotlin.math.floor(c * count) / count
            return stepped.coerceAtMost(1f)
        }
    }
}

/** A color scale that maps an intensity (0.0 - 1.0) onto an interpolated color. */
class HeatmapColorScale(
    val colors: List<Color>,
    val interpolation: ColorInterpolation = ColorInterpolation.Linear,
) {
    fun colorFor(intensity: Float): Color {
        if (colors.isEmpty()) return Color.Gray
        if (colors.size == 1) return colors[0]

        val curved = interpolation.apply(intensity.coerceIn(0f, 1f))
        val scaledIndex = curved * (colors.size - 1)
        val lower = scaledIndex.toInt()
        val upper = (lower + 1).coerceAtMost(colors.size - 1)
        val fraction = scaledIndex - lower
        if (fraction < 0.01f) return colors[lower]
        return lerp(colors[lower], colors[upper], fraction)
    }

    companion object {
        // Colors below mirror the SwiftUI system colors used by the upstream presets:
        // .yellow #FFCC00, .orange #FF9500, .red #FF3B30, .blue #007AFF, .green #34C759,
        // and .mmDefaultFill = Color(white: 0.78) = #C7C7C7.

        /** Default workout intensity: muted gray -> yellow -> orange -> red. */
        val Workout = HeatmapColorScale(
            listOf(Color(0xFFC7C7C7), Color(0xFFFFCC00), Color(0xFFFF9500), Color(0xFFFF3B30)),
        )

        /** Cool to warm: blue -> green -> yellow -> red. */
        val Thermal = HeatmapColorScale(
            listOf(Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFFFF3B30)),
        )

        /** Medical style: green -> yellow -> red. */
        val Medical = HeatmapColorScale(
            listOf(Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFFFF3B30)),
        )

        /** Monochrome: light gray -> dark. */
        val Monochrome = HeatmapColorScale(
            listOf(Color(0xFFD9D9D9), Color(0xFF262626)),
        )

        /** Workout with 5 discrete steps instead of a smooth gradient. */
        val WorkoutStepped = HeatmapColorScale(
            listOf(Color(0xFFC7C7C7), Color(0xFFFFCC00), Color(0xFFFF9500), Color(0xFFFF3B30)),
            interpolation = ColorInterpolation.Step(count = 5),
        )

        /** Thermal with a smooth ease-in-out curve. */
        val ThermalSmooth = HeatmapColorScale(
            listOf(Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFFCC00), Color(0xFFFF3B30)),
            interpolation = ColorInterpolation.EaseInOut,
        )
    }
}

/**
 * Converts a list of [MuscleIntensity] entries into a `Muscle -> MuscleHighlight`
 * map using the given [colorScale]. Mirrors `BodyView.heatmap(...)`.
 */
fun heatmapHighlights(
    data: List<MuscleIntensity>,
    colorScale: HeatmapColorScale = HeatmapColorScale.Workout,
    threshold: Float? = null,
): Map<Muscle, MuscleHighlight> {
    val result = LinkedHashMap<Muscle, MuscleHighlight>()
    for (entry in data) {
        if (threshold != null && entry.intensity < threshold) continue
        val highlight = if (entry.color != null) {
            MuscleHighlight.solid(entry.muscle, entry.color, 1f)
        } else {
            MuscleHighlight.solid(entry.muscle, colorScale.colorFor(entry.intensity), 1f)
        }
        result[entry.muscle] = highlight
    }
    return result
}
