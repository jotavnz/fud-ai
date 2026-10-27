package com.apoorvdarshan.calorietracker.models

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

/**
 * "I'm still sore": the user (on the body map, or through Coach) says a group has not recovered
 * yet. It keeps that group locked for [extraHours] after the report, whatever the estimate says.
 */
@Serializable
data class SorenessReport(
    val id: String = UUID.randomUUID().toString(),
    /** [MachineMuscleGroup] name. */
    val group: String,
    @Serializable(with = InstantSerializer::class)
    val reportedAt: Instant,
    val extraHours: Int = Soreness.DEFAULT_EXTRA_HOURS,
    val note: String? = null
) {
    val muscleGroup: MachineMuscleGroup? get() = runCatching { MachineMuscleGroup.valueOf(group) }.getOrNull()
    val lockedUntil: Instant get() = reportedAt.plus(Duration.ofHours(extraHours.toLong()))
}

object Soreness {
    const val DEFAULT_EXTRA_HOURS = 24
    const val MIN_EXTRA_HOURS = 6
    const val MAX_EXTRA_HOURS = 96
    /** A locked group never shows as more than this recovered. */
    private const val LOCKED_MAX_PERCENT = 40

    /** Readiness with the user's reports applied: a still-sore group stays locked until its report ends. */
    fun apply(readiness: List<MuscleReadiness>, reports: List<SorenessReport>, now: Instant): List<MuscleReadiness> =
        readiness.map { r ->
            val until = reports.filter { it.muscleGroup == r.group && it.lockedUntil.isAfter(now) }.maxOfOrNull { it.lockedUntil }
                ?: return@map r
            val hours = ceil(Duration.between(now, until).toMinutes() / 60.0).toInt()
            r.copy(
                percent = minOf(r.percent, LOCKED_MAX_PERCENT),
                readyInHours = maxOf(r.readyInHours, hours),
                lockedUntil = until
            )
        }

    /** A muscle group from the words a user or the model uses, English or Spanish. */
    fun groupFromName(raw: String): MachineMuscleGroup? {
        val key = raw.trim().lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
        MachineMuscleGroup.entries.firstOrNull { it.name.lowercase() == key }?.let { return it }
        return when (key) {
            "pecho", "pectorales", "pectoral" -> MachineMuscleGroup.CHEST
            "espalda", "dorsales", "dorsal", "lats", "upper back", "trapecio", "traps" -> MachineMuscleGroup.BACK
            "hombros", "hombro", "deltoides", "shoulder", "delts" -> MachineMuscleGroup.SHOULDERS
            "bicep", "bicepses" -> MachineMuscleGroup.BICEPS
            "tricep" -> MachineMuscleGroup.TRICEPS
            "piernas", "pierna", "cuadriceps", "quads", "quadriceps", "leg" -> MachineMuscleGroup.LEGS
            "isquios", "isquiotibiales", "femorales", "femoral", "hamstring" -> MachineMuscleGroup.HAMSTRINGS
            "gluteos", "gluteo", "glute", "nalgas" -> MachineMuscleGroup.GLUTES
            "pantorrillas", "pantorrilla", "gemelos", "calf" -> MachineMuscleGroup.CALVES
            "abdomen", "abdominales", "core", "abdominal", "oblicuos" -> MachineMuscleGroup.ABS
            else -> null
        }
    }
}
