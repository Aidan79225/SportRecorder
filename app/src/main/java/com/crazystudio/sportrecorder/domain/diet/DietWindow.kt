package com.crazystudio.sportrecorder.domain.diet

import java.util.concurrent.TimeUnit

enum class DietPhase { IDLE, EATING, FASTING, SUCCESS }

data class DietWindowState(
    val phase: DietPhase,
    val ringProgress: Float = 0f,
    val elapsedMillis: Long = 0L,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val lastEat: Long? = null,
    val fastTargetAt: Long? = null,
)

/** Pure (Android-free) eating/fasting window calculator. */
object DietWindow {
    fun compute(
        eatTimesAsc: List<Long>,
        eatingHours: Long,
        fastingHours: Long,
        now: Long,
    ): DietWindowState {
        if (eatTimesAsc.isEmpty()) return DietWindowState(phase = DietPhase.IDLE)

        val ehMillis = TimeUnit.HOURS.toMillis(eatingHours)
        val fhMillis = TimeUnit.HOURS.toMillis(fastingHours)

        // Current eating window = most recent cluster; a new window starts when an
        // eat lands after firstEat + eatingHours.
        var first = eatTimesAsc[0]
        var last = eatTimesAsc[0]
        for (t in eatTimesAsc) {
            if (t > first + ehMillis) {
                first = t
                last = t
            } else {
                last = t
            }
        }

        val windowEnd = first + ehMillis
        val fastTargetAt = last + fhMillis

        return when {
            now < windowEnd -> DietWindowState(
                phase = DietPhase.EATING,
                ringProgress = ((now - first).toFloat() / ehMillis).coerceIn(0f, 1f),
                elapsedMillis = (windowEnd - now).coerceAtLeast(0L),
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastTargetAt = fastTargetAt,
            )
            now - last >= fhMillis -> DietWindowState(
                phase = DietPhase.SUCCESS,
                ringProgress = 1f,
                elapsedMillis = now - last,
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastTargetAt = fastTargetAt,
            )
            else -> DietWindowState(
                phase = DietPhase.FASTING,
                ringProgress = ((now - last).toFloat() / fhMillis).coerceIn(0f, 1f),
                elapsedMillis = now - last,
                windowStart = first,
                windowEnd = windowEnd,
                lastEat = last,
                fastTargetAt = fastTargetAt,
            )
        }
    }
}
