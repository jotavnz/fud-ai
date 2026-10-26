package com.apoorvdarshan.calorietracker.models

import android.content.Context
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.services.ai.FoodAnalysis
import java.util.UUID

/** Map a diary food to one ingredient line (nested ingredients stay collapsed). */
fun FoodEntry.toMealIngredient(): MealIngredient = MealIngredient(
    name = name,
    grams = servingSizeGrams?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    imageFilename = allImageFilenames.firstOrNull(),
    additionalImageFilenames = allImageFilenames.drop(1),
    emoji = emoji
)

/**
 * Map an analysis result to one ingredient line (nested ingredients stay collapsed). An unknown
 * weight stays 0 g, like [FoodEntry.toMealIngredient]: such an analysis carries a serving count
 * (a saved label restored without its mass) or a placeholder in [FoodAnalysis.servingSizeGrams].
 */
fun FoodAnalysis.toMealIngredient(): MealIngredient = MealIngredient(
    name = name,
    grams = servingSizeGrams.takeIf { servingSizeIsKnown && it.isFinite() && it > 0.0 } ?: 0.0,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fat = fat,
    emoji = emoji
)

/** Recompute parent macros from an ingredient list. */
fun FoodEntry.withIngredients(ingredients: List<MealIngredient>): FoodEntry {
    val totals = ingredients.totals()
    return copy(
        calories = totals.calories,
        protein = totals.protein,
        carbs = totals.carbs,
        fat = totals.fat,
        servingSizeGrams = totals.grams.takeIf { it > 0 } ?: servingSizeGrams,
        servingUnitOptions = emptyList(),
        selectedServingUnit = null,
        selectedServingQuantity = null,
        ingredients = ingredients
    )
}

/**
 * Words used when naming a combined entry. The name is stored with the entry, so it is built in
 * the language active when the meals are combined; [English] is the default for callers without
 * a Context, and [from] resolves the current app language.
 */
class CombinedMealLabels(
    val combinedMeal: String,
    val more: (Int) -> String
) {
    companion object {
        val English = CombinedMealLabels("Combined meal") { "$it more" }

        fun from(context: Context): CombinedMealLabels = CombinedMealLabels(
            combinedMeal = context.getString(R.string.hm_combined_meal),
            more = { context.getString(R.string.hm_combined_more_format, it) }
        )
    }
}

object CombinedMeal {
    fun combinedName(entries: List<FoodEntry>, labels: CombinedMealLabels = CombinedMealLabels.English): String {
        val names = entries.map { it.name.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return labels.combinedMeal
        if (names.size <= 3) return names.joinToString(" + ")
        return names.take(2).joinToString(" + ") + " + " + labels.more(names.size - 2)
    }

    fun combineFoodEntries(
        entries: List<FoodEntry>,
        labels: CombinedMealLabels = CombinedMealLabels.English
    ): FoodEntry {
        require(entries.size >= 2) { "Combine requires at least two food entries" }
        val ingredients = entries.map { it.toMealIngredient() }
        val totals = ingredients.totals()
        val latest = entries.maxByOrNull { it.timestamp } ?: entries.first()
        val filenames = entries.flatMap { it.allImageFilenames }.distinct()
        return FoodEntry(
            id = UUID.randomUUID(),
            name = combinedName(entries, labels),
            calories = totals.calories,
            protein = totals.protein,
            carbs = totals.carbs,
            fat = totals.fat,
            timestamp = latest.timestamp,
            imageFilename = filenames.firstOrNull(),
            additionalImageFilenames = filenames.drop(1),
            emoji = entries.firstNotNullOfOrNull { it.emoji },
            source = FoodSource.MANUAL,
            healthConnectOrigin = entries.firstNotNullOfOrNull { it.healthConnectOrigin },
            mealType = latest.mealType,
            servingSizeGrams = totals.grams.takeIf { it > 0 },
            ingredients = ingredients
        )
    }
}

fun combineFoodEntries(
    entries: List<FoodEntry>,
    labels: CombinedMealLabels = CombinedMealLabels.English
): FoodEntry = CombinedMeal.combineFoodEntries(entries, labels)
