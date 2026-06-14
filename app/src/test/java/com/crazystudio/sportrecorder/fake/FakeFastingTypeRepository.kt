package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake. [observeRecentCustomWindows] surfaces the backing list (newest-first
 * is the caller's responsibility). [exists] is seeded; [add] is recorded.
 */
class FakeFastingTypeRepository(
    initial: List<FastingWindow> = emptyList(),
    private val existing: Set<FastingWindow> = emptySet(),
) : FastingTypeRepository {
    private val state = MutableStateFlow(initial)
    val added = mutableListOf<FastingWindow>()

    fun setWindows(windows: List<FastingWindow>) {
        state.value = windows
    }

    override fun observeRecentCustomWindows(): Flow<List<FastingWindow>> = state

    override suspend fun exists(window: FastingWindow): Boolean =
        window in existing || window in added

    override suspend fun add(window: FastingWindow) {
        added.add(window)
    }
}
