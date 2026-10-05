package com.crazystudio.sportrecorder.ui.diet.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.data.PhotoImageSource
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import com.crazystudio.sportrecorder.domain.usecase.LoadEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.SaveEatRecordUseCase
import com.crazystudio.sportrecorder.domain.venue.VenuePicker
import com.crazystudio.sportrecorder.platform.LocationProvider
import com.crazystudio.sportrecorder.platform.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

// LongParameterList: these are injected DI dependencies, not logic params.
@Suppress("TooManyFunctions", "LongParameterList") // cohesive editor VM: one handler per UI interaction
class EatTimeEditorViewModel constructor(
    private val loadEatRecord: LoadEatRecordUseCase,
    private val saveEatRecord: SaveEatRecordUseCase,
    private val locationProvider: LocationProvider,
    private val venueRepository: VenueRepository,
    private val photoImporter: PhotoImporter,
    private val photoFileStore: PhotoFileStore,
    private val photoImageSource: PhotoImageSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Resolves a stored photo's file name into a Coil-loadable model for the editor's thumbnails. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)

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

    // Every venue, as last emitted; the picker's order is derived from it and the live note.
    private var allVenues: List<Venue> = emptyList()

    // The venue the record already had when it was opened; saving it back is not a new use.
    private var loadedVenueId: Int? = null

    init {
        viewModelScope.launch {
            venueRepository.observeAll().collect { venues ->
                allVenues = venues
                _uiState.update { it.withVenueOptions() }
            }
        }
        if (isEditMode) {
            viewModelScope.launch {
                val record = loadEatRecord(eatTimeId) ?: return@launch
                currentMillis = record.time
                loadedVenueId = record.venue?.id
                val loc = record.location?.let { EatTimeEditorUiState.LatLng(it.lat, it.lng) }
                _uiState.update {
                    it.copy(
                        dateMillis = currentMillis,
                        note = record.note.orEmpty(),
                        existingPhotos = record.photos,
                        venue = record.venue,
                        location = loc,
                        locationStatus = if (loc != null) {
                            EatTimeEditorUiState.LocationStatus.AVAILABLE
                        } else {
                            EatTimeEditorUiState.LocationStatus.IDLE
                        },
                    ).withVenueOptions()
                }
            }
        }
    }

    // The suggestion follows the note as it is typed, so the order is recomputed with it.
    private fun EatTimeEditorUiState.withVenueOptions() =
        copy(venueOptions = VenuePicker.order(allVenues, note))

    fun setNote(value: String) = _uiState.update { it.copy(note = value).withVenueOptions() }

    fun selectVenue(venue: Venue) = _uiState.update { it.copy(venue = venue) }

    fun clearVenue() = _uiState.update { it.copy(venue = null) }

    /**
     * A name the user typed that is not in the list yet. The venue adopts this record's fix, if any.
     * A name that is blank once normalized is refused: it would substring-match every note.
     */
    fun createVenue(name: String) {
        if (VenueName.normalize(name).isEmpty()) return
        viewModelScope.launch {
            val state = _uiState.value
            val venue = venueRepository.findOrCreate(
                name = name,
                lat = state.location?.lat,
                lng = state.location?.lng,
                now = now(),
            )
            _uiState.update { it.copy(venue = venue) }
        }
    }

    /**
     * Rename [venue]. When [newName] already belongs to another venue this is a merge, which
     * rewrites other records — so nothing happens until the user confirms, and the confirmation
     * carries how many records would move.
     */
    fun requestRename(venue: Venue, newName: String) {
        if (VenueName.normalize(newName).isEmpty()) return
        viewModelScope.launch {
            val collides = venueRepository.observeAll().first()
                .any { it.id != venue.id && VenueName.sameAs(it.name, newName) }
            if (collides) {
                val movedRecords = venueRepository.recordCount(venue.id)
                _uiState.update {
                    it.copy(
                        pendingMerge = EatTimeEditorUiState.PendingMerge(
                            from = venue,
                            intoName = VenueName.normalize(newName),
                            movedRecords = movedRecords,
                        ),
                    )
                }
            } else {
                val renamed = venueRepository.rename(venue.id, newName, now())
                _uiState.update { state ->
                    state.copy(venue = if (state.venue?.id == venue.id) renamed else state.venue)
                }
            }
        }
    }

    fun confirmRename() {
        val pending = _uiState.value.pendingMerge ?: return
        // Clear first: a second tap on Merge must find nothing pending, not rename a venue that
        // the first one already merged away.
        _uiState.update { it.copy(pendingMerge = null) }
        viewModelScope.launch {
            val survivor = venueRepository.rename(pending.from.id, pending.intoName, now())
            _uiState.update { state ->
                state.copy(venue = if (state.venue?.id == pending.from.id) survivor else state.venue)
            }
        }
    }

    fun cancelRename() = _uiState.update { it.copy(pendingMerge = null) }

    /**
     * Correct where a venue is, using this record's own fix. Because records read through
     * `venue_id`, this moves the venue's marker for **every past record at once** — the reason a
     * venue is an entity at all. Offered only when this record actually has a position.
     */
    fun useThisPositionFor(venue: Venue) {
        val fix = _uiState.value.location ?: return
        viewModelScope.launch {
            venueRepository.setPosition(venue.id, fix.lat, fix.lng)
        }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

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
        viewModelScope.launch(Dispatchers.Default) { photoFileStore.delete(fileName) }
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
            // The update path rewrites the whole row, so the venue must travel with the record:
            // leaving it out here would silently clear it whenever only the note was edited.
            venue = state.venue,
        )
        val ok = saveEatRecord(record, state.pendingPhotos, photosToDelete)
        if (ok) {
            committed = true
            // Recency should reflect use, not creation, and not a later edit of an old record:
            // fixing last month's note must not push that venue above what is eaten now.
            state.venue?.takeIf { it.id != loadedVenueId }?.let { venueRepository.touch(it.id, now()) }
        }
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
