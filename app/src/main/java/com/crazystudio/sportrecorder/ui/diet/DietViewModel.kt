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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DietViewModel(
    observeDietState: ObserveDietStateUseCase,
    private val now: () -> Long,
) : ViewModel() {

    constructor(observeDietState: ObserveDietStateUseCase) :
        this(observeDietState, System::currentTimeMillis)

    /** Per-second ticker so elapsedText / progress recompute every second. */
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(TimeUnit.SECONDS.toMillis(1))
        }
    }

    val uiState: StateFlow<DietUiState> =
        combine(observeDietState(now()), tickerFlow) { snapshot, now ->
            buildState(snapshot, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DietUiState())

    private fun buildState(snapshot: ObserveDietStateUseCase.DietSnapshot, now: Long): DietUiState {
        val eatingHours = snapshot.settings.eatingHours
        val fastingHours = snapshot.settings.fastingHours
        val fastingLabel = "%d : %d".format(fastingHours, eatingHours)

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
        val date = Date(millis)
        return FastTimeLabel(
            time = HM_FORMAT.format(date),
            day = relativeDay(now, millis),
            date = DATE_FORMAT.format(date),
        )
    }

    private fun formatElapsed(timestamp: Long): String {
        var temp = timestamp
        val hours = TimeUnit.MILLISECONDS.toHours(temp)
        temp -= TimeUnit.HOURS.toMillis(hours)
        val mins = TimeUnit.MILLISECONDS.toMinutes(temp)
        temp -= TimeUnit.MINUTES.toMillis(mins)
        val ses = TimeUnit.MILLISECONDS.toSeconds(temp)
        // Matches R.string.diet_time_format = "%02d:%02d:%02d"
        return "%02d:%02d:%02d".format(hours, mins, ses)
    }

    companion object {
        private val HM_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val DATE_FORMAT = SimpleDateFormat("M/d", Locale.getDefault())
    }
}
