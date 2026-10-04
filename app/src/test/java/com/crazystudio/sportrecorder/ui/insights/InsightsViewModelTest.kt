package com.crazystudio.sportrecorder.ui.insights

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private fun h(n: Long) = TimeUnit.HOURS.toMillis(n)
    private fun d(n: Long) = TimeUnit.DAYS.toMillis(n)
    private fun record(id: Int, time: Long) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList())

    private fun viewModel(repo: FakeEatRecordRepository) = InsightsViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(repo),
        dietSettingsRepository = FakeDietSettingsRepository(),
        photoImageSource = FakePhotoImageSource(),
        now = { fixedNow },
    )

    @Test
    fun uiState_reflectsRecordsAndDefaultsToMonth() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(record(1, fixedNow - h(3)), record(2, fixedNow - h(1))))
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem()
            val loaded = awaitItem()
            assertTrue(loaded.isLoaded)
            assertEquals(Period.MONTH, loaded.period)
            assertEquals(2, loaded.result.stats.mealCount)
            assertTrue(loaded.result.calendarDays.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initialState_isNotLoadedSoTheScreenDrawsNothingYet() {
        // The seed must not look like "no records": that flashed the empty card at every open.
        val seed = viewModel(FakeEatRecordRepository()).uiState.value
        assertFalse(seed.isLoaded)
        assertEquals(fixedNow, seed.anchor)
    }

    @Test
    fun setPeriod_updatesStateAndKeepsTheAnchor() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeEatRecordRepository())

        vm.uiState.test {
            awaitItem()
            val initial = awaitItem()
            vm.setPeriod(Period.WEEK)
            val updated = awaitItem()
            assertEquals(Period.WEEK, updated.period)
            assertEquals(initial.anchor, updated.anchor)
            assertEquals(7, updated.result.calendarDays.size)
            assertEquals(7, updated.result.bands.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shiftPeriod_monthMovesBackwardAndReturns() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeEatRecordRepository())

        vm.uiState.test {
            awaitItem()
            val initial = awaitItem()
            assertTrue(initial.result.isCurrentPeriod)
            vm.shiftPeriod(-1)
            val back = awaitItem()
            assertTrue(back.anchor < initial.anchor)
            assertFalse(back.result.isCurrentPeriod)
            vm.shiftPeriod(1)
            val forward = awaitItem()
            assertTrue(forward.anchor > back.anchor)
            assertTrue(forward.result.isCurrentPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shiftPeriod_doesNotPageIntoTheFuture() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeEatRecordRepository())

        vm.uiState.test {
            awaitItem()
            val initial = awaitItem()

            vm.shiftPeriod(1)
            // Force an emission so a moved anchor could not hide behind StateFlow conflation.
            vm.setPeriod(Period.WEEK)
            val after = awaitItem()

            assertEquals(Period.WEEK, after.period)
            assertEquals(initial.anchor, after.anchor)
            assertTrue(after.result.isCurrentPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shiftPeriod_weekPagesBySevenDays() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeEatRecordRepository())

        vm.uiState.test {
            awaitItem()
            awaitItem()
            vm.setPeriod(Period.WEEK)
            val thisWeek = awaitItem()
            vm.shiftPeriod(-1)
            val lastWeek = awaitItem()
            // The anchor lands on a local midnight seven days back (fixedNow itself is mid-day).
            assertTrue(lastWeek.anchor < fixedNow - d(6))
            assertTrue(lastWeek.anchor > fixedNow - d(8))
            // Day-based, not millis-based: a DST switch inside either week would shift the millis by an hour.
            assertEquals(7, lastWeek.result.range.dayStarts.size)
            assertTrue(lastWeek.result.range.endInclusive < thisWeek.result.range.start)
            assertFalse(lastWeek.result.isCurrentPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Local midnight a year before [now], in the zone the VM itself uses. */
    private fun oneYearAgo(now: Long): Long {
        val zone = TimeZone.currentSystemDefault()
        return Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
            .minus(1, DateTimeUnit.YEAR)
            .atStartOfDayIn(zone)
            .toEpochMilliseconds()
    }

    @Test
    fun onThisDay_followsTodayAndNotTheSelectedPeriod() = runTest(mainRule.testDispatcher.scheduler) {
        val anniversary = oneYearAgo(fixedNow)
        val repo = FakeEatRecordRepository(initial = listOf(record(1, anniversary + h(12))))
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem()
            assertEquals(anniversary, awaitItem().onThisDay?.dayStart)
            // Paging the period must not move or clear the memory: it is a fixed day.
            vm.shiftPeriod(-1)
            assertEquals(anniversary, awaitItem().onThisDay?.dayStart)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onThisDay_isNullWhenLastYearHasNothing() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(record(1, fixedNow - d(2))))
        val vm = viewModel(repo)

        vm.uiState.test {
            awaitItem()
            assertNull(awaitItem().onThisDay)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
