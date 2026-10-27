package com.apoorvdarshan.calorietracker.services.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/** Data types the Health Connect status screen reports on. */
enum class HealthSyncType(val key: String) {
    WEIGHT("weight"),
    BODY_FAT("bodyfat"),
    WORKOUTS("workouts"),
    NUTRITION("nutrition")
}

/**
 * Small, separate SharedPreferences file with sync bookkeeping for the status screen only:
 * when each data type last synced successfully and what the last manual run imported.
 * Nothing here drives sync behavior; deleting it only blanks the status screen.
 */
class HealthSyncStatusStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastSync(type: HealthSyncType): Instant? =
        sp.getLong("last_sync_${type.key}", 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    fun markSynced(types: Collection<HealthSyncType>, at: Instant = Instant.now()) {
        if (types.isEmpty()) return
        sp.edit().apply {
            types.forEach { putLong("last_sync_${it.key}", at.toEpochMilli()) }
        }.apply()
    }

    val lastAutoSync: Instant?
        get() = sp.getLong(KEY_LAST_AUTO, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    fun markAutoSync(at: Instant = Instant.now()) {
        sp.edit().putLong(KEY_LAST_AUTO, at.toEpochMilli()).apply()
    }

    val lastHistoryImport: Instant?
        get() = sp.getLong(KEY_LAST_HISTORY, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)

    fun saveLastRun(result: HealthImportRunResult) {
        sp.edit()
            .putLong(KEY_RUN_AT, result.finishedAt.toEpochMilli())
            .putBoolean(KEY_RUN_HISTORY, result.fullHistory)
            .putInt(KEY_RUN_WEIGHTS, result.weightsAdded)
            .putInt(KEY_RUN_BODY_FAT, result.bodyFatAdded)
            .putInt(KEY_RUN_WORKOUTS, result.workoutsAdded)
            .putBoolean(KEY_RUN_COMPLETE, result.complete)
            .apply {
                if (result.fullHistory) putLong(KEY_LAST_HISTORY, result.finishedAt.toEpochMilli())
            }
            .apply()
    }

    fun lastRun(): HealthImportRunResult? {
        val at = sp.getLong(KEY_RUN_AT, 0L).takeIf { it > 0L } ?: return null
        return HealthImportRunResult(
            finishedAt = Instant.ofEpochMilli(at),
            fullHistory = sp.getBoolean(KEY_RUN_HISTORY, false),
            weightsAdded = sp.getInt(KEY_RUN_WEIGHTS, 0),
            bodyFatAdded = sp.getInt(KEY_RUN_BODY_FAT, 0),
            workoutsAdded = sp.getInt(KEY_RUN_WORKOUTS, 0),
            complete = sp.getBoolean(KEY_RUN_COMPLETE, true)
        )
    }

    private companion object {
        const val FILE = "health_sync_status"
        const val KEY_LAST_AUTO = "last_auto_sync"
        const val KEY_LAST_HISTORY = "last_history_import"
        const val KEY_RUN_AT = "run_at"
        const val KEY_RUN_HISTORY = "run_history"
        const val KEY_RUN_WEIGHTS = "run_weights"
        const val KEY_RUN_BODY_FAT = "run_bodyfat"
        const val KEY_RUN_WORKOUTS = "run_workouts"
        const val KEY_RUN_COMPLETE = "run_complete"
    }
}

/** What one "Sync now" / "Import full history" run added to the app. */
data class HealthImportRunResult(
    val finishedAt: Instant,
    val fullHistory: Boolean,
    val weightsAdded: Int,
    val bodyFatAdded: Int,
    val workoutsAdded: Int,
    /** False when a Health Connect read failed part-way; the next sync retries. */
    val complete: Boolean
)

/** Friendly names for the apps that usually write watch/band data into Health Connect. */
internal val KNOWN_HEALTH_SOURCES: Map<String, String> = mapOf(
    "com.sec.android.app.shealth" to "Samsung Health",
    "com.samsung.android.wear.shealth" to "Samsung Health (Galaxy Watch)",
    "com.xiaomi.wearable" to "Mi Fitness",
    "com.mi.health" to "Mi Fitness",
    "com.xiaomi.hm.health" to "Zepp Life",
    "com.huami.watch.hmwatchmanager" to "Zepp",
    "com.Cubitt.wear" to "Cubitt",
    "com.google.android.apps.fitness" to "Google Fit / Fitbit",
    "com.fitbit.FitbitMobile" to "Fitbit",
    "com.huawei.health" to "Huawei Health",
    "com.garmin.android.apps.connectmobile" to "Garmin Connect",
    "com.strava" to "Strava",
    HealthConnectManager.HEALTH_CONNECT_PROVIDER_PACKAGE to "Health Connect"
)

/** Friendly name for a Health Connect data origin: known map, then the installed label, then the package. */
internal fun healthSourceName(packageName: String, installedLabel: (String) -> String?): String =
    KNOWN_HEALTH_SOURCES[packageName]
        ?: KNOWN_HEALTH_SOURCES.entries.firstOrNull { it.key.equals(packageName, ignoreCase = true) }?.value
        ?: installedLabel(packageName)
        ?: packageName

/** One record seen in Health Connect, reduced to what the status screen needs. */
data class HealthSeenRecord(
    val time: Instant,
    val originPackage: String,
    val clientRecordId: String?,
    val recordId: String,
    /** kg for weight, 0–1 fraction for body fat, kcal for nutrition; null for workouts. */
    val value: Double? = null,
    val exerciseType: Int? = null,
    val endTime: Instant? = null
)

/** Counts / range / sources of one record type as Health Connect holds it (other apps only). */
data class HealthTypeSnapshot(
    val count: Int,
    val oldest: Instant?,
    val newest: Instant?,
    /** Origin package to record count, largest first. */
    val sources: List<Pair<String, Int>>
) {
    companion object {
        fun of(records: List<HealthSeenRecord>): HealthTypeSnapshot = HealthTypeSnapshot(
            count = records.size,
            oldest = records.minOfOrNull { it.time },
            newest = records.maxOfOrNull { it.time },
            sources = records.groupingBy { it.originPackage }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key to it.value }
        )
    }
}

