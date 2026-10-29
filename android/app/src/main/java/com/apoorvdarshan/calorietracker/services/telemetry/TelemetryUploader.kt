package com.apoorvdarshan.calorietracker.services.telemetry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Sends queued events to Supabase's REST endpoint in batches. The anon key can only insert
 * into `events` (supabase/telemetry.sql), so the key inside the app reads nothing back.
 */
class TelemetryUploader(
    private val baseUrl: String,
    private val anonKey: String,
    private val client: OkHttpClient
) {
    enum class Outcome { SENT, REJECTED, RETRY }

    /** Sends everything queued, [batchSize] at a time; false when something is left to retry. */
    suspend fun flush(queue: TelemetryQueue, batchSize: Int = 200): Boolean {
        while (true) {
            val batch = queue.peek(batchSize)
            if (batch.isEmpty()) return true
            when (send(batch)) {
                // A batch Supabase refuses as malformed would block the queue forever; drop it.
                Outcome.SENT, Outcome.REJECTED -> queue.drop(batch.size)
                Outcome.RETRY -> return false
            }
        }
    }

    suspend fun send(lines: List<String>): Outcome = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/events")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Prefer", "return=minimal")
            .post(lines.joinToString(",", "[", "]").toRequestBody(JSON))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    in 200..299 -> Outcome.SENT
                    400, 409, 413, 422 -> Outcome.REJECTED
                    // 401/403/404 (wrong key, table missing), 429 and 5xx: keep and try later.
                    else -> Outcome.RETRY
                }
            }
        }.getOrDefault(Outcome.RETRY)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
