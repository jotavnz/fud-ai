package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.CoachGoalCollector
import com.apoorvdarshan.calorietracker.models.CoachGoalPlan
import com.apoorvdarshan.calorietracker.models.CoachGoalProposal
import com.apoorvdarshan.calorietracker.models.TrainingGoal
import com.apoorvdarshan.calorietracker.models.WeightGoal
import com.google.gson.GsonBuilder
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Coach chat's `propose_goal_plan` tool: after talking the user through what they want, the
 * model sums it up in a plan the chat shows as one card. Nothing changes until the user taps
 * Apply. Parsing here is pure (no Android, no I/O) so it is unit-tested.
 */
object CoachGoalTool {
    const val TOOL_NAME = "propose_goal_plan"

    const val DESCRIPTION = "Propose the user's goal plan once you understand what they want, especially goals the app's three options " +
        "(lose / maintain / gain weight) cannot express: losing fat while building muscle, a belly or other area that bothers them, " +
        "a sport, an event, an injury. The plan sets the profile's weight goal (which drives calories and macros) and the training goal " +
        "(which drives sets, reps and rest), and is remembered in every later conversation. This does NOT change anything: the user sees " +
        "a card with Apply and Dismiss. Afterwards briefly explain the plan and ask them to review the card; never say it was applied."

    private const val MAX_SUMMARY = 400
    private const val MAX_LIST_ITEMS = 6
    private const val MAX_FOCUS_TEXT = 40
    private const val MAX_NOTE_TEXT = 160
    private const val MIN_WEEKLY_KG = 0.1
    private const val MAX_LOSS_KG = 1.0
    private const val MAX_GAIN_KG = 0.5

    fun parameterSchema(): Map<String, Any> = linkedMapOf(
        "type" to "object",
        "properties" to linkedMapOf(
            "summary" to prop("string", "The goal in one or two sentences in the user's language and their own terms, e.g. \"Bajar la grasa del abdomen y ganar músculo\""),
            "weight_goal" to prop("string", "lose, maintain or gain: the calorie direction. Fat loss while building muscle (recomposition) is usually maintain or a slow lose."),
            "weekly_change_kg" to prop("number", "kg per week for lose (0.1–1.0) or gain (0.1–0.5). Omit for maintain."),
            "training_goal" to prop("string", "hypertrophy (build muscle), strength, or endurance"),
            "focus_areas" to linkedMapOf(
                "type" to "array",
                "description" to "Up to $MAX_LIST_ITEMS areas or muscles to emphasize, in the user's language",
                "items" to linkedMapOf("type" to "string")
            ),
            "training_days_per_week" to prop("number", "Days per week the user can train (1–7), if they said it"),
            "notes" to linkedMapOf(
                "type" to "array",
                "description" to "Up to $MAX_LIST_ITEMS short facts to remember, in the user's language: schedule, gym or home, equipment, injuries, foods they avoid",
                "items" to linkedMapOf("type" to "string")
            )
        ),
        "required" to listOf("summary", "weight_goal", "training_goal")
    )

    private fun prop(type: String, description: String) = linkedMapOf("type" to type, "description" to description)

    /** Result of reading the tool's arguments: the plan, or why it was refused. */
    data class Parsed(val plan: CoachGoalPlan?, val errors: List<String>)

    fun parse(args: Map<String, Any?>, now: Instant = Instant.now()): Parsed {
        val errors = mutableListOf<String>()
        val summary = text(args["summary"]).take(MAX_SUMMARY)
        if (summary.isEmpty()) errors += "summary is required."
        val weightGoal = when (text(args["weight_goal"]).lowercase()) {
            "lose" -> WeightGoal.LOSE
            "maintain" -> WeightGoal.MAINTAIN
            "gain" -> WeightGoal.GAIN
            else -> null.also { errors += "weight_goal must be lose, maintain or gain." }
        }
        val trainingGoal = when (text(args["training_goal"]).lowercase()) {
            "hypertrophy" -> TrainingGoal.HYPERTROPHY
            "strength" -> TrainingGoal.STRENGTH
            "endurance" -> TrainingGoal.ENDURANCE
            else -> null.also { errors += "training_goal must be hypertrophy, strength or endurance." }
        }
        if (summary.isEmpty() || weightGoal == null || trainingGoal == null) return Parsed(null, errors)

        val weekly = when (weightGoal) {
            WeightGoal.MAINTAIN -> null
            WeightGoal.LOSE -> number(args["weekly_change_kg"])?.coerceIn(MIN_WEEKLY_KG, MAX_LOSS_KG)
            WeightGoal.GAIN -> number(args["weekly_change_kg"])?.coerceIn(MIN_WEEKLY_KG, MAX_GAIN_KG)
        }?.let { (it * 100).roundToInt() / 100.0 }
        val days = number(args["training_days_per_week"])?.roundToInt()?.takeIf { it in 1..7 }
        val plan = CoachGoalPlan(
            summary = summary,
            weightGoal = weightGoal,
            weeklyChangeKg = weekly,
            trainingGoal = trainingGoal.name,
            focusAreas = textList(args["focus_areas"], MAX_FOCUS_TEXT),
            trainingDaysPerWeek = days,
            notes = textList(args["notes"], MAX_NOTE_TEXT),
            updatedAt = now
        )
        return Parsed(plan, errors)
    }

