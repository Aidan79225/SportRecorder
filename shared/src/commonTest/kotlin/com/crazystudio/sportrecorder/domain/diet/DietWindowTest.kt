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
    private fun min(n: Long) = n * 60_000L

    /** A meal starting at [start] that lasts [minutes] (the app's default is one hour). */
    private fun meal(start: Long, minutes: Long = 60) = MealSpan(start, start + min(minutes))

    @Test fun noEats_isIdle() {
        val s = DietWindow.compute(emptyList(), eh, fh, f)
        assertEquals(DietPhase.IDLE, s.phase)
        assertEquals(0f, s.ringProgress, 0f)
    }

    @Test fun singleEat_duringWindow_isEating() {
        val s = DietWindow.compute(listOf(meal(f)), eh, fh, f + h(2))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(0.25f, s.ringProgress, 0.001f)
        assertEquals(h(6), s.elapsedMillis)
        assertEquals(f, s.windowStart)
        assertEquals(f + ehMs, s.windowEnd)
    }

    @Test fun lastBiteBeforeWindowClose_stillEating() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(4))), eh, fh, f + h(5))
        assertEquals(DietPhase.EATING, s.phase)
        assertEquals(h(3), s.elapsedMillis)
    }

    @Test fun afterWindowClose_fastingCountsFromTheLastMealsEnd() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(4))), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(4), s.elapsedMillis)
        assertEquals(4f / 16f, s.ringProgress, 0.001f)
        assertEquals(f + h(5) + fhMs, s.fastTargetAt)
    }

    @Test fun fastingInProgress_singleMeal() {
        val s = DietWindow.compute(listOf(meal(f)), eh, fh, f + h(12))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(h(11), s.elapsedMillis)
        assertEquals(11f / 16f, s.ringProgress, 0.001f)
    }

    @Test fun success_whenSingleMealFastReachesTarget() {
        val s = DietWindow.compute(listOf(meal(f)), eh, fh, f + h(17))
        assertEquals(DietPhase.SUCCESS, s.phase)
        assertEquals(1f, s.ringProgress, 0f)
        assertEquals(h(16), s.elapsedMillis)
    }

    // --- the fast starts when the last meal ends -----------------------------

    @Test fun singleMeal_atTheDefaultDuration_fastStartsOneHourAfter_asBefore() {
        val s = DietWindow.compute(listOf(meal(f)), eh, fh, f + h(12))
        assertEquals(f + h(1), s.fastStartAt)
        assertEquals(f + h(1) + fhMs, s.fastTargetAt)
        assertEquals(f, s.lastEat)
    }

    @Test fun singleMeal_aDifferentDuration_movesTheFastStart() {
        val short = DietWindow.compute(listOf(meal(f, 30)), eh, fh, f + h(12))
        assertEquals(f + min(30), short.fastStartAt)
        assertEquals(f + min(30) + fhMs, short.fastTargetAt)

        val long = DietWindow.compute(listOf(meal(f, 120)), eh, fh, f + h(12))
        assertEquals(f + h(2), long.fastStartAt)
        assertEquals(f + h(2) + fhMs, long.fastTargetAt)
    }

    @Test fun twoMeals_fastStartsWhenTheLastMealEnds() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(3))), eh, fh, f + h(12))
        assertEquals(f + h(4), s.fastStartAt)
        assertEquals(f + h(3), s.lastEat)
    }

    @Test fun multiMeal_fastStartsWhenTheLastMealEnds() {
        val meals = listOf(meal(f), meal(f + h(2)), meal(f + h(3), 30))
        val s = DietWindow.compute(meals, eh, fh, f + h(12))
        assertEquals(f + h(3) + min(30), s.fastStartAt)
        assertEquals(f + h(3) + min(30) + fhMs, s.fastTargetAt)
    }

    @Test fun overlappingMeals_fastStartsAtTheLatestEnd() {
        // A long dinner with a quick drink inside it: the fast starts when the dinner ends.
        val s = DietWindow.compute(listOf(meal(f, 180), meal(f + h(1), 15)), eh, fh, f + h(12))
        assertEquals(f + h(3), s.fastStartAt)
        assertEquals(f + h(3) + fhMs, s.fastTargetAt)
        assertEquals(f + h(1), s.lastEat) // still the start of the last-started meal
    }

    @Test fun windowEnd_extendsToTheLastMealsEnd() {
        val meals = listOf(meal(f), meal(f + h(9)))
        val s = DietWindow.compute(meals, eh, fh, f + h(9) + min(30))
        assertEquals(f + h(10), s.windowEnd)
        assertEquals(DietPhase.EATING, s.phase) // still mid-meal, so not fasting yet
    }

    @Test fun aMealThatCrossesMidnight_isHandledLikeAnyOther() {
        // f is 22:13 UTC; a snack starts 30 minutes before the next UTC midnight and lasts 90.
        val dayMs = h(24)
        val midnight = (f / dayMs + 1) * dayMs
        val lateSnack = meal(midnight - min(30), 90)
        val s = DietWindow.compute(listOf(meal(f), lateSnack), eh, fh, midnight + h(12))
        assertEquals(f, s.windowStart) // grouped with the evening meal by its start
        assertEquals(midnight - min(30), s.lastEat)
        assertEquals(midnight + h(1), s.fastStartAt)
        assertEquals(midnight + h(1) + fhMs, s.fastTargetAt)
        assertEquals(DietPhase.FASTING, s.phase)
    }

    @Test fun lateMealWithinTolerance_mergesIntoWindow() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(4)), meal(f + h(10))), eh, fh, f + h(11))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f, s.windowStart)
        assertEquals(f + h(10), s.lastEat)
        assertEquals(f + h(11), s.fastStartAt)
        assertEquals(f + h(11) + fhMs, s.fastTargetAt)
    }

    @Test fun slightOverrun_extendsWindowEnd() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(9))), eh, fh, f + h(9))
        assertEquals(f, s.windowStart)
        assertEquals(f + h(10), s.windowEnd)
    }

    @Test fun mealBeyondTolerance_startsNewWindow() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(17))), eh, fh, f + h(18))
        assertEquals(f + h(17), s.windowStart)
        assertEquals(f + h(17), s.lastEat)
        assertEquals(f + h(17) + ehMs, s.windowEnd)
    }

    @Test fun boundary_exactlyAtWindowEnd_flipsToFasting() {
        val s = DietWindow.compute(listOf(meal(f)), eh, fh, f + ehMs)
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(ehMs - h(1), s.elapsedMillis)
    }

    @Test fun boundary_exactlyAtFastTarget_isSuccess() {
        val s = DietWindow.compute(listOf(meal(f)), eh, fh, f + h(1) + fhMs)
        assertEquals(DietPhase.SUCCESS, s.phase)
    }

    @Test fun boundary_multiMealFastTarget_isTheLastMealsEndPlusFastingHours() {
        val meals = listOf(meal(f), meal(f + h(3)))
        assertEquals(DietPhase.FASTING, DietWindow.compute(meals, eh, fh, f + h(4) + fhMs - 1).phase)
        assertEquals(DietPhase.SUCCESS, DietWindow.compute(meals, eh, fh, f + h(4) + fhMs).phase)
    }

    @Test fun multipleEatsInWindow_lastEatIsLatest() {
        val s = DietWindow.compute(listOf(meal(f), meal(f + h(1)), meal(f + h(3))), eh, fh, f + h(9))
        assertEquals(DietPhase.FASTING, s.phase)
        assertEquals(f + h(3), s.lastEat)
        assertEquals(h(5), s.elapsedMillis)
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
        val meals = listOf(meal(f), meal(f + h(2)), meal(f + h(20)), meal(f + h(21)))
        val s = DietWindow.compute(meals, eh, fh, f + h(22))
        assertEquals(f + h(20), s.windowStart)
        assertEquals(f + h(21), s.lastEat)
        assertEquals(f + h(22), s.fastStartAt) // the latest window's last meal ends at +22h
    }
}
