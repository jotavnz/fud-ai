package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.services.ai.CoachTools
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class PantryTest {
    private val today = LocalDate.parse("2026-10-27")

    @Test
    fun receiptJsonBecomesAReviewablePurchase() {
        val purchase = ReceiptParser.parse(
            """Here you go: {"store":"Mercadona","date":"2026-10-25","currency":"EUR","total":null,
               "items":[{"name":"Pechuga de pollo","quantity":"1 kg","price":6.5,"location":"fridge"},
                        {"name":"Arroz","quantity":"1 kg","price":1.2,"location":"PANTRY"},
                        {"name":"  ","quantity":"","price":0.1},
                        {"name":"Guisantes","quantity":"400 g","price":1.0,"location":"congelador"}]}""",
            today
        )
        assertEquals("Mercadona", purchase.store)
        assertEquals("2026-10-25", purchase.date)
        assertEquals(listOf("Pechuga de pollo", "Arroz", "Guisantes"), purchase.items.map { it.name })
        assertEquals(
            listOf(PantryLocation.FRIDGE, PantryLocation.PANTRY, PantryLocation.FREEZER),
            purchase.items.map { it.location }
        )
        // A missing total falls back to the sum of the lines.
        assertEquals(8.7, purchase.total!!, 0.001)
    }

    @Test
    fun receiptWithoutDateUsesToday() {
        val purchase = ReceiptParser.parse("""{"store":"","date":"25/10","items":[]}""", today)
        assertEquals(today.toString(), purchase.date)
        assertNull(purchase.total)
    }

    @Test
    fun daysLeftCountsFromToday() {
        assertEquals(3L, PantryItem(name = "Leche", expiresOn = "2026-10-30").daysLeft(today))
        assertEquals(-1L, PantryItem(name = "Yogur", expiresOn = "2026-10-26").daysLeft(today))
        assertNull(PantryItem(name = "Sal").daysLeft(today))
    }

    @Test
    fun similarNamesAreCaught() {
        assertTrue(PantryMatching.similar("Huevos", "huevos blancos"))
        assertTrue(PantryMatching.similar("Leche entera", "LECHE"))
        assertTrue(PantryMatching.similar("Plátanos", "platano"))
        assertTrue(!PantryMatching.similar("Leche", "Pan"))
    }

    @Test
    fun coachAsksBeforeAddingLikelyDuplicates() {
        val saved = mutableListOf<PantryItem>()
        val tools = CoachTools(
            weights = emptyList(), bodyFats = emptyList(), foods = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC),
            pantryItems = listOf(PantryItem(name = "Huevos", quantity = "6")),
            pantryWriter = { saved += it }
        )
        assertTrue("add_pantry_items" in tools.availableToolNames)
        val items = listOf(
            mapOf("name" to "huevos blancos", "quantity" to "12"),
            mapOf("name" to "Arroz", "location" to "pantry", "best_before_days" to 180)
        )
        val first = JsonParser.parseString(tools.execute("add_pantry_items", mapOf("items" to items))).asJsonObject
        assertEquals(false, first["saved"].asBoolean)
        assertEquals("Huevos", first["possible_duplicates"].asJsonArray[0].asJsonObject["already_have"].asString)
        assertTrue(saved.isEmpty())

        val second = JsonParser.parseString(tools.execute("add_pantry_items", mapOf("items" to items, "confirmed" to true))).asJsonObject
        assertEquals(true, second["saved"].asBoolean)
        assertEquals(listOf("huevos blancos", "Arroz"), saved.map { it.name })
        assertEquals("2027-04-25", saved[1].expiresOn)
        assertEquals(PantryLocation.PANTRY, saved[1].location)
    }

    @Test
    fun coachSeesThePantryAndSpending() {
        val tools = CoachTools(
            weights = emptyList(),
            bodyFats = emptyList(),
            foods = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC),
            pantryItems = listOf(PantryItem(name = "Huevos", quantity = "6", expiresOn = "2026-10-29")),
            groceryPurchases = listOf(GroceryPurchase(date = "2026-10-20", store = "Super", total = 40.0, currency = "USD"))
        )
        assertTrue("get_pantry" in tools.availableToolNames)
        val payload = JsonParser.parseString(tools.execute("get_pantry", emptyMap<String, Any?>())).asJsonObject
        val egg = payload["items"].asJsonArray[0].asJsonObject
        assertEquals("Huevos", egg["name"].asString)
        assertEquals(2, egg["days_left"].asInt)
        assertEquals(40.0, payload["spending_last_90_days"].asJsonObject["USD"].asDouble, 0.001)
    }
}
