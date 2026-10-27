package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.ChatMessage
import com.apoorvdarshan.calorietracker.models.CoachGoalCollector
import com.apoorvdarshan.calorietracker.models.CoachGoalPlan
import com.apoorvdarshan.calorietracker.models.CoachGoalProposal
import com.apoorvdarshan.calorietracker.models.TrainingGoal
import com.apoorvdarshan.calorietracker.models.UserProfile
import com.apoorvdarshan.calorietracker.models.WeightGoal
import com.apoorvdarshan.calorietracker.models.updatingGoalProposal
import com.apoorvdarshan.calorietracker.models.withGoalPlan
import com.google.gson.JsonParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CoachGoalToolTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")

    /** The case that started this: belly fat down, muscle up, in Spanish. */
    private val recomp = mapOf(
        "summary" to "Bajar la grasa del abdomen y ganar músculo",
        "weight_goal" to "lose",
        "weekly_change_kg" to "0,25",
        "training_goal" to "hypertrophy",
        "focus_areas" to listOf("Abdomen", "Espalda", "Abdomen", ""),
        "training_days_per_week" to 4.0,
        "notes" to listOf("Entrena en el gimnasio de 6 a 7 pm", "Le molesta la rodilla derecha")
    )

    @Test
    fun recompositionPlanIsParsedAndCleaned() {
        val plan = CoachGoalTool.parse(recomp, now).plan
        assertNotNull(plan)
        plan!!
        assertEquals(WeightGoal.LOSE, plan.weightGoal)
        assertEquals(0.25, plan.weeklyChangeKg!!, 1e-9)
        assertEquals(TrainingGoal.HYPERTROPHY, plan.training)
        assertEquals(listOf("Abdomen", "Espalda"), plan.focusAreas)
        assertEquals(4, plan.trainingDaysPerWeek)
        assertEquals(2, plan.notes.size)
        assertEquals(now, plan.updatedAt)
    }

    @Test
    fun paceIsClampedAndMaintainHasNone() {
        val fast = CoachGoalTool.parse(recomp + ("weekly_change_kg" to 3), now).plan!!
        assertEquals(1.0, fast.weeklyChangeKg!!, 1e-9)
        val bulk = CoachGoalTool.parse(recomp + mapOf("weight_goal" to "gain", "weekly_change_kg" to 2), now).plan!!
        assertEquals(0.5, bulk.weeklyChangeKg!!, 1e-9)
        val maintain = CoachGoalTool.parse(recomp + ("weight_goal" to "maintain"), now).plan!!
        assertNull(maintain.weeklyChangeKg)
    }

    @Test
    fun missingRequiredFieldsAreRefused() {
        val parsed = CoachGoalTool.parse(mapOf("summary" to "", "weight_goal" to "shred", "training_goal" to "cardio"), now)
        assertNull(parsed.plan)
        assertEquals(3, parsed.errors.size)
        val reply = JsonParser.parseString(CoachGoalTool.propose(emptyMap(), CoachGoalCollector())).asJsonObject
        assertTrue(reply.has("error"))
    }

    @Test
    fun proposeKeepsOneCardPerReplyAndNeverClaimsItApplied() {
        val collector = CoachGoalCollector()
        CoachGoalTool.propose(recomp, collector)
        val reply = JsonParser.parseString(CoachGoalTool.propose(recomp + ("weight_goal" to "maintain"), collector)).asJsonObject
        assertEquals(false, reply["applied"].asBoolean)
        assertEquals("maintain", reply["weight_goal"].asString)
        assertEquals(1, collector.proposals.size)
        assertEquals(WeightGoal.MAINTAIN, collector.proposals.single().plan.weightGoal)
        assertNull(CoachGoalTool.propose(recomp, null).let { JsonParser.parseString(it).asJsonObject["proposal_id"] })
    }

    @Test
    fun toolIsOfferedOnlyWithACollector() {
        val withCards = CoachTools(emptyList(), emptyList(), emptyList(), goalProposals = CoachGoalCollector())
        assertTrue(CoachGoalTool.TOOL_NAME in withCards.availableToolNames)
        val without = CoachTools(emptyList(), emptyList(), emptyList())
        assertTrue(CoachGoalTool.TOOL_NAME !in without.availableToolNames)
        val reply = JsonParser.parseString(withCards.execute(CoachGoalTool.TOOL_NAME, recomp)).asJsonObject
        assertTrue(reply.has("proposal_id"))
    }

    @Test
    fun applyingFollowsTheSettingsGoalRules() {
        val profile = UserProfile(weightKg = 80.0, goal = WeightGoal.GAIN, weeklyChangeKg = 0.5, goalWeightKg = 85.0)
        val plan = CoachGoalTool.parse(recomp, now).plan!!
        val lose = profile.withGoalPlan(plan)
        assertEquals(WeightGoal.LOSE, lose.goal)
        assertEquals(0.25, lose.weeklyChangeKg!!, 1e-9)
        assertNull("A target above the current weight contradicts losing", lose.goalWeightKg)
        val maintain = profile.withGoalPlan(plan.copy(weightGoal = WeightGoal.MAINTAIN, weeklyChangeKg = null))
        assertEquals(WeightGoal.MAINTAIN, maintain.goal)
        assertNull(maintain.weeklyChangeKg)
        assertNull(maintain.goalWeightKg)
    }

    @Test
    fun promptCarriesThePlanAndTheInterviewOnlyWhenCardsExist() {
        val plan = CoachGoalTool.parse(recomp, now).plan!!
        val lines = CoachGoalTool.promptLines(plan, canPropose = true).joinToString("\n")
        assertTrue(lines.contains("Bajar la grasa del abdomen"))
        assertTrue(lines.contains(CoachGoalTool.TOOL_NAME))
        assertTrue(lines.contains("cannot be lost from one spot"))
        val toolFree = CoachGoalTool.promptLines(plan, canPropose = false).joinToString("\n")
        assertTrue(!toolFree.contains(CoachGoalTool.TOOL_NAME))
        assertTrue(CoachGoalTool.promptLines(null, canPropose = false).isEmpty())
    }

    @Test
    fun cardOutcomeReachesHistoryAndSurvivesSaving() {
        val proposal = CoachGoalProposal(plan = CoachGoalTool.parse(recomp, now).plan!!)
        val message = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "Plan", goalProposals = listOf(proposal))
        assertTrue(message.contextContent.contains("waiting for the user to tap Apply"))
        val applied = listOf(message).updatingGoalProposal(message.id, proposal.id) {
            it.copy(status = CoachGoalProposal.Status.APPLIED)
        }!!
        assertTrue(applied.single().contextContent.contains("applied by the user"))
        assertNull(listOf(message).updatingGoalProposal(message.id, "missing") { it })

        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(ChatMessage.serializer())
        val restored = json.decodeFromString(serializer, json.encodeToString(serializer, applied))
        // Message timestamps are stored to the millisecond, so compare the card itself.
        assertEquals(applied.single().goalProposals, restored.single().goalProposals)
        // Messages saved before goal cards existed still load.
        val old = json.decodeFromString(serializer, """[{"role":"assistant","content":"Hola"}]""")
        assertTrue(old.single().goalProposals.isEmpty())
    }

    @Test
    fun unknownTrainingGoalFallsBackToHypertrophy() {
        assertEquals(TrainingGoal.HYPERTROPHY, CoachGoalPlan(summary = "x", trainingGoal = "YOGA").training)
    }
}
