package com.apoorvdarshan.calorietracker.models

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.floor

/** The span a year-in-review summary covers: one calendar year or everything logged. */
sealed interface YearInReviewPeriod {
    fun contains(date: LocalDate): Boolean

    data class Year(val year: Int) : YearInReviewPeriod {
        override fun contains(date: LocalDate): Boolean = date.year == year
    }

    data object AllTime : YearInReviewPeriod {
        override fun contains(date: LocalDate): Boolean = true
    }
}

/** One exercise's totals over the period, under its display name. */
data class YearInReviewExercise(
    val name: String,
    val volumeKg: Double,
    /** Diary days the exercise was performed on. */
    val days: Int,
    val performedSets: Int
)

/** Heaviest load logged for an exercise in the period, and the first day it was lifted. */
data class YearInReviewPersonalBest(
    val name: String,
    val loadKg: Double,
    val date: LocalDate
)

/** "As heavy as N …" comparisons for the lifted total, heaviest unit first. */
enum class VolumeEquivalenceKind(val kilograms: Double) {
    ELEPHANT(6_000.0),
    CAR(1_500.0),
    GRAND_PIANO(400.0)
}

data class VolumeEquivalence(val kind: VolumeEquivalenceKind, val count: Int)

data class YearInReviewTraining(
    val totalVolumeKg: Double,
    val performedSets: Int,
    val totalReps: Int,
    /** Days with diary work, a calculated burn, or a Health Connect workout. */
    val workoutDays: Int,
    val distinctExercises: Int,
    val longestStreakDays: Int,
    /** English muscle token (e.g. "Chest"); localize for display. */
    val favoriteMuscle: String?,
    val favoriteMuscleSets: Int,
    val topByVolume: List<YearInReviewExercise>,
    val topByFrequency: List<YearInReviewExercise>,
    val personalBests: List<YearInReviewPersonalBest>,
    /** Fud AI's own calculated burns, one per diary day (the same pick Progress charts). */
    val loggedBurnCalories: Long,
    /** Active calories other apps attached to their Health Connect sessions. Those
     *  sessions come from other apps only, so they never repeat Fud AI's own burns. */
    val importedBurnCalories: Long,
    val importedWorkouts: Int,
    val importedMinutes: Long,
    val equivalence: VolumeEquivalence?
) {
    val totalBurnCalories: Long get() = loggedBurnCalories + importedBurnCalories
}

data class YearInReviewFood(val name: String, val count: Int)

data class YearInReviewNutrition(
    val totalCalories: Long,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    /** Null when no entry in the period recorded fiber. */
    val fiberGrams: Double?,
    val loggedDays: Int,
    val entries: Int,
    val averageDailyCalories: Int,
    val topFoods: List<YearInReviewFood>
)

data class YearInReviewHydration(
    val totalMilliliters: Long,
    val days: Int
) {
    val liters: Double get() = totalMilliliters / 1_000.0
}

data class YearInReviewFasting(
    val completedFasts: Int,
    val goalsReached: Int,
    val totalHours: Double,
    val longestHours: Double
)

data class YearInReviewBody(
    val weighIns: Int,
    val firstWeightKg: Double?,
    val lastWeightKg: Double?,
    /** Last minus first weigh-in; null with fewer than two. */
    val weightChangeKg: Double?,
    val firstBodyFatFraction: Double?,
    val lastBodyFatFraction: Double?,
    /** Last minus first reading as a fraction; null with fewer than two. */
    val bodyFatChangeFraction: Double?
)

/** Everything the user did in [period]. A section is null when it has no data. */
data class YearInReview(
    val period: YearInReviewPeriod,
    /** Distinct days with anything logged: food, water, a workout, a weigh-in or a fast. */
    val activeDays: Int,
    val training: YearInReviewTraining?,
    val nutrition: YearInReviewNutrition?,
    val hydration: YearInReviewHydration?,
    val fasting: YearInReviewFasting?,
    val body: YearInReviewBody?
) {
    val isEmpty: Boolean
        get() = training == null && nutrition == null && hydration == null && fasting == null && body == null
}

/**
 * Pure year-in-review math (no Android framework calls), so it stays unit-testable.
 * Lifting numbers come from [WorkoutVolume]; the workout diary ([WorkoutPersistedState.dayPlans])
 * is the source of truth for sets, while completed sessions only contribute burns.
 */
object YearInReviewCalculator {
    const val TOP_COUNT = 3
    private const val MAX_DAILY_BURN = 5_000

