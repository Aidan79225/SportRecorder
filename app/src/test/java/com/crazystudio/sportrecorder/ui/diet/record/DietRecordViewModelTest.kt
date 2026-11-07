package com.crazystudio.sportrecorder.ui.diet.record

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.domain.venue.RecordVenueFilter
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DietRecordViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun venue(id: Int, name: String, address: String? = null) =
        Venue(id = id, name = name, lat = null, lng = null, lastUsedAt = 0L, address = address)

    private fun record(id: Int, time: Long, venue: Venue? = null) =
        EatRecord(id = id, time = time, location = null, note = null, photos = emptyList(), venue = venue)

    private val mcdonalds = venue(1, "麥當勞", address = "臺北市信義路")
    private val ichiran = venue(2, "Ichiran")

    // Newest first, as the repository gives them: two records at a venue, then one without.
    private val atMcdonalds = record(3, 3_000L, mcdonalds)
    private val atIchiran = record(2, 2_000L, ichiran)
    private val withoutVenue = record(1, 1_000L)
    private val everyRecord = listOf(atMcdonalds, atIchiran, withoutVenue)

    private fun viewModel(repo: FakeEatRecordRepository, observed: EatRecordRepository = repo) = DietRecordViewModel(
        observeEatRecords = ObserveEatRecordsUseCase(observed),
        deleteEatRecord = DeleteEatRecordUseCase(repo, FakeRemindersRescheduler()),
        photoImageSource = FakePhotoImageSource(),
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

    @Test
    fun records_followTheQuery() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeEatRecordRepository(initial = everyRecord))
        assertEquals("", vm.query.value)

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // StateFlow initial value
            assertEquals(everyRecord, awaitItem()) // no query yet: every record, in the repository's order

            vm.setQuery("麥當勞")
            assertEquals("麥當勞", vm.query.value)
            assertEquals(listOf(atMcdonalds), awaitItem())

            // Each query starts again from every record, not from what the last one left.
            vm.setQuery("ichiran")
            assertEquals(listOf(atIchiran), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearingTheQuery_showsEveryRecordAgain() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeEatRecordRepository(initial = everyRecord))

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // StateFlow initial value
            assertEquals(everyRecord, awaitItem())

            vm.setQuery("麥當勞")
            assertEquals(listOf(atMcdonalds), awaitItem())

            vm.setQuery("")
            assertEquals("", vm.query.value)
            assertEquals(everyRecord, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hasVenues_isFalseUntilARecordHasOne() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = listOf(withoutVenue))
        val vm = viewModel(repo)
        vm.records.launchIn(backgroundScope) // what the screen shows, kept live next to the flag

        vm.hasVenues.test {
            assertFalse(awaitItem()) // StateFlow initial value
            advanceUntilIdle() // the one record loads, and it has no venue
            expectNoEvents()

            repo.setRecords(listOf(withoutVenue, atMcdonalds))
            assertTrue(awaitItem())

            // A search that matches nothing empties the list, yet the search field has to stay to be cleared.
            vm.setQuery("nowhere")
            advanceUntilIdle()
            assertEquals(emptyList<EatRecord>(), vm.records.value)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchVenue_putsTheNameAndAddressInTheField() = runTest(mainRule.testDispatcher.scheduler) {
        // Another branch of the same chain: tapping the first one's venue must leave this one's record out.
        val atOtherBranch = record(4, 4_000L, venue(3, "麥當勞", address = "臺北市忠孝東路"))
        val stored = listOf(atOtherBranch) + everyRecord
        val vm = viewModel(FakeEatRecordRepository(initial = stored))

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // StateFlow initial value
            assertEquals(stored, awaitItem())

            vm.searchVenue(mcdonalds)
            assertEquals(RecordVenueFilter.queryFor(mcdonalds), vm.query.value)
            assertEquals(listOf(atMcdonalds), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteStillWorksWhileFiltered() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = everyRecord)
        val vm = viewModel(repo)

        vm.records.test {
            assertEquals(emptyList<EatRecord>(), awaitItem()) // StateFlow initial value
            assertEquals(everyRecord, awaitItem())
            vm.setQuery("麥當勞")
            assertEquals(listOf(atMcdonalds), awaitItem())

            vm.deleteRecord(atMcdonalds)
            advanceUntilIdle()

            assertEquals(listOf(atMcdonalds.id), repo.deletedIds)
            assertEquals(emptyList<EatRecord>(), awaitItem()) // it was the only match

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theSearchStaysWhileAQueryIsActive_evenWhenNoRecordHasAVenueAnymore() =
        runTest(mainRule.testDispatcher.scheduler) {
            val repo = FakeEatRecordRepository(initial = listOf(atMcdonalds, withoutVenue))
            val vm = viewModel(repo)
            vm.hasVenues.launchIn(backgroundScope) // kept live, to see that the venues really are gone

            vm.showSearch.test {
                assertFalse(awaitItem()) // StateFlow initial value
                assertTrue(awaitItem()) // a stored record has a venue
                assertTrue(vm.hasVenues.value)

                vm.setQuery("麥當勞")
                advanceUntilIdle()
                expectNoEvents()

                // The only record with a venue goes while the search is on. The list is empty now, so the
                // field has to stay: it is the only way to clear what is filtering the list.
                vm.deleteRecord(atMcdonalds)
                advanceUntilIdle()
                assertFalse(vm.hasVenues.value)
                expectNoEvents()

                vm.setQuery("")
                assertFalse(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theRepositoryIsObservedOnce_noMatterHowManyFlowsAreCollected() = runTest(mainRule.testDispatcher.scheduler) {
        val repo = FakeEatRecordRepository(initial = everyRecord)
        var observations = 0
        val observed = object : EatRecordRepository by repo {
            override fun observeAll(): Flow<List<EatRecord>> = repo.observeAll().onStart { observations++ }
        }
        val vm = viewModel(repo, observed)

        vm.records.launchIn(backgroundScope)
        vm.hasVenues.launchIn(backgroundScope)
        vm.showSearch.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(1, observations)
    }
}
