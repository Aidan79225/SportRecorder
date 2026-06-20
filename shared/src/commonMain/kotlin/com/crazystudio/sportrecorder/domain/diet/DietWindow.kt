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

    fun compute(
        eatTimesAsc: List<Long>,
        eatingHours: Long,
        fastingHours: Long,
        now: Long,
    ): DietWindowState {
        if (eatTimesAsc.isEmpty()) return DietWindowState(phase = DietPhase.IDLE)

        val ehMillis = eatingHours.hours.inWholeMilliseconds
        val fhMillis = fastingHours.hours.inWholeMilliseconds
        // A meal stays in the current window while it's within eatingHours + fastingHours/2 of the
        // window's first meal — a slight overrun is treated as the same window, not a new one.
        // Only a meal beyond that tolerance (i.e. after a real fast) opens a fresh window.
        val mergeLimit = ehMillis + fhMillis / 2

        var first = eatTimesAsc[0]
        var last = eatTimesAsc[0]
        var mealCount = 1
        for (i in 1 until eatTimesAsc.size) {
            val t = eatTimesAsc[i]
            if (t - first > mergeLimit) {
                first = t
                last = t
                mealCount = 1
            } else {
                last = t
                mealCount++
            }
        }

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
