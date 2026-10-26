package com.apoorvdarshan.calorietracker.data

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.apoorvdarshan.calorietracker.models.AppLanguage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Shows the free-exercise-db library in the app language. Names and instructions come
 * from bundled per-language assets keyed by exercise id (`exercises_es.json`); metadata
 * tokens (muscles, equipment, level, ...) stay English inside [ExerciseItem] so filters,
 * glyphs and saved selections keep working, and are translated only for display via [label].
 */
object ExerciseLocalization {
    private const val TAG = "ExerciseLocalization"

    private data class Entry(
        val name: String? = null,
        val instructions: List<String>? = null
    )

    @Volatile
    private var loaded: Pair<String, Map<String, Entry>>? = null

    /** Language the library is shown in, following the in-app choice, then the device. */
    fun currentLanguage(): String = runCatching { resolveLanguage() }.getOrDefault("en")

    // Android framework calls; JVM unit tests (no framework) fall back to English above.
    private fun resolveLanguage(): String {
        val appChoice = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore(',')
            .ifBlank { null }
        val device = LocaleListCompat.getAdjustedDefault()
        val deviceTags = (0 until device.size()).mapNotNull { device[it]?.toLanguageTag() }
        return AppLanguage.shownTag(appChoice, deviceTags)
    }

    /** Swaps names and instructions for the translated copy; unknown ids stay as they are. */
    fun localize(context: Context, items: List<ExerciseItem>, language: String): List<ExerciseItem> {
        val table = table(context, language)
        if (table.isEmpty()) return items
        return items.map { item ->
            val entry = table[item.id] ?: return@map item
            // Same cleanup as ExerciseItem.from (blank source steps are dropped), then the
            // step count must still line up with the English copy.
            val instructions = entry.instructions
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                ?.takeIf { it.size == item.instructions.size }
                ?: item.instructions
            item.copy(
                name = entry.name?.trim()?.takeIf { it.isNotEmpty() } ?: item.name,
                englishName = item.englishName,
                instructions = instructions
            )
        }
    }

    /**
     * Name in the current app language for an exercise the diary saved under another
     * language. Returns [fallback] for user exercises, custom activities, or before the
     * catalog has loaded.
     */
    fun name(itemId: String, fallback: String): String =
        ExerciseRepository.peek()?.byId?.get(itemId)?.name ?: fallback

    /** Display label for a metadata token such as "Chest", "Machine" or "Beginner". */
    fun label(token: String): String {
        if (currentLanguage() != "es") return token
        return spanishLabels[token.trim().lowercase()] ?: token
    }

    fun labels(tokens: List<String>): String = tokens.joinToString(", ") { label(it) }

    private fun table(context: Context, language: String): Map<String, Entry> {
        loaded?.let { (cachedLanguage, table) -> if (cachedLanguage == language) return table }
        val table = if (language == "en") emptyMap() else readAsset(context, "exercises_$language.json")
        loaded = language to table
        return table
    }

    private fun readAsset(context: Context, name: String): Map<String, Entry> = try {
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { reader ->
            val type = object : TypeToken<Map<String, Entry>>() {}.type
            Gson().fromJson<Map<String, Entry>>(reader, type).orEmpty()
        }
    } catch (t: Throwable) {
        Log.e(TAG, "failed to load $name", t)
        emptyMap()
    }

    private val spanishLabels = mapOf(
        // Muscles
        "abdominals" to "Abdominales",
        "abductors" to "Abductores",
        "adductors" to "Aductores",
        "biceps" to "Bíceps",
        "calves" to "Pantorrillas",
        "chest" to "Pecho",
        "forearms" to "Antebrazos",
        "glutes" to "Glúteos",
        "hamstrings" to "Femorales",
        "lats" to "Dorsales",
        "lower back" to "Zona lumbar",
        "middle back" to "Espalda media",
        "neck" to "Cuello",
        "quadriceps" to "Cuádriceps",
        "shoulders" to "Hombros",
        "traps" to "Trapecios",
        "triceps" to "Tríceps",
        // Equipment
        "bands" to "Bandas",
        "barbell" to "Barra",
        "body only" to "Peso corporal",
        "cable" to "Polea",
        "dumbbell" to "Mancuerna",
        "exercise ball" to "Pelota de ejercicio",
        "e-z curl bar" to "Barra Z",
        "foam roll" to "Rodillo de espuma",
        "kettlebells" to "Kettlebells",
        "machine" to "Máquina",
        "medicine ball" to "Balón medicinal",
        "other" to "Otro",
        // Level
        "beginner" to "Principiante",
        "intermediate" to "Intermedio",
        "expert" to "Avanzado",
        "advanced" to "Avanzado",
        // Force
        "push" to "Empuje",
        "pull" to "Tirón",
        "static" to "Estático",
        // Mechanic
        "compound" to "Compuesto",
        "isolation" to "Aislamiento",
        // Category
        "cardio" to "Cardio",
        "olympic weightlifting" to "Halterofilia olímpica",
        "plyometrics" to "Pliometría",
        "powerlifting" to "Powerlifting",
        "strength" to "Fuerza",
        "stretching" to "Estiramiento",
        "strongman" to "Strongman",
        "activity" to "Actividad",
        // Placeholders
        "unspecified" to "Sin especificar",
        "none" to "Ninguno"
    )
}
