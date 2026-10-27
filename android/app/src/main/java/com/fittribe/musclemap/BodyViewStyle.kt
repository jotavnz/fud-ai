//
// BodyViewStyle.kt
//
// Kotlin port of `BodyViewStyle` from the MIT-licensed MuscleMap Swift package
// (github.com/melihcolpan/MuscleMap, Copyright (c) 2026 Melih Colpan).
//

package com.fittribe.musclemap

import androidx.compose.ui.graphics.Color

/** Configuration for the visual appearance of a [BodyView]. */
data class BodyViewStyle(
    val defaultFillColor: Color = Color(0xFFC7C7C7),
    val strokeColor: Color = Color.Transparent,
    val strokeWidth: Float = 0f,
    val selectionColor: Color = Color(0xFF4CAF50),
    val selectionStrokeColor: Color = Color(0xFF4CAF50),
    val selectionStrokeWidth: Float = 2f,
    val headColor: Color = Color(0xFFBFBFBF),
    val hairColor: Color = Color(0xFF404040),
) {
    companion object {
        /** Default style with gray fill and green selection. */
        val Default = BodyViewStyle()

        /** Minimal style with thin strokes and subtle fill. */
        val Minimal = BodyViewStyle(
            defaultFillColor = Color(0xFFE0E0E0),
            strokeColor = Color(0xFFB3B3B3),
            strokeWidth = 0.5f,
            selectionStrokeWidth = 1.5f,
        )

        /** Neon style with dark background tones and a cyan selection. */
        val Neon = BodyViewStyle(
            defaultFillColor = Color(0xFF262626),
            strokeColor = Color(0xFF4D4D4D),
            strokeWidth = 0.5f,
            selectionColor = Color(0xFF00F4FE),
            selectionStrokeColor = Color(0xFF00F4FE),
            selectionStrokeWidth = 2f,
            headColor = Color(0xFF333333),
            hairColor = Color(0xFF1A1A1A),
        )

        /** Medical/clinical style. */
        val Medical = BodyViewStyle(
            defaultFillColor = Color(0xFFE6EBF2),
            strokeColor = Color(0xFFB3BFCC),
            strokeWidth = 0.5f,
            selectionColor = Color(0xFF007AFF),
            selectionStrokeColor = Color(0xFF007AFF),
            selectionStrokeWidth = 2f,
            headColor = Color(0xFFD9DEE6),
            hairColor = Color(0xFF4D5259),
        )
    }
}
