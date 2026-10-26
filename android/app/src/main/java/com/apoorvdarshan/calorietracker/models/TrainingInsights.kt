package com.apoorvdarshan.calorietracker.models

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** How hard a muscle group was worked recently: [daysAgo] since the last session with real work. */
data class GroupFatigue(
    val group: MachineMuscleGroup,
    val daysAgo: Int,
    val sets: Int
)

/** Something worth telling the user about their training, ordered by [kind] priority. */
sealed interface TrainingInsight {
    val group: MachineMuscleGroup

    /** Worked yesterday (or heavily two days ago): suggest other, rested groups. */
    data class Recovery(
        override val group: MachineMuscleGroup,
        val daysAgo: Int,
        val sets: Int,
        val restedGroups: List<MachineMuscleGroup>
    ) : TrainingInsight

    /** Too many sets or too many days on the same group in the last week. */
    data class Overuse(
        override val group: MachineMuscleGroup,
        val weeklySets: Int,
        val trainedDays: Int
    ) : TrainingInsight

    /** Same exercise, no load or rep progress across recent sessions: change the stimulus. */
    data class Plateau(
        override val group: MachineMuscleGroup,
        val exerciseId: String,
        val exerciseName: String,
        val sessions: Int,
        val loadKg: Double
    ) : TrainingInsight

    /** Pressing far outweighs pulling (or the reverse) over two weeks. */
    data class Imbalance(
        override val group: MachineMuscleGroup,
        val heavySide: MachineMuscleGroup,
        val heavySets: Int,
        val lightSets: Int
    ) : TrainingInsight

    /** A major group untouched for a long stretch while the user kept training. */
    data class Neglected(
        override val group: MachineMuscleGroup,
        val daysSince: Int?
    ) : TrainingInsight
}

/** Next-session load suggestion for an exercise, from the last time it was performed. */
data class ProgressionHint(
    val lastLoadKg: Double,
    val lastReps: Int,
    val suggestedLoadKg: Double,
    val increase: Boolean
)

/**
 * Training heuristics over the persisted diary. All rules use performed sets only (sets
 * with reps); a primary muscle counts a full set and a secondary muscle half a set, the
 * usual way weekly volume is tallied. Thresholds follow common general-population
 * guidance: ~48 h between hard sessions for a muscle, roughly 10–20 weekly sets per
 * muscle group, and small (+2.5 kg) load jumps once the top of the rep range is reached.
 */
object TrainingInsights {
    const val RECOVERY_SETS = 3
    const val HEAVY_SESSION_SETS = 10
    const val WEEKLY_SET_LIMIT = 20
    const val WEEKLY_DAY_LIMIT = 4
    const val PLATEAU_SESSIONS = 4
    const val NEGLECT_DAYS = 10
    private const val LOAD_STEP_KG = 2.5

    private val pushGroups = setOf(MachineMuscleGroup.CHEST, MachineMuscleGroup.SHOULDERS, MachineMuscleGroup.TRICEPS)
    private val pullGroups = setOf(MachineMuscleGroup.BACK, MachineMuscleGroup.BICEPS)
    private val majorGroups = listOf(
        MachineMuscleGroup.LEGS, MachineMuscleGroup.BACK, MachineMuscleGroup.CHEST, MachineMuscleGroup.SHOULDERS
    )

    /** Muscle group a dataset muscle belongs to, or null for muscles the coach does not offer. */
    fun groupOf(muscle: String): MachineMuscleGroup? {
        val key = muscle.trim().lowercase()
        return MachineMuscleGroup.entries.firstOrNull { group -> group.muscles.any { it.lowercase() == key } }
    }

    /** Diary days with at least one performed set, newest first, up to [today]. */
    private fun trainedDays(state: WorkoutPersistedState, today: LocalDate): List<Pair<LocalDate, WorkoutDayPlan>> =
        state.dayPlans.mapNotNull { (key, plan) ->
            val date = WorkoutDate.parse(key) ?: return@mapNotNull null
            if (date.isAfter(today)) return@mapNotNull null
            if (plan.exercises.none { WorkoutVolume.performedSets(it).isNotEmpty() }) return@mapNotNull null
            date to plan
        }.sortedByDescending { it.first }

    /** Effective sets per group for one day (primary = 1, secondary = 0.5). */
    private fun groupSets(plan: WorkoutDayPlan): Map<MachineMuscleGroup, Double> {
        val totals = mutableMapOf<MachineMuscleGroup, Double>()
        for (exercise in plan.exercises) {
            if (exercise.isCardio) continue
            val sets = WorkoutVolume.performedSets(exercise).size
            if (sets == 0) continue
            val primary = exercise.primaryMuscles.mapNotNull(::groupOf).toSet()
            val secondary = exercise.secondaryMuscles.mapNotNull(::groupOf).toSet() - primary
            primary.forEach { totals[it] = (totals[it] ?: 0.0) + sets }
            secondary.forEach { totals[it] = (totals[it] ?: 0.0) + sets * 0.5 }
        }
        return totals
    }