    fun compute(
        period: YearInReviewPeriod,
        foodEntries: List<FoodEntry>,
        waterEntries: List<WaterEntry>,
        fastingSessions: List<FastingSession>,
        weightEntries: List<WeightEntry>,
        bodyFatEntries: List<BodyFatEntry>,
        workoutState: WorkoutPersistedState,
        importedWorkouts: List<ImportedHealthWorkout>,
        defaultWeightUnit: WorkoutWeightUnit,
        zone: ZoneId,
        /** Display name for a diary exercise: (catalog id, saved name snapshot) -> name. */
        exerciseName: (itemId: String, fallback: String) -> String = { _, fallback -> fallback }
    ): YearInReview {
        fun Instant.day(): LocalDate = atZone(zone).toLocalDate()

        val foods = foodEntries.filter { period.contains(it.timestamp.day()) }
        val waters = waterEntries.filter { it.milliliters > 0 && period.contains(it.date.day()) }
        // A fast belongs to the day it ended; active fasts are not finished yet.
        val fasts = fastingSessions.filter { session ->
            val end = session.endedAt ?: return@filter false
            period.contains(end.day())
        }
        val weights = weightEntries.filter { period.contains(it.date.day()) }.sortedBy { it.date }
        val bodyFats = bodyFatEntries.filter { period.contains(it.date.day()) }.sortedBy { it.date }

        val training = training(period, workoutState, importedWorkouts, defaultWeightUnit, exerciseName)

        val activeDays = buildSet {
            foods.forEach { add(it.timestamp.day()) }
            waters.forEach { add(it.date.day()) }
            fasts.forEach { session -> session.endedAt?.let { add(it.day()) } }
            weights.forEach { add(it.date.day()) }
            bodyFats.forEach { add(it.date.day()) }
            training?.let { addAll(it.workoutDates) }
        }.size

        return YearInReview(
            period = period,
            activeDays = activeDays,
            training = training?.summary,
            nutrition = nutrition(foods, zone),
            hydration = hydration(waters, zone),
            fasting = fasting(fasts),
            body = body(weights, bodyFats)
        )
    }

    /** Years with any logged data, newest first; [currentYear] is always offered. */
    fun availableYears(
        currentYear: Int,
        foodEntries: List<FoodEntry>,
        waterEntries: List<WaterEntry>,
        fastingSessions: List<FastingSession>,
        weightEntries: List<WeightEntry>,
        bodyFatEntries: List<BodyFatEntry>,
        workoutState: WorkoutPersistedState,
        importedWorkouts: List<ImportedHealthWorkout>,
        zone: ZoneId
    ): List<Int> {
        fun Instant.year(): Int = atZone(zone).year
        val years = sortedSetOf(currentYear)
        foodEntries.forEach { years += it.timestamp.year() }
        waterEntries.forEach { years += it.date.year() }
        fastingSessions.forEach { years += (it.endedAt ?: it.startedAt).year() }
        weightEntries.forEach { years += it.date.year() }
        bodyFatEntries.forEach { years += it.date.year() }
        workoutState.dayPlans.forEach { (key, plan) ->
            val date = WorkoutDate.parse(key) ?: return@forEach
            if (plan.exercises.any(::hasWork)) years += date.year
        }
        workoutState.completedSessions.forEach { session ->
            WorkoutDate.parse(session.diaryDateKey)?.let { years += it.year }
        }
        importedWorkouts.forEach { workout -> workout.diaryDate?.let { years += it.year } }
        // Ignore obviously broken clocks (epoch zero, far future) so the picker stays sane.
        return years.filter { it in 2000..currentYear + 1 }.sortedDescending()
    }

