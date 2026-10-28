package com.apoorvdarshan.calorietracker.ui.pantry

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.GroceryPurchase
import com.apoorvdarshan.calorietracker.models.PantryItem
import com.apoorvdarshan.calorietracker.models.PantryLocation
import com.apoorvdarshan.calorietracker.services.FoodImageDecoder
import com.apoorvdarshan.calorietracker.services.OpenFoodFactsService
import com.apoorvdarshan.calorietracker.ui.components.FudGlassDialog
import com.apoorvdarshan.calorietracker.ui.components.FudGlassDialogActions
import com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.FudGlassTextButton
import com.apoorvdarshan.calorietracker.ui.home.BarcodeScannerSheet
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * Pantry: what the user has in the fridge, freezer and cupboard, stocked by hand, by barcode or
 * from a grocery receipt photo. Coach reads it (get_pantry) to suggest meals and shopping lists.
 */
@Composable
fun PantryScreen(container: AppContainer, onDismiss: () -> Unit) {
    val items by container.pantryRepository.items.collectAsState(initial = emptyList())
    val purchases by container.pantryRepository.purchases.collectAsState(initial = emptyList())
    val shopping by container.pantryRepository.shoppingList.collectAsState(initial = emptyList())
    var newShoppingItem by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<PantryLocation?>(null) }
    var editing by remember { mutableStateOf<PantryItem?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<GroceryPurchase?>(null) }
    val readingReceipt = stringResource(R.string.pantry_reading_receipt)
    val receiptFailed = stringResource(R.string.pantry_receipt_failed)
    val cookPrompt = stringResource(R.string.pantry_coach_cook_prompt)
    val shopPrompt = stringResource(R.string.pantry_coach_shop_prompt)

    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = readingReceipt
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: error("unreadable")
                val upload = withContext(Dispatchers.Default) {
                    FoodImageDecoder.decode(bytes, 2000)?.let { bitmap ->
                        ByteArrayOutputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                            out.toByteArray()
                        }
                    } ?: bytes
                }
                receipt = container.foodAnalysis.analyzeReceipt(upload)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                errorMessage = receiptFailed
            } finally {
                busyMessage = null
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.pantry_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.pantry_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pantry_close))
                        }
                    }
                }
                item {
                    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PantryActionChip(stringResource(R.string.pantry_add), Modifier.weight(1f)) {
                                    editing = PantryItem(name = "", location = filter ?: PantryLocation.FRIDGE)
                                }
                                PantryActionChip(stringResource(R.string.pantry_scan), Modifier.weight(1f)) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        scanning = true
                                    } else {
                                        cameraPermission.launch(Manifest.permission.CAMERA)
                                    }
                                }
                                PantryActionChip(stringResource(R.string.pantry_receipt), Modifier.weight(1f)) {
                                    receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                            FudGlassPrimaryButton(text = stringResource(R.string.pantry_coach_cook), onClick = {
                                onDismiss()
                                container.navigator.showCoachMessage(cookPrompt)
                            })
                            FudGlassTextButton(text = stringResource(R.string.pantry_coach_shop), onClick = {
                                onDismiss()
                                container.navigator.showCoachMessage(shopPrompt)
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PantryFilterChip(stringResource(R.string.pantry_all, items.size), filter == null) { filter = null }
                        PantryLocation.entries.forEach { location ->
                            val count = items.count { it.location == location }
                            PantryFilterChip("${stringResource(location.labelRes)} · $count", filter == location) { filter = location }
                        }
                    }
                }
                val shown = items.filter { filter == null || it.location == filter }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.pantry_empty),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
                items(shown, key = { it.id }) { item ->
                    PantryRow(
                        item = item,
                        onEdit = { editing = item },
                        onUsed = { scope.launch { container.pantryRepository.usedUp(item) } }
                    )
                }
                item {
                    ShoppingListCard(
                        items = shopping,
                        newItem = newShoppingItem,
                        onNewItem = { newShoppingItem = it.take(60) },
                        onAdd = {
                            val name = newShoppingItem
                            newShoppingItem = ""
                            scope.launch { container.pantryRepository.addToShoppingList(name) }
                        },
                        onToggle = { id -> scope.launch { container.pantryRepository.toggleShopping(id) } },
                        onRemove = { id -> scope.launch { container.pantryRepository.removeShopping(id) } },
                        onStock = { scope.launch { container.pantryRepository.stockCheckedShopping() } }
                    )
                }
                if (purchases.isNotEmpty()) {
                    item { SpendingCard(purchases) }
                }
            }
            busyMessage?.let { message ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = AppColors.Calorie)
                        Text(message, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }

        editing?.let { draft ->
            PantryItemDialog(
                initial = draft,
                isNew = items.none { it.id == draft.id },
                onDismiss = { editing = null },
                onSave = { saved ->
                    editing = null
                    scope.launch {
                        if (items.any { it.id == saved.id }) container.pantryRepository.update(saved)
                        else container.pantryRepository.add(saved)
                    }
                },
                onDelete = {
                    editing = null
                    scope.launch { container.pantryRepository.remove(draft.id) }
                }
            )
        }
        receipt?.let { purchase ->
            ReceiptReviewDialog(
                purchase = purchase,
                onDismiss = { receipt = null },
                onSave = { reviewed ->
                    receipt = null
                    scope.launch { container.pantryRepository.addPurchase(reviewed) }
                }
            )
        }
        errorMessage?.let { message ->
            FudGlassDialog(onDismissRequest = { errorMessage = null }) {
                Text(message, color = MaterialTheme.colorScheme.onSurface)
                FudGlassDialogActions(primaryText = stringResource(R.string.action_ok), onPrimary = { errorMessage = null })
            }
        }
    }

    if (scanning) {
        BarcodeScannerSheet(
            onBarcode = { code ->
                scanning = false
                scope.launch {
                    busyMessage = context.getString(R.string.pantry_looking_up)
                    val name = runCatching { OpenFoodFactsService.lookupWithImage(code).analysis.name }.getOrNull()
                    busyMessage = null
                    editing = PantryItem(name = name.orEmpty(), barcode = code, location = filter ?: PantryLocation.PANTRY)
                }
            },
            onDismiss = { scanning = false }
        )
    }
}

