package com.crazystudio.sportrecorder.ui.diet

import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Classify [target] relative to [now]'s day. Beyond ±1 day returns [RelativeDay.OTHER]. */
fun relativeDay(now: Long, target: Long): RelativeDay {
    val dayMillis = TimeUnit.DAYS.toMillis(1)
    // Round so DST-shortened/lengthened days still map to whole-day deltas.
    val diffDays = Math.round((startOfDay(target) - startOfDay(now)).toDouble() / dayMillis)
    return when (diffDays) {
        -1L -> RelativeDay.YESTERDAY
        0L -> RelativeDay.TODAY
        1L -> RelativeDay.TOMORROW
        else -> RelativeDay.OTHER
    }
}

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
