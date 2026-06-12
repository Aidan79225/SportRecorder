package com.crazystudio.sportrecorder.data.repository

import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.data.mapper.toDomain
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import com.crazystudio.sportrecorder.entity.FastingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val RECENT_CUSTOM_LIMIT = 10

class FastingTypeRepositoryImpl @Inject constructor(
    private val fastingTypeDao: FastingTypeDao,
) : FastingTypeRepository {

    override fun observeRecentCustomWindows(): Flow<List<FastingWindow>> =
        fastingTypeDao.flowLast(RECENT_CUSTOM_LIMIT).map { list -> list.map { it.toDomain() } }

    override suspend fun exists(window: FastingWindow): Boolean =
        fastingTypeDao.findByHours(window.fastingHours, window.eatingHours).isNotEmpty()

    override suspend fun add(window: FastingWindow) {
        fastingTypeDao.insert(
            FastingType(
                fastingHours = window.fastingHours,
                eatingHours = window.eatingHours,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }
}
