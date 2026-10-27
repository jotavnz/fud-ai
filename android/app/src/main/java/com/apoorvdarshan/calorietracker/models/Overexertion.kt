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

    /** Today's plan has exercises for a group that is still recovering: remember to rest it. */
    data class PlannedOnTired(val group: MachineMuscleGroup, val percent: Int, val day: String) : OverexertionAlert {
        override val key: String get() = "planned_${group.name}_$day"
    }

    /** A group worked hard (or reported sore) has recovered: ready to train again. */
    data class ReadyAgain(val group: MachineMuscleGroup, val lastWorked: Instant) : OverexertionAlert {
        override val key: String get() = "ready_${group.name}_${lastWorked.epochSecond}"
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
    /** Only groups trained this much this week get a "ready again" notice. */
    private const val READY_NOTICE_SETS = 6
    /** Only cardio that started recently is news. */
    private val RECENT = Duration.ofHours(6)

    fun check(
        state: WorkoutPersistedState,
        cardio: List<CardioBout>,
        /** Minutes of all exercise today: app sessions plus every imported workout. */
        exerciseMinutesToday: Int,
        now: Instant,
        zone: ZoneId,
        soreness: List<SorenessReport> = emptyList()
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
        val current = Soreness.apply(MuscleRecovery.readiness(MuscleRecovery.bouts(state, cardio, now, zone), now), soreness, now)
        current.filter { it.weeklySets > OVERUSE_SETS }
            .forEach { alerts += OverexertionAlert.WeeklyOveruse(it.group, it.weeklySets, week) }

        // Planned for today but not done yet, on a group under half recovered.
        val planned = state.dayPlans[WorkoutDate.key(today)]?.exercises.orEmpty()
            .filter { !it.isCardio && WorkoutVolume.performedSets(it).isEmpty() }
            .flatMap { it.primaryMuscles.mapNotNull(TrainingInsights::groupOf) }
            .toSet()
        current.filter { it.group in planned && it.percent < TIRED_PERCENT }
            .forEach { alerts += OverexertionAlert.PlannedOnTired(it.group, it.percent, today.toString()) }

        // Recovered again after real work in the last four days (or after a soreness report).
        val recentlySore = soreness.filter { Duration.between(it.reportedAt, now).toDays() < 5 }.mapNotNull { it.muscleGroup }.toSet()
        current.filter { r ->
            val last = r.lastWorked ?: return@filter false
            r.ready && Duration.between(last, now).toDays() < 4 && (r.weeklySets >= READY_NOTICE_SETS || r.group in recentlySore)
        }.forEach { alerts += OverexertionAlert.ReadyAgain(it.group, it.lastWorked!!) }

        if (MuscleRecovery.activeDays(state, cardio, today, zone) >= 7) alerts += OverexertionAlert.NoRestDay(today.toString())
        return alerts
    }
}
