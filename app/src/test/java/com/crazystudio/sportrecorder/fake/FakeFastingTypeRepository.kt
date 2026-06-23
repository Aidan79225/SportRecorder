package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake. [observeRecentCustomTypes] surfaces the backing list (newest-first
 * is the caller's responsibility). [exists] is seeded; [add] is recorded.
 */
class FakeFastingTypeRepository(
    initial: List<CustomFastingType> = emptyList(),
    private val existing: Set<FastingWindow> = emptySet(),
) : FastingTypeRepository {
    private val state = MutableStateFlow(initial)
    val added = mutableListOf<FastingWindow>()
    val addedNames = mutableListOf<String?>()

    fun setTypes(types: List<CustomFastingType>) {
        state.value = types
    }

    override fun observeRecentCustomTypes(): Flow<List<CustomFastingType>> = state

    override suspend fun exists(window: FastingWindow): Boolean =
        window in existing || window in added

    override suspend fun add(window: FastingWindow, name: String?) {
        added.add(window)
        addedNames.add(name)
    }

    override suspend fun replaceAllCustom(types: List<CustomFastingType>) {
        state.value = types
    }
}