    /** Longest run of consecutive calendar days in [dates]. */
    fun longestStreak(dates: Collection<LocalDate>): Int {
        val days = dates.map { it.toEpochDay() }.toSortedSet()
        var best = 0
        var run = 0
        var previous: Long? = null
        for (day in days) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    /** The heaviest comparison that still reads as at least one whole thing, or null. */
    fun equivalence(totalKg: Double): VolumeEquivalence? {
        if (!totalKg.isFinite()) return null
        for (kind in VolumeEquivalenceKind.entries) {
            val count = floor(totalKg / kind.kilograms).toInt()
            if (count >= 1) return VolumeEquivalence(kind, count)
        }
        return null
    }

    // ── Training ────────────────────────────────────────────────────

    private class TrainingResult(val summary: YearInReviewTraining, val workoutDates: Set<LocalDate>)

    private class ExerciseTally(val name: String) {
        var volumeKg = 0.0
        var days = 0
        var sets = 0
        var topLoadKg: Double? = null
        var topLoadDate: LocalDate? = null
    }

    /** Performed work: a set with reps, or a saved timer for timed activities. */
    private fun hasWork(exercise: PlannedExercise): Boolean =
        WorkoutVolume.performedSets(exercise).isNotEmpty() || (exercise.timer?.savedSeconds ?: 0.0) > 0.0

    private fun exerciseKey(exercise: PlannedExercise): String =
        exercise.itemId.ifEmpty { "name:" + ExerciseLiftHistory.normalizedName(exercise.name) }

    private fun training(
        period: YearInReviewPeriod,
        state: WorkoutPersistedState,
        importedWorkouts: List<ImportedHealthWorkout>,
        defaultUnit: WorkoutWeightUnit,
        exerciseName: (String, String) -> String
    ): TrainingResult? {
        val workoutDates = mutableSetOf<LocalDate>()
        val tallies = linkedMapOf<String, ExerciseTally>()
        val muscleSets = mutableMapOf<String, Int>()
        var volume = 0.0
        var sets = 0
        var reps = 0

        val plans = state.dayPlans.mapNotNull { (key, plan) ->
            val date = WorkoutDate.parse(key) ?: WorkoutDate.parse(plan.dateKey) ?: return@mapNotNull null
            if (period.contains(date)) date to plan else null
        }.sortedBy { it.first }

        for ((date, plan) in plans) {
            var dayHasWork = false
            for (exercise in plan.exercises) {
                if (!hasWork(exercise)) continue
                dayHasWork = true
                val performed = WorkoutVolume.performedSets(exercise)
                val exerciseVolume = WorkoutVolume.exerciseVolumeKg(exercise, defaultUnit)
                volume += exerciseVolume
                sets += performed.size
                reps += performed.sumOf { WorkoutVolume.reps(it) ?: 0 }

                val tally = tallies.getOrPut(exerciseKey(exercise)) {
                    ExerciseTally(exerciseName(exercise.itemId, exercise.name.trim()).ifBlank { exercise.name })
                }
                tally.volumeKg += exerciseVolume
                tally.days += 1
                tally.sets += performed.size
                if (!exercise.isCardio) {
                    WorkoutVolume.topLoadKg(exercise, defaultUnit)?.let { load ->
                        // Strictly heavier, so the first day a best was lifted keeps it.
                        val best = tally.topLoadKg
                        if (best == null || load > best) {
                            tally.topLoadKg = load
                            tally.topLoadDate = date
                        }
                    }
                    if (performed.isNotEmpty()) {
                        exercise.primaryMuscles
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .distinct()
                            .forEach { muscle -> muscleSets[muscle] = (muscleSets[muscle] ?: 0) + performed.size }
                    }
                }
            }
            if (dayHasWork) workoutDates += date
        }

        // One calculated burn per diary day: the newest health sync, then the latest session.
        val burnsByDay = state.completedSessions
            .mapNotNull { session ->
                val date = WorkoutDate.parse(session.diaryDateKey) ?: return@mapNotNull null
                val calories = session.caloriesBurned?.takeIf { it in 1..MAX_DAILY_BURN } ?: return@mapNotNull null
                if (period.contains(date)) Triple(date, session, calories) else null
            }
            .groupBy { it.first }
            .mapValues { (_, values) ->
                values.maxWith(
                    compareBy<Triple<LocalDate, WorkoutSession, Int>> { it.second.healthSyncVersion ?: 0 }
                        .thenBy { it.second.completedAt }
                ).third
            }
        workoutDates += burnsByDay.keys

        val imports = importedWorkouts.mapNotNull { workout ->
            val date = workout.diaryDate ?: return@mapNotNull null
            if (period.contains(date)) date to workout else null
        }
        imports.forEach { workoutDates += it.first }

        if (workoutDates.isEmpty()) return null

        val exercises = tallies.values.toList()
        val favorite = muscleSets.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()

        fun ExerciseTally.toSummary() = YearInReviewExercise(name, volumeKg, days, this.sets)

        val summary = YearInReviewTraining(
            totalVolumeKg = volume,
            performedSets = sets,
            totalReps = reps,
            workoutDays = workoutDates.size,
            distinctExercises = exercises.size,
            longestStreakDays = longestStreak(workoutDates),
            favoriteMuscle = favorite?.key,
            favoriteMuscleSets = favorite?.value ?: 0,
            topByVolume = exercises
                .filter { it.volumeKg > 0.0 }
                .sortedWith(compareByDescending<ExerciseTally> { it.volumeKg }.thenBy { it.name })
                .take(TOP_COUNT)
                .map { it.toSummary() },
            topByFrequency = exercises
                .sortedWith(
                    compareByDescending<ExerciseTally> { it.days }
                        .thenByDescending { it.sets }
                        .thenBy { it.name }
                )
                .take(TOP_COUNT)
                .map { it.toSummary() },
            personalBests = exercises
                .mapNotNull { tally ->
                    val load = tally.topLoadKg ?: return@mapNotNull null
                    val date = tally.topLoadDate ?: return@mapNotNull null
                    YearInReviewPersonalBest(tally.name, load, date)
                }
                .sortedWith(compareByDescending<YearInReviewPersonalBest> { it.loadKg }.thenBy { it.name })
                .take(TOP_COUNT),
            loggedBurnCalories = burnsByDay.values.sumOf { it.toLong() },
            importedBurnCalories = imports.sumOf { (_, workout) ->
                (workout.activeCalories?.takeIf { it > 0 } ?: 0).toLong()
            },
            importedWorkouts = imports.size,
            importedMinutes = imports.sumOf { (_, workout) -> workout.durationSeconds.coerceAtLeast(0).toLong() } / 60,
            equivalence = equivalence(volume)
        )
        return TrainingResult(summary, workoutDates)
    }

    // ── Nutrition, water, fasting, body ─────────────────────────────

    private fun nutrition(foods: List<FoodEntry>, zone: ZoneId): YearInReviewNutrition? {
        if (foods.isEmpty()) return null
        val days = foods.mapTo(HashSet()) { it.timestamp.atZone(zone).toLocalDate() }.size
        val totalCalories = foods.sumOf { it.calories.coerceAtLeast(0).toLong() }
        val fiber = foods.mapNotNull { it.fiber?.takeIf(Double::isFinite) }
        val topFoods = foods
            .mapNotNull { entry -> entry.name.trim().takeIf { it.isNotEmpty() } }
            .groupBy { it.lowercase(Locale.ROOT) }
            .map { (_, names) ->
                // Show the spelling the user logged most often.
                val shown = names.groupingBy { it }.eachCount().maxWith(
                    compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key }
                ).key
                YearInReviewFood(shown, names.size)
            }
            .sortedWith(compareByDescending<YearInReviewFood> { it.count }.thenBy { it.name.lowercase(Locale.ROOT) })
            .take(TOP_COUNT)
        return YearInReviewNutrition(
            totalCalories = totalCalories,
            proteinGrams = foods.sumOf { it.protein.finiteOrZero() },
            carbsGrams = foods.sumOf { it.carbs.finiteOrZero() },
            fatGrams = foods.sumOf { it.fat.finiteOrZero() },
            fiberGrams = fiber.takeIf { it.isNotEmpty() }?.sum(),
            loggedDays = days,
            entries = foods.size,
            averageDailyCalories = Math.round(totalCalories.toDouble() / days).toInt(),
            topFoods = topFoods
        )
    }

