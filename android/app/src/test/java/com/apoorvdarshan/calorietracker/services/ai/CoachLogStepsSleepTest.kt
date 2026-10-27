package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.ManualStepEntry
import com.apoorvdarshan.calorietracker.models.stepsByDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachLogStepsSleepTest {
    private val today = LocalDate.parse("2026-10-26")

    @Test
    fun aWalkWithStepsBecomesCardioAndSteps() {
        val parsed = CoachLogTool.parse(
            mapOf(
                "items" to listOf(
                    mapOf("type" to "cardio", "name" to "Caminata", "minutes" to 40),
                    mapOf("type" to "pasos", "steps" to "4.000".replace(".", ""))
                )
            ),
            today
        )
        assertTrue(parsed.errors.isEmpty())
        val steps = parsed.items.single { it.kind == CoachLogItem.Kind.STEPS }
        assertEquals(4_000, steps.steps)
        assertEquals("2026-10-26", steps.date)
    }

    @Test
    fun stepsOutOfRangeAreRefused() {
        val parsed = CoachLogTool.parse(mapOf("items" to listOf(mapOf("type" to "steps", "steps" to 0))), today)
        assertTrue(parsed.items.isEmpty())
        assertEquals(1, parsed.errors.size)
    }

    @Test
    fun bedTimeAfterWakeTimeWasTheNightBefore() {
        val item = CoachLogTool.parse(
            mapOf("items" to listOf(mapOf("type" to "sleep", "bed_time" to "23:30", "wake_time" to "06:45"))),
            today
        ).items.single()
        assertEquals("2026-10-25T23:30", item.sleepStart)
        assertEquals("2026-10-26T06:45", item.sleepEnd)
    }

    @Test
    fun hoursAloneEndAtSevenOrTheGivenWakeTime() {
        val plain = CoachLogTool.parse(mapOf("items" to listOf(mapOf("type" to "sueño", "hours" to 6.5))), today).items.single()
        assertEquals("2026-10-26T00:30", plain.sleepStart)
        assertEquals("2026-10-26T07:00", plain.sleepEnd)
        val nap = CoachLogTool.parse(
            mapOf("items" to listOf(mapOf("type" to "nap", "hours" to 1, "wake_time" to "16:00"))),
            today
        ).items.single()
        assertEquals("2026-10-26T15:00", nap.sleepStart)
    }

    @Test
    fun sleepNeedsTimesOrHours() {
        val parsed = CoachLogTool.parse(mapOf("items" to listOf(mapOf("type" to "sleep"))), today)
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun manualStepsAddUpPerDay() {
        val byDay = listOf(
            ManualStepEntry(date = "2026-10-26", steps = 3_000),
            ManualStepEntry(date = "2026-10-26", steps = 1_500),
            ManualStepEntry(date = "2026-10-25", steps = 800),
            ManualStepEntry(date = "not a date", steps = 99)
        ).stepsByDay()
        assertEquals(4_500, byDay[today])
        assertEquals(800, byDay[today.minusDays(1)])
        assertEquals(2, byDay.size)
    }
}
