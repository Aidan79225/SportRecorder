package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.SportApplication
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.ui.diet.select.DefaultFastingViewHolder
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateFastingTypeViewModel: ViewModel() {
    private val fastingTypeDao = SportApplication.db.getFastingTypeDao()

    suspend fun createCustomFastingType(fastingHours: Long, eatingHours: Long): Boolean = withContext(Dispatchers.Default) {
        if (fastingTypeDao.findByHours(fastingHours, eatingHours).isNotEmpty()) {
            return@withContext false
        }

        val isDefaultContains = FastingItem.defaultFastingItems.map {
            if (it is FastingItem.DefaultFastingItem) {
                return@map it.fastingHours == fastingHours && it.eatingHours == eatingHours
            } else {
                return@map false
            }
        }.reduce { acc, b -> acc || b }
        if (isDefaultContains) {
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