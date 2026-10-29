package com.apoorvdarshan.calorietracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apoorvdarshan.calorietracker.R
import com.apoorvdarshan.calorietracker.ui.theme.AppColors

/** Which camera the food "Scan" screen shows. */
enum class ScanTab { FOOD, BARCODE }

/**
 * Top of the food scanner: back arrow, "Scan" title and the two tabs, food photo or barcode,
 * so either camera switches to the other without going back to the menu.
 */
@Composable
fun ScanHeader(selected: ScanTab, onBack: () -> Unit, onSelect: (ScanTab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.scan_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        val shape = RoundedCornerShape(14.dp)
        // Solid pill so its shadow doesn't show through: white card in light mode, raised gray in dark.
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val activePill = if (dark) Color(0xFF2C2C2E) else MaterialTheme.colorScheme.surface
        Row(
            Modifier
                .padding(start = 8.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                .padding(4.dp)
        ) {
            listOf(ScanTab.FOOD to R.string.scan_tab_food, ScanTab.BARCODE to R.string.scan_tab_barcode).forEach { (tab, label) ->
                val active = tab == selected
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (active) Modifier.shadow(2.dp, RoundedCornerShape(11.dp)) else Modifier)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (active) activePill else Color.Transparent)
                        .clickable(enabled = !active) { onSelect(tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(label),
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) AppColors.Calorie else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

/**
 * With the Scan tabs, the camera sits as a rounded card on the app background, so the screen
 * follows light or dark mode like the rest of the app.
 */
fun Modifier.scanCameraFrame(tabbed: Boolean): Modifier =
    if (tabbed) padding(start = 12.dp, end = 12.dp, bottom = 12.dp).clip(RoundedCornerShape(24.dp)) else this

/** "Need help?" pill over the camera, with short tips for a good scan. */
@Composable
fun ScanHelpChip(tab: ScanTab, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = AppColors.Calorie, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.scan_help), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    if (open) {
        val tips = if (tab == ScanTab.FOOD) {
            listOf(R.string.scan_tip_food_light, R.string.scan_tip_food_whole, R.string.scan_tip_food_note, R.string.scan_tip_food_packaged)
        } else {
            listOf(R.string.scan_tip_barcode_close, R.string.scan_tip_barcode_light, R.string.scan_tip_barcode_missing)
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.scan_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tips.forEach { Text("• " + stringResource(it), textAlign = TextAlign.Start) }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.info_button_ok)) } }
        )
    }
}
