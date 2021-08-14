package com.crazystudio.sportrecorder.ui.diet.record

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.entity.EatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DietRecordViewModel @Inject constructor(private val eatTimeDao: EatTimeDao) : ViewModel() {
    val eatTimeLiveData = eatTimeDao.liveAll()

    fun deleteEatTime(eatTime: EatTime) {
        viewModelScope.launch {
            eatTimeDao.delete(eatTime)
        }
    }


}