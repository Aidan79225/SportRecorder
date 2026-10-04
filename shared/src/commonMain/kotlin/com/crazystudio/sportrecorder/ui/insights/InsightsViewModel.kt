package com.crazystudio.sportrecorder.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.data.PhotoImageSource
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.insights.InsightsRange
import com.crazystudio.sportrecorder.domain.insights.OnThisDay
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock

class InsightsViewModel(
    observeEatRecords: ObserveEatRecordsUseCase,
    dietSettingsRepository: DietSettingsRepository,
    private val photoImageSource: PhotoImageSource,
    private val now: () -> Long,
) : ViewModel() {

    constructor(
        observeEatRecords: ObserveEatRecordsUseCase,
        dietSettingsRepository: DietSettingsRepository,
        photoImageSource: PhotoImageSource,
    ) : this(
        observeEatRecords,
        dietSettingsRepository,
        photoImageSource,
        { Clock.System.now().toEpochMilliseconds() },
    )

    private val period = MutableStateFlow(Period.MONTH)
    private val anchor = MutableStateFlow(now())

    val uiState: StateFlow<InsightsUiState> =
        combine(
            observeEatRecords(),
            dietSettingsRepository.settings,
            period,
            anchor,
        ) { records, settings, selectedPeriod, selectedAnchor ->
            InsightsUiState(
                isLoaded = true,
                period = selectedPeriod,
                anchor = selectedAnchor,
                result = InsightsAggregator.compute(records, settings, now(), selectedPeriod, selectedAnchor),
                onThisDay = OnThisDay.find(records, settings, now()),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            // Seed with the real anchor (a 0L default would mean a January 1970 range) and
            // isLoaded = false so the screen stays blank instead of flashing the empty card.
            InsightsUiState(period = period.value, anchor = anchor.value),
        )

    /** Week ↔ Month keeps the anchor, so the user stays around the same days. */
    fun setPeriod(value: Period) {
        period.value = value
    }

    /** Pages the whole screen one period back or forward. Forward paging stops at today. */
    fun shiftPeriod(steps: Int) {
        anchor.value = InsightsRange.shift(anchor.value, period.value, steps, now())
    }

    /** Resolves a stored photo's file name into a Coil-loadable model for the UI. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)
}
