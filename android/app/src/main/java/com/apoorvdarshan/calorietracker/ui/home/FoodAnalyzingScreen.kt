package com.apoorvdarshan.calorietracker.ui.home

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * "Analyzing your food…": the photo as a card over a blurred copy of itself, three pulsing
 * dots, and short steps that change while the AI works. Without a photo (text or voice),
 * a search icon takes the photo's place.
 */
@Composable
internal fun FoodAnalyzingScreen(bitmap: Bitmap?, isPhoto: Boolean, onCancel: (() -> Unit)?) {
    val steps = if (isPhoto) {
        listOf(R.string.analyzing_step_ingredients, R.string.analyzing_step_portions, R.string.analyzing_step_macros)
    } else {
        listOf(R.string.analyzing_step_search, R.string.analyzing_step_macros)
    }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(steps) {
        while (true) {
            delay(2_200)
            step = (step + 1) % steps.size
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The same photo, blurred, behind everything (Android 12+ can blur; older ones just dim it).
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier)
            )
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (bitmap != null) {
                val shape = RoundedCornerShape(24.dp)
                Box(
                    Modifier
                        .fillMaxWidth(0.78f)
                        .aspectRatio((bitmap.width.toFloat() / bitmap.height).coerceIn(0.6f, 1.4f))
                        .shadow(18.dp, shape)
                        .clip(shape)
                        .background(Color.White)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Little handle, like a card that can be pulled.
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                    )
                }
            } else {
                Box(
                    Modifier.size(120.dp).clip(CircleShape).background(AppColors.Calorie.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ImageSearch, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(56.dp))
                }
            }
            Spacer(Modifier.height(36.dp))
            PulsingDots()
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(if (isPhoto) R.string.home_analyzing_food else R.string.home_looking_up_nutrition),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = steps[step.coerceIn(steps.indices)],
                transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
                label = "analyzingStep"
            ) { res ->
                Text(
                    stringResource(res),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
            onCancel?.let { cancel ->
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = cancel) {
                    Text(
                        stringResource(R.string.action_cancel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Three brand-colored dots that grow and fade one after another. */
@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, delayMillis = index * 180), RepeatMode.Reverse),
                label = "dot$index"
            )
            Box(
                Modifier
                    .size(12.dp)
                    .scale(0.75f + 0.35f * phase)
                    .clip(CircleShape)
                    .background(AppColors.Calorie.copy(alpha = 0.35f + 0.65f * phase))
            )
        }
    }
    Spacer(Modifier.width(0.dp))
}
