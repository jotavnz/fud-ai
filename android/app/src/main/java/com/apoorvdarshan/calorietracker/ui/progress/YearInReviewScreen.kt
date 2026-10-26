package com.apoorvdarshan.calorietracker.ui.progress

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.text.format.DateFormat
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.os.ConfigurationCompat
import androidx.core.view.WindowCompat
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.data.ExerciseLocalization
import com.apoorvdarshan.calorietracker.data.ExerciseRepository
import com.apoorvdarshan.calorietracker.models.EnergyUnit
import com.apoorvdarshan.calorietracker.models.VolumeEquivalenceKind
import com.apoorvdarshan.calorietracker.models.WaterUnit
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.models.YearInReview
import com.apoorvdarshan.calorietracker.models.YearInReviewCalculator
import com.apoorvdarshan.calorietracker.models.YearInReviewPeriod
import com.apoorvdarshan.calorietracker.ui.components.LocalEnergyUnit
import com.apoorvdarshan.calorietracker.ui.components.energyText
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import com.apoorvdarshan.calorietracker.ui.theme.AppThemeColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Saved-state key for the All time period (calendar years are saved as themselves). */
private const val ALL_TIME_KEY = 0
private const val POUNDS_PER_KILOGRAM = 2.20462
private const val US_GALLON_MILLILITERS = 3_785.411_784

/** The year Progress offers to review: last year through January, then the current one. */
internal fun defaultReviewYear(today: LocalDate = LocalDate.now()): Int =
    if (today.monthValue == 1) today.year - 1 else today.year

/**
 * Full-screen "Year in review" over the whole app (bottom bar included), opened from
 * the Progress tab. System back and the top-left arrow close it.
 */
@Composable
internal fun YearInReviewDialog(container: AppContainer, initialYear: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        val view = LocalView.current
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
        YearInReviewScreen(container = container, initialYear = initialYear, onBack = onDismiss)
    }
}

@Composable
fun YearInReviewScreen(container: AppContainer, initialYear: Int, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val energyUnit = LocalEnergyUnit.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val format = remember(locale) { ReviewFormat(locale) }

    val foods by container.foodRepository.entries.collectAsState(initial = null)
    val waters by container.waterRepository.entries.collectAsState(initial = null)
    val fasts by container.fastingRepository.sessions.collectAsState(initial = null)
    val weights by container.weightRepository.entries.collectAsState(initial = null)
    val bodyFats by container.bodyFatRepository.entries.collectAsState(initial = null)
    val workoutState by container.workoutRepository.state.collectAsState(initial = null)
    val imported by container.importedHealthWorkoutRepository.workouts.collectAsState(initial = null)
    val weightUnitRaw by container.prefs.weightUnit.collectAsState(initial = null)
    val waterUnit by container.prefs.waterUnit.collectAsState(initial = WaterUnit.Default)
    val useMetric = weightUnitRaw != "lbs"

    var periodKey by rememberSaveable { mutableIntStateOf(initialYear) }
    val period: YearInReviewPeriod =
        if (periodKey == ALL_TIME_KEY) YearInReviewPeriod.AllTime else YearInReviewPeriod.Year(periodKey)
    val zone = remember { ZoneId.systemDefault() }
    val currentYear = remember { LocalDate.now(zone).year }

    var review by remember { mutableStateOf<YearInReview?>(null) }
    var years by remember { mutableStateOf(listOf(initialYear)) }
    LaunchedEffect(foods, waters, fasts, weights, bodyFats, workoutState, imported, weightUnitRaw, period) {
        val f = foods ?: return@LaunchedEffect
        val w = waters ?: return@LaunchedEffect
        val fa = fasts ?: return@LaunchedEffect
        val we = weights ?: return@LaunchedEffect
        val bf = bodyFats ?: return@LaunchedEffect
        val state = workoutState ?: return@LaunchedEffect
        val imp = imported ?: return@LaunchedEffect
        val unitRaw = weightUnitRaw ?: return@LaunchedEffect
        val (computed, available) = withContext(Dispatchers.Default) {
            // Loads the localized catalog so diary names show in the app language.
            runCatching { ExerciseRepository.get(context) }
            YearInReviewCalculator.compute(
                period = period,
                foodEntries = f,
                waterEntries = w,
                fastingSessions = fa,
                weightEntries = we,
                bodyFatEntries = bf,
                workoutState = state,
                importedWorkouts = imp,
                defaultWeightUnit = WorkoutWeightUnit.fromStorage(unitRaw),
                zone = zone,
                exerciseName = ExerciseLocalization::name
            ) to YearInReviewCalculator.availableYears(
                currentYear = currentYear,
                foodEntries = f,
                waterEntries = w,
                fastingSessions = fa,
                weightEntries = we,
                bodyFatEntries = bf,
                workoutState = state,
                importedWorkouts = imp,
                zone = zone
            )
        }
        review = computed
        years = (available + initialYear).distinct().sortedDescending()
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shown = review?.takeIf { it.period == period }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        ReviewTopBar(
            canShare = shown?.isEmpty == false,
            onBack = onBack,
            onShare = {
                val current = shown ?: return@ReviewTopBar
                shareReview(context, current, format, useMetric, energyUnit, waterUnit)
            }
        )
        PeriodSelector(
            years = years,
            selectedKey = periodKey,
            onSelect = { periodKey = it }
        )
        if (shown == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Calorie)
            }
        } else {
            ReviewContent(
                review = shown,
                currentYear = currentYear,
                format = format,
                useMetric = useMetric,
                energyUnit = energyUnit,
                waterUnit = waterUnit,
                isDark = isDark,
                locale = locale,
                onShare = { shareReview(context, shown, format, useMetric, energyUnit, waterUnit) }
            )
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────

@Composable
private fun ReviewTopBar(canShare: Boolean, onBack: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            stringResource(R.string.yr_title),
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onShare, enabled = canShare) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.yr_share_cd))
        }
    }
}

