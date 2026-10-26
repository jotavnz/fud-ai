package com.apoorvdarshan.calorietracker.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class YearInReviewTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun exercise(
        itemId: String,
        name: String,
        sets: List<PlannedSet>,
        primaryMuscles: List<String> = emptyList(),
        category: String = "strength"
    ) = PlannedExercise(
        itemId = itemId,
        name = name,
        level = "beginner",
        imagePaths = emptyList(),
        force = "push",
        mechanic = "compound",
        category = category,
        equipment = "barbell",
        primaryMuscles = primaryMuscles,
        secondaryMuscles = emptyList(),
        instructions = emptyList(),
        sets = sets
    )

    private fun day(date: String, vararg exercises: PlannedExercise) =
        date to WorkoutDayPlan(dateKey = date, exercises = exercises.toList())

    private fun food(name: String, calories: Int, at: String, protein: Double = 10.0, fiber: Double? = null) = FoodEntry(
        name = name,
        calories = calories,
        protein = protein,
        carbs = 20.0,
        fat = 5.0,
        fiber = fiber,
        timestamp = Instant.parse(at),
        source = FoodSource.MANUAL
    )

    private fun compute(
        period: YearInReviewPeriod,
        foods: List<FoodEntry> = emptyList(),
        waters: List<WaterEntry> = emptyList(),
        fasts: List<FastingSession> = emptyList(),
        weights: List<WeightEntry> = emptyList(),
        bodyFats: List<BodyFatEntry> = emptyList(),
        state: WorkoutPersistedState = WorkoutPersistedState(),
        imported: List<ImportedHealthWorkout> = emptyList(),
        unit: WorkoutWeightUnit = WorkoutWeightUnit.KG
    ) = YearInReviewCalculator.compute(
        period = period,
        foodEntries = foods,
        waterEntries = waters,
        fastingSessions = fasts,
        weightEntries = weights,
        bodyFatEntries = bodyFats,
        workoutState = state,
        importedWorkouts = imported,
        defaultWeightUnit = unit,
        zone = zone
    )

    private fun imported(dateKey: String, calories: Int?, minutes: Int = 30) = ImportedHealthWorkout(
        id = "hc-$dateKey",
        exerciseType = 56,
        startedAt = Instant.parse("${dateKey}T07:00:00Z"),
        endedAt = Instant.parse("${dateKey}T07:00:00Z").plusSeconds(minutes * 60L),
        durationSeconds = minutes * 60,
        activeCalories = calories,
        sourcePackage = "com.example.watch",
        diaryDateKey = dateKey
    )

    @Test
    fun volumeTotalsMixKilogramAndPoundSets() {
        val bench = exercise(
            itemId = "Barbell_Bench_Press",
            name = "Barbell Bench Press",
            primaryMuscles = listOf("chest"),
            sets = listOf(
                PlannedSet(weight = "100", weightUnit = WorkoutWeightUnit.KG, reps = "5"),
                // 220.462 lbs is 100 kg.
                PlannedSet(weight = "220.4622622", weightUnit = WorkoutWeightUnit.LBS, reps = "10"),
                // Load without reps was never performed.
                PlannedSet(weight = "80", weightUnit = WorkoutWeightUnit.KG, reps = "")
            )
        )
        val pullUp = exercise(
            itemId = "Pullups",
            name = "Pullups",
            primaryMuscles = listOf("lats"),
            sets = listOf(PlannedSet(weight = "", reps = "8"))
        )
        val state = WorkoutPersistedState(dayPlans = mapOf(day("2026-03-01", bench, pullUp)))

        val training = compute(YearInReviewPeriod.Year(2026), state = state).training!!

        assertEquals(1_500.0, training.totalVolumeKg, 0.01)
        assertEquals(3, training.performedSets)
        assertEquals(23, training.totalReps)
        assertEquals(1, training.workoutDays)
        assertEquals(2, training.distinctExercises)
        assertEquals("chest", training.favoriteMuscle)
        assertEquals(2, training.favoriteMuscleSets)
        assertEquals(listOf("Barbell Bench Press"), training.topByVolume.map { it.name })
        assertEquals(100.0, training.personalBests.single().loadKg, 0.01)
        assertEquals(LocalDate.parse("2026-03-01"), training.personalBests.single().date)
        assertEquals(VolumeEquivalence(VolumeEquivalenceKind.CAR, 1), training.equivalence)
    }

    @Test
    fun setsWithoutTheirOwnUnitUseTheDefaultUnit() {
        val squat = exercise(
            itemId = "Squat",
            name = "Squat",
            sets = listOf(PlannedSet(weight = "100", weightUnit = null, reps = "1"))
        )
        val state = WorkoutPersistedState(dayPlans = mapOf(day("2026-05-10", squat)))

        val inKg = compute(YearInReviewPeriod.Year(2026), state = state, unit = WorkoutWeightUnit.KG).training!!
        val inLbs = compute(YearInReviewPeriod.Year(2026), state = state, unit = WorkoutWeightUnit.LBS).training!!

        assertEquals(100.0, inKg.totalVolumeKg, 0.001)
        assertEquals(45.359, inLbs.totalVolumeKg, 0.001)
    }

    @Test
    fun streakCountsConsecutiveDiaryAndImportedWorkoutDays() {
        val row = exercise("Row", "Row", listOf(PlannedSet(weight = "40", weightUnit = WorkoutWeightUnit.KG, reps = "10")))
        val state = WorkoutPersistedState(
            dayPlans = mapOf(
                day("2026-03-01", row),
                day("2026-03-02", row),
                day("2026-03-03", row),
                day("2026-03-05", row),
                // A planned but never performed day breaks nothing and counts as nothing.
                day("2026-03-04", exercise("Row", "Row", listOf(PlannedSet(weight = "40")))),
            )
        )
        val training = compute(
            YearInReviewPeriod.Year(2026),
            state = state,
            imported = listOf(imported("2026-03-06", 200), imported("2026-03-07", null))
        ).training!!

        assertEquals(6, training.workoutDays)
        // 3/1–3/3 and 3/5–3/7 (the last two imported) are both three days long.
        assertEquals(3, training.longestStreakDays)
        assertEquals(2, training.importedWorkouts)
        assertEquals(200L, training.importedBurnCalories)
        assertEquals(60L, training.importedMinutes)
        assertEquals(4, training.topByFrequency.single().days)
    }

    @Test
    fun longestStreakHandlesGapsDuplicatesAndEmptyInput() {
        fun d(text: String) = LocalDate.parse(text)
        assertEquals(0, YearInReviewCalculator.longestStreak(emptyList()))
        assertEquals(1, YearInReviewCalculator.longestStreak(listOf(d("2026-01-01"), d("2026-01-01"))))
        assertEquals(
            4,
            YearInReviewCalculator.longestStreak(
                listOf(d("2025-12-30"), d("2026-01-02"), d("2025-12-31"), d("2026-01-01"), d("2026-01-05"))
            )
        )
    }

    @Test
    fun calculatedBurnsCountOncePerDayPlusImportedCalories() {
        val sessions = listOf(
            WorkoutSession(
                diaryDateKey = "2026-04-01",
                startedAt = Instant.parse("2026-04-01T10:00:00Z"),
                completedAt = Instant.parse("2026-04-01T11:00:00Z"),
                exercises = emptyList(),
                caloriesBurned = 200,
                healthSyncVersion = 1
            ),
            // A later recalculation of the same diary day replaces the first one.
            WorkoutSession(
                diaryDateKey = "2026-04-01",
                startedAt = Instant.parse("2026-04-01T10:00:00Z"),
                completedAt = Instant.parse("2026-04-01T12:00:00Z"),
                exercises = emptyList(),
                caloriesBurned = 300,
                healthSyncVersion = 2
            ),
            WorkoutSession(
                diaryDateKey = "2025-04-01",
                startedAt = Instant.parse("2025-04-01T10:00:00Z"),
                completedAt = Instant.parse("2025-04-01T11:00:00Z"),
                exercises = emptyList(),
                caloriesBurned = 999
            )
        )
        val training = compute(
            YearInReviewPeriod.Year(2026),
            state = WorkoutPersistedState(completedSessions = sessions),
            imported = listOf(imported("2026-04-02", 150))
        ).training!!

        assertEquals(300L, training.loggedBurnCalories)
        assertEquals(150L, training.importedBurnCalories)
        assertEquals(450L, training.totalBurnCalories)
        assertEquals(2, training.workoutDays)
    }

    @Test
    fun periodFilteringSeparatesYearsAndAllTimeIncludesEverything() {
        val curl = exercise("Curl", "Curl", listOf(PlannedSet(weight = "10", weightUnit = WorkoutWeightUnit.KG, reps = "10")))
        val state = WorkoutPersistedState(
            dayPlans = mapOf(day("2025-12-30", curl), day("2025-12-31", curl), day("2026-01-01", curl))
        )
        val foods = listOf(
            food("Oatmeal", 300, "2025-12-31T08:00:00Z"),
            food("Oatmeal", 300, "2026-01-01T08:00:00Z"),
            food("oatmeal ", 320, "2026-01-01T09:00:00Z", fiber = 4.0),
            food("Banana", 100, "2026-01-02T09:00:00Z")
        )
        val weights = listOf(
            WeightEntry(date = Instant.parse("2025-06-01T08:00:00Z"), weightKg = 90.0),
            WeightEntry(date = Instant.parse("2026-01-05T08:00:00Z"), weightKg = 80.0),
            WeightEntry(date = Instant.parse("2026-09-05T08:00:00Z"), weightKg = 78.0)
        )

        val y2026 = compute(YearInReviewPeriod.Year(2026), foods = foods, weights = weights, state = state)
        val y2025 = compute(YearInReviewPeriod.Year(2025), foods = foods, weights = weights, state = state)
        val all = compute(YearInReviewPeriod.AllTime, foods = foods, weights = weights, state = state)

        val training2026 = y2026.training!!
        val training2025 = y2025.training!!
        val trainingAll = all.training!!
        assertEquals(1, training2026.workoutDays)
        assertEquals(1, training2026.longestStreakDays)
        assertEquals(100.0, training2026.totalVolumeKg, 0.001)
        assertEquals(2, training2025.workoutDays)
        assertEquals(2, training2025.longestStreakDays)
        assertEquals(3, trainingAll.longestStreakDays)
        assertEquals(300.0, trainingAll.totalVolumeKg, 0.001)

        val nutrition = y2026.nutrition!!
        assertEquals(720L, nutrition.totalCalories)
        assertEquals(2, nutrition.loggedDays)
        assertEquals(360, nutrition.averageDailyCalories)
        assertEquals(4.0, nutrition.fiberGrams!!, 0.001)
        assertEquals(YearInReviewFood("Oatmeal", 2), nutrition.topFoods.first())
        assertNull(y2025.nutrition!!.fiberGrams)

        assertEquals(-2.0, y2026.body!!.weightChangeKg!!, 0.001)
        assertNull(y2025.body!!.weightChangeKg)
        assertEquals(-12.0, all.body!!.weightChangeKg!!, 0.001)

        // 2026: food on 1/1 and 1/2, workout on 1/1, weigh-ins on 1/5 and 9/5.
        assertEquals(4, y2026.activeDays)
    }

    @Test
    fun waterAndFastingTotals() {
        val waters = listOf(
            WaterEntry(date = Instant.parse("2026-02-01T08:00:00Z"), milliliters = 500),
            WaterEntry(date = Instant.parse("2026-02-02T08:00:00Z"), milliliters = 1_500),
            WaterEntry(date = Instant.parse("2025-02-02T08:00:00Z"), milliliters = 9_000)
        )
        val fasts = listOf(
            FastingSession(
                startedAt = Instant.parse("2026-02-01T20:00:00Z"),
                endedAt = Instant.parse("2026-02-02T12:00:00Z"),
                goalMinutes = 16 * 60
            ),
            FastingSession(
                startedAt = Instant.parse("2026-02-03T20:00:00Z"),
                endedAt = Instant.parse("2026-02-04T04:00:00Z"),
                goalMinutes = 16 * 60
            ),
            // Still running: not a completed fast.
            FastingSession(startedAt = Instant.parse("2026-02-05T20:00:00Z"))
        )
        val review = compute(YearInReviewPeriod.Year(2026), waters = waters, fasts = fasts)

        val hydration = review.hydration!!
        val fasting = review.fasting!!
        assertEquals(2_000L, hydration.totalMilliliters)
        assertEquals(2.0, hydration.liters, 0.0001)
        assertEquals(2, hydration.days)
        assertEquals(2, fasting.completedFasts)
        assertEquals(1, fasting.goalsReached)
        assertEquals(24.0, fasting.totalHours, 0.001)
        assertEquals(16.0, fasting.longestHours, 0.001)
        assertNull(review.training)
    }

    @Test
    fun emptyDataProducesAnEmptyReview() {
        val review = compute(YearInReviewPeriod.Year(2026))

        assertTrue(review.isEmpty)
        assertEquals(0, review.activeDays)
        assertNull(review.training)
        assertNull(review.nutrition)
        assertNull(review.hydration)
        assertNull(review.fasting)
        assertNull(review.body)
        assertNull(YearInReviewCalculator.equivalence(0.0))
    }

    @Test
    fun plannedButUnperformedWorkoutsDoNotCount() {
        val planned = exercise("Bench", "Bench", listOf(PlannedSet(weight = "60", weightUnit = WorkoutWeightUnit.KG)))
        val review = compute(
            YearInReviewPeriod.AllTime,
            state = WorkoutPersistedState(dayPlans = mapOf(day("2026-06-01", planned)))
        )

        assertTrue(review.isEmpty)
    }

    @Test
    fun equivalencePicksTheHeaviestWholeUnit() {
        assertEquals(VolumeEquivalence(VolumeEquivalenceKind.ELEPHANT, 2), YearInReviewCalculator.equivalence(12_450.0))
        assertEquals(VolumeEquivalence(VolumeEquivalenceKind.CAR, 3), YearInReviewCalculator.equivalence(4_600.0))
        assertEquals(VolumeEquivalence(VolumeEquivalenceKind.GRAND_PIANO, 1), YearInReviewCalculator.equivalence(450.0))
        assertNull(YearInReviewCalculator.equivalence(399.0))
    }

    @Test
    fun availableYearsListsYearsWithDataNewestFirst() {
        val years = YearInReviewCalculator.availableYears(
            currentYear = 2026,
            foodEntries = listOf(food("Toast", 200, "2024-05-01T08:00:00Z")),
            waterEntries = emptyList(),
            fastingSessions = emptyList(),
            weightEntries = listOf(WeightEntry(date = Instant.parse("2025-05-01T08:00:00Z"), weightKg = 70.0)),
            bodyFatEntries = emptyList(),
            workoutState = WorkoutPersistedState(),
            importedWorkouts = emptyList(),
            zone = zone
        )

        assertEquals(listOf(2026, 2025, 2024), years)
        assertFalse(years.contains(2023))
    }
}
