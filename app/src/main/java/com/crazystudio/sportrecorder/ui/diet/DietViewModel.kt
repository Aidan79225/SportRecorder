package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.database.AppDatabase

import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class DietViewModel @Inject constructor(val db: AppDatabase): ViewModel() {
    private val eatTimeDao = db.getEatTimeDao()

    val lastEatTimeLiveData = MutableLiveData<Pair<EatTime, EatTime>>()

    val selectFastingItemLiveData = MutableLiveData<FastingItem.DefaultFastingItem>()

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
            if (pre.time - t[0].time > TimeUnit.HOURS.toMillis(8)) {
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