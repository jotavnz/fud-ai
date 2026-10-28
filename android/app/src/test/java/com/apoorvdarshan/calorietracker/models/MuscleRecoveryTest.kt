package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.data.ExerciseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class MuscleRecoveryTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 26)
    private val noon = today.atTime(LocalTime.NOON).toInstant(zone)

    private fun exercise(primary: List<String>, sets: Int, equipment: String = "Machine") = PlannedExercise(
        itemId = primary.first(),
        name = primary.first(),
        level = "Beginner",
        imagePaths = emptyList(),
        force = "Push",
        mechanic = "Compound",
        category = "Strength",
        equipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        sets = List(sets) { PlannedSet(weight = "60", weightUnit = WorkoutWeightUnit.KG, reps = "10") }
    )

    private fun state(vararg days: Pair<LocalDate, List<PlannedExercise>>) = WorkoutPersistedState(
        dayPlans = days.associate { (date, exercises) -> WorkoutDate.key(date) to WorkoutDayPlan(WorkoutDate.key(date), exercises) }
    )

    private fun readiness(s: WorkoutPersistedState, cardio: List<CardioBout> = emptyList()) =
        MuscleRecovery.readiness(MuscleRecovery.bouts(s, cardio, noon, zone), noon).associateBy { it.group }

    private fun legCardio(start: java.time.Instant, minutes: Int, thighs: Double) =
        CardioBout(start, minutes, mapOf(MachineMuscleGroup.LEGS to thighs, MachineMuscleGroup.CALVES to thighs * 0.6))

    @Test
    fun aSwimOnTiredShouldersRaisesAShoulderAlert() {
        val s = state(today.minusDays(1) to listOf(exercise(listOf("Shoulders"), 10)))
        val swim = CardioBout(noon.minusSeconds(1800), 30, mapOf(MachineMuscleGroup.SHOULDERS to 6.0, MachineMuscleGroup.BACK to 5.0))
        val alert = OverexertionMonitor.check(s, listOf(swim), 30, noon, zone)
            .filterIsInstance<OverexertionAlert.CardioOnTiredMuscles>().single()
        assertEquals(MachineMuscleGroup.SHOULDERS, alert.group)
    }

    @Test
    fun rowsAfterAHardArmDayAreFlaggedForTheBiceps() {
        // Biceps hammered yesterday; today the user picks back, whose rows also work the biceps.
        val s = state(today.minusDays(1) to listOf(exercise(listOf("Biceps"), 12)))
        val r = readiness(s).values.toList()
        val tired = CrossFatigue.tiredGroups(r)
        assertTrue(MachineMuscleGroup.BICEPS in tired)
        fun item(id: String, primary: String, secondary: List<String>) = ExerciseItem(
            id = id, name = id, level = "beginner", imagePaths = emptyList(), force = "pull", mechanic = "compound",
            category = "strength", equipment = "machine", primaryMuscles = listOf(primary),
            secondaryMuscles = secondary, instructions = emptyList()
        )
        val row = item("Seated_Cable_Rows", "Middle Back", listOf("Biceps"))
        val hits = CrossFatigue.hits(row, MachineMuscleGroup.BACK, tired)
        assertEquals(MachineMuscleGroup.BICEPS, hits.single().first)
        assertTrue(CrossFatigue.hits(item("Pullover", "Lats", emptyList()), MachineMuscleGroup.BACK, tired).isEmpty())
        // Back itself is rested, so it is among the alternatives when another group is tired.
        assertTrue(MachineMuscleGroup.BACK in CrossFatigue.readyAlternatives(r, MachineMuscleGroup.BICEPS))
    }

    @Test
    fun aHardLegDayYesterdayLeavesLegsRecovering() {
        val legs = readiness(state(today.minusDays(1) to listOf(exercise(listOf("Quadriceps"), 10))))[MachineMuscleGroup.LEGS]!!
        // Trained at 18:00 yesterday, 18 h ago, of a 72 h window.
        assertEquals(54, legs.readyInHours)
        assertEquals(25, legs.percent)
        assertEquals(10, legs.weeklySets)
        assertFalse(legs.ready)
    }

    @Test
    fun armsRecoverFasterThanLegsAndUntrainedGroupsAreReady() {
        val r = readiness(state(today.minusDays(2) to listOf(exercise(listOf("Biceps"), 6), exercise(listOf("Quadriceps"), 6))))
        assertTrue(r[MachineMuscleGroup.BICEPS]!!.ready)
        assertFalse(r[MachineMuscleGroup.LEGS]!!.ready)
        assertTrue(r.getValue(MachineMuscleGroup.CHEST).ready)
        assertEquals(100, r.getValue(MachineMuscleGroup.CHEST).percent)
    }

    @Test
    fun aWatchRunLoadsTheLegs() {
        val run = legCardio(noon.minusSeconds(3 * 3600), 60, 8.0)
        val r = readiness(state(), listOf(run))
        assertFalse(r.getValue(MachineMuscleGroup.LEGS).ready)
        assertTrue(r.getValue(MachineMuscleGroup.CHEST).ready)
    }

    @Test
    fun aRunOnTiredLegsRaisesAnAlertOnce() {
        val s = state(today.minusDays(1) to listOf(exercise(listOf("Quadriceps"), 10)))
        val run = legCardio(noon.minusSeconds(3600), 40, 8.0)
        val alerts = OverexertionMonitor.check(s, listOf(run), exerciseMinutesToday = 40, now = noon, zone = zone)
        val tired = alerts.filterIsInstance<OverexertionAlert.CardioOnTiredMuscles>().single()
        assertTrue(tired.percentBefore < 50)
        assertEquals(MachineMuscleGroup.LEGS, tired.group)
        assertEquals("cardio_LEGS_${run.start.epochSecond}", tired.key)
    }

    @Test
    fun anEasyWalkOrRestedLegsRaiseNothing() {
        val walk = legCardio(noon.minusSeconds(3600), 40, 2.0)
        val tiredLegs = state(today.minusDays(1) to listOf(exercise(listOf("Quadriceps"), 10)))
        assertTrue(OverexertionMonitor.check(tiredLegs, listOf(walk), 40, noon, zone).isEmpty())
        val run = legCardio(noon.minusSeconds(3600), 40, 8.0)
        assertTrue(OverexertionMonitor.check(state(), listOf(run), 40, noon, zone).isEmpty())
    }

    @Test
    fun longDaysTooMuchVolumeAndNoRestDayAreFlagged() {
        val everyDay = (0L..6L).map { today.minusDays(it) to listOf(exercise(listOf("Chest"), 4)) }.toTypedArray()
        val alerts = OverexertionMonitor.check(state(*everyDay), emptyList(), exerciseMinutesToday = 160, now = noon, zone = zone)
        assertTrue(alerts.any { it is OverexertionAlert.LongDay })
        assertTrue(alerts.any { it is OverexertionAlert.WeeklyOveruse && it.group == MachineMuscleGroup.CHEST })
        assertTrue(alerts.any { it is OverexertionAlert.NoRestDay })
    }

    @Test
    fun homeTrainingSkipsMachines() {
        fun item(id: String, equipment: String) = ExerciseItem(
            id = id, name = id, level = "beginner", imagePaths = emptyList(), force = "push", mechanic = "compound",
            category = "strength", equipment = equipment, primaryMuscles = listOf("chest"),
            secondaryMuscles = emptyList(), instructions = emptyList()
        )
        val catalog = listOf(item("ed_0577", "machine"), item("Dumbbell_Press", "dumbbell"), item("Pushups", "body only"))
            .associateBy { it.id }
        val home = MachineCoach.candidates(MachineMuscleGroup.CHEST, catalog, homeEquipment = setOf("dumbbell")).map { it.id }
        assertEquals(listOf("Dumbbell_Press", "Pushups"), home)
        val bodyOnly = MachineCoach.candidates(MachineMuscleGroup.CHEST, catalog, homeEquipment = emptySet()).map { it.id }
        assertEquals(listOf("Pushups"), bodyOnly)
        assertEquals("ed_0577", MachineCoach.candidates(MachineMuscleGroup.CHEST, catalog).first().id)
    }
}
