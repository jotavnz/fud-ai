package com.apoorvdarshan.calorietracker.ui.pantry

import com.apoorvdarshan.calorietracker.ui.components.UndoBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.GroceryPurchase
import com.apoorvdarshan.calorietracker.models.ShoppingHistory
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every shop on record (receipts, the shopping list, what was told to Coach) and prices per store. */
@Composable
fun ShoppingHistoryScreen(container: AppContainer, onDismiss: () -> Unit) {
    val purchases by container.pantryRepository.purchases.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPrices by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<java.util.UUID?>(null) }
    val months = remember(purchases) { ShoppingHistory.months(purchases) }
    val prices = remember(purchases) { ShoppingHistory.prices(purchases) }
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.pantry_history_title),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pantry_close)) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryTab(stringResource(R.string.pantry_history_shops, purchases.size), !showPrices) { showPrices = false }
                        HistoryTab(stringResource(R.string.pantry_history_prices, prices.size), showPrices) { showPrices = true }
                    }
                }
                if (!showPrices) {
                    if (purchases.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.pantry_history_empty)) }
                    }
                    months.forEach { month ->
                        item(key = "m-${month.key}") {
                            val title = runCatching { YearMonth.parse(month.key).format(monthFormat) }.getOrDefault(month.key)
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text(formatTotals(month.totals), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Calorie)
                            }
                        }
                        items(month.purchases, key = { it.id }) { purchase ->
                            PurchaseCard(
                                purchase = purchase,
                                expanded = expanded == purchase.id,
                                onToggle = { expanded = if (expanded == purchase.id) null else purchase.id },
                                onDelete = {
                                    scope.launch {
                                        container.pantryRepository.removePurchase(purchase.id)
                                        container.undo.offer(context.getString(R.string.undo_removed, purchase.store.ifBlank { context.getString(R.string.undo_purchase) })) {
                                            container.pantryRepository.restorePurchase(purchase)
                                        }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    if (prices.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.pantry_prices_empty)) }
                    }
                    items(prices, key = { it.name }) { product ->
                        FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, padding = 12.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    stringResource(R.string.pantry_price_latest, money(product.latest.price, product.latest.currency), storeLabel(product.latest.store), product.latest.date),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                if (product.byStore.size > 1) {
                                    Text(
                                        stringResource(R.string.pantry_price_cheapest, storeLabel(product.cheapest.store), money(product.cheapest.price, product.cheapest.currency)),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            UndoBar(container.undo, bottomPadding = 16.dp)
        }
    }
}

@Composable
private fun storeLabel(store: String): String = store.ifBlank { stringResource(R.string.pantry_store_unknown) }

private fun money(value: Double, currency: String): String =
    String.format(Locale.getDefault(), "%s %.2f", currency, value).trim()

private fun formatTotals(totals: Map<String, Double>): String =
    totals.entries.joinToString("  ·  ") { (currency, sum) -> money(sum, currency) }

@Composable
private fun HistoryTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp))
}

@Composable
private fun PurchaseCard(purchase: GroceryPurchase, expanded: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val sourceLabel = stringResource(
        when (purchase.source) {
            GroceryPurchase.SOURCE_LIST -> R.string.pantry_source_list
            GroceryPurchase.SOURCE_COACH -> R.string.pantry_source_coach
            else -> R.string.pantry_source_receipt
        }
    )
    FudGlassSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), cornerRadius = 16.dp, padding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${purchase.date} · ${storeLabel(purchase.store)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.pantry_purchase_summary, purchase.items.size, sourceLabel),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                purchase.total?.let {
                    Text(money(it, purchase.currency), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Calorie)
                }
            }
            if (expanded) {
                purchase.items.forEach { line ->
                    Row {
                        Text(
                            listOfNotNull(line.name, line.quantity.takeIf { it.isNotBlank() }).joinToString(" · "),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f)
                        )
                        line.price?.let { Text(money(it, purchase.currency), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }
}
