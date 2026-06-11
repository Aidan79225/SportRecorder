package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class LoadEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    suspend operator fun invoke(id: Int): EatRecord? = repository.findById(id)
}
