package app.aaps.plugins.periodcalendar.services

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.periodcalendar.model.CycleInterval
import app.aaps.plugins.periodcalendar.model.CyclePhase
import app.aaps.plugins.periodcalendar.model.CycleStartRecord
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class CycleCounterService
    @Inject
    constructor(
        private val sp: SP,
        private val aapsLogger: AAPSLogger,
    ) {
        private val spKeyCycleStart = "period_calendar_cycle_start"
        private val spKeyCycleHistory = "period_calendar_cycle_history"
        private val spKeyPhases = "period_calendar_phases"

        private val json = Json { ignoreUnknownKeys = true }

        var cycleStartTimestamp: Long
            get() {
                val value = sp.getLong(spKeyCycleStart, 0L)
                aapsLogger.debug(LTag.CORE, "Reading cycleStartTimestamp: $value")
                return value
            }
            set(value) {
                try {
                    sp.putLong(spKeyCycleStart, value)
                    aapsLogger.debug(LTag.CORE, "Saved cycleStartTimestamp: $value")
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to save cycleStartTimestamp", e)
                }
            }

        var cycleHistory: List<CycleStartRecord>
            get() {
                val jsonStr = sp.getString(spKeyCycleHistory, "")
                return if (jsonStr.isEmpty()) {
                    emptyList()
                } else {
                    try {
                        json.decodeFromString<List<CycleStartRecord>>(jsonStr)
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.CORE, "Failed to parse cycle history", e)
                        emptyList()
                    }
                }
            }
            set(value) {
                try {
                    val jsonStr = json.encodeToString(value)
                    sp.putString(spKeyCycleHistory, jsonStr)
                    aapsLogger.debug(LTag.CORE, "Cycle history saved: ${value.size} entries")
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to save cycle history", e)
                }
            }

        var phases: List<CyclePhase>
            get() {
                val jsonStr = sp.getString(spKeyPhases, "")
                return if (jsonStr.isEmpty()) {
                    CyclePhase.defaultPhases()
                } else {
                    try {
                        json.decodeFromString<List<CyclePhase>>(jsonStr)
                    } catch (e: Exception) {
                        aapsLogger.error(LTag.CORE, "Failed to parse cycle phases", e)
                        CyclePhase.defaultPhases()
                    }
                }
            }
            set(value) {
                aapsLogger.debug(LTag.CORE, "phases setter called with ${value.size} phases: $value")
                try {
                    val jsonStr = json.encodeToString(value)
                    aapsLogger.debug(LTag.CORE, "JSON encoded phases (length=${jsonStr.length}): ${jsonStr.take(100)}")
                    sp.putString(spKeyPhases, jsonStr)
                    aapsLogger.debug(LTag.CORE, "Cycle phases saved: ${value.size} phases")
                } catch (e: Exception) {
                    aapsLogger.error(LTag.CORE, "Failed to save cycle phases", e)
                }
            }

        fun getCurrentCycleDay(): Int {
            val start = cycleStartTimestamp
            if (start == 0L) return 0
            val startMidnight = midnightOf(start)
            val nowMidnight = midnightOf(System.currentTimeMillis())
            val diffDays = ((nowMidnight - startMidnight) / (1000L * 60 * 60 * 24)).toInt() + 1
            return diffDays.coerceIn(0, 99)
        }

        fun getCurrentPhase(): CyclePhase? {
            val day = getCurrentCycleDay()
            if (day == 0) return null
            return phases.firstOrNull { it.containsDay(day) }
        }

        fun getFormattedDisplay(): String {
            val day = getCurrentCycleDay()
            if (day == 0) return "--"
            val phase = getCurrentPhase()
            return if (phase != null) {
                String.format("%02d%s", day, phase.label)
            } else {
                String.format("%02d", day)
            }
        }

        fun resetCycle() {
            val now = System.currentTimeMillis()
            val currentStart = cycleStartTimestamp
            aapsLogger.debug(LTag.CORE, "resetCycle() called: currentStart=$currentStart, now=$now")
            if (currentStart > 0L) {
                val prevHistory = cycleHistory.toMutableList()
                prevHistory.add(CycleStartRecord(currentStart))
                if (prevHistory.size > 24) {
                    prevHistory.subList(0, prevHistory.size - 24).clear()
                }
                aapsLogger.debug(LTag.CORE, "Adding $currentStart to history, new size=${prevHistory.size}")
                cycleHistory = prevHistory
            } else {
                aapsLogger.debug(LTag.CORE, "Skipping history save because currentStart was 0L")
            }
            cycleStartTimestamp = now
            val savedHistory = cycleHistory
            aapsLogger.debug(LTag.CORE, "Cycle reset to day 1, history size: ${savedHistory.size}, cycleStartTimestamp now=$cycleStartTimestamp")
        }

        fun setCycleDay(day: Int) {
            val clampedDay = day.coerceIn(1, 99)
            val todayMidnight = midnightOf(System.currentTimeMillis())
            val newStart = todayMidnight - (clampedDay - 1) * 24L * 60 * 60 * 1000
            val currentStart = cycleStartTimestamp
            aapsLogger.debug(LTag.CORE, "setCycleDay: day=$clampedDay, todayMidnight=$todayMidnight, newStart=$newStart, currentStart=$currentStart")
            if (currentStart > 0L && newStart != currentStart) {
                val prevHistory = cycleHistory.toMutableList()
                prevHistory.add(CycleStartRecord(currentStart))
                if (prevHistory.size > 24) {
                    prevHistory.subList(0, prevHistory.size - 24).clear()
                }
                cycleHistory = prevHistory
                aapsLogger.debug(LTag.CORE, "Cycle history updated, size=${cycleHistory.size}")
            }
            cycleStartTimestamp = newStart
            aapsLogger.debug(LTag.CORE, "Cycle set to day $clampedDay, saved timestamp=$newStart, verify read=${cycleStartTimestamp}")
        }

        fun isCycleStarted(): Boolean = cycleStartTimestamp > 0L

        fun clearHistory() {
            cycleHistory = emptyList()
            aapsLogger.debug(LTag.CORE, "Cycle history cleared")
        }

        fun getAverageCycleLength(): Double {
            // Include gap from last history entry to current cycle start so one real cycle
            // length is measured rather than defaulting to 28 days.
            val history = cycleHistory
            val currentStart = cycleStartTimestamp
            val allStarts = history.map { it.timestamp }.toMutableList()
            if (currentStart > 0L) allStarts.add(currentStart)
            allStarts.sort()
            if (allStarts.size < 2) return 28.0
            var totalDays = 0L
            for (i in 1 until allStarts.size) {
                totalDays += (allStarts[i] - allStarts[i - 1]) / (1000L * 60 * 60 * 24)
            }
            return totalDays.toDouble() / (allStarts.size - 1)
        }

        fun getPredictedNextStart(): Long? {
            // Base prediction on current cycle start, not the previous one stored in history.
            val currentStart = cycleStartTimestamp
            if (currentStart == 0L) return null
            val avgLength = getAverageCycleLength()
            return currentStart + (avgLength * 1000L * 60 * 60 * 24).toLong()
        }

        fun getPredictedCycleDayForDate(dateTimestamp: Long): Int {
            val start = cycleStartTimestamp
            if (start == 0L) return 0
            val startMidnight = midnightOf(start)
            val dateMidnight = midnightOf(dateTimestamp)
            val diffMs = dateMidnight - startMidnight
            if (diffMs < 0) return 0
            val diffDays = (diffMs / (1000L * 60 * 60 * 24)).toInt() + 1
            val avgLength = getAverageCycleLength()
            if (avgLength <= 0) return 1
            val dayInCycle = ((diffDays - 1) % avgLength.toInt().coerceAtLeast(1)) + 1
            return dayInCycle.coerceIn(1, 99)
        }

        /**
         * Cycle intervals, newest first: each start with the number of calendar days
         * until the next start. The entry matching the active cycle is marked ongoing
         * and carries its length so far (= current cycle day).
         */
        fun getCycleIntervals(): List<CycleInterval> {
            val currentStartMidnight = cycleStartTimestamp.takeIf { it > 0L }?.let { midnightOf(it) }
            val starts =
                (cycleHistory.map { midnightOf(it.timestamp) } + listOfNotNull(currentStartMidnight))
                    .distinct()
                    .sorted()
            if (starts.isEmpty()) return emptyList()
            val dayMs = 1000L * 60 * 60 * 24
            return starts
                .mapIndexed { i, start ->
                    val next = starts.getOrNull(i + 1)
                    if (next != null) {
                        // Round instead of truncate so a DST-shortened day still counts as a full day.
                        CycleInterval(start, ((next - start).toDouble() / dayMs).roundToInt(), ongoing = false)
                    } else {
                        val daysSoFar = ((midnightOf(System.currentTimeMillis()) - start).toDouble() / dayMs).roundToInt() + 1
                        CycleInterval(start, daysSoFar.coerceAtLeast(1), ongoing = start == currentStartMidnight)
                    }
                }.reversed()
        }

        fun getPhaseForDay(day: Int): CyclePhase? {
            if (day == 0) return null
            return phases.firstOrNull { it.containsDay(day) }
        }

        fun getPhaseForDate(dateTimestamp: Long): CyclePhase? {
            val day = getPredictedCycleDayForDate(dateTimestamp)
            return getPhaseForDay(day)
        }

        fun getHistoricalCycleDays(): Map<Long, Int> {
            val result = mutableMapOf<Long, Int>()
            val history = cycleHistory
            val currentStart = cycleStartTimestamp
            if (history.isEmpty() && currentStart == 0L) return result

            val avgLength = getAverageCycleLength()
            val maxCyclesToShow = 3

            val recentHistory = if (history.size > maxCyclesToShow) {
                history.takeLast(maxCyclesToShow)
            } else {
                history
            }

            for (i in recentHistory.indices) {
                val cycleStart = recentHistory[i].timestamp
                val cycleLength =
                    if (i < recentHistory.size - 1) {
                        (recentHistory[i + 1].timestamp - cycleStart) / (1000L * 60 * 60 * 24)
                    } else if (currentStart > 0L && currentStart > cycleStart) {
                        // Use actual gap to current cycle start instead of default avg
                        (currentStart - cycleStart) / (1000L * 60 * 60 * 24)
                    } else {
                        avgLength.toLong()
                    }
                val startDay = midnightOf(cycleStart)
                for (dayOffset in 0 until cycleLength) {
                    result[startDay + dayOffset * 24L * 60 * 60 * 1000] = (dayOffset.toInt() + 1).coerceIn(1, 99)
                }
            }

            // Include current active cycle so its TDD data appears in statistics
            if (currentStart > 0L) {
                val startDay = midnightOf(currentStart)
                val todayStart = midnightOf(System.currentTimeMillis())
                val daysInCycle = ((todayStart - startDay) / (1000L * 60 * 60 * 24)).toInt() + 1
                for (dayOffset in 0 until daysInCycle) {
                    result[startDay + dayOffset * 24L * 60 * 60 * 1000] = (dayOffset.toInt() + 1).coerceIn(1, 99)
                }
            }

            return result
        }

        private fun midnightOf(timestamp: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = timestamp
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
