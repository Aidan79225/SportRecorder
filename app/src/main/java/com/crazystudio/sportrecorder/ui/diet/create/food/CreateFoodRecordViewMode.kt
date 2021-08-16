package com.crazystudio.sportrecorder.ui.diet.create.food

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.dao.FoodRecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CreateFoodRecordViewModel @Inject constructor(
    private val foodRecordDao: FoodRecordDao
): ViewModel() {

}