//
// BodyView.kt
//
// Kotlin/Compose port of `BodyView` + `BodyRenderer` from the MIT-licensed
// MuscleMap Swift package (github.com/melihcolpan/MuscleMap,
// Copyright (c) 2026 Melih Colpan). See LICENSE.
//

package com.fittribe.musclemap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.min

/** Maps the SVG viewBox onto a given canvas [Size]; mirrors the Swift renderer math. */
private data class BodyGeometry(val scale: Float, val offsetX: Float, val offsetY: Float) {
    companion object {
        fun of(viewBox: BodyViewBox, size: Size): BodyGeometry {
            val scale = min(size.width / viewBox.width, size.height / viewBox.height)
            val offsetX = (size.width - viewBox.width * scale) / 2f - viewBox.originX * scale
            val offsetY = (size.height - viewBox.height * scale) / 2f - viewBox.originY * scale
            return BodyGeometry(scale, offsetX, offsetY)
        }
    }
}

/**
 * Renders an anatomical body figure for the given [gender]/[side], optionally
 * highlighting muscles via [highlights] (e.g. a heatmap) and/or [selectedMuscles].
 *
 * When [onMuscleSelected] is provided, tapping a muscle region reports the muscle
 * and the side that was hit.
 */
@Composable
fun BodyView(
    gender: BodyGender,
    side: BodySide,
    modifier: Modifier = Modifier,
    highlights: Map<Muscle, MuscleHighlight> = emptyMap(),
    selectedMuscles: Set<Muscle> = emptySet(),
    style: BodyViewStyle = BodyViewStyle.Default,
    hideSubGroups: Boolean = true,
    onMuscleSelected: ((Muscle, MuscleSide) -> Unit)? = null,
) {
    val parts = remember(gender, side) { BodyPathProvider.paths(gender, side) }
    val viewBox = remember(gender, side) { BodyPathProvider.viewBox(gender, side) }

    val tapModifier = if (onMuscleSelected != null) {
        Modifier.pointerInput(gender, side, hideSubGroups) {
            detectTapGestures { tap ->
                val size = Size(this.size.width.toFloat(), this.size.height.toFloat())
                hitTest(tap, size, parts, viewBox, hideSubGroups)?.let { (muscle, hitSide) ->
                    onMuscleSelected(muscle, hitSide)
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(tapModifier)) {
        if (size.minDimension <= 0f) return@Canvas
        val geo = BodyGeometry.of(viewBox, size)
        withTransform({
            translate(geo.offsetX, geo.offsetY)
            scale(geo.scale, geo.scale, pivot = Offset.Zero)
        }) {
            for (part in parts) {
                val muscle = part.slug.muscle
                if (hideSubGroups && muscle != null && muscle.isSubGroup && !muscle.isAlwaysVisibleSubGroup) {
                    continue
                }

                val highlight = muscle?.let { highlights[it] }
                val isSelected = muscle != null && (
                    selectedMuscles.contains(muscle) ||
                        (hideSubGroups && muscle.isAlwaysVisibleSubGroup &&
                            muscle.parentGroup?.let { selectedMuscles.contains(it) } == true)
                    )

                val fill = resolveFill(part.slug, highlight, isSelected, highlights, style)
                val opacity = highlight?.opacity ?: 1f

                for (svg in part.allPaths) {
                    val path = PathCache.path(svg)
                    when (fill) {
                        is MuscleFill.Solid -> drawPath(path, fill.color, alpha = opacity)
                        is MuscleFill.LinearGradient -> {
                            val b = path.getBounds()
                            val brush = Brush.linearGradient(
                                colors = fill.colors,
                                start = Offset(b.left + b.width * fill.start.x, b.top + b.height * fill.start.y),
                                end = Offset(b.left + b.width * fill.end.x, b.top + b.height * fill.end.y),
                            )
                            drawPath(path, brush, alpha = opacity)
                        }
                    }

                    if (style.strokeWidth > 0f) {
                        drawPath(path, style.strokeColor, style = Stroke(width = style.strokeWidth / geo.scale))
                    }
                    if (isSelected) {
                        drawPath(
                            path,
                            style.selectionStrokeColor,
                            style = Stroke(width = style.selectionStrokeWidth / geo.scale),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convenience wrapper that renders a heatmap of muscle [intensities] using the
 * given [colorScale]. Equivalent to iOS `BodyView(...).heatmap(...)`.
 */
@Composable
fun BodyHeatmapView(
    gender: BodyGender,
    side: BodySide,
    intensities: List<MuscleIntensity>,
    modifier: Modifier = Modifier,
    colorScale: HeatmapColorScale = HeatmapColorScale.Workout,
    style: BodyViewStyle = BodyViewStyle.Default,
) {
    val highlights = remember(intensities, colorScale) { heatmapHighlights(intensities, colorScale) }
    BodyView(
        gender = gender,
        side = side,
        modifier = modifier,
        highlights = highlights,
        style = style,
    )
}

/** Mirrors `BodyRenderer.resolveFill`, including sub-group fill inheritance. */
private fun resolveFill(
    slug: BodySlug,
    highlight: MuscleHighlight?,
    isSelected: Boolean,
    highlights: Map<Muscle, MuscleHighlight>,
    style: BodyViewStyle,
): MuscleFill {
    if (slug == BodySlug.Hair) return MuscleFill.Solid(style.hairColor)
    if (slug == BodySlug.Head) return MuscleFill.Solid(style.headColor)
    if (isSelected) return MuscleFill.Solid(style.selectionColor)
    if (highlight != null) return highlight.fill
    slug.muscle?.parentGroup?.let { parent -> highlights[parent]?.let { return it.fill } }
    return MuscleFill.Solid(style.defaultFillColor)
}

/**
 * Finds which muscle was tapped, in screen coordinates. Sub-groups are tested
 * before their parents so they take priority. Mirrors `BodyRenderer.hitTest`.
 */
private fun hitTest(
    point: Offset,
    size: Size,
    parts: List<BodyPartPathData>,
    viewBox: BodyViewBox,
    hideSubGroups: Boolean,
): Pair<Muscle, MuscleSide>? {
    if (size.minDimension <= 0f) return null
    val geo = BodyGeometry.of(viewBox, size)
    val rawX = ((point.x - geo.offsetX) / geo.scale)
    val rawY = ((point.y - geo.offsetY) / geo.scale)
    val px = rawX.toInt()
    val py = rawY.toInt()

    val sorted = parts.sortedByDescending { it.slug.muscle?.isSubGroup == true }
    for (part in sorted) {
        val muscle = part.slug.muscle ?: continue
        if (hideSubGroups && muscle.isSubGroup && !muscle.isAlwaysVisibleSubGroup) continue

        val resolved = if (hideSubGroups && muscle.isAlwaysVisibleSubGroup) {
            muscle.parentGroup ?: muscle
        } else {
            muscle
        }

        for (svg in part.left) if (PathCache.region(svg).contains(px, py)) return resolved to MuscleSide.Left
        for (svg in part.right) if (PathCache.region(svg).contains(px, py)) return resolved to MuscleSide.Right
        for (svg in part.common) if (PathCache.region(svg).contains(px, py)) return resolved to MuscleSide.Both
    }
    return null
}