internal val PantryLocation.labelRes: Int
    get() = when (this) {
        PantryLocation.FRIDGE -> R.string.pantry_fridge
        PantryLocation.FREEZER -> R.string.pantry_freezer
        PantryLocation.PANTRY -> R.string.pantry_pantry
    }

@Composable
private fun PantryActionChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Calorie.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AppColors.Calorie, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PantryFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun PantryRow(item: PantryItem, onEdit: () -> Unit, onUsed: () -> Unit) {
    val daysLeft = item.daysLeft()
    val (badgeText, badgeColor) = when {
        daysLeft == null -> null to Color.Unspecified
        daysLeft < 0 -> stringResource(R.string.pantry_expired) to Color(0xFFD32F2F)
        daysLeft == 0L -> stringResource(R.string.pantry_today) to Color(0xFFD32F2F)
        daysLeft <= 3 -> stringResource(R.string.pantry_days_left, daysLeft.toInt()) to Color(0xFFF57C00)
        else -> stringResource(R.string.pantry_days_left, daysLeft.toInt()) to Color(0xFF2E7D32)
    }
    FudGlassSurface(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit), cornerRadius = 16.dp, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(item.quantity.takeIf { it.isNotBlank() }, stringResource(item.location.labelRes))
                Text(details.joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (badgeText != null) {
                Text(
                    badgeText,
                    color = badgeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(badgeColor.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            IconButton(onClick = onUsed) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.pantry_used), tint = AppColors.Calorie)
            }
        }
    }
}

@Composable
private fun ShoppingListCard(
    items: List<com.apoorvdarshan.calorietracker.models.ShoppingItem>,
    newItem: String,
    onNewItem: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (java.util.UUID) -> Unit,
    onRemove: (java.util.UUID) -> Unit,
    onStock: () -> Unit
) {
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.pantry_shopping_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.pantry_shopping_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = onNewItem,
                    placeholder = { Text(stringResource(R.string.pantry_shopping_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FudGlassTextButton(text = stringResource(R.string.pantry_add), onClick = onAdd, enabled = newItem.isNotBlank())
            }
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.id) })
                    Text(
                        if (item.quantity.isBlank()) item.name else "${item.name} · ${item.quantity}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.checked) 0.45f else 1f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemove(item.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (items.any { it.checked }) {
                FudGlassTextButton(text = stringResource(R.string.pantry_shopping_stock), onClick = onStock)
            }
        }
    }
}

