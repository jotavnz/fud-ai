package com.apoorvdarshan.calorietracker.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/** Where a food is kept at home. */
@Serializable
enum class PantryLocation {
    FRIDGE,
    FREEZER,
    PANTRY;

    /** Rough shelf life when the user gives no date (opened/unknown items), in days. */
    val defaultShelfLifeDays: Long
        get() = when (this) {
            FRIDGE -> 5
            FREEZER -> 90
            PANTRY -> 180
        }

    companion object {
        fun parse(value: String?): PantryLocation? = when (value?.trim()?.lowercase()) {
            "fridge", "nevera", "refrigerator" -> FRIDGE
            "freezer", "congelador" -> FREEZER
            "pantry", "despensa", "cupboard" -> PANTRY
            else -> null
        }
    }
}

/** One food the user has at home. Quantities are free-form ("2", "500 g", "1 bottle"). */
@Serializable
data class PantryItem(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val quantity: String = "",
    val location: PantryLocation = PantryLocation.FRIDGE,
    /** ISO date (yyyy-MM-dd) the item should be eaten by, when known or estimated. */
    val expiresOn: String? = null,
    val barcode: String? = null,
    @Serializable(with = InstantSerializer::class)
    val addedAt: Instant = Instant.now()
) {
    fun daysLeft(today: LocalDate = LocalDate.now()): Long? =
        expiresOn?.let { runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(it)) }.getOrNull() }
}

/** Something to buy on the next shop. */
@Serializable
data class ShoppingItem(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val quantity: String = "",
    val checked: Boolean = false,
    /** Where it goes once bought. */
    val location: PantryLocation = PantryLocation.PANTRY
)

/** One shop: what was spent, so Coach can summarize spending and suggest the next shopping list. */
@Serializable
data class GroceryPurchase(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    /** ISO date of the purchase. */
    val date: String,
    val store: String = "",
    val total: Double? = null,
    val currency: String = "",
    val items: List<GroceryLine> = emptyList()
)

@Serializable
data class GroceryLine(
    val name: String,
    val quantity: String = "",
    val price: Double? = null,
    val location: PantryLocation = PantryLocation.PANTRY
)

/** Spots items that are probably the same food ("Huevos" vs "huevos blancos", "leche" vs "Leche entera"). */
object PantryMatching {
    private val accents = Regex("[\\u0300-\\u036f]")

    fun key(name: String): String =
        java.text.Normalizer.normalize(name.lowercase().trim(), java.text.Normalizer.Form.NFD)
            .replace(accents, "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun similar(a: String, b: String): Boolean {
        val ka = key(a)
        val kb = key(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        val wa = ka.split(' ').filter { it.length >= 4 }.map { it.trimEnd('s') }.toSet()
        val wb = kb.split(' ').filter { it.length >= 4 }.map { it.trimEnd('s') }.toSet()
        return wa.isNotEmpty() && wb.isNotEmpty() && (wa.containsAll(wb) || wb.containsAll(wa))
    }
}

/**
 * One thing the user said about their food at home: bought or has it, corrected or moved it,
 * finished it, or threw it away. Coach turns a long story ("I went shopping, put the chicken in
 * the freezer, finished the milk...") into a list of these.
 */
data class PantryChange(
    val action: Action,
    val name: String,
    val quantity: String = "",
    /** For ADD where it goes; for SET where it now is (a move). */
    val location: PantryLocation? = null,
    val bestBeforeDays: Long? = null
) {
    enum class Action {
        ADD, SET, USED_UP, DISCARD;

        companion object {
            fun parse(value: String?): Action? = when (value?.trim()?.lowercase()) {
                "add", "bought", "have" -> ADD
                "set", "update", "move", "moved", "correct" -> SET
                "used_up", "finished", "eaten" -> USED_UP
                "discard", "thrown_away", "remove", "delete" -> DISCARD
                else -> null
            }
        }
    }
}

/**
 * Applies [PantryChange]s to the pantry: what is bought again joins the item already there,
 * a move to the freezer gets a freezer best-before, what is finished goes on the shopping list.
 */
object PantryEditor {
    enum class Result { ADDED, MERGED, UPDATED, REMOVED, NOT_FOUND }

    data class Line(val change: PantryChange, val result: Result, val item: PantryItem?)

    data class Outcome(val items: List<PantryItem>, val toShoppingList: List<PantryItem>, val lines: List<Line>)

    private val amount = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(.*)$")

