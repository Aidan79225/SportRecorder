package com.crazystudio.sportrecorder.ui.diet

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.database.AppDatabase

import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@HiltViewModel
class DietViewModel @Inject constructor(private val eatTimeDao: EatTimeDao): ViewModel() {
    val lastEatTimeLiveData = MutableLiveData<Pair<Long, Long>>()

    val historyLiveData = MutableLiveData<List<Pair<Long, Float>>>()

    val selectFastingItemLiveData = MutableLiveData<FastingItem.DefaultFastingItem>()

    init {

        viewModelScope.launch {
            findHistory()
        }
    }

    private suspend fun findHistory() {
        val before = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)+1)
        }.timeInMillis

        val after = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)-8)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        eatTimeDao.flowByTimeInterval(before, after).collect { data ->
            val timeInterval = mergeInterval(data)
            if (timeInterval.isNotEmpty()) {
                lastEatTimeLiveData.value = timeInterval.last()
            }
            (0..4).map { i ->
                val before = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)-i-1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val after = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, get(Calendar.DAY_OF_YEAR)-i)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                before to effectiveProgress(timeInterval, before, after)
            }.let {
                historyLiveData.value = it.asReversed()
            }
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
                    add(Pair(start.time, end.time))
                    start = it
                    end = it
                }
            }
            add(Pair(start.time, end.time))
        }
    }

    private fun effectiveProgress(timeInterval: List<Pair<Long, Long>>, before: Long, after: Long): Float {
        var sum = 0f

        timeInterval.forEach {
            val max = min(after, it.second)

            val min = max(before, it.first)
            sum += max(0, max - min)
        }

        return 1.0f - (sum / (after-before).toFloat())
    }
}