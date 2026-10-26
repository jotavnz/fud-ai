package com.apoorvdarshan.calorietracker.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.HEALTH_BODY_BACKFILL_DAYS
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.importedWorkoutTypeLabel
import com.apoorvdarshan.calorietracker.services.health.HealthActivitySnapshot
import com.apoorvdarshan.calorietracker.services.health.HealthConnectAvailability
import com.apoorvdarshan.calorietracker.services.health.HealthImportRunResult
import com.apoorvdarshan.calorietracker.services.health.HealthImportRunner
import com.apoorvdarshan.calorietracker.services.health.HealthImportStep
import com.apoorvdarshan.calorietracker.services.health.HealthSeenRecord
import com.apoorvdarshan.calorietracker.services.health.HealthSyncInspector
import com.apoorvdarshan.calorietracker.services.health.HealthSyncStatusStore
import com.apoorvdarshan.calorietracker.services.health.HealthSyncType
import com.apoorvdarshan.calorietracker.services.health.HealthTypeSnapshot
import com.apoorvdarshan.calorietracker.services.health.healthSourceName
import com.apoorvdarshan.calorietracker.services.health.importedEntryId
import com.apoorvdarshan.calorietracker.services.health.isOwn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The single Settings > Health & Data entry for the Health Connect status screen. Owns its
 * own open state so SettingsScreen only needs one line to host it.
 */
@Composable
internal fun HealthSyncStatusEntry(container: AppContainer) {
    var open by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider()
    SettingRow(
        stringResource(R.string.hs_entry_title),
        stringResource(R.string.hs_entry_value),
        icon = Icons.Outlined.Sync
    ) { open = true }
    if (open) HealthSyncStatusScreen(container) { open = false }
}

// -- State ---------------------------------------------------------------------------------

private data class TypeStatus(
    val type: HealthSyncType,
    /** Entries of this type the app holds in total (manual + imported). */
    val inAppTotal: Int,
    /** Entries the app holds that came from other apps through Health Connect. */
    val imported: Int,
    val importedOldest: Instant?,
    val importedNewest: Instant?,
    /** Source package to count for the imported entries. */
    val importedSources: List<Pair<String, Int>>,
    /** Other apps' records Health Connect holds now; null when not readable. */
    val healthConnect: HealthTypeSnapshot?,
    val readGranted: Boolean,
    val lastSync: Instant?
)

private data class RecentItem(
    val time: Instant,
    val type: HealthSyncType,
    val title: String,
    val sourcePackage: String?
)

private data class PermissionRow(val labelRes: Int, val read: String?, val write: String?)

private data class StatusState(
    val availability: HealthConnectAvailability,
    val syncEnabled: Boolean,
    /** Null when the permission probe failed. */
    val granted: Set<String>?,
    val historySupported: Boolean,
    val types: List<TypeStatus>,
    val activity: HealthActivitySnapshot?,
    val recent: List<RecentItem>,
    val lastAutoSync: Instant?,
    val lastHistoryImport: Instant?,
    val lastRun: HealthImportRunResult?
)

private fun permissionRows(i: HealthSyncInspector): List<PermissionRow> = listOf(
    PermissionRow(R.string.hs_perm_weight, i.weightRead, i.weightWrite),
    PermissionRow(R.string.hs_perm_body_fat, i.bodyFatRead, i.bodyFatWrite),
    PermissionRow(R.string.hs_perm_exercise, i.exerciseRead, null),
    PermissionRow(R.string.hs_perm_nutrition, i.nutritionRead, i.nutritionWrite),
    PermissionRow(R.string.hs_perm_active, i.activeRead, i.activeWrite),
    PermissionRow(R.string.hs_perm_total, i.totalRead, null),
    PermissionRow(R.string.hs_perm_steps, i.stepsRead, null)
)

/** What the "grant missing" button asks for: the regular connect set plus optional Exercise. */
private fun requestablePermissions(container: AppContainer): Set<String> =
    container.health.connectPermissions + container.health.exerciseImportPermissions

