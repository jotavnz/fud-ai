package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Fase 3 coach rules in [TrainingInsights]: deload, gentle start, milestones, next session, style. */
class TrainingCompanionRulesTest {
    private val today = LocalDate.of(2026, 10, 28) // Wednesday

    private fun exercise(primary: String, sets: Int) = PlannedExercise(
        itemId = primary, name = primary, level = "", imagePaths = emptyList(), force = "", mechanic = "",
        category = "Strength", equipment = "Machine", primaryMuscles = listOf(primary), secondaryMuscles = emptyList(),
        instructions = emptyList(),
        sets = List(sets) { PlannedSet(weight = "40", weightUnit = WorkoutWeightUnit.KG, reps = "10") }
    )

    private fun state(days: Map<LocalDate, List<PlannedExercise>>, split: WorkoutSplit = WorkoutSplit.FULL_BODY) =
        WorkoutPersistedState(
            dayPlans = days.entries.associate { (date, exercises) ->
                WorkoutDate.key(date) to WorkoutDayPlan(WorkoutDate.key(date), exercises)
            },
            preferences = WorkoutPreferences(split = split)
        )

    @Test
    fun aMuchHeavierWeekThanUsualSuggestsADeload() {
        val days = (1..3).associate { week -> today.minusWeeks(week.toLong()) to listOf(exercise("Chest", 6)) } +
            mapOf(today to listOf(exercise("Chest", 8)), today.minusDays(2) to listOf(exercise("Chest", 6)))
        val load = TrainingInsights.highLoad(state(days), today).single()
        assertEquals(MachineMuscleGroup.CHEST, load.group)
        assertEquals(14, load.weeklySets)
        assertEquals(6, load.usualSets)
    }

    @Test
    fun noDeloadTipWithoutThreeWeeksOfHistory() {
        val days = mapOf(today to listOf(exercise("Chest", 14)), today.minusWeeks(1) to listOf(exercise("Chest", 4)))
        assertTrue(TrainingInsights.highLoad(state(days), today).isEmpty())
    }

    @Test
    fun aNewcomerWithTooManySetsGetsTheGentleStartTip() {
        val tip = TrainingInsights.beginnerVolume(state(mapOf(today to listOf(exercise("Chest", 12)))), today)
        assertEquals(TrainingInsight.BeginnerVolume(MachineMuscleGroup.CHEST, 12), tip)
        assertNull(TrainingInsights.beginnerVolume(state(mapOf(today to listOf(exercise("Chest", 8)))), today))
    }

    @Test
    fun consecutiveTrainingWeeksAreCelebratedAtMilestones() {
        val fourWeeks = (0..3).associate { today.minusWeeks(it.toLong()) to listOf(exercise("Chest", 3)) }
        assertEquals(TrainingInsight.Milestone(4), TrainingInsights.milestone(state(fourWeeks), today))
        val threeWeeks = (0..2).associate { today.minusWeeks(it.toLong()) to listOf(exercise("Chest", 3)) }
        assertNull(TrainingInsights.milestone(state(threeWeeks), today))
    }

    @Test
    fun afterASessionTheNextOneSkipsWhatWasJustTrained() {
        val tip = TrainingInsights.nextSession(state(mapOf(today to listOf(exercise("Chest", 4), exercise("Triceps", 3)))), today)
        val groups = tip!!.restedGroups
        assertTrue(groups.isNotEmpty())
        assertTrue(MachineMuscleGroup.CHEST !in groups && MachineMuscleGroup.TRICEPS !in groups)
        assertNull(TrainingInsights.nextSession(state(mapOf(today to listOf(exercise("Chest", 3)))), today))
    }

    @Test
    fun pushPullLegsDaysAreDetectedAsThatSplit() {
        val kinds = listOf("Chest", "Lats", "Quadriceps")
        val days = (0 until 6).associate { today.minusDays(it * 2L) to listOf(exercise(kinds[it % 3], 4)) }
        assertEquals(TrainingInsight.Style(WorkoutSplit.PUSH_PULL_LEGS), TrainingInsights.style(state(days), today))
        assertNull(TrainingInsights.style(state(days, WorkoutSplit.PUSH_PULL_LEGS), today))
    }
}
