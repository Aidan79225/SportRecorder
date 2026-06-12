package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.repository.FastingTypeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCustomFastingTypesUseCase @Inject constructor(
    private val repository: FastingTypeRepository,
) {
    operator fun invoke(): Flow<List<FastingWindow>> = repository.observeRecentCustomWindows()
}
