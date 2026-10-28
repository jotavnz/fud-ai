package com.apoorvdarshan.calorietracker.data

import com.apoorvdarshan.calorietracker.models.GroceryPurchase
import com.apoorvdarshan.calorietracker.models.PantryItem
import com.apoorvdarshan.calorietracker.models.ShoppingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Local-only store for the food the user has at home (fridge, freezer, pantry) and the shops
 * that filled it. Coach reads both to suggest meals from what is available and the next
 * shopping list; nothing here touches the food diary.
 */
class PantryRepository(private val prefs: PreferencesStore) {
    /** Soonest-to-expire first; undated items last, alphabetically. */
    val items: Flow<List<PantryItem>> = prefs.pantryItems.map { list ->
        list.sortedWith(compareBy<PantryItem>({ it.expiresOn ?: "9999" }, { it.name.lowercase() }))
    }

    val purchases: Flow<List<GroceryPurchase>> = prefs.groceryPurchases.map { list -> list.sortedByDescending { it.date } }

    suspend fun add(item: PantryItem) {
        if (item.name.isBlank()) return
        prefs.updatePantryItems { it + item.copy(name = item.name.trim()) }
    }

    /** Everything Coach heard in one go: added, merged, moved, corrected or removed in one write. */
    suspend fun applyChanges(
        changes: List<com.apoorvdarshan.calorietracker.models.PantryChange>,
        today: LocalDate = LocalDate.now()
    ): com.apoorvdarshan.calorietracker.models.PantryEditor.Outcome {
        var outcome: com.apoorvdarshan.calorietracker.models.PantryEditor.Outcome? = null
        prefs.updatePantryItems { list ->
            com.apoorvdarshan.calorietracker.models.PantryEditor.apply(list, changes, today).also { outcome = it }.items
        }
        val result = checkNotNull(outcome)
        result.toShoppingList.forEach { addToShoppingList(it.name, location = it.location) }
        return result
    }

    suspend fun update(item: PantryItem) {
        prefs.updatePantryItems { list -> list.map { if (it.id == item.id) item else it } }
    }

    suspend fun remove(id: UUID) {
        prefs.updatePantryItems { list -> list.filter { it.id != id } }
    }

    /** Used up: leaves the pantry and goes on the shopping list (unless it is already there). */
    suspend fun usedUp(item: PantryItem) {
        remove(item.id)
        addToShoppingList(item.name, location = item.location)
    }

    val shoppingList: Flow<List<ShoppingItem>> = prefs.shoppingList

    suspend fun addToShoppingList(
        name: String,
        quantity: String = "",
        location: com.apoorvdarshan.calorietracker.models.PantryLocation = com.apoorvdarshan.calorietracker.models.PantryLocation.PANTRY
    ) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        prefs.updateShoppingList { list ->
            if (list.any { !it.checked && it.name.equals(clean, ignoreCase = true) }) list
            else list + ShoppingItem(name = clean, quantity = quantity.trim(), location = location)
        }
    }

    suspend fun toggleShopping(id: UUID) {
        prefs.updateShoppingList { list -> list.map { if (it.id == id) it.copy(checked = !it.checked) else it } }
    }

    suspend fun removeShopping(id: UUID) {
        prefs.updateShoppingList { list -> list.filter { it.id != id } }
    }

    /** Moves everything ticked on the list into the pantry, with a best-before estimated per food ([ShelfLife]). */
    suspend fun stockCheckedShopping(today: LocalDate = LocalDate.now()) {
        val bought = prefs.shoppingList.first().filter { it.checked }
        if (bought.isEmpty()) return
        prefs.updatePantryItems { list ->
            list + bought.map {
                PantryItem(
                    name = it.name,
                    quantity = it.quantity,
                    location = it.location,
                    expiresOn = today.plusDays(ShelfLife.estimateDays(it.name, it.location)).toString()
                )
            }
        }
        prefs.updateShoppingList { list -> list.filter { !it.checked } }
    }

    suspend fun shoppingSnapshot(): List<ShoppingItem> = prefs.shoppingList.first()

    /**
     * Saves a reviewed receipt and stocks its lines, with a best-before estimated per food
     * ([ShelfLife]); a food already in the same place is joined instead of listed twice.
     */
    suspend fun addPurchase(purchase: GroceryPurchase, today: LocalDate = LocalDate.now()) {
        prefs.updateGroceryPurchases { it + purchase }
        stock(purchase.items, today)
    }

    /** Stocks foods seen in a photo (or read off a receipt) without recording a shop. */
    suspend fun stock(lines: List<com.apoorvdarshan.calorietracker.models.GroceryLine>, today: LocalDate = LocalDate.now()) {
        applyChanges(
            lines.map { line ->
                com.apoorvdarshan.calorietracker.models.PantryChange(
                    action = com.apoorvdarshan.calorietracker.models.PantryChange.Action.ADD,
                    name = line.name,
                    quantity = line.quantity,
                    location = line.location
                )
            },
            today
        )
    }

    suspend fun removePurchase(id: UUID) {
        prefs.updateGroceryPurchases { list -> list.filter { it.id != id } }
    }

    suspend fun snapshot(): List<PantryItem> = items.first()

    suspend fun purchaseSnapshot(): List<GroceryPurchase> = purchases.first()
}
