package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.venue.RoundedDistance
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The venue part of the meal editor: picking, creating, clearing, and opening it from a place
 * shared out of Google Maps. Editing, merging and deleting venues: [EatTimeEditorViewModelVenueEditTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

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

    // A venue the repair at app start has just renamed from a glued Maps-app title, and the same store
    // within 50 m: the repair next merges the first into the second in the background, while the
    // editor may have it open.
    private val mergedAway = Venue(id = 1, name = "鼎泰豐 信義店", lat = 25.03350, lng = 121.5301, lastUsedAt = 1L)
    private val store = Venue(id = 2, name = "鼎泰豐 信義店", lat = 25.03352, lng = 121.5301, lastUsedAt = 2L)

    @Test
    fun aVenueMergedAwayWhileItsMealIsOpen_isShownAndSavedAsTheOneItWentInto() =
        runTest(mainRule.testDispatcher.scheduler) {
            val editor = Editor(listOf(mealAt(venue = mergedAway)), listOf(mergedAway, store))
            val vm = editor.viewModel(eatTimeId = 1)
            advanceUntilIdle()
            assertEquals(mergedAway, vm.uiState.value.venue)

            val survivor = editor.venues.merge(fromId = mergedAway.id, intoId = store.id, now = 2L)
            advanceUntilIdle()

            // The editor follows the meal's records to where they went…
            assertEquals(survivor, vm.uiState.value.venue)
            vm.setNote("午餐(補記)")
            assertTrue(vm.save())
            // …and saves the meal there, never at the venue that is gone; nor is fixing it a new use.
            assertEquals(survivor, editor.records.stored.single().venue)
            assertEquals(2L, editor.venues.stored.single().lastUsedAt)
        }

    @Test
    fun aVenueMergedAwayJustBeforeTheSave_isSavedAsTheOneItWentInto() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(mergedAway, store))
        val vm = editor.viewModel()
        advanceUntilIdle()
        vm.selectVenue(mergedAway)

        // Merged with no chance for the list to show it: the save asks for the venue by id.
        editor.venues.merge(fromId = mergedAway.id, intoId = store.id, now = 2L)
        assertTrue(vm.save())

        assertEquals(store.id, editor.records.stored.single().venue?.id)
        // A new meal is a use of the venue it is saved at.
        assertTrue(editor.venues.stored.single().lastUsedAt > 2L)
    }

    @Test
    fun aVenueDeletedByAnotherWriter_isNeitherShownNorSaved() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        editor.venues.delete(daHuWu.id)
        advanceUntilIdle()

        assertNull(vm.uiState.value.venue)
        assertTrue(vm.save())
        assertNull(editor.records.stored.single().venue)
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
    fun aFarSameNameVenue_asksWhichStore() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 12
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        val pending = requireNotNull(vm.uiState.value.pendingStore)
        assertEquals(dinTaiFung, pending.venue)
        assertEquals(mapsPosition, pending.to)
        // The saved one is about 53.5 km from the link's pin.
        assertEquals(RoundedDistance.Kilometers(535), pending.distance)
        // Moving a venue moves its marker for every record at it, so the question carries the count.
        assertEquals(12, pending.movedRecords)
        assertEquals("鼎泰豐 信義店", pending.sharedName)
        assertEquals(dinTaiFung.id, vm.uiState.value.venue?.id)
        assertNull(vm.uiState.value.pendingMove)
        // Nothing is written yet — the user has not answered.
        assertEquals(listOf(dinTaiFung), editor.venues.stored)
    }

    @Test
    fun anotherBranch_createsANewVenueAtThePin_andSelectsIt() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)

        vm.answerAnotherBranch()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingStore)
        val branch = editor.venues.stored.last()
        assertEquals(2, editor.venues.stored.size)
        assertEquals("鼎泰豐 信義店", branch.name)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(branch.lat), requireNotNull(branch.lng)))
        assertEquals(branch, vm.uiState.value.venue)
        // The other branch stays exactly where it was.
        assertEquals(dinTaiFung, editor.venues.stored.first())
    }

    @Test
    fun sameStoreMove_movesIt() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung.copy(address = "舊地址")))
        editor.venues.recordCounts[dinTaiFung.id] = 2
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)

        vm.answerSameStoreMove()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingStore)
        val moved = editor.venues.stored.single()
        assertEquals(dinTaiFung.id, moved.id)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(moved.lat), requireNotNull(moved.lng)))
        // The old address described the old position.
        assertNull(moved.address)
        assertEquals(moved, vm.uiState.value.venue)
    }

    @Test
    fun sameStoreKeep_keepsIt() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 2
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)

        vm.answerSameStoreKeep()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingStore)
        assertEquals(listOf(dinTaiFung), editor.venues.stored)
        assertEquals(dinTaiFung, vm.uiState.value.venue)
    }

    @Test
    fun zeroRecords_stillAsks() = runTest(mainRule.testDispatcher.scheduler) {
        // Even with no record at it, the user is asked: it may be another branch (#84 moved it silently).
        val editor = Editor(venues = listOf(dinTaiFung))
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        val pending = requireNotNull(vm.uiState.value.pendingStore)
        assertEquals(dinTaiFung, pending.venue)
        assertEquals(0, pending.movedRecords)
        assertEquals(listOf(dinTaiFung), editor.venues.stored)
        assertEquals(dinTaiFung, vm.uiState.value.venue)
    }

    @Test
    fun aCreatedOrMovedVenue_getsItsAddressFilled() = runTest(mainRule.testDispatcher.scheduler) {
        val address = "信義路二段 194號"
        val creating = Editor()
        val branching = Editor(venues = listOf(dinTaiFung))
        val moving = Editor(venues = listOf(dinTaiFung))
        listOf(creating, branching, moving).forEach { it.lookup.address = address }
        creating.viewModel(sharedText = SHARE_TEXT)
        val branched = branching.viewModel(sharedText = SHARE_TEXT)
        val moved = moving.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        // Asking which store looks nothing up: only what the VM creates or moves gets an address.
        assertTrue(branching.lookup.asked.isEmpty())

        branched.answerAnotherBranch()
        moved.answerSameStoreMove()
        advanceUntilIdle()

        assertEquals(address, creating.venues.stored.single().address)
        assertEquals(listOf(null, address), branching.venues.stored.map { it.address })
        assertEquals(address, moving.venues.stored.single().address)
        assertEquals(listOf(mapsPosition), moving.lookup.asked)
    }

    @Test
    fun aSharedVenue_showsTheAddressFilledInAfterIt() = runTest(mainRule.testDispatcher.scheduler) {
        // A web-made link carries no address: the reverse lookup fills it after the venue is chosen.
        val editor = Editor()
        editor.lookup.address = "信義路二段 194 號"
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()

        assertEquals("信義路二段 194 號", editor.venues.stored.single().address)
        assertEquals(editor.venues.stored.single(), vm.uiState.value.venue)
    }

    @Test
    fun aPickedVenue_showsTheAddressThePickerFillsIn() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        editor.lookup.address = "仁愛路"
        val vm = editor.viewModel()
        advanceUntilIdle()

        vm.selectVenue(daHuWu)
        vm.onPickerShown()
        advanceUntilIdle()

        // A venue picked by hand gets its address when the picker back-fills it.
        assertEquals(daHuWu.copy(address = "仁愛路"), vm.uiState.value.venue)
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
        assertNull(vm.uiState.value.pendingStore)
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
        assertNull(vm.uiState.value.pendingStore)
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
    fun pickingAVenueWhileAsked_withdrawsTheQuestion() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu, dinTaiFung.copy(id = 2)))
        editor.venues.recordCounts[2] = 4
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)

        vm.selectVenue(daHuWu)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingStore)
        assertEquals(daHuWu, vm.uiState.value.venue)
        // Neither moved nor branched.
        assertEquals(listOf(daHuWu, dinTaiFung.copy(id = 2)), editor.venues.stored)
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
    fun anAnsweredQuestion_isNotAskedAgainAfterProcessDeath() = runTest(mainRule.testDispatcher.scheduler) {
        val keeping = Editor(venues = listOf(dinTaiFung))
        val branching = Editor(venues = listOf(dinTaiFung))
        val keepHandle = SavedStateHandle(mapOf("sharedText" to SHARE_TEXT))
        val branchHandle = SavedStateHandle(mapOf("sharedText" to SHARE_TEXT))
        val kept = keeping.viewModel(handle = keepHandle)
        val branched = branching.viewModel(handle = branchHandle)
        advanceUntilIdle()
        kept.answerSameStoreKeep()
        branched.answerAnotherBranch()
        advanceUntilIdle()

        // The process dies; the route (with its shared text) and the saved state come back.
        val keptAgain = keeping.viewModel(handle = keepHandle)
        val branchedAgain = branching.viewModel(handle = branchHandle)
        advanceUntilIdle()

        assertNull(keptAgain.uiState.value.pendingStore)
        assertEquals(dinTaiFung, keptAgain.uiState.value.venue)
        assertEquals(listOf(dinTaiFung), keeping.venues.stored)
        // The new branch is at the pin now: it is the one picked, and no third venue appears.
        assertNull(branchedAgain.uiState.value.pendingStore)
        assertEquals(2, branching.venues.stored.size)
        assertEquals(branching.venues.stored.last().id, branchedAgain.uiState.value.venue?.id)
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
        assertNull(second.uiState.value.pendingStore)
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
