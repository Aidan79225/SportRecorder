package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import javax.inject.Inject

class ObserveDietStateUseCase @Inject constructor(
    private val eatRecordRepository: EatRecordRepository,
    private val dietSettingsRepository: DietSettingsRepository,
) {
    data class DietSnapshot(
        val eatTimesAsc: List<Long>,
        val settings: DietSettings,
    )

    /** [now] anchors the (fixed-at-subscription) window of recent eat times fed to DietWindow. */
    operator fun invoke(now: Long): Flow<DietSnapshot> {
        val before = dayStart(now, DAY_OFFSET_TOMORROW)
        val after = windowStart(now)
        return combine(
            eatRecordRepository.observeInWindow(after = after, before = before),
            dietSettingsRepository.settings,
        ) { records, settings ->
            DietSnapshot(eatTimesAsc = records.map { it.time }, settings = settings)
        }
    }

    private fun dayStart(now: Long, dayOffset: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) + dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun windowStart(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR) - RECENT_WINDOW_DAYS)
            set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val DAY_OFFSET_TOMORROW = 1
        const val RECENT_WINDOW_DAYS = 8
        const val WINDOW_START_HOUR = 16
    }
}