@Composable
private fun PeriodSelector(years: List<Int>, selectedKey: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        years.forEach { year ->
            PeriodPill(label = year.toString(), selected = selectedKey == year) { onSelect(year) }
        }
        PeriodPill(
            label = stringResource(R.string.yr_period_all_time),
            selected = selectedKey == ALL_TIME_KEY
        ) { onSelect(ALL_TIME_KEY) }
    }
}

@Composable
private fun PeriodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = if (selected) {
        Modifier.background(AppColors.CalorieGradient, shape)
    } else {
        Modifier.background(
            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
            shape
        )
    }
    Text(
        label,
        modifier = Modifier
            .clip(shape)
            .then(background)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
    )
}

// ── Story cards ─────────────────────────────────────────────────────

@Composable
private fun ReviewContent(
    review: YearInReview,
    currentYear: Int,
    format: ReviewFormat,
    useMetric: Boolean,
    energyUnit: EnergyUnit,
    waterUnit: WaterUnit,
    isDark: Boolean,
    locale: Locale,
    onShare: () -> Unit
) {
    val scroll = rememberScrollState()
    // Back to the top when the period changes, so each recap starts at its cover.
    LaunchedEffect(review.period) { scroll.scrollTo(0) }
    val weightUnitLabel = stringResource(if (useMetric) R.string.unit_kg else R.string.unit_lbs)
    val energyLabel = stringResource(energyUnit.symbolRes)
    fun weight(kg: Double, decimals: Boolean = true): String {
        val value = if (useMetric) kg else kg * POUNDS_PER_KILOGRAM
        return (if (decimals) format.decimal(value) else format.integer(value.roundToLong())) + " " + weightUnitLabel
    }
    fun energy(kcal: Long): String =
        format.integer(energyUnit.displayAmount(kcal.toDouble()).roundToLong()) + " " + energyLabel

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        var index = 0
        HeroCard(review, format, Modifier.appear(index++, review.period))

        if (review.isEmpty) {
            EmptyCard(Modifier.appear(index++, review.period))
        }

        review.training?.let { training ->
            SectionKicker(stringResource(R.string.yr_section_training), Modifier.appear(index++, review.period))
            if (training.totalVolumeKg > 0.0) {
                val accent = AppColors.CalorieStart
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.FitnessCenter, liftedLabel(review.period, currentYear), accent, isDark)
                    BigNumber(
                        stringResource(R.string.yr_value_with_unit, weightNumber(training.totalVolumeKg, useMetric, format), weightUnitLabel)
                    )
                    training.equivalence?.let { equivalence ->
                        val res = when (equivalence.kind) {
                            VolumeEquivalenceKind.ELEPHANT -> R.plurals.yr_equiv_elephant
                            VolumeEquivalenceKind.CAR -> R.plurals.yr_equiv_car
                            VolumeEquivalenceKind.GRAND_PIANO -> R.plurals.yr_equiv_grand_piano
                        }
                        Caption(pluralStringResource(res, equivalence.count, format.integer(equivalence.count.toLong())))
                    }
                }
            }

            StoryCard(AppThemeColor.INDIGO.start, isDark, Modifier.appear(index++, review.period)) {
                val tiles = listOf(
                    stringResource(R.string.yr_stat_workout_days) to format.integer(training.workoutDays.toLong()),
                    stringResource(R.string.yr_stat_exercises) to format.integer(training.distinctExercises.toLong()),
                    stringResource(R.string.yr_stat_sets) to format.integer(training.performedSets.toLong()),
                    stringResource(R.string.yr_stat_reps) to format.integer(training.totalReps.toLong())
                ).filter { (_, value) -> value != format.integer(0) }
                tiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, value) ->
                            StatTile(label, value, AppThemeColor.INDIGO.start, isDark, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (training.totalVolumeKg <= 0.0 && training.performedSets > 0) {
                    Caption(stringResource(R.string.yr_lifted_no_load))
                }
            }

            if (training.longestStreakDays >= 2) {
                val accent = AppThemeColor.CORAL.start
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.Whatshot, stringResource(R.string.yr_streak_label), accent, isDark)
                    BigNumber(
                        pluralStringResource(
                            R.plurals.yr_days_count,
                            training.longestStreakDays,
                            format.integer(training.longestStreakDays.toLong())
                        )
                    )
                    Caption(stringResource(R.string.yr_streak_caption))
                }
            }

            training.favoriteMuscle?.let { muscle ->
                val accent = AppThemeColor.PURPLE.start
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.EmojiEvents, stringResource(R.string.yr_favorite_muscle_label), accent, isDark)
                    BigNumber(ExerciseLocalization.label(muscle))
                    Caption(
                        pluralStringResource(
                            R.plurals.yr_favorite_muscle_sets,
                            training.favoriteMuscleSets,
                            format.integer(training.favoriteMuscleSets.toLong())
                        )
                    )
                }
            }

            if (training.topByVolume.isNotEmpty()) {
                val accent = AppColors.CalorieStart
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.FitnessCenter, stringResource(R.string.yr_top_volume_title), accent, isDark)
                    Subtitle(stringResource(R.string.yr_top_volume_subtitle))
                    training.topByVolume.forEachIndexed { rank, exercise ->
                        RankedRow(rank + 1, exercise.name, weight(exercise.volumeKg, decimals = false), accent, isDark)
                    }
                }
            }

            if (training.topByFrequency.isNotEmpty()) {
                val accent = AppThemeColor.TEAL.start
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.Repeat, stringResource(R.string.yr_top_frequency_title), accent, isDark)
                    Subtitle(stringResource(R.string.yr_top_frequency_subtitle))
                    training.topByFrequency.forEachIndexed { rank, exercise ->
                        RankedRow(
                            rank + 1,
                            exercise.name,
                            pluralStringResource(R.plurals.yr_days_count, exercise.days, format.integer(exercise.days.toLong())),
                            accent,
                            isDark
                        )
                    }
                }
            }

            if (training.personalBests.isNotEmpty()) {
                val accent = AppThemeColor.ORANGE.start
                val dateFormatter = remember(locale, review.period) {
                    val skeleton = if (review.period is YearInReviewPeriod.Year) "MMMd" else "yMMMd"
                    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                }
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.EmojiEvents, stringResource(R.string.yr_personal_bests_title), accent, isDark)
                    Subtitle(stringResource(R.string.yr_personal_bests_subtitle))
                    training.personalBests.forEachIndexed { rank, best ->
                        RankedRow(
                            rank + 1,
                            best.name,
                            weight(best.loadKg),
                            accent,
                            isDark,
                            detail = dateFormatter.format(best.date)
                        )
                    }
                }
            }

            if (training.totalBurnCalories > 0) {
                val accent = AppThemeColor.RED.start
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(
                        Icons.Filled.LocalFireDepartment,
                        stringResource(energyUnit.pick(R.string.yr_burn_label, R.string.yr_burn_label_kj)),
                        accent,
                        isDark
                    )
                    BigNumber(energy(training.totalBurnCalories))
                    if (training.loggedBurnCalories > 0 && training.importedBurnCalories > 0) {
                        Caption(stringResource(R.string.yr_burn_logged, energy(training.loggedBurnCalories)))
                        Caption(stringResource(R.string.yr_burn_imported, energy(training.importedBurnCalories)))
                    }
                    if (training.importedWorkouts > 0) {
                        Subtitle(
                            pluralStringResource(
                                R.plurals.yr_imported_workouts,
                                training.importedWorkouts,
                                format.integer(training.importedWorkouts.toLong()),
                                format.integer(training.importedMinutes)
                            )
                        )
                    }
                }
            }
        }

        val hasNutrition = review.nutrition != null || review.hydration != null || review.fasting != null
        if (hasNutrition) {
            SectionKicker(stringResource(R.string.yr_section_nutrition), Modifier.appear(index++, review.period))
        }
        review.nutrition?.let { nutrition ->
            val accent = AppThemeColor.GREEN.start
            StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                CardKicker(Icons.Filled.Restaurant, stringResource(R.string.yr_eaten_label), accent, isDark)
                BigNumber(energy(nutrition.totalCalories))
                Caption(
                    pluralStringResource(
                        R.plurals.yr_eaten_days,
                        nutrition.loggedDays,
                        format.integer(nutrition.loggedDays.toLong())
                    )
                )
                Subtitle(stringResource(R.string.yr_eaten_average, energyText(nutrition.averageDailyCalories)))
            }

            val macroAccent = AppThemeColor.SKY_CYAN.start
            StoryCard(macroAccent, isDark, Modifier.appear(index++, review.period)) {
                CardKicker(Icons.Filled.AutoAwesome, stringResource(R.string.yr_macros_title), macroAccent, isDark)
                val macros = buildList {
                    add(stringResource(R.string.macro_protein) to nutrition.proteinGrams)
                    add(stringResource(R.string.macro_carbs) to nutrition.carbsGrams)
                    add(stringResource(R.string.macro_fat) to nutrition.fatGrams)
                    nutrition.fiberGrams?.let { add(stringResource(R.string.nutrition_label_fiber) to it) }
                }
                macros.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, grams) ->
                            StatTile(
                                label,
                                stringResource(R.string.yr_grams_value, format.integer(grams.roundToLong())),
                                macroAccent,
                                isDark,
                                Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (nutrition.topFoods.isNotEmpty()) {
                val foodAccent = AppThemeColor.LIME.start
                StoryCard(foodAccent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.Restaurant, stringResource(R.string.yr_top_foods_title), foodAccent, isDark)
                    Subtitle(stringResource(R.string.yr_top_foods_subtitle))
                    nutrition.topFoods.forEachIndexed { rank, food ->
                        RankedRow(
                            rank + 1,
                            food.name,
                            pluralStringResource(R.plurals.yr_times_logged, food.count, format.integer(food.count.toLong())),
                            foodAccent,
                            isDark
                        )
                    }
                }
            }
        }

        review.hydration?.let { hydration ->
            val accent = AppThemeColor.BLUE.start
            StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                CardKicker(Icons.Filled.WaterDrop, stringResource(R.string.yr_water_label), accent, isDark)
                BigNumber(waterText(hydration.totalMilliliters, waterUnit, format))
                Caption(pluralStringResource(R.plurals.yr_water_days, hydration.days, format.integer(hydration.days.toLong())))
            }
        }

        review.fasting?.let { fasting ->
            val accent = AppThemeColor.LAVENDER.start
            StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                CardKicker(Icons.Filled.Timer, stringResource(R.string.yr_fasting_label), accent, isDark)
                BigNumber(stringResource(R.string.yr_hours_value, format.integer(fasting.totalHours.roundToLong())))
                Caption(
                    pluralStringResource(
                        R.plurals.yr_fasts_completed,
                        fasting.completedFasts,
                        format.integer(fasting.completedFasts.toLong())
                    )
                )
                if (fasting.goalsReached > 0) {
                    Subtitle(
                        pluralStringResource(
                            R.plurals.yr_fast_goals,
                            fasting.goalsReached,
                            format.integer(fasting.goalsReached.toLong())
                        )
                    )
                }
                Subtitle(stringResource(R.string.yr_fast_longest, format.decimal(fasting.longestHours)))
            }
        }

        review.body?.let { body ->
            SectionKicker(stringResource(R.string.yr_section_body), Modifier.appear(index++, review.period))
            if (body.firstWeightKg != null && body.lastWeightKg != null) {
                val accent = AppThemeColor.MINT.start
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    val change = body.weightChangeKg
                    CardKicker(
                        Icons.Filled.MonitorWeight,
                        stringResource(if (change != null) R.string.yr_weight_label else R.string.yr_weight_latest_label),
                        accent,
                        isDark
                    )
                    if (change != null) {
                        val shownChange = if (useMetric) change else change * POUNDS_PER_KILOGRAM
                        BigNumber(stringResource(R.string.yr_value_with_unit, format.signed(shownChange), weightUnitLabel))
                        Caption(stringResource(R.string.yr_weight_from_to, weight(body.firstWeightKg), weight(body.lastWeightKg)))
                    } else {
                        BigNumber(weight(body.lastWeightKg))
                    }
                    Subtitle(pluralStringResource(R.plurals.yr_weigh_ins, body.weighIns, format.integer(body.weighIns.toLong())))
                }
            }
            val fatChange = body.bodyFatChangeFraction
            if (fatChange != null && body.firstBodyFatFraction != null && body.lastBodyFatFraction != null) {
                val accent = AppThemeColor.GREEN.start
                StoryCard(accent, isDark, Modifier.appear(index++, review.period)) {
                    CardKicker(Icons.Filled.MonitorWeight, stringResource(R.string.yr_body_fat_label), accent, isDark)
                    BigNumber(stringResource(R.string.yr_percent_value, format.signed(fatChange * 100)))
                    Caption(
                        stringResource(
                            R.string.yr_weight_from_to,
                            stringResource(R.string.yr_percent_value, format.decimal(body.firstBodyFatFraction * 100)),
                            stringResource(R.string.yr_percent_value, format.decimal(body.lastBodyFatFraction * 100))
                        )
                    )
                }
            }
        }

        if (!review.isEmpty) {
            ClosingCard(review.period, currentYear, onShare, Modifier.appear(index++, review.period))
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun liftedLabel(period: YearInReviewPeriod, currentYear: Int): String = when (period) {
    is YearInReviewPeriod.Year -> if (period.year == currentYear) {
        stringResource(R.string.yr_lifted_label_this_year)
    } else {
        stringResource(R.string.yr_lifted_label_year, period.year)
    }
    YearInReviewPeriod.AllTime -> stringResource(R.string.yr_lifted_label_all_time)
}

@Composable
private fun HeroCard(review: YearInReview, format: ReviewFormat, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.CalorieStart, AppColors.CalorieEnd)))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.14f), radius = size.width * 0.42f, center = Offset(size.width * 0.95f, 0f))
                drawCircle(Color.White.copy(alpha = 0.08f), radius = size.width * 0.28f, center = Offset(0f, size.height))
            }
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Text(
            when (val period = review.period) {
                is YearInReviewPeriod.Year -> stringResource(R.string.yr_hero_year, period.year)
                YearInReviewPeriod.AllTime -> stringResource(R.string.yr_hero_all_time)
            },
            color = Color.White,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 44.sp, stepSize = 2.sp)
        )
        if (review.activeDays > 0) {
            Text(
                pluralStringResource(R.plurals.yr_hero_active_days, review.activeDays, format.integer(review.activeDays.toLong())),
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.yr_hero_subtitle),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun EmptyCard(modifier: Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    StoryCard(AppThemeColor.GRAPHITE.start, isDark, modifier) {
        Text(stringResource(R.string.yr_empty_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Caption(stringResource(R.string.yr_empty_body))
    }
}

@Composable
private fun ClosingCard(period: YearInReviewPeriod, currentYear: Int, onShare: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.CalorieEnd, AppColors.CalorieStart)))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.12f), radius = size.width * 0.35f, center = Offset(size.width, size.height))
            }
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            when (period) {
                is YearInReviewPeriod.Year -> if (period.year == currentYear) {
                    stringResource(R.string.yr_closing_this_year)
                } else {
                    stringResource(R.string.yr_closing_year, period.year)
                }
                YearInReviewPeriod.AllTime -> stringResource(R.string.yr_closing_all_time)
            },
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.yr_closing_caption),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(role = Role.Button, onClick = onShare)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, tint = AppColors.CalorieStart, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.yr_share_button),
                color = AppColors.CalorieStart,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Building blocks ─────────────────────────────────────────────────

