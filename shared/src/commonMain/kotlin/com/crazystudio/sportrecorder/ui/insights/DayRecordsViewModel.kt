package com.crazystudio.sportrecorder.ui.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.data.PhotoImageSource
import com.crazystudio.sportrecorder.domain.insights.InsightsAggregator
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.repository.DietSettingsRepository
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

/**
 * The meals of one eating day, for the read-only sheet a calendar cell or chart row opens.
 * Grouped exactly as the calendar groups them ([InsightsAggregator.mealsByEatingDay]), so the
 * 00:30 snack the calendar counted under the 12th is listed under the 12th here too.
 */
class DayRecordsViewModel(
    observeEatRecords: ObserveEatRecordsUseCase,
    dietSettingsRepository: DietSettingsRepository,
    private val photoImageSource: PhotoImageSource,
    savedStateHandle: SavedStateHandle,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** Route arg: type-safe route field name "dayStart" — the eating day's local midnight. */
    val dayStart: Long = savedStateHandle.get<Long>("dayStart") ?: 0L

    val records: StateFlow<List<EatRecord>> =
        combine(observeEatRecords(), dietSettingsRepository.settings) { records, settings ->
            InsightsAggregator.mealsByEatingDay(records, settings, timeZone)[dayStart].orEmpty()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Resolves a stored photo's file name into a Coil-loadable model for the UI. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)
}
