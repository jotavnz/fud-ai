package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.data.CycleRepository
import com.apoorvdarshan.calorietracker.services.ai.CoachTools
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CycleTest {
    private val periods = listOf(
        CyclePeriod(start = "2026-08-01", end = "2026-08-05"),
        CyclePeriod(start = "2026-08-30", end = "2026-09-03"),
        CyclePeriod(start = "2026-09-28", end = "2026-10-02")
    )

    @Test
    fun averageLengthAndPhase() {
        val status = CycleMath.status(periods, CycleMode.NATURAL, LocalDate.parse("2026-10-27"))
        assertEquals(29, status.averageLength)
        assertEquals(30, status.cycleDay)
        assertEquals(CyclePhase.PREMENSTRUAL, status.phase)
        assertEquals(LocalDate.parse("2026-10-27"), status.nextPeriod)
        assertEquals("medium", status.confidence)
        assertFalse(status.onPeriod)
    }

    @Test
    fun onPeriodAndFollicular() {
        assertEquals(CyclePhase.MENSTRUAL, CycleMath.status(periods, CycleMode.NATURAL, LocalDate.parse("2026-09-30")).phase)
        assertEquals(CyclePhase.FOLLICULAR, CycleMath.status(periods, CycleMode.NATURAL, LocalDate.parse("2026-10-06")).phase)
    }

    @Test
    fun hormonalContraceptionHasNoPhases() {
        val status = CycleMath.status(periods, CycleMode.HORMONAL, LocalDate.parse("2026-10-10"))
        assertNull(status.cycleDay)
        assertNull(status.phase)
    }

    @Test
    fun outOfRangeIntervalsAreIgnored() {
        val withGap = periods + CyclePeriod(start = "2026-05-01")
        assertEquals(listOf(29, 29), CycleMath.intervals(withGap))
    }

    @Test
    fun symptomsDriveAdjustments() {
        assertTrue(CycleMath.trainingAdjustments(CycleSymptoms(date = "2026-10-27")).isEmpty())
        val tips = CycleMath.trainingAdjustments(CycleSymptoms(date = "2026-10-27", cramps = 3, heavyFlow = true))
        assertTrue(tips.any { it.startsWith("Lower the intensity") })
        assertTrue(tips.any { it.startsWith("Strong cramps") })
    }

    @Test
    fun coachSharesNothingWhileOff() {
        val off = CoachTools(weights = emptyList(), bodyFats = emptyList(), foods = emptyList())
        assertFalse(JsonParser.parseString(off.execute("get_cycle")).asJsonObject["enabled"].asBoolean)

        val on = CoachTools(
            weights = emptyList(), bodyFats = emptyList(), foods = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC),
            cycle = CycleRepository.Snapshot(
                CycleSettings(enabled = true),
                periods,
                listOf(CycleSymptoms(date = "2026-09-30", cramps = 2))
            )
        )
        val payload = JsonParser.parseString(on.execute("get_cycle")).asJsonObject
        assertTrue(payload["on_period"].asBoolean)
        assertEquals("menstrual", payload["estimated_phase"].asString)
        assertEquals(2, payload["today_symptoms"].asJsonObject["cramps"].asInt)
        assertEquals(1, payload["training_adjustments"].asJsonArray.size())
    }
}
