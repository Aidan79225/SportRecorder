package com.crazystudio.sportrecorder.domain.repository

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import kotlinx.coroutines.flow.Flow

interface FastingTypeRepository {
    /**
     * Recently-created custom windows (capped), newest-first (index 0 = most recent).
     * Consumers wanting oldest-first display reverse the list themselves.
     */
    fun observeRecentCustomWindows(): Flow<List<FastingWindow>>

    /** True if a custom window with these exact hours already exists. */
    suspend fun exists(window: FastingWindow): Boolean

    /** Persist a new custom window (stamped with the current time). */
    suspend fun add(window: FastingWindow)
}
