package com.crazystudio.sportrecorder.domain.diet

import kotlin.test.Test
import kotlin.test.assertEquals

// Moved from :app into :shared/commonTest (kotlin.test). Runs on JVM locally and on the
// iosSimulatorArm64 target in CI — proving the shared calculator and its tests run on iOS.
class DietWindowTest {
    private val eh = 8L
    private val fh = 16L
    private val ehMs = h(eh)
    private val fhMs = h(fh)
    private val f = 1_700_000_000_000L
    private fun h(n: Long) = n * 3_600_000L

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

    @Test fun fastingInProgress_singleMealGrace() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(12))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(11), s.elapsedMillis)
        assertEquals(11f / 16f, s.ringProgress, 0.001f)
    }

    @Test fun success_whenSingleMealFastReachesTarget() {
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
        val s = DietWindow.compute(listOf(f, f + h(4), f + h(10)), eh, fh, f + h(11))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f, s.windowStart)
        assertEquals(f + h(10), s.lastEat)
        assertEquals(f + h(10), s.fastStartAt)
        assertEquals(f + h(10) + fhMs, s.fastTargetAt)
    }

    @Test fun slightOverrun_extendsWindowEnd() {
        val s = DietWindow.compute(listOf(f, f + h(9)), eh, fh, f + h(9))
        assertEquals(f, s.windowStart)
        assertEquals(f + h(9), s.windowEnd)
    }

    @Test fun mealBeyondTolerance_startsNewWindow() {
        val s = DietWindow.compute(listOf(f, f + h(17)), eh, fh, f + h(18))
        assertEquals(f + h(17), s.windowStart)
        assertEquals(f + h(17), s.lastEat)
        assertEquals(f + h(17) + ehMs, s.windowEnd)
    }

    @Test fun boundary_exactlyAtWindowEnd_flipsToFasting() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + ehMs)
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(ehMs - h(1), s.elapsedMillis)
    }

    @Test fun boundary_exactlyAtFastTarget_isSuccess() {
        val s = DietWindow.compute(listOf(f), eh, fh, f + h(1) + fhMs)
        assertEquals(DietPhase.SUCCESS, s.phase)
    }

    @Test fun multipleEatsInWindow_lastEatIsLatest() {
        val s = DietWindow.compute(listOf(f, f + h(1), f + h(3)), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f + h(3), s.lastEat)
        assertEquals(h(6), s.elapsedMillis)
    }

    // --- groupIntoWindows: the shared "same eating day" rule ---------------

    @Test fun groupIntoWindows_empty_isEmpty() {
        assertEquals(emptyList(), DietWindow.groupIntoWindows(emptyList<Long>(), eh, fh) { it })
    }

    @Test fun groupIntoWindows_mealsWithinToleranceShareAWindow() {
        // 16:8 → tolerance 8 + 16/2 = 16h from the window's first meal; 15h still merges.
        val windows = DietWindow.groupIntoWindows(listOf(f, f + h(4), f + h(15)), eh, fh) { it }
        assertEquals(listOf(listOf(f, f + h(4), f + h(15))), windows)
    }

    @Test fun groupIntoWindows_mealBeyondToleranceOpensANewWindow() {
        val windows = DietWindow.groupIntoWindows(listOf(f, f + h(4), f + h(17), f + h(20)), eh, fh) { it }
        assertEquals(listOf(listOf(f, f + h(4)), listOf(f + h(17), f + h(20))), windows)
    }

    @Test fun groupIntoWindows_measuresFromTheWindowsFirstMealNotTheLast() {
        // 4h, then 8h after that: 12h from the first meal → same window; 17h from the first → new.
        val windows = DietWindow.groupIntoWindows(listOf(f, f + h(4), f + h(12), f + h(17)), eh, fh) { it }
        assertEquals(2, windows.size)
        assertEquals(listOf(f + h(17)), windows[1])
    }

    @Test fun compute_usesTheLatestWindowOnly() {
        val s = DietWindow.compute(listOf(f, f + h(2), f + h(20), f + h(21)), eh, fh, f + h(22))
        assertEquals(f + h(20), s.windowStart)
        assertEquals(f + h(21), s.lastEat)
        assertEquals(f + h(21), s.fastStartAt) // two meals → no single-meal grace
    }
}
