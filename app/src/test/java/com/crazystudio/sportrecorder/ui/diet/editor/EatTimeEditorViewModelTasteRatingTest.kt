package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * How the meal tasted: the editor starts with nothing chosen, so a quick capture asks for nothing extra,
 * and an edit keeps whatever the record already had until the user changes or clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelTasteRatingTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun aNewMeal_startsUnrated() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = Editor().viewModel()

        assertNull(vm.uiState.value.tasteRating)
    }

    @Test
    fun aChosenRating_isSavedWithTheMeal() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        vm.setTasteRating(4)

        assertEquals(4, vm.uiState.value.tasteRating)
        assertTrue(vm.save())
        assertEquals(4, editor.records.stored.single().tasteRating)
    }

    @Test
    fun savingWithoutTouchingTheRow_storesNoRating() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        assertTrue(vm.save())

        // Nothing was asked for, so nothing is stored: unrated, not a middle value.
        assertNull(editor.records.stored.single().tasteRating)
    }

    @Test
    fun choosingAnotherFace_changesTheRating() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        vm.setTasteRating(2)
        vm.setTasteRating(5)

        assertEquals(5, vm.uiState.value.tasteRating)
        assertTrue(vm.save())
        assertEquals(5, editor.records.stored.single().tasteRating)
    }

    @Test
    fun editingAMeal_loadsItsStoredRating() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null).copy(tasteRating = 5)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        assertEquals(5, vm.uiState.value.tasteRating)
    }

    @Test
    fun editingAnUnratedMeal_leavesItUnrated() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        assertNull(vm.uiState.value.tasteRating)
        assertTrue(vm.save())
        assertNull(editor.records.stored.single().tasteRating)
    }

    @Test
    fun anEditOfTheNoteAlone_keepsTheRating() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null).copy(tasteRating = 3)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        vm.setNote("晚餐")

        assertTrue(vm.save())

        assertEquals("晚餐", editor.records.stored.single().note)
        assertEquals(3, editor.records.stored.single().tasteRating)
    }

    @Test
    fun clearingTheRating_savesTheMealUnrated() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null).copy(tasteRating = 4)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        vm.setTasteRating(null)

        assertNull(vm.uiState.value.tasteRating)
        assertTrue(vm.save())
        assertNull(editor.records.stored.single().tasteRating)
    }

    // The editor offers five faces, so this cannot come from a tap; a bogus value must not wipe or
    // overwrite what the user chose.
    @Test
    fun aChoiceOffTheScale_isIgnored() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = Editor().viewModel()
        vm.setTasteRating(3)

        listOf(0, 6, -1, 99).forEach { value ->
            vm.setTasteRating(value)

            assertEquals("value $value", 3, vm.uiState.value.tasteRating)
        }
    }

    // A stored value off the scale (it cannot come through Room, which reads it as unrated, but a fake or a
    // future source might hand one over) must not reach the faces or be saved back.
    @Test
    fun aStoredRatingOffTheScale_isLoadedAsUnrated() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null).copy(tasteRating = 9)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        assertNull(vm.uiState.value.tasteRating)
        assertTrue(vm.save())
        assertNull(editor.records.stored.single().tasteRating)
    }
}
