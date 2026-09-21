package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake. [exists] is seeded via [existing]; [add] is recorded in [added] / [addedNames]
 * **and** prepended to the backing list, so [observeRecentCustomTypes] is newest-first like
 * `FastingTypeDao.flowLast` (`ORDER BY timestamp DESC`).
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
        state.value = listOf(
            CustomFastingType(window.fastingHours, window.eatingHours, name),
        ) + state.value
    }

    override suspend fun replaceAllCustom(types: List<CustomFastingType>) {
        state.value = types
    }
}
