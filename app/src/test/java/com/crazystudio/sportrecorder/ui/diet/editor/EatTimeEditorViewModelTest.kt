package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeLocationProvider
import com.crazystudio.sportrecorder.fake.FakePhotoFileStore
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.fake.FakePhotoImporter
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.fake.FakeVenueRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The venue part of the meal editor: picking, creating, clearing, renaming (and merging). */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val pastMeal = 1_700_000_000_000L
    private val home = GeoPoint(24.0, 120.0)
    private val daHuWu = Venue(id = 1, name = "大戶屋", lat = 25.0, lng = 121.0, lastUsedAt = 1L)

    private class Editor(records: List<EatRecord> = emptyList(), venues: List<Venue> = emptyList()) {
        val records = FakeEatRecordRepository(records)
        val venues = FakeVenueRepository(venues)
        val locationProvider = FakeLocationProvider()

        fun viewModel(eatTimeId: Int? = null) = EatTimeEditorViewModel(
            loadEatRecord = LoadEatRecordUseCase(records),
            saveEatRecord = SaveEatRecordUseCase(records, FakeRemindersRescheduler()),
            locationProvider = locationProvider,
            venueRepository = venues,
            photoImporter = FakePhotoImporter(),
            photoFileStore = FakePhotoFileStore(),
            photoImageSource = FakePhotoImageSource(),
            savedStateHandle = if (eatTimeId == null) {
                SavedStateHandle()
            } else {
                SavedStateHandle(mapOf("eatTimeId" to eatTimeId))
            },
        )
    }

    private fun mealAt(venue: Venue?, location: GeoPoint? = home) =
        EatRecord(id = 1, time = pastMeal, location = location, note = "午餐", photos = emptyList(), venue = venue)

    @Test
    fun pickingAVenue_doesNotTouchTheRecordsOwnLocation() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        val before = vm.uiState.value.location

        vm.selectVenue(daHuWu)

        assertEquals("大戶屋", vm.uiState.value.venue?.name)
        // Two different facts: the venue knows where it is; the record still knows where you were.
        assertEquals(before, vm.uiState.value.location)
        assertEquals(EatTimeEditorUiState.LatLng(24.0, 120.0), vm.uiState.value.location)
        assertTrue(vm.save())
        assertEquals(home, editor.records.stored.single().location)
    }

    @Test
    fun theVenueIsOptional_aMealSavesWithoutOne() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        val vm = editor.viewModel()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        assertTrue(vm.save())

        assertNull(editor.records.stored.single().venue)
    }

    @Test
    fun savingAMealKeepsTheVenueItWasGiven() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        val vm = editor.viewModel()
        vm.selectVenue(daHuWu)
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        assertTrue(vm.save())

        assertEquals(daHuWu, editor.records.stored.single().venue)
    }

    @Test
    fun savingAMeal_touchesItsVenueSoRecencyReflectsUse() = runTest(mainRule.testDispatcher.scheduler) {
        val other = Venue(id = 2, name = "星巴克", lat = null, lng = null, lastUsedAt = 5_000L)
        val editor = Editor(venues = listOf(daHuWu, other))
        val vm = editor.viewModel()
        vm.selectVenue(daHuWu)
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        vm.save()

        assertTrue(editor.venues.stored.first { it.id == 1 }.lastUsedAt > 5_000L)
    }

    @Test
    fun editingOnlyTheNote_doesNotBumpTheVenuesRecency() = runTest(mainRule.testDispatcher.scheduler) {
        // Fixing last month's note is not a new visit: the venue must not jump above what the user
        // eats now, so saving an already-venued record leaves its lastUsedAt alone.
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.setNote("午餐(補記)")
        assertTrue(vm.save())

        assertEquals(1L, editor.venues.stored.single().lastUsedAt)
    }

    @Test
    fun switchingAnOldMealToAnotherVenue_touchesOnlyTheNewOne() = runTest(mainRule.testDispatcher.scheduler) {
        val other = Venue(id = 2, name = "星巴克", lat = null, lng = null, lastUsedAt = 5L)
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu, other))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.selectVenue(other)
        assertTrue(vm.save())

        assertEquals(1L, editor.venues.stored.first { it.id == 1 }.lastUsedAt)
        assertTrue(editor.venues.stored.first { it.id == 2 }.lastUsedAt > 5L)
    }

    @Test
    fun editingOnlyTheNote_keepsTheMealsVenue() = runTest(mainRule.testDispatcher.scheduler) {
        // The update path rewrites the whole row from the EatRecord the editor builds, so a record
        // rebuilt without its venue would silently lose it. Edit the note and nothing else.
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        assertEquals(daHuWu, vm.uiState.value.venue)

        vm.setNote("午餐(補記)")
        assertTrue(vm.save())

        val saved = editor.records.stored.single()
        assertEquals("午餐(補記)", saved.note)
        assertEquals(daHuWu, saved.venue)
    }

    @Test
    fun clearingTheVenue_removesItFromTheSavedMeal() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.clearVenue()
        assertTrue(vm.save())

        assertNull(editor.records.stored.single().venue)
        assertEquals(1, editor.venues.stored.size) // the venue itself stays for next time
    }

    @Test
    fun creatingAVenue_selectsItAndAdoptsTheRecordsFix() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.locationProvider.location = home
        val vm = editor.viewModel()
        vm.requestLocation()
        advanceUntilIdle()

        vm.createVenue("  大戶屋 ")
        advanceUntilIdle()

        val venue = requireNotNull(vm.uiState.value.venue)
        assertEquals("大戶屋", venue.name)
        assertEquals(24.0, venue.lat)
        assertEquals(EatTimeEditorUiState.LatLng(24.0, 120.0), vm.uiState.value.location)
    }

    @Test
    fun creatingAVenueWithABlankName_isRefused() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()

        vm.createVenue("")
        vm.createVenue("   　 ")
        advanceUntilIdle()

        assertTrue(editor.venues.stored.isEmpty())
        assertNull(vm.uiState.value.venue)
    }

    @Test
    fun renamingToABlankName_isRefused() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        val vm = editor.viewModel()

        vm.requestRename(daHuWu, "  　 ")
        advanceUntilIdle()

        assertEquals("大戶屋", editor.venues.stored.single().name)
        assertNull(vm.uiState.value.pendingMerge)
    }

    @Test
    fun renamingWithoutACollision_renamesAtOnceAndFollowsTheSelectedVenue() =
        runTest(mainRule.testDispatcher.scheduler) {
            val editor = Editor(venues = listOf(daHuWu))
            val vm = editor.viewModel()
            vm.selectVenue(daHuWu)

            vm.requestRename(daHuWu, "大戶屋 信義店")
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingMerge)
            assertEquals("大戶屋 信義店", editor.venues.stored.single().name)
            assertEquals("大戶屋 信義店", vm.uiState.value.venue?.name)
        }

    @Test
    fun renamingOntoAnExistingName_asksBeforeMoving() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        val typo = editor.venues.findOrCreate("大户屋", null, null, now = 1L)
        editor.venues.findOrCreate("大戶屋", null, null, now = 2L)
        editor.venues.recordCounts[typo.id] = 7

        vm.requestRename(typo, "大戶屋")
        advanceUntilIdle()

        val pending = requireNotNull(vm.uiState.value.pendingMerge)
        assertEquals("大戶屋", pending.intoName)
        // The dialog's whole job: say how many records are about to change hands.
        assertEquals(7, pending.movedRecords)
        // Nothing has moved yet — the user has not said yes.
        assertEquals(2, editor.venues.observeAll().first().size)
    }

    @Test
    fun cancellingAMerge_leavesEverythingWhereItWas() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        val typo = editor.venues.findOrCreate("大户屋", null, null, now = 1L)
        editor.venues.findOrCreate("大戶屋", null, null, now = 2L)
        vm.requestRename(typo, "大戶屋")
        advanceUntilIdle()

        vm.cancelRename()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        assertEquals(2, editor.venues.stored.size)
    }

    @Test
    fun confirmingAMerge_movesTheRecordsAndSwapsTheSelectedVenueForTheSurvivor() =
        runTest(mainRule.testDispatcher.scheduler) {
            val editor = Editor()
            val vm = editor.viewModel()
            val typo = editor.venues.findOrCreate("大户屋", null, null, now = 1L)
            val keeper = editor.venues.findOrCreate("大戶屋", null, null, now = 2L)
            editor.venues.recordCounts[typo.id] = 3
            vm.selectVenue(typo)
            vm.requestRename(typo, "大戶屋")
            advanceUntilIdle()

            vm.confirmRename()
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingMerge)
            assertEquals(listOf(keeper.id), editor.venues.stored.map { it.id })
            assertEquals(3, editor.venues.recordCount(keeper.id))
            assertEquals(keeper.id, vm.uiState.value.venue?.id)
        }

    @Test
    fun confirmingAMergeTwice_mergesOnlyOnce() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        val typo = editor.venues.findOrCreate("大户屋", null, null, now = 1L)
        val keeper = editor.venues.findOrCreate("大戶屋", null, null, now = 2L)
        editor.venues.recordCounts[typo.id] = 3
        vm.requestRename(typo, "大戶屋")
        advanceUntilIdle()

        // A double tap: the second must find nothing pending instead of renaming a venue that the
        // first one is about to delete.
        vm.confirmRename()
        vm.confirmRename()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        assertEquals(listOf(keeper.id), editor.venues.stored.map { it.id })
        assertEquals(3, editor.venues.recordCount(keeper.id))
    }

    @Test
    fun useThisPositionFor_correctsTheVenueFromTheRecordsFix() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.useThisPositionFor(daHuWu)
        advanceUntilIdle()

        val moved = editor.venues.stored.single()
        assertEquals(24.0, moved.lat)
        assertEquals(120.0, moved.lng)
    }

    @Test
    fun useThisPositionFor_doesNothingWhenTheRecordHasNoLocation() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu, location = null)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.useThisPositionFor(daHuWu)
        advanceUntilIdle()

        assertEquals(daHuWu, editor.venues.stored.single())
    }

    @Test
    fun theVenueNamedInTheNote_isOfferedFirstAsTheNoteIsTyped() = runTest(mainRule.testDispatcher.scheduler) {
        val newer = Venue(id = 2, name = "星巴克", lat = null, lng = null, lastUsedAt = 9L)
        val editor = Editor(venues = listOf(daHuWu, newer))
        val vm = editor.viewModel()
        advanceUntilIdle()
        assertEquals(listOf("星巴克", "大戶屋"), vm.uiState.value.venueOptions.map { it.name })

        vm.setNote("今天吃大戶屋")

        assertEquals(listOf("大戶屋", "星巴克"), vm.uiState.value.venueOptions.map { it.name })
    }
}
