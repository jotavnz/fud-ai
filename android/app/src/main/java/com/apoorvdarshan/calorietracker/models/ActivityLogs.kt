package com.apoorvdarshan.calorietracker.models

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Steps the user added by hand (through Coach) for walks their phone or watch did not count.
 * They are added on top of the day's Health Connect steps.
 */
@Serializable
data class ManualStepEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Local day, yyyy-MM-dd. */
    val date: String,
    val steps: Int,
    val note: String? = null
)

/** One night (or nap) of sleep: logged by hand through Coach, or read from Health Connect. */
@Serializable
data class SleepEntry(
    val id: String = UUID.randomUUID().toString(),
    @Serializable(with = InstantSerializer::class)
    val start: Instant,
    @Serializable(with = InstantSerializer::class)
    val end: Instant,
    /** "manual", or the package of the app that recorded it (Samsung Health, a ring's app...). */
    val source: String = MANUAL
) {
    val minutes: Long get() = Duration.between(start, end).toMinutes().coerceAtLeast(0)

    /** The night belongs to the day the user woke up. */
    fun wakeDate(zone: ZoneId): LocalDate = end.atZone(zone).toLocalDate()

    companion object {
        const val MANUAL = "manual"
    }
}

/** Manual steps per day. */
fun List<ManualStepEntry>.stepsByDay(): Map<LocalDate, Int> =
    mapNotNull { entry -> runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { it to entry.steps } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, steps) -> steps.sum() }
