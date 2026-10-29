package com.apoorvdarshan.calorietracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One removal the user can still take back. */
class UndoEvent(val message: String, val undo: suspend () -> Unit) {
    val id: Long = System.nanoTime()
}

/** Holds the latest removal; a newer one replaces it (the older removal stays done). */
class UndoController {
    private val _event = MutableStateFlow<UndoEvent?>(null)
    val event: StateFlow<UndoEvent?> = _event.asStateFlow()

    fun offer(message: String, undo: suspend () -> Unit) {
        _event.value = UndoEvent(message, undo)
    }

    fun dismiss(event: UndoEvent) {
        if (_event.value?.id == event.id) _event.value = null
    }
}

const val UndoVisibleMillis = 5_000L

/**
 * Bottom pill "<message> · Undo" that disappears after [UndoVisibleMillis]. Put one in each
 * window that can delete things (the app root and every bottom sheet); the one on top is seen.
 */
@Composable
fun BoxScope.UndoBar(controller: UndoController, bottomPadding: Dp = 96.dp) {
    val event by controller.event.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(event?.id) {
        val shown = event ?: return@LaunchedEffect
        delay(UndoVisibleMillis)
        controller.dismiss(shown)
    }
    AnimatedVisibility(
        visible = event != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding)
    ) {
        val shown = event ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape)
                .background(Color(0xFF26262B), shape)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shown.message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                controller.dismiss(shown)
                scope.launch { shown.undo() }
            }) {
                Text(stringResource(R.string.undo), color = Color(0xFFFFB38A), fontWeight = FontWeight.Bold)
            }
        }
    }
}