    fun apply(
        current: List<PantryItem>,
        changes: List<PantryChange>,
        today: LocalDate,
        estimateDays: (String, PantryLocation) -> Long = com.apoorvdarshan.calorietracker.data.ShelfLife::estimateDays
    ): Outcome {
        val items = current.toMutableList()
        val toShop = mutableListOf<PantryItem>()
        fun bestBefore(name: String, location: PantryLocation, days: Long?) =
            today.plusDays(days ?: estimateDays(name, location)).toString()

        fun addNew(change: PantryChange): Line {
            val location = change.location ?: PantryLocation.FRIDGE
            val match = find(items, change.name, location, sameLocationOnly = true)
            if (match != null) {
                val newDate = bestBefore(change.name, location, change.bestBeforeDays)
                // The sooner date wins, so the item still shows up as "eat soon".
                val merged = match.copy(
                    quantity = mergeQuantity(match.quantity, change.quantity),
                    expiresOn = listOfNotNull(match.expiresOn, newDate).minOrNull()
                )
                items[items.indexOf(match)] = merged
                return Line(change, Result.MERGED, merged)
            }
            val item = PantryItem(
                name = change.name,
                quantity = change.quantity,
                location = location,
                expiresOn = bestBefore(change.name, location, change.bestBeforeDays)
            )
            items += item
            return Line(change, Result.ADDED, item)
        }

        val lines = changes.filter { it.name.isNotBlank() }.map { change ->
            when (change.action) {
                PantryChange.Action.ADD -> addNew(change)
                PantryChange.Action.SET -> {
                    val match = find(items, change.name, change.location, sameLocationOnly = false)
                    when {
                        match == null -> addNew(change)
                        isZero(change.quantity) -> {
                            items.remove(match)
                            toShop += match
                            Line(change, Result.REMOVED, match)
                        }
                        else -> {
                            val location = change.location ?: match.location
                            val moved = location != match.location
                            val updated = match.copy(
                                quantity = change.quantity.ifBlank { match.quantity },
                                location = location,
                                expiresOn = when {
                                    change.bestBeforeDays != null -> bestBefore(match.name, location, change.bestBeforeDays)
                                    moved -> bestBefore(match.name, location, null)
                                    else -> match.expiresOn
                                }
                            )
                            items[items.indexOf(match)] = updated
                            Line(change, Result.UPDATED, updated)
                        }
                    }
                }
                PantryChange.Action.USED_UP, PantryChange.Action.DISCARD -> {
                    val match = find(items, change.name, change.location, sameLocationOnly = false)
                    if (match == null) {
                        Line(change, Result.NOT_FOUND, null)
                    } else {
                        items.remove(match)
                        if (change.action == PantryChange.Action.USED_UP) toShop += match
                        Line(change, Result.REMOVED, match)
                    }
                }
            }
        }
        return Outcome(items, toShop, lines)
    }

    /** The same food, preferring an exact name and the given place. */
    private fun find(items: List<PantryItem>, name: String, location: PantryLocation?, sameLocationOnly: Boolean): PantryItem? {
        val similar = items.filter { PantryMatching.similar(it.name, name) }
            .filter { !sameLocationOnly || it.location == location }
        val key = PantryMatching.key(name)
        return similar.sortedWith(
            compareBy<PantryItem>({ PantryMatching.key(it.name) != key }, { it.location != location })
        ).firstOrNull()
    }

    private fun isZero(quantity: String) =
        amount.find(quantity.trim())?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() == 0.0

    /** "6" + "12" = "18", "500 g" + "1 kg" = "500 g + 1 kg". */
    internal fun mergeQuantity(a: String, b: String): String {
        if (a.isBlank()) return b.trim()
        if (b.isBlank()) return a.trim()
        val ma = amount.find(a.trim())
        val mb = amount.find(b.trim())
        if (ma != null && mb != null && ma.groupValues[2].trim().equals(mb.groupValues[2].trim(), ignoreCase = true)) {
            val sum = ma.groupValues[1].replace(',', '.').toDouble() + mb.groupValues[1].replace(',', '.').toDouble()
            val number = if (sum % 1.0 == 0.0) sum.toLong().toString() else "%.1f".format(Locale.ROOT, sum)
            return listOf(number, ma.groupValues[2].trim()).filter { it.isNotEmpty() }.joinToString(" ").take(30)
        }
        return "${a.trim()} + ${b.trim()}".take(30)
    }
}

/** Reads the AI's receipt JSON into a purchase the user reviews before saving. */
object ReceiptParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun prompt(): String = """
        Read this grocery store receipt. Expand abbreviations into plain food names in the receipt's language
        (e.g. "PECH POLLO" -> "Pechuga de pollo"). Skip non-food lines, discounts, taxes, bags and totals.
        For each food line give the quantity as printed (count or weight with unit), the line price as a number,
        and where it is usually stored: "fridge", "freezer" or "pantry".
        Respond ONLY with JSON:
        {"store":"","date":"yyyy-MM-dd or null","currency":"ISO code or symbol","total":0.0,
         "items":[{"name":"","quantity":"","price":0.0,"location":"pantry"}]}
    """.trimIndent()

    /** A photo of the open fridge, freezer or cupboard: what can be seen, without prices. */
    fun shelfPrompt(): String = """
        This is a photo of food at home: inside a fridge, freezer or cupboard, or groceries on a counter.
        List every food or drink you can clearly identify, with plain food names in the user's language
        (Spanish if any label is Spanish). Count units you can see ("6", "2 bottles") or leave quantity empty.
        Never guess hidden items. location: "fridge", "freezer" or "pantry" — where this photo seems to be,
        or for groceries on a counter, where each one is usually stored.
        Respond ONLY with JSON:
        {"items":[{"name":"","quantity":"","price":null,"location":"fridge"}]}
    """.trimIndent()

    fun parse(response: String, today: LocalDate = LocalDate.now()): GroceryPurchase {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        require(start >= 0 && end > start) { "No receipt data" }
        val root = json.parseToJsonElement(response.substring(start, end + 1)).jsonObject
        fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        fun JsonObject.number(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
        val items = root["items"]?.jsonArray.orEmpty().mapNotNull { element ->
            val line = element.jsonObject
            val name = line.text("name").take(80)
            if (name.isEmpty()) return@mapNotNull null
            GroceryLine(
                name = name,
                quantity = line.text("quantity").take(30),
                price = line.number("price"),
                location = PantryLocation.parse(line.text("location")) ?: PantryLocation.PANTRY
            )
        }
        val date = root.text("date").takeIf { runCatching { LocalDate.parse(it) }.isSuccess } ?: today.toString()
        return GroceryPurchase(
            date = date,
            store = root.text("store").take(60),
            total = root.number("total") ?: items.mapNotNull { it.price }.takeIf { it.isNotEmpty() }?.sum(),
            currency = root.text("currency").take(8),
            items = items
        )
    }
}
