package com.crazystudio.sportrecorder.ui.diet.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
@Suppress("TooManyFunctions") // cohesive editor VM: one handler per UI interaction
class EatTimeEditorViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appDatabase: AppDatabase,
    private val eatTimeDao: EatTimeDao,
    private val photoDao: PhotoDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    val currentCalendar: Calendar = Calendar.getInstance()
    private var committed = false
    private val photosToDelete = mutableListOf<Photo>()

    private val _uiState = MutableStateFlow(
        EatTimeEditorUiState(date = currentCalendar.clone() as Calendar, isEditMode = isEditMode)
    )
    val uiState: StateFlow<EatTimeEditorUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                val record = eatTimeDao.findWithPhotosById(eatTimeId) ?: return@launch
                currentCalendar.timeInMillis = record.eatTime.time
                val loc = if (record.eatTime.lat != null && record.eatTime.lng != null) {
                    EatTimeEditorUiState.LatLng(record.eatTime.lat, record.eatTime.lng)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        date = currentCalendar.clone() as Calendar,
                        note = record.eatTime.note.orEmpty(),
                        existingPhotos = record.photos,
                        location = loc,
                        locationStatus = if (loc != null) {
                            EatTimeEditorUiState.LocationStatus.AVAILABLE
                        } else {
                            EatTimeEditorUiState.LocationStatus.IDLE
                        },
                    )
                }
            }
        }
    }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

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

    /** Mark an already-saved photo for deletion; actual delete happens on save(). */
    fun removeExistingPhoto(photo: Photo) {
        photosToDelete.add(photo)
        _uiState.update { it.copy(existingPhotos = it.existingPhotos - photo) }
    }

    /** Re-capture (or first capture) the current location. */
    fun requestLocation() {
        _uiState.update { it.copy(locationStatus = EatTimeEditorUiState.LocationStatus.LOADING) }
        viewModelScope.launch {
            val result = LocationProvider.currentLocation(appContext)
            _uiState.update {
                if (result == null) {
                    it.copy(
                        location = null,
                        locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE
                    )
                } else {
                    it.copy(
                        location = EatTimeEditorUiState.LatLng(result.first, result.second),
                        locationStatus = EatTimeEditorUiState.LocationStatus.AVAILABLE,
                    )
                }
            }
        }
    }

    fun clearLocation() = _uiState.update {
        it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.IDLE)
    }

    fun locationDenied() = _uiState.update {
        it.copy(locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
    }

    /** INSERT (create) or UPDATE (edit) the eat record and reconcile photos. */
    suspend fun save(): Boolean {
        if (currentCalendar.timeInMillis > Calendar.getInstance().timeInMillis) return false
        val state = _uiState.value
        val note = state.note.ifBlank { null }
        appDatabase.withTransaction {
            val id: Int = if (isEditMode) {
                eatTimeDao.update(
                    EatTime(
                        id = eatTimeId,
                        time = currentCalendar.timeInMillis,
                        lat = state.location?.lat,
                        lng = state.location?.lng,
                        note = note
                    )
                )
                photosToDelete.forEach { photoDao.delete(it) }
                eatTimeId
            } else {
                eatTimeDao.insert(
                    EatTime(
                        time = currentCalendar.timeInMillis,
                        lat = state.location?.lat,
                        lng = state.location?.lng,
                        note = note
                    )
                ).toInt()
            }
            val now = System.currentTimeMillis()
            state.pendingPhotos.forEach { name ->
                photoDao.insert(Photo(eatTimeId = id, fileName = name, createdAt = now))
            }
        }
        // File deletes are not transactional — only after the DB transaction succeeds.
        photosToDelete.forEach { PhotoStorage.deleteByName(appContext, it.fileName) }
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

    private fun publishDate() = _uiState.update { it.copy(date = currentCalendar.clone() as Calendar) }

    override fun onCleared() {
        super.onCleared()
        if (!committed) {
            // Dismissed without saving — delete only the newly-captured (unsaved) files.
            _uiState.value.pendingPhotos.forEach { PhotoStorage.deleteByName(appContext, it) }
        }
    }
}
