package com.crazystudio.sportrecorder.ui.diet

import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Classify [target] relative to [now]'s calendar day. Beyond ±1 day returns [RelativeDay.OTHER].
 * Calendar-day based (kotlinx-datetime), so DST-shortened/lengthened days still map correctly.
 */
fun relativeDay(now: Long, target: Long): RelativeDay {
    val zone = TimeZone.currentSystemDefault()
    val nowDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    val targetDate = Instant.fromEpochMilliseconds(target).toLocalDateTime(zone).date
    return when (nowDate.daysUntil(targetDate)) {
        -1 -> RelativeDay.YESTERDAY
        0 -> RelativeDay.TODAY
        1 -> RelativeDay.TOMORROW
        else -> RelativeDay.OTHER
    }
}
