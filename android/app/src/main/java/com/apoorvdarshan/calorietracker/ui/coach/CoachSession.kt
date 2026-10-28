package com.apoorvdarshan.calorietracker.ui.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Coach's draft and the reply being written, kept for the whole app run. The Coach screen and its
 * ViewModel go away when the phone rotates or the user opens another tab; the typed text, the
 * attached photo and the request to the AI must not go with them.
 */
class CoachSession {
    /** Sends run here, not in the ViewModel, so leaving the screen never cancels a reply. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var draft by mutableStateOf("")
    var attachment by mutableStateOf<ByteArray?>(null)

    val sending = MutableStateFlow(false)
    val error = MutableStateFlow<CoachError?>(null)
}
