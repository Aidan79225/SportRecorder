package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository

class DeleteEatRecordUseCase constructor(
    private val repository: EatRecordRepository,
    private val rescheduleReminders: RemindersRescheduler,
) {
    suspend operator fun invoke(recordId: Int) {
        repository.delete(recordId)
        rescheduleReminders.reschedule()
    }
}
