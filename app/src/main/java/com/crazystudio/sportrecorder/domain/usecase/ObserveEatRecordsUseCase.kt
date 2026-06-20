package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow

class ObserveEatRecordsUseCase constructor(
    private val repository: EatRecordRepository,
) {
    operator fun invoke(): Flow<List<EatRecord>> = repository.observeAll()
}
