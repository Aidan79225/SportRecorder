package com.crazystudio.sportrecorder.ui.diet.create.fasting

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.CreateCustomFastingTypeUseCase
import com.crazystudio.sportrecorder.fake.FakeFastingTypeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateFastingTypeViewModelTest {

    private fun viewModel(repo: FakeFastingTypeRepository) =
        CreateFastingTypeViewModel(CreateCustomFastingTypeUseCase(repo))

    @Test
    fun newWindow_isCreated() = runTest {
        val repo = FakeFastingTypeRepository()
        val vm = viewModel(repo)

        val ok = vm.createCustomFastingType(fastingHours = 18, eatingHours = 6)

        assertTrue(ok)
        assertEquals(listOf(FastingWindow(18, 6)), repo.added)
    }

    @Test
    fun builtInDefault_isRejected() = runTest {
        val repo = FakeFastingTypeRepository()
        val vm = viewModel(repo)

        val ok = vm.createCustomFastingType(fastingHours = 16, eatingHours = 8)

        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }

    @Test
    fun existingCustom_isRejected() = runTest {
        val repo = FakeFastingTypeRepository(existing = setOf(FastingWindow(18, 6)))
        val vm = viewModel(repo)

        val ok = vm.createCustomFastingType(fastingHours = 18, eatingHours = 6)

        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }
}
