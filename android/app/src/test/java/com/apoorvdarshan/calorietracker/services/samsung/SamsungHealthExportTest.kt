package com.apoorvdarshan.calorietracker.services.samsung

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.apoorvdarshan.calorietracker.services.health.StepSourceTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Files shaped like Samsung Health's personal-data download: a metadata line, prefixed
 * column names, UTC times with a separate offset, trailing commas. Kept to the columns the
 * importer reads; real files carry many more, which it ignores.
 */
class SamsungHealthExportTest {
    private val zone = ZoneOffset.ofHours(-4)

    @Test
    fun fileNamesMapToKinds() {
        assertEquals(SamsungHealthExport.Kind.STEPS_DAY, SamsungHealthExport.kindOf("com.samsung.shealth.tracker.pedometer_day_summary.20261027001500.csv"))
        assertEquals(SamsungHealthExport.Kind.SLEEP, SamsungHealthExport.kindOf("jsons/../com.samsung.shealth.sleep.20261027001500.csv"))
        assertEquals(SamsungHealthExport.Kind.EXERCISE, SamsungHealthExport.kindOf("com.samsung.shealth.exercise.20261027001500.csv"))
        assertEquals(SamsungHealthExport.Kind.WEIGHT, SamsungHealthExport.kindOf("com.samsung.health.weight.20261027001500.csv"))
        // Related files with other data must not be read as the main type.
        assertNull(SamsungHealthExport.kindOf("com.samsung.shealth.sleep_stage.20261027001500.csv"))
        assertNull(SamsungHealthExport.kindOf("com.samsung.shealth.exercise.weather.20261027001500.csv"))
        assertNull(SamsungHealthExport.kindOf("com.samsung.shealth.sleep.20261027001500.json"))
    }

    @Test
    fun dailyStepsPreferTheCombinedRow() {
        val csv = """
            com.samsung.shealth.tracker.pedometer_day_summary,6315004,3
            create_sh_ver,step_count,source_type,day_time,distance,create_time,
            6315004,5720,0,1792972800000,4100.5,2026-10-26 23:50:00.000,
            6315004,11499,-2,1792972800000,8200.0,2026-10-26 23:50:00.000,
            6315004,8000,0,1792886400000,6000,2026-10-25 23:50:00.000,
        """.trimIndent()
        val steps = SamsungHealthExport.parse(SamsungHealthExport.Kind.STEPS_DAY, csv, zone).stepsByDay
        assertEquals(11_499, steps[LocalDate.parse("2026-10-26")])
        assertEquals(8_000, steps[LocalDate.parse("2026-10-25")])
    }

    @Test
    fun sleepRowsWithPrefixedColumnsAndQuotes() {
        val csv = """
            com.samsung.shealth.sleep,6315004,7
            "com.samsung.health.sleep.comment, original",com.samsung.health.sleep.start_time,com.samsung.health.sleep.end_time,com.samsung.health.sleep.time_offset,
            "slept ""well"", mostly",2026-10-26 03:30:00.000,2026-10-26 10:45:00.000,UTC-0400,
            ,2026-10-26 03:30:00.000,2026-10-26 10:45:00.000,UTC-0400,
            ,not a time,2026-10-26 10:45:00.000,UTC-0400,
        """.trimIndent()
        val sleep = SamsungHealthExport.parse(SamsungHealthExport.Kind.SLEEP, csv, zone).sleep
        assertEquals(1, sleep.size)
        assertEquals(Instant.parse("2026-10-26T03:30:00Z"), sleep.single().start)
        assertEquals(435, sleep.single().minutes)
        assertEquals(SamsungHealthExport.SOURCE_PACKAGE, sleep.single().source)
    }

    @Test
    fun workoutsKeepTypeDurationAndCalories() {
        val csv = """
            com.samsung.shealth.exercise,6315004,12
            com.samsung.health.exercise.start_time,com.samsung.health.exercise.end_time,com.samsung.health.exercise.exercise_type,com.samsung.health.exercise.duration,com.samsung.health.exercise.calorie,com.samsung.health.exercise.distance
            2026-10-26 22:00:00.000,,1002,2400000,410.6,6100
            2026-10-25 21:00:00.000,2026-10-25 22:00:00.000,10007,,300,
        """.trimIndent()
        val workouts = SamsungHealthExport.parse(SamsungHealthExport.Kind.EXERCISE, csv, zone).workouts
        val run = workouts.first()
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, run.exerciseType)
        assertEquals(40 * 60, run.durationSeconds)
        assertEquals(411, run.activeCalories)
        assertEquals("2026-10-26", run.diaryDateKey)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, workouts[1].exerciseType)
    }

    @Test
    fun weightAndBodyFat() {
        val csv = """
            com.samsung.health.weight,6315004,5
            start_time,weight,body_fat,create_time
            2026-10-20 12:00:00.000,82.4,21.5,2026-10-20 12:00:00.000
            2026-10-21 12:00:00.000,0,,2026-10-21 12:00:00.000
        """.trimIndent()
        val data = SamsungHealthExport.parse(SamsungHealthExport.Kind.WEIGHT, csv, zone)
        assertEquals(82.4, data.weights.single().second, 1e-9)
        assertEquals(0.215, data.bodyFats.single().second, 1e-9)
    }

    @Test
    fun aFileWithoutAHeaderReadsAsNothing() {
        assertTrue(SamsungHealthExport.parse(SamsungHealthExport.Kind.SLEEP, "just,some,text\n1,2,3", zone).isEmpty)
    }

    @Test
    fun importedStepsCountAsSamsungsOwnTotal() {
        // The phone counted 5,720 in Health Connect; the download says the watch saw 11,499.
        val day = StepSourceTotals(merged = 5_720, bySource = mapOf("android" to 5_720L))
            .withSource(SamsungHealthExport.SOURCE_PACKAGE, 11_499)
        assertEquals(11_499L, day.chosen("auto"))
        assertEquals(5_720L, day.withSource("android", 0).chosen("android"))
    }
}
