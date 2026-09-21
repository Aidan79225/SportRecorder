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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            // Seed with the real anchor: a 0L default would flash a January 1970 calendar.
            InsightsUiState(period = period.value, monthAnchor = monthAnchor.value),
        )

    fun setPeriod(value: Period) {
        period.value = value
    }

    /** Pages the calendar. Forward paging stops at the current month — there is nothing yet. */
    fun shiftMonth(months: Int) {
        val zone = TimeZone.currentSystemDefault()
        val date = Instant.fromEpochMilliseconds(monthAnchor.value).toLocalDateTime(zone).date
        // Aggregator reads only the month from monthAnchor, so start-of-day is fine.
        val shifted = date.plus(months, DateTimeUnit.MONTH)
        val today = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date
        val isFuture = shifted.year > today.year ||
            (shifted.year == today.year && shifted.month.number > today.month.number)
        if (isFuture) return
        monthAnchor.value = shifted.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** Resolves a stored photo's file name into a Coil-loadable model for the UI. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)
}
