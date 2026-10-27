package com.apoorvdarshan.calorietracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.apoorvdarshan.calorietracker.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A date field that opens the calendar instead of asking for typed "yyyy-MM-dd". It shows the
 * date in the user's locale ("27 oct 2026") and reports the pick as ISO yyyy-MM-dd, so callers
 * that keep ISO text keep working. Dates after [maxDate] cannot be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDateField(
    isoDate: String,
    onIsoDateChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxDate: LocalDate = LocalDate.now()
) {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull()
    var open by remember { mutableStateOf(false) }
    val shown = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: isoDate

    Box(modifier) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) }
        )
        // The read-only field does not take taps itself; this layer opens the calendar.
        Box(Modifier.matchParentSize().clickable(enabled = enabled) { open = true })
    }

    if (open) {
        val maxMillis = maxDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: maxDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxMillis
                override fun isSelectableYear(year: Int) = year <= maxDate.year
            }
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onIsoDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) {
            DatePicker(state = state)
        }
    }
}
