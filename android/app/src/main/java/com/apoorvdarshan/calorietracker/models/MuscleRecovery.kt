package com.apoorvdarshan.calorietracker.models

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cardio that loads the legs (a run, a hike, a ride), usually imported from a watch. */
data class CardioBout(
    val start: Instant,
    val minutes: Int,
    /** Leg work per hour in effective sets: running ≈ 8, cycling ≈ 5, walking ≈ 2. */
    val legSetsPerHour: Double,
    /** Activity name resource for messages, when known. */
    val labelRes: Int? = null
) {
    val legSets: Double get() = minutes / 60.0 * legSetsPerHour
}

/** How ready one muscle group is to train again. */
data class MuscleReadiness(
    val group: MachineMuscleGroup,
    /** 100 = fully recovered. */
    val percent: Int,
    val readyInHours: Int,
    /** Effective sets in the last seven days (primary 1, secondary 0.5, leg cardio included). */
    val weeklySets: Int,
    val lastWorked: Instant?
) {
    val ready: Boolean get() = readyInHours == 0
}

/**
 * Per-muscle recovery from the workout diary and leg-heavy cardio. Each bout of work leaves
 * fatigue proportional to its sets (ten effective sets is a full session) that fades over the
 * group's recovery window: about 72 h for big lower-body and back muscles, 60 h for chest,
 * 48 h for shoulders and 36 h for arms, calves and abs; lighter work recovers sooner. These
 * are general-population heuristics, not a measurement.
 */
object MuscleRecovery {
    const val FULL_SESSION_SETS = 10.0
    /** Work below this many effective sets does not hold a group back. */
    private const val MEANINGFUL_SETS = 3.0
    /** Diary days carry no time; a past day's training counts as done at this hour. */
    private val DEFAULT_TRAINING_TIME: LocalTime = LocalTime.of(18, 0)

    val legGroups = setOf(MachineMuscleGroup.LEGS, MachineMuscleGroup.HAMSTRINGS, MachineMuscleGroup.GLUTES, MachineMuscleGroup.CALVES)

    fun recoveryHours(group: MachineMuscleGroup): Int = when (group) {
        MachineMuscleGroup.LEGS, MachineMuscleGroup.HAMSTRINGS, MachineMuscleGroup.GLUTES, MachineMuscleGroup.BACK -> 72
        MachineMuscleGroup.CHEST -> 60
        MachineMuscleGroup.SHOULDERS -> 48
        else -> 36
    }

    /** One bout of work on one group. */
    data class Bout(val group: MachineMuscleGroup, val at: Instant, val sets: Double)

    /**
     * Bouts from the diary (a day's sets per group, timed at the day's completed session, else
     * [DEFAULT_TRAINING_TIME], never later than [now]) and from leg-heavy cardio.
     */
    fun bouts(
        state: WorkoutPersistedState,
        cardio: List<CardioBout>,
        now: Instant,
        zone: ZoneId,
        days: Long = 7
    ): List<Bout> {
        val today = now.atZone(zone).toLocalDate()
        val sessionTimes = state.completedSessions.groupBy { it.diaryDateKey }
            .mapValues { (_, sessions) -> sessions.maxOf { it.completedAt } }
        val fromDiary = TrainingInsights.trainedDays(state, today)
            .filter { (date, _) -> ChronoUnit.DAYS.between(date, today) <= days }
            .flatMap { (date, plan) ->
                val at = sessionTimes[WorkoutDate.key(date)]
                    ?: minOf(date.atTime(DEFAULT_TRAINING_TIME).atZone(zone).toInstant(), now)
                TrainingInsights.groupSets(plan).map { (group, sets) -> Bout(group, at, sets) }
            }
        val fromCardio = cardio
            .filter { !it.start.isAfter(now) && Duration.between(it.start, now).toDays() <= days && it.legSets > 0 }
            .flatMap { bout ->
                legGroups.map { group ->
                    // Calves and glutes share the load; the thighs carry most of it.
                    val share = if (group == MachineMuscleGroup.LEGS) 1.0 else 0.6
                    Bout(group, bout.start, bout.legSets * share)
                }
            }
        return fromDiary + fromCardio
    }

    /** Readiness of every group at [now]. */
    fun readiness(bouts: List<Bout>, now: Instant): List<MuscleReadiness> =
        MachineMuscleGroup.entries.map { group ->
            val mine = bouts.filter { it.group == group && !it.at.isAfter(now) }
            var fatigue = 0.0
            var readyIn = 0.0
            for (bout in mine) {
                val intensity = min(1.0, bout.sets / FULL_SESSION_SETS)
                val needed = recoveryHours(group) * (0.4 + 0.6 * intensity)
                val elapsed = Duration.between(bout.at, now).toMinutes() / 60.0
                val remaining = max(0.0, needed - elapsed)
                fatigue += intensity * (remaining / needed)
                if (bout.sets >= MEANINGFUL_SETS) readyIn = max(readyIn, remaining)
            }
            val weekAgo = now.minus(Duration.ofDays(7))
            MuscleReadiness(
                group = group,
                percent = (100 * (1 - min(1.0, fatigue))).roundToInt(),
                readyInHours = kotlin.math.ceil(readyIn).toInt(),
                weeklySets = mine.filter { it.at.isAfter(weekAgo) }.sumOf { it.sets }.roundToInt(),
                lastWorked = mine.filter { it.sets >= MEANINGFUL_SETS }.maxOfOrNull { it.at }
            )
        }

    /** Lines for Coach's prompt. Model input, so it is not localized. */
    fun promptLines(readiness: List<MuscleReadiness>): List<String> = buildList {
        val tired = readiness.filter { !it.ready }.sortedBy { it.percent }
        add("## Muscle recovery (estimated from the diary and watch cardio)")
        if (tired.isEmpty()) {
            add("- Every muscle group is recovered.")
        } else {
            tired.forEach { add("- ${it.group.name.lowercase()}: ${it.percent}% recovered, ready in ~${it.readyInHours} h, ${it.weeklySets} sets this week") }
        }
        readiness.filter { it.weeklySets > TrainingInsights.WEEKLY_SET_LIMIT }.forEach {
            add("- Over the weekly volume that still builds muscle (~10–20 sets): ${it.group.name.lowercase()} ${it.weeklySets} sets")
        }
        add("When the user wants to train a group that is not recovered, say so, suggest rested groups or active recovery, and warn about overreaching.")
    }

    /** Days in the last week with training or at least 20 minutes of cardio, today included. */
    fun activeDays(state: WorkoutPersistedState, cardio: List<CardioBout>, today: LocalDate, zone: ZoneId): Int {
        val trained = TrainingInsights.trainedDays(state, today).map { it.first }
        val moved = cardio.filter { it.minutes >= 20 }.map { it.start.atZone(zone).toLocalDate() }
        return (trained + moved).filter { ChronoUnit.DAYS.between(it, today) in 0..6 }.toSet().size
    }
}
