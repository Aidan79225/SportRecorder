package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.dao.FastingTypeDao
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CreateFastingTypeViewModel @Inject constructor(private val fastingTypeDao: FastingTypeDao) : ViewModel() {

    suspend fun createCustomFastingType(fastingHours: Long, eatingHours: Long): Boolean = withContext(
        Dispatchers.Default
    ) {
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
