package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.crazystudio.sportrecorder.SportApplication

import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.launch

class DietViewModel: ViewModel() {
    val eatTimeDao = SportApplication.db.eatTimeDao()

    val lastEatTimeLiveData = eatTimeDao.liveLast()

    fun createEatTime() {
        viewModelScope.launch {
            eatTimeDao.insert(EatTime(time = System.currentTimeMillis()))
        }
    }

}