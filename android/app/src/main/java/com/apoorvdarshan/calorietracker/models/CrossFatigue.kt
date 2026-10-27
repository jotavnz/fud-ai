package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.data.ExerciseItem

/**
 * Cross-fatigue for any muscle group: an exercise for one group often also works another
 * (rows and pulldowns load the biceps, presses load the triceps and shoulders). When that other
 * group is still recovering, the machine coach ranks the exercise lower, labels it, and asks
 * before adding it; when the chosen group itself is tired it points to rested ones.
 */
object CrossFatigue {
    /** Below this a group counts as still recovering. */
    const val TIRED_PERCENT = 70
    /** Below this adding an exercise that works the group asks first. */
    const val WARN_PERCENT = 50
    /** Groups at or above this are good picks for today. */
    const val READY_PERCENT = 85

    private val major = listOf(
        MachineMuscleGroup.LEGS, MachineMuscleGroup.BACK, MachineMuscleGroup.CHEST, MachineMuscleGroup.SHOULDERS,
        MachineMuscleGroup.GLUTES, MachineMuscleGroup.HAMSTRINGS
    )

    fun tiredGroups(readiness: List<MuscleReadiness>): Map<MachineMuscleGroup, Int> =
        readiness.filter { it.percent < TIRED_PERCENT }.associate { it.group to it.percent }

    /** Dataset muscle names of [groups], for the candidate ranking. */
    fun musclesOf(groups: Collection<MachineMuscleGroup>): Set<String> = groups.flatMapTo(mutableSetOf()) { it.muscles }

    /** Tired groups (other than [target]) that [item] also works, most tired first. */
    fun hits(item: ExerciseItem, target: MachineMuscleGroup, tired: Map<MachineMuscleGroup, Int>): List<Pair<MachineMuscleGroup, Int>> =
        (item.primaryMuscles + item.secondaryMuscles)
            .mapNotNull(TrainingInsights::groupOf)
            .toSet()
            .minus(target)
            .mapNotNull { group -> tired[group]?.let { group to it } }
            .sortedBy { it.second }

    /** Rested groups to train instead of [exclude]: big groups first, then the most recovered. */
    fun readyAlternatives(readiness: List<MuscleReadiness>, exclude: MachineMuscleGroup, count: Int = 3): List<MachineMuscleGroup> =
        readiness
            .filter { it.group != exclude && it.percent >= READY_PERCENT }
            .sortedWith(compareBy<MuscleReadiness>({ if (it.group in major) 0 else 1 }, { -it.percent }))
            .map { it.group }
            .take(count)
}
