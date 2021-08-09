package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.database.AppDatabase

import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@HiltViewModel
class DietViewModel @Inject constructor(private val eatTimeDao: EatTimeDao): ViewModel() {
    val lastEatTimeLiveData = MutableLiveData<Pair<EatTime, EatTime>>()

    val historyLiveData = MutableLiveData<List<Float>>()

    val selectFastingItemLiveData = MutableLiveData<FastingItem.DefaultFastingItem>()

    init {
        viewModelScope.launch {
            updateEatingTime()
            findHistory()
        }
    }

    private suspend fun updateEatingTime() {
        val last = eatTimeDao.findLast()
        if (last.isEmpty()) {
            return
        }
        lastEatTimeLiveData.value = getTimeInterval(last[0])
    }

    private suspend fun getTimeInterval(last: EatTime): Pair<EatTime, EatTime> {
        var pre = last
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
        return Pair(pre, last)
    }

    private suspend fun findHistory() {
        val before = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val after = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)-8)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val data = eatTimeDao.findByTimeInterval(before, after)
        val timeInterval = mergeInterval(data)

        (0..6).map { i ->
            val before = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)+i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val after = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)+i+1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            effectiveProgress(timeInterval, before, after)
        }.let {
            historyLiveData.value = it.asReversed()
        }

    }

    private fun mergeInterval(data: List<EatTime>): List<Pair<Long, Long>> {
        val eight = TimeUnit.HOURS.toMillis(8)
        return mutableListOf<Pair<Long, Long>>().apply{
            if (data.isEmpty()) {
                return@apply
            }
            var start = data[0]
            var end = data[0]
            data.forEach {
                if (end.time + eight > it.time) {
                    end = it
                } else {
                    add(Pair(start.time, max(end.time, start.time+eight)))
                    start = it
                    end = it
                }
            }
            add(Pair(start.time, max(end.time, start.time+eight)))
        }
    }

    private fun effectiveProgress(timeInterval: List<Pair<Long, Long>>, before: Long, after: Long): Float {
        var sum = 0f

        timeInterval.forEach {
            val max = min(after, it.second)

            val min = max(before, it.first)
            sum += max(0, max - min)
        }

        return sum / (after-before).toFloat()


    }

    fun createEatTime() {
        viewModelScope.launch {
            eatTimeDao.insert(EatTime(time = Calendar.getInstance().timeInMillis))
            updateEatingTime()
            findHistory()
        }
    }

}