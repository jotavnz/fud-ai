package com.apoorvdarshan.calorietracker.services.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apoorvdarshan.calorietracker.BuildConfig
import com.apoorvdarshan.calorietracker.services.ai.FoodAnalysisService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The closed beta's opt-in usage and error log, sent to Supabase (supabase/telemetry.sql).
 *
 * It records what people use (screens, features), how AI calls and syncs go, and crashes,
 * with an anonymous install id, the app version and the phone model. It never records what
 * someone ate, weighs, slept or wrote: [TelemetryEvent.sanitized] keeps only short plain
 * values, and callers pass counts and outcomes, never content. Nothing is queued until the
 * person says yes, and saying no deletes what was waiting.
 */
object Telemetry {
    enum class Consent { UNASKED, YES, NO }

    private const val TAG = "FudAITelemetry"
    private const val PREFS = "fudai_telemetry"
    private const val KEY_CONSENT = "consent"
    private const val KEY_INSTALL_ID = "install_id"
    private const val MAX_STACK = 3_000

    private var prefs: SharedPreferences? = null
    private var queue: TelemetryQueue? = null
    private var context: TelemetryContext? = null
    private var appContext: Context? = null

    private val _consent = MutableStateFlow(Consent.UNASKED)
    val consent: StateFlow<Consent> = _consent.asStateFlow()

    /** False when the build has no Supabase project: the prompt and the setting stay hidden. */
    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun init(app: Context) {
        appContext = app.applicationContext
        val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        queue = TelemetryQueue(File(app.filesDir, "telemetry/queue.jsonl"))
        context = TelemetryContext(
            appVersion = BuildConfig.VERSION_NAME,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".take(80),
            android = Build.VERSION.SDK_INT,
            locale = Locale.getDefault().toLanguageTag()
        )
        _consent.value = runCatching { Consent.valueOf(store.getString(KEY_CONSENT, null) ?: "") }
            .getOrDefault(Consent.UNASKED)
        installCrashHandler()
        if (isOn()) TelemetryWorker.schedule(app)
    }

    fun setConsent(yes: Boolean) {
        val app = appContext ?: return
        prefs?.edit()?.putString(KEY_CONSENT, if (yes) Consent.YES.name else Consent.NO.name)?.apply()
        _consent.value = if (yes) Consent.YES else Consent.NO
        if (yes) {
            track("telemetry_on")
            TelemetryWorker.schedule(app)
            TelemetryWorker.sendSoon(app)
        } else {
            queue?.clear()
            TelemetryWorker.cancel(app)
        }
    }

    fun isOn(): Boolean = configured && _consent.value == Consent.YES

    /** Queues one event; a no-op until the person opted in. Never throws. */
    fun track(name: String, props: Map<String, Any?> = emptyMap()) {
        if (!isOn()) return
        runCatching {
            val event = TelemetryEvent.sanitized(name, props, Instant.now())
            queue?.append(event.toJson(installId(), context ?: return))
        }.onFailure { Log.w(TAG, "track failed", it) }
    }

    /** A handled failure: where it happened and its type, with a short message. */
    fun error(where: String, error: Throwable, props: Map<String, Any?> = emptyMap()) {
        track(
            "error",
            props + mapOf(
                "where" to where,
                "type" to error::class.java.simpleName,
                "message" to error.message?.take(200)
            )
        )
    }

    /** Sends now, when online (the app opening, pull to refresh). */
    fun sendSoon() {
        val app = appContext ?: return
        if (isOn()) TelemetryWorker.sendSoon(app)
    }

    internal suspend fun flush(): Boolean {
        if (!isOn()) return true
        val queued = queue ?: return true
        val uploader = TelemetryUploader(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, FoodAnalysisService.defaultClient)
        return uploader.flush(queued)
    }

    private fun installId(): String {
        val store = prefs ?: return "unknown"
        store.getString(KEY_INSTALL_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also { store.edit().putString(KEY_INSTALL_ID, it).apply() }
    }

    /**
     * A crash is written to the queue before the process dies (the file append is synchronous)
     * and goes out on a later launch. The system's handler still runs afterwards.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashRecorder) return
        Thread.setDefaultUncaughtExceptionHandler(CrashRecorder(previous))
    }

    private class CrashRecorder(private val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            runCatching {
                if (isOn()) {
                    val root = generateSequence(error) { it.cause }.last()
                    val event = TelemetryEvent(
                        name = "crash",
                        occurredAt = Instant.now(),
                        props = mapOf(
                            "type" to error::class.java.name,
                            "root" to root::class.java.name,
                            "message" to error.message?.take(200),
                            "thread" to thread.name,
                            "stack" to error.stackTraceToString().take(MAX_STACK)
                        )
                    )
                    queue?.append(event.toJson(installId(), context ?: return@runCatching))
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

/** Sends queued events every few hours and right after the app opens, only with a network. */
class TelemetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (runCatching { Telemetry.flush() }.getOrDefault(false)) Result.success() else Result.retry()

    companion object {
        private const val PERIODIC = "telemetry-upload"
        private const val NOW = "telemetry-upload-now"
        private val ONLINE = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TelemetryWorker>(3, TimeUnit.HOURS).setConstraints(ONLINE).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun sendSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<TelemetryWorker>().setConstraints(ONLINE).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }
}
