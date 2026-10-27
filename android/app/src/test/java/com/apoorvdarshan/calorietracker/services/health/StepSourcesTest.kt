package com.apoorvdarshan.calorietracker.services.health

import org.junit.Assert.assertEquals
import org.junit.Test

class StepSourcesTest {
    /** The day that started this: Health Connect kept the phone's count over the watch's. */
    private val watchDay = StepSourceTotals(
        merged = 5_720,
        bySource = mapOf("com.hihonor.health" to 5_720, "com.sec.android.app.shealth" to 11_499)
    )

    @Test
    fun autoUsesTheMostCompleteSource() {
        assertEquals(11_499L, watchDay.chosen(StepSources.AUTO))
    }

    @Test
    fun autoKeepsTheMergedTotalWhenItIsLarger() {
        // Two sources covering different hours: Health Connect's merge adds them up.
        val split = StepSourceTotals(merged = 9_000, bySource = mapOf("a" to 5_000, "b" to 4_000))
        assertEquals(9_000L, split.chosen(StepSources.AUTO))
    }

    @Test
    fun aChosenAppIsUsedWhenItHasSteps() {
        assertEquals(5_720L, watchDay.chosen("com.hihonor.health"))
    }

    @Test
    fun aChosenAppWithoutStepsThatDayFallsBackToAuto() {
        val phoneOnly = StepSourceTotals(merged = 4_200, bySource = mapOf("com.hihonor.health" to 4_200))
        assertEquals(4_200L, phoneOnly.chosen("com.sec.android.app.shealth"))
    }

    @Test
    fun noDataIsZero() {
        assertEquals(0L, StepSourceTotals(0, emptyMap()).chosen(StepSources.AUTO))
    }
}
