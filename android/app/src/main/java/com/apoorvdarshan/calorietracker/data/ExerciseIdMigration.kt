package com.apoorvdarshan.calorietracker.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Points saved workouts at the current catalog. The library moved from free-exercise-db
 * ids ("Seated_Cable_Rows") to exercises-dataset ids ("ed_0861"); the asset
 * `exercise-id-migration.json` (scripts/import_exercises_dataset.mjs) maps each old id
 * that has an equivalent. Saved state is rewritten as it is read, so sessions, routines
 * and favorites keep their exercise; the next save stores the new ids.
 */
object ExerciseIdMigration {
    private const val TAG = "ExerciseIdMigration"
    private const val ASSET = "exercise-id-migration.json"

    @Volatile
    private var map: Map<String, String> = emptyMap()

    /** Loads the map; call once at startup, before saved workouts are read. */
    fun load(context: Context) {
        map = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
                val type = object : TypeToken<Map<String, String>>() {}.type
                Gson().fromJson<Map<String, String>>(reader, type).orEmpty()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to load $ASSET", t)
            emptyMap()
        }
    }

    /** New id for an old catalog id, or [id] itself. */
    fun resolve(id: String): String = map[id] ?: id

    private val quotedString = Regex("\"([A-Za-z0-9_()'.,-]+)\"")

    /**
     * Replaces every JSON string value that is exactly an old id. Old ids are distinctive
     * ("Barbell_Full_Squat"), so they never collide with names, notes or other fields.
     */
    fun migrateJson(json: String, mapping: Map<String, String> = map): String {
        if (mapping.isEmpty()) return json
        return quotedString.replace(json) { match ->
            mapping[match.groupValues[1]]?.let { "\"$it\"" } ?: match.value
        }
    }
}
