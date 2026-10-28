package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WarmupReminderTest {
    private val wednesday = LocalDate.of(2026, 10, 28)
    private val on = WorkoutPreferences(warmupReminder = true)

    private fun exercise(muscle: String, category: String = "strength") = PlannedExercise(
        itemId = "ed_0001", name = "Lift", level = "", imagePaths = emptyList(), force = "", mechanic = "",
        category = category, equipment = "", primaryMuscles = listOf(muscle), secondaryMuscles = emptyList(),
        instructions = emptyList()
    )

    private fun state(
        preferences: WorkoutPreferences = on,
        planned: List<PlannedExercise> = emptyList(),
        trainedToday: Boolean = false
    ) = WorkoutPersistedState(
        preferences = preferences,
        dayPlans = mapOf(WorkoutDate.key(wednesday) to WorkoutDayPlan(WorkoutDate.key(wednesday), planned)),
        completedSessions = if (trainedToday) listOf(
            WorkoutSession(diaryDateKey = WorkoutDate.key(wednesday), startedAt = Instant.EPOCH, completedAt = Instant.EPOCH, exercises = emptyList())
        ) else emptyList()
    )

    @Test
    fun plannedLiftsGetAWarmUpForTheirMuscles() {
        val plan = WarmupReminder.plan(state(planned = listOf(exercise("chest"))), wednesday)
        assertNotNull(plan)
        plan!!
        assertEquals(listOf("chest"), plan.muscles)
        assertTrue(plan.picks.any { it.id == "Arm_Circles" })
    }

    @Test
    fun aUsualTrainingDayWithoutAPlanGetsAGeneralWarmUp() {
        val plan = WarmupReminder.plan(state(on.copy(trainingDays = setOf(3))), wednesday)
        assertEquals(emptyList<String>(), plan?.muscles)
    }

    @Test
    fun noReminderWhenOffRestDayOnlyCardioOrAlreadyTrained() {
        assertNull(WarmupReminder.plan(state(WorkoutPreferences(), listOf(exercise("chest"))), wednesday))
        assertNull(WarmupReminder.plan(state(on.copy(trainingDays = setOf(1, 5))), wednesday))
        assertNull(WarmupReminder.plan(state(planned = listOf(exercise("cardiovascular", "cardio"))), wednesday))
        assertNull(WarmupReminder.plan(state(planned = listOf(exercise("chest")), trainedToday = true), wednesday))
    }

    @Test
    fun alarmFiresHalfAnHourBefore() {
        assertEquals(17 to 30, WarmupReminder.alarmTime(18))
        assertEquals(23 to 30, WarmupReminder.alarmTime(0))
    }
}
