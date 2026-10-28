package com.apoorvdarshan.calorietracker.models

import java.time.DayOfWeek
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
    /** The muscle group the tip is about, or null for tips about training as a whole. */
    val group: MachineMuscleGroup?

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

    /** Much more weekly volume than usual for this group: offer a light session or a deload week. */
    data class HighLoad(
        override val group: MachineMuscleGroup,
        val weeklySets: Int,
        val usualSets: Int
    ) : TrainingInsight

    /** A newcomer did a lot of sets for one group in a single session: build up gradually. */
    data class BeginnerVolume(
        override val group: MachineMuscleGroup,
        val sets: Int
    ) : TrainingInsight

    /** Trained every week for [weeks] weeks in a row: worth celebrating (most quit before month 3). */
    data class Milestone(val weeks: Int) : TrainingInsight {
        override val group: MachineMuscleGroup? get() = null
    }

    /** Today's session is done: the groups that will be most rested for the next one. */
    data class NextSession(val restedGroups: List<MachineMuscleGroup>) : TrainingInsight {
        override val group: MachineMuscleGroup? get() = null
    }

    /** Recent sessions follow [split] although another split is configured. */
    data class Style(val split: WorkoutSplit) : TrainingInsight {
        override val group: MachineMuscleGroup? get() = null
    }
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
    const val HIGH_LOAD_MIN_SETS = 10
    const val BEGINNER_DAYS = 8
    const val BEGINNER_SESSION_SETS = 10
    const val NEXT_SESSION_MIN_SETS = 6
    const val STYLE_MIN_DAYS = 6
    val MILESTONE_WEEKS = setOf(2, 4, 6, 8, 12, 16, 26, 52)
    private const val LOAD_STEP_KG = 2.5

    private val pushGroups = setOf(MachineMuscleGroup.CHEST, MachineMuscleGroup.SHOULDERS, MachineMuscleGroup.TRICEPS)
    private val pullGroups = setOf(MachineMuscleGroup.BACK, MachineMuscleGroup.BICEPS)
    private val legGroups = setOf(
        MachineMuscleGroup.LEGS, MachineMuscleGroup.HAMSTRINGS, MachineMuscleGroup.GLUTES, MachineMuscleGroup.CALVES
    )
    private val majorGroups = listOf(
        MachineMuscleGroup.LEGS, MachineMuscleGroup.BACK, MachineMuscleGroup.CHEST, MachineMuscleGroup.SHOULDERS
    )

    /** Muscle group a dataset muscle belongs to, or null for muscles the coach does not offer. */
    fun groupOf(muscle: String): MachineMuscleGroup? {
        val key = muscle.trim().lowercase()
        return MachineMuscleGroup.entries.firstOrNull { group -> group.muscles.any { it.lowercase() == key } }
    }

    /** Diary days with at least one performed set, newest first, up to [today]. */
    internal fun trainedDays(state: WorkoutPersistedState, today: LocalDate): List<Pair<LocalDate, WorkoutDayPlan>> =
        state.dayPlans.mapNotNull { (key, plan) ->
            val date = WorkoutDate.parse(key) ?: return@mapNotNull null
            if (date.isAfter(today)) return@mapNotNull null
            if (plan.exercises.none { WorkoutVolume.performedSets(it).isNotEmpty() }) return@mapNotNull null
            date to plan
        }.sortedByDescending { it.first }

    /** Effective sets per group for one day (primary = 1, secondary = 0.5). */
    internal fun groupSets(plan: WorkoutDayPlan): Map<MachineMuscleGroup, Double> {
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
        milestone(state, today)?.let(::add)
        nextSession(state, today)?.let(::add)
        beginnerVolume(state, today)?.let(::add)
        val overused = overuse(state, today)
        addAll(overused)
        addAll(highLoad(state, today).filter { load -> overused.none { it.group == load.group } })
        neglected(state, today)?.let(::add)
        imbalance(state, today)?.let(::add)
        style(state, today)?.let(::add)
        addAll(plateaus(state, today, defaultUnit))
    }

    /**
     * Groups with at least [HIGH_LOAD_MIN_SETS] sets in the last 7 days and more than 1.5× their
     * weekly average over the three weeks before (each of which must have had training).
     */
    fun highLoad(state: WorkoutPersistedState, today: LocalDate): List<TrainingInsight.HighLoad> {
        val days = trainedDays(state, today)
        fun weeksAgo(date: LocalDate) = (ChronoUnit.DAYS.between(date, today) / 7).toInt()
        if ((1..3).any { week -> days.none { weeksAgo(it.first) == week } }) return emptyList()
        val thisWeek = mutableMapOf<MachineMuscleGroup, Double>()
        val before = mutableMapOf<MachineMuscleGroup, Double>()
        for ((date, plan) in days) {
            val target = when (weeksAgo(date)) {
                0 -> thisWeek
                1, 2, 3 -> before
                else -> break
            }
            groupSets(plan).forEach { (group, sets) -> target[group] = (target[group] ?: 0.0) + sets }
        }
        return thisWeek.mapNotNull { (group, sets) ->
            val usual = (before[group] ?: 0.0) / 3
            if (sets >= HIGH_LOAD_MIN_SETS && usual > 0 && sets > usual * 1.5) {
                TrainingInsight.HighLoad(group, sets.roundToInt(), usual.roundToInt())
            } else {
                null
            }
        }.sortedByDescending { it.weeklySets }
    }

    /** In the first [BEGINNER_DAYS] training days, a group above [BEGINNER_SESSION_SETS] sets in the latest session (today or yesterday). */
    fun beginnerVolume(state: WorkoutPersistedState, today: LocalDate): TrainingInsight.BeginnerVolume? {
        val days = trainedDays(state, today)
        if (days.size > BEGINNER_DAYS) return null
        val (date, plan) = days.firstOrNull() ?: return null
        if (ChronoUnit.DAYS.between(date, today) > 1) return null
        return groupSets(plan).entries
            .filter { it.value > BEGINNER_SESSION_SETS }
            .maxByOrNull { it.value }
            ?.let { TrainingInsight.BeginnerVolume(it.key, it.value.roundToInt()) }
    }

    /** Weeks in a row (Monday to Sunday, this week included) with training, when that count is a milestone. */
    fun milestone(state: WorkoutPersistedState, today: LocalDate): TrainingInsight.Milestone? {
        val monday = today.with(DayOfWeek.MONDAY)
        val weeks = trainedDays(state, today)
            .map { ChronoUnit.WEEKS.between(it.first.with(DayOfWeek.MONDAY), monday).toInt() }
            .toSet()
        var streak = 0
        while (streak in weeks) streak++
        return if (streak in MILESTONE_WEEKS) TrainingInsight.Milestone(streak) else null
    }

    /** After [NEXT_SESSION_MIN_SETS]+ sets today, the groups most rested for the next session. */
    fun nextSession(state: WorkoutPersistedState, today: LocalDate): TrainingInsight.NextSession? {
        val plan = state.dayPlans[WorkoutDate.key(today)] ?: return null
        val sets = plan.exercises.filter { !it.isCardio }.sumOf { WorkoutVolume.performedSets(it).size }
        if (sets < NEXT_SESSION_MIN_SETS) return null
        val trainedToday = groupSets(plan).filterValues { it >= RECOVERY_SETS }.keys
        val rested = restedAlternatives(state, today.plusDays(1), exclude = trainedToday)
        return rested.takeIf { it.isNotEmpty() }?.let { TrainingInsight.NextSession(it) }
    }

    /**
     * The split the last four weeks follow, when it differs from the configured one: needs
     * [STYLE_MIN_DAYS] sessions, mostly single push / pull / legs days (all three present) or
     * upper / lower days (both present).
     */
    fun style(state: WorkoutPersistedState, today: LocalDate): TrainingInsight.Style? {
        val days = trainedDays(state, today).filter { ChronoUnit.DAYS.between(it.first, today) < 28 }
        if (days.size < STYLE_MIN_DAYS) return null
        val kinds = days.map { (_, plan) ->
            val groups = groupSets(plan).filterValues { it >= RECOVERY_SETS }.keys
            val push = groups.any { it in pushGroups }
            val pull = groups.any { it in pullGroups }
            val legs = groups.any { it in legGroups }
            when {
                push && !pull && !legs -> DayKind.PUSH
                pull && !push && !legs -> DayKind.PULL
                legs && !push && !pull -> DayKind.LEGS
                (push || pull) && !legs -> DayKind.UPPER
                else -> DayKind.OTHER
            }
        }
        val majority = days.size * 0.6
        val singles = setOf(DayKind.PUSH, DayKind.PULL, DayKind.LEGS)
        val detected = when {
            kinds.count { it in singles } >= majority && kinds.containsAll(singles) -> WorkoutSplit.PUSH_PULL_LEGS
            kinds.count { it == DayKind.UPPER || it == DayKind.LEGS } >= majority &&
                DayKind.LEGS in kinds && kinds.count { it == DayKind.UPPER } >= 2 -> WorkoutSplit.UPPER_LOWER
            else -> return null
        }
        return if (detected != state.preferences.split) TrainingInsight.Style(detected) else null
    }

    private enum class DayKind { PUSH, PULL, LEGS, UPPER, OTHER }

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
