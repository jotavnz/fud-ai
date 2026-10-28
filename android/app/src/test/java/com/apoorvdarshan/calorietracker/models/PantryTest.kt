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

    private val days: (String, PantryLocation) -> Long = { _, location -> location.defaultShelfLifeDays }

    @Test
    fun aLongStoryIsSortedIntoThePantry() {
        val milk = PantryItem(name = "Leche", quantity = "1", location = PantryLocation.FRIDGE, expiresOn = "2026-10-29")
        val eggs = PantryItem(name = "Huevos", quantity = "6", location = PantryLocation.FRIDGE, expiresOn = "2026-11-10")
        val chicken = PantryItem(name = "Pechuga de pollo", quantity = "500 g", location = PantryLocation.FRIDGE, expiresOn = "2026-10-28")
        val outcome = PantryEditor.apply(
            listOf(milk, eggs, chicken),
            listOf(
                PantryChange(PantryChange.Action.ADD, "huevos blancos", "12", PantryLocation.FRIDGE),
                PantryChange(PantryChange.Action.ADD, "Arroz", "1 kg", PantryLocation.PANTRY, bestBeforeDays = 180),
                PantryChange(PantryChange.Action.SET, "pollo", location = PantryLocation.FREEZER),
                PantryChange(PantryChange.Action.USED_UP, "leche"),
                PantryChange(PantryChange.Action.DISCARD, "yogur")
            ),
            today,
            days
        )
        assertEquals(
            listOf(PantryEditor.Result.MERGED, PantryEditor.Result.ADDED, PantryEditor.Result.UPDATED, PantryEditor.Result.REMOVED, PantryEditor.Result.NOT_FOUND),
            outcome.lines.map { it.result }
        )
        val byName = outcome.items.associateBy { it.name }
        // Bought again: joined with the eggs already there, the sooner date kept.
        assertEquals("18", byName.getValue("Huevos").quantity)
        assertEquals("2026-11-01", byName.getValue("Huevos").expiresOn)
        assertEquals("2027-04-25", byName.getValue("Arroz").expiresOn)
        // Moved to the freezer: freezer shelf life from today.
        assertEquals(PantryLocation.FREEZER, byName.getValue("Pechuga de pollo").location)
        assertEquals("2027-01-25", byName.getValue("Pechuga de pollo").expiresOn)
        assertTrue("Leche" !in byName)
        assertEquals(listOf("Leche"), outcome.toShoppingList.map { it.name })
    }

    @Test
    fun quantitiesAddUpWhenTheUnitsMatch() {
        assertEquals("18", PantryEditor.mergeQuantity("6", "12"))
        assertEquals("1.5 kg", PantryEditor.mergeQuantity("1 kg", "0,5 kg"))
        assertEquals("500 g + 1 kg", PantryEditor.mergeQuantity("500 g", "1 kg"))
        assertEquals("2", PantryEditor.mergeQuantity("", "2"))
    }

    @Test
    fun coachUpdatesThePantryWithoutAsking() {
        val saved = mutableListOf<PantryChange>()
        val tools = CoachTools(
            weights = emptyList(), bodyFats = emptyList(), foods = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC),
            pantryItems = listOf(PantryItem(name = "Huevos", quantity = "6")),
            pantryWriter = { saved += it }
        )
        assertTrue("update_pantry" in tools.availableToolNames)
        val items = listOf(
            mapOf("name" to "huevos blancos", "quantity" to "12"),
            mapOf("name" to "Arroz", "location" to "pantry", "best_before_days" to 180),
            mapOf("action" to "used_up", "name" to "Huevos")
        )
        val result = JsonParser.parseString(tools.execute("update_pantry", mapOf("items" to items))).asJsonObject
        assertEquals(true, result["saved"].asBoolean)
        val lines = result["changes"].asJsonArray.map { it.asJsonObject["result"].asString }
        assertEquals(listOf("merged", "added", "removed"), lines)
        assertEquals(listOf("huevos blancos", "Arroz", "Huevos"), saved.map { it.name })
        // A second call in the same reply sees the first: the eggs are gone now.
        val again = JsonParser.parseString(tools.execute("get_pantry", emptyMap<String, Any?>())).asJsonObject
        assertEquals(listOf("Arroz"), again["items"].asJsonArray.map { it.asJsonObject["name"].asString })
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
