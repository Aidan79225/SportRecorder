package com.crazystudio.sportrecorder.ui.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DietTimeFormatTest {
    private fun at(dayOffset: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val now = at(dayOffset = 0, hour = 12)

    @Test fun sameDay_isToday() {
        assertEquals(RelativeDay.TODAY, relativeDay(now, at(dayOffset = 0, hour = 23)))
    }

    @Test fun dayBefore_isYesterday() {
        assertEquals(RelativeDay.YESTERDAY, relativeDay(now, at(dayOffset = -1, hour = 8)))
    }

    @Test fun dayAfter_isTomorrow() {
        assertEquals(RelativeDay.TOMORROW, relativeDay(now, at(dayOffset = 1, hour = 6)))
    }

    @Test fun beyondRange_isOther() {
        assertEquals(RelativeDay.OTHER, relativeDay(now, at(dayOffset = 3, hour = 12)))
    }
}
