package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.reminder.RemindersRescheduler

/** Records how many times [reschedule] was invoked. */
class FakeRemindersRescheduler : RemindersRescheduler {
    var rescheduleCount = 0
        private set

    override suspend fun reschedule() {
        rescheduleCount++
    }
}
