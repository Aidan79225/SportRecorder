package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.SportApplication
import com.crazystudio.sportrecorder.entity.FastingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateFastingTypeViewModel: ViewModel() {
    private val fastingTypeDao = SportApplication.db.getFastingTypeDao()

    suspend fun createCustomFastingType(fastingHours: Long, eatingHours: Long): Boolean = withContext(Dispatchers.Default) {
        if (fastingTypeDao.findByHours(fastingHours, eatingHours).isNotEmpty()) {
            return@withContext false
        }
        fastingTypeDao.insert(
            FastingType(
                fastingHours = fastingHours,
                eatingHours = eatingHours,
                timestamp = System.currentTimeMillis()
            )
        )
        return@withContext true
    }
}