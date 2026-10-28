package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.data.ExerciseItem
import com.apoorvdarshan.calorietracker.data.ExerciseRecord
import com.apoorvdarshan.calorietracker.services.ai.CoachTools
import com.apoorvdarshan.calorietracker.services.voice.RepoFiles
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRolesTest {
    private fun records(path: String): List<ExerciseRecord> =
        Gson().fromJson(RepoFiles.find(path).readText(), object : TypeToken<List<ExerciseRecord>>() {}.type)

    private val catalog: List<ExerciseItem> by lazy {
        (records("src/main/assets/exercises.json") +
            records("src/main/assets/exercises_extra.json")).mapNotNull { ExerciseItem.from(it) }
    }

    @Test
    fun everySuggestedExerciseIsInTheCatalog() {
        val ids = catalog.map { it.id }.toSet()
        val missing = ExerciseRoles.allIds - ids
        assertTrue("missing: $missing", missing.isEmpty())
    }

    @Test
    fun progressionLaddersUseCatalogExercisesOnce() {
        val ids = catalog.map { it.id }.toSet()
        val steps = ExerciseProgressions.ladders.flatten()
        assertTrue("missing: ${steps.toSet() - ids}", ids.containsAll(steps))
        assertTrue("an exercise on two ladders", steps.size == steps.toSet().size)
        assertTrue(ExerciseProgressions.around("Fud_Knee_Push_Up") == ExerciseProgressions.Steps("ed_0493", "ed_0662"))
    }

    @Test
    fun extraExercisesUseTheLibraryVocabularyAndHaveSpanish() {
        val extras = records("src/main/assets/exercises_extra.json")
        val es: Map<String, Map<String, Any>> = Gson().fromJson(
            RepoFiles.find("src/main/assets/exercises_es.json").readText(),
            object : TypeToken<Map<String, Map<String, Any>>>() {}.type
        )
        val muscles = catalog.flatMap { it.primaryMuscles + it.secondaryMuscles }.map { it.lowercase() }.toSet()
        extras.forEach { record ->
            assertTrue(record.id, record.id.startsWith("Fud_"))
            assertTrue(record.id, (record.primaryMuscles + record.secondaryMuscles).all { it in muscles })
            val translation = es.getValue(record.id)
            @Suppress("UNCHECKED_CAST")
            assertEquals(record.id, record.instructions.size, (translation["instructions"] as List<String>).size)
        }
    }

    @Test
    fun warmupFollowsTheDaysMuscles() {
        val legs = ExerciseRoles.warmup(listOf("Quadriceps", "Glutes"), gentle = true).map { it.id }
        assertEquals("Fud_Marching_In_Place", legs.first())
        assertTrue("Chair_Squat" in legs)
        val push = ExerciseRoles.warmup(listOf("Chest"), gentle = false).map { it.id }
        assertEquals("Walking_Treadmill", push.first())
        assertTrue("Arm_Circles" in push && "Fud_Leg_Swings" !in push)
    }

    @Test
    fun cooldownAfterHeavyLegsStartsWithAnEasyWalk() {
        val picks = ExerciseRoles.cooldown(listOf("quadriceps", "hamstrings"), heavyLegs = true).map { it.id }
        assertEquals(listOf("Walking_Treadmill", "ed_0613", "ed_1511", "Fud_Diaphragmatic_Breathing"), picks)
    }

    @Test
    fun coachGetsCatalogExtras() {
        val tools = CoachTools(weights = emptyList(), bodyFats = emptyList(), foods = emptyList(), exerciseLibrary = catalog)
        val result = JsonParser.parseString(
            tools.execute("get_session_extras", mapOf("kind" to "hypopressives"))
        ).asJsonObject
        assertEquals("hypopressive", result["kind"].asString)
        assertEquals(3, result["exercises"].asJsonArray.size())
        assertTrue(result["guidance"].asString.contains("Not a treatment"))
        val error = JsonParser.parseString(tools.execute("get_session_extras", mapOf("kind" to "yoga"))).asJsonObject
        assertTrue(error.has("error"))
    }
}
