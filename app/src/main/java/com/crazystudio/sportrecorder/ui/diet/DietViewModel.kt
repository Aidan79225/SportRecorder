package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.crazystudio.sportrecorder.SportApplication

import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DietViewModel: ViewModel() {
    private val eatTimeDao = SportApplication.db.eatTimeDao()

    val lastEatTimeLiveData = MutableLiveData<Pair<EatTime, EatTime>>()

    init {
        viewModelScope.launch {
            updateEatingTime()
        }
    }

    private suspend fun updateEatingTime() {
        val last = eatTimeDao.findLast()
        if (last.isEmpty()) {
            return
        }

        var pre = last[0]
        while (true) {
            val t = eatTimeDao.findLastByTime(pre.time)
            if (t.isEmpty()) {
                break
            }
            if (pre.time - t[0].time > TimeUnit.HOURS.toMillis(6)) {
                break
            }
            pre = t[0]
        }
        lastEatTimeLiveData.value = Pair(pre, last[0])
    }

    fun createEatTime() {
        viewModelScope.launch {
            eatTimeDao.insert(EatTime(time = System.currentTimeMillis()))
            updateEatingTime()
        }
    }

}