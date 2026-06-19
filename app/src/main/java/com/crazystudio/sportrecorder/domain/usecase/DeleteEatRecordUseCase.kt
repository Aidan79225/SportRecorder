package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import javax.inject.Inject

class DeleteEatRecordUseCase @Inject constructor(
    private val repository: EatRecordRepository,
    private val rescheduleReminders: RemindersRescheduler,
) {
    suspend operator fun invoke(recordId: Int) {
        repository.delete(recordId)
        rescheduleReminders.reschedule()
    }
}
