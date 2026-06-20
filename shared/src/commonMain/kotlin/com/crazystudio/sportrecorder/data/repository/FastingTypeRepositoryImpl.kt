package com.crazystudio.sportrecorder.data.repository

import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.data.mapper.toCustomType
import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.entity.FastingType
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val RECENT_CUSTOM_LIMIT = 10

class FastingTypeRepositoryImpl constructor(
    private val fastingTypeDao: FastingTypeDao,
) : FastingTypeRepository {

    override fun observeRecentCustomTypes(): Flow<List<CustomFastingType>> =
        fastingTypeDao.flowLast(RECENT_CUSTOM_LIMIT).map { list -> list.map { it.toCustomType() } }

    override suspend fun exists(window: FastingWindow): Boolean =
        fastingTypeDao.findByHours(window.fastingHours, window.eatingHours).isNotEmpty()

    override suspend fun add(window: FastingWindow, name: String?) {
        fastingTypeDao.insert(
            FastingType(
                fastingHours = window.fastingHours,
                eatingHours = window.eatingHours,
                name = name?.ifBlank { null },
                timestamp = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
}
