package com.crazystudio.sportrecorder.ui.diet

import java.util.Calendar

/** True if [endMillis] falls on a later calendar day than [startMillis] (device-default time zone). */
fun fastWindowCrossesDay(startMillis: Long, endMillis: Long): Boolean {
    val start = Calendar.getInstance().apply { timeInMillis = startMillis }
    val end = Calendar.getInstance().apply { timeInMillis = endMillis }
    val startDay = start.get(Calendar.YEAR) to start.get(Calendar.DAY_OF_YEAR)
    val endDay = end.get(Calendar.YEAR) to end.get(Calendar.DAY_OF_YEAR)
    return endDay.first > startDay.first ||
        (endDay.first == startDay.first && endDay.second > startDay.second)
}
