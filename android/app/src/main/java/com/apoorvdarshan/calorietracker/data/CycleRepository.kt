package com.apoorvdarshan.calorietracker.data

import com.apoorvdarshan.calorietracker.models.CycleMath
import com.apoorvdarshan.calorietracker.models.CyclePeriod
import com.apoorvdarshan.calorietracker.models.CycleSettings
import com.apoorvdarshan.calorietracker.models.CycleStatus
import com.apoorvdarshan.calorietracker.models.CycleSymptoms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Menstrual cycle data (periods, daily symptoms, settings), kept on the device. It leaves it
 * only as the summary Coach reads while tracking is on (no period dates, only cycle day, phase
 * and the next-period estimate) and inside the user's own Google Drive backup when that is on.
 */
class CycleRepository(private val prefs: PreferencesStore) {
    val settings: Flow<CycleSettings> = prefs.cycleSettings
    val periods: Flow<List<CyclePeriod>> = prefs.cyclePeriods.map { list -> list.sortedByDescending { it.start } }
    val symptoms: Flow<List<CycleSymptoms>> = prefs.cycleSymptoms

    suspend fun setSettings(settings: CycleSettings) = prefs.setCycleSettings(settings)

    /** Starts a period today, unless one already started within the last 10 days. */
    suspend fun startPeriod(date: LocalDate = LocalDate.now()) {
        prefs.updateCyclePeriods { list ->
            val recent = list.any { !it.startDate.isAfter(date) && it.startDate.isAfter(date.minusDays(10)) }
            if (recent) list else list + CyclePeriod(start = date.toString())
        }
    }

    /** Ends the latest open period on [date]. */
    suspend fun endPeriod(date: LocalDate = LocalDate.now()) {
        prefs.updateCyclePeriods { list ->
            val open = list.filter { it.end == null && !it.startDate.isAfter(date) }.maxByOrNull { it.start }
                ?: return@updateCyclePeriods list
            list.map { if (it.id == open.id) it.copy(end = date.toString()) else it }
        }
    }

    suspend fun removePeriod(id: UUID) {
        prefs.updateCyclePeriods { list -> list.filter { it.id != id } }
    }

    /** Adds periods read from Health Connect, skipping any that start within 3 days of a known one. */
    suspend fun importPeriods(imported: List<CyclePeriod>): Int {
        var added = 0
        prefs.updateCyclePeriods { list ->
            val merged = list.toMutableList()
            imported.sortedBy { it.start }.forEach { candidate ->
                val duplicate = merged.any { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it.startDate, candidate.startDate)) <= 3 }
                if (!duplicate) {
                    merged += candidate
                    added++
                }
            }
            merged
        }
        return added
    }

    suspend fun setSymptoms(entry: CycleSymptoms) {
        prefs.updateCycleSymptoms { list ->
            val others = list.filter { it.date != entry.date }
            // Keep about a year of history.
            val cutoff = LocalDate.now().minusDays(400).toString()
            (if (entry.isEmpty) others else others + entry).filter { it.date >= cutoff }
        }
    }

    suspend fun status(today: LocalDate = LocalDate.now()): CycleStatus =
        CycleMath.status(periods.first(), settings.first().mode, today)

    /** Everything Coach may read, or null while tracking is off. */
    suspend fun coachSnapshot(): Snapshot? {
        val current = settings.first()
        if (!current.enabled) return null
        return Snapshot(current, periods.first(), symptoms.first())
    }

    data class Snapshot(
        val settings: CycleSettings,
        val periods: List<CyclePeriod>,
        val symptoms: List<CycleSymptoms>
    )
}
