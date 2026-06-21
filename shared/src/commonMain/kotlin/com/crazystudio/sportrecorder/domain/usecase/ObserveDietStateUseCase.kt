package com.crazystudio.sportrecorder.domain.usecase

import com.crazystudio.sportrecorder.domain.model.DietSettings
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.repository.EatRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class ObserveDietStateUseCase constructor(
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

    /** Local midnight [dayOffset] days from [now], as epoch millis (kotlinx-datetime; no Calendar). */
    private fun dayStart(now: Long, dayOffset: Int): Long {
        val date = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
            .plus(dayOffset, DateTimeUnit.DAY)
        return date.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** [WINDOW_START_HOUR]:00 local, [RECENT_WINDOW_DAYS] days before [now], as epoch millis. */
    private fun windowStart(now: Long): Long {
        val date = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
            .minus(RECENT_WINDOW_DAYS, DateTimeUnit.DAY)
        return LocalDateTime(date, LocalTime(WINDOW_START_HOUR, 0)).toInstant(zone).toEpochMilliseconds()
    }

    private val zone get() = TimeZone.currentSystemDefault()

    private companion object {
        const val DAY_OFFSET_TOMORROW = 1
        const val RECENT_WINDOW_DAYS = 8
        const val WINDOW_START_HOUR = 16
    }
}
