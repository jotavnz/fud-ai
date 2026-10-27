package com.apoorvdarshan.calorietracker.services.samsung

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.apoorvdarshan.calorietracker.models.ImportedHealthWorkout
import com.apoorvdarshan.calorietracker.models.SleepEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** What a Samsung Health "Download personal data" folder holds that the app uses. */
data class SamsungHealthData(
    val stepsByDay: Map<LocalDate, Int> = emptyMap(),
    val sleep: List<SleepEntry> = emptyList(),
    val workouts: List<ImportedHealthWorkout> = emptyList(),
    /** Weigh-ins in kg. */
    val weights: List<Pair<Instant, Double>> = emptyList(),
    /** Body fat as a 0–1 fraction. */
    val bodyFats: List<Pair<Instant, Double>> = emptyList()
) {
    val isEmpty: Boolean get() = stepsByDay.isEmpty() && sleep.isEmpty() && workouts.isEmpty() && weights.isEmpty()

    val oldest: LocalDate?
        get() = listOfNotNull(
            stepsByDay.keys.minOrNull(),
            sleep.minOfOrNull { it.end }?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            workouts.minOfOrNull { it.startedAt }?.atZone(ZoneId.systemDefault())?.toLocalDate(),
            weights.minOfOrNull { it.first }?.atZone(ZoneId.systemDefault())?.toLocalDate()
        ).minOrNull()

    operator fun plus(other: SamsungHealthData) = SamsungHealthData(
        stepsByDay = (stepsByDay.keys + other.stepsByDay.keys).associateWith { maxOf(stepsByDay[it] ?: 0, other.stepsByDay[it] ?: 0) },
        sleep = sleep + other.sleep,
        workouts = workouts + other.workouts,
        weights = weights + other.weights,
        bodyFats = bodyFats + other.bodyFats
    )
}

/**
 * Reads the CSV files of Samsung Health's personal-data download. Each file is one data type
 * ("com.samsung.shealth.sleep.<date>.csv"); its first line is metadata, the second the column
 * names (often prefixed, "com.samsung.health.sleep.start_time"), times are UTC
 * ("2026-10-20 23:41:00.000") with a separate "time_offset", and column sets vary between app
 * versions. So columns are found by suffix and anything unreadable is skipped, never guessed.
 * Pure, so it is unit-tested.
 */
object SamsungHealthExport {
    const val SOURCE_PACKAGE = "com.sec.android.app.shealth"

    enum class Kind { STEPS_DAY, SLEEP, EXERCISE, WEIGHT }

    /** The data type of an export file by its name, or null for files the app does not use. */
    fun kindOf(fileName: String): Kind? {
        val name = fileName.substringAfterLast('/').lowercase()
        if (!name.endsWith(".csv")) return null
        // "<type>.<yyyyMMddHHmmss>.csv": the type is everything before the timestamp.
        val type = name.removeSuffix(".csv").replace(Regex("""\.\d{8,}$"""), "")
        return when (type) {
            // step_daily_trend marks the phone+watch total (source_type -2); the day summary is a fallback.
            "com.samsung.shealth.tracker.pedometer_day_summary", "com.samsung.shealth.step_daily_trend" -> Kind.STEPS_DAY
            "com.samsung.shealth.sleep", "com.samsung.health.sleep" -> Kind.SLEEP
            "com.samsung.shealth.exercise", "com.samsung.health.exercise" -> Kind.EXERCISE
            "com.samsung.health.weight", "com.samsung.shealth.weight" -> Kind.WEIGHT
            else -> null
        }
    }

    fun parse(kind: Kind, csv: String, zone: ZoneId = ZoneId.systemDefault()): SamsungHealthData {
        val table = Table.read(csv) ?: return SamsungHealthData()
        return when (kind) {
            Kind.STEPS_DAY -> SamsungHealthData(stepsByDay = steps(table, zone))
            Kind.SLEEP -> SamsungHealthData(sleep = sleep(table))
            Kind.EXERCISE -> SamsungHealthData(workouts = workouts(table, zone))
            Kind.WEIGHT -> weights(table)
        }
    }

    // -- Data types -------------------------------------------------------------------------

    /**
     * Daily totals. A day can have several rows (phone, watch, and the combined total with
     * source_type -2); the combined row wins, else the largest.
     */
    private fun steps(table: Table, zone: ZoneId): Map<LocalDate, Int> {
        val combined = mutableMapOf<LocalDate, Int>()
        val largest = mutableMapOf<LocalDate, Int>()
        table.rows.forEach { row ->
            val day = table.time(row, "day_time")?.let { dayOf(it) } ?: table.time(row, "create_time")?.atZone(zone)?.toLocalDate()
                ?: return@forEach
            val count = (table.number(row, "step_count") ?: table.number(row, "count"))
                ?.roundToInt()?.takeIf { it in 1..200_000 } ?: return@forEach
            if (table.text(row, "source_type") == "-2") combined[day] = maxOf(combined[day] ?: 0, count)
            largest[day] = maxOf(largest[day] ?: 0, count)
        }
        return largest.keys.associateWith { combined[it] ?: largest.getValue(it) }
    }

