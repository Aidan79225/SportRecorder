package com.crazystudio.sportrecorder.domain.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DietHistoryCalculatorTest {
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)

    @Test fun mergeIntervals_empty_isEmpty() {
        assertEquals(emptyList<Pair<Long, Long>>(), DietHistoryCalculator.mergeIntervals(emptyList()))
    }

    @Test fun mergeIntervals_singleEat_hasFourHourFloor() {
        val result = DietHistoryCalculator.mergeIntervals(listOf(0L))
        assertEquals(listOf(0L to h(4)), result)
    }

    @Test fun mergeIntervals_closeEats_merge() {
        // eats 1h apart stay one interval; end extends to last eat, floored at 4h
        val result = DietHistoryCalculator.mergeIntervals(listOf(0L, h(1)))
        assertEquals(listOf(0L to h(4)), result)
    }

    @Test fun mergeIntervals_gapOverEightHours_splits() {
        val result = DietHistoryCalculator.mergeIntervals(listOf(0L, h(10)))
        assertEquals(listOf(0L to h(4), h(10) to h(14)), result)
    }

    @Test fun fastedRatio_noIntervals_fullyFasted() {
        assertEquals(1.0f, DietHistoryCalculator.fastedRatio(emptyList(), 0L, h(24)), 0.0001f)
    }

    @Test fun fastedRatio_partialEating() {
        // 4h eating inside a 24h day → fasted 20/24
        val ratio = DietHistoryCalculator.fastedRatio(listOf(0L to h(4)), 0L, h(24))
        assertEquals(20f / 24f, ratio, 0.0001f)
    }

    @Test fun fastedRatio_intervalOutsideDay_ignored() {
        val ratio = DietHistoryCalculator.fastedRatio(listOf(h(100) to h(104)), 0L, h(24))
        assertEquals(1.0f, ratio, 0.0001f)
    }
}
