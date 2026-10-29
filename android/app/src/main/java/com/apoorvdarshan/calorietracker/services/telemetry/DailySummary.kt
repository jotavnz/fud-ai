package com.apoorvdarshan.calorietracker.services.telemetry

import android.content.Context
import com.apoorvdarshan.calorietracker.AppContainer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * One "daily_summary" record per finished day (records sharing only): what was eaten in
 * total, water, weight, sleep, steps and training, so a day can be read at a glance in
 * Supabase without replaying every event. Sent for yesterday on the first open of a day.
 */
object DailySummary {
    private const val PREFS = "fudai_telemetry"
    private const val KEY_LAST_DAY = "summary_last_day"

    suspend fun sendForYesterdayIfDue(app: Context, container: AppContainer, today: LocalDate = LocalDate.now()) {
        if (!Telemetry.isContentOn()) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = today.minusDays(1)
        if (prefs.getString(KEY_LAST_DAY, null) == day.toString()) return
        val zone = ZoneId.systemDefault()
        fun onDay(instant: java.time.Instant) = instant.atZone(zone).toLocalDate() == day

        val foods = container.foodRepository.entries.first().filter { onDay(it.timestamp) }
        val water = container.waterRepository.entries.first().filter { onDay(it.date) }.sumOf { it.milliliters }
        val weight = container.weightRepository.entries.first().filter { onDay(it.date) }.maxByOrNull { it.date }?.weightKg
        val workouts = container.workoutRepository.completedSessions.first().filter { it.diaryDateKey == day.toString() }
        val sleep = runCatching { container.sleepService.history(days = 3) }.getOrDefault(emptyList())
            .filter { it.wakeDate == day }
        val steps = runCatching { container.health.readStepsForDay(day) }.getOrNull()

        Telemetry.trackContent(
            "daily_summary",
            mapOf(
                "day" to day.toString(),
                "foods" to foods.size,
                "kcal" to foods.sumOf { it.calories },
                "protein_g" to foods.sumOf { it.protein }.roundToInt(),
                "carbs_g" to foods.sumOf { it.carbs }.roundToInt(),
                "fat_g" to foods.sumOf { it.fat }.roundToInt(),
                "water_ml" to water,
                "weight_kg" to weight,
                "workouts" to workouts.size,
                "workout_min" to workouts.sumOf { it.durationSeconds } / 60,
                "burn_kcal" to workouts.sumOf { it.caloriesBurned ?: 0 },
                "sleep_min" to sleep.sumOf { it.minutes },
                "sleep_source" to sleep.firstOrNull()?.source,
                "steps" to steps
            )
        )
        prefs.edit().putString(KEY_LAST_DAY, day.toString()).apply()
    }
}
