package com.crazystudio.sportrecorder.ui.diet.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.domain.usecase.ObserveCustomFastingTypesUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveFastingSelectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectFastingTypeViewModel @Inject constructor(
    observeCustomFastingTypes: ObserveCustomFastingTypesUseCase,
    private val saveFastingSelection: SaveFastingSelectionUseCase,
) : ViewModel() {

    val fastingItemFlow: Flow<List<FastingItem>> = observeCustomFastingTypes().map { types ->
        FastingItem.defaultFastingItems +
            types.map { FastingItem.CustomFastingItem(it.fastingHours, it.eatingHours, it.name) }.reversed()
    }

    fun saveSelection(fastingHours: Long, eatingHours: Long) {
        viewModelScope.launch { saveFastingSelection(FastingWindow(fastingHours, eatingHours)) }
    }
}
