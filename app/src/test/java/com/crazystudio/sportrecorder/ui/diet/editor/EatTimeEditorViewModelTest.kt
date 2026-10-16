package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.fake.FakeEatRecordRepository
import com.crazystudio.sportrecorder.fake.FakeLocationProvider
import com.crazystudio.sportrecorder.fake.FakeMapsLinkResolver
import com.crazystudio.sportrecorder.fake.FakePhotoFileStore
import com.crazystudio.sportrecorder.fake.FakePhotoImageSource
import com.crazystudio.sportrecorder.fake.FakePhotoImporter
import com.crazystudio.sportrecorder.fake.FakeRemindersRescheduler
import com.crazystudio.sportrecorder.fake.FakeVenueRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// What Google Maps hands over when a place is shared: name, address, and a short link (#84).
private const val SHARE_TEXT = "鼎泰豐 信義店\n106臺北市大安區信義路二段194號\nhttps://maps.app.goo.gl/3oUGbkxLYE6jt7L29"

// The full URL that short link redirects to: pin and camera both at the restaurant.
private const val FULL_URL = "https://www.google.com/maps/place/" +
    "%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/" +
    "@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035" +
    "!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts"

/**
 * The venue part of the meal editor: picking, creating, clearing, renaming (and merging), and
 * opening it from a place shared out of Google Maps.
 */
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
        val resolver = FakeMapsLinkResolver(location = FULL_URL)

        fun viewModel(
            eatTimeId: Int? = null,
            sharedText: String? = null,
            handle: SavedStateHandle = SavedStateHandle(
                listOfNotNull(eatTimeId?.let { "eatTimeId" to it }, sharedText?.let { "sharedText" to it }).toMap(),
            ),
        ) = EatTimeEditorViewModel(
            loadEatRecord = LoadEatRecordUseCase(records),
            saveEatRecord = SaveEatRecordUseCase(records, FakeRemindersRescheduler()),
            locationProvider = locationProvider,
            venueRepository = venues,
            photoImporter = FakePhotoImporter(),
            photoFileStore = FakePhotoFileStore(),
            photoImageSource = FakePhotoImageSource(),
            savedStateHandle = handle,
            mapsLinkResolver = resolver,
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
    fun useThisPositionFor_confirmsInAMessage_thatConsumeClears() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        assertNull(vm.uiState.value.message)

        vm.useThisPositionFor(daHuWu)
        advanceUntilIdle()
        assertEquals(EditorMessage.VenuePositionUpdated, vm.uiState.value.message)

        vm.consumeMessage()
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun useThisPositionFor_doesNothingWhenTheRecordHasNoLocation() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu, location = null)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.useThisPositionFor(daHuWu)
        advanceUntilIdle()

        assertEquals(daHuWu, editor.venues.stored.single())
        assertNull(vm.uiState.value.message)
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

    // --- Opening the editor from a place shared out of Google Maps (#84) ---

    private val mapsPosition = GeoPoint(25.0335035, 121.530118)
    private val dinTaiFung = Venue(id = 1, name = "鼎泰豐 信義店", lat = 25.0, lng = 121.0, lastUsedAt = 1L)

    @Test
    fun sharedShortLink_createsTheVenueWithTheMapsPosition() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.locationProvider.location = home
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        vm.requestLocation()
        advanceUntilIdle()

        val venue = requireNotNull(vm.uiState.value.venue)
        assertEquals("鼎泰豐 信義店", venue.name)
        // Where the place is, from Maps — not where the phone happens to be.
        assertEquals(mapsPosition, GeoPoint(requireNotNull(venue.lat), requireNotNull(venue.lng)))
        assertEquals(listOf(venue), editor.venues.stored)
        assertEquals(1, editor.resolver.calls)
        assertFalse(vm.uiState.value.resolvingLink)
    }

    @Test
    fun sharedFullLink_neverCallsTheResolver() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel(sharedText = FULL_URL)
        advanceUntilIdle()

        assertEquals(0, editor.resolver.calls)
        assertEquals("鼎泰豐 信義店", vm.uiState.value.venue?.name)
        assertEquals(25.0335035, vm.uiState.value.venue?.lat)
    }

    @Test
    fun nonMapsText_neverCallsTheResolver() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel(sharedText = "好吃 https://evil.example/maps/place/x")
        advanceUntilIdle()

        assertEquals(ShareNotice.NotAMapLink, vm.uiState.value.shareNotice)
        assertEquals(0, editor.resolver.calls)
        assertNull(vm.uiState.value.venue)
        assertFalse(vm.uiState.value.resolvingLink)
    }

    @Test
    fun unresolvableLink_saysSoAndLeavesTheVenueEmpty() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.resolver.location = null
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        assertEquals(ShareNotice.MapLinkUnreadable, vm.uiState.value.shareNotice)
        assertNull(vm.uiState.value.venue)
        assertTrue(editor.venues.stored.isEmpty())
        assertFalse(vm.uiState.value.resolvingLink)
    }

    @Test
    fun aLocationToAnotherHost_isUnreadable() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.resolver.location = "https://consent.google.com/ml?continue=https://www.google.com/maps/place/x"
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        assertEquals(ShareNotice.MapLinkUnreadable, vm.uiState.value.shareNotice)
        assertNull(vm.uiState.value.venue)
        assertTrue(editor.venues.stored.isEmpty())
    }

    @Test
    fun linkWithoutAName_isUnreadableEvenWithCoordinates() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.resolver.location = "https://www.google.com/maps/@25.0335035,121.530118,17z"
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        // Coordinates alone name nothing; a venue needs a name, so they are dropped.
        assertEquals(ShareNotice.MapLinkUnreadable, vm.uiState.value.shareNotice)
        assertNull(vm.uiState.value.venue)
        assertTrue(editor.venues.stored.isEmpty())
    }

    @Test
    fun existingVenueFarAway_asksBeforeMoving() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 12
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        val pending = requireNotNull(vm.uiState.value.pendingMove)
        assertEquals(dinTaiFung, pending.venue)
        assertEquals(mapsPosition, pending.to)
        // The dialog's whole job: moving a venue moves its marker for every record at it.
        assertEquals(12, pending.movedRecords)
        assertEquals(dinTaiFung.id, vm.uiState.value.venue?.id)
        // Nothing has moved yet — the user has not said yes.
        assertEquals(dinTaiFung, editor.venues.stored.single())
    }

    @Test
    fun confirmMove_movesIt_cancelMove_keepsIt() = runTest(mainRule.testDispatcher.scheduler) {
        val confirming = Editor(venues = listOf(dinTaiFung))
        confirming.venues.recordCounts[dinTaiFung.id] = 2
        val yes = confirming.viewModel(sharedText = SHARE_TEXT)
        val cancelling = Editor(venues = listOf(dinTaiFung))
        cancelling.venues.recordCounts[dinTaiFung.id] = 2
        val no = cancelling.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        yes.confirmMove()
        no.cancelMove()
        advanceUntilIdle()

        val moved = confirming.venues.stored.single()
        assertEquals(mapsPosition, GeoPoint(requireNotNull(moved.lat), requireNotNull(moved.lng)))
        assertEquals(moved, yes.uiState.value.venue)
        assertNull(yes.uiState.value.pendingMove)

        assertEquals(dinTaiFung, cancelling.venues.stored.single())
        assertEquals(dinTaiFung, no.uiState.value.venue)
        assertNull(no.uiState.value.pendingMove)
    }

    @Test
    fun existingVenueFarAwayButUnused_movesWithoutAsking() = runTest(mainRule.testDispatcher.scheduler) {
        // No record is at it yet, so no marker moves: there is nothing to ask about.
        val editor = Editor(venues = listOf(dinTaiFung))
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        val moved = dinTaiFung.copy(lat = mapsPosition.lat, lng = mapsPosition.lng)
        assertNull(vm.uiState.value.pendingMove)
        assertEquals(moved, editor.venues.stored.single())
        assertEquals(moved, vm.uiState.value.venue)
    }

    @Test
    fun existingVenueWithoutPosition_getsTheMapsPosition() = runTest(mainRule.testDispatcher.scheduler) {
        val unplaced = dinTaiFung.copy(lat = null, lng = null)
        val editor = Editor(venues = listOf(unplaced))
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        val learned = unplaced.copy(lat = mapsPosition.lat, lng = mapsPosition.lng)
        assertEquals(learned, editor.venues.stored.single())
        assertEquals(learned, vm.uiState.value.venue)
        assertNull(vm.uiState.value.pendingMove)
    }

    @Test
    fun theRecordsOwnLocationIsStillTheDevicesFix() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.locationProvider.location = home
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        vm.requestLocation()
        advanceUntilIdle()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        assertEquals(EatTimeEditorUiState.LatLng(24.0, 120.0), vm.uiState.value.location)
        assertTrue(vm.save())

        val saved = editor.records.stored.single()
        // Two different facts: the venue is where Maps says; the record is where you were.
        assertEquals(home, saved.location)
        assertEquals(25.0335035, saved.venue?.lat)
    }

    @Test
    fun savingBeforeTheLinkResolves_savesWithoutAVenue() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val slowNetwork = CompletableDeferred<Unit>()
        editor.resolver.gate = slowNetwork
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.resolvingLink)

        // A share never blocks a meal: saving goes through while the link is still resolving.
        assertTrue(vm.save())
        slowNetwork.complete(Unit)
        advanceUntilIdle()

        assertNull(editor.records.stored.single().venue)
        // The meal is saved; a late answer must not create or move a venue behind the user's back.
        assertTrue(editor.venues.stored.isEmpty())
    }

    @Test
    fun pickingAVenueWhileResolving_keepsTheUsersChoice() = runTest(mainRule.testDispatcher.scheduler) {
        // The link names a venue that exists elsewhere: left alone, the share would select it and ask to move it.
        val editor = Editor(venues = listOf(daHuWu, dinTaiFung.copy(id = 2)))
        val slowNetwork = CompletableDeferred<Unit>()
        editor.resolver.gate = slowNetwork
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        vm.selectVenue(daHuWu)
        slowNetwork.complete(Unit)
        advanceUntilIdle()

        assertEquals(daHuWu, vm.uiState.value.venue)
        assertNull(vm.uiState.value.pendingMove)
        assertFalse(vm.uiState.value.resolvingLink)
        assertEquals(listOf(daHuWu, dinTaiFung.copy(id = 2)), editor.venues.stored)
    }

    @Test
    fun clearingOrCreatingAVenueWhileResolving_keepsTheUsersChoice() = runTest(mainRule.testDispatcher.scheduler) {
        val clearing = Editor()
        val creating = Editor()
        val slowNetwork = CompletableDeferred<Unit>()
        clearing.resolver.gate = slowNetwork
        creating.resolver.gate = slowNetwork
        val cleared = clearing.viewModel(sharedText = SHARE_TEXT)
        val created = creating.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        cleared.clearVenue()
        created.createVenue("大戶屋")
        slowNetwork.complete(Unit)
        advanceUntilIdle()

        assertNull(cleared.uiState.value.venue)
        assertTrue(clearing.venues.stored.isEmpty())
        assertEquals("大戶屋", created.uiState.value.venue?.name)
        assertEquals(listOf("大戶屋"), creating.venues.stored.map { it.name })
        assertFalse(cleared.uiState.value.resolvingLink)
        assertFalse(created.uiState.value.resolvingLink)
    }

    @Test
    fun pickingAVenueWhileAskedToMove_withdrawsTheQuestion() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu, dinTaiFung.copy(id = 2)))
        editor.venues.recordCounts[2] = 4
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingMove)

        vm.selectVenue(daHuWu)

        assertNull(vm.uiState.value.pendingMove)
        assertEquals(daHuWu, vm.uiState.value.venue)
        assertEquals(dinTaiFung.copy(id = 2), editor.venues.stored.last()) // not moved
    }

    @Test
    fun aShareResolvingDuringTheSave_createsNoVenue() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val slowNetwork = CompletableDeferred<Unit>()
        val slowDisk = CompletableDeferred<Unit>()
        editor.resolver.gate = slowNetwork
        editor.records.saveGate = slowDisk
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)
        advanceUntilIdle()

        // The link answers while the meal is being written: the write already took "no venue".
        val saving = async { vm.save() }
        advanceUntilIdle()
        slowNetwork.complete(Unit)
        advanceUntilIdle()
        slowDisk.complete(Unit)
        assertTrue(saving.await())
        advanceUntilIdle()

        assertNull(editor.records.stored.single().venue)
        assertTrue(editor.venues.stored.isEmpty())
        assertNull(vm.uiState.value.venue)
    }

    @Test
    fun aMoveAlreadyDeclined_isNotAskedAgainAfterProcessDeath() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 5
        val handle = SavedStateHandle(mapOf("sharedText" to SHARE_TEXT))
        val before = editor.viewModel(handle = handle)
        advanceUntilIdle()
        requireNotNull(before.uiState.value.pendingMove)
        before.cancelMove()

        // The process dies; the route (with its shared text) and the saved state come back.
        val after = editor.viewModel(handle = handle)
        advanceUntilIdle()

        assertEquals(dinTaiFung, after.uiState.value.venue)
        assertNull(after.uiState.value.pendingMove)
        assertEquals(dinTaiFung, editor.venues.stored.single())
    }

    @Test
    fun runningTheSameShareTwice_isIdempotent() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val first = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        val second = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        val venue = editor.venues.stored.single()
        assertEquals(venue.id, first.uiState.value.venue?.id)
        assertEquals(venue.id, second.uiState.value.venue?.id)
        assertNull(second.uiState.value.pendingMove)
    }

    @Test
    fun editModeIgnoresSharedText() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null)))
        val vm = editor.viewModel(eatTimeId = 1, sharedText = SHARE_TEXT)
        advanceUntilIdle()

        assertEquals(0, editor.resolver.calls)
        assertNull(vm.uiState.value.venue)
        assertTrue(editor.venues.stored.isEmpty())
    }

    @Test
    fun withoutSharedText_nothingChanges() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        assertFalse(vm.uiState.value.resolvingLink)
        advanceUntilIdle()

        assertEquals(0, editor.resolver.calls)
        assertFalse(vm.uiState.value.resolvingLink)
        assertNull(vm.uiState.value.shareNotice)
        assertNull(vm.uiState.value.venue)
    }

    @Test
    fun choosingAVenue_clearsTheShareNotice() = runTest(mainRule.testDispatcher.scheduler) {
        val selecting = Editor(venues = listOf(daHuWu))
        val clearing = Editor()
        val creating = Editor()
        val selected = selecting.viewModel(sharedText = "hello")
        val cleared = clearing.viewModel(sharedText = "hello")
        val created = creating.viewModel(sharedText = "hello")
        advanceUntilIdle()
        assertEquals(ShareNotice.NotAMapLink, selected.uiState.value.shareNotice)

        selected.selectVenue(daHuWu)
        cleared.clearVenue()
        created.createVenue("大戶屋")
        advanceUntilIdle()

        // The notice explains an empty venue; once the user picks (or clears) it, it has done its job.
        assertNull(selected.uiState.value.shareNotice)
        assertNull(cleared.uiState.value.shareNotice)
        assertNull(created.uiState.value.shareNotice)
    }

    @Test
    fun theShareNotice_staysUntilTheUserChooses() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel(sharedText = "hello")
        advanceUntilIdle()

        // No timer: typing a note or touching location leaves it in place.
        vm.setNote("午餐")
        vm.clearLocation()
        advanceTimeBy(60_000)
        advanceUntilIdle()

        assertEquals(ShareNotice.NotAMapLink, vm.uiState.value.shareNotice)
    }
}
