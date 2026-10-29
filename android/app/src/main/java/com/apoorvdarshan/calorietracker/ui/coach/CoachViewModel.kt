package com.apoorvdarshan.calorietracker.ui.coach

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apoorvdarshan.calorietracker.AppContainer
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.data.ExerciseItem
import com.apoorvdarshan.calorietracker.data.ExerciseRepository
import com.apoorvdarshan.calorietracker.models.ChatMessage
import com.apoorvdarshan.calorietracker.models.CoachGoalCollector
import com.apoorvdarshan.calorietracker.models.CoachGoalProposal
import com.apoorvdarshan.calorietracker.models.CoachLogCollector
import com.apoorvdarshan.calorietracker.models.CoachLogItem
import com.apoorvdarshan.calorietracker.models.CoachLogProposal
import com.apoorvdarshan.calorietracker.models.CoachProposalCollector
import com.apoorvdarshan.calorietracker.models.CurrentMealSchedule
import com.apoorvdarshan.calorietracker.models.WeightGoal
import com.apoorvdarshan.calorietracker.models.WorkoutWeightUnit
import com.apoorvdarshan.calorietracker.models.withGoalPlan
import com.apoorvdarshan.calorietracker.services.ai.AiError
import com.apoorvdarshan.calorietracker.services.ai.CoachLogService
import com.apoorvdarshan.calorietracker.services.ai.CoachProposalApplier
import com.apoorvdarshan.calorietracker.services.voice.AndroidVoiceAppPorts
import com.apoorvdarshan.calorietracker.services.voice.BarcodeOutcome
import com.apoorvdarshan.calorietracker.services.voice.CameraOutcome
import com.apoorvdarshan.calorietracker.services.voice.PhotoOutcome
import com.apoorvdarshan.calorietracker.services.voice.PickOutcome
import com.apoorvdarshan.calorietracker.services.voice.VoiceCameraMode
import com.apoorvdarshan.calorietracker.services.voice.VoiceUiBridge
import com.apoorvdarshan.calorietracker.ui.workouts.TrainingGoalPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.apoorvdarshan.calorietracker.models.stepsByDay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

/**
 * Sealed wrapper around chip text — either a resource (for our preset chips,
 * so they translate) or a literal string (for user-typed sends, which already
 * go through the localized input path).
 */
sealed class CoachError {
    data class FromResource(val resId: Int) : CoachError()
    data class Literal(val message: String) : CoachError()
}

data class CoachUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    val suggestions: List<Int> = emptyList(),
    /** Proposal cards whose Add or Dismiss is being saved; their buttons are disabled meanwhile. */
    val resolvingProposalIds: Set<String> = emptySet(),
    /** Log cards being estimated or saved; their buttons are disabled meanwhile. */
    val busyLogProposalIds: Set<String> = emptySet(),
    /** Goal cards whose Apply or Dismiss is being saved. */
    val busyGoalProposalIds: Set<String> = emptySet()
)

