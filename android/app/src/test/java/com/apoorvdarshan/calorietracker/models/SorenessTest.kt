package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.services.ai.CoachLogTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SorenessTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 26)
    private val noon = today.atTime(LocalTime.NOON).toInstant(zone)

    private fun exercise(primary: String, sets: Int, performed: Boolean = true) = PlannedExercise(
        itemId = primary, name = primary, level = "Beginner", imagePaths = emptyList(), force = "Pull",
        mechanic = "Isolation", category = "Strength", equipment = "Machine",
        primaryMuscles = listOf(primary), secondaryMuscles = emptyList(), instructions = emptyList(),
        sets = List(sets) { PlannedSet(weight = "20", weightUnit = WorkoutWeightUnit.KG, reps = if (performed) "10" else "") }
    )

    private fun state(vararg days: Pair<LocalDate, List<PlannedExercise>>) = WorkoutPersistedState(
        dayPlans = days.associate { (date, exercises) -> WorkoutDate.key(date) to WorkoutDayPlan(WorkoutDate.key(date), exercises) }
    )

    private fun ready(group: MachineMuscleGroup) =
        MuscleReadiness(group, percent = 100, readyInHours = 0, weeklySets = 8, lastWorked = noon.minus(Duration.ofDays(3)))

    @Test
    fun aSorenessReportLocksAReadyGroupForItsHours() {
        val report = SorenessReport(group = "BICEPS", reportedAt = noon.minus(Duration.ofHours(2)), extraHours = 24)
        val biceps = Soreness.apply(listOf(ready(MachineMuscleGroup.BICEPS)), listOf(report), noon).single()
        assertEquals(22, biceps.readyInHours)
        assertEquals(40, biceps.percent)
        assertEquals(report.lockedUntil, biceps.lockedUntil)
        // Once the report runs out the estimate rules again.
        val later = Soreness.apply(listOf(ready(MachineMuscleGroup.BICEPS)), listOf(report), noon.plus(Duration.ofHours(23)))
        assertNull(later.single().lockedUntil)
    }

    @Test
    fun groupNamesWorkInSpanishAndEnglish() {
        assertEquals(MachineMuscleGroup.BICEPS, Soreness.groupFromName("Bíceps"))
        assertEquals(MachineMuscleGroup.LEGS, Soreness.groupFromName("cuádriceps"))
        assertEquals(MachineMuscleGroup.CALVES, Soreness.groupFromName("gemelos"))
        assertEquals(MachineMuscleGroup.BACK, Soreness.groupFromName("back"))
        assertNull(Soreness.groupFromName("nariz"))
    }

    @Test
    fun coachTurnsStillSoreIntoASorenessItem() {
        val item = CoachLogTool.parse(
            mapOf("items" to listOf(mapOf("type" to "dolor", "muscle" to "bíceps", "extra_hours" to 200))),
            today
        ).items.single()
        assertEquals("BICEPS", item.sorenessGroup)
        assertEquals(Soreness.MAX_EXTRA_HOURS, item.extraHours)
        val missing = CoachLogTool.parse(mapOf("items" to listOf(mapOf("type" to "soreness"))), today)
        assertTrue(missing.items.isEmpty())
    }

    @Test
    fun aPlannedSessionOnATiredGroupAsksToRestIt() {
        val s = state(
            today.minusDays(1) to listOf(exercise("Biceps", 12)),
            today to listOf(exercise("Biceps", 3, performed = false))
        )
        // 08:00, 14 h after yesterday's 18:00 session: the biceps are still under half recovered.
        val morning = today.atTime(8, 0).toInstant(zone)
        val alerts = OverexertionMonitor.check(s, emptyList(), 0, morning, zone)
        val planned = alerts.filterIsInstance<OverexertionAlert.PlannedOnTired>().single()
        assertEquals(MachineMuscleGroup.BICEPS, planned.group)
    }

    @Test
    fun aRecoveredGroupGetsOneReadyNotice() {
        val s = state(today.minusDays(3) to listOf(exercise("Biceps", 8)))
        val ready = OverexertionMonitor.check(s, emptyList(), 0, noon, zone).filterIsInstance<OverexertionAlert.ReadyAgain>()
        assertEquals(MachineMuscleGroup.BICEPS, ready.single().group)
        assertNotNull(ready.single().key)
        // Still locked by a soreness report: no "ready" yet.
        val sore = SorenessReport(group = "BICEPS", reportedAt = noon.minus(Duration.ofHours(1)))
        assertTrue(OverexertionMonitor.check(s, emptyList(), 0, noon, zone, listOf(sore)).none { it is OverexertionAlert.ReadyAgain })
    }
}
