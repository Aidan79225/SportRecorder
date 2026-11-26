package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What a meal had survives an edit made elsewhere in the editor (the dishes row itself comes later). */
@OptIn(ExperimentalCoroutinesApi::class)
class EatTimeEditorViewModelDishesTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun anEditOfTheNoteAlone_keepsTheDishes() = runTest(mainRule.testDispatcher.scheduler) {
        val editor = Editor(listOf(mealAt(venue = null).copy(dishes = listOf("滷肉飯", "Soup"))))
        val vm = editor.viewModel(eatTimeId = 1)
        advanceUntilIdle()
        assertEquals(listOf("滷肉飯", "Soup"), vm.uiState.value.dishes)
        vm.setNote("晚餐")

        assertTrue(vm.save())

        assertEquals(listOf("滷肉飯", "Soup"), editor.records.stored.single().dishes)
    }

    @Test
    fun aNewMeal_startsWithNoDishes() = runTest(mainRule.testDispatcher.scheduler) {
        assertEquals(emptyList<String>(), Editor().viewModel().uiState.value.dishes)
    }
}
