package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.services.ai.CoachTools
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ShoppingHistoryTest {
    private val shops = listOf(
        GroceryPurchase(date = "2026-09-30", store = "Mercadona", total = 30.0, currency = "EUR",
            items = listOf(GroceryLine("Leche", price = 1.10), GroceryLine("Huevos", price = 2.50))),
        GroceryPurchase(date = "2026-10-05", store = "Lidl", total = 20.0, currency = "EUR",
            items = listOf(GroceryLine("leche entera", price = 0.95))),
        GroceryPurchase(date = "2026-10-20", store = "Mercadona", total = 12.5, currency = "EUR",
            items = listOf(GroceryLine("Leche", price = 1.20)), source = GroceryPurchase.SOURCE_LIST)
    )

    @Test
    fun monthsAreNewestFirstWithTotals() {
        val months = ShoppingHistory.months(shops)
        assertEquals(listOf("2026-10", "2026-09"), months.map { it.key })
        assertEquals(32.5, months[0].totals.getValue("EUR"), 0.001)
        assertEquals("2026-10-20", months[0].purchases.first().date)
    }

    @Test
    fun pricesShowLatestAndCheapestStore() {
        val milk = ShoppingHistory.prices(shops).first { it.name == "Leche" }
        assertEquals(1.20, milk.latest.price, 0.001)
        assertEquals("Mercadona", milk.latest.store)
        assertEquals("Lidl", milk.cheapest.store)
        assertEquals(2, milk.byStore.size)
    }

    @Test
    fun coachRecordsAShopWhenToldAboutOne() {
        val recorded = mutableListOf<GroceryPurchase>()
        val tools = CoachTools(
            weights = emptyList(), bodyFats = emptyList(), foods = emptyList(),
            clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC),
            pantryWriter = { },
            purchaseWriter = { recorded += it }
        )
        val result = JsonParser.parseString(
            tools.execute(
                "update_pantry",
                mapOf(
                    "items" to listOf(
                        mapOf("name" to "Pollo", "quantity" to "1 kg", "location" to "freezer", "price" to 7.5),
                        mapOf("name" to "Arroz", "price" to 1.2),
                        mapOf("name" to "Leche", "action" to "used_up")
                    ),
                    "shop" to mapOf("store" to "Éxito", "currency" to "COP")
                )
            )
        ).asJsonObject
        assertTrue(result["shop_recorded"].asBoolean)
        val shop = recorded.single()
        assertEquals("Éxito", shop.store)
        assertEquals(GroceryPurchase.SOURCE_COACH, shop.source)
        assertEquals(listOf("Pollo", "Arroz"), shop.items.map { it.name })
        assertNotNull(shop.total)
        assertEquals(8.7, shop.total!!, 0.001)
        assertEquals("2026-10-28", shop.date)
    }
}
