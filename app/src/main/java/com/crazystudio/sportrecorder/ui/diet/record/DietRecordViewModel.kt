package com.crazystudio.sportrecorder.ui.diet.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.util.PhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DietRecordViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
) : ViewModel() {

    val records: StateFlow<List<EatTimeWithPhotos>> = eatTimeDao.flowAllWithPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteEatTime(eatTime: EatTime) {
        viewModelScope.launch {
            val photos = photoDao.findByEatTimeId(eatTime.id)
            photos.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
            photoDao.deleteByEatTimeId(eatTime.id)
            eatTimeDao.delete(eatTime)
        }
    }
}
