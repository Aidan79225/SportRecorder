package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.diet.DietPhase
import com.crazystudio.sportrecorder.domain.diet.DietWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.shared.resources.Res
import com.crazystudio.sportrecorder.shared.resources.diet_fasting_time
import com.crazystudio.sportrecorder.shared.resources.diet_no_record
import com.crazystudio.sportrecorder.shared.resources.diet_remaining_time
import com.crazystudio.sportrecorder.shared.resources.diet_status_eating
import com.crazystudio.sportrecorder.shared.resources.diet_status_fasting
import com.crazystudio.sportrecorder.shared.resources.diet_status_idle
import com.crazystudio.sportrecorder.shared.resources.diet_status_success
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_fastfood_24
import com.crazystudio.sportrecorder.shared.resources.ic_baseline_no_food_24
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class DietViewModel(
    observeDietState: ObserveDietStateUseCase,
    private val now: () -> Long,
) : ViewModel() {

    constructor(observeDietState: ObserveDietStateUseCase) :
        this(observeDietState, { Clock.System.now().toEpochMilliseconds() })

    /** Per-second ticker so elapsedText / progress recompute every second. */
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(MILLIS_PER_SECOND)
        }
    }

    val uiState: StateFlow<DietUiState> =
        combine(observeDietState(now()), tickerFlow) { snapshot, now ->
            buildState(snapshot, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DietUiState())

    private fun buildState(snapshot: ObserveDietStateUseCase.DietSnapshot, now: Long): DietUiState {
        val eatingHours = snapshot.settings.eatingHours
        val fastingHours = snapshot.settings.fastingHours
        val fastingLabel = "$fastingHours : $eatingHours"

        val s = DietWindow.compute(
            eatTimesAsc = snapshot.eatTimesAsc,
            eatingHours = eatingHours,
            fastingHours = fastingHours,
            now = now,
        )

        val base = DietUiState(
            progress = s.ringProgress * 100f,
            fastingLabel = fastingLabel,
            elapsedText = formatElapsed(s.elapsedMillis),
            fastStart = timeLabel(s.windowEnd, now),
            fastEnd = timeLabel(s.fastTargetAt, now),
        )

        return when (s.phase) {
            DietPhase.IDLE -> base.copy(
                elapsedText = formatElapsed(0L),
                statusIcon = Res.drawable.ic_baseline_no_food_24,
                statusText = Res.string.diet_status_idle,
                promptText = Res.string.diet_no_record,
            )
            DietPhase.EATING -> base.copy(
                statusIcon = Res.drawable.ic_baseline_fastfood_24,
                statusText = Res.string.diet_status_eating,
                promptText = Res.string.diet_remaining_time,
            )
            DietPhase.FASTING -> base.copy(
                statusIcon = Res.drawable.ic_baseline_no_food_24,
                statusText = Res.string.diet_status_fasting,
                promptText = Res.string.diet_fasting_time,
                // Show when the fast actually started (last meal, or +1h for a single-meal window).
                fastStart = timeLabel(s.fastStartAt, now),
            )
            DietPhase.SUCCESS -> base.copy(
                statusIcon = Res.drawable.ic_baseline_no_food_24,
                statusText = Res.string.diet_status_success,
                promptText = Res.string.diet_fasting_time,
                fastStart = timeLabel(s.fastStartAt, now),
            )
        }
    }

    private fun timeLabel(millis: Long?, now: Long): FastTimeLabel? {
        if (millis == null) return null
        val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return FastTimeLabel(
            time = "${pad2(dt.hour.toLong())}:${pad2(dt.minute.toLong())}", // "HH:mm"
            day = relativeDay(now, millis),
            date = "${dt.month.ordinal + 1}/${dt.day}", // "M/d", non-padded (shown only when day == OTHER)
        )
    }

    private fun formatElapsed(timestamp: Long): String {
        val totalSeconds = timestamp / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val mins = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val secs = totalSeconds % SECONDS_PER_MINUTE
        // Matches the legacy "%02d:%02d:%02d" formatting.
        return "${pad2(hours)}:${pad2(mins)}:${pad2(secs)}"
    }

    private fun pad2(n: Long): String = n.toString().padStart(2, '0')

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3600L
    }
}
