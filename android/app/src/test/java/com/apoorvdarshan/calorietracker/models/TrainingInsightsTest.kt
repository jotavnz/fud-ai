package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingInsightsTest {
    private val today = LocalDate.of(2026, 10, 26)

    private fun exercise(
        id: String,
        primary: List<String>,
        sets: Int,
        weight: String = "40",
        reps: String = "10",
        secondary: List<String> = emptyList()
    ) = PlannedExercise(
        itemId = id,
        name = id,
        level = "Beginner",
        imagePaths = emptyList(),
        force = "Push",
        mechanic = "Compound",
        category = "Strength",
        equipment = "Machine",
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        instructions = emptyList(),
        sets = List(sets) { PlannedSet(weight = weight, weightUnit = WorkoutWeightUnit.KG, reps = reps) }
    )

    private fun state(vararg days: Pair<LocalDate, List<PlannedExercise>>) = WorkoutPersistedState(
        dayPlans = days.associate { (date, exercises) ->
            WorkoutDate.key(date) to WorkoutDayPlan(WorkoutDate.key(date), exercises)
        }
    )

    @Test
    fun chestTrainedYesterdayIsRecoveringButTodaysSessionIsNot() {
        val s = state(
            today.minusDays(1) to listOf(exercise("Machine_Bench_Press", listOf("Chest"), 4)),
            today to listOf(exercise("Leg_Press", listOf("Quadriceps"), 4))
        )
        val fatigue = TrainingInsights.fatigue(s, today)
        assertEquals(1, fatigue[MachineMuscleGroup.CHEST]?.daysAgo)
        assertNull(fatigue[MachineMuscleGroup.LEGS])
    }

    @Test
    fun plannedButUnloggedSetsDoNotCount() {
        val s = state(today.minusDays(1) to listOf(exercise("Machine_Bench_Press", listOf("Chest"), 5, reps = "")))
        assertTrue(TrainingInsights.fatigue(s, today).isEmpty())
    }

    @Test
    fun crossFatigueMovesDipsBehindPushdownsForTriceps() {
        val s = state(today.minusDays(1) to listOf(exercise("Machine_Bench_Press", listOf("Chest"), 4)))
        val muscles = TrainingInsights.fatiguedMuscles(s, today)
        assertTrue("chest" in muscles)
    }

    @Test
    fun overuseFlagsTooManyWeeklySets() {
        val s = state(
            today.minusDays(1) to listOf(exercise("A", listOf("Chest"), 8)),
            today.minusDays(3) to listOf(exercise("B", listOf("Chest"), 8)),
            today.minusDays(5) to listOf(exercise("C", listOf("Chest"), 8))
        )
        val overuse = TrainingInsights.overuse(s, today)
        assertEquals(MachineMuscleGroup.CHEST, overuse.single().group)
        assertEquals(24, overuse.single().weeklySets)
    }

    @Test
    fun plateauNeedsFourSessionsWithoutProgress() {
        val flat = state(
            today.minusDays(1) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100")),
            today.minusDays(4) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100")),
            today.minusDays(8) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100")),
            today.minusDays(11) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100"))
        )
        assertEquals("Leg_Press", TrainingInsights.plateaus(flat, today, WorkoutWeightUnit.KG).single().exerciseId)

        val rising = state(
            today.minusDays(1) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "105")),
            today.minusDays(4) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100")),
            today.minusDays(8) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100")),
            today.minusDays(11) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100"))
        )
        assertTrue(TrainingInsights.plateaus(rising, today, WorkoutWeightUnit.KG).isEmpty())
    }

    @Test
    fun imbalanceSuggestsBackAfterMostlyPushing() {
        val s = state(
            today.minusDays(2) to listOf(exercise("A", listOf("Chest"), 8)),
            today.minusDays(5) to listOf(exercise("B", listOf("Shoulders"), 6)),
            today.minusDays(6) to listOf(exercise("C", listOf("Lats"), 2))
        )
        val imbalance = TrainingInsights.imbalance(s, today)
        assertNotNull(imbalance)
        assertEquals(MachineMuscleGroup.BACK, imbalance!!.group)
    }

    @Test
    fun neglectedLegsAfterSeveralUpperBodyDays() {
        val s = state(
            today.minusDays(1) to listOf(exercise("A", listOf("Chest"), 4)),
            today.minusDays(3) to listOf(exercise("B", listOf("Lats"), 4)),
            today.minusDays(5) to listOf(exercise("C", listOf("Shoulders"), 4)),
            today.minusDays(20) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 4))
        )
        assertEquals(MachineMuscleGroup.LEGS, TrainingInsights.neglected(s, today)?.group)
    }

    @Test
    fun progressionAddsLoadOnlyAfterHittingTopReps() {
        val prescription = SetPrescription(sets = 3, minReps = 8, maxReps = 12, restSeconds = 90)
        val hit = state(today.minusDays(3) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100", reps = "12")))
        val hint = TrainingInsights.progression(hit, "Leg_Press", today, prescription, WorkoutWeightUnit.KG)!!
        assertTrue(hint.increase)
        assertEquals(102.5, hint.suggestedLoadKg, 0.001)

        val miss = state(today.minusDays(3) to listOf(exercise("Leg_Press", listOf("Quadriceps"), 3, weight = "100", reps = "9")))
        val keep = TrainingInsights.progression(miss, "Leg_Press", today, prescription, WorkoutWeightUnit.KG)!!
        assertFalse(keep.increase)
        assertEquals(100.0, keep.suggestedLoadKg, 0.001)
    }

    @Test
    fun poundsAreConvertedToKilograms() {
        val set = PlannedSet(weight = "100", weightUnit = WorkoutWeightUnit.LBS, reps = "10")
        assertEquals(453.59, WorkoutVolume.setVolumeKg(set, WorkoutWeightUnit.KG), 0.01)
    }
}
