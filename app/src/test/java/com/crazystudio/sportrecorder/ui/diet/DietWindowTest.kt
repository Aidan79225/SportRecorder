package com.crazystudio.sportrecorder.ui.diet

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
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(12))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(12), s.elapsedMillis)
        assertEquals(12f / 16f, s.ringProgress, 0.001f)
    }

    @Test fun success_whenFastReachesTarget() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(16))
        assertEquals(DietPhase.SUCCESS, s.phase)
        assertEquals(1f, s.ringProgress, 0f)
        assertEquals(h(16), s.elapsedMillis)
    }

    @Test fun eatAfterWindowClose_startsNewWindow() {
        val s = DietWindow.compute(listOf(f, f + h(4), f + h(10)), eh, fh, f + h(11))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(f + h(10), s.windowStart)
        assertEquals(f + h(10) + ehMs, s.windowEnd)
        assertEquals(f + h(10), s.lastEat)
    }

    @Test fun boundary_exactlyAtWindowEnd_flipsToFasting() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + ehMs)
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(ehMs, s.elapsedMillis)
    }

    @Test fun boundary_exactlyAtFastTarget_isSuccess() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + fhMs)
        assertEquals(DietPhase.SUCCESS, s.phase)
    }

    @Test fun multipleEatsInWindow_lastEatIsLatest() {
        val s = DietWindow.compute(listOf(f, f + h(1), f + h(3)), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f + h(3), s.lastEat)
        assertEquals(h(6), s.elapsedMillis)
    }
}
