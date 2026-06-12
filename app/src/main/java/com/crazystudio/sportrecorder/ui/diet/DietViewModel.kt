package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.domain.diet.DietPhase
import com.crazystudio.sportrecorder.domain.diet.DietWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveDietStateUseCase
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

@HiltViewModel
class DietViewModel @Inject constructor(
    observeDietState: ObserveDietStateUseCase,
) : ViewModel() {

    /** Per-second ticker so elapsedText / progress recompute every second. */
    private val tickerFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TimeUnit.SECONDS.toMillis(1))
        }
    }

    val uiState: StateFlow<DietUiState> =
        combine(observeDietState(System.currentTimeMillis()), tickerFlow) { snapshot, now ->
            buildState(snapshot, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DietUiState())

    private fun buildState(snapshot: ObserveDietStateUseCase.DietSnapshot, now: Long): DietUiState {
        val eatingHours = snapshot.settings.eatingHours
        val fastingHours = snapshot.settings.fastingHours
        val fastingLabel = "%d : %d".format(fastingHours, eatingHours)
        val selectedFastingItem = FastingItem.defaultFastingItems.firstOrNull {
            it.fastingHours == fastingHours && it.eatingHours == eatingHours
        }

        val s = DietWindow.compute(
            eatTimesAsc = snapshot.eatTimesAsc,
            eatingHours = eatingHours,
            fastingHours = fastingHours,
            now = now,
        )

        val fastStartMs = s.windowEnd
        val fastEndMs = s.fastTargetAt
        val base = DietUiState(
            progress = s.ringProgress * 100f,
            fastingLabel = fastingLabel,
            selectedFastingItem = selectedFastingItem,
            elapsedText = formatElapsed(s.elapsedMillis),
            fastStart = hm(fastStartMs),
            fastEnd = hm(fastEndMs),
            fastEndsNextDay = fastStartMs != null && fastEndMs != null &&
                fastWindowCrossesDay(fastStartMs, fastEndMs),
        )

        return when (s.phase) {
            DietPhase.IDLE -> base.copy(
                elapsedText = formatElapsed(0L),
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_no_record,
            )
            DietPhase.EATING -> base.copy(
                statusIcon = R.drawable.ic_baseline_fastfood_24,
                statusTextRes = R.string.diet_status_eating,
                promptTextRes = R.string.diet_remaining_time,
            )
            DietPhase.FASTING -> base.copy(
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_fasting,
                promptTextRes = R.string.diet_fasting_time,
            )
            DietPhase.SUCCESS -> base.copy(
                statusIcon = R.drawable.ic_baseline_no_food_24,
                statusTextRes = R.string.diet_status_success,
                promptTextRes = R.string.diet_fasting_time,
            )
        }
    }

    private fun hm(millis: Long?): String =
        if (millis == null) "" else HM_FORMAT.format(Date(millis))

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
    }
}
