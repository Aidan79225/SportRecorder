package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class SaveEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
) {
    /** Returns false (without saving) if [EatRecord.time] is in the future. */
    suspend operator fun invoke(
        record: EatRecord,
        newPhotoFileNames: List<String>,
        removedPhotos: List<EatPhoto>,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (record.time > now) return false
        repository.save(record, newPhotoFileNames, removedPhotos)
        return true
    }
}
