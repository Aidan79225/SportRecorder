package com.crazystudio.sportrecorder.ui.insights

import com.crazystudio.sportrecorder.domain.insights.DayBand
import kotlin.test.Test
import kotlin.test.assertEquals

class RhythmChartScaleTest {
    private fun band(first: Int, last: Int, meals: Int = 2) =
        DayBand(dayStart = 0L, dayOfMonth = 1, firstMinutes = first, lastMinutes = last, mealCount = meals)

    @Test fun domainEnd_isADayWhenNothingCrossesMidnight() {
        assertEquals(24 * 60, RhythmChartScale.domainEnd(listOf(band(9 * 60, 17 * 60))))
        assertEquals(24 * 60, RhythmChartScale.domainEnd(emptyList()))
    }

    @Test fun domainEnd_growsInSixHourStepsPastMidnight() {
        assertEquals(30 * 60, RhythmChartScale.domainEnd(listOf(band(20 * 60, 24 * 60 + 30))))
        assertEquals(30 * 60, RhythmChartScale.domainEnd(listOf(band(20 * 60, 30 * 60))))
        assertEquals(36 * 60, RhythmChartScale.domainEnd(listOf(band(20 * 60, 30 * 60 + 1))))
    }

    @Test fun domainEnd_ignoresEmptyRows() {
        // An empty row carries 0/0; a stale lastMinutes on it must never stretch the axis.
        assertEquals(24 * 60, RhythmChartScale.domainEnd(listOf(band(0, 40 * 60, meals = 0))))
    }

    @Test fun ticks_runEverySixHoursToTheEnd() {
        assertEquals(listOf(0, 360, 720, 1080, 1440), RhythmChartScale.ticks(24 * 60))
        assertEquals(listOf(0, 360, 720, 1080, 1440, 1800), RhythmChartScale.ticks(30 * 60))
    }

    @Test fun fraction_isLinear() {
        assertEquals(0.5f, RhythmChartScale.fraction(12 * 60, 24 * 60))
        assertEquals(1f, RhythmChartScale.fraction(30 * 60, 30 * 60))
    }
}
