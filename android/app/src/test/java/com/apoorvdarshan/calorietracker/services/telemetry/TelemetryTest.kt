package com.apoorvdarshan.calorietracker.services.telemetry

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class TelemetryTest {
    private val context = TelemetryContext("8.0.1-debug", "HONOR Magic8 Lite", 36, "es-CL")
    private val at = Instant.parse("2026-10-28T12:00:00Z")
    private lateinit var dir: File
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("telemetry").toFile()
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun event(name: String, props: Map<String, Any?> = emptyMap()) =
        TelemetryEvent.sanitized(name, props, at).toJson("install-1", context)

    @Test
    fun onlyShortPlainValuesTravel() {
        val event = TelemetryEvent.sanitized(
            "ai_call",
            mapOf(
                "ok" to true,
                "ms" to 1234L,
                "note" to "x".repeat(1_000),
                "foods" to listOf("arroz", "pollo"),
                "weight" to mapOf("kg" to 82.4),
                "nan" to Double.NaN
            ),
            at
        )
        assertEquals(setOf("ok", "ms", "note", "nan"), event.props.keys)
        assertEquals(TelemetryEvent.MAX_TEXT, (event.props["note"] as String).length)
        assertEquals(null, event.props["nan"])
    }

    @Test
    fun rowMatchesTheEventsTable() {
        val json = event("screen", mapOf("route" to "home"))
        assertEquals("install-1", json["install_id"]!!.jsonPrimitive.content)
        assertEquals("2026-10-28T12:00:00Z", json["occurred_at"]!!.jsonPrimitive.content)
        assertEquals("HONOR Magic8 Lite", json["device"]!!.jsonPrimitive.content)
        assertEquals("home", json["props"]!!.jsonObject["route"]!!.jsonPrimitive.content)
    }

    @Test
    fun queueKeepsOrderAndCapsItsSize() {
        val queue = TelemetryQueue(File(dir, "q.jsonl"), maxLines = 3)
        (1..5).forEach { queue.append(event("e$it")) }
        assertEquals(3, queue.size())
        assertTrue(queue.peek(1).single().contains("\"e3\""))
        queue.drop(2)
        assertTrue(queue.peek(5).single().contains("\"e5\""))
        queue.clear()
        assertEquals(0, queue.size())
    }

    @Test
    fun flushSendsBatchesAndEmptiesTheQueue() = runTest {
        val queue = TelemetryQueue(File(dir, "q.jsonl"))
        (1..5).forEach { queue.append(event("e$it")) }
        repeat(3) { server.enqueue(MockResponse().setResponseCode(201)) }
        val uploader = TelemetryUploader(server.url("/").toString().trimEnd('/'), "anon-key", OkHttpClient())

        assertTrue(uploader.flush(queue, batchSize = 2))
        assertEquals(0, queue.size())
        val first = server.takeRequest()
        assertEquals("/rest/v1/events", first.path)
        assertEquals("anon-key", first.getHeader("apikey"))
        assertEquals("Bearer anon-key", first.getHeader("Authorization"))
        assertEquals(2, Json.parseToJsonElement(first.body.readUtf8()).jsonArray.size)
    }

    @Test
    fun serverTroubleKeepsEventsAndMalformedBatchesAreDropped() = runTest {
        val queue = TelemetryQueue(File(dir, "q.jsonl"))
        queue.append(event("a"))
        val uploader = TelemetryUploader(server.url("/").toString().trimEnd('/'), "anon-key", OkHttpClient())

        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(uploader.flush(queue))
        assertEquals(1, queue.size())

        server.enqueue(MockResponse().setResponseCode(401))
        assertFalse(uploader.flush(queue))
        assertEquals(1, queue.size())

        server.enqueue(MockResponse().setResponseCode(400))
        assertTrue(uploader.flush(queue))
        assertEquals(0, queue.size())
    }
}
