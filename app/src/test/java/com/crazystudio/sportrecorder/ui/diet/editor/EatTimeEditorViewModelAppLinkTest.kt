package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Links made by the Google Maps **app** (#84 addendum, 2026-10-28): no pin, the address glued to the
 * name. Pasted or shared, they give the clean name, the link's own (trimmed) address, and the position
 * the phone's geocoder finds for that address.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelAppLinkTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // What the reverse lookup would say at the pin: the link's own address must win over it.
    private val geocoderAddress = "信義路二段 194 號"

    /** An editor whose clipboard holds the Maps-app link; [locatable] = the geocoder finds its address. */
    private fun appLinkEditor(venues: List<Venue> = emptyList(), locatable: Boolean = true) =
        Editor(venues = venues).apply {
            resolver.location = APP_LOCATION
            clipboard.text = APP_LINK
            lookup.address = geocoderAddress
            if (locatable) lookup.positions = mapOf(APP_FULL_ADDRESS to mapsPosition)
        }

    @Test
    fun pastingAnAndroidAppLink_createsTheCleanVenueWithAddressAndPin() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = appLinkEditor()
        val vm = editor.viewModel()
        advanceUntilIdle()

        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()

        val venue = editor.venues.stored.single()
        assertEquals("鼎泰豐 信義店", venue.name)
        assertEquals(APP_ADDRESS, venue.address)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(venue.lat), requireNotNull(venue.lng)))
        assertEquals(venue, vm.uiState.value.venue)
        // Placed by the link's full address; the reverse lookup has nothing left to fill.
        assertEquals(listOf(APP_FULL_ADDRESS), editor.lookup.queried)
        assertTrue(editor.lookup.asked.isEmpty())
        assertNull(vm.uiState.value.shareNotice)
    }

    @Test
    fun sharingAnAndroidAppLink_readsItTheSameWay() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = appLinkEditor()
        val vm = editor.viewModel(sharedText = APP_LINK)
        advanceUntilIdle()

        val venue = editor.venues.stored.single()
        assertEquals("鼎泰豐 信義店", venue.name)
        assertEquals(APP_ADDRESS, venue.address)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(venue.lat), requireNotNull(venue.lng)))
        assertEquals(venue, vm.uiState.value.venue)
    }

    @Test
    fun pastingItTwice_reusesOneVenue() = runTest(mainRule.testDispatcher.scheduler) {
        val placed = appLinkEditor()
        val unplaced = appLinkEditor(locatable = false)
        val placedVm = placed.viewModel()
        val unplacedVm = unplaced.viewModel()

        repeat(2) {
            assertTrue(placedVm.pasteMapsLink())
            assertTrue(unplacedVm.pasteMapsLink())
            advanceUntilIdle()
        }

        // Found again at the same looked-up position, or (unlocatable) by its clean name.
        listOf(placed to placedVm, unplaced to unplacedVm).forEach { (editor, vm) ->
            val venue = editor.venues.stored.single()
            assertEquals("鼎泰豐 信義店", venue.name)
            assertEquals(APP_ADDRESS, venue.address)
            assertEquals(venue.id, vm.uiState.value.venue?.id)
            assertNull(vm.uiState.value.pendingStore)
        }
    }

    @Test
    fun pastingWhenTheAddressCantBeLocated_createsItWithNameAndAddressOnly() =
        runTest(mainRule.testDispatcher.scheduler) {
            val editor = appLinkEditor(locatable = false)
            val vm = editor.viewModel()

            assertTrue(vm.pasteMapsLink())
            advanceUntilIdle()

            val venue = editor.venues.stored.single()
            assertEquals("鼎泰豐 信義店", venue.name)
            assertEquals(APP_ADDRESS, venue.address)
            assertNull(venue.lat)
            assertNull(venue.lng)
            assertEquals(venue, vm.uiState.value.venue)
            assertEquals(listOf(APP_FULL_ADDRESS), editor.lookup.queried)
            // A name with no coordinates is still a venue (#84): nothing to apologise for.
            assertNull(vm.uiState.value.shareNotice)
        }

    @Test
    fun anExistingVenueWithoutAddress_getsTheLinksAddress() = runTest(mainRule.testDispatcher.scheduler) {
        // Within 50 m of where the link's address is: the same store, used as it is.
        val nearby = dinTaiFung.copy(lat = mapsPosition.lat, lng = mapsPosition.lng)
        val near = appLinkEditor(listOf(nearby))
        // Not placed yet: it learns the position and the address that goes with it.
        val unplaced = appLinkEditor(listOf(dinTaiFung.copy(lat = null, lng = null)))
        // An address the user typed is never replaced.
        val typed = appLinkEditor(listOf(nearby.copy(address = "我打的地址")))
        val nearVm = near.viewModel()
        val unplacedVm = unplaced.viewModel()
        val typedVm = typed.viewModel()

        listOf(nearVm, unplacedVm, typedVm).forEach { assertTrue(it.pasteMapsLink()) }
        advanceUntilIdle()

        assertEquals(nearby.copy(address = APP_ADDRESS), near.venues.stored.single())
        assertEquals(nearby.copy(address = APP_ADDRESS), unplaced.venues.stored.single())
        assertEquals(nearby.copy(address = "我打的地址"), typed.venues.stored.single())
        assertEquals(near.venues.stored.single(), nearVm.uiState.value.venue)
        assertEquals(unplaced.venues.stored.single(), unplacedVm.uiState.value.venue)
        assertEquals(typed.venues.stored.single(), typedVm.uiState.value.venue)
        listOf(near, unplaced, typed).forEach { assertTrue(it.lookup.asked.isEmpty()) }
    }

    @Test
    fun sameStoreMove_writesTheLinksAddressWithThePin() = runTest(mainRule.testDispatcher.scheduler) {
        // About 53.5 km from where the link's address is.
        val far = dinTaiFung.copy(address = "舊地址")
        val editor = appLinkEditor(listOf(far))
        editor.venues.recordCounts[far.id] = 2
        val vm = editor.viewModel()
        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()
        val pending = requireNotNull(vm.uiState.value.pendingStore)
        assertEquals(mapsPosition, pending.to)

        vm.answerSameStoreMove()
        advanceUntilIdle()

        // The address belongs to its position: it moves with the pin, and is the link's own.
        val moved = editor.venues.stored.single()
        assertEquals(far.copy(lat = mapsPosition.lat, lng = mapsPosition.lng, address = APP_ADDRESS), moved)
        assertEquals(moved, vm.uiState.value.venue)
        assertTrue(editor.lookup.asked.isEmpty())
    }

    @Test
    fun anotherBranch_isCreatedWithTheLinksAddress() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = appLinkEditor(listOf(dinTaiFung))
        val vm = editor.viewModel()
        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)

        vm.answerAnotherBranch()
        advanceUntilIdle()

        val branch = editor.venues.stored.last()
        assertEquals(listOf(dinTaiFung.id, branch.id), editor.venues.stored.map { it.id })
        assertEquals("鼎泰豐 信義店", branch.name)
        assertEquals(APP_ADDRESS, branch.address)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(branch.lat), requireNotNull(branch.lng)))
        assertEquals(branch, vm.uiState.value.venue)
        assertEquals(dinTaiFung, editor.venues.stored.first())
    }

    @Test
    fun theSelectedVenue_showsItsFilledAddress() = runTest(mainRule.testDispatcher.scheduler) {
        // A web-made link carries no address: the reverse lookup fills it after the venue is chosen.
        val sharing = Editor()
        sharing.lookup.address = geocoderAddress
        val shared = sharing.viewModel(sharedText = SHARE_TEXT)
        // A venue picked by hand gets its address when the picker back-fills it.
        val picking = Editor(venues = listOf(daHuWu))
        picking.lookup.address = "仁愛路"
        val picked = picking.viewModel()
        advanceUntilIdle()
        picked.selectVenue(daHuWu)
        picked.onPickerShown()

        advanceUntilIdle()

        assertEquals(geocoderAddress, sharing.venues.stored.single().address)
        assertEquals(sharing.venues.stored.single(), shared.uiState.value.venue)
        assertEquals(daHuWu.copy(address = "仁愛路"), picked.uiState.value.venue)
    }

    @Test
    fun repositionFromAnAppLink_movesWithTheLinksAddress_andOffersItsName() =
        runTest(mainRule.testDispatcher.scheduler) {
            // No record at it, so it moves at once; its old address described its old position.
            val old = daHuWu.copy(address = "舊地址")
            val editor = appLinkEditor(listOf(old))
            val vm = editor.viewModel()

            assertTrue(vm.repositionFromClipboard(old))
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingMove)
            // The pin and the link's own address are written together; the name only fills the 店名 draft.
            assertEquals(
                old.copy(lat = mapsPosition.lat, lng = mapsPosition.lng, address = APP_ADDRESS),
                editor.venues.stored.single(),
            )
            assertEquals(EatTimeEditorUiState.RepositionDraft(old.id, "鼎泰豐 信義店"), vm.uiState.value.repositionDraft)
            assertEquals(listOf(APP_FULL_ADDRESS), editor.lookup.queried)
            assertTrue(editor.lookup.asked.isEmpty())
            assertNull(vm.uiState.value.repositionNotice)
            // Editing a venue never picks it for this meal.
            assertNull(vm.uiState.value.venue)

            // The dialog took the name: it is not offered again.
            vm.consumeRepositionDraft()
            assertNull(vm.uiState.value.repositionDraft)
        }

    @Test
    fun repositionWithRecords_asksFirst_thenWritesTheAddress() = runTest(mainRule.testDispatcher.scheduler) {
        val old = daHuWu.copy(address = "舊地址")
        val editor = appLinkEditor(listOf(old))
        editor.venues.recordCounts[old.id] = 3
        val vm = editor.viewModel()

        assertTrue(vm.repositionFromClipboard(old))
        advanceUntilIdle()

        assertEquals(
            EatTimeEditorUiState.PendingMove(old, to = mapsPosition, movedRecords = 3, address = APP_ADDRESS),
            vm.uiState.value.pendingMove,
        )
        // Nothing is written until the user agrees; the name is offered all the same.
        assertEquals(listOf(old), editor.venues.stored)
        assertEquals(EatTimeEditorUiState.RepositionDraft(old.id, "鼎泰豐 信義店"), vm.uiState.value.repositionDraft)

        vm.confirmMove()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMove)
        assertEquals(
            old.copy(lat = mapsPosition.lat, lng = mapsPosition.lng, address = APP_ADDRESS),
            editor.venues.stored.single(),
        )
        assertTrue(editor.lookup.asked.isEmpty())
    }

    @Test
    fun repositionWhenUnlocatable_offersNameAndAddress_movesNothing() = runTest(mainRule.testDispatcher.scheduler) {
        val old = daHuWu.copy(address = "舊地址")
        val editor = appLinkEditor(listOf(old), locatable = false)
        editor.venues.recordCounts[old.id] = 3
        val vm = editor.viewModel()

        assertTrue(vm.repositionFromClipboard(old))
        advanceUntilIdle()

        // Nowhere to move it to: nothing is asked or written, and the dialog says why.
        assertNull(vm.uiState.value.pendingMove)
        assertEquals(listOf(old), editor.venues.stored)
        assertEquals(
            EatTimeEditorUiState.RepositionDraft(old.id, "鼎泰豐 信義店", APP_ADDRESS),
            vm.uiState.value.repositionDraft,
        )
        assertEquals(ShareNotice.PlaceNotLocated, vm.uiState.value.repositionNotice)
        assertFalse(vm.uiState.value.repositioning)
        assertEquals(listOf(APP_FULL_ADDRESS), editor.lookup.queried)

        // Closing the dialog drops what it was offered.
        vm.closeVenueEdit()
        assertNull(vm.uiState.value.repositionDraft)
        assertNull(vm.uiState.value.repositionNotice)
    }
}
