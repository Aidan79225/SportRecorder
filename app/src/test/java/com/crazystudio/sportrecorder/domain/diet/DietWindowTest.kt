package com.crazystudio.sportrecorder.domain.diet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DietWindowTest {
    private val eh = 8L
    private val fh = 16L
    private val ehMs = TimeUnit.HOURS.toMillis(eh)
    private val fhMs = TimeUnit.HOURS.toMillis(fh)
    private val f = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)

    @Test fun noEats_isIdle() {
        val s = DietWindow.compute(emptyList(), eh, fh, f)
        assertEquals(DietPhase.IDLE, s.phase)
        assertEquals(0f, s.ringProgress, 0f)
    }

    @Test fun singleEat_duringWindow_isEating() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(2))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(0.25f, s.ringProgress, 0.001f)
        assertEquals(h(6), s.elapsedMillis)
        assertEquals(f, s.windowStart)
        assertEquals(f + ehMs, s.windowEnd)
    }

    @Test fun lastBiteBeforeWindowClose_stillEating() {
        val s = DietWindow.compute(listOf(f, f + h(4)), eh, fh, f + h(5))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(h(3), s.elapsedMillis)
    }

    @Test fun afterWindowClose_fastingCountsFromLastBite() {
        val s = DietWindow.compute(listOf(f, f + h(4)), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(5), s.elapsedMillis)
        assertEquals(5f / 16f, s.ringProgress, 0.001f)
        assertEquals(f + h(4) + fhMs, s.fastTargetAt)
    }

    @Test fun fastingInProgress() {
        // Single meal: the fast clock starts 1h after the meal, so at +12h elapsed is 11h.
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(12))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(11), s.elapsedMillis)
        assertEquals(11f / 16f, s.ringProgress, 0.001f)
    }

    @Test fun success_whenFastReachesTarget() {
        // Single meal: target = meal + 1h + fastingHours = f + 17h.
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(17))
        assertEquals(DietPhase.SUCCESS, s.phase)
        assertEquals(1f, s.ringProgress, 0f)
        assertEquals(h(16), s.elapsedMillis)
    }

    @Test fun singleMeal_fastStartsOneHourAfterMeal() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(12))
        assertEquals(f + h(1), s.fastStartAt)
        assertEquals(f + h(1) + fhMs, s.fastTargetAt)
    }

    @Test fun multiMeal_fastStartsAtLastMealNoGrace() {
        val s = DietWindow.compute(listOf(f, f + h(3)), eh, fh, f + h(12))
        assertEquals(f + h(3), s.fastStartAt)
        assertEquals(f + h(3) + fhMs, s.fastTargetAt)
    }

    @Test fun lateMealWithinTolerance_mergesIntoWindow() {
        // Meal at +10h is past the 8h window but within 8h+8h tolerance → same window, not a new
        // one. So we're fasting from that meal, NOT starting a fresh 8h eating window.
        val s = DietWindow.compute(listOf(f, f + h(4), f + h(10)), eh, fh, f + h(11))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f, s.windowStart)
        assertEquals(f + h(10), s.lastEat)
        assertEquals(f + h(10), s.fastStartAt) // multi-meal → no grace
        assertEquals(f + h(10) + fhMs, s.fastTargetAt)
    }

    @Test fun slightOverrun_extendsWindowEnd() {
        // Original 8h window, but a meal at +9h (within tolerance) extends the window to +9h.
        val s = DietWindow.compute(listOf(f, f + h(9)), eh, fh, f + h(9))
        assertEquals(f, s.windowStart)
        assertEquals(f + h(9), s.windowEnd)
    }

    @Test fun mealBeyondTolerance_startsNewWindow() {
        // +17h is past eatingHours + fastingHours/2 (8h + 8h = 16h) → genuinely a new window.
        val s = DietWindow.compute(listOf(f, f + h(17)), eh, fh, f + h(18))
        assertEquals(f + h(17), s.windowStart)
        assertEquals(f + h(17), s.lastEat)
        assertEquals(f + h(17) + ehMs, s.windowEnd)
    }

    @Test fun boundary_exactlyAtWindowEnd_flipsToFasting() {
        // Single meal: at the 8h window end, fast clock (from meal+1h) shows 7h elapsed.
        val s = DietWindow.compute(listOf(f), eh, fh, f + ehMs)
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(ehMs - h(1), s.elapsedMillis)
    }

    @Test fun boundary_exactlyAtFastTarget_isSuccess() {
        // Single meal: target is meal + 1h + fastingHours.
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(1) + fhMs)
        assertEquals(DietPhase.SUCCESS, s.phase)
    }

    @Test fun multipleEatsInWindow_lastEatIsLatest() {
        val s = DietWindow.compute(listOf(f, f + h(1), f + h(3)), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f + h(3), s.lastEat)
        assertEquals(h(6), s.elapsedMillis)
    }
}
