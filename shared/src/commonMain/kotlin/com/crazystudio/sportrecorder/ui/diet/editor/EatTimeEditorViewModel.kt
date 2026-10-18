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
import com.crazystudio.sportrecorder.domain.venue.MapsLink
import com.crazystudio.sportrecorder.domain.venue.MapsPlace
import com.crazystudio.sportrecorder.domain.venue.SharedVenue
import com.crazystudio.sportrecorder.domain.venue.SharedVenueMatch
import com.crazystudio.sportrecorder.domain.venue.VenueAddressFiller
import com.crazystudio.sportrecorder.domain.venue.VenuePicker
import com.crazystudio.sportrecorder.domain.venue.roundedDistance
import com.crazystudio.sportrecorder.platform.LocationProvider
import com.crazystudio.sportrecorder.platform.MapsLinkResolver
import com.crazystudio.sportrecorder.platform.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

// SavedStateHandle key: the user already answered this share's move question.
private const val MOVE_ANSWERED = "shareMoveAnswered"

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
    private val savedStateHandle: SavedStateHandle,
    private val mapsLinkResolver: MapsLinkResolver,
    private val venueAddressFiller: VenueAddressFiller,
) : ViewModel() {

    /** Resolves a stored photo's file name into a Coil-loadable model for the editor's thumbnails. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    // Route arg "sharedText": what another app (Google Maps) shared into a new meal (#84).
    private val sharedText: String? = savedStateHandle.get<String>("sharedText")

    // Reading the shared place; cancelled once the meal is saved so nothing changes after that.
    private var shareJob: Job? = null

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
        if (!isEditMode && sharedText != null) {
            shareJob = viewModelScope.launch { openSharedPlace(sharedText) }
        }
    }

    /**
     * A place shared from Google Maps becomes this meal's venue, at the position Maps gives — never
     * the phone's fix. Any failure only leaves a notice under the venue row: the editor stays usable
     * and saving is never held up by it.
     */
    private suspend fun openSharedPlace(text: String) {
        _uiState.update { it.copy(resolvingLink = true) }
        try {
            val link = MapsLink.find(text)
            val place = link?.let { readPlace(it) }
            val name = place?.name
            when {
                link == null -> showNotice(ShareNotice.NotAMapLink)
                // A place without a name is unusable: coordinates alone name no venue.
                place == null || name == null -> showNotice(ShareNotice.MapLinkUnreadable)
                else -> applySharedPlace(name, place.position())
            }
        } finally {
            _uiState.update { it.copy(resolvingLink = false) }
        }
    }

    /** A short link is resolved first (one request, no redirects); [MapsLink.parse] then vets the target host. */
    private suspend fun readPlace(link: MapsLink): MapsPlace? {
        val fullUrl = when (link) {
            is MapsLink.Short -> mapsLinkResolver.resolve(link)
            is MapsLink.Full -> link.url
        }
        return fullUrl?.let(MapsLink::parse)
    }

    private fun MapsPlace.position(): GeoPoint? {
        val lat = lat
        val lng = lng
        return if (lat != null && lng != null) GeoPoint(lat, lng) else null
    }

    // Matched against the venues as stored right now, not the possibly not-yet-loaded picker list,
    // so sharing the same place twice finds the venue the first share created.
    private suspend fun applySharedPlace(name: String, position: GeoPoint?) {
        val venues = venueRepository.observeAll().first()
        when (val match = SharedVenue.match(name, position, venues)) {
            is SharedVenueMatch.Create -> createSharedVenue(match)
            is SharedVenueMatch.Use -> useSharedVenue(match)
            is SharedVenueMatch.AskWhichStore -> askWhichStore(name, match)
        }
    }

    // Not createVenue: that adopts the record's device fix, and a shared place is where Maps says.
    private suspend fun createSharedVenue(match: SharedVenueMatch.Create) {
        val venue = venueRepository.findOrCreate(
            name = match.name,
            lat = match.position?.lat,
            lng = match.position?.lng,
            now = now(),
        )
        showVenue(venue)
        fillAddress(venue)
    }

    private suspend fun useSharedVenue(match: SharedVenueMatch.Use) {
        val fill = match.fillPosition
        if (fill == null) {
            showVenue(match.venue)
        } else {
            venueRepository.setPosition(match.venue.id, fill.lat, fill.lng)
            val placed = match.venue.copy(lat = fill.lat, lng = fill.lng, address = null)
            showVenue(placed)
            fillAddress(placed)
        }
    }

    // The same name far away may be another branch or the same store mis-placed: only the user knows.
    // Moving it would move its marker for every record at it, so the question carries the count.
    // Asked once per share: a share re-run after process death must not re-ask what was answered.
    private suspend fun askWhichStore(sharedName: String, match: SharedVenueMatch.AskWhichStore) {
        if (savedStateHandle.get<Boolean>(MOVE_ANSWERED) == true) {
            showVenue(match.venue)
        } else {
            val pending = EatTimeEditorUiState.PendingStore(
                venue = match.venue,
                to = match.to,
                distance = roundedDistance(match.meters),
                movedRecords = venueRepository.recordCount(match.venue.id),
                sharedName = sharedName,
            )
            _uiState.update { it.copy(venue = match.venue, pendingStore = pending) }
        }
    }

    private fun showVenue(venue: Venue) = _uiState.update { it.copy(venue = venue) }

    // An answer replaces the venue only if the user has not switched to another one meanwhile.
    private fun showVenueInPlaceOf(replacedId: Int, venue: Venue) = _uiState.update { state ->
        state.copy(venue = if (state.venue?.id == replacedId) venue else state.venue)
    }

    private fun showNotice(notice: ShareNotice) = _uiState.update { it.copy(shareNotice = notice) }

    // Fire and forget: a slow or failing lookup never holds up the editor, and a result that comes
    // back after the venue moved again is dropped by the filler.
    private fun fillAddress(venue: Venue) {
        viewModelScope.launch { venueAddressFiller.fill(listOf(venue)) }
    }

    // The user's own venue choice (or saving) wins over the share: once dropped, a share still
    // resolving creates, moves or asks nothing, a question still open is withdrawn, and a notice
    // about the shared text has nothing left to explain.
    private fun dropSharedPlace() {
        shareJob?.cancel()
        _uiState.update { it.copy(pendingMove = null, pendingStore = null, shareNotice = null) }
    }

    // Clear first, so a second tap finds nothing pending; remembered so a re-run share won't re-ask.
    private fun takePendingStore(): EatTimeEditorUiState.PendingStore? {
        val pending = _uiState.value.pendingStore
        if (pending != null) {
            _uiState.update { it.copy(pendingStore = null) }
            savedStateHandle[MOVE_ANSWERED] = true
        }
        return pending
    }

    /** The shared place is another branch: a new venue by that name at the link's pin, for this meal. */
    fun answerAnotherBranch() {
        val pending = takePendingStore() ?: return
        viewModelScope.launch {
            val branch = venueRepository.create(pending.sharedName, pending.to.lat, pending.to.lng, now())
            showVenueInPlaceOf(pending.venue.id, branch)
            fillAddress(branch)
        }
    }

    /** Same store, mis-placed: move it to the link's pin — for every record at it. */
    fun answerSameStoreMove() {
        val pending = takePendingStore() ?: return
        viewModelScope.launch {
            venueRepository.setPosition(pending.venue.id, pending.to.lat, pending.to.lng)
            val moved = pending.venue.copy(lat = pending.to.lat, lng = pending.to.lng, address = null)
            showVenueInPlaceOf(pending.venue.id, moved)
            fillAddress(moved)
        }
    }

    /** Same store, and it stays where it is; it stays selected for this meal. Also what dismissing means. */
    fun answerSameStoreKeep() {
        takePendingStore()
    }

    /** Move the shared venue to where Maps puts it — for every record at it. */
    fun confirmMove() {
        val pending = _uiState.value.pendingMove ?: return
        // Clear first, so a second tap finds nothing pending.
        _uiState.update { it.copy(pendingMove = null) }
        savedStateHandle[MOVE_ANSWERED] = true
        viewModelScope.launch {
            venueRepository.setPosition(pending.venue.id, pending.to.lat, pending.to.lng)
            val moved = pending.venue.copy(lat = pending.to.lat, lng = pending.to.lng)
            _uiState.update { state ->
                state.copy(venue = if (state.venue?.id == moved.id) moved else state.venue)
            }
        }
    }

    /** Keep the venue where it is; it stays selected for this meal. */
    fun cancelMove() {
        _uiState.update { it.copy(pendingMove = null) }
        savedStateHandle[MOVE_ANSWERED] = true
    }

    // The suggestion follows the note as it is typed, so the order is recomputed with it.
    private fun EatTimeEditorUiState.withVenueOptions() =
        copy(venueOptions = VenuePicker.order(allVenues, note))

    fun setNote(value: String) = _uiState.update { it.copy(note = value).withVenueOptions() }

    fun selectVenue(venue: Venue) {
        dropSharedPlace()
        showVenue(venue)
    }

    fun clearVenue() {
        dropSharedPlace()
        _uiState.update { it.copy(venue = null) }
    }

    /**
     * A name the user typed that is not in the list yet. The venue adopts this record's fix, if any.
     * A name that is blank once normalized is refused: it would substring-match every note.
     */
    fun createVenue(name: String) {
        if (VenueName.normalize(name).isEmpty()) return
        dropSharedPlace()
        viewModelScope.launch {
            val state = _uiState.value
            val venue = venueRepository.findOrCreate(
                name = name,
                lat = state.location?.lat,
                lng = state.location?.lng,
                now = now(),
            )
            _uiState.update { it.copy(venue = venue) }
            fillAddress(venue)
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
            val venues = venueRepository.observeAll().first()
            val collides = venues.any { it.id != venue.id && VenueName.sameAs(it.name, newName) }
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
                // Keep whatever address is stored now; this flow only changes the name.
                val currentAddress = venues.firstOrNull { it.id == venue.id }?.address
                val renamed = venueRepository.edit(venue.id, newName, currentAddress, now())
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
            // Names are not unique any more: merge into the most recently used venue with that name
            // (observeAll is most-recently-used first). If it vanished meanwhile, just rename.
            val venues = venueRepository.observeAll().first()
            val into = venues.firstOrNull { it.id != pending.from.id && VenueName.sameAs(it.name, pending.intoName) }
            val survivor = if (into != null) {
                venueRepository.merge(fromId = pending.from.id, intoId = into.id, now = now())
            } else {
                val currentAddress = venues.firstOrNull { it.id == pending.from.id }?.address
                venueRepository.edit(pending.from.id, pending.intoName, currentAddress, now())
            }
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
            fillAddress(venue.copy(lat = fix.lat, lng = fix.lng, address = null))
            // The write moves a marker the user is not looking at; say so, or it feels like a no-op.
            _uiState.update { it.copy(message = EditorMessage.VenuePositionUpdated) }
        }
    }

    /** Clear the transient message once the UI has shown it. */
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

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
        // Saving takes the venue as it is now: a share still resolving must not change it during or
        // after the write (it would create a venue the saved meal never gets).
        dropSharedPlace()
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
