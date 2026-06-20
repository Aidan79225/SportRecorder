package com.crazystudio.sportrecorder.ui.diet.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DietRecordViewModel constructor(
    observeEatRecords: ObserveEatRecordsUseCase,
    private val deleteEatRecord: DeleteEatRecordUseCase,
) : ViewModel() {

    val records: StateFlow<List<EatRecord>> = observeEatRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRecord(record: EatRecord) {
        viewModelScope.launch { deleteEatRecord(record.id) }
    }
}
