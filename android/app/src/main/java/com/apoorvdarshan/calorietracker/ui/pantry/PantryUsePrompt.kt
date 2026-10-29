package com.apoorvdarshan.calorietracker.ui.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.PantryStock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * After a meal is logged, if it seems to use food from the pantry: "Did you cook it at home?"
 * Yes takes the amounts off the stock (with Undo); takeaway or eaten out leaves the pantry alone.
 */
@Composable
fun PantryUsePromptHost(container: AppContainer) {
    var seen by remember { mutableStateOf<Set<UUID>?>(null) }
    var pending by remember { mutableStateOf<Pair<FoodEntry, List<PantryStock.Use>>?>(null) }
    LaunchedEffect(Unit) {
        container.foodRepository.entries.collect { entries ->
            val ids = entries.mapTo(mutableSetOf()) { it.id }
            val before = seen
            seen = (before ?: emptySet()) + ids
            if (before == null) return@collect
            // One new meal logged by the user (imports and Health Connect restores add many, or carry an origin).
            val added = entries.filter { it.id !in before && it.healthConnectOrigin == null }
            val entry = added.singleOrNull() ?: return@collect
            val pantry = container.pantryRepository.items.first()
            val uses = PantryStock.suggestUses(entry, pantry)
            if (uses.isNotEmpty()) pending = entry to uses
        }
    }
    val (entry, uses) = pending ?: return
    PantryUseDialog(container, entry, uses, onDone = { pending = null })
}

@Composable
private fun PantryUseDialog(container: AppContainer, entry: FoodEntry, uses: List<PantryStock.Use>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Per row: checked, amount text.
    val checked = remember(entry.id) { mutableStateListOf(*Array(uses.size) { true }) }
    val amounts = remember(entry.id) { mutableStateListOf(*uses.map { PantryStock.format(it.amount, it.item.quantity) }.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.pantry_use_title, entry.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pantry_use_body), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                uses.forEachIndexed { index, use ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                        Column(Modifier.weight(1f)) {
                            Text(use.item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                stringResource(R.string.pantry_use_have, use.item.quantity.ifBlank { "?" }),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        OutlinedTextField(
                            value = amounts[index],
                            onValueChange = { amounts[index] = it.take(12) },
                            singleLine = true,
                            enabled = checked[index],
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.width(96.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDone()
                scope.launch {
                    val before = container.pantryRepository.items.first()
                    val chosen = uses.indices.filter { checked[it] }.map { i ->
                        val typed = PantryStock.parse(amounts[i])
                        // A plain number means the stock's own unit (grams, ml or units).
                        uses[i].item to (
                            typed?.takeIf { it.unit == PantryStock.Unit.COUNT || it.unit == uses[i].amount.unit }
                                ?.let { PantryStock.Amount(it.value, uses[i].amount.unit) }
                                ?: uses[i].amount
                            )
                    }
                    chosen.forEach { (item, amount) ->
                        val left = PantryStock.deduct(item, amount)
                        if (left == null) container.pantryRepository.usedUp(item) else container.pantryRepository.update(left)
                    }
                    if (chosen.isNotEmpty()) {
                        container.undo.offer(context.getString(R.string.pantry_use_done, chosen.size)) {
                            chosen.forEach { (item, _) ->
                                val original = before.firstOrNull { it.id == item.id } ?: return@forEach
                                container.pantryRepository.restore(original)
                                container.pantryRepository.update(original)
                            }
                        }
                    }
                }
            }) { Text(stringResource(R.string.pantry_use_yes), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.pantry_use_no)) }
        }
    )
}
