package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.Flow

interface FastingTypeRepository {
    /**
     * Recently-created custom types (capped), newest-first (index 0 = most recent).
     * Consumers wanting oldest-first display reverse the list themselves.
     */
    fun observeRecentCustomTypes(): Flow<List<CustomFastingType>>

    /** True if a custom window with these exact hours already exists. */
    suspend fun exists(window: FastingWindow): Boolean

    /** Persist a new custom window with an optional [name] (stamped with the current time). */
    suspend fun add(window: FastingWindow, name: String?)

    /** Restore: delete all custom types, then insert [types]. */
    suspend fun replaceAllCustom(types: List<CustomFastingType>)
}
