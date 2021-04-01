package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.SportApplication
import com.crazystudio.sportrecorder.entity.FastingType
import kotlinx.coroutines.launch

class CreateFastingTypeViewModel: ViewModel() {
    private val fastingTypeDao = SportApplication.db.getFastingTypeDao()

    fun createCustomFastingType(fastingHours: Long, eatingHours: Long) = viewModelScope.launch {
        fastingTypeDao.insert(
            FastingType(
                fastingHours = fastingHours,
                eatingHours = eatingHours,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}