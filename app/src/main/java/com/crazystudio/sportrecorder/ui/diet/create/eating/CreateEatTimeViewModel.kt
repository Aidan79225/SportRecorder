package com.crazystudio.sportrecorder.ui.diet.create.eating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.FoodRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class CreateEatTimeViewModel @Inject constructor(
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val foodRecordDao: FoodRecordDao
) : ViewModel() {

    // Food records are staged under the "draft" eat-time id (0) until the eat time
    // is created, at which point they are reassigned from 0 to the new eat-time id.
    private val draftEatTimeId = 0

    val currentCalendar = Calendar.getInstance()

    private val _uiState = MutableStateFlow(CreateEatTimeUiState(date = currentCalendar))
    val uiState: StateFlow<CreateEatTimeUiState> = _uiState.asStateFlow()

    /** The eat-time id under which newly created food records should be staged. */
    val foodCreateEatTimeId: Long = draftEatTimeId.toLong()

    init {
        viewModelScope.launch {
            foodRecordDao.foodRecordByEatTimeIdFlow(draftEatTimeId).collect { foods ->
                _uiState.update { it.copy(foods = foods) }
            }
        }
    }

    suspend fun createEatingTime(): Boolean {
        return if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) {
            false
        } else {
            appDatabase.withTransaction {
                val eatTimeId = eatTimeDao.insert(EatTime(time = currentCalendar.timeInMillis))
                foodRecordDao.updateEatTimeId(draftEatTimeId, eatTimeId.toInt())
            }
            true
        }
    }

    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        currentCalendar.set(Calendar.YEAR, year)
        currentCalendar.set(Calendar.MONTH, month)
        currentCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        publishDate()
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        currentCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
        currentCalendar.set(Calendar.MINUTE, minute)
        publishDate()
    }

    suspend fun deleteFoodRecord(foodRecord: FoodRecord) {
        foodRecordDao.delete(foodRecord)
    }

    private fun publishDate() {
        // Emit a clone so Compose observes a distinct Calendar instance and recomposes.
        _uiState.update { it.copy(date = currentCalendar.clone() as Calendar) }
    }
}
