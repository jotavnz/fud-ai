package com.apoorvdarshan.calorietracker.ui.workouts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.data.ExerciseLocalization
import com.apoorvdarshan.calorietracker.data.ExerciseRepository
import com.apoorvdarshan.calorietracker.data.ExerciseVisual
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.models.PlannedExercise
import com.apoorvdarshan.calorietracker.models.importedWorkoutsOn
import com.apoorvdarshan.calorietracker.models.ExerciseTimerAction
import com.apoorvdarshan.calorietracker.models.WorkoutRpeScale
import com.apoorvdarshan.calorietracker.models.WorkoutSetFeel
import com.apoorvdarshan.calorietracker.models.ExerciseRoles
import com.apoorvdarshan.calorietracker.models.WorkoutIntensity
import com.apoorvdarshan.calorietracker.models.WorkoutBurnEstimator
import com.apoorvdarshan.calorietracker.models.WorkoutSetInput
import com.apoorvdarshan.calorietracker.models.PlannedSet
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.ui.components.FudGlassDialog
import com.apoorvdarshan.calorietracker.ui.components.energyText
import com.apoorvdarshan.calorietracker.ui.components.energyUnitLabel
import com.apoorvdarshan.calorietracker.ui.components.FudGlassSurface
import com.apoorvdarshan.calorietracker.ui.components.FudGlassTextButton
import com.apoorvdarshan.calorietracker.ui.home.OutdoorActivityDurationSheet
import com.apoorvdarshan.calorietracker.ui.home.OutdoorActivityKind
import com.apoorvdarshan.calorietracker.ui.voice.VoiceAssistantButton
import com.apoorvdarshan.calorietracker.ui.home.SheetGlassDropdownMenu
import com.apoorvdarshan.calorietracker.ui.home.SheetGlassDropdownMenuItem
import com.apoorvdarshan.calorietracker.ui.navigation.BottomNavScrollPadding
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import com.apoorvdarshan.calorietracker.ui.util.formattedWholeNumber
import coil.compose.AsyncImage
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val WORKOUT_WEEKS = 53
private const val CURRENT_WORKOUT_WEEK = WORKOUT_WEEKS - 1

