package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class DeleteEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    suspend operator fun invoke(recordId: Int) = repository.delete(recordId)
}
