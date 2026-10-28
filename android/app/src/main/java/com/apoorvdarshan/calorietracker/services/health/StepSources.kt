package com.apoorvdarshan.calorietracker.services.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * Steps from one window: Health Connect's merged total and each app's own total.
 *
 * Several apps often count the same walk: the phone's own health app (Honor Health, Google Fit)
 * and the watch's app (Samsung Health for a Galaxy Watch). Health Connect merges overlapping
 * steps by app priority, so when the phone app ranks first a day worn with the watch can show
 * only the steps the phone saw. Each app's own total is never double-counted, so the most
 * complete one is a safe daily figure.
 */
data class StepSourceTotals(val merged: Long, val bySource: Map<String, Long>) {
    fun chosen(preference: String): Long = StepSources.choose(merged, bySource, preference)

    /** With an app's count from elsewhere (a Samsung Health download); the larger count of that app wins. */
    fun withSource(pkg: String, steps: Long): StepSourceTotals =
        if (steps <= 0) this else copy(bySource = bySource + (pkg to maxOf(bySource[pkg] ?: 0L, steps)))
}

object StepSources {
    /** Use the most complete count: Health Connect's merged total or the largest single app. */
    const val AUTO = "auto"

    /**
     * The day's steps for [preference] (AUTO or an app package). A chosen app that has no steps
     * that day (the watch stayed home) falls back to AUTO, so the day is never left at zero.
     */
    fun choose(merged: Long, bySource: Map<String, Long>, preference: String): Long {
        if (preference != AUTO) bySource[preference]?.takeIf { it > 0 }?.let { return it }
        return maxOf(merged, bySource.values.maxOrNull() ?: 0L)
    }
}

/** Merged and per-app step totals between [start] and [end], leaving out Fud AI's own records. */
internal suspend fun HealthConnectClient.stepSourceTotals(start: Instant, end: Instant, ownPackage: String): StepSourceTotals {
    val range = TimeRangeFilter.between(start, end)
    val merged = aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = range))
    val bySource = merged.dataOrigins
        .filter { !isFudAiHealthOrigin(it.packageName, ownPackage) }
        .associate { origin ->
            origin.packageName to (
                aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = range,
                        dataOriginFilter = setOf(origin)
                    )
                )[StepsRecord.COUNT_TOTAL] ?: 0L
                )
        }
    return StepSourceTotals(merged[StepsRecord.COUNT_TOTAL] ?: 0L, bySource)
}

/**
 * [stepSourceTotals] for each local day from [firstDay] through [lastDay] (at most a year, one
 * Health Connect query per app plus one merged), keyed by day.
 */
internal suspend fun HealthConnectClient.dailyStepSourceTotals(
    firstDay: LocalDate,
    lastDay: LocalDate,
    ownPackage: String
): Map<LocalDate, StepSourceTotals> {
    val range = TimeRangeFilter.between(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay())
    suspend fun perDay(origins: Set<DataOrigin>): Map<LocalDate, Long> =
        aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = range,
                timeRangeSlicer = Period.ofDays(1),
                dataOriginFilter = origins
            )
        ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }

    val merged = perDay(emptySet())
    val origins = aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = range))
        .dataOrigins
        .filter { !isFudAiHealthOrigin(it.packageName, ownPackage) }
    val perSource = origins.associate { it.packageName to perDay(setOf(it)) }
    return (merged.keys + perSource.values.flatMap { it.keys }).associateWith { day ->
        StepSourceTotals(
            merged = merged[day] ?: 0L,
            bySource = perSource.mapValues { (_, days) -> days[day] ?: 0L }.filterValues { it > 0 }
        )
    }
}

/** Coach's view of the step count, so a day total from the watch is not added on top of steps already counted. */
object StepPromptLines {
    data class Day(val label: String, val counted: Int?, val manual: Int, val bySource: Map<String, Long>)

    fun lines(days: List<Day>, appLabel: (String) -> String): List<String> {
        if (days.isEmpty()) return emptyList()
        return listOf("## Steps (Health Connect plus steps added through Coach)") + days.map { day ->
            val sources = day.bySource.entries.sortedByDescending { it.value }
                .joinToString(", ") { "${appLabel(it.key)} ${it.value}" }
            val counted = day.counted?.toString() ?: "unknown (Health Connect not readable)"
            buildString {
                append("- ${day.label}: $counted steps counted")
                if (sources.isNotEmpty()) append(" (by app: $sources)")
                if (day.manual > 0) append("; ${day.manual} of them added through Coach")
            }
        } + "Watch apps (Samsung Health) send steps to Health Connect only when they sync, so a fresh walk can be missing for a while. When the user reports a day total, add only the difference to what is counted here."
    }
}
