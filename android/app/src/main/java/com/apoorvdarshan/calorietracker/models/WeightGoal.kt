package com.apoorvdarshan.calorietracker.models

import androidx.annotation.StringRes
import com.apoorvdarshan.calorietracker.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WeightGoal {
    @SerialName("lose") LOSE,
    @SerialName("maintain") MAINTAIN,
    @SerialName("gain") GAIN;

    @get:StringRes
    val displayNameRes: Int get() = when (this) {
        LOSE -> R.string.goal_lose
        MAINTAIN -> R.string.goal_maintain
        GAIN -> R.string.goal_gain
    }

    /** What the option means, shown behind its ⓘ button. */
    @get:StringRes
    val infoRes: Int get() = when (this) {
        LOSE -> R.string.goal_info_lose
        MAINTAIN -> R.string.goal_info_maintain
        GAIN -> R.string.goal_info_gain
    }
}
