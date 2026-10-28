package com.apoorvdarshan.calorietracker.services.training

import com.apoorvdarshan.calorietracker.models.PantryItem
import com.apoorvdarshan.calorietracker.models.PlannedExercise
import com.apoorvdarshan.calorietracker.models.PlannedSet
import com.apoorvdarshan.calorietracker.models.WorkoutDayPlan
import com.apoorvdarshan.calorietracker.models.WorkoutPersistedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CoachNudgesTest {
    private val today = LocalDate.parse("2026-10-27")

    @Test
    fun onlyItemsDueTodayOrTomorrowAreFlagged() {
        val items = listOf(
            PantryItem(name = "Leche", expiresOn = "2026-10-28"),
            PantryItem(name = "Pan", expiresOn = "2026-10-27"),
            PantryItem(name = "Yogur", expiresOn = "2026-10-26"),
            PantryItem(name = "Arroz", expiresOn = "2027-04-01"),
            PantryItem(name = "Sal")
        )
        assertEquals(listOf("Leche", "Pan"), CoachNudges.expiringSoon(items, today).map { it.name })
    }

    @Test
    fun daysAwayCountsOnlyDaysWithLoggedWork() {
        fun day(reps: String) = WorkoutDayPlan(
            "2026-10-15",
            listOf(PlannedExercise(itemId = "x", name = "Squat", level = "", imagePaths = emptyList(), force = "", mechanic = "", category = "Strength", equipment = "", primaryMuscles = emptyList(), secondaryMuscles = emptyList(), instructions = emptyList(), sets = listOf(PlannedSet(reps = reps))))
        )
        assertNull(CoachNudges.daysSinceLastWorkout(WorkoutPersistedState(), today))
        assertNull(CoachNudges.daysSinceLastWorkout(WorkoutPersistedState(dayPlans = mapOf("2026-10-15" to day(""))), today))
        assertEquals(12L, CoachNudges.daysSinceLastWorkout(WorkoutPersistedState(dayPlans = mapOf("2026-10-15" to day("10"))), today))
    }
}
