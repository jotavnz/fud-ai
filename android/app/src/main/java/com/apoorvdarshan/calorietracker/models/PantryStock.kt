package com.apoorvdarshan.calorietracker.models

import java.util.Locale

/**
 * Pantry quantities as numbers, so cooking at home can take stock down and Coach can say how
 * many portions are left. Quantities stay free text ("500 g", "1 kg", "6", "2 latas"); only
 * the leading number and a weight/volume unit are understood, anything else counts units.
 * Idea from grocy's "consume recipe" (MIT); written from scratch here.
 */
object PantryStock {
    enum class Unit { GRAMS, MILLILITERS, COUNT }

    data class Amount(val value: Double, val unit: Unit)

    private val pattern = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*([a-zA-Záéíóú]*)""")

    fun parse(quantity: String): Amount? {
        val match = pattern.find(quantity) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "g", "gr", "grs", "gramos", "gramo", "grams", "gram" -> Amount(number, Unit.GRAMS)
            "kg", "kilo", "kilos", "kilogramos" -> Amount(number * 1000, Unit.GRAMS)
            "lb", "lbs", "libra", "libras" -> Amount(number * 453.6, Unit.GRAMS)
            "ml", "mililitros" -> Amount(number, Unit.MILLILITERS)
            "l", "lt", "lts", "litro", "litros", "liter", "liters" -> Amount(number * 1000, Unit.MILLILITERS)
            else -> Amount(number, Unit.COUNT)
        }
    }

    fun format(amount: Amount, originalQuantity: String = ""): String {
        val v = amount.value
        fun n(x: Double) = if (x % 1.0 == 0.0) x.toLong().toString() else String.format(Locale.ROOT, "%.1f", x)
        return when (amount.unit) {
            Unit.GRAMS -> if (v >= 1000) "${n(v / 1000)} kg" else "${n(v)} g"
            Unit.MILLILITERS -> if (v >= 1000) "${n(v / 1000)} l" else "${n(v)} ml"
            Unit.COUNT -> {
                // Keep the user's word after the number ("2 latas").
                val word = pattern.find(originalQuantity)?.groupValues?.get(2).orEmpty()
                val rest = originalQuantity.substringAfter(word, "").trim().takeIf { word.isNotEmpty() }.orEmpty()
                listOf(n(v), word, rest).filter { it.isNotBlank() }.joinToString(" ")
            }
        }
    }

    /** One pantry item a home-cooked meal probably used, and how much (editable by the user). */
    data class Use(val item: PantryItem, val amount: Amount)

    /** Words that name the food ("pechuga de pollo" → pechuga, pollo), without short connectors. */
    private fun words(name: String): Set<String> =
        PantryMatching.key(name).split(' ').filter { it.length >= 4 }.map { it.trimEnd('s') }.toSet()

    private fun mentions(text: String, item: PantryItem): Boolean {
        val dish = words(text)
        return words(item.name).any { it in dish }
    }

    /**
     * Pantry items a logged meal seems to use: matched against each ingredient, else the dish name.
     * Grams come from the ingredient, else the serving; counted items use one unit.
     */
    fun suggestUses(entry: FoodEntry, pantry: List<PantryItem>): List<Use> = pantry.mapNotNull { item ->
        val stock = parse(item.quantity)
        val ingredient = entry.ingredients.firstOrNull { mentions(it.name, item) }
        if (ingredient == null && !mentions(entry.name, item)) return@mapNotNull null
        val grams = ingredient?.grams?.takeIf { it > 0 }
            ?: entry.servingSizeGrams?.takeIf { it > 0 }?.times(entry.selectedServingQuantity ?: 1.0)
        val amount = when {
            stock == null || stock.unit == Unit.COUNT -> Amount(1.0, Unit.COUNT)
            grams != null -> Amount(grams.coerceAtMost(stock.value), stock.unit)
            else -> Amount((stock.value / 4).coerceAtLeast(1.0), stock.unit)
        }
        Use(item, amount)
    }

    /** The item after using [amount]; null when nothing is left (it goes to the shopping list). */
    fun deduct(item: PantryItem, amount: Amount): PantryItem? {
        val stock = parse(item.quantity) ?: return null
        // suggestUses always speaks the stock's own unit, so the numbers subtract directly.
        val left = stock.value - amount.value
        if (left <= 0.0001) return null
        return item.copy(quantity = format(Amount(left, stock.unit), item.quantity))
    }

    /** How many portions of [portion] the item still gives (null when the quantity is not a number). */
    fun portionsLeft(item: PantryItem, portion: Amount): Int? {
        val stock = parse(item.quantity) ?: return null
        if (portion.value <= 0) return null
        return (stock.value / portion.value).toInt()
    }
}
