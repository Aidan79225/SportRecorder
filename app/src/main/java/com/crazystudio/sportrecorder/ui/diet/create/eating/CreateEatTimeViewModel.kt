package com.crazystudio.sportrecorder.ui.diet.create.eating

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.FoodRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CreateEatTimeViewModel @Inject constructor(
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val foodRecordDao: FoodRecordDao
): ViewModel() {

    val currentCalendar = Calendar.getInstance().apply {

    }
    val calendarLiveData = MutableLiveData(currentCalendar)

    val foodRecordLiveData = foodRecordDao.liveFoodRecordByEatTimeId(0)

    suspend fun createEatingTime(): Boolean {
        return if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) {
            false
        } else {
            appDatabase.withTransaction {
                val eatTimeId = eatTimeDao.insert(EatTime(time = currentCalendar.timeInMillis))
                foodRecordDao.updateEatTimeId(0, eatTimeId.toInt())
            }
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

    suspend fun deleteFoodRecord(foodRecord: FoodRecord) {
        foodRecordDao.delete(foodRecord)
    }


}