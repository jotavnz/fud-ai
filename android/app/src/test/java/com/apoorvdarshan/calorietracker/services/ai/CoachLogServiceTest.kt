package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.CoachLogMacros
import com.apoorvdarshan.calorietracker.models.CoachLogProposal
import com.apoorvdarshan.calorietracker.models.FastingSession
import com.apoorvdarshan.calorietracker.models.FoodSource
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.WorkoutTextDraft
import com.apoorvdarshan.calorietracker.models.WorkoutTextExercise
import com.apoorvdarshan.calorietracker.services.voice.FakeVoiceAppPorts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class CoachLogServiceTest {
    // FakeVoiceAppPorts: 2026-10-16 12:30 UTC; meals: <11 breakfast, <16 lunch, else dinner.
    private val ports = FakeVoiceAppPorts()
    private val service = CoachLogService(ports)
    private val today = "2026-10-16"

    private fun food(description: String, meal: MealType? = null, estimate: CoachLogMacros? = null) = CoachLogItem(
        kind = CoachLogItem.Kind.FOOD, description = description, name = description, meal = meal, date = today, estimate = estimate
    )

    @Test
    fun prepareEstimatesFoodsWithTheTextAnalysis() = runTest {
        val card = CoachLogProposal(items = listOf(food("2 ham sandwiches", MealType.BREAKFAST), food("mate")))
        val ready = service.prepare(card)
        assertEquals(CoachLogProposal.Status.READY, ready.status)
        // FakeVoiceAppPorts echoes the description as the analysis name.
        assertEquals(listOf("2 ham sandwiches", "mate"), ready.items.map { it.analysis?.name })
        assertEquals(320, ready.items[0].effectiveMacros?.calories)
    }

    @Test
    fun analysisFailureKeepsTheModelsEstimateOrLeavesTheRowOut() = runTest {
        ports.analysisError = AiError.InvalidResponse
        val card = CoachLogProposal(
            items = listOf(food("empanada", estimate = CoachLogMacros(300, 9.0, 30.0, 15.0)), food("mystery dish"))
        )
        val ready = service.prepare(card)
        val (estimated, failed) = ready.items
        assertEquals(CoachLogItem.State.PENDING, estimated.state)
        assertNull(estimated.analysis)
        assertEquals(300, estimated.effectiveMacros?.calories)
        assertEquals(CoachLogItem.State.FAILED, failed.state)
        assertFalse(failed.included)
        assertEquals(CoachLogService.ERROR_ANALYSIS, failed.error)
        // Only the estimated food is logged.
        assertEquals(listOf(estimated), ready.loggableItems)
    }

    @Test
    fun logWritesEveryIncludedRowOnceInTheRightMeal() = runTest {
        val workoutDraft = WorkoutTextDraft(today, listOf(WorkoutTextExercise(exerciseId = null, name = "Bench Press")))
        ports.workoutDraft = workoutDraft
        val card = service.prepare(
            CoachLogProposal(
                items = listOf(
                    food("2 ham sandwiches", MealType.BREAKFAST),
                    food("milanesa sandwich"),
                    food("left out").copy(included = false),
                    CoachLogItem(kind = CoachLogItem.Kind.WATER, waterMl = 750, date = today),
                    CoachLogItem(kind = CoachLogItem.Kind.WEIGHT, weightKg = 80.0, date = today),
                    CoachLogItem(kind = CoachLogItem.Kind.BODY_FAT, bodyFatPercent = 20.0, date = today),
                    CoachLogItem(kind = CoachLogItem.Kind.WORKOUT, description = "bench 3x10", date = today),
                    CoachLogItem(kind = CoachLogItem.Kind.CARDIO, name = "Walk", minutes = 30, date = today)
                )
            )
        )
        val outcome = service.log(card)
        assertEquals(0, outcome.failed)
        assertEquals(CoachLogProposal.Status.LOGGED, outcome.proposal.status)

        assertEquals(2, ports.foods.size)
        val breakfast = ports.foods.first { it.name == "2 ham sandwiches" }
        assertEquals(MealType.BREAKFAST, breakfast.mealType)
        assertEquals(FoodSource.TEXT_INPUT, breakfast.source)
        assertEquals(Instant.parse("2026-10-16T07:30:00Z"), breakfast.timestamp)
        assertEquals(UUID.fromString(card.items[0].id), breakfast.id)
        // No meal named: the schedule's meal for now (12:30 → lunch).
        assertEquals(MealType.LUNCH, ports.foods.first { it.name == "milanesa sandwich" }.mealType)

        assertEquals(750, ports.water.single().milliliters)
        assertEquals(80.0, ports.weights.single().weightKg, 0.0)
        assertEquals(0.2, ports.bodyFats.single().bodyFatFraction, 1e-9)
        assertEquals(listOf(today, "Walk:30"), ports.workoutWrites)

        val states = outcome.proposal.items.map { it.state }
        assertEquals(CoachLogItem.State.PENDING, states[2])
        assertEquals(7, states.count { it == CoachLogItem.State.LOGGED })

        // A second tap on a logged card writes nothing.
        val again = service.log(outcome.proposal)
        assertEquals(2, ports.foods.size)
        assertEquals(1, ports.water.size)
        assertEquals(outcome.proposal, again.proposal)
    }

    @Test
    fun aRunningFastBlocksFoodsButNotTheRest() = runTest {
        ports.fasts += FastingSession(startedAt = Instant.parse("2026-10-16T08:00:00Z"), goalMinutes = 960)
        val card = service.prepare(
            CoachLogProposal(
                items = listOf(food("toast"), CoachLogItem(kind = CoachLogItem.Kind.WATER, waterMl = 250, date = today))
            )
        )
        val outcome = service.log(card)
        assertEquals(1, outcome.failed)
        assertTrue(outcome.foodBlockedByFast)
        assertEquals(CoachLogProposal.Status.READY, outcome.proposal.status)
        assertEquals(CoachLogItem.State.PENDING, outcome.proposal.items[0].state)
        assertEquals(CoachLogItem.State.LOGGED, outcome.proposal.items[1].state)
        assertTrue(ports.foods.isEmpty())

        // After the fast ends, Log retries only the food.
        ports.fasts.clear()
        val retry = service.log(outcome.proposal)
        assertEquals(0, retry.failed)
        assertEquals(CoachLogProposal.Status.LOGGED, retry.proposal.status)
        assertEquals(1, ports.foods.size)
        assertEquals(1, ports.water.size)
    }

    @Test
    fun aFoodAlreadyInTheDiaryIsNotAddedTwice() = runTest {
        val card = service.prepare(CoachLogProposal(items = listOf(food("toast"))))
        val entry = service.foodEntry(card.items[0], UUID.fromString(card.items[0].id), ports.now())!!
        ports.foods += entry
        val outcome = service.log(card)
        assertEquals(0, outcome.failed)
        assertEquals(1, ports.foods.size)
    }
}
