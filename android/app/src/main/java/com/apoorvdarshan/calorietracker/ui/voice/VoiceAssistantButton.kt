package com.apoorvdarshan.calorietracker.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.services.voice.OrbMode
import com.apoorvdarshan.calorietracker.ui.theme.AppColors

/** The shared floating entry point on Home and the workout diary. */
@Composable
fun VoiceAssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp, paused: Boolean = false) {
    val label = stringResource(R.string.voice_talk_to_fud_ai)
    // No square ripple/focus box around the sphere: the orb itself shrinks a little while pressed.
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.9f else 1f, label = "orbPress")
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .testTag("voiceAssistantButton")
    ) {
        LiveOrb(
            mode = OrbMode.IDLE,
            tint = AppColors.Calorie,
            // The sphere occupies 56% of its canvas. Match the add button's
            // visible diameter while keeping this button's existing bounds.
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1f / 0.56f
                scaleY = 1f / 0.56f
            },
            clearBackground = true,
            paused = paused
        )
    }
}
