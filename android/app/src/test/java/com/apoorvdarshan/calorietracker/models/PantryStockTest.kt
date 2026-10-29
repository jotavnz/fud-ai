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

    @Test
    fun receiptPriceIsNotAQuantityAndHalfCartonsOpenIntoEggs() {
        val eggs = PantryItem(name = "Huevos tipo AA 1/2 cartón", quantity = "2X2386.84")
        assertEquals("30 huevos", PantryStock.normalized(eggs).quantity)
        // Already in eggs: not multiplied again.
        assertEquals("30 huevos", PantryStock.normalized(eggs.copy(quantity = "30 huevos")).quantity)
        val perico = entry("Arepa con huevo perico", listOf(MealIngredient(name = "huevo perico", grams = 100.0, calories = 150, protein = 12.0, carbs = 2.0, fat = 10.0)))
        val use = PantryStock.suggestUses(perico, listOf(eggs)).single()
        assertEquals(2.0, use.amount.value, 0.01)
        assertEquals("28 huevos", PantryStock.deduct(eggs, use.amount)?.quantity)
        assertEquals("2", PantryStock.cleanQuantity("2 X 2386.84"))
    }

    @Test
    fun packagesOfAnyFoodOpenIntoTheirContent() {
        fun n(name: String, q: String) = PantryStock.normalized(PantryItem(name = name, quantity = q)).quantity
        assertEquals("2 kg", n("Arroz 1 kg", "2"))
        assertEquals("3 l", n("Leche completa 1L", "3 cartones"))
        assertEquals("420 g", n("Atún en aceite 140 g", "3 latas"))
        assertEquals("1 kg", n("Harina de maíz 1/2 kg", "2"))
        assertEquals("12 unidades", n("Pan de sándwich x6", "2"))
        assertEquals("24 unidades", n("Huevos docena", "2").replace("huevos", "unidades"))
        // Already in kitchen units, or no size in the name: left alone.
        assertEquals("800 g", n("Arroz 1 kg", "800 g"))
        assertEquals("3", n("Tomates", "3"))
        // A home-cooked meal then takes grams off the opened-up stock.
        val rice = PantryItem(name = "Arroz 1 kg", quantity = "2")
        val meal = entry("Arroz con pollo", listOf(MealIngredient(name = "arroz blanco", grams = 150.0, calories = 200, protein = 4.0, carbs = 45.0, fat = 0.5)))
        val use = PantryStock.suggestUses(meal, listOf(rice)).single()
        assertEquals("1.85 kg", PantryStock.deduct(rice, use.amount)?.quantity)
    }

    @Test
    fun receiptQuantitiesWithATrailingXAndFoodSoldByTheKilo() {
        fun n(name: String, q: String) = PantryStock.normalized(PantryItem(name = name, quantity = q)).quantity
        assertEquals("1", n("Queso mozzarella", "1 X"))
        assertEquals("8 unidades", n("Jamón pechuga de pavo al vacío 8 un", "1 X"))
        assertEquals("2.38 kg", n("Filet de pollo KG", "2.38 X"))
        assertEquals("1 kg", n("Carne molida premium bandeja 500 gr", "2 X"))
        assertEquals("445 g", n("Mayonesa mavesa 445 gr 1", "1 X"))
        assertEquals("1 kg", n("Pasta pluma premium Capri 1 kg", "1 X"))
    }
}
