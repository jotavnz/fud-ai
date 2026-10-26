package com.apoorvdarshan.calorietracker.models

import androidx.annotation.StringRes
import com.apoorvdarshan.calorietracker.R

/**
 * [title] is the English fallback; UI and shortcuts should show the localized [titleRes].
 */
enum class QuickAction(val title: String, @StringRes val titleRes: Int) {
    CAMERA("Camera + Note", R.string.home_menu_camera_note),
    PHOTOS("Photos", R.string.hm_qa_photos),
    VOICE("Voice", R.string.home_menu_voice),
    TEXT("Text", R.string.hm_qa_text),
    BARCODE("Barcode", R.string.home_menu_barcode),
    FAVORITES("Favorites", R.string.saved_meals_tab_favorites),
    FREQUENT("Frequent", R.string.saved_meals_tab_frequent),
    RECENT("Recent", R.string.hm_qa_recent),
    MANUAL("Manual", R.string.hm_qa_manual),
    FASTING("Fasting", R.string.fasting);

    companion object {
        val Defaults = listOf(CAMERA, VOICE, BARCODE)

        fun fromStorage(value: String?, fallback: QuickAction = CAMERA): QuickAction =
            entries.firstOrNull { it.name == value } ?: fallback
    }
}

data class QuickActionRequest(
    val action: QuickAction,
    val id: Long = System.nanoTime()
)
