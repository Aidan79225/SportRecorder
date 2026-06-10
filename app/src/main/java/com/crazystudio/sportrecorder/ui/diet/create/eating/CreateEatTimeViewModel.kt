package com.crazystudio.sportrecorder.ui.diet.create.eating

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.crazystudio.sportrecorder.dao.EatTimeDao
import com.crazystudio.sportrecorder.dao.PhotoDao
import com.crazystudio.sportrecorder.database.AppDatabase
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.Photo
import com.crazystudio.sportrecorder.util.LocationProvider
import com.crazystudio.sportrecorder.util.PhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class CreateEatTimeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
) : ViewModel() {

    val currentCalendar: Calendar = Calendar.getInstance()
    private var committed = false

    private val _uiState = MutableStateFlow(CreateEatTimeUiState(date = currentCalendar.clone() as Calendar))
    val uiState: StateFlow<CreateEatTimeUiState> = _uiState.asStateFlow()

    /** Convert a temp capture file to webp (off the main thread) and stage its file name. */
    fun addCapturedPhoto(tempFile: File) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { PhotoStorage.convertToWebp(appContext, tempFile) }
            _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + name) }
        }
    }

    fun removePendingPhoto(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) { PhotoStorage.deleteByName(appContext, fileName) }
        _uiState.update { it.copy(pendingPhotos = it.pendingPhotos - fileName) }
    }

    /** Call once the location permission is granted. */
    fun requestLocation() {
        _uiState.update { it.copy(locationStatus = CreateEatTimeUiState.LocationStatus.LOADING) }
        viewModelScope.launch {
            val result = LocationProvider.currentLocation(appContext)
            _uiState.update {
                if (result == null) it.copy(location = null, locationStatus = CreateEatTimeUiState.LocationStatus.UNAVAILABLE)
                else it.copy(
                    location = CreateEatTimeUiState.LatLng(result.first, result.second),
                    locationStatus = CreateEatTimeUiState.LocationStatus.AVAILABLE,
                )
            }
        }
    }

    fun locationDenied() {
        _uiState.update { it.copy(locationStatus = CreateEatTimeUiState.LocationStatus.UNAVAILABLE) }
    }

    suspend fun createEatingTime(): Boolean {
        if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) return false
        val state = _uiState.value
        appDatabase.withTransaction {
            val eatTimeId = eatTimeDao.insert(
                EatTime(time = currentCalendar.timeInMillis, lat = state.location?.lat, lng = state.location?.lng)
            ).toInt()
            val now = System.currentTimeMillis()
            state.pendingPhotos.forEach { name ->
                photoDao.insert(Photo(eatTimeId = eatTimeId, fileName = name, createdAt = now))
            }
        }
        committed = true
        return true
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

    private fun publishDate() {
        _uiState.update { it.copy(date = currentCalendar.clone() as Calendar) }
    }

    override fun onCleared() {
        super.onCleared()
        if (!committed) {
            // Sheet dismissed without creating — delete the staged webp files.
            _uiState.value.pendingPhotos.forEach { PhotoStorage.deleteByName(appContext, it) }
        }
    }
}
