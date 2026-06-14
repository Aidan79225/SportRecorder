package com.crazystudio.sportrecorder.ui.diet.select

import app.cash.turbine.test
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveCustomFastingTypesUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import com.crazystudio.sportrecorder.fake.FakeDietSettingsRepository
import com.crazystudio.sportrecorder.fake.FakeFastingTypeRepository
import com.crazystudio.sportrecorder.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectFastingTypeViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun viewModel(
        typeRepo: FakeFastingTypeRepository,
        settingsRepo: FakeDietSettingsRepository,
    ) = SelectFastingTypeViewModel(
        observeCustomFastingTypes = ObserveCustomFastingTypesUseCase(typeRepo),
        saveFastingSelection = SaveFastingSelectionUseCase(settingsRepo),
    )

    @Test
    fun fastingItemFlow_emptyCustom_isDefaultsOnly() = runTest(mainRule.testDispatcher.scheduler) {
        val vm = viewModel(FakeFastingTypeRepository(), FakeDietSettingsRepository())

        assertEquals(FastingItem.defaultFastingItems, vm.fastingItemFlow.first())
    }

    @Test
    fun fastingItemFlow_customWindows_appendedReversed() = runTest(mainRule.testDispatcher.scheduler) {
        // Repository order is newest-first [20/4, 18/6]; display tail is reversed [18/6, 20/4].
        val typeRepo = FakeFastingTypeRepository(
            initial = listOf(FastingWindow(20, 4), FastingWindow(18, 6)),
        )
        val vm = viewModel(typeRepo, FakeDietSettingsRepository())

        val expected = FastingItem.defaultFastingItems + listOf(
            FastingItem.CustomFastingItem(18, 6),
            FastingItem.CustomFastingItem(20, 4),
        )
        assertEquals(expected, vm.fastingItemFlow.first())
    }

    @Test
    fun saveSelection_persistsToSettings() = runTest(mainRule.testDispatcher.scheduler) {
        val settingsRepo = FakeDietSettingsRepository()
        val vm = viewModel(FakeFastingTypeRepository(), settingsRepo)

        vm.saveSelection(fastingHours = 20, eatingHours = 4)
        advanceUntilIdle()

        settingsRepo.settings.test {
            assertEquals(
                com.crazystudio.sportrecorder.domain.model.DietSettings(fastingHours = 20, eatingHours = 4),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
