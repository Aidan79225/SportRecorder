package com.crazystudio.sportrecorder.ui.diet.create.eating

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.entity.EatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CreateEatTimeViewModel @Inject constructor(private val eatTimeDao: EatTimeDao): ViewModel() {

    val currentCalendar = Calendar.getInstance()
    val calendarLiveData = MutableLiveData(currentCalendar)

    suspend fun createEatingTime(): Boolean {
        return if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) {
            false
        } else {
            eatTimeDao.insert(EatTime(time = currentCalendar.timeInMillis))
            true
        }
    }

    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        currentCalendar.set(Calendar.YEAR, year)
        currentCalendar.set(Calendar.MONTH, month)
        currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        calendarLiveData.value = currentCalendar
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        currentCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        currentCalendar.set(Calendar.MINUTE, minute)
        calendarLiveData.value = currentCalendar
    }


}