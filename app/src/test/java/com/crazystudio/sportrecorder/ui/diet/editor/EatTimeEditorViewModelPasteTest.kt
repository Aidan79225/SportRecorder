package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import com.crazystudio.sportrecorder.domain.model.GeoPoint
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

/** Pasting a Google Maps link into the venue picker (#89): the share's path, as its own run. */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelPasteTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun pastingAMapsLink_runsTheSharePath_andSelectsTheVenue() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()
        advanceUntilIdle()

        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()

        val venue = requireNotNull(vm.uiState.value.venue)
        assertEquals("鼎泰豐 信義店", venue.name)
        assertEquals(mapsPosition, GeoPoint(requireNotNull(venue.lat), requireNotNull(venue.lng)))
        assertEquals(listOf(venue), editor.venues.stored)
        assertEquals(1, editor.resolver.calls)
        assertFalse(vm.uiState.value.resolvingLink)
        assertNull(vm.uiState.value.pasteDialog)
    }

    @Test
    fun pastingWithoutALink_opensThePasteDialog_andMakesNoRequest() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(daHuWu))
        editor.clipboard.text = "午餐好吃 https://evil.example/maps/place/x"
        val vm = editor.viewModel()
        vm.selectVenue(daHuWu)
        advanceUntilIdle()

        assertFalse(vm.pasteMapsLink())
        advanceUntilIdle()

        assertEquals(PasteTarget.Select, vm.uiState.value.pasteDialog)
        assertEquals(0, editor.resolver.calls)
        assertNull(vm.uiState.value.shareNotice)
        assertFalse(vm.uiState.value.resolvingLink)
        // Nothing was pasted yet: the venue stays until the dialog gives a link.
        assertEquals(daHuWu, vm.uiState.value.venue)

        // An empty clipboard asks the same way.
        vm.dismissPasteDialog()
        editor.clipboard.text = null
        assertFalse(vm.pasteMapsLink())
        assertEquals(PasteTarget.Select, vm.uiState.value.pasteDialog)
        vm.dismissPasteDialog()
        assertNull(vm.uiState.value.pasteDialog)
        assertEquals(0, editor.resolver.calls)
    }

    @Test
    fun pasteText_fromTheDialog_runsTheSamePath() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        assertFalse(vm.pasteMapsLink())

        vm.pasteText(SHARE_TEXT)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pasteDialog)
        assertEquals("鼎泰豐 信義店", vm.uiState.value.venue?.name)
        assertEquals(1, editor.venues.stored.size)
        assertEquals(1, editor.resolver.calls)

        // Text without a Maps link closes the dialog and says so, without a request.
        assertFalse(vm.pasteMapsLink())
        vm.pasteText("hello")
        advanceUntilIdle()

        assertNull(vm.uiState.value.pasteDialog)
        assertEquals(ShareNotice.NotAMapLink, vm.uiState.value.shareNotice)
        assertNull(vm.uiState.value.venue)
        assertEquals(1, editor.resolver.calls)
    }

    @Test
    fun pasteInEditMode_changesTheVenueButNotTheLocation() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = daHuWu)), listOf(daHuWu))
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()

        assertEquals("鼎泰豐 信義店", vm.uiState.value.venue?.name)
        assertEquals(EatTimeEditorUiState.LatLng(home.lat, home.lng), vm.uiState.value.location)
        assertTrue(vm.save())
        val saved = editor.records.stored.single()
        // Two facts: the venue is where Maps says; the meal is still where you were.
        assertEquals(home, saved.location)
        assertEquals("鼎泰豐 信義店", saved.venue?.name)
    }

    @Test
    fun aSecondPaste_cancelsTheFirst() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val slowNetwork = CompletableDeferred<Unit>()
        editor.resolver.gate = slowNetwork
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel()
        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()
        assertEquals(1, editor.resolver.calls)
        assertTrue(vm.uiState.value.resolvingLink)

        editor.clipboard.text = ICHIRAN_URL
        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()
        slowNetwork.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Ichiran"), editor.venues.stored.map { it.name })
        assertEquals("Ichiran", vm.uiState.value.venue?.name)
        assertFalse(vm.uiState.value.resolvingLink)
    }

    @Test
    fun aPasteAfterAnAnsweredShare_asksAgain() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.clipboard.text = SHARE_TEXT
        val vm = editor.viewModel(sharedText = SHARE_TEXT)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)
        vm.answerSameStoreKeep()

        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()

        // The share's answer was about the share; the paste is its own question.
        val pending = requireNotNull(vm.uiState.value.pendingStore)
        assertEquals(dinTaiFung, pending.venue)
        assertEquals(listOf(dinTaiFung), editor.venues.stored)
    }

    @Test
    fun answeringAPaste_leavesTheSharesQuestionOpen() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(venues = listOf(dinTaiFung))
        editor.clipboard.text = SHARE_TEXT
        val handle = SavedStateHandle(mapOf("sharedText" to SHARE_TEXT))
        val vm = editor.viewModel(handle = handle)
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)
        assertTrue(vm.pasteMapsLink())
        advanceUntilIdle()
        requireNotNull(vm.uiState.value.pendingStore)
        vm.answerSameStoreKeep()

        // The process dies; the share re-runs from the route, and its question was never answered.
        val again = editor.viewModel(handle = handle)
        advanceUntilIdle()

        assertEquals(dinTaiFung, again.uiState.value.pendingStore?.venue)
    }
}
