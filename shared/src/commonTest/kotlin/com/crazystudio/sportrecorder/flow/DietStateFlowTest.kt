package com.crazystudio.sportrecorder.flow

import com.crazystudio.sportrecorder.backup.fakes.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeEatRecordRepository
import com.crazystudio.sportrecorder.backup.fakes.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.domain.diet.DietPhase
import com.crazystudio.sportrecorder.domain.diet.DietWindow
import com.crazystudio.sportrecorder.domain.diet.DietWindowState
import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Home / 斷食視窗 journey at the use-case layer — Android-free, so it also runs on iOS in CI.
 *
 * This pins the *wiring*: that saving a meal or changing the fasting window re-drives
 * [ObserveDietStateUseCase] and moves the computed phase. The arithmetic itself is already
 * exhaustively covered by `DietWindowTest`, and is not re-asserted here.
 */
class DietStateFlowTest {

    private val f = 1_700_000_000_000L
    private fun h(n: Long) = n * 3_600_000L

    private class Harness(settings: DietSettings = DietSettings(fastingHours = 16, eatingHours = 8)) {
        val eatRepo = FakeEatRecordRepository()
        val settingsRepo = FakeDietSettingsRepository(settings)
        val rescheduler = FakeRemindersRescheduler()
        val observeDietState = ObserveDietStateUseCase(eatRepo, settingsRepo)
        val save = SaveEatRecordUseCase(eatRepo, rescheduler)
        val delete = DeleteEatRecordUseCase(eatRepo, rescheduler)
        val selectWindow = SaveFastingSelectionUseCase(settingsRepo, rescheduler)
    }

    private fun meal(time: Long) =
        EatRecord(id = 0, time = time, location = null, note = null, photos = emptyList())

    /** Runs the snapshot the app would render at [now] through the same calculator the UI uses. */
    private suspend fun Harness.stateAt(now: Long): DietWindowState {
        val snapshot = observeDietState(now).first()
        return DietWindow.compute(
            eatTimesAsc = snapshot.eatTimesAsc,
            eatingHours = snapshot.settings.eatingHours,
            fastingHours = snapshot.settings.fastingHours,
            now = now,
        )
    }

    @Test
    fun noRecords_isIdleWithNoFastWindow() = runTest {
        val app = Harness()

        val state = app.stateAt(f)

        assertEquals(DietPhase.IDLE, state.phase)
        assertNull(state.windowStart)
        assertNull(state.fastTargetAt)
    }

    @Test
    fun savingAMeal_movesIdleToEating() = runTest {
        val app = Harness()
        assertEquals(DietPhase.IDLE, app.stateAt(f).phase)

        app.save(meal(f), emptyList(), emptyList(), now = f)

        assertEquals(DietPhase.EATING, app.stateAt(f).phase)
    }

    @Test
    fun theSameMealWalksEatingThenFastingThenSuccess() = runTest {
        val app = Harness()
        app.save(meal(f), emptyList(), emptyList(), now = f)

        // Single meal, 16:8 → window closes at +8h; the fast clock starts at +1h and targets +17h.
        assertEquals(DietPhase.EATING, app.stateAt(f + h(2)).phase)
        assertEquals(DietPhase.FASTING, app.stateAt(f + h(12)).phase)
        assertEquals(DietPhase.SUCCESS, app.stateAt(f + h(17)).phase)
    }

    @Test
    fun aSecondMealInTheWindowMovesTheFastClock() = runTest {
        val app = Harness()
        app.save(meal(f), emptyList(), emptyList(), now = f)
        val beforeSecondMeal = app.stateAt(f + h(3))

        app.save(meal(f + h(3)), emptyList(), emptyList(), now = f + h(3))
        val afterSecondMeal = app.stateAt(f + h(3))

        // Single-meal window got a 1h grace; two meals start the fast at the later bite instead.
        assertEquals(f + h(1), beforeSecondMeal.fastStartAt)
        assertEquals(f + h(3), afterSecondMeal.fastStartAt)
        assertEquals(f, afterSecondMeal.windowStart)
    }

    @Test
    fun aMealBeyondTheToleranceOpensANewWindow() = runTest {
        val app = Harness()
        app.save(meal(f), emptyList(), emptyList(), now = f)
        app.save(meal(f + h(20)), emptyList(), emptyList(), now = f + h(20))

        val state = app.stateAt(f + h(21))

        assertEquals(f + h(20), state.windowStart)
        assertEquals(DietPhase.EATING, state.phase)
    }

    @Test
    fun changingTheFastingWindowChangesThePhaseUnderTheSameMeal() = runTest {
        val app = Harness()
        app.save(meal(f), emptyList(), emptyList(), now = f)
        // 16:8 → still eating 6h in.
        assertEquals(DietPhase.EATING, app.stateAt(f + h(6)).phase)

        app.selectWindow(FastingWindow(fastingHours = 20, eatingHours = 4))

        // 20:4 → the window closed at +4h, so the same moment is now a fast in progress.
        val state = app.stateAt(f + h(6))
        assertEquals(DietPhase.FASTING, state.phase)
        assertEquals(DietSettings(20, 4), app.settingsRepo.state.value)
    }

    @Test
    fun deletingTheLastMealReturnsToIdle() = runTest {
        val app = Harness()
        app.save(meal(f), emptyList(), emptyList(), now = f)
        assertEquals(DietPhase.EATING, app.stateAt(f).phase)

        app.delete(app.eatRepo.state.value.single().id)

        assertEquals(DietPhase.IDLE, app.stateAt(f).phase)
    }

    @Test
    fun crossMidnightWindowKeepsRunningPastTheDayBoundary() = runTest {
        // Pure epoch-millis arithmetic, so this holds in every timezone: a 22:00-ish meal's
        // window end and fast target both land on the following calendar day.
        val app = Harness()
        val lateMeal = f + h(22)
        app.save(meal(lateMeal), emptyList(), emptyList(), now = lateMeal)

        val state = app.stateAt(lateMeal + h(4))

        assertEquals(DietPhase.EATING, state.phase)
        assertEquals(lateMeal + h(8), state.windowEnd)
        assertEquals(lateMeal + h(1) + h(16), state.fastTargetAt)
    }
}
