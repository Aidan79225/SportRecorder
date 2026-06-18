package com.crazystudio.sportrecorder.ui.insights

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun record(id: Int, time: Long) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList())

    private fun viewModel(repo: FakeEatRecordRepository) = InsightsViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(repo),
        dietSettingsRepository = FakeDietSettingsRepository(),
        now = { fixedNow },
    )

    @Test
    fun uiState_reflectsRecordsAndDefaultsToMonth() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(record(1, fixedNow - h(3)), record(2, fixedNow - h(1))))
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem()
            val loaded = awaitItem()
            assertEquals(Period.MONTH, loaded.period)
            assertEquals(2, loaded.result.stats.mealCount)
            assertTrue(loaded.result.calendarDays.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setPeriod_updatesState() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository()
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem()
            vm.setPeriod(Period.WEEK)
            val updated = awaitItem()
            assertEquals(Period.WEEK, updated.period)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shiftMonth_movesAnchorBackward() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository()
        val vm = viewModel(repo)

        vm.uiState.test {
            // First item is the stateIn default (monthAnchor = 0L); skip it.
            awaitItem()
            // Second item is the real combined state produced by now() = fixedNow.
            val initial = awaitItem()
            vm.shiftMonth(-1)
            val shifted = awaitItem()
            assertTrue(shifted.monthAnchor < initial.monthAnchor)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