private suspend fun loadStatus(
    context: Context,
    container: AppContainer,
    inspector: HealthSyncInspector,
    store: HealthSyncStatusStore,
    weightUnitLbs: Boolean
): StatusState {
    val availability = container.health.availability()
    val usable = availability == HealthConnectAvailability.AVAILABLE
    val granted = if (usable) inspector.grantedPermissions() else emptySet()
    val g = granted.orEmpty()
    val own = context.packageName
    val now = Instant.now()
    val from = now.minus(Duration.ofDays(HEALTH_BODY_BACKFILL_DAYS))
    val recent = mutableListOf<RecentItem>()
    val numberFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }

    // Weight ------------------------------------------------------------------------------
    val weights = container.weightRepository.entries.first()
    val weightHc = if (usable && inspector.weightRead in g) inspector.readWeights(from, now)?.filterNot { it.isOwn(own) } else null
    val weightStatus = run {
        val byId = weights.associateBy { it.id }
        val matched = if (weightHc != null) {
            weightHc.mapNotNull { r -> byId[importedEntryId(r, "hc-weight")]?.let { r.originPackage to it } }.distinctBy { it.second.id }
        } else {
            // Without a Health Connect read, deterministic (name-based, v3) ids mark imports.
            weights.filter { it.id.version() == 3 }.map { null to it }
        }
        matched.forEach { (pkg, entry) ->
            val value = if (weightUnitLbs) "${numberFormat.format(entry.weightLbs)} lb" else "${numberFormat.format(entry.weightKg)} kg"
            recent += RecentItem(entry.date, HealthSyncType.WEIGHT, context.getString(R.string.hs_item_weight, value), pkg)
        }
        TypeStatus(
            type = HealthSyncType.WEIGHT,
            inAppTotal = weights.size,
            imported = matched.size,
            importedOldest = matched.minOfOrNull { it.second.date },
            importedNewest = matched.maxOfOrNull { it.second.date },
            importedSources = sourcesOf(matched.mapNotNull { it.first }),
            healthConnect = weightHc?.let(HealthTypeSnapshot::of),
            readGranted = inspector.weightRead in g,
            lastSync = store.lastSync(HealthSyncType.WEIGHT)
        )
    }

    // Body fat ----------------------------------------------------------------------------
    val bodyFats = container.bodyFatRepository.entries.first()
    val bodyFatHc = if (usable && inspector.bodyFatRead in g) inspector.readBodyFats(from, now)?.filterNot { it.isOwn(own) } else null
    val bodyFatStatus = run {
        val byId = bodyFats.associateBy { it.id }
        val matched = if (bodyFatHc != null) {
            bodyFatHc.mapNotNull { r -> byId[importedEntryId(r, "hc-bodyfat")]?.let { r.originPackage to it } }.distinctBy { it.second.id }
        } else {
            bodyFats.filter { it.id.version() == 3 }.map { null to it }
        }
        matched.forEach { (pkg, entry) ->
            recent += RecentItem(
                entry.date, HealthSyncType.BODY_FAT,
                context.getString(R.string.hs_item_body_fat, numberFormat.format(entry.bodyFatFraction * 100) + "%"), pkg
            )
        }
        TypeStatus(
            type = HealthSyncType.BODY_FAT,
            inAppTotal = bodyFats.size,
            imported = matched.size,
            importedOldest = matched.minOfOrNull { it.second.date },
            importedNewest = matched.maxOfOrNull { it.second.date },
            importedSources = sourcesOf(matched.mapNotNull { it.first }),
            healthConnect = bodyFatHc?.let(HealthTypeSnapshot::of),
            readGranted = inspector.bodyFatRead in g,
            lastSync = store.lastSync(HealthSyncType.BODY_FAT)
        )
    }

    // Workouts ----------------------------------------------------------------------------
    val workouts = container.importedHealthWorkoutRepository.workouts.first()
    val exerciseHc = if (usable && inspector.exerciseRead in g) inspector.readExercise(from, now)?.filterNot { it.isOwn(own) } else null
    workouts.forEach { w ->
        val title = context.getString(
            R.string.hs_item_workout, context.getString(importedWorkoutTypeLabel(w.exerciseType)), w.durationMinutes
        )
        recent += RecentItem(w.startedAt, HealthSyncType.WORKOUTS, title, w.sourcePackage)
    }
    val workoutStatus = TypeStatus(
        type = HealthSyncType.WORKOUTS,
        inAppTotal = workouts.size,
        imported = workouts.size,
        importedOldest = workouts.minOfOrNull { it.startedAt },
        importedNewest = workouts.maxOfOrNull { it.startedAt },
        importedSources = sourcesOf(workouts.map { it.sourcePackage }),
        healthConnect = exerciseHc?.let(HealthTypeSnapshot::of),
        readGranted = inspector.exerciseRead in g,
        lastSync = store.lastSync(HealthSyncType.WORKOUTS)
    )

    // Meals other apps shared (imported manually) -----------------------------------------
    val foods = container.foodRepository.entries.first()
    val importedFoods = foods.filter { it.healthConnectOrigin != null }
    val nutritionHc = if (usable && inspector.nutritionRead in g) {
        inspector.readNutrition(now.minus(Duration.ofDays(730)), now)?.filterNot { it.isOwn(own) }
    } else null
    importedFoods.forEach { f ->
        recent += RecentItem(f.timestamp, HealthSyncType.NUTRITION, f.name, f.healthConnectOrigin)
    }
    val nutritionStatus = TypeStatus(
        type = HealthSyncType.NUTRITION,
        inAppTotal = foods.size,
        imported = importedFoods.size,
        importedOldest = importedFoods.minOfOrNull { it.timestamp },
        importedNewest = importedFoods.maxOfOrNull { it.timestamp },
        importedSources = sourcesOf(importedFoods.mapNotNull { it.healthConnectOrigin }),
        healthConnect = nutritionHc?.let(HealthTypeSnapshot::of),
        readGranted = inspector.nutritionRead in g,
        lastSync = store.lastSync(HealthSyncType.NUTRITION)
    )

    val activity = if (usable) {
        runCatching { inspector.readActivity(inspector.stepsRead in g, inspector.activeRead in g) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
    } else null

    return StatusState(
        availability = availability,
        syncEnabled = container.prefs.healthConnectEnabled.first(),
        granted = granted,
        historySupported = usable && inspector.historyFeatureAvailable(),
        types = listOf(workoutStatus, weightStatus, bodyFatStatus, nutritionStatus),
        activity = activity,
        recent = recent.sortedByDescending { it.time }.take(20),
        lastAutoSync = store.lastAutoSync,
        lastHistoryImport = store.lastHistoryImport,
        lastRun = store.lastRun()
    )
}

