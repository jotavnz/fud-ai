package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryStockTest {
    private fun entry(name: String, ingredients: List<MealIngredient> = emptyList()) =
        FoodEntry(name = name, calories = 500, protein = 30.0, carbs = 40.0, fat = 10.0, source = FoodSource.TEXT_INPUT, ingredients = ingredients)

    @Test
    fun parsesWeightsVolumesAndCounts() {
        assertEquals(PantryStock.Amount(1500.0, PantryStock.Unit.GRAMS), PantryStock.parse("1,5 kg"))
        assertEquals(PantryStock.Amount(500.0, PantryStock.Unit.GRAMS), PantryStock.parse("500 g"))
        assertEquals(PantryStock.Amount(1000.0, PantryStock.Unit.MILLILITERS), PantryStock.parse("1 litro"))
        assertEquals(PantryStock.Amount(6.0, PantryStock.Unit.COUNT), PantryStock.parse("6 huevos"))
        assertNull(PantryStock.parse("un poco"))
    }

    @Test
    fun homeMealTakesTheIngredientGramsOffTheStock() {
        val chicken = PantryItem(name = "Pechuga de pollo", quantity = "1 kg")
        val meal = entry("Pollo con arroz", listOf(MealIngredient(name = "pollo a la plancha", grams = 200.0, calories = 300, protein = 40.0, carbs = 0.0, fat = 5.0)))
        val use = PantryStock.suggestUses(meal, listOf(chicken, PantryItem(name = "Leche", quantity = "1 l"))).single()
        assertEquals(200.0, use.amount.value, 0.01)
        assertEquals("800 g", PantryStock.deduct(chicken, use.amount)?.quantity)
    }

    @Test
    fun countedItemsUseOneAndRunOut() {
        val eggs = PantryItem(name = "Huevos", quantity = "1 huevo")
        val use = PantryStock.suggestUses(entry("Huevos revueltos"), listOf(eggs)).single()
        assertNull(PantryStock.deduct(eggs, use.amount))
        assertEquals("5 huevos", PantryStock.deduct(eggs.copy(quantity = "6 huevos"), use.amount)?.quantity)
    }

    @Test
    fun portionsLeft() {
        assertEquals(3, PantryStock.portionsLeft(PantryItem(name = "Carne", quantity = "500 g"), PantryStock.Amount(150.0, PantryStock.Unit.GRAMS)))
        assertTrue(PantryStock.suggestUses(entry("Ensalada"), listOf(PantryItem(name = "Pollo", quantity = "1 kg"))).isEmpty())
    }
}
