package com.crazystudio.sportrecorder.domain.diet

import kotlin.time.Duration.Companion.hours

enum class DietPhase { IDLE, EATING, FASTING, SUCCESS }

data class DietWindowState(
    val phase: DietPhase,
    val ringProgress: Float = 0f,
    val elapsedMillis: Long = 0L,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val lastEat: Long? = null,
    /** When the fast clock starts: the last meal, or last meal + 1h for a single-meal window. */
    val fastStartAt: Long? = null,
    val fastTargetAt: Long? = null,
)

/** Pure (Android-free) eating/fasting window calculator. */
object DietWindow {

    /** Grace before the fast clock starts when a window holds a single meal. */
    private val SINGLE_MEAL_FAST_GRACE_MILLIS = 1.hours.inWholeMilliseconds

    /**
     * Splits meals (ascending by time) into eating windows. A meal joins the current window while
     * it is within `eatingHours + fastingHours / 2` of that window's **first** meal — a slight
     * overrun is the same window, not a new one; only a meal after a real fast opens a fresh one.
     *
     * This is the single definition of "the same eating day" in the app: Home's ring uses the last
     * window, Insights buckets every meal by the window it belongs to (so a 00:30 snack after a
     * 20:00 dinner stays with that dinner instead of becoming tomorrow's first meal).
     */
    fun <T> groupIntoWindows(
        itemsAsc: List<T>,
        eatingHours: Long,
        fastingHours: Long,
        timeOf: (T) -> Long,
    ): List<List<T>> {
        if (itemsAsc.isEmpty()) return emptyList()
        val mergeLimit = eatingHours.hours.inWholeMilliseconds + fastingHours.hours.inWholeMilliseconds / 2
        val windows = mutableListOf<List<T>>()
        var current = mutableListOf(itemsAsc[0])
        var first = timeOf(itemsAsc[0])
        for (i in 1 until itemsAsc.size) {
            val item = itemsAsc[i]
            val t = timeOf(item)
            if (t - first > mergeLimit) {
                windows += current
                current = mutableListOf(item)
                first = t
            } else {
                current += item
            }
        }
        windows += current
        return windows
    }

    fun compute(
        eatTimesAsc: List<Long>,
        eatingHours: Long,
        fastingHours: Long,
        now: Long,
    ): DietWindowState {
        if (eatTimesAsc.isEmpty()) return DietWindowState(phase = DietPhase.IDLE)

        val ehMillis = eatingHours.hours.inWholeMilliseconds
        val fhMillis = fastingHours.hours.inWholeMilliseconds
        // Only the latest window matters for the live state; see [groupIntoWindows] for the rule.
        val window = groupIntoWindows(eatTimesAsc, eatingHours, fastingHours) { it }.last()
        val first = window.first()
        val last = window.last()
        val mealCount = window.size

        // Window extends to the late meal if it overran the nominal eating hours.
        val windowEnd = maxOf(first + ehMillis, last)
        // A single-meal window doesn't start fasting the instant you eat — give it 1h grace.
        val fastStartAt = if (mealCount == 1) last + SINGLE_MEAL_FAST_GRACE_MILLIS else last
        val fastTargetAt = fastStartAt + fhMillis

        return when {
            now < windowEnd -> DietWindowState(
                phase = DietPhase.EATING,
                ringProgress = ((now - first).toFloat() / ehMillis).coerceIn(0f, 1f),
                elapsedMillis = (windowEnd - now).coerceAtLeast(0L),
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastStartAt = fastStartAt,
                fastTargetAt = fastTargetAt,
            )
            now >= fastTargetAt -> DietWindowState(
                phase = DietPhase.SUCCESS,
                ringProgress = 1f,
                elapsedMillis = now - fastStartAt,
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastStartAt = fastStartAt,
                fastTargetAt = fastTargetAt,
            )
            else -> DietWindowState(
                phase = DietPhase.FASTING,
                ringProgress = ((now - fastStartAt).toFloat() / fhMillis).coerceIn(0f, 1f),
                elapsedMillis = now - fastStartAt,
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastStartAt = fastStartAt,
                fastTargetAt = fastTargetAt,
            )
        }
    }
}
