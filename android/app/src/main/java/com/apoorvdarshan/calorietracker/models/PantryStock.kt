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

    /** Receipts print "2 X 2386.84" (units × unit price); only the units are a quantity. */
    private val pricedQuantity = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*[xX×*]\s*\d[\d.,]*\s*$""")

    fun cleanQuantity(quantity: String): String = pricedQuantity.find(quantity)?.groupValues?.get(1) ?: quantity

    /** Units inside one package named in the food ("Huevos 1/2 cartón" = 15, "docena" = 12). */
    fun unitsPerPack(name: String): Int? {
        val key = PantryMatching.key(name.replace("½", " 1/2 ").replace("1/2", " medio "))
        return when {
            Regex("""\bmedi[oa] (carton|maple|bandeja)\b""").containsMatchIn(key) -> 15
            Regex("""\b(carton|cartones|maple|bandeja de 30)\b""").containsMatchIn(key) -> 30
            Regex("""\bmedia docena\b""").containsMatchIn(key) -> 6
            Regex("""\bdocenas?\b""").containsMatchIn(key) -> 12
            else -> Regex("""(?:\bx|\bpack de|\bpaquete de|\bbolsa de|\bcaja de)\s*(\d+)\b|\b(\d+)\s*(?:unidades|unid|und|uds|piezas|pzas|rebanadas|panes|huevos)\b""")
                .find(key)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()?.takeIf { it > 1 } }
        }
    }

    private val contentInName = Regex(
        """(\d+(?:[.,]\d+)?)\s*(kg|kilos?|kilogramos?|grs?|gramos?|g|ml|cc|mililitros?|lts?|litros?|l|lbs?|libras?)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * What one package holds when the name says it: "Arroz 1 kg", "Leche 1 L", "Atún 140 g",
     * "Pan x6", "Huevos 1/2 cartón". Null when the name gives no size.
     */
    fun packContent(name: String): Amount? {
        val text = name.replace("½", "0.5 ").replace(Regex("""\b1/2\s*(?=kg|kilo|l\b|lt|litro)""", RegexOption.IGNORE_CASE), "0.5 ")
        contentInName.find(text)?.let { m -> parse("${m.groupValues[1]} ${m.groupValues[2]}")?.takeIf { it.unit != Unit.COUNT && it.value > 0 }?.let { return it } }
        return unitsPerPack(name)?.let { Amount(it.toDouble(), Unit.COUNT) }
    }

    /** Words that only say how many packages there are ("2 paquetes", "3 latas"). */
    private val packageWords = setOf(
        "", "paquete", "paquetes", "bolsa", "bolsas", "lata", "latas", "caja", "cajas", "botella", "botellas",
        "frasco", "frascos", "pote", "potes", "sobre", "sobres", "envase", "envases", "carton", "cartón",
        "cartones", "unidad", "unidades", "und", "u", "pack", "packs", "pza", "pzas", "x"
    )

    private fun countWord(name: String): String {
        val key = PantryMatching.key(name)
        return when {
            key.contains("huevo") -> "huevos"
            key.contains("rebanad") -> "rebanadas"
            else -> "unidades"
        }
    }

    /** Rough weight of one unit, to turn a meal's grams into units (two eggs in a perico). */
    private val unitGrams = listOf(
        "huevo" to 50.0, "egg" to 50.0, "platano" to 180.0, "cambur" to 120.0, "banana" to 120.0,
        "tomate" to 120.0, "cebolla" to 150.0, "papa" to 170.0, "aguacate" to 170.0, "manzana" to 180.0,
        "limon" to 60.0, "zanahoria" to 80.0, "pimenton" to 150.0, "arepa" to 90.0, "pan " to 60.0
    )

    private fun gramsPerUnit(name: String): Double? {
        val key = PantryMatching.key(name) + " "
        return unitGrams.firstOrNull { (word, _) -> key.contains(word) }?.second
    }

    /**
     * The item with its quantity in units used in the kitchen: no receipt price, and packages
     * opened up ("2" of "Huevos 1/2 cartón" → "30 huevos").
     */
    fun normalized(item: PantryItem): PantryItem {
        val clean = cleanQuantity(item.quantity)
        val amount = parse(clean)
        val content = packContent(item.name)
        // Only a count of packages opens up ("2", "3 latas"); "30 huevos" or "800 g" are already in units.
        val word = pattern.find(clean)?.groupValues?.get(2).orEmpty().lowercase(Locale.ROOT)
        val rest = clean.substringAfter(word, "").trim().takeIf { word.isNotEmpty() } ?: clean.replace(Regex("""^\s*\d+(?:[.,]\d+)?"""), "").trim()
        val packages = amount != null && amount.unit == Unit.COUNT && word in packageWords && rest.isEmpty()
        if (packages && content != null && amount != null) {
            val total = Amount(amount.value * content.value, content.unit)
            val text = format(total)
            return item.copy(quantity = if (content.unit == Unit.COUNT) "$text ${countWord(item.name)}" else text)
        }
        return if (clean == item.quantity) item else item.copy(quantity = clean)
    }

    fun parse(quantity: String): Amount? {
        val match = pattern.find(cleanQuantity(quantity)) ?: return null
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
                val rest = cleanQuantity(originalQuantity).substringAfter(word, "").trim().takeIf { word.isNotEmpty() }.orEmpty()
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
    fun suggestUses(entry: FoodEntry, pantry: List<PantryItem>): List<Use> = pantry.map(::normalized).mapNotNull { item ->
        val stock = parse(item.quantity)
        val ingredient = entry.ingredients.firstOrNull { mentions(it.name, item) }
        if (ingredient == null && !mentions(entry.name, item)) return@mapNotNull null
        val grams = ingredient?.grams?.takeIf { it > 0 }
            ?: entry.servingSizeGrams?.takeIf { it > 0 }?.times(entry.selectedServingQuantity ?: 1.0)
        val perUnit = gramsPerUnit(item.name)
        val amount = when {
            (stock == null || stock.unit == Unit.COUNT) && grams != null && perUnit != null ->
                Amount(Math.round(grams / perUnit).toDouble().coerceAtLeast(1.0).coerceAtMost(stock?.value ?: Double.MAX_VALUE), Unit.COUNT)
            stock == null || stock.unit == Unit.COUNT -> Amount(1.0, Unit.COUNT)
            grams != null -> Amount(grams.coerceAtMost(stock.value), stock.unit)
            else -> Amount((stock.value / 4).coerceAtLeast(1.0), stock.unit)
        }
        Use(item, amount)
    }

    /** The item after using [amount]; null when nothing is left (it goes to the shopping list). */
    fun deduct(original: PantryItem, amount: Amount): PantryItem? {
        val item = normalized(original)
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