    /** Groups still recovering: real work yesterday, or a heavy session two days ago. Today is excluded. */
    fun fatigue(state: WorkoutPersistedState, today: LocalDate): Map<MachineMuscleGroup, GroupFatigue> {
        val result = mutableMapOf<MachineMuscleGroup, GroupFatigue>()
        for ((date, plan) in trainedDays(state, today)) {
            val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
            if (daysAgo == 0) continue
            if (daysAgo > 2) break
            for ((group, sets) in groupSets(plan)) {
                val threshold = if (daysAgo == 1) RECOVERY_SETS else HEAVY_SESSION_SETS
                if (sets >= threshold && group !in result) {
                    result[group] = GroupFatigue(group, daysAgo, sets.roundToInt())
                }
            }
        }
        return result
    }

    /** Dataset muscles worked hard in the last two days (today included), for cross-fatigue. */
    fun fatiguedMuscles(state: WorkoutPersistedState, today: LocalDate): Set<String> {
        val sets = mutableMapOf<String, Int>()
        for ((date, plan) in trainedDays(state, today)) {
            if (ChronoUnit.DAYS.between(date, today) > 1) break
            for (exercise in plan.exercises) {
                if (exercise.isCardio) continue
                val count = WorkoutVolume.performedSets(exercise).size
                exercise.primaryMuscles.forEach { sets[it.lowercase()] = (sets[it.lowercase()] ?: 0) + count }
            }
        }
        return sets.filterValues { it >= RECOVERY_SETS }.keys
    }

    /** Weekly volume (last 7 days, today included) and the days each group was trained. */
    private fun weekly(state: WorkoutPersistedState, today: LocalDate): Map<MachineMuscleGroup, Pair<Double, Int>> {
        val result = mutableMapOf<MachineMuscleGroup, Pair<Double, Int>>()
        for ((date, plan) in trainedDays(state, today)) {
            if (ChronoUnit.DAYS.between(date, today) > 6) break
            for ((group, sets) in groupSets(plan)) {
                val (total, days) = result[group] ?: (0.0 to 0)
                result[group] = (total + sets) to (days + if (sets >= RECOVERY_SETS) 1 else 0)
            }
        }
        return result
    }

    fun overuse(state: WorkoutPersistedState, today: LocalDate): List<TrainingInsight.Overuse> =
        weekly(state, today).mapNotNull { (group, totals) ->
            val (sets, days) = totals
            if (sets > WEEKLY_SET_LIMIT || days >= WEEKLY_DAY_LIMIT) {
                TrainingInsight.Overuse(group, sets.roundToInt(), days)
            } else {
                null
            }
        }.sortedByDescending { it.weeklySets }

    /**
     * Exercises whose best load and best reps have not improved over the last
     * [PLATEAU_SESSIONS] sessions within four weeks.
     */
    fun plateaus(
        state: WorkoutPersistedState,
        today: LocalDate,
        defaultUnit: WorkoutWeightUnit
    ): List<TrainingInsight.Plateau> {
        val sessions = mutableMapOf<String, MutableList<Pair<Double, Int>>>()
        val names = mutableMapOf<String, PlannedExercise>()
        for ((date, plan) in trainedDays(state, today)) {
            if (ChronoUnit.DAYS.between(date, today) > 27) break
            for (exercise in plan.exercises) {
                if (exercise.isCardio) continue
                val top = WorkoutVolume.topLoadKg(exercise, defaultUnit) ?: continue
                val bestReps = WorkoutVolume.performedSets(exercise).mapNotNull(WorkoutVolume::reps).maxOrNull() ?: continue
                sessions.getOrPut(exercise.itemId) { mutableListOf() }.add(top to bestReps)
                names.putIfAbsent(exercise.itemId, exercise)
            }
        }
        return sessions.mapNotNull { (itemId, newestFirst) ->
            if (newestFirst.size < PLATEAU_SESSIONS) return@mapNotNull null
            val recent = newestFirst.take(PLATEAU_SESSIONS)
            val oldest = recent.last()
            val improved = recent.dropLast(1).any { (load, reps) ->
                load > oldest.first + 0.01 || (load >= oldest.first - 0.01 && reps > oldest.second)
            }
            if (improved) return@mapNotNull null
            val exercise = names.getValue(itemId)
            val group = exercise.primaryMuscles.firstNotNullOfOrNull(::groupOf) ?: return@mapNotNull null
            TrainingInsight.Plateau(group, itemId, exercise.name, recent.size, recent.first().first)
        }
    }

