package com.apoorvdarshan.calorietracker.services.sleep

import com.apoorvdarshan.calorietracker.models.SleepEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One night as the app counts it: the most complete source for that morning. */
data class SleepNight(
    val wakeDate: LocalDate,
    val start: Instant,
    val end: Instant,
    val minutes: Long,
    /** "manual" or the app package that recorded it. */
    val source: String
)

/** What the day asked of the body, for the sleep advice. */
data class DayLoad(
    /** Strength sets performed today (app workouts). */
    val sets: Int = 0,
    /** Minutes of other exercise today (imported from the watch, or logged cardio). */
    val exerciseMinutes: Int = 0,
    val steps: Int? = null,
    /** Today's food: when it was eaten and its calories. */
    val meals: List<Pair<Instant, Int>> = emptyList()
)

data class SleepAdvice(
    val recommendedMinutes: Long,
    val bedtime: LocalTime,
    val wakeTime: LocalTime,
    val reasons: List<Reason>,
    val lastNight: SleepNight?,
    /** Average of the last seven nights with data, when there are at least three. */
    val weekAverageMinutes: Long?,
    /** When a late heavy meal was logged, the earliest comfortable bedtime after it. */
    val digestUntil: LocalTime?
) {
    enum class Reason { HARD_TRAINING, MODERATE_TRAINING, SLEEP_DEBT, SMALL_SLEEP_DEBT, LATE_HEAVY_MEAL, NO_HISTORY }
}

/**
 * Tonight's sleep advice: the age-based need, plus extra after a hard day and to pay back a
 * short week, timed back from the user's usual wake-up. A late heavy meal does not change the
 * hours, but it pushes the earliest comfortable bedtime and is mentioned. Pure, so it is
 * unit-tested; the numbers follow common guidance (7–9 h for adults, more after hard training)
 * and are advice, not a medical assessment.
 */
object SleepAdvisor {
    private const val NIGHT_MIN_MINUTES = 180L

    /**
     * Nights by wake-up day. A night logged by hand wins over device data for that morning;
     * otherwise the app with the most sleep that morning wins (a watch and a ring often both
     * record the same night). Naps shorter than three hours are left out.
     */
    fun nights(entries: List<SleepEntry>, zone: ZoneId): List<SleepNight> =
        entries.filter { it.end.isAfter(it.start) }
            .groupBy { it.wakeDate(zone) }
            .mapNotNull { (day, sessions) ->
                val manual = sessions.filter { it.source == SleepEntry.MANUAL }
                val chosen = manual.ifEmpty {
                    sessions.groupBy { it.source }.maxByOrNull { (_, s) -> s.sumOf { it.minutes } }?.value.orEmpty()
                }
                val minutes = chosen.sumOf { it.minutes }
                if (minutes < NIGHT_MIN_MINUTES) return@mapNotNull null
                SleepNight(
                    wakeDate = day,
                    start = chosen.minOf { it.start },
                    end = chosen.maxOf { it.end },
                    minutes = minutes,
                    source = chosen.first().source
                )
            }
            .sortedBy { it.wakeDate }

    fun baseNeedMinutes(age: Int): Long = when {
        age in 1..17 -> 540
        age >= 65 -> 450
        else -> 480
    }

    fun advise(
        nights: List<SleepNight>,
        load: DayLoad,
        age: Int,
        today: LocalDate,
        zone: ZoneId
    ): SleepAdvice {
        val reasons = mutableListOf<SleepAdvice.Reason>()
        val base = baseNeedMinutes(age)
        var extra = 0L

        val hard = load.sets >= 16 || load.exerciseMinutes >= 60 || (load.steps ?: 0) >= 15_000
        val moderate = load.sets >= 8 || load.exerciseMinutes >= 30 || (load.steps ?: 0) >= 10_000
        when {
            hard -> { extra += 30; reasons += SleepAdvice.Reason.HARD_TRAINING }
            moderate -> { extra += 15; reasons += SleepAdvice.Reason.MODERATE_TRAINING }
        }

        val week = nights.filter { !it.wakeDate.isAfter(today) && it.wakeDate.isAfter(today.minusDays(8)) }
        val average = week.takeIf { it.size >= 3 }?.map { it.minutes }?.average()?.toLong()
        when {
            average == null -> reasons += SleepAdvice.Reason.NO_HISTORY
            average < base - 60 -> { extra += 30; reasons += SleepAdvice.Reason.SLEEP_DEBT }
            average < base - 30 -> { extra += 15; reasons += SleepAdvice.Reason.SMALL_SLEEP_DEBT }
        }
        val recommended = base + extra.coerceAtMost(60)

        // Usual wake-up: the median of the last week's mornings, else 07:00.
        val wakeTimes = week.map { it.end.atZone(zone).toLocalTime() }.sorted()
        val wake = wakeTimes.getOrNull(wakeTimes.size / 2) ?: LocalTime.of(7, 0)
        val bedtime = wake.minusMinutes(recommended)

        // A heavy meal late in the evening: give it about two and a half hours before lying down.
        val lateHeavy = load.meals
            .filter { (time, kcal) -> time.atZone(zone).toLocalDate() == today && kcal >= 600 && time.atZone(zone).toLocalTime() >= LocalTime.of(20, 0) }
            .maxByOrNull { it.first }
        val digestUntil = lateHeavy?.first?.plus(Duration.ofMinutes(150))?.atZone(zone)?.toLocalTime()
        if (digestUntil != null) reasons += SleepAdvice.Reason.LATE_HEAVY_MEAL

        return SleepAdvice(
            recommendedMinutes = recommended,
            bedtime = bedtime,
            wakeTime = wake,
            reasons = reasons,
            lastNight = nights.lastOrNull { it.wakeDate == today } ?: nights.lastOrNull { it.wakeDate == today.minusDays(1) },
            weekAverageMinutes = average,
            digestUntil = digestUntil
        )
    }

    /** Lines for Coach's prompt. Model input, so it is not localized. */
    fun promptLines(advice: SleepAdvice): List<String> = buildList {
        add("## Sleep")
        advice.lastNight?.let { add("- Last night: ${hm(it.minutes)} (${it.wakeDate})") }
        advice.weekAverageMinutes?.let { add("- Average of the last 7 nights: ${hm(it)}") }
        add("- Tonight's recommendation: ${hm(advice.recommendedMinutes)}, bed around ${advice.bedtime} to wake at ${advice.wakeTime}")
        if (advice.reasons.isNotEmpty()) add("- Why: ${advice.reasons.joinToString(", ") { it.name.lowercase() }}")
        advice.digestUntil?.let { add("- Late heavy meal today: better not to lie down before $it") }
        add("Use sleep when judging fatigue and recovery; it is advice, not a medical assessment.")
    }

    private fun hm(minutes: Long): String = "${minutes / 60} h ${minutes % 60} min"
}
