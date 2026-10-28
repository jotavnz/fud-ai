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
