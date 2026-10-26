package com.apoorvdarshan.calorietracker.models

import com.apoorvdarshan.calorietracker.services.ai.FoodAnalysis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Things the user asked Coach to log (food, water, weight, body fat, workouts). Coach can only
 * propose: the chat shows a card listing every item and nothing is written until the user taps
 * Log on it. Foods are re-estimated with the same text analysis Home's "Describe food" uses
 * before the card becomes ready, so the numbers match a normal text log.
 */
@Serializable
data class CoachLogProposal(
    val id: String = UUID.randomUUID().toString(),
    val status: Status = Status.PREPARING,
    val items: List<CoachLogItem> = emptyList()
) {
    @Serializable
    enum class Status {
        /** Foods and workouts are still being estimated. */
        @SerialName("preparing") PREPARING,
        @SerialName("ready") READY,
        @SerialName("logged") LOGGED,
        @SerialName("dismissed") DISMISSED
    }

    /** Items the Log button will write: included, still pending and not handed to the editor. */
    val loggableItems: List<CoachLogItem>
        get() = items.filter { it.included && it.state == CoachLogItem.State.PENDING }

    /** What the model is told about this card on later turns, so it knows what the user did. */
    val contextLine: String
        get() {
            val outcome = when (status) {
                Status.PREPARING, Status.READY -> "waiting for the user to tap Log"
                Status.LOGGED -> "logged by the user"
                Status.DISMISSED -> "cancelled by the user; nothing was logged"
            }
            val summary = items.joinToString("; ") { item ->
                val state = when (item.state) {
                    CoachLogItem.State.LOGGED -> " (logged)"
                    CoachLogItem.State.FAILED -> " (could not be logged)"
                    CoachLogItem.State.HANDED_OFF -> " (opened in the food editor)"
                    CoachLogItem.State.PENDING -> if (item.included) "" else " (left out)"
                }
                "${item.kind.wire} ${item.displayName}$state"
            }
            return "[Coach proposed logging: $summary — $outcome]"
        }
}

/** One row of a log card. */
@Serializable
data class CoachLogItem(
    /** Also the diary entry's id, so a repeated Log never writes the same row twice. */
    val id: String = UUID.randomUUID().toString(),
    val kind: Kind = Kind.FOOD,
    /** The user's words (food or workout), sent to the text analysis. */
    val description: String = "",
    /** Short display name from the model, in the user's language. */
    val name: String = "",
    /** Portion as the model described it, e.g. "2 sandwiches". */
    val serving: String? = null,
    val meal: MealType? = null,
    /** ISO yyyy-MM-dd. */
    val date: String,
    /** HH:mm, 24-hour; null lets the app pick a time inside the meal. */
    val time: String? = null,
    /** The model's own estimate, used when the food analysis fails. */
    val estimate: CoachLogMacros? = null,
    /** The food analysis result (same pipeline as Home's text entry). */
    val analysis: FoodAnalysis? = null,
    val workoutDraft: WorkoutTextDraft? = null,
    val waterMl: Int? = null,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val minutes: Int? = null,
    /** Cardio: kcal burned the user stated. */
    val caloriesBurned: Int? = null,
    val included: Boolean = true,
    val state: State = State.PENDING,
    /** Short reason shown under a failed row. */
    val error: String? = null
) {
    @Serializable
    enum class Kind(val wire: String) {
        @SerialName("food") FOOD("food"),
        @SerialName("water") WATER("water"),
        @SerialName("weight") WEIGHT("weight"),
        @SerialName("body_fat") BODY_FAT("body_fat"),
        @SerialName("workout") WORKOUT("workout"),
        @SerialName("cardio") CARDIO("cardio");

        companion object {
            fun fromWire(raw: String?): Kind? {
                val key = raw?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
                return when (key) {
                    "food", "meal", "drink", "beverage" -> FOOD
                    "water", "hydration" -> WATER
                    "weight", "body_weight" -> WEIGHT
                    "body_fat", "bodyfat", "body_fat_percent" -> BODY_FAT
                    "workout", "exercise", "strength", "training" -> WORKOUT
                    "cardio", "activity" -> CARDIO
                    else -> null
                }
            }
        }
    }

    @Serializable
    enum class State {
        @SerialName("pending") PENDING,
        @SerialName("logged") LOGGED,
        @SerialName("failed") FAILED,
        /** The user opened this food in Home's review sheet and logs it from there. */
        @SerialName("handed_off") HANDED_OFF
    }

    val displayName: String
        get() = name.ifBlank { analysis?.name ?: description }.ifBlank { kind.wire }

    /** Nutrition the card shows and the diary gets: the analysis, else the model's estimate. */
    val effectiveMacros: CoachLogMacros?
        get() = analysis?.let { CoachLogMacros(it.calories, it.protein, it.carbs, it.fat) } ?: estimate

    /** A food the user can still review in the editor: the analysis, or one built from the estimate. */
    fun reviewAnalysis(): FoodAnalysis? {
        if (kind != Kind.FOOD) return null
        analysis?.let { return it }
        val macros = estimate ?: return null
        return FoodAnalysis(
            name = displayName,
            calories = macros.calories,
            protein = macros.protein,
            carbs = macros.carbs,
            fat = macros.fat,
            servingSizeGrams = 0.0,
            servingSizeIsKnown = false,
            customNote = serving
        )
    }
}

@Serializable
data class CoachLogMacros(
    val calories: Int,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0
)

/** Totals of the items a card will log (foods only). */
fun List<CoachLogItem>.foodTotals(): CoachLogMacros {
    val foods = filter { it.kind == CoachLogItem.Kind.FOOD }.mapNotNull { it.effectiveMacros }
    return CoachLogMacros(
        calories = foods.sumOf { it.calories },
        protein = (foods.sumOf { it.protein } * 10).roundToInt() / 10.0,
        carbs = (foods.sumOf { it.carbs } * 10).roundToInt() / 10.0,
        fat = (foods.sumOf { it.fat } * 10).roundToInt() / 10.0
    )
}

/** Collects the log cards Coach proposes while answering one message. */
class CoachLogCollector {
    private val collected = mutableListOf<CoachLogProposal>()

    val proposals: List<CoachLogProposal>
        get() = synchronized(collected) { collected.toList() }

    /** A provider fallback re-runs the whole reply, so cards from a failed attempt are dropped. */
    fun reset() {
        synchronized(collected) { collected.clear() }
    }

    /** Keeps the card, or returns null once a reply already holds [MAX_PROPOSALS]. */
    fun add(proposal: CoachLogProposal): CoachLogProposal? = synchronized(collected) {
        if (collected.size >= MAX_PROPOSALS) return null
        collected += proposal
        proposal
    }

    companion object {
        const val MAX_PROPOSALS = 3
        const val MAX_ITEMS = 20
    }
}

/** Replaces one log card in a saved conversation; null when the message or card is missing. */
fun List<ChatMessage>.updatingLogProposal(
    messageId: UUID,
    proposalId: String,
    transform: (CoachLogProposal) -> CoachLogProposal?
): List<ChatMessage>? {
    val message = firstOrNull { it.id == messageId } ?: return null
    val proposal = message.logProposals.firstOrNull { it.id == proposalId } ?: return null
    val updated = transform(proposal) ?: return null
    if (updated == proposal) return null
    return map { candidate ->
        if (candidate.id != messageId) candidate
        else candidate.copy(logProposals = candidate.logProposals.map { if (it.id == proposalId) updated else it })
    }
}