/** Staggered fade-and-rise when a card first shows, replayed when the period changes. */
@Composable
private fun Modifier.appear(index: Int, key: Any): Modifier {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(60L * index.coerceAtMost(8))
        progress.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

@Composable
private fun SectionKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 10.dp),
        fontSize = 13.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    )
}

@Composable
private fun StoryCard(
    accent: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val base = if (isDark) Color(0xFF17171B) else Color(0xFFFFFBF8)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(base)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (isDark) 0.30f else 0.20f),
                        accent.copy(alpha = if (isDark) 0.06f else 0.04f)
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    accent.copy(alpha = if (isDark) 0.16f else 0.12f),
                    radius = size.width * 0.32f,
                    center = Offset(size.width * 0.98f, size.height * 0.02f)
                )
            }
            .border(1.dp, accent.copy(alpha = if (isDark) 0.30f else 0.22f), shape)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Accent dark enough to read as text on the light card. */
private fun accentText(accent: Color, isDark: Boolean): Color =
    if (isDark) accent else lerp(accent, Color.Black, 0.28f)

@Composable
private fun CardKicker(icon: ImageVector, text: String, accent: Color, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (isDark) 0.26f else 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentText(accent, isDark), modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = accentText(accent, isDark),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BigNumber(text: String) {
    Text(
        text,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 26.sp, maxFontSize = 52.sp, stepSize = 2.sp)
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    )
}

