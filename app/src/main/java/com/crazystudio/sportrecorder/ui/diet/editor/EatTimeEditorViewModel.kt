package com.crazystudio.sportrecorder.ui.diet.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.util.LocationProvider
import com.crazystudio.sportrecorder.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

@Suppress("TooManyFunctions") // cohesive editor VM: one handler per UI interaction
class EatTimeEditorViewModel constructor(
    private val appContext: Context,
    private val loadEatRecord: LoadEatRecordUseCase,
    private val saveEatRecord: SaveEatRecordUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    val currentCalendar: Calendar = Calendar.getInstance()
    private var committed = false
    private val photosToDelete = mutableListOf<EatPhoto>()

    private val _uiState = MutableStateFlow(
        EatTimeEditorUiState(date = currentCalendar.clone() as Calendar, isEditMode = isEditMode),
    )
    val uiState: StateFlow<EatTimeEditorUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                val record = loadEatRecord(eatTimeId) ?: return@launch
                currentCalendar.timeInMillis = record.time
                val loc = record.location?.let { EatTimeEditorUiState.LatLng(it.lat, it.lng) }
                _uiState.update {
                    it.copy(
                        date = currentCalendar.clone() as Calendar,
                        note = record.note.orEmpty(),
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

    /** Import an existing photo picked from the gallery. The source file is kept intact. */
    fun addPickedPhoto(uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { PhotoStorage.importFromUri(appContext, uri) }
            _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + name) }
        }
    }

    fun removePendingPhoto(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) { PhotoStorage.deleteByName(appContext, fileName) }
        _uiState.update { it.copy(pendingPhotos = it.pendingPhotos - fileName) }
    }

    /** Mark an already-saved photo for deletion; actual delete happens on save(). */
    fun removeExistingPhoto(photo: EatPhoto) {
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
                    it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
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

    /** Validate + persist via the use case. Returns false if rejected (e.g. future time). */
    suspend fun save(): Boolean {
        val state = _uiState.value
        val record = EatRecord(
            id = eatTimeId,
            time = currentCalendar.timeInMillis,
            location = state.location?.let { GeoPoint(it.lat, it.lng) },
            note = state.note.ifBlank { null },
            photos = emptyList(), // photos are managed via pendingPhotos / photosToDelete below
        )
        val ok = saveEatRecord(record, state.pendingPhotos, photosToDelete)
        if (ok) committed = true
        return ok
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
