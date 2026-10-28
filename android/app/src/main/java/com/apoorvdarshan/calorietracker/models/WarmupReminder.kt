package com.apoorvdarshan.calorietracker.models

import java.time.LocalDate

/**
 * The warm-up nudge sent before training (Settings → Training): on a day with exercises
 * planned in the diary, or on one of the user's usual training days, as long as nothing was
 * trained yet today. Lets the user warm up on the way to the gym.
 */
object WarmupReminder {
    /** Minutes before the usual training time that the reminder fires. */
    const val LEAD_MINUTES = 30

    /** Hours offered in Settings for "I usually train at". */
    val trainingHours = listOf(7, 9, 12, 17, 18, 19, 20)

    data class Plan(
        /** Muscles of today's planned lifts; empty on a usual training day without a plan. */
        val muscles: List<String>,
        val picks: List<ExerciseRoles.Pick>
    )

    fun plan(state: WorkoutPersistedState, today: LocalDate): Plan? {
        val preferences = state.preferences
        if (!preferences.warmupReminder) return null
        val key = WorkoutDate.key(today)
        if (state.completedSessions.any { it.diaryDateKey == key }) return null
        val planned = state.dayPlans[key]?.exercises.orEmpty()
        val lifts = planned.filter { !it.isCardio }
        if (lifts.isEmpty() && today.dayOfWeek.value !in preferences.trainingDays) return null
        val muscles = lifts.flatMap { it.primaryMuscles }.map { it.lowercase() }.distinct()
        return Plan(muscles, ExerciseRoles.warmup(muscles, gentle = preferences.simpleLogging))
    }

    /** Hour and minute the alarm fires for [trainingHour]. */
    fun alarmTime(trainingHour: Int): Pair<Int, Int> {
        val minutes = Math.floorMod(trainingHour * 60 - LEAD_MINUTES, 24 * 60)
        return minutes / 60 to minutes % 60
    }
}
