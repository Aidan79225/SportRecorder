package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The picker's edit dialog (#89): name and address, merge or keep both, delete, and moving a venue. */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelVenueEditTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val typo = Venue(id = 10, name = "大户屋", lat = null, lng = null, lastUsedAt = 1L)
    private val keeper = Venue(id = 11, name = "大戶屋", lat = null, lng = null, lastUsedAt = 2L)

    @Test
    fun savingANewName_withoutCollision_edits() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        val vm = editor.viewModel()
        vm.selectVenue(daHuWu)

        vm.saveVenue(daHuWu, "大戶屋　 信義店", "  信義路五段 7 號 ")
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        val edited = editor.venues.stored.single()
        assertEquals("大戶屋 信義店", edited.name)
        assertEquals("信義路五段 7 號", edited.address)
        // The selected venue follows its new name.
        assertEquals(edited, vm.uiState.value.venue)
    }

    @Test
    fun savingABlankName_isRefused() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        val vm = editor.viewModel()

        vm.saveVenue(daHuWu, "  　 ", "信義路")
        advanceUntilIdle()

        assertEquals(daHuWu, editor.venues.stored.single())
        assertNull(vm.uiState.value.pendingMerge)
    }

    @Test
    fun savingWithoutAChange_writesNothing() = runTest(mainRule.testDispatcher.scheduler) {
        val placed = daHuWu.copy(address = "信義路")
        val editor = Editor(venues = listOf(placed))
        val vm = editor.viewModel()

        vm.saveVenue(placed, "大戶屋", " 信義路 ")
        advanceUntilIdle()

        // Not even its recency: the row stays where it was in the picker.
        assertEquals(placed, editor.venues.stored.single())
    }

    @Test
    fun savingTheSameName_neverAsksToMerge() = runTest(mainRule.testDispatcher.scheduler) {
        val branch = daHuWu.copy(id = 2, lat = 24.0, lng = 120.0)
        val editor = Editor(venues = listOf(daHuWu, branch))
        val vm = editor.viewModel()

        // Only the address changes (and the name's case/spacing): another 大戶屋 existing is no merge.
        vm.saveVenue(daHuWu, " 大戶屋 ", "南京東路")
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        assertEquals(listOf("南京東路", null), editor.venues.stored.map { it.address })
        assertEquals(2, editor.venues.stored.size)
    }

    @Test
    fun savingOntoAnotherVenuesName_offersMergeOrKeep_withTheNearestAsTarget() =
        runTest(mainRule.testDispatcher.scheduler) {
            val from = Venue(id = 10, name = "麥當當", lat = 25.0330, lng = 121.5650, lastUsedAt = 5L)
            // Used more recently, but in another city.
            val far = Venue(id = 11, name = "麥當勞", lat = 22.6, lng = 120.3, lastUsedAt = 9L)
            val near = Venue(id = 12, name = "麥當勞", lat = 25.0335, lng = 121.5655, lastUsedAt = 1L)
                .copy(address = "信義路五段 7 號")
            val editor = Editor(venues = listOf(from, far, near))
            editor.venues.recordCounts[from.id] = 4
            val vm = editor.viewModel()

            vm.saveVenue(from, "麥當勞", "  基隆路 ")
            advanceUntilIdle()

            val pending = requireNotNull(vm.uiState.value.pendingMerge)
            assertEquals(from, pending.from)
            assertEquals(near, pending.into)
            // The dialog says how many records would change hands.
            assertEquals(4, pending.movedRecords)
            assertEquals("麥當勞", pending.newName)
            assertEquals("  基隆路 ", pending.newAddress)
            // Nothing is written until the user chooses.
            assertEquals(listOf(from, far, near), editor.venues.stored)
        }

    @Test
    fun savingOntoAnotherVenuesName_withoutPositions_targetsTheMostRecentlyUsed() =
        runTest(mainRule.testDispatcher.scheduler) {
            val older = keeper.copy(id = 12, lastUsedAt = 0L)
            val editor = Editor(venues = listOf(typo, keeper, older))
            val vm = editor.viewModel()

            vm.saveVenue(typo, "大戶屋", null)
            advanceUntilIdle()

            assertEquals(keeper, vm.uiState.value.pendingMerge?.into)
        }

    @Test
    fun keepBoth_leavesTwoSameNameVenues() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(typo, keeper))
        editor.venues.recordCounts[typo.id] = 3
        val vm = editor.viewModel()
        vm.selectVenue(typo)
        vm.saveVenue(typo, "大戶屋", "敦化南路")
        advanceUntilIdle()

        vm.keepBothVenues()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        assertEquals(listOf("大戶屋", "大戶屋"), editor.venues.stored.map { it.name })
        assertEquals(listOf("敦化南路", null), editor.venues.stored.map { it.address })
        // Its records stay with it.
        assertEquals(3, editor.venues.recordCount(typo.id))
        assertEquals(typo.id, vm.uiState.value.venue?.id)
        assertEquals("大戶屋", vm.uiState.value.venue?.name)
    }

    @Test
    fun cancellingAMerge_leavesEverythingWhereItWas() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(typo, keeper))
        val vm = editor.viewModel()
        vm.saveVenue(typo, "大戶屋", null)
        advanceUntilIdle()

        vm.cancelMerge()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        assertEquals(listOf(typo, keeper), editor.venues.stored)
    }

    @Test
    fun merge_movesTheRecords_andTheSelectionFollows() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(typo, keeper))
        editor.venues.recordCounts[typo.id] = 3
        val vm = editor.viewModel()
        vm.selectVenue(typo)
        vm.saveVenue(typo, "大戶屋", "敦化南路")
        advanceUntilIdle()

        // A double tap: the second must find nothing pending instead of merging a venue that is gone.
        vm.mergeVenues()
        vm.mergeVenues()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMerge)
        assertEquals(listOf(keeper.id), editor.venues.stored.map { it.id })
        assertEquals(3, editor.venues.recordCount(keeper.id))
        // The survivor keeps its own address; the selection is now the survivor.
        assertNull(editor.venues.stored.single().address)
        assertEquals(keeper.id, vm.uiState.value.venue?.id)
    }

    @Test
    fun deletingAVenue_asksWithTheCount() = runTest(mainRule.testDispatcher.scheduler) {
        val other = keeper
        val editor = Editor(venues = listOf(daHuWu, other))
        editor.venues.recordCounts[daHuWu.id] = 12
        val vm = editor.viewModel()
        vm.selectVenue(other)

        vm.requestDelete(daHuWu)
        advanceUntilIdle()

        assertEquals(EatTimeEditorUiState.PendingDelete(daHuWu, records = 12), vm.uiState.value.pendingDelete)
        // Nothing is deleted until the user confirms.
        assertEquals(2, editor.venues.stored.size)
        vm.cancelDelete()
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(2, editor.venues.stored.size)

        vm.requestDelete(daHuWu)
        advanceUntilIdle()
        vm.confirmDelete()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(listOf(other), editor.venues.stored)
        // Another venue was selected: it stays.
        assertEquals(other, vm.uiState.value.venue)
    }

    @Test
    fun deletingTheSelectedVenue_clearsIt() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        assertEquals(daHuWu, vm.uiState.value.venue)

        vm.requestDelete(daHuWu)
        advanceUntilIdle()
        vm.confirmDelete()
        advanceUntilIdle()

        assertTrue(editor.venues.stored.isEmpty())
        assertNull(vm.uiState.value.venue)
        assertTrue(vm.save())
        // The meal itself stays; it just has no venue any more.
        val saved = editor.records.stored.single()
        assertNull(saved.venue)
        assertEquals("午餐", saved.note)
    }

    @Test
    fun repositionFromALink_withRecords_asksFirst() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 5
        editor.clipboard.text = SHARE_TEXT
        editor.lookup.address = "信義路二段 194 號"
        val vm = editor.viewModel()

        assertTrue(vm.repositionFromClipboard(dinTaiFung))
        advanceUntilIdle()

        assertEquals(
            EatTimeEditorUiState.PendingMove(dinTaiFung, to = mapsPosition, movedRecords = 5),
            vm.uiState.value.pendingMove,
        )
        assertEquals(listOf(dinTaiFung), editor.venues.stored)

        vm.confirmMove()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMove)
        val moved = editor.venues.stored.single()
        assertEquals(mapsPosition, GeoPoint(requireNotNull(moved.lat), requireNotNull(moved.lng)))
        // The new position gets its own address.
        assertEquals("信義路二段 194 號", moved.address)
    }

    @Test
    fun repositionFromALink_keepingThePosition_movesNothing() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 5
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()
        vm.repositionFromClipboard(dinTaiFung)
        advanceUntilIdle()

        vm.cancelMove()
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingMove)
        assertEquals(listOf(dinTaiFung), editor.venues.stored)
    }

    @Test
    fun repositionFromALink_isSaidToBeReadingWhileTheLinkResolves() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        val slowNetwork = CompletableDeferred<Unit>()
        editor.resolver.gate = slowNetwork
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()

        assertTrue(vm.repositionFromClipboard(dinTaiFung))
        advanceUntilIdle()

        assertEquals(1, editor.resolver.calls)
        assertTrue(vm.uiState.value.repositioning)
        // The edit dialog says so; the editor's own venue row does not.
        assertFalse(vm.uiState.value.resolvingLink)

        slowNetwork.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.repositioning)
        val moved = editor.venues.stored.single()
        assertEquals(mapsPosition, GeoPoint(requireNotNull(moved.lat), requireNotNull(moved.lng)))
    }

    @Test
    fun closingTheEditDialog_dropsALinkStillResolving() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        val slowNetwork = CompletableDeferred<Unit>()
        editor.resolver.gate = slowNetwork
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()
        vm.repositionFromClipboard(dinTaiFung)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.repositioning)

        vm.closeVenueEdit()
        slowNetwork.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.repositioning)
        assertNull(vm.uiState.value.pendingMove)
        assertNull(vm.uiState.value.repositionNotice)
        // The link came back after the dialog was gone: the venue stays where it was.
        assertEquals(listOf(dinTaiFung), editor.venues.stored)
    }

    @Test
    fun closingTheEditDialog_withdrawsItsMoveQuestionAndPasteDialog() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.venues.recordCounts[dinTaiFung.id] = 5
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()
        vm.repositionFromClipboard(dinTaiFung)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingMove)

        vm.closeVenueEdit()

        assertNull(vm.uiState.value.pendingMove)
        assertEquals(listOf(dinTaiFung), editor.venues.stored)

        editor.clipboard.text = "午餐"
        assertFalse(vm.repositionFromClipboard(dinTaiFung))
        vm.closeVenueEdit()

        assertNull(vm.uiState.value.pasteDialog)
    }

    @Test
    fun repositionFromALink_takesOnlyThePin_notTheName() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu.copy(address = "舊地址")))
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()

        assertTrue(vm.repositionFromClipboard(daHuWu))
        advanceUntilIdle()

        // No records at it: moved at once, without asking.
        assertNull(vm.uiState.value.pendingMove)
        val moved = editor.venues.stored.single()
        assertEquals("大戶屋", moved.name)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(moved.lat), requireNotNull(moved.lng)))
        assertNull(moved.address)
        // Editing a venue never picks it for this meal.
        assertNull(vm.uiState.value.venue)
    }

    @Test
    fun repositionWithoutALinkOnTheClipboard_asksForOne() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        editor.clipboard.text = "午餐"
        val vm = editor.viewModel()

        assertFalse(vm.repositionFromClipboard(daHuWu))
        assertEquals(PasteTarget.Reposition(daHuWu), vm.uiState.value.pasteDialog)
        assertEquals(0, editor.resolver.calls)

        vm.pasteText(ICHIRAN_URL)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pasteDialog)
        val moved = editor.venues.stored.single()
        assertEquals("大戶屋", moved.name)
        assertEquals(GeoPoint(25.0418, 121.5503), GeoPoint(requireNotNull(moved.lat), requireNotNull(moved.lng)))
        assertNull(vm.uiState.value.venue)
    }

    @Test
    fun repositionFromALinkWithoutAPin_saysSoInTheDialog() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        editor.resolver.location = "https://www.google.com/maps/place/Ichiran"
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()
        vm.selectVenue(daHuWu)

        vm.repositionFromClipboard(daHuWu)
        advanceUntilIdle()

        assertEquals(ShareNotice.MapLinkUnreadable, vm.uiState.value.repositionNotice)
        // The editor's own notice line and the meal's venue are left alone.
        assertNull(vm.uiState.value.shareNotice)
        assertEquals(daHuWu, vm.uiState.value.venue)
        assertEquals(listOf(daHuWu), editor.venues.stored)

        vm.closeVenueEdit()
        assertNull(vm.uiState.value.repositionNotice)
    }

    @Test
    fun answeringAMove_leavesTheSharesQuestionOpen() = runTest(mainRule.testDispatcher.scheduler) {
        val other = daHuWu.copy(id = 2)
        val editor = Editor(venues = listOf(dinTaiFung, other))
        editor.venues.recordCounts[other.id] = 1
        editor.clipboard.text = SHARE_TEXT
        val handle = SavedStateHandle(mapOf("sharedText" to SHARE_TEXT))
        val vm = editor.viewModel(handle = handle)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)
        vm.repositionFromClipboard(other)
        advanceUntilIdle()
        vm.cancelMove()

        // The process dies; the share re-runs from its route, and its own question was never answered.
        val again = editor.viewModel(handle = handle)
        advanceUntilIdle()

        assertEquals(dinTaiFung, again.uiState.value.pendingStore?.venue)
    }

    @Test
    fun openingThePicker_backfillsMissingAddresses() = runTest(mainRule.testDispatcher.scheduler) {
        val unplaced = keeper
        val known = Venue(id = 3, name = "一蘭", lat = 25.0418, lng = 121.5503, lastUsedAt = 3L, address = "松壽路")
        val editor = Editor(venues = listOf(daHuWu, unplaced, known))
        editor.lookup.address = "仁愛路"
        val vm = editor.viewModel()
        advanceUntilIdle()
        assertTrue(editor.lookup.asked.isEmpty())

        vm.onPickerShown()
        advanceUntilIdle()

        // Only the venue that has a position and no address is looked up.
        assertEquals(listOf(GeoPoint(25.0, 121.0)), editor.lookup.asked)
        assertEquals(listOf("仁愛路", null, "松壽路"), editor.venues.stored.map { it.address })
    }
}
