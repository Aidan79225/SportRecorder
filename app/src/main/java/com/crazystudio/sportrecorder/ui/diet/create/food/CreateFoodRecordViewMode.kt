package com.crazystudio.sportrecorder.ui.diet.create.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.ColumnInfo
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import com.crazystudio.sportrecorder.entity.FoodRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateFoodRecordViewModel @Inject constructor(
    private val foodRecordDao: FoodRecordDao
) : ViewModel() {

    suspend fun createFoodRecord(
        eatTimeId: Int,
        name: String,
        carbohydrate: Float,
        protein: Float,
        fat: Float,
    ) {
        foodRecordDao.insert(FoodRecord(0, eatTimeId, name, carbohydrate, protein, fat))
    }
}