@Composable
private fun Subtitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, isDark: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.75f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = accentText(accent, isDark),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp, stepSize = 1.sp)
        )
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RankedRow(
    rank: Int,
    name: String,
    value: String,
    accent: Color,
    isDark: Boolean,
    detail: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (rank == 1) accent else accent.copy(alpha = if (isDark) 0.24f else 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                rank.toString(),
                color = if (rank == 1) Color.White else accentText(accent, isDark),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            detail?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = accentText(accent, isDark),
            textAlign = TextAlign.End
        )
    }
}

// ── Progress entry point ────────────────────────────────────────────

/** Days of use before the Year in review unlocks on its own. */
internal const val YEAR_REVIEW_UNLOCK_DAYS = 365L

/**
 * Days left until the Year in review unlocks, counted from the app's first install, or
 * null when it is already unlocked.
 */
internal fun yearReviewDaysLeft(firstInstallMillis: Long, nowMillis: Long = System.currentTimeMillis()): Int? {
    val usedDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays((nowMillis - firstInstallMillis).coerceAtLeast(0))
    val left = YEAR_REVIEW_UNLOCK_DAYS - usedDays
    return if (left > 0) left.toInt() else null
}

/**
 * The prominent "Your 2026 in review" card near the top of Progress. With [lockedDaysLeft]
 * it shows a locked "come back in…" state and does nothing on tap.
 */
