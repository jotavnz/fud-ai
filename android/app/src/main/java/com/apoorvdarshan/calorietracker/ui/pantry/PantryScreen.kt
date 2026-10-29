package com.apoorvdarshan.calorietracker.ui.pantry

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.apoorvdarshan.calorietracker.services.ExchangeRates
import com.apoorvdarshan.calorietracker.ui.components.UndoBar
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Kitchen
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
import com.apoorvdarshan.calorietracker.data.ShelfLife
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
import com.apoorvdarshan.calorietracker.ui.components.InAppCameraCaptureDialog
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
fun PantryScreen(container: AppContainer, onDismiss: () -> Unit, startOnShopping: Boolean = false) {
    val items by container.pantryRepository.items.collectAsState(initial = emptyList())
    val purchases by container.pantryRepository.purchases.collectAsState(initial = emptyList())
    val shopping by container.pantryRepository.shoppingList.collectAsState(initial = emptyList())
    var newShoppingItem by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<PantryLocation?>(null) }
    var freshFilter by remember { mutableStateOf<Freshness?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var stockingShop by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PantryItem?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // What a photo was read into, and whether it was a receipt (a shop) or a shelf (just stock).
    var review by remember { mutableStateOf<Pair<PantryPhoto, GroceryPurchase>?>(null) }
    var choosingSource by remember { mutableStateOf<PantryPhoto?>(null) }
    var cameraFor by remember { mutableStateOf<PantryPhoto?>(null) }
    var pickFor by remember { mutableStateOf(PantryPhoto.RECEIPT) }
    val uiPrefs = remember { context.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE) }
    var autoSave by remember { mutableStateOf(uiPrefs.getBoolean(KEY_AUTO_SAVE, false)) }
    val cookPrompt = stringResource(R.string.pantry_coach_cook_prompt)
    val shopPrompt = stringResource(R.string.pantry_coach_shop_prompt)
    val tellPrompt = stringResource(R.string.pantry_tell_coach_prompt)

    suspend fun save(kind: PantryPhoto, purchase: GroceryPurchase) {
        if (kind == PantryPhoto.RECEIPT) container.pantryRepository.addPurchase(purchase)
        else container.pantryRepository.stock(purchase.items)
    }

    fun analyze(kind: PantryPhoto, bytes: ByteArray) {
        scope.launch {
            busyMessage = context.getString(kind.readingRes)
            try {
                val upload = withContext(Dispatchers.Default) {
                    FoodImageDecoder.decode(bytes, 2000)?.let { bitmap ->
                        ByteArrayOutputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                            out.toByteArray()
                        }
                    } ?: bytes
                }
                val read = if (kind == PantryPhoto.RECEIPT) container.foodAnalysis.analyzeReceipt(upload)
                else container.foodAnalysis.analyzeShelfPhoto(upload)
                if (autoSave && read.items.isNotEmpty()) {
                    save(kind, read)
                    errorMessage = context.getString(R.string.pantry_auto_saved, read.items.size)
                } else {
                    review = kind to read
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                errorMessage = context.getString(kind.failedRes)
            } finally {
                busyMessage = null
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val kind = pickFor
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) errorMessage = context.getString(kind.failedRes) else analyze(kind, bytes)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true
    }
    var cameraAfterPermission by remember { mutableStateOf<PantryPhoto?>(null) }
    val photoCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraFor = cameraAfterPermission
        cameraAfterPermission = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val shoppingCard: @Composable () -> Unit = {
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
                    onRemove = { id ->
                        scope.launch {
                            val removed = shopping.firstOrNull { it.id == id }
                            container.pantryRepository.removeShopping(id)
                            if (removed != null) container.undo.offer(context.getString(R.string.undo_removed, removed.name)) {
                                container.pantryRepository.restoreShopping(removed)
                            }
                        }
                    },
                    onStock = { stockingShop = true }
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (startOnShopping) R.string.pantry_shopping_shortcut else R.string.pantry_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.pantry_subtitle), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.pantry_close))
                        }
                    }
                }
                // Opened from the shopping shortcut: the list right under the title.
                if (startOnShopping) item(key = "shopping-first") { shoppingCard() }
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
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PantryActionChip(stringResource(R.string.pantry_receipt), Modifier.weight(1f)) {
                                    choosingSource = PantryPhoto.RECEIPT
                                }
                                PantryActionChip(stringResource(R.string.pantry_shelf_photo), Modifier.weight(1f)) {
                                    choosingSource = PantryPhoto.SHELF
                                }
                            }
                            FudGlassTextButton(text = stringResource(R.string.pantry_tell_coach), onClick = {
                                onDismiss()
                                container.navigator.showCoachMessage(tellPrompt)
                            }, modifier = Modifier.fillMaxWidth())
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
                if (items.isNotEmpty()) {
                    item {
                        FreshnessSummary(items, freshFilter) { freshFilter = if (freshFilter == it) null else it }
                    }
                }
                val shown = items.filter { (filter == null || it.location == filter) && (freshFilter == null || Freshness.of(it) == freshFilter) }
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
                // With no filter, one section per place (fridge, freezer, cupboard), soonest first.
                val groups = if (filter == null && freshFilter == null) shown.groupBy { it.location }.toSortedMap()
                else sortedMapOf<PantryLocation, List<PantryItem>>().apply { if (shown.isNotEmpty()) put(filter ?: shown.first().location, shown) }
                groups.forEach { (location, list) ->
                    if (filter == null && freshFilter == null) {
                        item(key = "h-$location") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                Icon(location.icon, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(20.dp))
                                Text(
                                    "  ${stringResource(location.labelRes)} · ${list.size}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    items(list, key = { it.id }) { item ->
                        PantryRow(
                            item = item,
                            onEdit = { editing = item },
                            onUsed = {
                                scope.launch {
                                    container.pantryRepository.usedUp(item)
                                    container.undo.offer(context.getString(R.string.undo_pantry_used_up, item.name)) {
                                        container.pantryRepository.restore(item, fromUsedUp = true)
                                    }
                                }
                            }
                        )
                    }
                }
                if (!startOnShopping) item(key = "shopping") { shoppingCard() }
                item { SpendingCard(purchases, onOpenHistory = { showHistory = true }) }
            }
            busyMessage?.let { message ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = AppColors.Calorie)
                        Text(message, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
            UndoBar(container.undo, bottomPadding = 16.dp)
        }

        if (showHistory) ShoppingHistoryScreen(container) { showHistory = false }
        if (stockingShop) {
            StockShopDialog(
                lastCurrency = purchases.firstOrNull { it.currency.isNotBlank() }?.currency.orEmpty(),
                onDismiss = { stockingShop = false },
                onSave = { store, total, currency ->
                    stockingShop = false
                    scope.launch { container.pantryRepository.stockCheckedShopping(store = store, total = total, currency = currency) }
                }
            )
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
                    scope.launch {
                        val removed = items.firstOrNull { it.id == draft.id }
                        container.pantryRepository.remove(draft.id)
                        if (removed != null) container.undo.offer(context.getString(R.string.undo_removed, removed.name)) {
                            container.pantryRepository.restore(removed)
                        }
                    }
                }
            )
        }
        review?.let { (kind, purchase) ->
            ReceiptReviewDialog(
                purchase = purchase,
                isShelf = kind == PantryPhoto.SHELF,
                onDismiss = { review = null },
                onSave = { reviewed ->
                    review = null
                    scope.launch { save(kind, reviewed) }
                }
            )
        }
        choosingSource?.let { kind ->
            FudGlassDialog(onDismissRequest = { choosingSource = null }) {
                Text(stringResource(kind.titleRes), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                FudGlassPrimaryButton(text = stringResource(R.string.pantry_take_photo), onClick = {
                    choosingSource = null
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraFor = kind
                    } else {
                        cameraAfterPermission = kind
                        photoCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                })
                FudGlassTextButton(text = stringResource(R.string.pantry_pick_photo), onClick = {
                    choosingSource = null
                    pickFor = kind
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.fillMaxWidth())
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        autoSave = !autoSave
                        uiPrefs.edit().putBoolean(KEY_AUTO_SAVE, autoSave).apply()
                    }
                ) {
                    Checkbox(checked = autoSave, onCheckedChange = null)
                    Text(stringResource(R.string.pantry_auto_save), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        cameraFor?.let { kind ->
            InAppCameraCaptureDialog(
                onCapture = { bytes ->
                    cameraFor = null
                    analyze(kind, bytes)
                },
                onDismiss = { cameraFor = null }
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

/** A photo read into pantry items: a receipt records the shop too, a shelf photo only stocks. */
private enum class PantryPhoto(val titleRes: Int, val readingRes: Int, val failedRes: Int) {
    RECEIPT(R.string.pantry_receipt, R.string.pantry_reading_receipt, R.string.pantry_receipt_failed),
    SHELF(R.string.pantry_shelf_photo, R.string.pantry_reading_shelf, R.string.pantry_shelf_failed)
}

private const val UI_PREFS = "pantry_ui"
private const val KEY_AUTO_SAVE = "auto_save_photos"

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
            // Freshness light: red eat today, orange eat soon, green fine, grey no date.
            Box(
                Modifier
                    .size(width = 5.dp, height = 40.dp)
                    .clip(CircleShape)
                    .background(Freshness.of(item).color)
            )
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

/** Traffic light for the pantry (idea from whats-in-the-fridge): eat first what turns red. */
internal enum class Freshness(val color: Color, val labelRes: Int) {
    NOW(Color(0xFFD32F2F), R.string.pantry_fresh_now),
    SOON(Color(0xFFF57C00), R.string.pantry_fresh_soon),
    OK(Color(0xFF2E7D32), R.string.pantry_fresh_ok),
    UNKNOWN(Color(0xFF9E9E9E), R.string.pantry_fresh_unknown);

    companion object {
        fun of(item: PantryItem, today: LocalDate = LocalDate.now()): Freshness {
            val days = item.daysLeft(today) ?: return UNKNOWN
            return when {
                days <= 0 -> NOW
                days <= 3 -> SOON
                else -> OK
            }
        }
    }
}

internal val PantryLocation.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        PantryLocation.FRIDGE -> Icons.Filled.Kitchen
        PantryLocation.FREEZER -> Icons.Filled.AcUnit
        PantryLocation.PANTRY -> Icons.Filled.Inventory2
    }

@Composable
private fun FreshnessSummary(items: List<PantryItem>, selected: Freshness?, onSelect: (Freshness) -> Unit) {
    val counts = items.groupingBy { Freshness.of(it) }.eachCount()
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val first = (counts[Freshness.NOW] ?: 0) + (counts[Freshness.SOON] ?: 0)
            Text(
                if (first > 0) stringResource(R.string.pantry_eat_first, first) else stringResource(R.string.pantry_all_fresh),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Freshness.NOW, Freshness.SOON, Freshness.OK).forEach { level ->
                    val isSelected = selected == level
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(level.color.copy(alpha = if (isSelected) 0.32f else 0.14f))
                            .clickable { onSelect(level) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${counts[level] ?: 0}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = level.color)
                        Text(stringResource(level.labelRes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockShopDialog(lastCurrency: String, onDismiss: () -> Unit, onSave: (String, Double?, String) -> Unit) {
    var store by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(lastCurrency) }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.pantry_stock_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.pantry_stock_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        OutlinedTextField(value = store, onValueChange = { store = it.take(60) }, label = { Text(stringResource(R.string.pantry_stock_store)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = total,
                onValueChange = { total = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                label = { Text(stringResource(R.string.pantry_stock_total)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(2f)
            )
            OutlinedTextField(value = currency, onValueChange = { currency = it.take(8) }, label = { Text(stringResource(R.string.pantry_stock_currency)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        FudGlassDialogActions(
            primaryText = stringResource(R.string.pantry_shopping_stock),
            onPrimary = { onSave(store, total.replace(',', '.').toDoubleOrNull(), currency) },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun SpendingCard(purchases: List<GroceryPurchase>, onOpenHistory: () -> Unit) {
    val since = LocalDate.now().minusDays(30).toString()
    val recent = purchases.filter { it.date >= since }
    val rawTotals = recent.filter { it.total != null }.groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.total ?: 0.0 } }
    val context = LocalContext.current
    val exchange = remember { ExchangeRates(context.applicationContext) }
    var rates by remember { mutableStateOf(exchange.cached()) }
    // Shown as bought, all in bolívares, or all in dollars (Venezuela: BCV or parallel rate).
    var shownIn by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(ExchangeRates.OFFICIAL) }
    val convertible = rawTotals.keys.any { ExchangeRates.isBolivares(it) || ExchangeRates.isDollars(it) }
    LaunchedEffect(convertible) { if (convertible) rates = exchange.rates() }
    val rate = rates.firstOrNull { it.source == source } ?: rates.firstOrNull()
    val totals = if (shownIn.isNotEmpty() && rate != null) ExchangeRates.convert(rawTotals, shownIn, rate.bsPerUsd) else rawTotals
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.pantry_spending_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (convertible && rate != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PantryFilterChip(stringResource(R.string.pantry_spend_as_bought), shownIn.isEmpty()) { shownIn = "" }
                    PantryFilterChip("Bs", shownIn == "Bs") { shownIn = "Bs" }
                    PantryFilterChip("$", shownIn == "$") { shownIn = "$" }
                }
                if (shownIn.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        rates.forEach { r ->
                            PantryFilterChip(
                                if (r.source == ExchangeRates.OFFICIAL) stringResource(R.string.pantry_rate_official) else stringResource(R.string.pantry_rate_parallel),
                                r.source == rate.source
                            ) { source = r.source }
                        }
                    }
                    Text(
                        stringResource(
                            R.string.pantry_rate_used,
                            if (rate.source == ExchangeRates.OFFICIAL) stringResource(R.string.pantry_rate_official) else stringResource(R.string.pantry_rate_parallel),
                            String.format(Locale.getDefault(), "%.2f", rate.bsPerUsd),
                            rate.updated
                        ),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                if (totals.isEmpty()) stringResource(R.string.pantry_spending_none)
                else totals.entries.joinToString("  ·  ") { (currency, sum) -> String.format(Locale.getDefault(), "%s %.2f", currency, sum).trim() },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Calorie
            )
            recent.take(3).forEach { shop ->
                Text(
                    listOfNotNull(shop.date, shop.store.takeIf { it.isNotBlank() }, shop.total?.let { String.format(Locale.getDefault(), "%.2f", it) })
                        .joinToString(" · "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            FudGlassTextButton(text = stringResource(R.string.pantry_history_open, purchases.size), onClick = onOpenHistory)
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
        // Per-food estimate (USDA FoodKeeper); the fixed chips stay for anything else.
        val suggested = remember(name, location) { name.trim().takeIf { it.length >= 3 }?.let { ShelfLife.estimateDays(it, location) } }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggested?.let { days ->
                val date = today.plusDays(days).toString()
                PantryFilterChip(stringResource(R.string.pantry_suggested_days, days.toInt()), expiresOn == date) { expiresOn = date }
            }
            listOf(2L, 5L, 14L, 90L).filter { it != suggested }.forEach { days ->
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
    isShelf: Boolean,
    onDismiss: () -> Unit,
    onSave: (GroceryPurchase) -> Unit
) {
    val lines = remember { mutableStateListOf(*purchase.items.toTypedArray()) }
    val keep = remember { mutableStateListOf(*Array(purchase.items.size) { true }) }
    FudGlassDialog(onDismissRequest = onDismiss) {
        Text(
            stringResource(if (isShelf) R.string.pantry_shelf_review else R.string.pantry_receipt_review),
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
        )
        if (!isShelf) Text(
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
            primaryText = stringResource(if (isShelf) R.string.pantry_shelf_save else R.string.pantry_receipt_save),
            onPrimary = { onSave(purchase.copy(items = lines.filterIndexed { index, _ -> keep[index] })) },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            primaryEnabled = keep.any { it }
        )
    }
}
