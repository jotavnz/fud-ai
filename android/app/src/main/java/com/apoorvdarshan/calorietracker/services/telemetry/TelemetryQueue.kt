package com.apoorvdarshan.calorietracker.services.telemetry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant

/** One usage or error event, as the Supabase `events` table stores it. */
data class TelemetryEvent(
    val name: String,
    val occurredAt: Instant,
    val props: Map<String, Any?> = emptyMap()
) {
    fun toJson(installId: String, context: TelemetryContext): JsonObject = buildJsonObject {
        put("install_id", installId)
        put("occurred_at", occurredAt.toString())
        put("name", name)
        put("app_version", context.appVersion)
        put("device", context.device)
        put("android", context.android)
        put("locale", context.locale)
        put("props", JsonObject(props.mapValues { (_, value) -> jsonOf(value) }))
    }

    companion object {
        const val MAX_NAME = 64
        const val MAX_TEXT = 300
        const val MAX_PROPS = 20

        /**
         * Only short names and plain values travel: strings are cut, anything else (lists,
         * objects, health records) is dropped, so a careless call cannot send a diary.
         */
        fun sanitized(name: String, props: Map<String, Any?>, at: Instant): TelemetryEvent = TelemetryEvent(
            name = name.trim().take(MAX_NAME),
            occurredAt = at,
            props = props.entries.take(MAX_PROPS).mapNotNull { (key, value) ->
                val clean: Any? = when (value) {
                    null -> null
                    is Boolean, is Int, is Long -> value
                    is Double -> value.takeIf { it.isFinite() }
                    is Float -> value.toDouble().takeIf { it.isFinite() }
                    is String -> value.take(MAX_TEXT)
                    is Enum<*> -> value.name
                    else -> return@mapNotNull null
                }
                key.take(MAX_NAME) to clean
            }.toMap()
        )

        private fun jsonOf(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}

/** Who sent an event: app build and phone, never the person. */
data class TelemetryContext(val appVersion: String, val device: String, val android: Int, val locale: String)

/**
 * Events waiting to be sent, one JSON object per line in a private file. Survives restarts
 * and crashes (the crash handler appends synchronously) and keeps at most [maxLines], dropping
 * the oldest, so a phone offline for weeks cannot fill its storage.
 */
class TelemetryQueue(private val file: File, private val maxLines: Int = 2_000) {
    private val lock = Any()

    fun append(event: JsonObject) = synchronized(lock) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(event.toString() + "\n")
            trimIfNeeded()
        }
        Unit
    }

    /** Up to [limit] of the oldest events, each a JSON object line. */
    fun peek(limit: Int): List<String> = synchronized(lock) { readLines().take(limit) }

    /** Removes the [count] oldest events, after they were sent. */
    fun drop(count: Int) = synchronized(lock) { rewrite(readLines().drop(count)) }

    fun size(): Int = synchronized(lock) { readLines().size }

    fun clear() = synchronized(lock) { runCatching { file.delete() }; Unit }

    private fun readLines(): List<String> =
        if (file.exists()) runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()) else emptyList()

    private fun trimIfNeeded() {
        val lines = readLines()
        if (lines.size > maxLines) rewrite(lines.takeLast(maxLines))
    }

    private fun rewrite(lines: List<String>) {
        runCatching {
            if (lines.isEmpty()) file.delete()
            else file.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }
}
