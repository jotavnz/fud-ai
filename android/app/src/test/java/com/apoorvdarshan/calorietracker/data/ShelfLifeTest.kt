package com.apoorvdarshan.calorietracker.data

import com.apoorvdarshan.calorietracker.models.PantryLocation
import com.apoorvdarshan.calorietracker.services.voice.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfLifeTest {
    private val table = ShelfLife.parse(RepoFiles.find("src/main/assets/shelf_life.json").readText())

    private fun days(name: String, location: PantryLocation = PantryLocation.FRIDGE) =
        ShelfLife.days(name, location, table)

    @Test
    fun spanishAndEnglishNamesFindTheirFood() {
        assertEquals(1L, days("Pechuga de pollo"))
        assertEquals(270L, days("Pechuga de pollo", PantryLocation.FREEZER))
        assertEquals(1L, days("Carne molida"))
        assertEquals(21L, days("Huevos"))
        assertEquals(21L, days("eggs"))
        assertEquals(7L, days("Yogur griego"))
        assertEquals(3L, days("Plátanos"))
        assertEquals(730L, days("Arroz blanco", PantryLocation.PANTRY))
    }

    @Test
    fun aGenericNameTakesTheShortestTimeOfItsMatches() {
        // "queso" fits hard cheeses (months) and soft ones (a week or two).
        assertEquals(7L, days("Queso"))
    }

    @Test
    fun noTimeFallsBackToTheLocationDefault() {
        // FoodKeeper says "use the package date" for milk in the fridge.
        assertNull(days("Leche entera"))
        assertNull(days("Cosa rara"))
        assertNull(days("Lechuga", PantryLocation.FREEZER))
    }
}
