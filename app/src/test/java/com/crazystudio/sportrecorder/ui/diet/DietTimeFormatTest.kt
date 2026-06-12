package com.crazystudio.sportrecorder.ui.diet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DietTimeFormatTest {
    private fun at(hour: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test fun sameDay_isNotNextDay() {
        assertFalse(fastWindowCrossesDay(at(hour = 8), at(hour = 20)))
    }

    @Test fun acrossMidnight_isNextDay() {
        assertTrue(fastWindowCrossesDay(at(hour = 20), at(hour = 10, dayOffset = 1)))
    }
}