/** Display-only activity summary (steps / active calories) with the apps that supplied it. */
data class HealthActivitySnapshot(
    val stepsToday: Long?,
    val steps7Days: Long?,
    val activeKcalToday: Double?,
    val activeKcal7Days: Double?,
    /** Newest step or active-calorie record another app wrote in the last 3 days. */
    val latestRecordAt: Instant?,
    val sources: List<String>,
    /** Today's steps per app, so the user can see which source counts more and pick it. */
    val stepSourcesToday: StepSourceTotals? = null
)

/**
 * Read-only Health Connect inspection for the status screen. Never writes, never changes what
 * the regular sync imports; uses only permissions the app already declares.
 */
class HealthSyncInspector(private val context: Context) {
    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    val weightRead = HealthPermission.getReadPermission(WeightRecord::class)
    val weightWrite = HealthPermission.getWritePermission(WeightRecord::class)
    val bodyFatRead = HealthPermission.getReadPermission(BodyFatRecord::class)
    val bodyFatWrite = HealthPermission.getWritePermission(BodyFatRecord::class)
    val exerciseRead = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    val nutritionRead = HealthPermission.getReadPermission(NutritionRecord::class)
    val nutritionWrite = HealthPermission.getWritePermission(NutritionRecord::class)
    val activeRead = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    val activeWrite = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    val totalRead = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    val stepsRead = HealthPermission.getReadPermission(StepsRecord::class)
    val sleepRead = HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class)
    val historyRead = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
    val backgroundRead = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Granted permissions, or null when Health Connect could not be asked. */
    suspend fun grantedPermissions(): Set<String>? = try {
        client?.permissionController?.getGrantedPermissions()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "permission probe failed: ${e.javaClass.simpleName}")
        null
    }

    fun historyFeatureAvailable(): Boolean = runCatching {
        client?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    suspend fun readWeights(from: Instant, to: Instant): List<HealthSeenRecord>? =
        read(WeightRecord::class, from, to) {
            HealthSeenRecord(it.time, it.metadata.dataOrigin.packageName, it.metadata.clientRecordId, it.metadata.id, it.weight.inKilograms)
        }

    suspend fun readBodyFats(from: Instant, to: Instant): List<HealthSeenRecord>? =
        read(BodyFatRecord::class, from, to) {
            HealthSeenRecord(it.time, it.metadata.dataOrigin.packageName, it.metadata.clientRecordId, it.metadata.id, it.percentage.value / 100.0)
        }

    suspend fun readExercise(from: Instant, to: Instant): List<HealthSeenRecord>? =
        read(ExerciseSessionRecord::class, from, to) {
            HealthSeenRecord(
                it.startTime, it.metadata.dataOrigin.packageName, it.metadata.clientRecordId, it.metadata.id,
                exerciseType = it.exerciseType, endTime = it.endTime
            )
        }

    suspend fun readNutrition(from: Instant, to: Instant): List<HealthSeenRecord>? =
        read(NutritionRecord::class, from, to) {
            HealthSeenRecord(it.startTime, it.metadata.dataOrigin.packageName, it.metadata.clientRecordId, it.metadata.id, it.energy?.inKilocalories)
        }

    /** Steps and active calories for display only. Null fields are types not granted or not readable. */
    suspend fun readActivity(stepsGranted: Boolean, activeGranted: Boolean): HealthActivitySnapshot? {
        val c = client ?: return null
        if (!stepsGranted && !activeGranted) return null
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val weekStart = LocalDate.now(zone).minusDays(6).atStartOfDay(zone).toInstant()
        val sources = linkedSetOf<String>()
        suspend fun aggregate(metric: androidx.health.connect.client.aggregate.AggregateMetric<*>, from: Instant) = try {
            c.aggregate(AggregateRequest(metrics = setOf(metric), timeRangeFilter = TimeRangeFilter.between(from, now)))
                .also { r -> r.dataOrigins.forEach { o -> if (!isFudAiHealthOrigin(o.packageName, context.packageName)) sources += o.packageName } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val stepsToday = if (stepsGranted) aggregate(StepsRecord.COUNT_TOTAL, todayStart)?.get(StepsRecord.COUNT_TOTAL) else null
        val stepSources = if (stepsGranted) {
            try {
                c.stepSourceTotals(todayStart, now, context.packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } else null
        val steps7 = if (stepsGranted) aggregate(StepsRecord.COUNT_TOTAL, weekStart)?.get(StepsRecord.COUNT_TOTAL) else null
        val activeToday = if (activeGranted) aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, todayStart)
            ?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories else null
        val active7 = if (activeGranted) aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, weekStart)
            ?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories else null
        // Newest raw record from another app in the last 3 days: "is my watch still syncing?".
        val recentFrom = now.minus(Duration.ofDays(3))
        val latestSteps = if (stepsGranted) read(StepsRecord::class, recentFrom, now) { it.endTime to it.metadata.dataOrigin.packageName } else null
        val latestActive = if (activeGranted) read(ActiveCaloriesBurnedRecord::class, recentFrom, now) { it.endTime to it.metadata.dataOrigin.packageName } else null
        val latest = (latestSteps.orEmpty() + latestActive.orEmpty())
            .filter { !isFudAiHealthOrigin(it.second, context.packageName) }
            .maxByOrNull { it.first }?.first
        return HealthActivitySnapshot(
            stepsToday = stepsToday,
            steps7Days = steps7,
            activeKcalToday = activeToday,
            activeKcal7Days = active7,
            latestRecordAt = latest,
            sources = sources.toList(),
            stepSourcesToday = stepSources
        )
    }

    /**
     * Apps that wrote steps, sleep, workouts, weight or active calories in the last [days] days,
     * among the types the app may read. Shows whether a watch's app is really sharing.
     */
    suspend fun recentOrigins(granted: Set<String>, days: Long = 14): Set<String> {
        val c = client ?: return emptySet()
        val now = Instant.now()
        val from = now.minus(Duration.ofDays(days))
        val origins = mutableSetOf<String>()
        suspend fun aggregateOrigins(metric: androidx.health.connect.client.aggregate.AggregateMetric<*>) {
            try {
                c.aggregate(AggregateRequest(metrics = setOf(metric), timeRangeFilter = TimeRangeFilter.between(from, now)))
                    .dataOrigins.forEach { origins += it.packageName }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        if (stepsRead in granted) aggregateOrigins(StepsRecord.COUNT_TOTAL)
        if (activeRead in granted) aggregateOrigins(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        if (sleepRead in granted) {
            read(androidx.health.connect.client.records.SleepSessionRecord::class, from, now) { it.metadata.dataOrigin.packageName }
                ?.let { origins += it }
        }
        if (exerciseRead in granted) readExercise(from, now)?.let { list -> origins += list.map { it.originPackage } }
        if (weightRead in granted) readWeights(from, now)?.let { list -> origins += list.map { it.originPackage } }
        return origins
    }

    private suspend fun <T : Record, R> read(type: KClass<T>, from: Instant, to: Instant, map: (T) -> R): List<R>? {
        val c = client ?: return null
        if (!from.isBefore(to)) return emptyList()
        return readAllPages { pageToken ->
            val response = c.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(from, to), pageToken = pageToken)
            )
            response.records.map(map) to response.pageToken
        }
    }

    private companion object {
        const val TAG = "FudAIHealthStatus"
    }
}

/** True for records written by any Fud AI build (own data, not something a watch imported). */
internal fun HealthSeenRecord.isOwn(ownPackage: String): Boolean =
    isFudAiHealthOrigin(originPackage, ownPackage) || clientRecordId?.startsWith("fudai_") == true

/**
 * The id WeightRepository / BodyFatRepository give an external reading when importing it
 * (kept in step with their private externalId), so the status screen can tell which Health
 * Connect records are already in the app's history.
 */
internal fun importedEntryId(record: HealthSeenRecord, fallbackPrefix: String): UUID {
    val seed = record.clientRecordId?.takeIf { it.isNotBlank() }
        ?: record.recordId.takeIf { it.isNotBlank() }
        ?: "$fallbackPrefix:${record.time.toEpochMilli()}"
    return UUID.nameUUIDFromBytes(seed.toByteArray())
}
