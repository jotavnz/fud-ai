package com.apoorvdarshan.calorietracker.services.health

import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.HEALTH_BODY_BACKFILL_DAYS
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/** Progress steps the status screen shows while a manual run is going. */
enum class HealthImportStep { SYNCING, WEIGHTS, BODY_FAT, WORKOUTS }

/**
 * "Sync now" and "Import full history" for the Health Connect status screen. Both reuse the
 * regular read sync ([AppContainer.syncHealthConnectReadsAndWait]); the full-history run then
 * re-reads the whole weight/body-fat backfill window (adding only readings the app lacks) and
 * imports other apps' workouts over the same window instead of the rolling 90 days. Read-only
 * towards Health Connect: nothing is written there.
 */
object HealthImportRunner {

    /** Workout sessions looked up for calories in one full-history run (aggregate reads). */
    private const val HISTORY_CALORIE_LOOKUPS = 300

    /** Label FudAIApp stores with the changes-token types once deleted ids are remembered. */
    private const val DELETIONS_TRACKED = "deletionsTracked"

    suspend fun run(
        container: AppContainer,
        fullHistory: Boolean,
        onStep: (HealthImportStep) -> Unit = {}
    ): HealthImportRunResult {
        val prefs = container.prefs
        val weightsBefore = container.weightRepository.entries.first().mapTo(HashSet()) { it.id }
        val bodyFatBefore = container.bodyFatRepository.entries.first().mapTo(HashSet()) { it.id }
        val workoutsBefore = container.importedHealthWorkoutRepository.workouts.first().mapTo(HashSet()) { it.id }

        onStep(HealthImportStep.SYNCING)
        var complete = container.syncHealthConnectReadsAndWait()

        if (fullHistory && prefs.healthConnectEnabled.first() && container.health.isAvailable()) {
            val caps = container.health.capabilities()
            val now = Instant.now()
            val from = now.minus(Duration.ofDays(HEALTH_BODY_BACKFILL_DAYS))
            // Re-reading the whole window is only safe once deletions are remembered; tokens
            // from older builds would bring back readings the user deleted back then. The
            // regular sync above already imports history safely in that case.
            val deletionsTracked = DELETIONS_TRACKED in prefs.healthChangesTokenTypes.first()
            if (deletionsTracked && caps.weightRead) {
                onStep(HealthImportStep.WEIGHTS)
                val weights = container.health.readWeights(from, now)
                if (weights != null) container.weightRepository.addMissingExternalWeights(weights) else complete = false
            }
            if (deletionsTracked && caps.bodyFatRead) {
                onStep(HealthImportStep.BODY_FAT)
                val bodyFats = container.health.readBodyFats(from, now)
                if (bodyFats != null) container.bodyFatRepository.addMissingExternalBodyFats(bodyFats) else complete = false
            }
            if (caps.exerciseRead) {
                onStep(HealthImportStep.WORKOUTS)
                val result = container.importedHealthWorkoutRepository.synchronize(
                    lookbackDays = HEALTH_BODY_BACKFILL_DAYS,
                    maxCalorieLookups = HISTORY_CALORIE_LOOKUPS
                )
                if (result == com.apoorvdarshan.calorietracker.data.HealthWorkoutImportResult.SYNCED) {
                    HealthSyncStatusStore(container.appContext).markSynced(listOf(HealthSyncType.WORKOUTS))
                } else {
                    complete = false
                }
            }
        }

        val weightsAfter = container.weightRepository.entries.first().count { it.id !in weightsBefore }
        val bodyFatAfter = container.bodyFatRepository.entries.first().count { it.id !in bodyFatBefore }
        val workoutsAfter = container.importedHealthWorkoutRepository.workouts.first().count { it.id !in workoutsBefore }
        val result = HealthImportRunResult(
            finishedAt = Instant.now(),
            fullHistory = fullHistory,
            weightsAdded = weightsAfter,
            bodyFatAdded = bodyFatAfter,
            workoutsAdded = workoutsAfter,
            complete = complete
        )
        HealthSyncStatusStore(container.appContext).saveLastRun(result)
        return result
    }
}
