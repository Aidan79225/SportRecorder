package com.crazystudio.sportrecorder.domain.diet

import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Pure (Android-free except java.util.Calendar) 5-day eating/fasting history aggregator. */
object DietHistoryCalculator {
    data class HistoryBar(val dateMillis: Long, val ratio: Float)

    private const val MERGE_GAP_HOURS = 8L
    private const val MIN_INTERVAL_HOURS = 4L
    private const val HISTORY_DAYS = 5

    /**
     * Merge ascending eat timestamps into eating intervals: a new interval starts when the gap
     * from the previous eat exceeds [MERGE_GAP_HOURS]; each interval is at least [MIN_INTERVAL_HOURS] long.
     */
    fun mergeIntervals(eatTimesAsc: List<Long>): List<Pair<Long, Long>> {
        if (eatTimesAsc.isEmpty()) return emptyList()
        val gap = TimeUnit.HOURS.toMillis(MERGE_GAP_HOURS)
        val floor = TimeUnit.HOURS.toMillis(MIN_INTERVAL_HOURS)
        val result = mutableListOf<Pair<Long, Long>>()
        var start = eatTimesAsc[0]
        var end = eatTimesAsc[0]
        eatTimesAsc.forEach { t ->
            if (end + gap > t) {
                end = t
            } else {
                result.add(start to max(end, start + floor))
                start = t
                end = t
            }
        }
        result.add(start to max(end, start + floor))
        return result
    }

    /** Fraction of [dayStart, dayEnd) spent fasting (1.0 == fully fasted). */
    fun fastedRatio(intervals: List<Pair<Long, Long>>, dayStart: Long, dayEnd: Long): Float {
        var eating = 0L
        intervals.forEach {
            val hi = min(dayEnd, it.second)
            val lo = max(dayStart, it.first)
            eating += max(0L, hi - lo)
        }
        return 1.0f - (eating / (dayEnd - dayStart).toFloat())
    }

    /** Five most-recent whole-day bars (oldest first), each a fasting ratio. */
    fun compute(eatTimesAsc: List<Long>, now: Long): List<HistoryBar> {
        val intervals = mergeIntervals(eatTimesAsc)
        return (0 until HISTORY_DAYS).map { i ->
            val dayStart = dayBoundary(now, -i - 1)
            val dayEnd = dayBoundary(now, -i)
            HistoryBar(dateMillis = dayStart, ratio = fastedRatio(intervals, dayStart, dayEnd))
        }.asReversed()
    }

    private fun dayBoundary(now: Long, dayOffset: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) + dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
