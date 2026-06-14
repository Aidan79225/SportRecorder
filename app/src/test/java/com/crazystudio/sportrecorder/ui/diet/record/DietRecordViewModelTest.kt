package com.crazystudio.sportrecorder.ui.diet.record

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietRecordViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun record(id: Int, time: Long) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList())

    private fun viewModel(repo: FakeEatRecordRepository) = DietRecordViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(repo),
        deleteEatRecord = DeleteEatRecordUseCase(repo),
    )

    @Test
    fun records_reflectRepositoryEmissions() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(record(1, 1_000L)))
        val vm = viewModel(repo)

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // StateFlow initial value
            assertEquals(listOf(record(1, 1_000L)), awaitItem())

            repo.setRecords(listOf(record(1, 1_000L), record(2, 2_000L)))
            assertEquals(listOf(record(1, 1_000L), record(2, 2_000L)), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRecord_deletesById() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository()
        val vm = viewModel(repo)

        vm.deleteRecord(record(7, 1_000L))
        advanceUntilIdle()

        assertEquals(listOf(7), repo.deletedIds)
    }
}
