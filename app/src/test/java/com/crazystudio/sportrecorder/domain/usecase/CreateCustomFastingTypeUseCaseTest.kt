package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeFastingTypeRepository(
    private val existing: Set<FastingWindow> = emptySet(),
) : FastingTypeRepository {
    val added = mutableListOf<FastingWindow>()
    override fun observeRecentCustomWindows(): Flow<List<FastingWindow>> = TODO()
    override suspend fun exists(window: FastingWindow): Boolean = window in existing
    override suspend fun add(window: FastingWindow) {
        added.add(window)
    }
}

class CreateCustomFastingTypeUseCaseTest {
    @Test fun builtInDefault_isRejected() = runTest {
        val repo = FakeFastingTypeRepository()
        val ok = CreateCustomFastingTypeUseCase(repo)(FastingWindow(16, 8))
        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }

    @Test fun existingCustom_isRejected() = runTest {
        val repo = FakeFastingTypeRepository(existing = setOf(FastingWindow(18, 6)))
        val ok = CreateCustomFastingTypeUseCase(repo)(FastingWindow(18, 6))
        assertFalse(ok)
        assertTrue(repo.added.isEmpty())
    }

    @Test fun newWindow_isAdded() = runTest {
        val repo = FakeFastingTypeRepository()
        val ok = CreateCustomFastingTypeUseCase(repo)(FastingWindow(18, 6))
        assertTrue(ok)
        assertEquals(listOf(FastingWindow(18, 6)), repo.added)
    }
}
