package com.apoorvdarshan.calorietracker.services.ai

import java.util.Locale

/**
 * The reply-language line for food AI prompts. The prompts themselves stay in English (the
 * models follow them best), but anything the user reads (food names, notes, suggestions) has
 * to come back in the app's language. Null for English, so those prompts are unchanged.
 */
object AiLanguage {
    fun instruction(locale: Locale = Locale.getDefault()): String? = when (locale.language) {
        "es" -> "LANGUAGE: Write every text the user will read (food and dish names, notes, explanations, suggestions) " +
            "in Spanish. Keep JSON keys, enum values, units and numbers exactly as specified above."
        else -> null
    }
}
