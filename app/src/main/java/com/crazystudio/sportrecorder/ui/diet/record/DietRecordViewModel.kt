package com.crazystudio.sportrecorder.ui.diet.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.entity.EatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DietRecordViewModel @Inject constructor(private val eatTimeDao: EatTimeDao) : ViewModel() {

    val records: StateFlow<List<EatTime>> = eatTimeDao.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteEatTime(eatTime: EatTime) {
        viewModelScope.launch {
            eatTimeDao.delete(eatTime)
        }
    }
}