    /** Last date each group had real work, for rest suggestions and neglect checks. */
    fun lastTrained(state: WorkoutPersistedState, today: LocalDate): Map<MachineMuscleGroup, LocalDate> {
        val result = mutableMapOf<MachineMuscleGroup, LocalDate>()
        for ((date, plan) in trainedDays(state, today)) {
            for ((group, sets) in groupSets(plan)) {
                if (sets >= RECOVERY_SETS && group !in result) result[group] = date
            }
        }
        return result
    }

    /** Groups to train instead of [group]: not recovering, least recently trained first. */
    fun restedAlternatives(
        state: WorkoutPersistedState,
        today: LocalDate,
        exclude: Set<MachineMuscleGroup>,
        limit: Int = 3
    ): List<MachineMuscleGroup> {
        val tired = fatigue(state, today).keys + exclude
        val last = lastTrained(state, today)
        return (majorGroups + MachineMuscleGroup.entries)
            .distinct()
            .filter { it !in tired }
            .sortedBy { last[it] ?: LocalDate.MIN }
            .take(limit)
    }

    fun imbalance(state: WorkoutPersistedState, today: LocalDate): TrainingInsight.Imbalance? {
        var push = 0.0
        var pull = 0.0
        for ((date, plan) in trainedDays(state, today)) {
            if (ChronoUnit.DAYS.between(date, today) > 13) break
            for ((group, sets) in groupSets(plan)) {
                if (group in pushGroups) push += sets
                if (group in pullGroups) pull += sets
            }
        }
        return when {
            push >= 12 && push > pull * 1.5 ->
                TrainingInsight.Imbalance(MachineMuscleGroup.BACK, MachineMuscleGroup.CHEST, push.roundToInt(), pull.roundToInt())
            pull >= 12 && pull > push * 1.5 ->
                TrainingInsight.Imbalance(MachineMuscleGroup.CHEST, MachineMuscleGroup.BACK, pull.roundToInt(), push.roundToInt())
            else -> null
        }
    }

    /** A major group untrained for [NEGLECT_DAYS]+ days while the user logged 3+ workout days. */
    fun neglected(state: WorkoutPersistedState, today: LocalDate): TrainingInsight.Neglected? {
        val days = trainedDays(state, today)
        val recentDays = days.count { ChronoUnit.DAYS.between(it.first, today) < NEGLECT_DAYS }
        if (recentDays < 3) return null
        val last = lastTrained(state, today)
        return majorGroups
            .map { group -> group to last[group]?.let { ChronoUnit.DAYS.between(it, today).toInt() } }
            .filter { (_, since) -> since == null || since >= NEGLECT_DAYS }
            .maxByOrNull { (_, since) -> since ?: Int.MAX_VALUE }
            ?.let { (group, since) -> TrainingInsight.Neglected(group, since) }
    }

    /** The insights worth a card in the diary, most important first. */
    fun dashboard(
        state: WorkoutPersistedState,
        today: LocalDate,
        defaultUnit: WorkoutWeightUnit
    ): List<TrainingInsight> = buildList {
        addAll(overuse(state, today))
        neglected(state, today)?.let(::add)
        imbalance(state, today)?.let(::add)
        addAll(plateaus(state, today, defaultUnit))
    }

    /**
     * Load for the next session from the most recent earlier session of [itemId]: when every
     * performed set reached [prescription]'s top reps, add [LOAD_STEP_KG]; otherwise keep the
     * load and add reps. Null when the exercise was never logged with a load.
     */
    fun progression(
        state: WorkoutPersistedState,
        itemId: String,
        today: LocalDate,
        prescription: SetPrescription,
        defaultUnit: WorkoutWeightUnit
    ): ProgressionHint? {
        for ((date, plan) in trainedDays(state, today)) {
            if (!date.isBefore(today)) continue
            val exercise = plan.exercises.firstOrNull { it.itemId == itemId } ?: continue
            val sets = WorkoutVolume.performedSets(exercise)
            val load = WorkoutVolume.topLoadKg(exercise, defaultUnit) ?: continue
            val topSets = sets.filter { (WorkoutVolume.loadKg(it, defaultUnit) ?: 0.0) >= load - 0.01 }
            val reps = topSets.mapNotNull(WorkoutVolume::reps)
            val hitTop = reps.isNotEmpty() && reps.all { it >= prescription.maxReps }
            return ProgressionHint(
                lastLoadKg = load,
                lastReps = reps.maxOrNull() ?: 0,
                suggestedLoadKg = if (hitTop) load + LOAD_STEP_KG else load,
                increase = hitTop
            )
        }
        return null
    }
}
