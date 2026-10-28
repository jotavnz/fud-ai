package com.apoorvdarshan.calorietracker.data

import android.content.Context
import android.util.Log
import com.apoorvdarshan.calorietracker.models.PantryLocation
import com.apoorvdarshan.calorietracker.models.PantryMatching
import com.google.gson.JsonParser

/**
 * How long a food keeps in the fridge, freezer or pantry, from USDA FoodKeeper (asset
 * `shelf_life.json`, scripts/build_shelf_life.mjs). Names are matched in Spanish or English
 * ("pechuga de pollo", "chicken breast"); unknown foods, or a storage FoodKeeper gives no
 * time for, fall back to [PantryLocation.defaultShelfLifeDays]. Estimates are orientative.
 */
object ShelfLife {
    private const val TAG = "ShelfLife"
    private const val ASSET = "shelf_life.json"

    /** A FoodKeeper entry: words before the first comma must all appear in the item name. */
    internal class Food(val name: String, val headWords: Set<String>, val detailWords: Set<String>, val days: List<Int?>)

    internal class Table(val foods: List<Food>, val spanish: Map<String, List<String>>)

    @Volatile
    private var table = Table(emptyList(), emptyMap())

    /** Loads the table; call once at startup. */
    fun load(context: Context) {
        table = try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        } catch (t: Throwable) {
            Log.e(TAG, "failed to load $ASSET", t)
            Table(emptyList(), emptyMap())
        }
    }

    internal fun parse(json: String): Table {
        val root = JsonParser.parseString(json).asJsonObject
        val foods = root.getAsJsonArray("foods").map { element ->
            val entry = element.asJsonArray
            val name = entry[0].asString
            Food(
                name = name,
                headWords = words(name.substringBefore(',')),
                detailWords = words(name.substringAfter(',', "")),
                days = (1..3).map { if (entry[it].isJsonNull) null else entry[it].asInt }
            )
        }
        val spanish = root.getAsJsonObject("es").entrySet().associate { (word, english) ->
            PantryMatching.key(word) to words(english.asString).toList()
        }
        return Table(foods, spanish)
    }

    /** Best-before estimate in days for [name] stored in [location]. */
    fun estimateDays(name: String, location: PantryLocation): Long =
        days(name, location, table) ?: location.defaultShelfLifeDays

    /**
     * FoodKeeper days, or null when the food is unknown or FoodKeeper has no time for that
     * storage. Among equally good matches ("queso" fits every cheese) the shortest time wins,
     * unless the most general entry has none (milk: "use the package date").
     */
    internal fun days(name: String, location: PantryLocation, table: Table): Long? {
        val query = PantryMatching.key(name).split(' ').filter { it.isNotEmpty() }
            .flatMap { word ->
                table.spanish[word] ?: table.spanish[word.removeSuffix("s")]
                    ?: table.spanish[word.removeSuffix("es")] ?: listOf(stem(word))
            }
            .toSet()
        if (query.isEmpty()) return null
        val scored = table.foods.mapNotNull { food ->
            if (!query.containsAll(food.headWords)) return@mapNotNull null
            food to food.headWords.size * 10 + food.detailWords.count { it in query }
        }
        val best = scored.maxOfOrNull { it.second } ?: return null
        val ties = scored.filter { it.second == best }.map { it.first }
            .let { all -> all.filter { food -> food.detailWords.none { it in preparedWords && it !in query } }.ifEmpty { all } }
        val column = when (location) {
            PantryLocation.FRIDGE -> 0
            PantryLocation.FREEZER -> 1
            PantryLocation.PANTRY -> 2
        }
        if (ties.first().days[column] == null) return null
        return ties.mapNotNull { it.days[column] }.min().toLong()
    }

    /** Entries for a prepared form ("Eggs, hard boiled") count only when the name says so. */
    private val preparedWords = setOf(
        "cooked", "boiled", "raw", "yolk", "opened", "leftover", "homemade", "thawed", "prepared", "dish"
    )

    private val ignored = setOf("and", "or", "the", "of", "in", "with", "such", "as", "etc", "")

    private fun words(text: String): Set<String> =
        PantryMatching.key(text).split(' ').filter { it !in ignored }.map(::stem).toSet()

    /** Plural to singular, so "tomatoes", "berries" and "eggs" match "tomato", "berry", "egg". */
    private fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("oes") -> word.dropLast(2)
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }
}