    /** day_time is midnight UTC of the local day. */
    private fun dayOf(instant: Instant): LocalDate = instant.atZone(ZoneOffset.UTC).toLocalDate()

    private fun sleep(table: Table): List<SleepEntry> = table.rows.mapNotNull { row ->
        val start = table.time(row, "start_time") ?: return@mapNotNull null
        val end = table.time(row, "end_time") ?: return@mapNotNull null
        val minutes = java.time.Duration.between(start, end).toMinutes()
        if (minutes !in 15..(18 * 60)) return@mapNotNull null
        SleepEntry(id = "samsung-sleep-${start.epochSecond}", start = start, end = end, source = SOURCE_PACKAGE)
    }.distinctBy { it.id }

    private fun workouts(table: Table, zone: ZoneId): List<ImportedHealthWorkout> = table.rows.mapNotNull { row ->
        val start = table.time(row, "start_time") ?: return@mapNotNull null
        val end = table.time(row, "end_time")
            ?: table.number(row, "duration")?.let { start.plusMillis(it.toLong()) }
            ?: return@mapNotNull null
        val seconds = java.time.Duration.between(start, end).seconds.toInt()
        if (seconds !in 60..(24 * 3600)) return@mapNotNull null
        val type = table.number(row, "exercise_type")?.toInt()
        ImportedHealthWorkout(
            id = "samsung-exercise-${start.epochSecond}",
            exerciseType = healthConnectType(type),
            startedAt = start,
            endedAt = end,
            durationSeconds = seconds,
            activeCalories = table.number(row, "calorie")?.roundToInt()?.takeIf { it in 1..5_000 },
            distanceMeters = table.number(row, "distance")?.takeIf { it > 0 },
            sourcePackage = SOURCE_PACKAGE,
            sourceName = "Samsung Health",
            diaryDateKey = start.atZone(zone).toLocalDate().toString()
        )
    }.distinctBy { it.id }

    private fun weights(table: Table): SamsungHealthData {
        val weights = mutableListOf<Pair<Instant, Double>>()
        val bodyFats = mutableListOf<Pair<Instant, Double>>()
        table.rows.forEach { row ->
            val time = table.time(row, "start_time") ?: table.time(row, "create_time") ?: return@forEach
            table.number(row, "weight")?.takeIf { it in 20.0..400.0 }?.let { weights += time to it }
            table.number(row, "body_fat")?.takeIf { it in 2.0..70.0 }?.let { bodyFats += time to it / 100.0 }
        }
        return SamsungHealthData(weights = weights.distinctBy { it.first }, bodyFats = bodyFats.distinctBy { it.first })
    }

    /**
     * Samsung's exercise codes to Health Connect's, for the ones that load muscles in recovery
     * and alerts; the rest become a generic workout (minutes only).
     */
    fun healthConnectType(samsung: Int?): Int = when (samsung) {
        1001 -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        1002 -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        11007 -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        13001 -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
        14001 -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        15005 -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
        15006 -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
        15003 -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
        10007, 10008, 10009, 10010, 10011, 10012, 10013, 10014, 10015 -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    // -- CSV --------------------------------------------------------------------------------

    private val UTC_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")

    private class Table(val columns: List<String>, val rows: List<List<String>>) {
        /** Index of the column named [name] or ending in ".[name]". */
        fun index(name: String): Int? = columns.indexOfFirst { it == name || it.endsWith(".$name") }.takeIf { it >= 0 }

        fun text(row: List<String>, name: String): String? = index(name)?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }

        fun number(row: List<String>, name: String): Double? = text(row, name)?.toDoubleOrNull()?.takeIf { it.isFinite() }

        /** Epoch milliseconds, or a UTC "yyyy-MM-dd HH:mm:ss.SSS" time. */
        fun time(row: List<String>, name: String): Instant? {
            val raw = text(row, name) ?: return null
            raw.toLongOrNull()?.let { if (it > 100_000_000_000L) return Instant.ofEpochMilli(it) }
            return runCatching { LocalDateTime.parse(raw, UTC_TIME).toInstant(ZoneOffset.UTC) }.getOrNull()
        }

        companion object {
            fun read(csv: String): Table? {
                val lines = csv.removePrefix("﻿").lineSequence().filter { it.isNotBlank() }.toList()
                // The header is the first line with a time column; the line before it is metadata.
                val headerAt = lines.indexOfFirst { line ->
                    val fields = split(line)
                    fields.any { it.endsWith("start_time") || it.endsWith("day_time") || it.endsWith("create_time") }
                }
                if (headerAt < 0) return null
                val columns = split(lines[headerAt]).map { it.trim() }
                return Table(columns, lines.drop(headerAt + 1).map(::split))
            }

            /** One CSV line; handles quoted fields with commas and doubled quotes. */
            fun split(line: String): List<String> {
                val out = mutableListOf<String>()
                val field = StringBuilder()
                var quoted = false
                var i = 0
                while (i < line.length) {
                    val c = line[i]
                    when {
                        c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                        c == '"' -> quoted = !quoted
                        c == ',' && !quoted -> { out += field.toString(); field.clear() }
                        else -> field.append(c)
                    }
                    i++
                }
                out += field.toString()
                return out
            }
        }
    }
}
