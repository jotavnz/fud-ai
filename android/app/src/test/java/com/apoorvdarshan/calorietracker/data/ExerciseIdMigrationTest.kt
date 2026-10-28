package com.apoorvdarshan.calorietracker.data

import com.apoorvdarshan.calorietracker.services.voice.RepoFiles
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIdMigrationTest {
    private val mapping = mapOf("Seated_Cable_Rows" to "ed_0861", "Barbell_Full_Squat" to "ed_0043")

    @Test
    fun replacesOnlyWholeIdValues() {
        val saved = """{"itemId":"Seated_Cable_Rows","name":"Seated_Cable_Rows plus","saved":["Barbell_Full_Squat","Fud_Cat_Cow"]}"""
        assertEquals(
            """{"itemId":"ed_0861","name":"Seated_Cable_Rows plus","saved":["ed_0043","Fud_Cat_Cow"]}""",
            ExerciseIdMigration.migrateJson(saved, mapping)
        )
    }

    @Test
    fun everyMigrationTargetIsInTheCatalog() {
        fun read(path: String): String = RepoFiles.find(path).readText()
        val ids = Gson().fromJson<List<ExerciseRecord>>(
            read("src/main/assets/exercises.json"),
            object : TypeToken<List<ExerciseRecord>>() {}.type
        ).map { it.id }.toSet()
        val migration = Gson().fromJson<Map<String, String>>(
            read("src/main/assets/exercise-id-migration.json"),
            object : TypeToken<Map<String, String>>() {}.type
        )
        val missing = migration.values.toSet() - ids
        assertTrue("missing: $missing", missing.isEmpty())
    }
}
