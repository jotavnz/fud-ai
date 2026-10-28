package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSetFeelTest {
    @Test
    fun eachFeelRoundTripsOnEveryScale() {
        for (scale in WorkoutRpeScale.entries) {
            for (feel in WorkoutSetFeel.entries) {
                val stored = feel.rpe(scale)
                assertEquals("$scale $feel", stored, scale.sanitize(stored))
                assertEquals("$scale $feel", feel, WorkoutSetFeel.from(stored, scale))
            }
        }
    }

    @Test
    fun typedRpeReadsAsTheNearestFeel() {
        assertEquals(WorkoutSetFeel.EASY, WorkoutSetFeel.from("5", WorkoutRpeScale.STRENGTH))
        assertEquals(WorkoutSetFeel.GOOD, WorkoutSetFeel.from("7,5", WorkoutRpeScale.STRENGTH))
        assertEquals(WorkoutSetFeel.HARD, WorkoutSetFeel.from("10", WorkoutRpeScale.STRENGTH))
        assertEquals(WorkoutSetFeel.HARD, WorkoutSetFeel.from("19", WorkoutRpeScale.BORG))
        assertNull(WorkoutSetFeel.from("", WorkoutRpeScale.STRENGTH))
    }

    @Test
    fun simpleLoggingIsOnByDefault() {
        assertEquals(true, WorkoutPreferences().simpleLogging)
        assertEquals(false, WorkoutPreferences().loggingGuideDismissed)
    }
}
