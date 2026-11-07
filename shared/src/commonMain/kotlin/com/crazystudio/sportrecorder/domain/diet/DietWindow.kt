package com.crazystudio.sportrecorder.domain.diet

import kotlin.time.Duration.Companion.hours

enum class DietPhase { IDLE, EATING, FASTING, SUCCESS }

/** One eating occasion, as epoch millis. */
data class MealSpan(val start: Long, val end: Long)

data class DietWindowState(
    val phase: DietPhase,
    val ringProgress: Float = 0f,
    val elapsedMillis: Long = 0L,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    /** When the fast clock starts: the latest end of any meal in the window. */
    val fastStartAt: Long? = null,
    val fastTargetAt: Long? = null,
)

/** Pure (Android-free) eating/fasting window calculator. */
object DietWindow {

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

    /**
     * The live state for meals [mealsAsc] (ascending by start). Meals are grouped into eating
     * windows by their **start** ([groupIntoWindows]); within the latest window the fast starts
     * when the last meal **ends** — the latest end, so a long meal with a quick drink inside it
     * ends when the long meal does.
     */
    fun compute(
        mealsAsc: List<MealSpan>,
        eatingHours: Long,
        fastingHours: Long,
        now: Long,
    ): DietWindowState {
        if (mealsAsc.isEmpty()) return DietWindowState(phase = DietPhase.IDLE)

        val ehMillis = eatingHours.hours.inWholeMilliseconds
        val fhMillis = fastingHours.hours.inWholeMilliseconds
        // Only the latest window matters for the live state; see [groupIntoWindows] for the rule.
        val window = groupIntoWindows(mealsAsc, eatingHours, fastingHours) { it.start }.last()
        val first = window.first().start
        val latestEnd = window.maxOf { it.end }

        // Window extends to the late meal's end if it overran the nominal eating hours.
        val windowEnd = maxOf(first + ehMillis, latestEnd)
        val fastStartAt = latestEnd
        val fastTargetAt = fastStartAt + fhMillis

        return when {
            now < windowEnd -> DietWindowState(
                phase = DietPhase.EATING,
                ringProgress = ((now - first).toFloat() / ehMillis).coerceIn(0f, 1f),
                elapsedMillis = (windowEnd - now).coerceAtLeast(0L),
                windowStart = first,
                windowEnd = windowEnd,
                fastStartAt = fastStartAt,
                fastTargetAt = fastTargetAt,
            )
            now >= fastTargetAt -> DietWindowState(
                phase = DietPhase.SUCCESS,
                ringProgress = 1f,
                elapsedMillis = now - fastStartAt,
                windowStart = first,
                windowEnd = windowEnd,
                fastStartAt = fastStartAt,
                fastTargetAt = fastTargetAt,
            )
            else -> DietWindowState(
                phase = DietPhase.FASTING,
                ringProgress = ((now - fastStartAt).toFloat() / fhMillis).coerceIn(0f, 1f),
                elapsedMillis = now - fastStartAt,
                windowStart = first,
                windowEnd = windowEnd,
                fastStartAt = fastStartAt,
                fastTargetAt = fastTargetAt,
            )
        }
    }
}