@Composable
private fun SpendingCard(purchases: List<GroceryPurchase>) {
    val since = LocalDate.now().minusDays(30).toString()
    val recent = purchases.filter { it.date >= since }
    val totals = recent.filter { it.total != null }.groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.total ?: 0.0 } }
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.pantry_spending_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (totals.isEmpty()) stringResource(R.string.pantry_spending_none)
                else totals.entries.joinToString("  ·  ") { (currency, sum) -> String.format(Locale.getDefault(), "%s %.2f", currency, sum).trim() },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Calorie
            )
            recent.take(5).forEach { shop ->
                Text(
                    listOfNotNull(shop.date, shop.store.takeIf { it.isNotBlank() }, shop.total?.let { String.format(Locale.getDefault(), "%.2f", it) })
                        .joinToString(" · "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun PantryItemDialog(
    initial: PantryItem,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var quantity by remember { mutableStateOf(initial.quantity) }
    var location by remember { mutableStateOf(initial.location) }
    var expiresOn by remember { mutableStateOf(initial.expiresOn) }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(
            stringResource(if (isNew) R.string.pantry_add_title else R.string.pantry_edit_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text(stringResource(R.string.pantry_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = quantity, onValueChange = { quantity = it.take(30) }, label = { Text(stringResource(R.string.pantry_quantity)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.pantry_where), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PantryLocation.entries.forEach { option ->
                PantryFilterChip(stringResource(option.labelRes), option == location) { location = option }
            }
        }
        Text(stringResource(R.string.pantry_best_before), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        val today = LocalDate.now()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(2L, 5L, 14L, 90L).forEach { days ->
                val date = today.plusDays(days).toString()
                PantryFilterChip(stringResource(R.string.pantry_in_days, days.toInt()), expiresOn == date) { expiresOn = date }
            }
            PantryFilterChip(stringResource(R.string.pantry_no_date), expiresOn == null) { expiresOn = null }
        }
        expiresOn?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) FudGlassTextButton(text = stringResource(R.string.action_delete), onClick = onDelete, color = Color(0xFFD32F2F))
            Spacer(Modifier.weight(1f))
            FudGlassTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
            FudGlassTextButton(
                text = stringResource(R.string.action_save),
                enabled = name.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim(), quantity = quantity.trim(), location = location, expiresOn = expiresOn)) }
            )
        }
    }
}

@Composable
private fun ReceiptReviewDialog(
    purchase: GroceryPurchase,
    onDismiss: () -> Unit,
    onSave: (GroceryPurchase) -> Unit
) {
    val lines = remember { mutableStateListOf(*purchase.items.toTypedArray()) }
    val keep = remember { mutableStateListOf(*Array(purchase.items.size) { true }) }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.pantry_receipt_review), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            listOfNotNull(purchase.store.takeIf { it.isNotBlank() }, purchase.date, purchase.total?.let { "${purchase.currency} ${String.format(Locale.getDefault(), "%.2f", it)}".trim() })
                .joinToString(" · "),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (lines.isEmpty()) {
            Text(stringResource(R.string.pantry_receipt_empty), color = MaterialTheme.colorScheme.onSurface)
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(lines.indices.toList()) { index ->
                val line = lines[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep[index], onCheckedChange = { keep[index] = it })
                    Column(Modifier.weight(1f)) {
                        Text(line.name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(line.quantity.takeIf { it.isNotBlank() }, line.price?.let { String.format(Locale.getDefault(), "%.2f", it) }).joinToString(" · "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    // Tap cycles fridge → freezer → pantry.
                    Text(
                        stringResource(line.location.labelRes),
                        color = AppColors.Calorie,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(92.dp)
                            .clip(CircleShape)
                            .background(AppColors.Calorie.copy(alpha = 0.12f))
                            .clickable {
                                val next = PantryLocation.entries[(line.location.ordinal + 1) % PantryLocation.entries.size]
                                lines[index] = line.copy(location = next)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.pantry_receipt_save),
            onPrimary = { onSave(purchase.copy(items = lines.filterIndexed { index, _ -> keep[index] })) },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            primaryEnabled = keep.any { it }
        )
    }
}
