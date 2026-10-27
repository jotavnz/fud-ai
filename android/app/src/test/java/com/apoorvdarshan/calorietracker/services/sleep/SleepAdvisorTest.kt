package com.apoorvdarshan.calorietracker.services.sleep

import com.apoorvdarshan.calorietracker.models.SleepEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SleepAdvisorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-10-26")

    /** A night ending on [day]: a bed time after the wake time was the evening before. */
    private fun night(day: LocalDate, bed: String, wake: String, source: String = "com.sec.android.app.shealth") = SleepEntry(
        start = (if (LocalTime.parse(bed) > LocalTime.parse(wake)) day.minusDays(1) else day)
            .atTime(LocalTime.parse(bed)).toInstant(zone),
        end = day.atTime(LocalTime.parse(wake)).toInstant(zone),
        source = source
    )

    @Test
    fun theMostCompleteSourceWinsAndManualBeatsDevices() {
        val watch = night(today, "23:00", "06:30")
        val ring = night(today, "23:30", "06:00", source = "com.ouraring.oura")
        assertEquals(450L, SleepAdvisor.nights(listOf(watch, ring), zone).single().minutes)
        val manual = night(today, "22:00", "07:00", source = SleepEntry.MANUAL)
        assertEquals(SleepEntry.MANUAL, SleepAdvisor.nights(listOf(watch, ring, manual), zone).single().source)
    }

    @Test
    fun shortNapsAreNotNights() {
        val nap = SleepEntry(start = today.atTime(15, 0).toInstant(zone), end = today.atTime(16, 0).toInstant(zone))
        assertTrue(SleepAdvisor.nights(listOf(nap), zone).isEmpty())
    }

    @Test
    fun hardTrainingAndAShortWeekAddUpToAnHourAtMost() {
        val week = (0L..6L).map { night(today.minusDays(it), "01:00", "06:30") } // 5 h 30 a night
        val nights = SleepAdvisor.nights(week, zone)
        val advice = SleepAdvisor.advise(nights, DayLoad(sets = 20), age = 30, today = today, zone = zone)
        assertEquals(480L + 60L, advice.recommendedMinutes)
        assertEquals(LocalTime.of(6, 30), advice.wakeTime)
        assertEquals(LocalTime.of(21, 30), advice.bedtime)
        assertTrue(SleepAdvice.Reason.HARD_TRAINING in advice.reasons)
        assertTrue(SleepAdvice.Reason.SLEEP_DEBT in advice.reasons)
    }

    @Test
    fun aRestfulWeekWithoutTrainingKeepsTheBaseNeed() {
        val nights = SleepAdvisor.nights((0L..6L).map { night(today.minusDays(it), "23:00", "07:00") }, zone)
        val advice = SleepAdvisor.advise(nights, DayLoad(), age = 30, today = today, zone = zone)
        assertEquals(480L, advice.recommendedMinutes)
        assertTrue(advice.reasons.isEmpty())
        assertNull(advice.digestUntil)
    }

    @Test
    fun aLateHeavyDinnerSetsTheEarliestBedtime() {
        val dinner = today.atTime(21, 30).toInstant(zone) to 900
        val snack = today.atTime(22, 0).toInstant(zone) to 150
        val advice = SleepAdvisor.advise(emptyList(), DayLoad(meals = listOf(dinner, snack)), age = 30, today = today, zone = zone)
        assertEquals(LocalTime.of(0, 0), advice.digestUntil)
        assertTrue(SleepAdvice.Reason.LATE_HEAVY_MEAL in advice.reasons)
        assertTrue(SleepAdvice.Reason.NO_HISTORY in advice.reasons)
    }

    @Test
    fun ageChangesTheBaseNeed() {
        assertEquals(540L, SleepAdvisor.baseNeedMinutes(16))
        assertEquals(450L, SleepAdvisor.baseNeedMinutes(70))
    }
}
