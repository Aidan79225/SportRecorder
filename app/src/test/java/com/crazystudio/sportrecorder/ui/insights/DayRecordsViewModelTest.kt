package com.crazystudio.sportrecorder.ui.insights

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DayRecordsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val zone = TimeZone.UTC

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(2026, month, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun record(id: Int, time: Long) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList())

    // 16:8 (the fake's default) merges up to 8 + 16/2 = 16h from a window's first meal: the 00:30
    // snack is 4.5h after dinner → the 10th's eating day; the 11th's 13:00 lunch is 17h after that
    // dinner → a new day.
    private val repo = FakeEatRecordRepository(
        initial = listOf(
            record(1, at(3, 12, 9)),
            record(2, at(3, 11, 0, 30)),
            record(3, at(3, 10, 20)),
            record(4, at(3, 11, 13)),
        ),
    )

    private fun viewModel(dayStart: Long) = DayRecordsViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(repo),
        dietSettingsRepository = FakeDietSettingsRepository(),
        photoImageSource = FakePhotoImageSource(),
        savedStateHandle = SavedStateHandle(mapOf("dayStart" to dayStart)),
        timeZone = zone,
    )

    @Test
    fun records_areThatEatingDaysMealsInOrder_lateSnackIncluded() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(at(3, 10, 0))
        assertEquals(at(3, 10, 0), vm.dayStart)

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // stateIn seed
            assertEquals(listOf(3, 2), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun records_theNextDayStartsWithTheMealAfterTheFast() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(at(3, 11, 0))

        vm.records.test {
            awaitItem()
            assertEquals(listOf(4), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun records_emptyForADayWithoutMeals() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(at(3, 13, 0))

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem())
            // The real computation is also empty; StateFlow conflates it, so nothing else arrives.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
