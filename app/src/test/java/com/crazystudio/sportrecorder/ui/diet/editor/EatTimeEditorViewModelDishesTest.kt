package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.Dishes
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What a meal had: the editor row's add / remove / suggest / commit behaviour, and that an edit elsewhere keeps it. */
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

    private fun earlier(id: Int, time: Long, vararg dishes: String) =
        mealAt(venue = null).copy(id = id, time = time, dishes = dishes.toList())

    private fun newMeal(vararg earlierMeals: EatRecord): Pair<Editor, EatTimeEditorViewModel> {
        val editor = Editor(earlierMeals.toList())
        return editor to editor.viewModel().also { it.updateDate(year = 2024, month = 2, dayOfMonth = 5) }
    }

    @Test
    fun aTypedDish_isAddedOnDone_andTheFieldEmptied() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal()
        vm.setDishDraft("  滷肉飯 ")

        vm.commitDishDraft()

        assertEquals(listOf("滷肉飯"), vm.uiState.value.dishes)
        assertEquals("", vm.uiState.value.dishDraft)
    }

    @Test
    fun aRepeatedDish_isNotAddedTwice_butTheFieldStillEmpties() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal()
        vm.addDish("Soup")
        vm.setDishDraft("soup")

        vm.commitDishDraft()

        assertEquals(listOf("Soup"), vm.uiState.value.dishes)
        assertEquals("", vm.uiState.value.dishDraft)
    }

    @Test
    fun aBlankDraft_addsNothing() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal()
        vm.setDishDraft("   ")

        vm.commitDishDraft()

        assertEquals(emptyList<String>(), vm.uiState.value.dishes)
        assertEquals("", vm.uiState.value.dishDraft)
    }

    @Test
    fun aMealHoldsAtMostTenDishes() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal()
        (1..12).forEach { vm.addDish("dish $it") }

        assertEquals((1..10).map { "dish $it" }, vm.uiState.value.dishes)
    }

    @Test
    fun removingADish_takesOnlyThatOne() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal()
        listOf("A", "B", "C").forEach(vm::addDish)

        vm.removeDish("B")

        assertEquals(listOf("A", "C"), vm.uiState.value.dishes)
    }

    @Test
    fun textStillTypedOnSave_becomesADish() = runTest(mainRule.testDispatcher.scheduler) {
        val (editor, vm) = newMeal()
        vm.addDish("Rice")
        vm.setDishDraft("Tea")

        assertTrue(vm.save())

        assertEquals(listOf("Rice", "Tea"), editor.records.stored.single().dishes)
    }

    @Test
    fun savingWithoutTouchingTheRow_storesNoDishes() = runTest(mainRule.testDispatcher.scheduler) {
        val (editor, vm) = newMeal()

        assertTrue(vm.save())

        assertEquals(emptyList<String>(), editor.records.stored.single().dishes)
    }

    @Test
    fun suggestions_comeFromEarlierMeals_mostUsedFirst() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal(
            earlier(1, 1_000L, "Rice", "Tea"),
            earlier(2, 2_000L, "Tea"),
        )
        advanceUntilIdle()

        assertEquals(listOf("Tea", "Rice"), vm.uiState.value.dishSuggestions)
    }

    @Test
    fun suggestions_leaveOutChosenDishes_andFollowTheDraft() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal(earlier(1, 1_000L, "Rice", "Tea", "Rib"))
        advanceUntilIdle()

        vm.addDish("Tea")
        assertEquals(listOf("Rice", "Rib"), vm.uiState.value.dishSuggestions)

        vm.setDishDraft("ri")
        assertEquals(listOf("Rice", "Rib"), vm.uiState.value.dishSuggestions)
        vm.setDishDraft("rib")
        assertEquals(listOf("Rib"), vm.uiState.value.dishSuggestions)
    }

    @Test
    fun aSuggestionTapped_isAddedAndClearsTheDraft() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal(earlier(1, 1_000L, "Rice"))
        advanceUntilIdle()
        vm.setDishDraft("ri")

        vm.addDish("Rice")

        assertEquals(listOf("Rice"), vm.uiState.value.dishes)
        assertEquals("", vm.uiState.value.dishDraft)
        assertEquals(emptyList<String>(), vm.uiState.value.dishSuggestions)
    }

    @Test
    fun suggestions_areEmptyOnceTheMealHasTheMostDishes() = runTest(mainRule.testDispatcher.scheduler) {
        val (_, vm) = newMeal(earlier(1, 1_000L, "Extra"))
        advanceUntilIdle()
        assertEquals(listOf("Extra"), vm.uiState.value.dishSuggestions)

        repeat(Dishes.MAX_PER_MEAL) { vm.addDish("Dish $it") }

        assertEquals(emptyList<String>(), vm.uiState.value.dishSuggestions)
        vm.removeDish("Dish 0")
        assertEquals(listOf("Extra"), vm.uiState.value.dishSuggestions)
    }
}
