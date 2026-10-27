package com.apoorvdarshan.calorietracker.services.training

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.FudAIApp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.CardioBout
import com.apoorvdarshan.calorietracker.models.MuscleReadiness
import com.apoorvdarshan.calorietracker.models.MuscleRecovery
import com.apoorvdarshan.calorietracker.models.OverexertionAlert
import com.apoorvdarshan.calorietracker.models.OverexertionMonitor
import com.apoorvdarshan.calorietracker.models.asCardioBout
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Muscle recovery and overreaching alerts from the workout diary plus the workouts other apps
 * (the watch) shared with Health Connect.
 */
class TrainingLoadService(private val container: AppContainer) {

    private suspend fun cardio(): List<CardioBout> =
        container.importedHealthWorkoutRepository.workouts.first().map { it.asCardioBout() }

    suspend fun readiness(now: Instant = Instant.now()): List<MuscleReadiness> {
        val zone = ZoneId.systemDefault()
        val bouts = MuscleRecovery.bouts(container.workoutRepository.snapshot(), cardio(), now, zone)
        return MuscleRecovery.readiness(bouts, now)
    }

    /** Sends each new overreaching alert once; returns how many were sent. */
    suspend fun checkAndNotify(): Int {
        val prefs = container.prefs
        if (!prefs.overexertionAlertsEnabled.first()) return 0
        if (!container.notifications.canPostNotifications()) return 0
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val state = container.workoutRepository.snapshot()
        val imported = container.importedHealthWorkoutRepository.workouts.first()
        val minutesToday = state.completedSessions.filter { it.diaryDateKey == today.toString() }.sumOf { it.durationMinutes } +
            imported.filter { it.diaryDateKey == today.toString() }.sumOf { it.durationMinutes }
        val sent = prefs.overexertionAlertedKeys.first()
        val fresh = OverexertionMonitor.check(state, imported.map { it.asCardioBout() }, minutesToday, now, zone)
            .filter { it.key !in sent }
            .take(MAX_PER_CHECK)
        fresh.forEachIndexed { index, alert ->
            val (title, text) = message(alert)
            container.notifications.showTrainingAlert(index, title, text)
        }
        // Remember even what was not shown this time, so the same alert never piles up later.
        if (fresh.isNotEmpty()) prefs.addOverexertionAlertedKeys(fresh.map { it.key })
        return fresh.size
    }

    private fun message(alert: OverexertionAlert): Pair<String, String> {
        val ctx = container.appContext
        return when (alert) {
            is OverexertionAlert.CardioOnTiredLegs -> {
                val activity = alert.bout.labelRes?.let(ctx::getString) ?: ctx.getString(R.string.alert_activity_generic)
                ctx.getString(R.string.alert_tired_legs_title) to
                    ctx.getString(R.string.alert_tired_legs_text, activity, alert.bout.minutes, alert.legPercentBefore)
            }
            is OverexertionAlert.LongDay ->
                ctx.getString(R.string.alert_long_day_title) to ctx.getString(R.string.alert_long_day_text, alert.minutes)
            is OverexertionAlert.WeeklyOveruse -> {
                val group = ctx.getString(alert.group.titleRes)
                ctx.getString(R.string.alert_overuse_title, group) to ctx.getString(R.string.alert_overuse_text, alert.sets)
            }
            is OverexertionAlert.NoRestDay ->
                ctx.getString(R.string.alert_no_rest_title) to ctx.getString(R.string.alert_no_rest_text)
        }
    }

    private companion object {
        const val MAX_PER_CHECK = 2
    }
}

/** Every two hours: refresh the watch's workouts when allowed, then check for overreaching. */
class TrainingAlertsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? FudAIApp)?.container ?: return Result.success()
        // Background reads need Health Connect's background permission; without it the import
        // simply finds nothing new and the check uses what the last foreground sync brought.
        runCatching { container.importedHealthWorkoutRepository.synchronize() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        runCatching { container.trainingLoad.checkAndNotify() }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Log.w("FudAITrainingAlerts", "Overreaching check failed: ${it.javaClass.simpleName}")
            }
        return Result.success()
    }

    companion object {
        private const val WORK = "fud_ai_training_alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrainingAlertsWorker>(2, TimeUnit.HOURS).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
