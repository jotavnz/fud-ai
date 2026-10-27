//
// BodyPathData.kt
//
// Kotlin port of the MIT-licensed MuscleMap Swift package
// (github.com/melihcolpan/MuscleMap, Copyright (c) 2026 Melih Colpan).
//

package com.fittribe.musclemap

/** SVG path data for a single body part, supporting common, left, and right sub-paths. */
internal class BodyPartPathData(
    val slug: BodySlug,
    val common: List<String> = emptyList(),
    val left: List<String> = emptyList(),
    val right: List<String> = emptyList(),
) {
    /** All SVG path strings combined. */
    val allPaths: List<String> get() = common + left + right
}

/**
 * ViewBox configuration for body rendering, expressed in the SVG coordinate space.
 * Mirrors `BodyViewBox` from the Swift package.
 */
internal data class BodyViewBox(
    val originX: Float,
    val originY: Float,
    val width: Float,
    val height: Float,
) {
    companion object {
        val MaleFront = BodyViewBox(originX = 0f, originY = 95f, width = 727f, height = 1280f)
        val MaleBack = BodyViewBox(originX = 718f, originY = 95f, width = 727f, height = 1280f)
        val FemaleFront = BodyViewBox(originX = 0f, originY = 0f, width = 650f, height = 1450f)
        val FemaleBack = BodyViewBox(originX = 823f, originY = 0f, width = 650f, height = 1450f)
    }
}

/** Provides body path data and viewbox for a given gender and side. */
internal object BodyPathProvider {

    fun paths(gender: BodyGender, side: BodySide): List<BodyPartPathData> =
        when (gender to side) {
            BodyGender.Male to BodySide.Front -> MaleFrontPaths.paths
            BodyGender.Male to BodySide.Back -> MaleBackPaths.paths
            BodyGender.Female to BodySide.Front -> FemaleFrontPaths.paths
            BodyGender.Female to BodySide.Back -> FemaleBackPaths.paths
            else -> MaleFrontPaths.paths
        }

    fun viewBox(gender: BodyGender, side: BodySide): BodyViewBox =
        when (gender to side) {
            BodyGender.Male to BodySide.Front -> BodyViewBox.MaleFront
            BodyGender.Male to BodySide.Back -> BodyViewBox.MaleBack
            BodyGender.Female to BodySide.Front -> BodyViewBox.FemaleFront
            BodyGender.Female to BodySide.Back -> BodyViewBox.FemaleBack
            else -> BodyViewBox.MaleFront
        }
}
