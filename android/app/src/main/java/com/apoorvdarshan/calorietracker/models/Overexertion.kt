package com.apoorvdarshan.calorietracker.models

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/** A reason to tell the user they may be pushing too hard. [key] makes each alert fire once. */
sealed interface OverexertionAlert {
    val key: String

    /** Cardio or sport (often from the watch) on a muscle group still recovering from training. */
    data class CardioOnTiredMuscles(val bout: CardioBout, val group: MachineMuscleGroup, val percentBefore: Int) : OverexertionAlert {
        override val key: String get() = "cardio_${group.name}_${bout.start.epochSecond}"
    }

    /** A very long day of exercise. */
    data class LongDay(val minutes: Int, val day: String) : OverexertionAlert {
        override val key: String get() = "long_day_$day"
    }

    /** Far more weekly sets on a group than still build muscle. */
    data class WeeklyOveruse(val group: MachineMuscleGroup, val sets: Int, val week: String) : OverexertionAlert {
        override val key: String get() = "overuse_${group.name}_$week"
    }

    /** Training every day for a week without a rest day. */
    data class NoRestDay(val day: String) : OverexertionAlert {
        override val key: String get() = "no_rest_$day"
    }
}

/**
 * Overreaching checks over the diary and imported cardio, for gym and home training alike.
 * Thresholds sit a little above the in-app suggestions so notifications only come when it
 * matters: a 15+ minute activity that works a group under half recovered (a run on tired legs,
 * a swim on tired shoulders), 150+ minutes of exercise in a day, more than 25 weekly sets on a
 * group, and seven active days in a row.
 */
object OverexertionMonitor {
    private const val TIRED_PERCENT = 50
    private const val MIN_CARDIO_MINUTES = 15
    /** Only groups an activity works hard count (a walk barely loads the legs). */
    private const val MIN_SETS_PER_HOUR = 4.0
    private const val LONG_DAY_MINUTES = 150
    private const val OVERUSE_SETS = 25
    /** Only cardio that started recently is news. */
    private val RECENT = Duration.ofHours(6)

    fun check(
        state: WorkoutPersistedState,
        cardio: List<CardioBout>,
        /** Minutes of all exercise today: app sessions plus every imported workout. */
        exerciseMinutesToday: Int,
        now: Instant,
        zone: ZoneId
    ): List<OverexertionAlert> {
        val today = now.atZone(zone).toLocalDate()
        val alerts = mutableListOf<OverexertionAlert>()

        cardio.filter {
            it.minutes >= MIN_CARDIO_MINUTES && !it.start.isAfter(now) && Duration.between(it.start, now) <= RECENT
        }.forEach { bout ->
            val worked = bout.setsPerHour.filterValues { it >= MIN_SETS_PER_HOUR }.keys
            if (worked.isEmpty()) return@forEach
            // How each worked group stood just before this bout, from everything that came earlier.
            val before = MuscleRecovery.bouts(state, cardio.filter { it.start.isBefore(bout.start) }, bout.start, zone)
            MuscleRecovery.readiness(before, bout.start)
                .filter { it.group in worked && it.percent < TIRED_PERCENT }
                .minByOrNull { it.percent }
                ?.let { alerts += OverexertionAlert.CardioOnTiredMuscles(bout, it.group, it.percent) }
        }

        if (exerciseMinutesToday >= LONG_DAY_MINUTES) alerts += OverexertionAlert.LongDay(exerciseMinutesToday, today.toString())

        val week = "${today.get(IsoFields.WEEK_BASED_YEAR)}-${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        MuscleRecovery.readiness(MuscleRecovery.bouts(state, cardio, now, zone), now)
            .filter { it.weeklySets > OVERUSE_SETS }
            .forEach { alerts += OverexertionAlert.WeeklyOveruse(it.group, it.weeklySets, week) }

        if (MuscleRecovery.activeDays(state, cardio, today, zone) >= 7) alerts += OverexertionAlert.NoRestDay(today.toString())
        return alerts
    }
}