    fun propose(args: Map<String, Any?>, collector: CoachGoalCollector?): String {
        collector ?: return errorJson("Goal plans are only available in Coach chat.")
        val parsed = parse(args)
        val plan = parsed.plan ?: return errorJson(parsed.errors.joinToString(" "))
        val stored = collector.add(CoachGoalProposal(plan = plan))
        val result = linkedMapOf<String, Any?>(
            "proposal_id" to stored.id,
            "status" to "pending_user_confirmation",
            "applied" to false,
            "weight_goal" to plan.weightGoal.name.lowercase(),
            "weekly_change_kg" to plan.weeklyChangeKg,
            "training_goal" to plan.training.name.lowercase()
        )
        if (parsed.errors.isNotEmpty()) result["skipped"] = parsed.errors
        result["note"] = "Nothing changed yet. The user sees a card with Apply and Dismiss under your reply. " +
            "Briefly explain the plan and ask them to review it. Never say it was applied."
        return GSON.toJson(result)
    }

    /**
     * System-prompt lines: the saved plan (when there is one) and how to interview a user whose
     * goal does not fit the profile's three options. Model input, so it is not localized.
     */
    fun promptLines(currentPlan: CoachGoalPlan?, canPropose: Boolean): List<String> = buildList {
        if (currentPlan != null) {
            add("## The user's goal plan (agreed with you; it drives the app)")
            addAll(currentPlan.promptLines())
            add("Ground training and nutrition advice in this plan. If the user's goal changes, help them update it.")
            add("")
        }
        if (!canPropose) return@buildList
        add("## Understanding the user's goal")
        add("Many goals do not fit lose / maintain / gain. When the user wants to explain their goal, says theirs is more complex, " +
            "or describes several goals at once, interview them before planning: ask ONE short question at a time, at most 4 questions, " +
            "covering what they want to change and why, how many days and how long they can train, where (gym or home) and with what, " +
            "injuries or limits, and how they eat now. Skip what you already know from the profile or this conversation.")
        add("Be honest about physiology: fat cannot be lost from one spot by training that area. Belly fat goes down with overall fat " +
            "loss (a moderate calorie deficit or maintenance with recomposition, high protein, full-body strength training, steps and sleep), " +
            "and it is often the last area to shrink. Training the core still helps posture and strength. Losing fat while building muscle " +
            "(recomposition) works best for beginners or people returning to training: maintain or lose slowly (0.25 kg/week) with hypertrophy training.")
        add("Then call $TOOL_NAME once with the plan and summarize it in two or three sentences.")
        add("")
    }

    private fun text(value: Any?): String = when (value) {
        null -> ""
        is String -> value.trim()
        is Number, is Boolean -> value.toString()
        else -> ""
    }

    private fun number(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }
        is String -> value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    private fun textList(value: Any?, maxChars: Int): List<String> {
        val raw = when (value) {
            is List<*> -> value
            is String -> value.split(',', ';', '\n')
            else -> emptyList()
        }
        return raw.map { text(it).take(maxChars) }.filter { it.isNotEmpty() }.distinct().take(MAX_LIST_ITEMS)
    }

    private fun errorJson(message: String): String = GSON.toJson(linkedMapOf("error" to message))

    private val GSON = GsonBuilder().disableHtmlEscaping().create()
}
