package com.apoorvdarshan.calorietracker.services.ai

import com.apoorvdarshan.calorietracker.models.ManualStepEntry
import com.apoorvdarshan.calorietracker.models.SleepEntry
import java.time.LocalDateTime
import com.apoorvdarshan.calorietracker.models.BodyFatEntry
import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.CoachLogProposal
import com.apoorvdarshan.calorietracker.models.FoodEntry
import com.apoorvdarshan.calorietracker.models.FoodSource
import com.apoorvdarshan.calorietracker.models.MealSchedule
import com.apoorvdarshan.calorietracker.models.MealType
import com.apoorvdarshan.calorietracker.models.WaterEntry
import com.apoorvdarshan.calorietracker.models.WeightEntry
import com.apoorvdarshan.calorietracker.models.WorkoutClarification
import com.apoorvdarshan.calorietracker.services.voice.VoiceAppPorts
import com.apoorvdarshan.calorietracker.services.voice.VoiceArguments
import com.apoorvdarshan.calorietracker.services.voice.VoiceToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Prepares and writes Coach log cards through the voice assistant's app ports, so a log from
 * the chat goes through exactly the same repositories and pipelines as one from the voice
 * assistant: Home's text food analysis, the fasting guard on food writes, the weight-goal
 * celebration, the workout text parser and the cardio burn estimate.
 */
