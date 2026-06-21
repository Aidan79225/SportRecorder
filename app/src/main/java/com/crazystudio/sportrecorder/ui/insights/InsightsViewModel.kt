package com.crazystudio.sportrecorder.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.data.PhotoImageSource
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.insights.Period
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

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
    ) : this(observeEatRecords, dietSettingsRepository, photoImageSource, System::currentTimeMillis)

    private val period = MutableStateFlow(Period.MONTH)
    private val monthAnchor = MutableStateFlow(now())

    val uiState: StateFlow<InsightsUiState> =
        combine(
            observeEatRecords(),
            dietSettingsRepository.settings,
            period,
            monthAnchor,
        ) { records, settings, selectedPeriod, anchor ->
            InsightsUiState(
                period = selectedPeriod,
                monthAnchor = anchor,
                result = InsightsAggregator.compute(records, settings, now(), selectedPeriod, anchor),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    fun setPeriod(value: Period) {
        period.value = value
    }

    fun shiftMonth(months: Int) {
        monthAnchor.value = Calendar.getInstance().apply {
            timeInMillis = monthAnchor.value
            add(Calendar.MONTH, months)
        }.timeInMillis
    }

    /** Resolves a stored photo's file name into a Coil-loadable model for the UI. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)
}
