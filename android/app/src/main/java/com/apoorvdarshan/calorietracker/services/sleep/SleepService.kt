package com.apoorvdarshan.calorietracker.services.sleep

import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.models.stepsByDay
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Gathers what the sleep advice needs: two weeks of sleep from every Health Connect source
 * plus nights logged through Coach, and today's training, steps and meals.
 */
class SleepService(private val container: AppContainer) {

    /** Tonight's advice, or null when the user turned it off. */
    suspend fun adviceIfEnabled(): SleepAdvice? =
        if (container.prefs.sleepAdviceEnabled.first()) advice() else null

    /** Nights of the last [days] days, newest first, from every source (one per morning). */
    suspend fun history(days: Long = 30): List<SleepNight> {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().minusDays(days).atStartOfDay(zone).toInstant()
        val measured = if (container.prefs.healthConnectEnabled.first()) {
            container.health.readSleepSessions(from, Instant.now()).orEmpty()
        } else emptyList()
        val manual = container.prefs.sleepEntries.first().filter { it.end.isAfter(from) }
        return SleepAdvisor.nights(measured + manual, zone).sortedByDescending { it.wakeDate }
    }

    /** The user's age-based nightly need, for the history bars. */
    suspend fun needMinutes(): Long = SleepAdvisor.baseNeedMinutes(container.profileRepository.current()?.age ?: 30)

    suspend fun advice(today: LocalDate = LocalDate.now()): SleepAdvice {
        val zone = ZoneId.systemDefault()
        val from = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val measured = if (container.prefs.healthConnectEnabled.first()) {
            container.health.readSleepSessions(from, now).orEmpty()
        } else emptyList()
        val manual = container.prefs.sleepEntries.first().filter { it.end.isAfter(from) }
        val nights = SleepAdvisor.nights(measured + manual, zone)

        val todayKey = today.toString()
        val sets = container.workoutRepository.completedSessions.first()
            .filter { it.diaryDateKey == todayKey }
            .sumOf { it.performedSetCount }
        val dayStart = today.atStartOfDay(zone).toInstant()
        val importedMinutes = container.importedHealthWorkoutRepository.workouts.first()
            .filter { !it.startedAt.isBefore(dayStart) }
            .sumOf { it.durationSeconds / 60 }
        val measuredSteps = if (container.prefs.healthConnectEnabled.first()) container.health.readStepsForDay(today) else null
        val manualSteps = container.prefs.manualSteps.first().stepsByDay()[today] ?: 0
        val meals = container.foodRepository.entries.first()
            .filter { Duration.between(it.timestamp, now).toHours() < 24 }
            .map { it.timestamp to it.calories }
        val age = container.profileRepository.current()?.age ?: 30

        return SleepAdvisor.advise(
            nights = nights,
            load = DayLoad(
                sets = sets,
                exerciseMinutes = importedMinutes,
                steps = (measuredSteps ?: 0) + manualSteps,
                meals = meals
            ),
            age = age,
            today = today,
            zone = zone
        )
    }

    private companion object {
        const val HISTORY_DAYS = 14L
    }
}
