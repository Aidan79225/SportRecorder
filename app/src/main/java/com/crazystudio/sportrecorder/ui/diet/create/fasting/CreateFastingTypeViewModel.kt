package com.crazystudio.sportrecorder.ui.diet.create.fasting

import androidx.lifecycle.ViewModel
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.FastingType
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CreateFastingTypeViewModel @Inject constructor(val db: AppDatabase): ViewModel() {
    private val fastingTypeDao = db.getFastingTypeDao()

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