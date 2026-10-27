package com.apoorvdarshan.calorietracker.models

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/** A reason to tell the user they may be pushing too hard. [key] makes each alert fire once. */
sealed interface OverexertionAlert {
    val key: String

    /** Leg cardio (often from the watch) while the legs were still recovering from training. */
    data class CardioOnTiredLegs(val bout: CardioBout, val legPercentBefore: Int) : OverexertionAlert {
        override val key: String get() = "cardio_legs_${bout.start.epochSecond}"
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
 * matters: legs under half recovered when a 15+ minute run starts, 150+ minutes of exercise
 * in a day, more than 25 weekly sets on a group, and seven active days in a row.
 */
object OverexertionMonitor {
    private const val TIRED_LEGS_PERCENT = 50
    private const val MIN_CARDIO_MINUTES = 15
    private const val MIN_LEG_SETS_PER_HOUR = 4.0
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
            it.minutes >= MIN_CARDIO_MINUTES && it.legSetsPerHour >= MIN_LEG_SETS_PER_HOUR &&
                !it.start.isAfter(now) && Duration.between(it.start, now) <= RECENT
        }.forEach { bout ->
            // How the legs stood just before this bout, from everything that came earlier.
            val before = MuscleRecovery.bouts(state, cardio.filter { it.start.isBefore(bout.start) }, bout.start, zone)
            val legs = MuscleRecovery.readiness(before, bout.start).first { it.group == MachineMuscleGroup.LEGS }
            if (legs.percent < TIRED_LEGS_PERCENT) alerts += OverexertionAlert.CardioOnTiredLegs(bout, legs.percent)
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
