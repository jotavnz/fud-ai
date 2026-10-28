package com.apoorvdarshan.calorietracker.models

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Optional menstrual cycle tracking. Off by default; everything stays on the device and Coach
 * only reads a summary (cycle day, estimated phase, today's symptoms) when the user turns it on.
 * Training advice follows the day's symptoms; the phase is context, never a rule (research
 * shows small, individual effects of cycle phase on performance).
 */
@Serializable
enum class CycleMode {
    /** Natural cycles: phases are estimated. */
    NATURAL,
    /** Hormonal contraception: no natural phases, symptoms only. */
    HORMONAL,
    /** Irregular or changing cycles: no predictions. */
    PERIMENOPAUSE,
    MENOPAUSE,
    PREGNANT;

    val estimatesPhases: Boolean get() = this == NATURAL
}

@Serializable
data class CycleSettings(
    val enabled: Boolean = false,
    val mode: CycleMode = CycleMode.NATURAL
)

@Serializable
data class CyclePeriod(
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    /** ISO date the period started. */
    val start: String,
    /** ISO date it ended, null while ongoing or unknown. */
    val end: String? = null,
    /** "manual" or the package of the app that shared it through Health Connect. */
    val source: String = MANUAL
) {
    val startDate: LocalDate get() = LocalDate.parse(start)
    val endDate: LocalDate? get() = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        const val MANUAL = "manual"
    }
}

/** How the user feels on one day; levels are 0 (none) to 3 (strong). */
@Serializable
data class CycleSymptoms(
    val date: String,
    val cramps: Int = 0,
    val fatigue: Int = 0,
    val headache: Int = 0,
    val lowMood: Int = 0,
    val bloating: Int = 0,
    val heavyFlow: Boolean = false
) {
    val isEmpty: Boolean get() = cramps + fatigue + headache + lowMood + bloating == 0 && !heavyFlow
}

enum class CyclePhase { MENSTRUAL, FOLLICULAR, OVULATORY, LUTEAL, PREMENSTRUAL }

data class CycleStatus(
    val cycleDay: Int?,
    val averageLength: Int,
    val phase: CyclePhase?,
    val nextPeriod: LocalDate?,
    /** "low" with fewer than 3 periods or irregular cycles, else "medium". */
    val confidence: String,
    val onPeriod: Boolean
)

/** Cycle math, written for Fud AI (idea follows Mensinator's average-interval prediction). */
object CycleMath {
    const val DEFAULT_LENGTH = 28

    /** Average of the last 6 start-to-start intervals between 21 and 45 days. */
    fun intervals(periods: List<CyclePeriod>): List<Int> {
        val starts = periods.map { it.startDate }.distinct().sorted()
        return starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
            .filter { it in 21..45 }
            .takeLast(6)
    }

    fun status(periods: List<CyclePeriod>, mode: CycleMode, today: LocalDate = LocalDate.now()): CycleStatus {
        val last = periods.filter { !it.startDate.isAfter(today) }.maxByOrNull { it.startDate }
        val lengths = intervals(periods)
        val average = if (lengths.isEmpty()) DEFAULT_LENGTH else lengths.average().roundToInt()
        val spread = if (lengths.size < 2) 0.0 else {
            val mean = lengths.average()
            sqrt(lengths.sumOf { (it - mean) * (it - mean) } / lengths.size)
        }
        val onPeriod = last != null && !today.isBefore(last.startDate) &&
            (last.endDate?.let { !today.isAfter(it) } ?: (ChronoUnit.DAYS.between(last.startDate, today) < 5))
        if (last == null || !mode.estimatesPhases) {
            return CycleStatus(null, average, if (onPeriod) CyclePhase.MENSTRUAL else null, null, "low", onPeriod)
        }
        val day = ChronoUnit.DAYS.between(last.startDate, today).toInt() + 1
        val ovulation = average - 14
        val periodLength = last.endDate?.let { ChronoUnit.DAYS.between(last.startDate, it).toInt() + 1 } ?: 5
        val phase = when {
            onPeriod || day <= periodLength -> CyclePhase.MENSTRUAL
            day < ovulation - 1 -> CyclePhase.FOLLICULAR
            day <= ovulation + 1 -> CyclePhase.OVULATORY
            day <= average - 5 -> CyclePhase.LUTEAL
            day <= average + 7 -> CyclePhase.PREMENSTRUAL
            else -> null // late or missed: no guess
        }
        return CycleStatus(
            cycleDay = day,
            averageLength = average,
            phase = phase,
            nextPeriod = last.startDate.plusDays(average.toLong()),
            confidence = if (lengths.size < 2 || spread > 7) "low" else "medium",
            onPeriod = onPeriod
        )
    }

    /** Plain training adjustments from today's symptoms (English, for Coach and tests). */
    fun trainingAdjustments(symptoms: CycleSymptoms?): List<String> {
        if (symptoms == null || symptoms.isEmpty) return emptyList()
        val tips = mutableListOf<String>()
        if (symptoms.cramps >= 2 || symptoms.heavyFlow) {
            tips += "Lower the intensity: low-impact cardio (walk, bike), mobility, gentle core; skip max lifts and jumping today."
        }
        if (symptoms.cramps >= 3) tips += "Strong cramps: a rest day or a short easy walk is fine; light movement can ease menstrual pain."
        if (symptoms.fatigue >= 2) tips += "Fatigue: cut volume (fewer sets), keep effort at 'good', end early if energy drops."
        if (symptoms.headache >= 2) tips += "Headache: avoid heavy straining and breath-holding; hydrate."
        if (symptoms.lowMood >= 2) tips += "Low mood: a short, enjoyable session or outdoor walk often helps more than skipping."
        if (symptoms.heavyFlow) tips += "Heavy flow: hydrate and include iron-rich foods; if very heavy every cycle, see a doctor."
        return tips
    }
}
