package com.apoorvdarshan.calorietracker.services.training

import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.PantryItem
import com.apoorvdarshan.calorietracker.models.WorkoutPersistedState
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Gentle reminders the way an attentive coach would give them: food about to expire (at most
 * once a day) and a no-guilt "come back" after a week without training (at most once a week).
 * Runs with the two-hourly training check; quiet at night.
 */
object CoachNudges {
    private const val PANTRY = "pantry"
    private const val COMEBACK = "comeback"
    const val COMEBACK_AFTER_DAYS = 7L

    suspend fun checkAndNotify(container: AppContainer, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()) {
        if (now.hour !in 9..20) return
        val shown = container.prefs.coachNudgeDates.first()
        val context = container.appContext

        val expiring = expiringSoon(container.pantryRepository.snapshot(), today)
        if (expiring.isNotEmpty() && shown[PANTRY] != today.toString()) {
            container.notifications.showTrainingAlert(
                index = 10,
                title = context.getString(R.string.nudge_pantry_title),
                text = context.getString(R.string.nudge_pantry_text, expiring.take(3).joinToString(", ") { it.name })
            )
            container.prefs.setCoachNudgeDate(PANTRY, today.toString())
        }

        val daysAway = daysSinceLastWorkout(container.workoutRepository.snapshot(), today)
        val lastComeback = shown[COMEBACK]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (daysAway != null && daysAway >= COMEBACK_AFTER_DAYS &&
            (lastComeback == null || ChronoUnit.DAYS.between(lastComeback, today) >= COMEBACK_AFTER_DAYS)
        ) {
            container.notifications.showTrainingAlert(
                index = 11,
                title = context.getString(R.string.nudge_comeback_title),
                text = context.getString(R.string.nudge_comeback_text, daysAway.toInt())
            )
            container.prefs.setCoachNudgeDate(COMEBACK, today.toString())
        }
    }

    /** Items with a best-before of today or tomorrow (not already expired). */
    fun expiringSoon(items: List<PantryItem>, today: LocalDate): List<PantryItem> =
        items.filter { item -> item.daysLeft(today)?.let { it in 0..1 } == true }

    /** Days since the last day with logged work, or null for someone who never trained here. */
    fun daysSinceLastWorkout(state: WorkoutPersistedState, today: LocalDate): Long? {
        val planned = state.dayPlans.filterValues { plan ->
            plan.exercises.any { exercise ->
                exercise.sets.any { it.reps.isNotBlank() } || exercise.timer?.isSaved == true
            }
        }.keys
        val last = (state.completedSessions.map { it.diaryDateKey } + planned)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(today) }
            .maxOrNull() ?: return null
        return ChronoUnit.DAYS.between(last, today)
    }
}
