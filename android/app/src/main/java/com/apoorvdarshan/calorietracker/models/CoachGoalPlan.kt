package com.apoorvdarshan.calorietracker.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * The user's goal as they explained it to Coach, for goals the three profile options cannot
 * hold (lose fat while building muscle, a belly that bothers them, a sport, an injury...).
 * Coach turns the conversation into this plan; once the user applies it, it sets the profile's
 * weight goal and the machine coach's training goal, and every later Coach conversation reads
 * it so advice follows what the user actually wants.
 */
@Serializable
data class CoachGoalPlan(
    /** The goal in the user's own terms, in the app language. */
    val summary: String,
    val weightGoal: WeightGoal = WeightGoal.MAINTAIN,
    /** kg per week for LOSE / GAIN; null for MAINTAIN. */
    val weeklyChangeKg: Double? = null,
    /** [TrainingGoal] name; kept as text so a renamed enum never breaks a saved plan. */
    val trainingGoal: String = TrainingGoal.HYPERTROPHY.name,
    /** Areas or muscles to emphasize, in the app language ("Abdomen", "Espalda"). */
    val focusAreas: List<String> = emptyList(),
    val trainingDaysPerWeek: Int? = null,
    /** Constraints and preferences worth remembering: schedule, equipment, injuries, food. */
    val notes: List<String> = emptyList(),
    /** "gym" or "home" when the user said where they train; null leaves it unchanged. */
    val trainingPlace: String? = null,
    /** Home equipment in the library's names (dumbbell, bands, kettlebells...). */
    val homeEquipment: List<String> = emptyList(),
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant = Instant.now()
) {
    val training: TrainingGoal
        get() = runCatching { TrainingGoal.valueOf(trainingGoal) }.getOrDefault(TrainingGoal.HYPERTROPHY)

    val place: TrainingPlace?
        get() = when (trainingPlace?.lowercase()) {
            "gym" -> TrainingPlace.GYM
            "home" -> TrainingPlace.HOME
            else -> null
        }

    /** What Coach's system prompt carries about the plan. Model input, so it is not localized. */
    fun promptLines(): List<String> = buildList {
        add("- In the user's words: $summary")
        val pace = weeklyChangeKg?.let { String.format(java.util.Locale.US, " at %.2f kg/week", it) }.orEmpty()
        add("- Weight goal applied to the profile: ${weightGoal.name.lowercase()}$pace")
        add("- Training goal: ${training.name.lowercase()}")
        if (focusAreas.isNotEmpty()) add("- Focus areas: ${focusAreas.joinToString(", ")}")
        trainingDaysPerWeek?.let { add("- Training days per week: $it") }
        notes.forEach { add("- Note: $it") }
        place?.let { add("- Trains at: ${it.name.lowercase()}${if (homeEquipment.isNotEmpty()) " with ${homeEquipment.joinToString(", ")}" else ""}") }
    }
}

/** A goal plan Coach proposed in a reply. Nothing changes until the user taps Apply. */
@Serializable
data class CoachGoalProposal(
    val id: String = UUID.randomUUID().toString(),
    val status: Status = Status.PENDING,
    val plan: CoachGoalPlan
) {
    @Serializable
    enum class Status {
        @SerialName("pending") PENDING,
        @SerialName("applied") APPLIED,
        @SerialName("dismissed") DISMISSED
    }

    /** What the model is told about this card on later turns, so it knows what the user did. */
    val contextLine: String
        get() {
            val outcome = when (status) {
                Status.PENDING -> "waiting for the user to tap Apply"
                Status.APPLIED -> "applied by the user; it now drives the app"
                Status.DISMISSED -> "dismissed by the user; nothing changed"
            }
            return "[Coach proposed a goal plan: ${plan.summary} — $outcome]"
        }
}

/** Collects the goal card one Coach reply proposes; a reply holds at most one. */
class CoachGoalCollector {
    private var collected: CoachGoalProposal? = null

    val proposals: List<CoachGoalProposal>
        get() = synchronized(this) { listOfNotNull(collected) }

    /** A provider fallback re-runs the whole reply, so a card from a failed attempt is dropped. */
    fun reset() {
        synchronized(this) { collected = null }
    }

    /** Keeps the card; a second call in the same reply replaces the first (the model revised it). */
    fun add(proposal: CoachGoalProposal): CoachGoalProposal = synchronized(this) {
        collected = proposal
        proposal
    }
}

/** Replaces one goal card in a saved conversation; null when the message or card is missing. */
fun List<ChatMessage>.updatingGoalProposal(
    messageId: UUID,
    proposalId: String,
    transform: (CoachGoalProposal) -> CoachGoalProposal?
): List<ChatMessage>? {
    val message = firstOrNull { it.id == messageId } ?: return null
    val proposal = message.goalProposals.firstOrNull { it.id == proposalId } ?: return null
    val updated = transform(proposal) ?: return null
    if (updated == proposal) return null
    return map { candidate ->
        if (candidate.id != messageId) candidate
        else candidate.copy(goalProposals = candidate.goalProposals.map { if (it.id == proposalId) updated else it })
    }
}

/**
 * The profile with [plan]'s weight goal, following the same rules as Settings' goal picker:
 * MAINTAIN clears the pace and target weight; LOSE / GAIN take the plan's pace (or keep the
 * current one, else 0.5 kg) and drop a target weight that now points the wrong way.
 */
fun UserProfile.withGoalPlan(plan: CoachGoalPlan): UserProfile = when (plan.weightGoal) {
    WeightGoal.MAINTAIN -> copy(goal = WeightGoal.MAINTAIN, weeklyChangeKg = null, goalWeightKg = null)
    else -> {
        val gw = goalWeightKg
        val mismatched = gw != null && (
            (plan.weightGoal == WeightGoal.LOSE && gw >= weightKg) ||
                (plan.weightGoal == WeightGoal.GAIN && gw <= weightKg)
            )
        copy(
            goal = plan.weightGoal,
            weeklyChangeKg = plan.weeklyChangeKg ?: weeklyChangeKg ?: 0.5,
            goalWeightKg = if (mismatched) null else goalWeightKg
        )
    }
}
