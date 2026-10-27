package com.apoorvdarshan.calorietracker.ui.util

import android.content.Context
import android.text.format.DateFormat

/**
 * The hour:minute pattern for the device's clock setting — "H:mm" (24-hour) or "h:mm a" (12-hour).
 * Honors Settings → System → Date & time → Use 24-hour format, so a phone set to 24-hour shows
 * "20:30" instead of "8:30 PM". Build a [java.time.format.DateTimeFormatter] from this at the call
 * site (adding `.withZone(...)` when formatting an Instant).
 */
fun clockTimePattern(context: Context): String =
    if (AppTimeFormat.is24(context)) "H:mm" else "h:mm a"

/**
 * The app's clock format from Settings (12 h with a.m./p.m., 24 h, or the phone's setting).
 * Kept in memory so formatting stays synchronous; the app container keeps it in step with the
 * stored preference.
 */
object AppTimeFormat {
    @Volatile
    var preference: String = "system"

    fun is24(context: Context): Boolean = when (preference) {
        "24" -> true
        "12" -> false
        else -> DateFormat.is24HourFormat(context)
    }
}