private fun sourcesOf(packages: List<String>): List<Pair<String, Int>> =
    packages.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }

// -- UI ------------------------------------------------------------------------------------

@Composable
internal fun HealthSyncStatusScreen(container: AppContainer, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val inspector = remember { HealthSyncInspector(context.applicationContext) }
    val store = remember { HealthSyncStatusStore(context.applicationContext) }

    var state by remember { mutableStateOf<StatusState?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var runStep by remember { mutableStateOf<HealthImportStep?>(null) }
    var runMessage by remember { mutableStateOf<String?>(null) }
    var importAfterGrant by remember { mutableStateOf(false) }

    fun reload() {
        if (loading) return
        loading = true
        loadFailed = false
        scope.launch {
            try {
                val lbs = container.prefs.weightUnit.first() == "lbs"
                state = loadStatus(context, container, inspector, store, lbs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loadFailed = true
            } finally {
                loading = false
            }
        }
    }

    fun summary(result: HealthImportRunResult): String {
        val parts = mutableListOf<String>()
        parts += if (result.weightsAdded + result.bodyFatAdded + result.workoutsAdded == 0) {
            resources.getString(R.string.hs_result_nothing_new)
        } else {
            resources.getString(R.string.hs_result_summary, result.workoutsAdded, result.weightsAdded, result.bodyFatAdded)
        }
        if (!result.complete) parts += resources.getString(R.string.hs_result_incomplete)
        return parts.joinToString(" ")
    }

    fun runImport(fullHistory: Boolean) {
        if (runStep != null) return
        runMessage = null
        runStep = HealthImportStep.SYNCING
        scope.launch {
            try {
                val result = HealthImportRunner.run(container, fullHistory) { runStep = it }
                runMessage = summary(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                runMessage = resources.getString(R.string.hs_failed)
            } finally {
                runStep = null
                reload()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(container.health.permissionRequestContract()) {
        if (importAfterGrant) {
            importAfterGrant = false
            runImport(fullHistory = true)
        } else {
            reload()
        }
    }

    fun importFullHistory() {
        val s = state
        // Ask for history access (and anything else missing) first, where Health Connect supports it.
        if (s != null && s.historySupported && s.granted != null && inspector.historyRead !in s.granted) {
            importAfterGrant = true
            runCatching { permissionLauncher.launch(requestablePermissions(container)) }
                .onFailure { importAfterGrant = false; runImport(fullHistory = true) }
        } else {
            runImport(fullHistory = true)
        }
    }

    LaunchedEffect(Unit) { reload() }

    Dialog(onDismissRequest = { if (runStep == null) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = runStep == null, onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.hs_back))
                    }
                    Text(
                        stringResource(R.string.hs_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(enabled = !loading && runStep == null, onClick = ::reload) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.hs_refresh))
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val s = state
                    if (s == null) {
                        Text(
                            stringResource(if (loadFailed) R.string.hs_failed else R.string.hs_loading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        ConnectionCard(s)
                        PermissionsCard(
                            s, inspector,
                            onGrant = { runCatching { permissionLauncher.launch(requestablePermissions(container)) } },
                            onOpenHealthConnect = { runCatching { context.startActivity(container.health.manageAccessIntent()) } }
                        )
                        ActionsCard(
                            s,
                            runStep = runStep,
                            runMessage = runMessage,
                            onSyncNow = { runImport(fullHistory = false) },
                            onImportHistory = ::importFullHistory
                        )
                        s.types.forEach { TypeCard(it, container) }
                        ActivityCard(s.activity, s.granted.orEmpty(), inspector, container)
                        RecentCard(s.recent, container)
                    }
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun Instant.formatDate(): String = atZone(ZoneId.systemDefault()).format(dateFormatter.withLocale(Locale.getDefault()))
private fun Instant.formatDateTime(): String = atZone(ZoneId.systemDefault()).format(dateTimeFormatter.withLocale(Locale.getDefault()))

@Composable
private fun Instant?.orNever(): String = this?.formatDateTime() ?: stringResource(R.string.hs_never)

private fun sourceLabel(container: AppContainer, pkg: String): String =
    healthSourceName(pkg) { container.health.appLabel(it) }

@Composable
private fun CardBody(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f))
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (icon, tint) = when (ok) {
            true -> Icons.Outlined.CheckCircle to Color(0xFF2E9E5B)
            false -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
            null -> Icons.AutoMirrored.Outlined.HelpOutline to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun ConnectionCard(s: StatusState) {
    SectionCard(stringResource(R.string.hs_section_connection)) {
        CardBody {
            val (availabilityText, availabilityOk) = when (s.availability) {
                HealthConnectAvailability.AVAILABLE -> stringResource(R.string.hs_available) to true
                HealthConnectAvailability.PROFILE_UNSUPPORTED -> stringResource(R.string.hs_profile_unsupported) to false
                HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> stringResource(R.string.hs_update_required) to false
                HealthConnectAvailability.UNAVAILABLE -> stringResource(R.string.hs_unavailable) to false
            }
            StatusLine(stringResource(R.string.hs_availability_label), availabilityText, availabilityOk)
            StatusLine(
                stringResource(R.string.hs_sync_toggle_label),
                stringResource(if (s.syncEnabled) R.string.hs_on else R.string.hs_off),
                s.syncEnabled
            )
            val historyGranted = s.granted?.contains(HealthSyncInspectorKeys.HISTORY) == true
            StatusLine(
                stringResource(R.string.hs_history_label),
                stringResource(
                    when {
                        historyGranted -> R.string.hs_history_granted
                        !s.historySupported -> R.string.hs_history_unsupported
                        else -> R.string.hs_history_missing
                    }
                ),
                when {
                    historyGranted -> true
                    !s.historySupported -> null
                    else -> false
                }
            )
            if (!s.syncEnabled) Muted(stringResource(R.string.hs_sync_off_hint))
            Muted(stringResource(R.string.hs_auto_sync_how))
            Muted(stringResource(R.string.hs_last_auto_sync, s.lastAutoSync.orNever()))
        }
    }
}

/** History permission string, without needing an inspector instance in composables. */
private object HealthSyncInspectorKeys {
    val HISTORY: String = androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
}

@Composable
private fun PermissionsCard(
    s: StatusState,
    inspector: HealthSyncInspector,
    onGrant: () -> Unit,
    onOpenHealthConnect: () -> Unit
) {
    SectionCard(stringResource(R.string.hs_section_permissions)) {
        CardBody {
            val granted = s.granted
            val readLabel = stringResource(R.string.hs_perm_read)
            val writeLabel = stringResource(R.string.hs_perm_write)
            val yes = stringResource(R.string.hs_perm_granted)
            val no = stringResource(R.string.hs_perm_missing)
            val unknown = stringResource(R.string.hs_perm_unknown)
            fun state(p: String): Boolean? = granted?.let { p in it }
            fun text(p: String): String = when (state(p)) { true -> yes; false -> no; null -> unknown }
            permissionRows(inspector).forEach { row ->
                val readOk = row.read?.let(::state)
                val writeOk = row.write?.let(::state)
                val value = buildList {
                    row.read?.let { add("$readLabel: ${text(it)}") }
                    row.write?.let { add("$writeLabel: ${text(it)}") }
                }.joinToString(" · ")
                val overall = when {
                    readOk == null && row.read != null -> null
                    readOk == true && (writeOk ?: true) -> true
                    else -> false
                }
                StatusLine(stringResource(row.labelRes), "", overall)
                Muted(value)
            }
            if (s.historySupported) {
                StatusLine(stringResource(R.string.hs_perm_history), text(inspector.historyRead), state(inspector.historyRead))
            }
            StatusLine(stringResource(R.string.hs_perm_background), text(inspector.backgroundRead), state(inspector.backgroundRead) ?: false)
            Muted(stringResource(R.string.hs_background_note))
            val usable = s.availability == HealthConnectAvailability.AVAILABLE
            val anyMissing = granted == null || permissionRows(inspector).any { r ->
                listOfNotNull(r.read, r.write).any { it !in granted }
            } || (s.historySupported && inspector.historyRead !in granted)
            Spacer(Modifier.size(4.dp))
            if (usable && anyMissing) {
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.hs_grant_missing)) }
            }
            OutlinedButton(onClick = onOpenHealthConnect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hs_open_hc))
            }
        }
    }
}

@Composable
private fun ActionsCard(
    s: StatusState,
    runStep: HealthImportStep?,
    runMessage: String?,
    onSyncNow: () -> Unit,
    onImportHistory: () -> Unit
) {
    SectionCard(stringResource(R.string.hs_section_actions)) {
        CardBody {
            val canRun = s.availability == HealthConnectAvailability.AVAILABLE && s.syncEnabled && runStep == null
            if (!s.syncEnabled) Muted(stringResource(R.string.hs_not_connected))
            Button(onClick = onSyncNow, enabled = canRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hs_sync_now))
            }
            OutlinedButton(onClick = onImportHistory, enabled = canRun, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hs_import_history))
            }
            Muted(stringResource(R.string.hs_import_history_hint))
            if (runStep != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            when (runStep) {
                                HealthImportStep.SYNCING -> R.string.hs_step_syncing
                                HealthImportStep.WEIGHTS -> R.string.hs_step_weights
                                HealthImportStep.BODY_FAT -> R.string.hs_step_body_fat
                                HealthImportStep.WORKOUTS -> R.string.hs_step_workouts
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            runMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            s.lastRun?.let { run ->
                Muted(stringResource(R.string.hs_last_run, run.finishedAt.formatDateTime()))
            }
            Muted(stringResource(R.string.hs_last_history, s.lastHistoryImport.orNever()))
        }
    }
}

@Composable
private fun TypeCard(t: TypeStatus, container: AppContainer) {
    val title = stringResource(
        when (t.type) {
            HealthSyncType.WEIGHT -> R.string.hs_section_weight
            HealthSyncType.BODY_FAT -> R.string.hs_section_body_fat
            HealthSyncType.WORKOUTS -> R.string.hs_section_workouts
            HealthSyncType.NUTRITION -> R.string.hs_section_nutrition
        }
    )
    SectionCard(title) {
        CardBody {
            if (t.type == HealthSyncType.WORKOUTS) {
                Text(stringResource(R.string.hs_in_app_simple, t.imported), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.hs_in_app, t.imported, t.inAppTotal), style = MaterialTheme.typography.bodyMedium)
            }
            if (t.importedOldest != null && t.importedNewest != null) {
                Muted(stringResource(R.string.hs_range, t.importedOldest.formatDate(), t.importedNewest.formatDate()))
            }
            if (t.importedSources.isNotEmpty()) {
                val names = t.importedSources.map { (pkg, n) -> "${sourceLabel(container, pkg)} ($n)" }
                Muted(stringResource(R.string.hs_sources, names.joinToString(", ")))
            }
            if (t.type != HealthSyncType.NUTRITION) {
                Muted(stringResource(R.string.hs_last_sync, t.lastSync.orNever()))
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val hc = t.healthConnect
            when {
                !t.readGranted -> StatusLine(stringResource(R.string.hs_hc_no_permission), "", false)
                hc == null -> StatusLine(stringResource(R.string.hs_hc_read_failed), "", null)
                else -> {
                    Text(stringResource(R.string.hs_in_hc, hc.count), style = MaterialTheme.typography.bodyMedium)
                    if (hc.oldest != null && hc.newest != null) {
                        Muted(stringResource(R.string.hs_in_hc_range, hc.oldest.formatDate(), hc.newest.formatDate()))
                    }
                    if (hc.sources.isEmpty()) {
                        Muted(stringResource(R.string.hs_sources_none))
                    } else {
                        val names = hc.sources.map { (pkg, n) -> "${sourceLabel(container, pkg)} ($n)" }
                        Muted(stringResource(R.string.hs_sources, names.joinToString(", ")))
                    }
                }
            }
            when (t.type) {
                HealthSyncType.WORKOUTS -> Muted(stringResource(R.string.hs_workouts_window))
                HealthSyncType.NUTRITION -> Muted(stringResource(R.string.hs_nutrition_manual))
                else -> Unit
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: HealthActivitySnapshot?,
    granted: Set<String>,
    inspector: HealthSyncInspector,
    container: AppContainer
) {
    SectionCard(stringResource(R.string.hs_section_activity)) {
        CardBody {
            val nf = remember { NumberFormat.getIntegerInstance() }
            if (inspector.stepsRead !in granted && inspector.activeRead !in granted) {
                StatusLine(stringResource(R.string.hs_hc_no_permission), "", false)
            } else if (activity == null) {
                StatusLine(stringResource(R.string.hs_hc_read_failed), "", null)
            } else {
                activity.stepsToday?.let { Text(stringResource(R.string.hs_steps_today, nf.format(it)), style = MaterialTheme.typography.bodyMedium) }
                activity.steps7Days?.let { Muted(stringResource(R.string.hs_steps_week, nf.format(it))) }
                activity.activeKcalToday?.let { Text(stringResource(R.string.hs_active_today, nf.format(it)), style = MaterialTheme.typography.bodyMedium) }
                activity.activeKcal7Days?.let { Muted(stringResource(R.string.hs_active_week, nf.format(it))) }
                if (activity.latestRecordAt != null) {
                    StatusLine(stringResource(R.string.hs_latest_data, activity.latestRecordAt.formatDateTime()), "", true)
                } else {
                    StatusLine(stringResource(R.string.hs_no_recent_data), "", false)
                }
                if (activity.sources.isNotEmpty()) {
                    Muted(stringResource(R.string.hs_sources, activity.sources.joinToString(", ") { sourceLabel(container, it) }))
                }
            }
            Muted(stringResource(R.string.hs_activity_note))
        }
    }
}

@Composable
private fun RecentCard(items: List<RecentItem>, container: AppContainer) {
    SectionCard(stringResource(R.string.hs_section_recent)) {
        CardBody {
            if (items.isEmpty()) {
                Muted(stringResource(R.string.hs_recent_empty))
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    val source = item.sourcePackage?.let { sourceLabel(container, it) }
                        ?: stringResource(R.string.hs_source_health_connect)
                    Muted("${item.time.formatDateTime()} · $source")
                }
            }
        }
    }
}