@Composable
internal fun YearInReviewEntryCard(year: Int, lockedDaysLeft: Int? = null, onClick: () -> Unit) {
    if (lockedDaysLeft != null) {
        LockedYearInReviewCard(lockedDaysLeft)
        return
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.CalorieGradient)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.14f), radius = size.height * 0.9f, center = Offset(size.width, 0f))
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.yr_entry_title, year),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.yr_entry_subtitle),
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 13.sp
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LockedYearInReviewCard(daysLeft: Int) {
    val shape = RoundedCornerShape(22.dp)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.yr_locked_title), color = onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            // Over two months away reads better in months ("Vuelve en 9 meses").
            val months = daysLeft / 30
            Text(
                if (months >= 2) {
                    pluralStringResource(R.plurals.yr_locked_months, months, months)
                } else {
                    pluralStringResource(R.plurals.yr_locked_days, daysLeft, daysLeft)
                },
                color = onSurface.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
        }
    }
}

// ── Formatting & sharing ────────────────────────────────────────────

/** Locale-grouped numbers for this screen ("12,450" / "12.450"). */
private class ReviewFormat(locale: Locale) {
    private val integerFormat = NumberFormat.getIntegerInstance(locale)
    private val decimalFormat = NumberFormat.getInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }

    fun integer(value: Long): String = integerFormat.format(value)
    fun decimal(value: Double): String = decimalFormat.format(value)

    /** "+1.5", "−4.2" or "0" with the true minus sign, like the weekly recap. */
    fun signed(value: Double): String {
        val magnitude = decimal(abs(value))
        return when {
            magnitude == decimal(0.0) -> magnitude
            value > 0 -> "+$magnitude"
            else -> "−$magnitude"
        }
    }
}

