package com.apoorvdarshan.calorietracker.ui.home

import android.text.format.DateFormat
import com.apoorvdarshan.calorietracker.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Amber, so it reads as "check this", not as an error. */
private val NoticeColor = Color(0xFFF5A623)

/** "Tuesday, October 27" in the user's language, for use mid-sentence. */
internal fun dayLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale))

/**
 * Says that what is added goes to [date], not today, with a one-tap way back to today. Shown on
 * Home under the week strip and on the food review sheet, so a day picked by accident is noticed
 * before the food lands there.
 */
@Composable
internal fun OtherDayNotice(text: String, actionText: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NoticeColor.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(Icons.Filled.EventNote, contentDescription = null, tint = NoticeColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            actionText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = NoticeColor,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAction)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun HomeOtherDayNotice(date: LocalDate, onToday: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val text = stringResource(
        if (date.isBefore(today)) R.string.home_other_day_past else R.string.home_other_day_future,
        dayLabel(date)
    )
    OtherDayNotice(text, stringResource(R.string.home_back_to_today), onToday, modifier)
}

@Composable
internal fun SheetOtherDayNotice(date: LocalDate, onToday: () -> Unit, modifier: Modifier = Modifier) {
    OtherDayNotice(
        stringResource(R.string.sheet_other_day, dayLabel(date)),
        stringResource(R.string.sheet_use_today),
        onToday,
        modifier
    )
}
