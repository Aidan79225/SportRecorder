package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * How long a meal took: the editor starts at an hour, so a quick capture costs nothing extra, and an
 * edit keeps whatever the record already had until the user picks another length.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelDurationTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun aNewMeal_startsAtOneHour() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = Editor().viewModel()

        assertEquals(60, vm.uiState.value.durationMinutes)
    }

    @Test
    fun aChosenDuration_isSavedWithTheMeal() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        vm.setDuration(90)

        assertEquals(90, vm.uiState.value.durationMinutes)
        assertTrue(vm.save())
        assertEquals(90, editor.records.stored.single().durationMinutes)
    }

    @Test
    fun editingAMeal_loadsItsStoredDuration() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null).copy(durationMinutes = 90)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()

        assertEquals(90, vm.uiState.value.durationMinutes)
    }

    @Test
    fun savingWithoutTouchingTheRow_storesOneHour() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor()
        val vm = editor.viewModel()
        vm.updateDate(year = 2024, month = 2, dayOfMonth = 5)

        assertTrue(vm.save())

        assertEquals(60, editor.records.stored.single().durationMinutes)
    }

    @Test
    fun anOddStoredDuration_isKeptUntilTheUserChoosesAnother() = runTest(mainRule.testDispatcher.scheduler) {
        // 75 is not on the menu (a backup from elsewhere, say): a save that never opens it keeps it.
        val editor = Editor(listOf(mealAt(venue = null).copy(durationMinutes = 75)))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        vm.setNote("晚餐")

        assertTrue(vm.save())
        assertEquals(75, editor.records.stored.single().durationMinutes)

        vm.setDuration(30)
        assertTrue(vm.save())
        assertEquals(30, editor.records.stored.single().durationMinutes)
    }
}