class CoachViewModel(private val container: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(CoachUiState())
    val ui: StateFlow<CoachUiState> = _ui.asStateFlow()

    /** The voice assistant's app ports, so a log from the chat takes exactly the same write paths. */
    private val logService by lazy {
        CoachLogService(AndroidVoiceAppPorts(container, NoVoiceUi)) { CurrentMealSchedule.value }
    }

    /** Log cards whose estimation runs in this ViewModel, so one card is never estimated twice. */
    private val preparingLogIds = mutableSetOf<String>()

    private val session = container.coachSession

    init {
        // A reply started before a rotation or a tab change is still "sending" here.
        combine(session.sending, session.error) { sending, error -> sending to error }
            .onEach { (sending, error) ->
                _ui.value = _ui.value.copy(
                    sending = sending,
                    error = (error as? CoachError.Literal)?.message,
                    errorRes = (error as? CoachError.FromResource)?.resId
                )
            }
            .launchIn(viewModelScope)

        container.chatRepository.messages
            .onEach { messages ->
                _ui.value = _ui.value.copy(messages = messages)
                // Cards left estimating (the app closed mid-way) resume here.
                messages.forEach { message ->
                    message.logProposals
                        .filter { it.status == CoachLogProposal.Status.PREPARING && it.id !in preparingLogIds }
                        .forEach { prepareLogProposal(message.id, it) }
                }
            }
            .launchIn(viewModelScope)

        // Keep chips current when either the user's goal or their workout history changes.
        combine(
            container.profileRepository.profile,
            container.workoutRepository.completedSessions,
            container.prefs.coachGoalPlan
        ) { profile, sessions, plan ->
            // Until a goal plan exists, the first chip starts the goal conversation.
            val chips = chipsFor(profile?.goal, hasWorkoutSessions = sessions.isNotEmpty())
            if (plan == null) listOf(R.string.cg_chip_explain_goal) + chips else chips
        }
            .onEach { suggestions -> _ui.value = _ui.value.copy(suggestions = suggestions) }
            .launchIn(viewModelScope)
    }

    private fun chipsFor(goal: WeightGoal?, hasWorkoutSessions: Boolean): List<Int> {
        val goalChips = when (goal) {
            WeightGoal.LOSE -> listOf(
                R.string.coach_chip_predict_30_days,
                R.string.coach_chip_lose_faster,
                R.string.coach_chip_eating_too_much,
                R.string.coach_chip_what_dinner
            )
            WeightGoal.GAIN -> listOf(
                R.string.coach_chip_predict_30_days,
                R.string.coach_chip_gain_healthy,
                R.string.coach_chip_eating_enough,
                R.string.coach_chip_high_protein
            )
            WeightGoal.MAINTAIN -> listOf(
                R.string.coach_chip_holding_weight,
                R.string.coach_chip_average_intake,
                R.string.coach_chip_macro_suggestions,
                R.string.coach_chip_trend
            )
            else -> listOf(
                R.string.coach_chip_doing_this_week,
                R.string.coach_chip_predict_30_days,
                R.string.coach_chip_log_advice
            )
        }
        return if (hasWorkoutSessions) listOf(R.string.coach_chip_training) + goalChips else goalChips
    }

    /** The suggested-message row above the input: off in Settings, or hidden for 24 h after the first X. */
    val suggestionChipsVisible: kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.combine(container.prefs.coachChipsEnabled, container.prefs.coachChipsHiddenUntil) { enabled, until ->
            enabled && System.currentTimeMillis() >= until
        }

    fun hideSuggestionChips() {
        viewModelScope.launch { container.prefs.hideCoachChips() }
    }

    fun send(userText: String, imageBytes: ByteArray? = null, thumbnailBytes: ByteArray? = null) {
        val trimmed = userText.trim()
        if ((trimmed.isBlank() && imageBytes == null) || session.sending.value) return
        val text = trimmed.ifEmpty { container.appContext.getString(R.string.st_coach_analyze_image) }
        session.sending.value = true
        session.error.value = null
        session.scope.launch {
            val userMsg = ChatMessage(
                role = ChatMessage.Role.USER,
                content = text,
                attachmentImageBase64 = thumbnailBytes?.let { Base64.getEncoder().encodeToString(it) }
            )
            val started = System.nanoTime()
            try {
                container.chatRepository.append(userMsg)
                val history = container.chatRepository.contextMessages(limit = 20).dropLast(1) // exclude the just-appended user msg — it's passed separately
                val profile = container.profileRepository.current()
                    ?: return@launch run {
                        session.error.value = CoachError.FromResource(R.string.coach_no_profile_error)
                    }
                val weights = container.weightRepository.entries.first()
                val bodyFats = container.bodyFatRepository.entries.first()
                val measurements = container.bodyMeasurementRepository.entries.first()
                val foods = container.foodRepository.entries.first()
                val fastingSessions = container.fastingRepository.sessions.first()
                val heightMetric = container.prefs.heightUnit.first() == "cm"
                val weightMetric = container.prefs.weightUnit.first() == "kg"
                val workoutState = container.workoutRepository.snapshot()
                val exerciseLibrary = currentExerciseLibrary()
                val proposals = CoachProposalCollector()
                val logProposals = CoachLogCollector()
                val goalProposals = CoachGoalCollector()
                val appContext = container.appContext

                val reply = container.chatService.sendMessage(
                    history = history,
                    newUserMessage = text,
                    profile = profile,
                    weights = weights,
                    bodyFats = bodyFats,
                    measurements = measurements,
                    foods = foods,
                    fastingSessions = fastingSessions,
                    heightMetric = heightMetric,
                    weightMetric = weightMetric,
                    imageBytes = imageBytes,
                    workoutSessions = workoutState.completedSessions,
                    workoutPlans = workoutState.dayPlans.values.toList(),
                    workoutPreferences = workoutState.preferences,
                    workoutPlanWeightUnit = if (weightMetric) WorkoutWeightUnit.KG else WorkoutWeightUnit.LBS,
                    exerciseLibrary = exerciseLibrary,
                    proposals = proposals,
                    logProposals = logProposals,
                    goalProposals = goalProposals,
                    extraContext = coachExtraContext(),
                    appLanguage = appContext.getString(R.string.cl_prompt_language),
                    logButtonLabel = appContext.getString(R.string.cl_action_log),
                    pantryItems = container.pantryRepository.snapshot(),
                    groceryPurchases = container.pantryRepository.purchaseSnapshot(),
                    shoppingList = container.pantryRepository.shoppingSnapshot(),
                    pantryWriter = { changes -> session.scope.launch { container.pantryRepository.applyChanges(changes) } },
                    purchaseWriter = { purchase -> session.scope.launch { container.pantryRepository.recordPurchase(purchase) } },
                    cycle = container.cycleRepository.coachSnapshot()
                )
                val logCards = logProposals.proposals
                val goalCards = goalProposals.proposals
                val content = reply.trim().ifEmpty {
                    when {
                        logCards.isNotEmpty() -> appContext.getString(R.string.cl_reply_review_card)
                        goalCards.isNotEmpty() -> appContext.getString(R.string.cg_reply_review_card)
                        else -> reply.trim()
                    }
                }
                container.chatRepository.append(
                    ChatMessage(
                        role = ChatMessage.Role.ASSISTANT,
                        content = content,
                        proposals = proposals.proposals,
                        logProposals = logCards,
                        goalProposals = goalCards
                    )
                )
                // Beta log: how the reply went, never what was said.
                com.apoorvdarshan.calorietracker.services.telemetry.Telemetry.track(
                    "coach_reply",
                    mapOf(
                        "ok" to true,
                        "ms" to (System.nanoTime() - started) / 1_000_000,
                        "image" to (imageBytes != null),
                        "workout_cards" to proposals.proposals.size,
                        "log_cards" to logCards.size,
                        "goal_cards" to goalCards.size
                    )
                )
            } catch (e: AiError) {
                logFailure(e)
                // Hosted quota / no-plan errors open the paywall flow; others show localized text.
                val routed = container.hostedUi.handle(e)
                if (!routed) session.error.value = CoachError.Literal(e.userMessage(container.appContext))
            } catch (e: Throwable) {
                ensureActive()
                logFailure(e)
                session.error.value = e.localizedMessage?.takeIf { it.isNotBlank() }?.let { CoachError.Literal(it) }
                    ?: CoachError.FromResource(R.string.coach_chat_failed)
            } finally {
                session.sending.value = false
            }
        }
    }

    /**
     * The user confirmed a Coach proposal. The repository runs the write only while the card is
     * pending, so a second tap (or a card already added) never writes again.
     */
    fun addProposal(messageId: UUID, proposalId: String) = resolveProposal(proposalId) {
        var failure: Int? = null
        container.chatRepository.addProposal(messageId, proposalId) { proposal ->
            when (CoachProposalApplier.apply(proposal, container.workoutRepository, container.imageStore, currentExerciseLibrary())) {
                CoachProposalApplier.Outcome.APPLIED -> true
                CoachProposalApplier.Outcome.EXERCISE_MISSING -> {
                    failure = R.string.coach_proposal_exercise_missing
                    false
                }
                CoachProposalApplier.Outcome.NAME_TAKEN -> {
                    failure = R.string.coach_proposal_name_taken
                    false
                }
                CoachProposalApplier.Outcome.SAVE_FAILED -> {
                    failure = R.string.coach_proposal_save_failed
                    false
                }
            }
        }
        failure?.let { _ui.value = _ui.value.copy(error = null, errorRes = it) }
    }

    fun dismissProposal(messageId: UUID, proposalId: String) = resolveProposal(proposalId) {
        container.chatRepository.dismissProposal(messageId, proposalId)
    }

    private fun resolveProposal(proposalId: String, work: suspend () -> Unit) {
        if (proposalId in _ui.value.resolvingProposalIds) return
        _ui.value = _ui.value.copy(resolvingProposalIds = _ui.value.resolvingProposalIds + proposalId, error = null, errorRes = null)
        viewModelScope.launch {
            try {
                work()
            } catch (e: Throwable) {
                ensureActive()
                logFailure(e)
                _ui.value = _ui.value.copy(error = null, errorRes = R.string.coach_proposal_save_failed)
            } finally {
                _ui.value = _ui.value.copy(resolvingProposalIds = _ui.value.resolvingProposalIds - proposalId)
            }
        }
    }

    /** Sleep (when its advice is on), muscle recovery and where the user trains, for Coach's prompt. */
    private suspend fun coachExtraContext(): List<String> {
        val sleep = runCatching { container.sleepService.adviceIfEnabled() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull()
            ?.let { com.apoorvdarshan.calorietracker.services.sleep.SleepAdvisor.promptLines(it) }
            .orEmpty()
        val recovery = runCatching { container.trainingLoad.readiness() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull()
            ?.let { com.apoorvdarshan.calorietracker.models.MuscleRecovery.promptLines(it) }
            .orEmpty()
        val place = TrainingGoalPrefs.setup(container.appContext).promptLines(java.time.Instant.now())
        val steps = runCatching { stepLines() }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull()
            .orEmpty()
        return listOf(sleep, recovery, place, steps).filter { it.isNotEmpty() }.flatMap { it + "" }
    }

    /** Today's and yesterday's steps per app, so Coach adds only what the watch has not sent yet. */
    private suspend fun stepLines(): List<String> {
        if (!container.prefs.healthConnectEnabled.first() || !container.health.hasStepsRead()) return emptyList()
        val manual = container.prefs.manualSteps.first().stepsByDay()
        val today = java.time.LocalDate.now()
        val days = listOf("Today ($today)" to today, "Yesterday (${today.minusDays(1)})" to today.minusDays(1)).map { (label, day) ->
            val extra = manual[day] ?: 0
            val measured = container.health.readStepsForDay(day)
            com.apoorvdarshan.calorietracker.services.health.StepPromptLines.Day(
                label = label,
                counted = measured?.plus(extra),
                manual = extra,
                bySource = container.health.readStepSourcesForDay(day)?.bySource.orEmpty()
            )
        }
        return com.apoorvdarshan.calorietracker.services.health.StepPromptLines.lines(days) { container.health.appLabel(it) ?: it }
    }

    // MARK: - Goal cards

    /**
     * Apply on a goal card: sets the profile's weight goal (as Settings' goal picker would), the
     * machine coach's training goal, and saves the plan Coach reads in every later conversation.
     */
    fun applyGoalProposal(messageId: UUID, proposalId: String) = runGoalAction(proposalId) {
        val applied = container.chatRepository.updateGoalProposal(messageId, proposalId) { current ->
            if (current.status != CoachGoalProposal.Status.PENDING) return@updateGoalProposal null
            current.copy(status = CoachGoalProposal.Status.APPLIED)
        } ?: return@runGoalAction
        val plan = applied.plan
        container.profileRepository.current()?.let { container.profileRepository.save(it.withGoalPlan(plan)) }
        TrainingGoalPrefs.write(container.appContext, plan.training)
        plan.place?.let { TrainingGoalPrefs.writeSetup(container.appContext, it, plan.homeEquipment.toSet()) }
        container.prefs.setCoachGoalPlan(plan)
        container.prefs.setCoachGoalPromptDismissed(true)
    }

    fun dismissGoalProposal(messageId: UUID, proposalId: String) = runGoalAction(proposalId) {
        container.chatRepository.updateGoalProposal(messageId, proposalId) { current ->
            if (current.status == CoachGoalProposal.Status.PENDING) current.copy(status = CoachGoalProposal.Status.DISMISSED) else null
        }
    }

    /** Opens the goal conversation: the user's first message asks Coach to understand their goal. */
    fun startGoalInterview() {
        if (_ui.value.sending) return
        send(container.appContext.getString(R.string.cg_interview_opener))
    }

    private fun runGoalAction(proposalId: String, work: suspend () -> Unit) {
        if (proposalId in _ui.value.busyGoalProposalIds) return
        _ui.value = _ui.value.copy(busyGoalProposalIds = _ui.value.busyGoalProposalIds + proposalId, error = null, errorRes = null)
        viewModelScope.launch {
            try {
                work()
            } catch (e: Throwable) {
                ensureActive()
                logFailure(e)
                _ui.value = _ui.value.copy(error = null, errorRes = R.string.cg_error_apply)
            } finally {
                _ui.value = _ui.value.copy(busyGoalProposalIds = _ui.value.busyGoalProposalIds - proposalId)
            }
        }
    }

    // MARK: - Log cards

    /**
     * Estimates a new card's foods with Home's text analysis (and reads its workouts) outside the
     * chat lock, then stores the result if the card is still waiting for it.
     */
    private fun prepareLogProposal(messageId: UUID, proposal: CoachLogProposal) {
        if (!preparingLogIds.add(proposal.id)) return
        markLogBusy(proposal.id, true)
        viewModelScope.launch {
            try {
                val prepared = logService.prepare(proposal)
                container.chatRepository.updateLogProposal(messageId, proposal.id) { current ->
                    if (current.status == CoachLogProposal.Status.PREPARING) prepared else null
                }
            } catch (e: Throwable) {
                ensureActive()
                logFailure(e)
                // Keep the model's own estimates so the user can still log or edit them.
                container.chatRepository.updateLogProposal(messageId, proposal.id) { current ->
                    if (current.status == CoachLogProposal.Status.PREPARING) current.copy(status = CoachLogProposal.Status.READY) else null
                }
            } finally {
                preparingLogIds.remove(proposal.id)
                markLogBusy(proposal.id, false)
            }
        }
    }

    /** The user tapped Log: writes every included row once, under the chat lock. */
    fun logProposal(messageId: UUID, proposalId: String) = runLogAction(proposalId) {
        var outcome: CoachLogService.LogOutcome? = null
        container.chatRepository.updateLogProposal(messageId, proposalId) { current ->
            if (current.status != CoachLogProposal.Status.READY || current.loggableItems.isEmpty()) return@updateLogProposal null
            logService.log(current).also { outcome = it }.proposal
        }
        val result = outcome ?: return@runLogAction
        if (result.failed > 0) {
            _ui.value = _ui.value.copy(
                error = null,
                errorRes = if (result.foodBlockedByFast) R.string.cl_error_fasting else R.string.cl_error_some_failed
            )
        }
    }

    fun dismissLogProposal(messageId: UUID, proposalId: String) = runLogAction(proposalId) {
        container.chatRepository.updateLogProposal(messageId, proposalId) { current ->
            if (current.status == CoachLogProposal.Status.READY || current.status == CoachLogProposal.Status.PREPARING) {
                current.copy(status = CoachLogProposal.Status.DISMISSED)
            } else null
        }
    }

    /** Includes or leaves out one row before logging. */
    fun toggleLogItem(messageId: UUID, proposalId: String, itemId: String) = runLogAction(proposalId) {
        container.chatRepository.updateLogProposal(messageId, proposalId) { current ->
            if (current.status != CoachLogProposal.Status.READY) return@updateLogProposal null
            current.copy(items = current.items.map { item ->
                if (item.id == itemId && item.state == CoachLogItem.State.PENDING) item.copy(included = !item.included) else item
            })
        }
    }

    /**
     * Edit on a food row: opens Home's review sheet with the estimate, where the user adjusts the
     * portion, meal and nutrition and logs it. The row is then left out of this card.
     */
    fun editLogItem(messageId: UUID, proposalId: String, itemId: String) = runLogAction(proposalId) {
        var handOff: Pair<CoachLogItem, com.apoorvdarshan.calorietracker.services.ai.FoodAnalysis>? = null
        container.chatRepository.updateLogProposal(messageId, proposalId) { current ->
            if (current.status != CoachLogProposal.Status.READY) return@updateLogProposal null
            val item = current.items.firstOrNull { it.id == itemId && it.state == CoachLogItem.State.PENDING }
                ?: return@updateLogProposal null
            val analysis = item.reviewAnalysis() ?: return@updateLogProposal null
            handOff = item to analysis
            current.copy(items = current.items.map {
                if (it.id == itemId) it.copy(state = CoachLogItem.State.HANDED_OFF, included = false) else it
            })
        }
        handOff?.let { (item, analysis) ->
            container.navigator.showFoodReview(analysis, LocalDate.parse(item.date))
        }
    }

    /** A logged food row opens its diary entry's edit sheet on Home. */
    fun openLoggedFood(item: CoachLogItem) {
        if (item.kind != CoachLogItem.Kind.FOOD || item.state != CoachLogItem.State.LOGGED) return
        val id = runCatching { UUID.fromString(item.id) }.getOrNull() ?: return
        container.navigator.showFoodEntry(id, LocalDate.parse(item.date))
    }

    /** "Send to food log": Home's Describe food dialog with the message's plain text typed in. */
    fun sendToFoodLog(message: ChatMessage) {
        val text = CoachMessageText.plain(message.content).take(MAX_FOOD_TEXT_CHARS)
        if (text.isBlank()) return
        container.navigator.showFoodText(text)
    }

    private fun runLogAction(proposalId: String, work: suspend () -> Unit) {
        if (proposalId in _ui.value.busyLogProposalIds) return
        markLogBusy(proposalId, true)
        _ui.value = _ui.value.copy(error = null, errorRes = null)
        viewModelScope.launch {
            try {
                work()
            } catch (e: Throwable) {
                ensureActive()
                logFailure(e)
                _ui.value = _ui.value.copy(error = null, errorRes = R.string.cl_error_some_failed)
            } finally {
                markLogBusy(proposalId, false)
            }
        }
    }

    private fun markLogBusy(proposalId: String, busy: Boolean) {
        val ids = _ui.value.busyLogProposalIds
        _ui.value = _ui.value.copy(busyLogProposalIds = if (busy) ids + proposalId else ids - proposalId)
    }

    /** Built-in catalog plus the user's custom activities and exercises. */
    private suspend fun currentExerciseLibrary(): List<ExerciseItem> {
        val workoutState = container.workoutRepository.snapshot()
        return withContext(Dispatchers.IO) {
            ExerciseRepository.get(container.appContext)
                .includingActivities(workoutState.customActivities + workoutState.userExercises)
                .exercises
        }
    }

    fun resetConversation() {
        viewModelScope.launch { container.chatRepository.clear() }
    }

    fun dismissError() {
        session.error.value = null
        _ui.value = _ui.value.copy(error = null, errorRes = null)
    }

    private fun logFailure(error: Throwable) {
        // Log is a stub in JVM unit tests; logging must never change the send's outcome.
        runCatching { Log.w(TAG, "Coach chat failed: ${coachFailureSummary(error)}") }
        com.apoorvdarshan.calorietracker.services.telemetry.Telemetry.track(
            "coach_reply",
            mapOf("ok" to false, "error" to ((error as? AiError)?.kind?.name ?: error::class.java.simpleName))
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CoachViewModel(container) as T
    }

    private companion object {
        const val TAG = "FudAICoach"
        const val MAX_FOOD_TEXT_CHARS = 2_000
    }
}

/** Coach logs never open the camera or photo picker, so the voice ports get a UI that has none. */
private object NoVoiceUi : VoiceUiBridge {
    override fun collapseForNavigation() = Unit
    override fun cameraMode(): VoiceCameraMode? = null
    override suspend fun openCamera(mode: VoiceCameraMode): CameraOutcome = CameraOutcome.Unavailable
    override fun closeCamera() = Unit
    override suspend fun takePhoto(): PhotoOutcome = PhotoOutcome.NotOpen
    override suspend fun scanBarcode(): BarcodeOutcome = BarcodeOutcome.Unavailable
    override suspend fun pickPhotos(maxCount: Int): PickOutcome = PickOutcome.Cancelled
}

/**
 * Why a send failed, for Logcat: exception types (with causes) and the top code locations.
 * Messages are left out because provider/HTTP errors can carry URLs, response text or keys.
 */
internal fun coachFailureSummary(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.take(5).joinToString(" <- ") { it.javaClass.name }
    val frames = error.stackTrace.take(3).joinToString(" | ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
    return if (frames.isEmpty()) causes else "$causes at $frames"
}