@Composable
internal fun WorkoutDiaryScreen(
    container: com.apoorvdarshan.calorietracker.AppContainer,
    bodyWeightKg: Double,
    state: WorkoutDiaryUiState,
    exerciseRepository: ExerciseRepository,
    viewModel: WorkoutsViewModel,
    modifier: Modifier = Modifier,
    weekStartsOnMonday: Boolean = true,
    onShowLibrary: () -> Unit,
    onCreateExercise: () -> Unit = {},
    onEditUserExercise: (String) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    var pickerRequest by remember { mutableStateOf<WorkoutPickerRequest?>(null) }
    // Muscle recovery from the diary and the watch's workouts; refreshed as the diary changes.
    val recovery by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.apoorvdarshan.calorietracker.models.MuscleReadiness>(),
        state.workoutCounts,
        state.selectedDate
    ) {
        value = runCatching { container.trainingLoad.readiness() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrDefault(emptyList())
    }
    var textSheetVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var workoutTextInputVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var workoutStartsWithVoice by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var workoutVoiceVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var workoutTranscript by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var copySheetVisible by remember { mutableStateOf(false) }
    var routinesSheetVisible by remember { mutableStateOf(false) }
    var saveRoutineVisible by remember { mutableStateOf(false) }
    val restTimer by container.workoutRestTimer.timer.collectAsState()
    val restAvailable = state.isRestAvailable()
    var historyRequest by remember { mutableStateOf<WorkoutExerciseHistoryRequest?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var outdoorActivitySheet by remember { mutableStateOf<OutdoorActivityKind?>(null) }
    val walkRunQuickLogEnabled by container.prefs.walkRunQuickLogEnabled.collectAsState(initial = false)
    val smartSuggestions by container.prefs.smartSuggestionsEnabled.collectAsState(initial = true)
    val importedWorkouts by container.importedHealthWorkoutRepository.workouts.collectAsState(initial = emptyList())
    val importedWorkoutsForDay = remember(importedWorkouts, state.selectedDate) {
        importedWorkoutsOn(importedWorkouts, state.selectedDate)
    }
    val heightUnit by container.prefs.heightUnit.collectAsState(initial = "cm")
    val voiceUi by container.voiceAssistant.ui.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val exerciseCardBounds = remember { mutableStateMapOf<UUID, androidx.compose.ui.geometry.Rect>() }
    var diaryRootOrigin by remember { mutableStateOf(Offset.Zero) }
    // Reordering belongs to one day: changing days or dropping below two exercises
    // ends it, so it never comes back on its own when the user returns or adds one.
    var reorderDateKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDateKey = state.selectedDate.toString()
    val isReordering = retainedReorderDateKey(reorderDateKey, selectedDateKey, state.exercises.size) != null
    LaunchedEffect(selectedDateKey, state.exercises.size > 1) {
        reorderDateKey = retainedReorderDateKey(reorderDateKey, selectedDateKey, state.exercises.size)
    }
    val moveUpLabel = stringResource(R.string.workout_move_up)
    val moveDownLabel = stringResource(R.string.workout_move_down)
    val addWorkoutLabel = stringResource(R.string.wk_cd_add_workout)
    val beginnerRoutinePrompt = stringResource(R.string.wk_coach_beginner_prompt)
    val unsurePrompt = stringResource(R.string.machine_coach_unsure_prompt)
    var dismissedExtras by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    var guidedVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var quickFinish by remember { mutableStateOf<PlannedExercise?>(null) }
    var warmupFor by remember { mutableStateOf<PlannedExercise?>(null) }
    warmupFor?.let { exercise ->
        WarmupPlatesDialog(exercise, state.weightUnit, onDismiss = { warmupFor = null })
    }
    quickFinish?.let { exercise ->
        QuickFinishDialog(
            exercise = exercise,
            weightUnit = state.weightUnit,
            lastTimeSummary = viewModel.lastExerciseLiftSummary(exercise.itemId, exercise.name),
            onDismiss = { quickFinish = null },
            onSave = { sets ->
                quickFinish = null
                viewModel.finishExercise(exercise.id, sets)
            }
        )
    }
    if (guidedVisible) {
        GuidedWorkoutScreen(
            exercises = state.exercises,
            weightUnit = state.weightUnit,
            rpeScale = state.preferences.rpeScale,
            restTimer = restTimer,
            visualFor = { exerciseRepository.visualFor(it.asExerciseItem(), state.visualGender) },
            onWeight = { exerciseId, setId, value -> viewModel.updateWeight(exerciseId, setId, value) },
            onReps = { exerciseId, setId, value -> viewModel.updateReps(exerciseId, setId, value) },
            onRpe = { exerciseId, setId, value -> viewModel.updateRpe(exerciseId, setId, value) },
            onStartRest = if (restAvailable) { exerciseId, setId -> viewModel.startRest(exerciseId, setId) } else null,
            onSkipRest = { viewModel.skipRest() },
            onDismiss = { guidedVisible = false }
        )
    }

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    // Focus relocation and IME resizing also scroll the list. Only a user's
    // drag should dismiss input; automatic scrolling must keep typing active.
    val isDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragging) {
        if (isDragging) dismissKeyboard()
    }

    LaunchedEffect(state.exercises.map { it.id }) {
        exerciseCardBounds.keys.retainAll(state.exercises.mapTo(mutableSetOf()) { it.id })
    }

    // The voice assistant logs workouts to the day shown here while the diary is open.
    LaunchedEffect(state.selectedDate) {
        container.navigator.workoutDiaryDate.value = state.selectedDate
    }
    DisposableEffect(Unit) {
        onDispose { container.navigator.workoutDiaryDate.value = null }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { diaryRootOrigin = it.boundsInRoot().topLeft }
            .pointerInput(diaryRootOrigin) {
                // Observe completed pointers without consuming them, so set
                // fields, buttons, scrolling, and day swipes keep their normal
                // input behavior.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.firstOrNull { it.previousPressed && !it.pressed }?.let { change ->
                            // Match iOS: blank screen chrome dismisses input,
                            // while the whole exercise card preserves focus.
                            val rootPosition = change.position + diaryRootOrigin
                            if (exerciseCardBounds.values.none { it.contains(rootPosition) }) {
                                dismissKeyboard()
                            }
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding().padding(bottom = 8.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                // Room for the pinned rest countdown while it runs.
                top = if (restTimer != null) 136.dp else 64.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "workout-week-strip") {
                WorkoutWeekStrip(
                    selectedDate = state.selectedDate,
                    workoutCounts = state.workoutCounts,
                    doneDates = state.doneDates,
                    onSelect = {
                        dismissKeyboard()
                        viewModel.selectDate(it)
                    },
                    weekStartsOnMonday = weekStartsOnMonday,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
            }

            if (state.exercises.isEmpty()) {
                item(key = "workout-empty") {
                    WorkoutEmptyState(
                        splitTitle = workoutSplitLabel(state.preferences.split),
                        simpleLogging = state.preferences.simpleLogging,
                        onAdd = { addMenuExpanded = true },
                        onAskCoach = { container.navigator.showCoachMessage(beginnerRoutinePrompt) },
                        onStarter = { routine ->
                            val items = routine.exerciseIds.mapNotNull { exerciseRepository.byId[it] }
                            viewModel.addStarterRoutine(items, StarterRoutine.SETS)
                        },
                        modifier = Modifier.workoutDaySwipe(
                            selectedDate = state.selectedDate,
                            onMove = { viewModel.moveDate(it) }
                        )
                    )
                }
            }

            item(key = "workout-machine-coach") {
                MachineCoachEntry(
                    onPick = {
                        dismissKeyboard()
                        viewModel.openMachineCoach(it)
                    },
                    recovering = if (smartSuggestions) state.fatigue.keys else emptySet(),
                    onUnsure = {
                        dismissKeyboard()
                        container.navigator.showCoachMessage(unsurePrompt)
                    }
                )
            }

            if (smartSuggestions && recovery.isNotEmpty()) {
                item(key = "workout-recovery") {
                    MuscleRecoveryCard(recovery) {
                        dismissKeyboard()
                        viewModel.openMachineCoach(it)
                    }
                }
            }

            // Smart suggestions: at most two tips at a time, each hidden until tomorrow on dismiss.
            if (smartSuggestions) {
                state.insights.take(2).forEach { insight ->
                    val detail = (insight as? com.apoorvdarshan.calorietracker.models.TrainingInsight.Plateau)?.exerciseId
                        ?: insight.group?.name.orEmpty()
                    item(key = "workout-insight-${insight::class.simpleName}-$detail") {
                        TrainingInsightCard(
                            insight = insight,
                            unit = state.weightUnit,
                            onOpenGroup = {
                                dismissKeyboard()
                                viewModel.openMachineCoach(it)
                            },
                            onDismiss = { viewModel.dismissInsight(insight) },
                            onApplySplit = { viewModel.applySplit(it) }
                        )
                    }
                }
            }

            item(key = "workout-burn") {
                WorkoutBurnHero(
                    state = state,
                    onCalculate = {
                        dismissKeyboard()
                        viewModel.calculateBurn()
                    },
                    modifier = Modifier.workoutDaySwipe(
                        selectedDate = state.selectedDate,
                        onMove = {
                            dismissKeyboard()
                            viewModel.moveDate(it)
                        }
                    )
                )
            }

            // Read-only sessions other apps shared with Health Connect (iOS shows Apple
            // Health workouts in the same place).
            if (importedWorkoutsForDay.isNotEmpty()) {
                item(key = "workout-health-imports") {
                    ImportedHealthWorkoutDayCard(
                        workouts = importedWorkoutsForDay,
                        useMetricDistance = heightUnit != "ftin",
                        modifier = Modifier.workoutDaySwipe(
                            selectedDate = state.selectedDate,
                            onMove = {
                                dismissKeyboard()
                                viewModel.moveDate(it)
                            }
                        )
                    )
                }
            }

            item(key = "workout-day-title") {
                WorkoutDayHeader(
                    selectedDate = state.selectedDate,
                    workoutCount = state.exercises.size,
                    isReordering = isReordering,
                    onToggleReorder = if (state.exercises.size > 1) {
                        {
                            dismissKeyboard()
                            reorderDateKey = if (isReordering) null else selectedDateKey
                        }
                    } else null,
                    modifier = Modifier
                        .workoutDaySwipe(
                            selectedDate = state.selectedDate,
                            onMove = {
                                dismissKeyboard()
                                viewModel.moveDate(it)
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { dismissKeyboard() }
                )
            }

            if (state.preferences.simpleLogging && state.exercises.any { !it.isCardio } &&
                state.selectedDate == java.time.LocalDate.now()
            ) {
                item(key = "workout-guided-entry") {
                    com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton(
                        text = stringResource(R.string.wk_guided_start),
                        onClick = { guidedVisible = true }
                    )
                }
            }
            // Warm-up before the day's lifts and a cool-down after a hard session, from the catalog.
            val sessionExtras = sessionExtrasFor(state.exercises, gentle = state.preferences.simpleLogging, rpeScale = state.preferences.rpeScale)
            sessionExtras?.let { extras ->
                val extrasKey = "${state.selectedDate}-${extras.kind}"
                if (extrasKey !in dismissedExtras) {
                    val items = extras.picks.mapNotNull { exerciseRepository.byId[it.id] }
                    if (items.isNotEmpty()) item(key = "workout-session-extras") {
                        WorkoutSessionExtrasCard(
                            kind = extras.kind,
                            names = items.map { it.name },
                            onAdd = {
                                viewModel.addSessionExtras(items, atStart = extras.kind == ExerciseRoles.Kind.WARMUP)
                                dismissedExtras = dismissedExtras + extrasKey
                            },
                            onDismiss = { dismissedExtras = dismissedExtras + extrasKey }
                        )
                    }
                }
            }
            if (state.preferences.simpleLogging && !state.preferences.loggingGuideDismissed &&
                state.exercises.any { !it.isCardio }
            ) {
                item(key = "workout-logging-guide") {
                    WorkoutLoggingGuide(onDismiss = { viewModel.dismissLoggingGuide() })
                }
            }
            val load = com.apoorvdarshan.calorietracker.models.WorkoutLoadCheck.check(state.exercises, state.preferences.restTimerSeconds)
            val loadKey = "load-${state.selectedDate}"
            if (load.tooMuch && loadKey !in dismissedExtras && !isReordering) {
                item(key = "workout-load-warning") {
                    WorkoutLoadWarning(load, onDismiss = { dismissedExtras = dismissedExtras + loadKey })
                }
            }
            if (state.exercises.isNotEmpty()) {
                itemsIndexed(state.exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    val canMoveUp = index > 0
                    val canMoveDown = index < state.exercises.lastIndex
                    val moveUp = { viewModel.moveExercise(exercise.id, -1) }
                    val moveDown = { viewModel.moveExercise(exercise.id, 1) }
                    // Placement-only animation keeps moved cards readable without fading edits.
                    val itemModifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                    if (isReordering) {
                        WorkoutReorderCard(
                            exercise = exercise,
                            visual = exerciseRepository.visualFor(exercise.asExerciseItem(), state.visualGender),
                            position = index + 1,
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            onMoveUp = moveUp,
                            onMoveDown = moveDown,
                            modifier = itemModifier
                        )
                        return@itemsIndexed
                    }
                    val isSaved = exercise.itemId in state.savedExerciseIds
                    val toggleSaved = { viewModel.toggleSaved(exercise.itemId) }
                    val removeExercise = {
                        dismissKeyboard()
                        viewModel.removeExercise(exercise.id)
                    }
                    // TalkBack users can reorder from the card itself, without entering Reorder.
                    val moveActions = buildList {
                        if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { moveUp(); true })
                        if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { moveDown(); true })
                    }
                    SwipeableWorkoutExerciseCard(
                        exerciseId = exercise.id,
                        finished = exercise.finished,
                        modifier = itemModifier.onGloballyPositioned { coordinates ->
                            exerciseCardBounds[exercise.id] = coordinates.boundsInRoot()
                        },
                        onFinish = {
                            dismissKeyboard()
                            when {
                                exercise.finished -> viewModel.finishExercise(exercise.id, finished = false)
                                exercise.setsMissingReps.isNotEmpty() -> quickFinish = exercise
                                else -> viewModel.finishExercise(exercise.id)
                            }
                        },
                        onRemove = removeExercise
                    ) {
                        WorkoutExerciseCard(
                            exercise = exercise,
                            visual = exerciseRepository.visualFor(exercise.asExerciseItem(), state.visualGender),
                            weightUnit = state.weightUnit,
                            rpeScale = state.preferences.rpeScale,
                            simpleLogging = state.preferences.simpleLogging,
                            editingDate = state.selectedDate,
                            isSaved = isSaved,
                            lastTimeSummary = viewModel.lastExerciseLiftSummary(exercise.itemId, exercise.name),
                            moveActions = moveActions,
                            onOpen = { viewModel.openDiaryExercise(exercise) },
                            onShowWarmup = { warmupFor = exercise },
                            onShowHistory = {
                                historyRequest = WorkoutExerciseHistoryRequest(exercise.itemId, exercise.name)
                            },
                            onToggleSaved = toggleSaved,
                            onRemove = removeExercise,
                            onSetCount = { viewModel.setSetCount(exercise.id, it) },
                            onWeight = { setId, value -> viewModel.updateWeight(exercise.id, setId, value) },
                            onReps = { setId, value -> viewModel.updateReps(exercise.id, setId, value) },
                            onRpe = { setId, value -> viewModel.updateRpe(exercise.id, setId, value) },
                            restingSetId = restTimer?.takeIf { it.exerciseId == exercise.id }?.setId,
                            onStartRest = if (restAvailable) {
                                { setId -> viewModel.startRest(exercise.id, setId) }
                            } else null,
                            onTimerAction = { viewModel.updateTimer(exercise.id, it) },
                            onEffort = { viewModel.setTimerIntensity(exercise.id, it) }
                        )
                    }
                }
            }

            if (state.exercises.isNotEmpty() && !isReordering) {
                item(key = "workout-finish") {
                    WorkoutFinishBar(
                        state = state,
                        onFinish = {
                            dismissKeyboard()
                            viewModel.calculateBurn()
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Real scrollable space lets the final set move clear of the IME.
            item(key = "workout-extra-space") {
                Spacer(Modifier.height(BottomNavScrollPadding + 160.dp))
            }
        }

        // Pinned at the top so it stays visible above the keyboard while the next set is typed.
        restTimer?.let { timer ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 60.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            ) {
                WorkoutRestTimerBar(
                    timer = timer,
                    onExtend = viewModel::extendRest,
                    onSkip = viewModel::skipRest
                )
            }
        }

        WorkoutModeToggleButton(
            mode = com.apoorvdarshan.calorietracker.models.WorkoutTabMode.LOG,
            onToggle = {
                dismissKeyboard()
                onShowLibrary()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
        )

        // The voice orb sits above the add button, out of the way of the cards' content.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!voiceUi.visible) {
                VoiceAssistantButton(
                    onClick = {
                        dismissKeyboard()
                        container.voiceAssistant.requestStart()
                    },
                    size = 48.dp,
                    paused = listState.isScrollInProgress
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(AppColors.Calorie)
                        .clickable(role = Role.Button) {
                            dismissKeyboard()
                            addMenuExpanded = true
                        }
                        .semantics { contentDescription = addWorkoutLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                SheetGlassDropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false },
                    modifier = Modifier.heightIn(max = 520.dp),
                    menuWidth = 236.dp
                ) {
                    if (state.splitGroups.isEmpty()) {
                        SheetGlassDropdownMenuItem(
                            label = stringResource(R.string.wk_all_exercises),
                            leadingContent = { WorkoutMenuGlyph(workoutMenuGlyphAsset("All exercises", emptySet())) },
                            onClick = {
                                addMenuExpanded = false
                                pickerRequest = WorkoutPickerRequest.all()
                            }
                        )
                    } else {
                        state.splitGroups.forEach { group ->
                            SheetGlassDropdownMenuItem(
                                label = workoutSplitGroupLabel(group.title),
                                leadingContent = {
                                    WorkoutMenuGlyph(workoutMenuGlyphAsset(group.title, group.muscles))
                                },
                                onClick = {
                                    addMenuExpanded = false
                                    pickerRequest = WorkoutPickerRequest.group(group)
                                }
                            )
                        }
                    }
                    SheetGlassDropdownMenuItem(
                        label = stringResource(R.string.wk_copy_from_day),
                        leadingIcon = Icons.Filled.ContentCopy,
                        onClick = {
                            addMenuExpanded = false
                            copySheetVisible = true
                        }
                    )
                    SheetGlassDropdownMenuItem(
                        label = stringResource(R.string.workout_routine_start),
                        leadingIcon = Icons.AutoMirrored.Outlined.ListAlt,
                        onClick = {
                            addMenuExpanded = false
                            routinesSheetVisible = true
                        }
                    )
                    if (state.exercises.isNotEmpty()) {
                        SheetGlassDropdownMenuItem(
                            label = stringResource(R.string.workout_routine_save),
                            leadingIcon = Icons.Filled.Save,
                            onClick = {
                                addMenuExpanded = false
                                saveRoutineVisible = true
                            }
                        )
                    }
                    SheetGlassDropdownMenuItem(
                        label = stringResource(R.string.workout_source_saved),
                        leadingIcon = Icons.Filled.Bookmark,
                        onClick = {
                            addMenuExpanded = false
                            pickerRequest = WorkoutPickerRequest.saved()
                        }
                    )
                    if (walkRunQuickLogEnabled) {
                        SheetGlassDropdownMenuItem(
                            label = stringResource(R.string.outdoor_activity_walking),
                            leadingIcon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                            onClick = {
                                addMenuExpanded = false
                                outdoorActivitySheet = OutdoorActivityKind.WALKING
                            }
                        )
                        SheetGlassDropdownMenuItem(
                            label = stringResource(R.string.outdoor_activity_running),
                            leadingIcon = Icons.AutoMirrored.Filled.DirectionsRun,
                            onClick = {
                                addMenuExpanded = false
                                outdoorActivitySheet = OutdoorActivityKind.RUNNING
                            }
                        )
                    }
                    SheetGlassDropdownMenuItem(
                        label = stringResource(R.string.workout_create_exercise),
                        leadingIcon = Icons.Filled.AddCircle,
                        onClick = {
                            addMenuExpanded = false
                            onCreateExercise()
                        }
                    )
                    HorizontalDivider(color = workoutsColors().hairline.copy(alpha = 0.45f))
                    SheetGlassDropdownMenuItem(label = stringResource(R.string.workout_text_voice), leadingIcon = Icons.Filled.Mic, onClick = {
                        addMenuExpanded = false
                        workoutStartsWithVoice = true
                        workoutVoiceVisible = true
                    })
                    SheetGlassDropdownMenuItem(label = stringResource(R.string.workout_text_menu), leadingIcon = Icons.Filled.Add, onClick = {
                        addMenuExpanded = false
                        workoutStartsWithVoice = false
                        workoutTextInputVisible = true
                    })
                }
            }
        }
    }

    if (workoutTextInputVisible) com.apoorvdarshan.calorietracker.ui.home.TextInputDialog(
        onDismiss = { workoutTextInputVisible = false },
        onSubmit = { workoutTranscript = it; workoutTextInputVisible = false; textSheetVisible = true },
        examples = listOf(stringResource(R.string.workout_text_example))
    )
    if (workoutVoiceVisible) com.apoorvdarshan.calorietracker.ui.home.VoiceInputSheet(
        container = container,
        onDismiss = { workoutVoiceVisible = false },
        onSubmit = { workoutTranscript = it; workoutVoiceVisible = false; textSheetVisible = true }
    )
    if (textSheetVisible) WorkoutTextSheet(
        container = container, library = exerciseRepository.exercises,
        initialDescription = workoutTranscript, analyzeOnOpen = workoutTranscript.isNotBlank(),
        onStartOver = {
            textSheetVisible = false; workoutTranscript = ""
            if (workoutStartsWithVoice) workoutVoiceVisible = true else workoutTextInputVisible = true
        },
        selectedDate = state.selectedDate, unit = state.weightUnit,
        bodyWeightKg = bodyWeightKg, rpeScale = state.preferences.rpeScale,
        onAdded = { viewModel.selectDate(it); textSheetVisible = false },
        onDismiss = { textSheetVisible = false }
    )

    pickerRequest?.let { request ->
        WorkoutPickerSheet(
            request = request,
            repository = exerciseRepository,
            selectedExerciseIds = state.exercises.mapTo(mutableSetOf()) { it.itemId },
            savedExerciseIds = state.savedExerciseIds,
            initialSource = if (request.isSavedContext) WorkoutPickerSource.SAVED else viewModel.pickerSource(),
            initialFilterState = viewModel.pickerFilter(request.contextId),
            visualGender = state.visualGender,
            preferredEquipment = state.preferences.equipment,
            hidePrimaryFilter = request.muscles.isNotEmpty() &&
                state.preferences.split in setOf(
                    com.apoorvdarshan.calorietracker.models.WorkoutSplit.FULL_BODY,
                    com.apoorvdarshan.calorietracker.models.WorkoutSplit.CUSTOM
                ),
            onSourceChange = viewModel::setPickerSource,
            onFilterStateChange = { viewModel.setPickerFilter(request.contextId, it) },
            onToggleExercise = viewModel::toggleExercise,
            onToggleSaved = viewModel::toggleSaved,
            onCreateExercise = {
                pickerRequest = null
                onCreateExercise()
            },
            onEditUserExercise = { id ->
                pickerRequest = null
                onEditUserExercise(id)
            },
            onDismiss = { pickerRequest = null }
        )
    }

    outdoorActivitySheet?.let { activity ->
        OutdoorActivityDurationSheet(
            activity = activity,
            onDismiss = { outdoorActivitySheet = null },
            onLog = { minutes, intensity ->
                viewModel.logQuickOutdoorActivity(activity, minutes, intensity)
            }
        )
    }

    if (copySheetVisible) {
        WorkoutCopySheet(
            targetDate = state.selectedDate,
            days = state.copyDays,
            onCopy = { date, includeSetDetails ->
                viewModel.copyPlan(date, includeSetDetails)
                copySheetVisible = false
            },
            onDismiss = { copySheetVisible = false }
        )
    }

    if (routinesSheetVisible) {
        WorkoutRoutinesSheet(
            routines = state.routines,
            onApply = { routine ->
                viewModel.applyRoutine(routine.id)
                routinesSheetVisible = false
            },
            onRename = { routine, name -> viewModel.renameRoutine(routine.id, name) },
            onDelete = { routine -> viewModel.deleteRoutine(routine.id) },
            onDismiss = { routinesSheetVisible = false }
        )
    }

    if (saveRoutineVisible) {
        WorkoutRoutineNameDialog(
            title = stringResource(R.string.workout_routine_save_title),
            message = stringResource(R.string.workout_routine_save_message),
            onSave = { name ->
                viewModel.saveRoutine(name)
                saveRoutineVisible = false
            },
            onDismiss = { saveRoutineVisible = false }
        )
    }

    historyRequest?.let { request ->
        WorkoutExerciseHistorySheet(
            request = request,
            selectedDate = state.selectedDate,
            weightUnit = state.weightUnit,
            history = viewModel.exerciseLiftHistory(request.itemId, request.name),
            onDismiss = { historyRequest = null }
        )
    }

    state.notice?.let { message ->
        FudGlassDialog(onDismissRequest = viewModel::dismissNotice) {
            Text(
                text = stringResource(R.string.wk_log_first_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FudGlassTextButton(text = stringResource(R.string.action_ok), onClick = viewModel::dismissNotice)
            }
        }
    }
}

@Composable
private fun WorkoutMenuGlyph(asset: String) {
    AsyncImage(
        model = asset,
        contentDescription = null,
        colorFilter = ColorFilter.tint(AppColors.Calorie),
        modifier = Modifier.size(20.dp)
    )
}

private fun workoutMenuGlyphAsset(title: String, muscles: Set<String>): String {
    if (muscles.size == 1) return muscleGlyphAsset(muscles.first())
    val key = when {
        title.contains("push", ignoreCase = true) -> "group_push"
        title.contains("pull", ignoreCase = true) -> "group_pull"
        title.contains("upper", ignoreCase = true) -> "group_upper"
        title.contains("lower", ignoreCase = true) ||
            title.contains("leg", ignoreCase = true) ||
            title.contains("quad", ignoreCase = true) ||
            title.contains("hamstring", ignoreCase = true) -> "group_lower"
        title.contains("core", ignoreCase = true) || title.contains("ab", ignoreCase = true) -> "abs"
        title.contains("arm", ignoreCase = true) ||
            title.contains("bicep", ignoreCase = true) ||
            title.contains("tricep", ignoreCase = true) -> "group_arms"
        title.contains("back", ignoreCase = true) ||
            title.contains("lat", ignoreCase = true) ||
            title.contains("trap", ignoreCase = true) -> "group_back"
        title.contains("chest", ignoreCase = true) -> "chest"
        title.contains("shoulder", ignoreCase = true) -> "shoulders"
        else -> "generic"
    }
    return "file:///android_asset/muscle/muscle_icon_$key.png"
}

@Composable
private fun WorkoutBurnHero(
    state: WorkoutDiaryUiState,
    onCalculate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FudGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 26.dp,
            padding = 10.dp
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WorkoutMetric(
                    label = stringResource(R.string.wk_metric_sets),
                    value = state.performedSetCount.formattedWholeNumber(),
                    icon = Icons.Filled.Checklist,
                    active = state.performedSetCount > 0,
                    modifier = Modifier.weight(1f)
                )
                MetricDivider()
                WorkoutMetric(
                    label = stringResource(R.string.wk_metric_workouts),
                    value = state.exercises.size.formattedWholeNumber(),
                    icon = Icons.Filled.FitnessCenter,
                    active = state.exercises.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
                MetricDivider()
                WorkoutMetric(
                    label = stringResource(R.string.wk_metric_reps),
                    value = state.repCount.formattedWholeNumber(),
                    icon = Icons.Filled.Repeat,
                    active = state.repCount > 0,
                    modifier = Modifier.weight(1f)
                )
                MetricDivider()
                WorkoutMetric(
                    label = stringResource(R.string.wk_metric_burn),
                    value = state.caloriesBurned?.let { energyText(it) } ?: "-- ${energyUnitLabel()}",
                    icon = Icons.Filled.LocalFireDepartment,
                    active = state.caloriesBurned != null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Under the day's exercise cards: "Terminé" saves the workout; once saved it turns green with the time. */
@Composable
private fun WorkoutFinishBar(
    state: WorkoutDiaryUiState,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val done = state.isDone && !state.hasChangesSinceDone
    val changed = state.isDone && state.hasChangesSinceDone
    val locale = LocalConfiguration.current.locales[0]
    val label = when {
        state.isCalculatingBurn -> stringResource(R.string.wk_calculating)
        done -> {
            val time = DateTimeFormatter.ofPattern("HH:mm", locale)
                .format(state.doneAt!!.atZone(java.time.ZoneId.systemDefault()))
            stringResource(R.string.wk_done_at, time)
        }
        changed -> stringResource(R.string.wk_done_update)
        else -> stringResource(R.string.wk_calculate)
    }
    val description = stringResource(if (done) R.string.wk_cd_done else R.string.wk_cd_calculate)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    if (done) Brush.linearGradient(WorkoutDoneGradient)
                    else Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd))
                )
                .clickable(enabled = !state.isCalculatingBurn, role = Role.Button, onClick = onFinish)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isCalculatingBurn) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(
                    if (changed) Icons.Filled.Save else Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (changed || !state.isDone) {
            Text(
                stringResource(if (changed) R.string.wk_done_changed else R.string.wk_done_hint),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp)
            )
        }
    }
}

@Composable
private fun WorkoutMetric(
    label: String,
    value: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (active) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    Column(
        modifier = modifier.padding(horizontal = 7.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            text = value,
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 14.sp,
                maxFontSize = 24.sp,
                stepSize = 0.5.sp
            )
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(44.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f))
    )
}

@Composable
private fun WorkoutDayHeader(
    selectedDate: LocalDate,
    workoutCount: Int,
    isReordering: Boolean,
    onToggleReorder: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = workoutDateTitle(selectedDate),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = pluralStringResource(R.plurals.wk_workout_count, workoutCount, workoutCount),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (onToggleReorder != null) {
            Spacer(Modifier.width(10.dp))
            // A labeled button rather than a hidden long-press drag keeps reordering discoverable.
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(AppColors.Calorie.copy(alpha = if (isReordering) 1f else 0.13f))
                    .clickable(role = Role.Button, onClick = onToggleReorder)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val tint = if (isReordering) Color.White else AppColors.Calorie
                Icon(
                    if (isReordering) Icons.Filled.Check else Icons.Filled.SwapVert,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(if (isReordering) R.string.action_done else R.string.workout_reorder),
                    color = tint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/** Compact card used while reordering: large, labeled arrows instead of a drag-only gesture. */
@Composable
private fun WorkoutReorderCard(
    exercise: PlannedExercise,
    visual: ExerciseVisual,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    FudGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        padding = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    position.toString(),
                    color = AppColors.Calorie,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(26.dp)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                ) {
                    AnimatedExerciseImage(visual, Modifier.fillMaxSize(), animatesFrames = false)
                }
                Text(
                    ExerciseLocalization.name(exercise.itemId, exercise.name),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            WorkoutMoveButton(
                icon = Icons.Filled.KeyboardArrowUp,
                description = stringResource(R.string.workout_move_up_named, exercise.name),
                enabled = canMoveUp,
                onClick = onMoveUp
            )
            WorkoutMoveButton(
                icon = Icons.Filled.KeyboardArrowDown,
                description = stringResource(R.string.workout_move_down_named, exercise.name),
                enabled = canMoveDown,
                onClick = onMoveDown
            )
        }
    }
}

@Composable
private fun WorkoutMoveButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(AppColors.Calorie.copy(alpha = if (enabled) 0.14f else 0.05f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun WorkoutEmptyState(
    splitTitle: String,
    simpleLogging: Boolean,
    onAdd: () -> Unit,
    onAskCoach: () -> Unit,
    onStarter: (StarterRoutine) -> Unit,
    modifier: Modifier = Modifier
) {
    FudGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd),
        cornerRadius = 20.dp,
        padding = 14.dp
    ) {
        Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.wk_empty_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (simpleLogging) stringResource(R.string.wk_empty_hint_simple)
                    else stringResource(R.string.wk_empty_hint, splitTitle),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        // The first thing on an empty day: let Coach build today's routine.
        com.apoorvdarshan.calorietracker.ui.components.FudGlassPrimaryButton(
            text = stringResource(R.string.wk_ask_coach_routine),
            onClick = onAskCoach,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            height = 46.dp
        )
        if (simpleLogging) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.wk_starter_heading),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                StarterRoutine.entries.forEach { routine ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.Calorie.copy(alpha = 0.1f))
                            .clickable { onStarter(routine) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(stringResource(routine.titleRes), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(routine.detailRes),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Row gestures next to the card's visible buttons. A leading swipe marks the exercise done
 * (or not done again); a trailing swipe removes it from this day (with Undo).
 */
@Composable
private fun SwipeableWorkoutExerciseCard(
    exerciseId: UUID,
    finished: Boolean,
    onFinish: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val saveTriggerPx = with(density) { 120.dp.toPx() }
    val deleteTriggerPx = with(density) { 160.dp.toPx() }
    var offsetPx by remember(exerciseId) { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxSwipePx = with(density) { maxWidth.toPx() * 0.58f }
        Box(Modifier.fillMaxWidth()) {
            WorkoutExerciseSwipeBackground(offsetPx = offsetPx, finished = finished)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
                    .pointerInput(exerciseId, finished, maxSwipePx) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetPx = (offsetPx + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                            },
                            onDragEnd = {
                                val finalOffset = offsetPx
                                offsetPx = 0f
                                when {
                                    finalOffset <= -deleteTriggerPx -> onRemove()
                                    finalOffset >= saveTriggerPx -> onFinish()
                                }
                            },
                            onDragCancel = { offsetPx = 0f }
                        )
                    }
            ) {
                content()
                if (finished) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2E7D32))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(stringResource(R.string.wk_exercise_done), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.WorkoutExerciseSwipeBackground(offsetPx: Float, finished: Boolean) {
    if (offsetPx == 0f) {
        Box(Modifier.matchParentSize())
        return
    }

    val density = LocalDensity.current
    val trailing = offsetPx < 0f
    val revealWidth = with(density) { abs(offsetPx).toDp() }
    val background = if (trailing) Color(0xFFD32F2F) else Color(0xFF2E7D32)
    val icon = when {
        trailing -> Icons.Filled.DeleteOutline
        finished -> Icons.Filled.Close
        else -> Icons.Filled.Check
    }
    val label = when {
        trailing -> stringResource(R.string.action_delete)
        finished -> stringResource(R.string.wk_swipe_not_done)
        else -> stringResource(R.string.wk_swipe_finish)
    }

    Box(Modifier.matchParentSize().clip(RoundedCornerShape(24.dp))) {
        Box(
            modifier = Modifier
                .align(if (trailing) Alignment.CenterEnd else Alignment.CenterStart)
                .fillMaxHeight()
                .width(revealWidth)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                if (revealWidth >= 76.dp) {
                    Spacer(Modifier.height(4.dp))
                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    exercise: PlannedExercise,
    visual: ExerciseVisual,
    modifier: Modifier = Modifier,
    weightUnit: WorkoutWeightUnit,
    rpeScale: WorkoutRpeScale,
    simpleLogging: Boolean = false,
    editingDate: LocalDate,
    isSaved: Boolean,
    lastTimeSummary: String?,
    moveActions: List<CustomAccessibilityAction>,
    onOpen: () -> Unit,
    onShowHistory: () -> Unit,
    onShowWarmup: () -> Unit = {},
    onToggleSaved: () -> Unit,
    onRemove: () -> Unit,
    onSetCount: (Int) -> Unit,
    onWeight: (UUID, String) -> Unit,
    onReps: (UUID, String) -> Unit,
    onRpe: (UUID, String) -> Unit,
    onTimerAction: (ExerciseTimerAction) -> Unit,
    onEffort: (WorkoutIntensity) -> Unit,
    /** The set whose rest is counting down, when it belongs to this exercise. */
    restingSetId: UUID? = null,
    /** Null when rest is unavailable (rest timer off, or not today's log). */
    onStartRest: ((UUID) -> Unit)? = null
) {
    FudGlassSurface(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        padding = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .semantics { customActions = moveActions },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        .border(
                            0.7.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    AnimatedExerciseImage(visual, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        ExerciseLocalization.name(exercise.itemId, exercise.name),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(
                                ExerciseLocalization.labels(exercise.primaryMuscles)
                                    .ifBlank { ExerciseLocalization.label("Unspecified") }
                            )
                            append(" · ")
                            append(ExerciseLocalization.label(exercise.equipment))
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.57f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.wk_cd_open_instructions),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!exercise.isCardio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!lastTimeSummary.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.wk_last_time, lastTimeSummary),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (simpleLogging) {
                        // No history yet: say how to pick a first weight instead of leaving a blank.
                        Text(
                            stringResource(R.string.wk_first_weight_hint),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                            fontSize = 13.sp
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (!exercise.isCardio) {
                        Text(
                            stringResource(R.string.wk_warmup_button),
                            modifier = Modifier.clickable(onClick = onShowWarmup),
                            color = AppColors.Calorie,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        stringResource(R.string.wk_history),
                        modifier = Modifier.clickable(onClick = onShowHistory),
                        color = AppColors.Calorie,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!exercise.isCardio) {
                    if (simpleLogging) {
                        Text(
                            stringResource(R.string.wk_sets_label),
                            color = AppColors.Calorie,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Filled.Checklist, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onSetCount(exercise.sets.size - 1) },
                        enabled = exercise.sets.size > 1,
                        modifier = Modifier.size(if (simpleLogging) 44.dp else 34.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.wk_cd_remove_set), modifier = Modifier.size(18.dp))
                    }
                    Text(
                        pluralStringResource(R.plurals.wk_set_count, exercise.sets.size, exercise.sets.size),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (simpleLogging) 14.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(64.dp)
                    )
                    IconButton(
                        onClick = { onSetCount(exercise.sets.size + 1) },
                        enabled = exercise.sets.size < 12,
                        modifier = Modifier.size(if (simpleLogging) 44.dp else 34.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.wk_cd_add_set), modifier = Modifier.size(18.dp))
                    }
                } else {
                    // Cardio has no RPE fields, so effort is chosen directly (Moderate by default).
                    // The weighted box lets a long translation ellipsize instead of squeezing the buttons.
                    Box(Modifier.weight(1f)) {
                        WorkoutEffortChip(
                            effort = WorkoutBurnEstimator.timerIntensity(exercise, rpeScale),
                            onSelect = onEffort
                        )
                    }
                }
                WorkoutExerciseTimer(exercise, onTimerAction, showLabel = simpleLogging)
                IconButton(onClick = onToggleSaved, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(if (isSaved) R.string.wk_cd_unsave_exercise else R.string.wk_cd_save_exercise),
                        tint = if (isSaved) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.wk_cd_remove_exercise),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.82f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (!exercise.isCardio) Column {
                if (simpleLogging) WorkoutSetHeader(weightUnit = weightUnit, hasRest = onStartRest != null)
                exercise.sets.forEachIndexed { index, set ->
                    key(editingDate, set.id) {
                        WorkoutSetRow(
                            index = index,
                            set = set,
                            weightUnit = weightUnit,
                            rpeScale = set.rpeScale ?: rpeScale,
                            onWeight = { onWeight(set.id, it) },
                            onReps = { onReps(set.id, it) },
                            onRpe = { onRpe(set.id, it) },
                            simpleLogging = simpleLogging,
                            isResting = restingSetId == set.id,
                            onStartRest = onStartRest?.let { start -> { start(set.id) } }
                        )
                    }
                    if (index < exercise.sets.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 59.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.6.dp
                        )
                    }
                }
            }
        }
    }
}

internal val WorkoutIntensity.labelRes: Int
    get() = when (this) {
        WorkoutIntensity.LIGHT -> R.string.workout_effort_light
        WorkoutIntensity.MODERATE -> R.string.workout_effort_moderate
        WorkoutIntensity.VIGOROUS -> R.string.workout_effort_vigorous
    }

@Composable
private fun WorkoutEffortChip(
    effort: WorkoutIntensity,
    onSelect: (WorkoutIntensity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val effortLabel = stringResource(effort.labelRes)
    val chipLabel = stringResource(R.string.workout_effort_value, effortLabel)
    Box {
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(AppColors.Calorie.copy(alpha = 0.12f))
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                chipLabel,
                color = AppColors.Calorie,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(18.dp))
        }
        SheetGlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, menuWidth = 200.dp) {
            WorkoutIntensity.entries.forEach { option ->
                SheetGlassDropdownMenuItem(
                    label = stringResource(option.labelRes),
                    selected = option == effort,
                    reserveSelectionSlot = true,
                    onClick = {
                        expanded = false
                        if (option != effort) onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun WorkoutExerciseTimer(
    exercise: PlannedExercise,
    onAction: (ExerciseTimerAction) -> Unit,
    /** Simple view names the stopwatch "Duration" before it starts. */
    showLabel: Boolean = false
) {
    // Choosing an effort first stores an empty timer; it still reads as "Start timer".
    val timer = exercise.timer?.takeIf { it.hasStarted }
    var now by remember { mutableStateOf(Instant.now()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ExerciseTimerAction?>(null) }
    LaunchedEffect(timer) {
        now = Instant.now()
        while (timer?.isRunning == true) {
            delay(1_000)
            now = Instant.now()
        }
    }
    val elapsed = (timer?.elapsedSeconds(now) ?: 0.0).toLong()
    val time = timerClockText(elapsed)
    val timerDescription = if (timer == null) {
        stringResource(R.string.wk_cd_start_timer, exercise.name)
    } else {
        stringResource(
            R.string.wk_cd_timer_options,
            exercise.name,
            time,
            stringResource(
                if (timer.isRunning) R.string.wk_timer_running
                else if (timer.isSaved) R.string.wk_timer_saved
                else R.string.wk_timer_paused
            )
        )
    }
    Box {
        IconButton(
            onClick = { if (timer == null) onAction(ExerciseTimerAction.START) else menuExpanded = true },
            modifier = Modifier.size(48.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = timerDescription,
                    tint = if (timer == null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f) else AppColors.Calorie,
                    modifier = Modifier.size(19.dp)
                )
                if (timer != null) {
                    Text(time, color = AppColors.Calorie, fontSize = if (showLabel) 10.sp else 9.sp, maxLines = 1)
                } else if (showLabel) {
                    Text(
                        stringResource(R.string.wk_timer_label),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
        SheetGlassDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, menuWidth = 200.dp) {
            SheetGlassDropdownMenuItem(label = stringResource(if (timer?.isRunning == true) R.string.wk_timer_pause else R.string.wk_timer_resume), onClick = {
                menuExpanded = false
                onAction(if (timer?.isRunning == true) ExerciseTimerAction.PAUSE else ExerciseTimerAction.RESUME)
            })
            if (timer?.isSaved != true) SheetGlassDropdownMenuItem(label = stringResource(R.string.wk_timer_stop_save), onClick = {
                menuExpanded = false
                onAction(ExerciseTimerAction.STOP)
            })
            SheetGlassDropdownMenuItem(label = stringResource(R.string.wk_timer_restart), onClick = {
                menuExpanded = false
                pendingAction = ExerciseTimerAction.RESTART
            })
            SheetGlassDropdownMenuItem(label = stringResource(R.string.wk_timer_discard), onClick = {
                menuExpanded = false
                pendingAction = ExerciseTimerAction.DISCARD
            })
        }
    }
    pendingAction?.let { action ->
        FudGlassDialog(onDismissRequest = { pendingAction = null }) {
            Text(
                stringResource(if (action == ExerciseTimerAction.RESTART) R.string.wk_timer_restart_title else R.string.wk_timer_discard_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.wk_timer_clear_message, exercise.name), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FudGlassTextButton(text = stringResource(R.string.action_cancel), onClick = { pendingAction = null })
                FudGlassTextButton(text = stringResource(if (action == ExerciseTimerAction.RESTART) R.string.wk_restart else R.string.action_discard), onClick = {
                    pendingAction = null
                    onAction(action)
                })
            }
        }
    }
}

private fun timerClockText(elapsed: Long): String =
    if (elapsed >= 3_600) {
        String.format(Locale.US, "%d:%02d:%02d", elapsed / 3_600, elapsed / 60 % 60, elapsed % 60)
    } else {
        String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
    }

@Composable
internal fun WorkoutSetRow(
    index: Int,
    set: PlannedSet,
    weightUnit: WorkoutWeightUnit,
    rpeScale: WorkoutRpeScale,
    onWeight: (String) -> Unit,
    onReps: (String) -> Unit,
    onRpe: (String) -> Unit,
    simpleLogging: Boolean = false,
    isResting: Boolean = false,
    onStartRest: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            stringResource(R.string.wk_set_number, index + 1),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            fontSize = if (simpleLogging) 14.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.width(52.dp)
        )
        WorkoutSetField(
            value = set.displayWeight(weightUnit),
            editingKey = weightUnit,
            sanitize = { proposed, _ -> WorkoutSetInput.weight(proposed) },
            onValueChange = onWeight,
            placeholder = weightUnit.storageValue,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
        WorkoutSetField(
            value = set.reps,
            sanitize = { proposed, _ -> WorkoutSetInput.reps(proposed) },
            onValueChange = onReps,
            placeholder = stringResource(R.string.wk_placeholder_reps),
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        if (simpleLogging) {
            WorkoutSetFeelPicker(
                setNumber = index + 1,
                feel = WorkoutSetFeel.from(set.rpe, rpeScale),
                onSelect = { feel ->
                    onRpe(feel?.rpe(rpeScale).orEmpty())
                    // Rating a finished set is the beginner's "done": start its rest too.
                    if (feel != null && set.reps.isNotBlank()) onStartRest?.invoke()
                },
                modifier = Modifier.weight(1f)
            )
        } else {
        val rpeHelp = stringResource(when (rpeScale) {
            WorkoutRpeScale.STRENGTH -> R.string.workout_rpe_help_strength
            WorkoutRpeScale.CR10 -> R.string.workout_rpe_help_cr10
            WorkoutRpeScale.BORG -> R.string.workout_rpe_help_borg
        })
        WorkoutSetField(
            value = set.rpe,
            editingKey = rpeScale,
            sanitize = rpeScale::sanitize,
            onValueChange = onRpe,
            placeholder = stringResource(R.string.wk_placeholder_rpe),
            keyboardType = if (rpeScale.allowsDecimalInput) KeyboardType.Decimal else KeyboardType.Number,
            modifier = Modifier.weight(1f).semantics { contentDescription = rpeHelp },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
        }
        if (onStartRest != null) {
            if (simpleLogging) {
                WorkoutSetDoneButton(setNumber = index + 1, done = set.reps.isNotBlank(), isResting = isResting, onClick = onStartRest)
            } else {
                WorkoutRestSetButton(setNumber = index + 1, isResting = isResting, onClick = onStartRest)
            }
        }
    }
}

/** Column titles over the set rows, so "Set 1  60  10" reads as weight and reps. */
@Composable
private fun WorkoutSetHeader(weightUnit: WorkoutWeightUnit, hasRest: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        WorkoutSetHeaderTitle(stringResource(R.string.wk_header_set), color, Modifier.width(52.dp))
        WorkoutSetHeaderTitle(stringResource(R.string.wk_header_weight, weightUnit.storageValue), color, Modifier.weight(1f))
        WorkoutSetHeaderTitle(stringResource(R.string.wk_header_reps), color, Modifier.weight(1f))
        WorkoutSetHeaderTitle(stringResource(R.string.wk_header_effort), color, Modifier.weight(1f))
        if (hasRest) WorkoutSetHeaderTitle(stringResource(R.string.wk_header_done), color, Modifier.width(WorkoutSetDoneButtonWidth))
    }
}

@Composable
private fun WorkoutSetHeaderTitle(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/** Easy / Good / Hard instead of a numeric RPE; the choice is stored as an RPE (see [WorkoutSetFeel]). */
@Composable
private fun WorkoutSetFeelPicker(
    setNumber: Int,
    feel: WorkoutSetFeel?,
    onSelect: (WorkoutSetFeel?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = feel?.let { stringResource(it.labelRes) }
    val description = stringResource(R.string.wk_cd_set_feel, setNumber, label ?: stringResource(R.string.wk_feel_question))
    val tint = when (feel) {
        null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
        WorkoutSetFeel.EASY -> Color(0xFF2E7D32)
        WorkoutSetFeel.GOOD -> AppColors.Calorie
        WorkoutSetFeel.HARD -> Color(0xFFD32F2F)
    }
    Box(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = if (feel == null) 0.08f else 0.16f))
                .clickable(role = Role.DropdownList) { expanded = true }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Text(
                label ?: stringResource(R.string.wk_feel_placeholder),
                color = tint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SheetGlassDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, menuWidth = 280.dp) {
            WorkoutSetFeel.entries.forEach { option ->
                SheetGlassDropdownMenuItem(
                    label = stringResource(option.labelRes) + " · " + stringResource(option.detailRes),
                    selected = option == feel,
                    reserveSelectionSlot = true,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
            if (feel != null) {
                SheetGlassDropdownMenuItem(
                    label = stringResource(R.string.wk_feel_clear),
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
            }
        }
    }
}

private val WorkoutSetFeel.labelRes: Int
    get() = when (this) {
        WorkoutSetFeel.EASY -> R.string.wk_feel_easy
        WorkoutSetFeel.GOOD -> R.string.wk_feel_good
        WorkoutSetFeel.HARD -> R.string.wk_feel_hard
    }

private val WorkoutSetFeel.detailRes: Int
    get() = when (this) {
        WorkoutSetFeel.EASY -> R.string.wk_feel_easy_detail
        WorkoutSetFeel.GOOD -> R.string.wk_feel_good_detail
        WorkoutSetFeel.HARD -> R.string.wk_feel_hard_detail
    }

private class SessionExtras(val kind: ExerciseRoles.Kind, val picks: List<ExerciseRoles.Pick>)

/**
 * A warm-up for days with lifts planned but nothing done yet, or a cool-down once the session
 * was hard (3+ sets rated hard, or 6+ logged leg sets). Null when the day already has one.
 */
private fun sessionExtrasFor(exercises: List<PlannedExercise>, gentle: Boolean, rpeScale: WorkoutRpeScale): SessionExtras? {
    val lifts = exercises.filter { !it.isCardio }
    if (lifts.isEmpty()) return null
    val muscles = lifts.flatMap { it.primaryMuscles }.map { it.lowercase() }
    val performed = lifts.flatMap { exercise -> exercise.sets.filter { it.reps.isNotBlank() }.map { exercise to it } }
    val ids = exercises.map { it.itemId }.toSet()
    if (performed.isEmpty()) {
        val picks = ExerciseRoles.warmup(muscles, gentle)
        return if (picks.any { it.id in ids }) null else SessionExtras(ExerciseRoles.Kind.WARMUP, picks)
    }
    val hardSets = performed.count { (_, set) -> WorkoutSetFeel.from(set.rpe, set.rpeScale ?: rpeScale) == WorkoutSetFeel.HARD }
    val legSets = performed.count { (exercise, _) ->
        exercise.primaryMuscles.any { it.lowercase() in setOf("quadriceps", "hamstrings", "glutes") }
    }
    if (hardSets < 3 && legSets < 6) return null
    val picks = ExerciseRoles.cooldown(muscles, heavyLegs = legSets >= 6)
    return if (picks.any { it.id in ids && it.id != "Walking_Treadmill" }) null else SessionExtras(ExerciseRoles.Kind.COOLDOWN, picks)
}

@Composable
private fun WorkoutSessionExtrasCard(
    kind: ExerciseRoles.Kind,
    names: List<String>,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val warmup = kind == ExerciseRoles.Kind.WARMUP
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (warmup) R.string.wk_extras_warmup_title else R.string.wk_extras_cooldown_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wk_extras_dismiss), modifier = Modifier.size(18.dp))
                }
            }
            Text(
                stringResource(if (warmup) R.string.wk_extras_warmup_body else R.string.wk_extras_cooldown_body),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            names.forEachIndexed { index, name ->
                Text(
                    "${index + 1}. $name",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            FudGlassTextButton(
                text = stringResource(if (warmup) R.string.wk_extras_warmup_add else R.string.wk_extras_cooldown_add),
                onClick = onAdd
            )
        }
    }
}

/** One-time card explaining set, reps, weight, effort and the two timers. */
@Composable
private fun WorkoutLoggingGuide(onDismiss: () -> Unit) {
    FudGlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.wk_guide_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            listOf(
                R.string.wk_guide_set,
                R.string.wk_guide_reps,
                R.string.wk_guide_weight,
                R.string.wk_guide_effort,
                R.string.wk_guide_rest
            ).forEach { line ->
                Text(
                    "• " + stringResource(line),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FudGlassTextButton(text = stringResource(R.string.wk_guide_dismiss), onClick = onDismiss)
            }
        }
    }
}

@Composable
internal fun WorkoutSetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Next,
    editingKey: Any = Unit,
    sanitize: (String, String) -> String = { proposed, _ -> proposed }
) {
    val editor = remember(editingKey) { WorkoutFieldEditor(value) }
    LaunchedEffect(value, editor) { editor.receive(value) }
    val shape = RoundedCornerShape(11.dp)
    BasicTextField(
        value = editor.value,
        onValueChange = { proposed ->
            editor.edit(proposed, sanitize)?.let(onValueChange)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(AppColors.Calorie),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        modifier = modifier
            .onFocusChanged { editor.setFocused(it.isFocused) }
            .height(39.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
            .padding(horizontal = 7.dp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (editor.value.text.isEmpty()) {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun WorkoutWeekStrip(
    selectedDate: LocalDate,
    workoutCounts: Map<LocalDate, Int>,
    doneDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    weekStartsOnMonday: Boolean,
    modifier: Modifier = Modifier
) {
    val firstDay = remember(weekStartsOnMonday) {
        if (weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    }
    val today = remember { LocalDate.now() }
    val currentWeekStart = remember(today, firstDay) { startOfWeek(today, firstDay) }
    val targetWeek = remember(selectedDate, currentWeekStart, firstDay) {
        val selectedStart = startOfWeek(selectedDate, firstDay)
        val difference = ChronoUnit.WEEKS.between(currentWeekStart, selectedStart).toInt()
        (CURRENT_WORKOUT_WEEK + difference).coerceIn(0, CURRENT_WORKOUT_WEEK)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = targetWeek)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    LaunchedEffect(targetWeek) {
        if (state.firstVisibleItemIndex != targetWeek) state.animateScrollToItem(targetWeek)
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pageWidth = maxWidth
        LazyRow(state = state, flingBehavior = fling, modifier = Modifier.fillMaxWidth()) {
            items((0 until WORKOUT_WEEKS).toList()) { weekIndex ->
                val weekStart = currentWeekStart.plusWeeks((weekIndex - CURRENT_WORKOUT_WEEK).toLong())
                Row(Modifier.width(pageWidth)) {
                    repeat(7) { dayIndex ->
                        val date = weekStart.plusDays(dayIndex.toLong())
                        WorkoutDayTile(
                            date = date,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            count = workoutCounts[date] ?: 0,
                            done = date in doneDates,
                            // The visible days in the current week are all
                            // selectable for planning, including tomorrow.
                            // Horizontal forward swipes remain capped at today.
                            enabled = true,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDayTile(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    count: Int,
    done: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.32f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale),
            color = if (isSelected) AppColors.Calorie else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    when {
                        isSelected -> Modifier
                            .shadow(6.dp, CircleShape, ambientColor = AppColors.Calorie.copy(alpha = 0.3f))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd)))
                        isToday -> Modifier.border(1.5.dp, AppColors.Calorie.copy(alpha = 0.35f), CircleShape)
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = when {
                    isSelected -> Color.White
                    isToday -> AppColors.Calorie
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.wk_cd_day_done),
                tint = WorkoutDoneGreen,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (count > 0) AppColors.Calorie else Color.Transparent)
            )
        }
    }
}

private val WorkoutDoneGreen = Color(0xFF2FB457)
private val WorkoutDoneGradient = listOf(Color(0xFF45D46F), Color(0xFF1F9A45))

private fun Modifier.workoutDaySwipe(
    selectedDate: LocalDate,
    onMove: (Long) -> Unit
): Modifier = pointerInput(selectedDate) {
    var accumulated = 0f
    val threshold = 80.dp.toPx()
    detectHorizontalDragGestures(
        onDragStart = { accumulated = 0f },
        onDragCancel = { accumulated = 0f },
        onHorizontalDrag = { change, amount ->
            accumulated += amount
            change.consume()
        },
        onDragEnd = {
            when {
                accumulated > threshold -> onMove(-1L)
                accumulated < -threshold && selectedDate.isBefore(LocalDate.now()) -> onMove(1L)
            }
            accumulated = 0f
        }
    )
}

private fun startOfWeek(date: LocalDate, firstDay: DayOfWeek): LocalDate {
    val daysBack = ((date.dayOfWeek.value - firstDay.value) + 7) % 7
    return date.minusDays(daysBack.toLong())
}

/**
 * Non-composable fallback kept for callers outside Compose scope; its Today/Tomorrow/Yesterday
 * words are English. Composables should use [workoutDateTitle], which is fully localized.
 */
internal fun selectedDateTitle(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> {
        val locale = Locale.getDefault()
        date.format(
            DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"),
                locale
            )
        )
    }
}

/**
 * Reorder mode belongs to the day it was started on. It ends when another day is
 * selected or fewer than two exercises remain, so returning to that day (or adding
 * an exercise back) shows the normal cards instead of silently resuming it.
 */
internal fun retainedReorderDateKey(reorderDateKey: String?, selectedDateKey: String, exerciseCount: Int): String? =
    reorderDateKey?.takeIf { it == selectedDateKey && exerciseCount > 1 }

/**
 * Swiping a card to finish it with sets still empty: fill weight and reps in a couple of taps
 * (empty sets start from the set above, or 10 reps), then it is marked done.
 */
@Composable
private fun QuickFinishDialog(
    exercise: PlannedExercise,
    weightUnit: WorkoutWeightUnit,
    lastTimeSummary: String?,
    onDismiss: () -> Unit,
    onSave: (List<WorkoutsViewModel.QuickSet>) -> Unit
) {
    val rows = remember(exercise.id) {
        var previous: PlannedSet? = null
        exercise.sets.map { set ->
            val weight = set.displayWeight(weightUnit).ifBlank { previous?.displayWeight(weightUnit).orEmpty() }
            val reps = set.reps.takeIf { (it.toIntOrNull() ?: 0) > 0 } ?: previous?.reps?.takeIf { (it.toIntOrNull() ?: 0) > 0 } ?: "10"
            previous = set.copy(weight = weight, weightUnit = weightUnit, reps = reps)
            androidx.compose.runtime.mutableStateListOf(set.id.toString(), weight, reps)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wk_quick_finish_title, exercise.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                lastTimeSummary?.let {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                rows.forEachIndexed { index, row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = row[1],
                            onValueChange = { row[1] = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                            label = { Text(weightUnit.name.lowercase()) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { row[2] = ((row[2].toIntOrNull() ?: 0) - 1).coerceAtLeast(1).toString() }) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }
                        Text(stringResource(R.string.wk_quick_reps, row[2]), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { row[2] = ((row[2].toIntOrNull() ?: 0) + 1).coerceAtMost(999).toString() }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(rows.map { WorkoutsViewModel.QuickSet(UUID.fromString(it[0]), it[1].replace(',', '.'), it[2]) })
            }) { Text(stringResource(R.string.wk_quick_finish_save), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun WorkoutLoadWarning(load: com.apoorvdarshan.calorietracker.models.WorkoutLoadCheck.Result, onDismiss: () -> Unit) {
    val amber = Color(0xFFB26A00)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(amber.copy(alpha = 0.12f))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.wk_load_title), color = amber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                if (load.unrealisticWeight) stringResource(R.string.wk_load_weight)
                else stringResource(R.string.wk_load_body, load.minutes, load.sets),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.wk_load_dismiss), tint = amber, modifier = Modifier.size(18.dp))
        }
    }
}

/** Warm-up sets up to the working weight and, for a barbell, the plates on each side. */
@Composable
private fun WarmupPlatesDialog(exercise: PlannedExercise, unit: WorkoutWeightUnit, onDismiss: () -> Unit) {
    val heaviest = exercise.sets.mapNotNull { it.displayWeight(unit).replace(',', '.').toDoubleOrNull() }.maxOrNull()
    var target by remember(exercise.id) { mutableStateOf(heaviest?.let { formatKg(it) }.orEmpty()) }
    val barbell = com.apoorvdarshan.calorietracker.models.PantryMatching.key(exercise.equipment + " " + exercise.name)
        .let { it.contains("barra") || it.contains("barbell") }
    val weight = target.replace(',', '.').toDoubleOrNull()
    val unitLabel = unit.storageValue
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wk_warmup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { Text(stringResource(R.string.wk_warmup_target, unitLabel)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                if (weight != null && weight > 0) {
                    val ramp = com.apoorvdarshan.calorietracker.models.WarmupPlates.warmup(weight, unit, barbell)
                    Text(stringResource(R.string.wk_warmup_sets), fontWeight = FontWeight.Bold)
                    if (ramp.isEmpty()) Text(stringResource(R.string.wk_warmup_none))
                    ramp.forEach { set ->
                        Text("${set.percent} %  ·  ${formatKg(set.weight)} $unitLabel × ${set.reps}" + platesSuffix(set.weight, unit, barbell))
                    }
                    Text(
                        stringResource(R.string.wk_warmup_work, formatKg(weight), unitLabel) + platesSuffix(weight, unit, barbell),
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Calorie
                    )
                    if (barbell) {
                        Text(
                            stringResource(R.string.wk_plates_note, formatKg(com.apoorvdarshan.calorietracker.models.WarmupPlates.barWeight(unit)), unitLabel),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_button_ok)) } }
    )
}

@Composable
private fun platesSuffix(weight: Double, unit: WorkoutWeightUnit, barbell: Boolean): String {
    if (!barbell) return ""
    val loading = com.apoorvdarshan.calorietracker.models.WarmupPlates.plates(weight, unit)
    if (loading.perSide.isEmpty()) return "  ·  " + stringResource(R.string.wk_plates_empty_bar)
    val text = loading.perSide.joinToString(" + ") { formatKg(it) }
    return "  ·  " + stringResource(R.string.wk_plates_per_side, text)
}

private fun formatKg(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
