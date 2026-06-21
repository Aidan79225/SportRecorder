package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow

class ObserveCustomFastingTypesUseCase constructor(
    private val repository: FastingTypeRepository,
) {
    operator fun invoke(): Flow<List<CustomFastingType>> = repository.observeRecentCustomTypes()
}
