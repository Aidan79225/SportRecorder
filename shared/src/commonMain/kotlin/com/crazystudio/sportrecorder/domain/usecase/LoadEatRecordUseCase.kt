package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository

class LoadEatRecordUseCase constructor(
    private val repository: EatRecordRepository,
) {
    suspend operator fun invoke(id: Int): EatRecord? = repository.findById(id)
}