    private fun hydration(waters: List<WaterEntry>, zone: ZoneId): YearInReviewHydration? {
        if (waters.isEmpty()) return null
        return YearInReviewHydration(
            totalMilliliters = waters.sumOf { it.milliliters.toLong() },
            days = waters.mapTo(HashSet()) { it.date.atZone(zone).toLocalDate() }.size
        )
    }

    private fun fasting(fasts: List<FastingSession>): YearInReviewFasting? {
        if (fasts.isEmpty()) return null
        val hours = fasts.map { it.durationSeconds() / 3_600.0 }
        return YearInReviewFasting(
            completedFasts = fasts.size,
            goalsReached = fasts.count { session -> session.endedAt?.let { it >= session.goalAt } == true },
            totalHours = hours.sum(),
            longestHours = hours.maxOrNull() ?: 0.0
        )
    }

    private fun body(weights: List<WeightEntry>, bodyFats: List<BodyFatEntry>): YearInReviewBody? {
        if (weights.isEmpty() && bodyFats.isEmpty()) return null
        val firstWeight = weights.firstOrNull()?.weightKg
        val lastWeight = weights.lastOrNull()?.weightKg
        val firstFat = bodyFats.firstOrNull()?.bodyFatFraction
        val lastFat = bodyFats.lastOrNull()?.bodyFatFraction
        return YearInReviewBody(
            weighIns = weights.size,
            firstWeightKg = firstWeight,
            lastWeightKg = lastWeight,
            weightChangeKg = if (weights.size >= 2 && firstWeight != null && lastWeight != null) lastWeight - firstWeight else null,
            firstBodyFatFraction = firstFat,
            lastBodyFatFraction = lastFat,
            bodyFatChangeFraction = if (bodyFats.size >= 2 && firstFat != null && lastFat != null) lastFat - firstFat else null
        )
    }

    private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0
}