class CoachLogService(
    private val ports: VoiceAppPorts,
    private val schedule: () -> MealSchedule = { MealSchedule.Default }
) {
    /** Result of a Log tap: the updated card and how many items failed. */
    data class LogOutcome(val proposal: CoachLogProposal, val failed: Int, val foodBlockedByFast: Boolean)

    /**
     * Estimates every pending food with the text analysis (and reads every workout with the
     * workout parser). A food whose analysis fails keeps the model's estimate; without one it
     * is marked failed so the card never logs zero calories by accident.
     */
    suspend fun prepare(proposal: CoachLogProposal): CoachLogProposal = coroutineScope {
        val gate = Semaphore(MAX_PARALLEL_ANALYSES)
        val items = proposal.items.map { item ->
            async { gate.withPermit { prepareItem(item) } }
        }.awaitAll()
        proposal.copy(items = items, status = CoachLogProposal.Status.READY)
    }

    private suspend fun prepareItem(item: CoachLogItem): CoachLogItem {
        if (item.state != CoachLogItem.State.PENDING) return item
        return when (item.kind) {
            CoachLogItem.Kind.FOOD -> {
                if (item.analysis != null) return item
                try {
                    item.copy(analysis = ports.analyzeFoodText(item.description.ifBlank { item.name }), error = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (item.estimate != null) item
                    else item.copy(state = CoachLogItem.State.FAILED, included = false, error = ERROR_ANALYSIS)
                }
            }
            CoachLogItem.Kind.WORKOUT -> {
                if (item.workoutDraft != null) return item
                try {
                    item.copy(workoutDraft = ports.describeWorkout(item.description, LocalDate.parse(item.date)), error = null)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (question: WorkoutClarification) {
                    item.copy(state = CoachLogItem.State.FAILED, included = false, error = question.message ?: ERROR_WORKOUT)
                } catch (error: Throwable) {
                    item.copy(state = CoachLogItem.State.FAILED, included = false, error = ERROR_WORKOUT)
                }
            }
            else -> item
        }
    }

    /**
     * Writes every included, pending item. Each row is marked logged as soon as its own write
     * succeeds, so a second tap after a partial failure retries only what is left.
     */
    suspend fun log(proposal: CoachLogProposal): LogOutcome {
        var failed = 0
        var blockedByFast = false
        val items = proposal.items.map { item ->
            if (!item.included || item.state != CoachLogItem.State.PENDING) return@map item
            try {
                when (writeItem(item)) {
                    WriteResult.OK -> item.copy(state = CoachLogItem.State.LOGGED, error = null)
                    WriteResult.BLOCKED_BY_FAST -> {
                        failed++
                        blockedByFast = true
                        item
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failed++
                item.copy(error = (error.message ?: ERROR_SAVE).take(200))
            }
        }
        val anyLogged = items.any { it.state == CoachLogItem.State.LOGGED }
        val status = if (failed == 0 && anyLogged) CoachLogProposal.Status.LOGGED else proposal.status
        return LogOutcome(proposal.copy(items = items, status = status), failed, blockedByFast)
    }

    private enum class WriteResult { OK, BLOCKED_BY_FAST }

    private suspend fun writeItem(item: CoachLogItem): WriteResult {
        val date = LocalDate.parse(item.date)
        val id = runCatching { UUID.fromString(item.id) }.getOrElse { UUID.nameUUIDFromBytes(item.id.toByteArray()) }
        when (item.kind) {
            CoachLogItem.Kind.FOOD -> {
                // A row already in the diary (an earlier tap that saved but did not update the card) is not added twice.
                if (ports.foodEntries().any { it.id == id }) return WriteResult.OK
                val entry = foodEntry(item, id, timestampFor(item, date)) ?: error(ERROR_ANALYSIS)
                if (!ports.addFoodEntry(entry)) return WriteResult.BLOCKED_BY_FAST
            }
            CoachLogItem.Kind.WATER -> {
                if (ports.waterEntries().any { it.id == id }) return WriteResult.OK
                ports.addWater(WaterEntry(id = id, date = timestampFor(item, date), milliliters = item.waterMl ?: error(ERROR_SAVE)))
            }
            CoachLogItem.Kind.WEIGHT ->
                ports.addWeight(WeightEntry(id = id, date = dayTimestamp(item, date), weightKg = item.weightKg ?: error(ERROR_SAVE)))
            CoachLogItem.Kind.BODY_FAT ->
                ports.addBodyFat(
                    BodyFatEntry(id = id, date = dayTimestamp(item, date), bodyFatFraction = (item.bodyFatPercent ?: error(ERROR_SAVE)) / 100.0)
                )
            CoachLogItem.Kind.WORKOUT -> ports.saveWorkout(item.workoutDraft ?: error(ERROR_WORKOUT))
            CoachLogItem.Kind.CARDIO ->
                ports.logCardio(item.displayName, item.minutes ?: error(ERROR_SAVE), item.caloriesBurned, date)
            CoachLogItem.Kind.STEPS ->
                ports.addManualSteps(
                    ManualStepEntry(id = item.id, date = item.date, steps = item.steps ?: error(ERROR_SAVE), note = item.description.ifBlank { null })
                )
            CoachLogItem.Kind.SLEEP -> {
                val start = LocalDateTime.parse(item.sleepStart ?: error(ERROR_SAVE)).atZone(ports.zone()).toInstant()
                val end = LocalDateTime.parse(item.sleepEnd ?: error(ERROR_SAVE)).atZone(ports.zone()).toInstant()
                ports.addSleep(SleepEntry(id = item.id, start = start, end = end))
            }
        }
        return WriteResult.OK
    }

    /** The diary entry for a food row: the analysis like Home's review sheet, else the model's estimate. */
    fun foodEntry(item: CoachLogItem, id: UUID, timestamp: Instant): FoodEntry? {
        val meal = mealFor(item, timestamp)
        item.analysis?.let { analysis ->
            return VoiceToolExecutor.foodEntryFromAnalysis(
                analysis, id, timestamp, meal, servings = 1.0, source = FoodSource.TEXT_INPUT, imageFilenames = emptyList()
            )
        }
        val estimate = item.estimate ?: return null
        return FoodEntry(
            id = id,
            name = item.displayName,
            calories = estimate.calories,
            protein = estimate.protein,
            carbs = estimate.carbs,
            fat = estimate.fat,
            timestamp = timestamp,
            source = FoodSource.TEXT_INPUT,
            mealType = meal,
            servingSizeGrams = item.serving?.let(VoiceArguments::gramsIn),
            customNote = item.serving
        )
    }

    /** The meal the user named, else the one the app's meal schedule gives the log time. */
    fun mealFor(item: CoachLogItem, timestamp: Instant): MealType =
        item.meal ?: ports.mealForTime(timestamp.atZone(ports.zone()).toLocalTime())

    fun timestampFor(item: CoachLogItem, date: LocalDate = LocalDate.parse(item.date)): Instant =
        CoachLogTool.timestamp(
            date = date,
            time = item.time?.let(VoiceArguments::parseTime),
            meal = if (item.kind == CoachLogItem.Kind.FOOD) item.meal else null,
            now = ports.now(),
            zone = ports.zone(),
            schedule = schedule()
        )

    /** Weight and body fat: today means now; another day keeps the current time of day, like the voice tools. */
    private fun dayTimestamp(item: CoachLogItem, date: LocalDate): Instant = timestampFor(item.copy(meal = null), date)

    companion object {
        private const val MAX_PARALLEL_ANALYSES = 3
        const val ERROR_ANALYSIS = "analysis_failed"
        const val ERROR_WORKOUT = "workout_failed"
        const val ERROR_SAVE = "save_failed"
    }
}
