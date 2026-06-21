package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.platform.LocationProvider
import com.crazystudio.sportrecorder.platform.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@Suppress("TooManyFunctions") // cohesive editor VM: one handler per UI interaction
class EatTimeEditorViewModel constructor(
    private val loadEatRecord: LoadEatRecordUseCase,
    private val saveEatRecord: SaveEatRecordUseCase,
    private val locationProvider: LocationProvider,
    private val photoImporter: PhotoImporter,
    private val photoFileStore: PhotoFileStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    // The meal's date+time being edited, as epoch millis. Read by the host to seed the platform
    // date/time pickers; mutated by updateDate/updateTime.
    var currentMillis: Long = Clock.System.now().toEpochMilliseconds()
        private set
    private var committed = false
    private val photosToDelete = mutableListOf<EatPhoto>()
    private val zone get() = TimeZone.currentSystemDefault()

    private val _uiState = MutableStateFlow(
        EatTimeEditorUiState(dateMillis = currentMillis, isEditMode = isEditMode),
    )
    val uiState: StateFlow<EatTimeEditorUiState> = _uiState.asStateFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                val record = loadEatRecord(eatTimeId) ?: return@launch
                currentMillis = record.time
                val loc = record.location?.let { EatTimeEditorUiState.LatLng(it.lat, it.lng) }
                _uiState.update {
                    it.copy(
                        dateMillis = currentMillis,
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

    /** Import a just-captured photo. [sourcePath] is the camera temp file's path. */
    fun addCapturedPhoto(sourcePath: String) {
        viewModelScope.launch {
            val name = photoImporter.importCapture(sourcePath) ?: return@launch
            _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + name) }
        }
    }

    /** Import an existing photo picked from the gallery. [sourceUri] is the picker URI; source kept intact. */
    fun addPickedPhoto(sourceUri: String) {
        viewModelScope.launch {
            val name = photoImporter.importPicked(sourceUri) ?: return@launch
            _uiState.update { it.copy(pendingPhotos = it.pendingPhotos + name) }
        }
    }

    fun removePendingPhoto(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) { photoFileStore.delete(fileName) }
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
            val result = locationProvider.currentLocation()
            _uiState.update {
                if (result == null) {
                    it.copy(location = null, locationStatus = EatTimeEditorUiState.LocationStatus.UNAVAILABLE)
                } else {
                    it.copy(
                        location = EatTimeEditorUiState.LatLng(result.lat, result.lng),
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
            time = currentMillis,
            location = state.location?.let { GeoPoint(it.lat, it.lng) },
            note = state.note.ifBlank { null },
            photos = emptyList(), // photos are managed via pendingPhotos / photosToDelete below
        )
        val ok = saveEatRecord(record, state.pendingPhotos, photosToDelete)
        if (ok) committed = true
        return ok
    }

    /** [month] is 0-based (Android DatePickerDialog convention); kotlinx-datetime is 1-based. */
    fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
        val dt = Instant.fromEpochMilliseconds(currentMillis).toLocalDateTime(zone)
        val newDate = LocalDate(year, month + 1, dayOfMonth)
        currentMillis = LocalDateTime(newDate, dt.time).toInstant(zone).toEpochMilliseconds()
        publishDate()
    }

    fun updateTime(hourOfDay: Int, minute: Int) {
        val dt = Instant.fromEpochMilliseconds(currentMillis).toLocalDateTime(zone)
        val newTime = LocalTime(hourOfDay, minute, dt.time.second, dt.time.nanosecond)
        currentMillis = LocalDateTime(dt.date, newTime).toInstant(zone).toEpochMilliseconds()
        publishDate()
    }

    private fun publishDate() = _uiState.update { it.copy(dateMillis = currentMillis) }

    override fun onCleared() {
        super.onCleared()
        if (!committed) {
            // Dismissed without saving — delete only the newly-captured (unsaved) files.
            _uiState.value.pendingPhotos.forEach { photoFileStore.delete(it) }
        }
    }
}
