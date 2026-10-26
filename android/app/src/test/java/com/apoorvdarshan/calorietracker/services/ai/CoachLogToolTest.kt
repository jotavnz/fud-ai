package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.ChatMessage
import com.apoorvdarshan.calorietracker.models.CoachLogCollector
import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.CoachLogMacros
import com.apoorvdarshan.calorietracker.models.CoachLogProposal
import com.apoorvdarshan.calorietracker.models.CoachProposalCollector
import com.apoorvdarshan.calorietracker.models.MealSchedule
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.foodTotals
import com.apoorvdarshan.calorietracker.models.updatingLogProposal
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class CoachLogToolTest {
    private val today = LocalDate.parse("2026-10-26")
    private val zone = ZoneOffset.UTC

    /** The report that started this: two meals, mate and water, in Spanish. */
    private val userReport = mapOf(
        "items" to listOf(
            mapOf(
                "type" to "food", "description" to "2 sándwiches de jamón y queso", "name" to "Sándwiches de jamón y queso",
                "meal" to "desayuno", "calories" to 640, "protein_g" to 34, "carbs_g" to 60.5, "fat_g" to 28
            ),
            mapOf(
                "type" to "food", "description" to "sándwich granjero con milanesa", "meal" to "lunch",
                "calories" to "850", "serving" to "1 sándwich"
            ),
            mapOf("type" to "food", "description" to "1.5 L de mate", "name" to "Mate", "calories" to 10),
            mapOf("type" to "water", "amount" to 3, "unit" to "vasos")
        )
    )

    @Test
    fun parsesTheUsersReportIntoOneRowPerDishPlusWater() {
        val parsed = CoachLogTool.parse(userReport, today)
        assertEquals(emptyList<String>(), parsed.errors)
        assertEquals(4, parsed.items.size)

        val breakfast = parsed.items[0]
        assertEquals(CoachLogItem.Kind.FOOD, breakfast.kind)
        assertEquals(MealType.BREAKFAST, breakfast.meal)
        assertEquals("2026-10-26", breakfast.date)
        assertEquals(CoachLogMacros(640, 34.0, 60.5, 28.0), breakfast.estimate)
        assertEquals("Sándwiches de jamón y queso", breakfast.displayName)

        val lunch = parsed.items[1]
        assertEquals(MealType.LUNCH, lunch.meal)
        assertEquals(850, lunch.estimate?.calories)
        assertEquals("1 sándwich", lunch.serving)
        // No name: the description is shown.
        assertEquals("sándwich granjero con milanesa", lunch.displayName)

        // Mate is a drink, not water, and has no meal: the app infers one later.
        assertNull(parsed.items[2].meal)

        val water = parsed.items[3]
        assertEquals(CoachLogItem.Kind.WATER, water.kind)
        assertEquals(750, water.waterMl)

        // Each row has its own id (also the diary entry id).
        assertEquals(4, parsed.items.map { it.id }.toSet().size)
    }

    @Test
    fun invalidRowsAreReportedAndTheRestKept() {
        val parsed = CoachLogTool.parse(
            mapOf(
                "items" to listOf(
                    mapOf("type" to "smoothie"),
                    mapOf("type" to "food"),
                    mapOf("type" to "food", "description" to "pizza", "date" to "2026-10-27"),
                    mapOf("type" to "food", "description" to "pizza", "date" to "2026-08-01"),
                    mapOf("type" to "food", "description" to "pizza", "meal" to "brunch"),
                    mapOf("type" to "water", "amount" to 9, "unit" to "l"),
                    mapOf("type" to "weight", "value" to 180, "unit" to "lb"),
                    mapOf("type" to "body_fat", "percent" to 18.5),
                    mapOf("type" to "cardio", "name" to "Caminata", "minutes" to 30, "calories" to 120),
                    mapOf("type" to "workout", "description" to "3x10 press de banca 60 kg", "date" to "yesterday"),
                    "not an object"
                )
            ),
            today
        )
        assertEquals(7, parsed.errors.size)
        assertTrue(parsed.errors[0].contains("type must be"))
        assertTrue(parsed.errors[2].contains("future"))
        assertTrue(parsed.errors[3].contains("within 30 days"))
        assertTrue(parsed.errors[4].contains("meal must be"))
        assertTrue(parsed.errors[5].contains("at most 5000 ml"))

        val (weight, bodyFat, cardio, workout) = parsed.items
        assertEquals(81.6, weight.weightKg!!, 0.05)
        assertEquals(18.5, bodyFat.bodyFatPercent!!, 0.0)
        assertEquals(30, cardio.minutes)
        assertEquals(120, cardio.caloriesBurned)
        assertEquals("Caminata", cardio.displayName)
        assertEquals("2026-10-25", workout.date)
    }

    @Test
    fun proposeKeepsOneCardAndNeverClaimsItSaved() {
        val collector = CoachLogCollector()
        val result = json(CoachLogTool.propose(userReport, today, collector))
        assertEquals("pending_user_confirmation", result["status"].asString)
        assertFalse(result["saved"].asBoolean)
        assertEquals(4, result.getAsJsonArray("items").size())
        assertTrue(result["note"].asString.contains("Never say it was logged"))

        val card = collector.proposals.single()
        assertEquals(result["proposal_id"].asString, card.id)
        assertEquals(CoachLogProposal.Status.PREPARING, card.status)

        assertTrue(json(CoachLogTool.propose(mapOf("items" to emptyList<Any>()), today, collector))["error"].asString.startsWith("Nothing to log"))
        assertTrue(json(CoachLogTool.propose(userReport, today, null))["error"].asString.contains("only available in Coach chat"))
    }

    @Test
    fun coachToolsOfferTheLogToolOnlyWithACollector() {
        val plain = CoachTools(weights = emptyList(), bodyFats = emptyList(), foods = emptyList())
        assertFalse(CoachLogTool.TOOL_NAME in plain.availableToolNames)

        val collector = CoachLogCollector()
        val chat = CoachTools(
            weights = emptyList(), bodyFats = emptyList(), foods = emptyList(),
            proposals = CoachProposalCollector(),
            clock = Clock.fixed(Instant.parse("2026-10-26T13:00:00Z"), zone),
            logProposals = collector
        )
        assertEquals(CoachTools.TOOL_NAMES + CoachTools.PROPOSAL_TOOL_NAMES + CoachLogTool.TOOL_NAME, chat.availableToolNames)
        assertTrue(CoachTools.TOOL_DESCRIPTIONS.getValue(CoachLogTool.TOOL_NAME).contains("does NOT save anything"))
        assertEquals(listOf("items"), CoachTools.parameterSchemaFor(CoachLogTool.TOOL_NAME)["required"])
        // The voice assistant's data tools never include it.
        assertFalse(CoachLogTool.TOOL_NAME in CoachTools.TOOL_NAMES)

        json(chat.execute(CoachLogTool.TOOL_NAME, userReport))
        assertEquals("2026-10-26", collector.proposals.single().items.first().date)
    }

    @Test
    fun fallbackBlockBecomesArgumentsAndLeavesTheVisibleReply() {
        val reply = """
            ¡Listo! Te preparé la tarjeta.

            <fudai-log>{"items":[{"type":"food","description":"2 empanadas","meal":"dinner","calories":540},{"type":"water","amount":500,"unit":"ml"}]}</fudai-log>
        """.trimIndent()
        val (cleaned, args) = CoachLogTool.extractFallbackBlock(reply)
        assertEquals("¡Listo! Te preparé la tarjeta.", cleaned)
        val parsed = CoachLogTool.parse(args!!, today)
        assertEquals(listOf(CoachLogItem.Kind.FOOD, CoachLogItem.Kind.WATER), parsed.items.map { it.kind })
        assertEquals(540, parsed.items[0].estimate?.calories)
        assertEquals(500, parsed.items[1].waterMl)

        val fenced = "Ok\n```fudai-log\n{\"items\":[{\"type\":\"weight\",\"value\":72,\"unit\":\"kg\"}]}\n```"
        val (fencedText, fencedArgs) = CoachLogTool.extractFallbackBlock(fenced)
        assertEquals("Ok", fencedText)
        assertEquals(72.0, CoachLogTool.parse(fencedArgs!!, today).items.single().weightKg!!, 0.0)

        assertEquals("Hola" to null, CoachLogTool.extractFallbackBlock("Hola"))
        // A broken block is still hidden but logs nothing.
        val (brokenText, brokenArgs) = CoachLogTool.extractFallbackBlock("Hola <fudai-log>{nope}</fudai-log>")
        assertEquals("Hola", brokenText)
        assertNull(brokenArgs)
    }

    @Test
    fun timestampsLandInsideTheRequestedMeal() {
        val schedule = MealSchedule.Default
        val lunchNow = Instant.parse("2026-10-26T13:10:00Z")
        val day = today

        // Requested meal is happening now: log at now.
        assertEquals(lunchNow, CoachLogTool.timestamp(day, null, MealType.LUNCH, lunchNow, zone, schedule))
        // Breakfast reported at lunch time: a breakfast time, not now.
        assertEquals(
            Instant.parse("2026-10-26T07:30:00Z"),
            CoachLogTool.timestamp(day, null, MealType.BREAKFAST, lunchNow, zone, schedule)
        )
        // Dinner later today is never logged in the future.
        assertEquals(lunchNow, CoachLogTool.timestamp(day, null, MealType.DINNER, lunchNow, zone, schedule))
        // Yesterday's dinner.
        assertEquals(
            Instant.parse("2026-10-25T18:30:00Z"),
            CoachLogTool.timestamp(day.minusDays(1), null, MealType.DINNER, lunchNow, zone, schedule)
        )
        // An explicit time wins.
        assertEquals(
            Instant.parse("2026-10-26T09:15:00Z"),
            CoachLogTool.timestamp(day, LocalTime.of(9, 15), MealType.LUNCH, lunchNow, zone, schedule)
        )
        // No meal: now (today) or the same time of day (earlier day).
        assertEquals(lunchNow, CoachLogTool.timestamp(day, null, null, lunchNow, zone, schedule))
        assertEquals(
            Instant.parse("2026-10-24T13:10:00Z"),
            CoachLogTool.timestamp(day.minusDays(2), null, null, lunchNow, zone, schedule)
        )

        val noMeal = CoachLogItem(kind = CoachLogItem.Kind.FOOD, description = "mate", date = day.toString())
        assertEquals(MealType.LUNCH, CoachLogTool.inferredMeal(noMeal, lunchNow, zone, schedule))
        assertEquals(MealType.DINNER, CoachLogTool.inferredMeal(noMeal.copy(meal = MealType.DINNER), lunchNow, zone, schedule))
        assertEquals(MealType.SNACK, CoachLogTool.parseMeal("merienda"))
        assertEquals(MealType.DINNER, CoachLogTool.parseMeal("Cena"))
    }

    @Test
    fun cardsPersistInChatHistoryAndTellTheModelWhatHappened() {
        val items = CoachLogTool.parse(userReport, today).items
        val card = CoachLogProposal(items = items)
        val message = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            content = "Revisa la tarjeta.",
            timestamp = Instant.parse("2026-10-26T10:00:00Z"),
            logProposals = listOf(card)
        )

        val codec = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val restored = codec.decodeFromString(
            ListSerializer(ChatMessage.serializer()),
            codec.encodeToString(ListSerializer(ChatMessage.serializer()), listOf(message))
        ).single()
        assertEquals(message, restored)
        assertTrue(restored.contextContent.contains("waiting for the user to tap Log"))

        // Older saved chats have no logProposals and still load.
        val legacy = codec.decodeFromString(
            ListSerializer(ChatMessage.serializer()),
            """[{"id":"${message.id}","role":"assistant","content":"hi","timestamp":1792994400000}]"""
        ).single()
        assertTrue(legacy.logProposals.isEmpty())

        val logged = listOf(message).updatingLogProposal(message.id, card.id) {
            it.copy(status = CoachLogProposal.Status.LOGGED, items = it.items.map { item -> item.copy(state = CoachLogItem.State.LOGGED) })
        }!!.single()
        assertTrue(logged.contextContent.contains("logged by the user"))
        assertNull(listOf(message).updatingLogProposal(message.id, "missing") { it })
        assertNull(listOf(message).updatingLogProposal(message.id, card.id) { it })
    }

    @Test
    fun totalsAndReviewUseTheEstimateUntilAnalyzed() {
        val items = CoachLogTool.parse(userReport, today).items
        val totals = items.foodTotals()
        assertEquals(640 + 850 + 10, totals.calories)
        assertEquals(34.0, totals.protein, 0.0)

        val review = items[0].reviewAnalysis()
        assertNotNull(review)
        assertEquals(640, review!!.calories)
        assertFalse(review.servingSizeIsKnown)
        assertNull(items[3].reviewAnalysis())

        val analyzed = items[0].copy(analysis = review.copy(calories = 700))
        assertEquals(700, analyzed.effectiveMacros?.calories)
    }

    private fun json(raw: String): JsonObject = JsonParser.parseString(raw).asJsonObject
}