private fun weightNumber(kg: Double, useMetric: Boolean, format: ReviewFormat): String =
    format.integer((if (useMetric) kg else kg * POUNDS_PER_KILOGRAM).roundToLong())

@Composable
private fun waterText(milliliters: Long, unit: WaterUnit, format: ReviewFormat): String =
    if (unit == WaterUnit.FLUID_OUNCES) {
        stringResource(R.string.yr_gallons_value, format.decimal(milliliters / US_GALLON_MILLILITERS))
    } else {
        stringResource(R.string.yr_liters_value, format.decimal(milliliters / 1_000.0))
    }

private fun shareReview(
    context: Context,
    review: YearInReview,
    format: ReviewFormat,
    useMetric: Boolean,
    energyUnit: EnergyUnit,
    waterUnit: WaterUnit
) {
    val text = shareText(context.resources, review, format, useMetric, energyUnit, waterUnit)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.yr_share_chooser)))
    }
}

private fun shareText(
    res: Resources,
    review: YearInReview,
    format: ReviewFormat,
    useMetric: Boolean,
    energyUnit: EnergyUnit,
    waterUnit: WaterUnit
): String = buildString {
    val weightLabel = res.getString(if (useMetric) R.string.unit_kg else R.string.unit_lbs)
    fun energy(kcal: Long) = res.getString(
        R.string.yr_value_with_unit,
        format.integer(energyUnit.displayAmount(kcal.toDouble()).roundToLong()),
        res.getString(energyUnit.symbolRes)
    )
    fun line(text: String) = appendLine("• $text")

    appendLine(
        when (val period = review.period) {
            is YearInReviewPeriod.Year -> res.getString(R.string.yr_share_header_year, period.year)
            YearInReviewPeriod.AllTime -> res.getString(R.string.yr_share_header_all_time)
        }
    )
    review.training?.let { training ->
        if (training.totalVolumeKg > 0.0) {
            line(
                res.getString(
                    R.string.yr_share_lifted,
                    res.getString(R.string.yr_value_with_unit, weightNumber(training.totalVolumeKg, useMetric, format), weightLabel)
                )
            )
        }
        line(res.getQuantityString(R.plurals.yr_share_workout_days, training.workoutDays, format.integer(training.workoutDays.toLong())))
        if (training.longestStreakDays >= 2) {
            line(
                res.getString(
                    R.string.yr_share_streak,
                    res.getQuantityString(R.plurals.yr_days_count, training.longestStreakDays, format.integer(training.longestStreakDays.toLong()))
                )
            )
        }
        training.favoriteMuscle?.let { line(res.getString(R.string.yr_share_favorite, ExerciseLocalization.label(it))) }
        if (training.totalBurnCalories > 0) line(res.getString(R.string.yr_share_burned, energy(training.totalBurnCalories)))
    }
    review.nutrition?.let { nutrition ->
        line(res.getString(R.string.yr_share_eaten, energy(nutrition.totalCalories)))
        if (nutrition.proteinGrams >= 1.0) {
            line(res.getString(R.string.yr_share_protein, format.integer(nutrition.proteinGrams.roundToLong())))
        }
    }
    review.hydration?.let { hydration ->
        val amount = if (waterUnit == WaterUnit.FLUID_OUNCES) {
            res.getString(R.string.yr_gallons_value, format.decimal(hydration.totalMilliliters / US_GALLON_MILLILITERS))
        } else {
            res.getString(R.string.yr_liters_value, format.decimal(hydration.liters))
        }
        line(res.getString(R.string.yr_share_water, amount))
    }
    review.fasting?.let { line(res.getString(R.string.yr_share_fasting, format.integer(it.totalHours.roundToLong()))) }
    review.body?.weightChangeKg?.let { change ->
        val shown = if (useMetric) change else change * POUNDS_PER_KILOGRAM
        line(res.getString(R.string.yr_share_weight, res.getString(R.string.yr_value_with_unit, format.signed(shown), weightLabel)))
    }
    appendLine()
    append(res.getString(R.string.yr_share_footer))
}
