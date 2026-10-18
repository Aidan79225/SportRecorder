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
import com.crazystudio.sportrecorder.domain.venue.VenueMerge
import com.crazystudio.sportrecorder.domain.venue.VenuePicker
import com.crazystudio.sportrecorder.domain.venue.roundedDistance
import com.crazystudio.sportrecorder.platform.ClipboardReader
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

/**
 * One opening of a Maps place, and whether the question it asked was answered. A share re-runs from
 * its route after process death, so its answer is kept in the SavedStateHandle; a paste never re-runs,
 * so its answer lives only as long as that paste. Neither answers the other's question.
 */
private sealed interface PlaceRun {
    var answered: Boolean

    class Share(private val handle: SavedStateHandle) : PlaceRun {
        override var answered: Boolean
            get() = handle.get<Boolean>(MOVE_ANSWERED) == true
            set(value) {
                handle[MOVE_ANSWERED] = value
            }
    }

    class Paste : PlaceRun {
        override var answered = false
    }
}

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
    private val clipboardReader: ClipboardReader,
) : ViewModel(), VenuePickerActions {

    /** Resolves a stored photo's file name into a Coil-loadable model for the editor's thumbnails. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)

    // Route arg: type-safe route field name "eatTimeId". 0 = create, >0 = edit.
    private val eatTimeId: Int = savedStateHandle.get<Int>("eatTimeId") ?: 0
    private val isEditMode: Boolean = eatTimeId > 0

    // Route arg "sharedText": what another app (Google Maps) shared into a new meal (#84).
    private val sharedText: String? = savedStateHandle.get<String>("sharedText")

    // Reading the shared or pasted place; cancelled once the meal is saved so nothing changes after that.
    private var placeJob: Job? = null

    // The run the open question (if any) belongs to: answering it marks that run, not another.
    private var placeRun: PlaceRun = PlaceRun.Share(savedStateHandle)

    // Reading a map link to move a venue from the edit dialog; dropped when the user declines the
    // move or closes the dialog.
    private var repositionJob: Job? = null

    // Looking up the addresses the picker is missing; one at a time, however often it is opened.
    private var backfillJob: Job? = null

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
            startPlaceRun(sharedText, PlaceRun.Share(savedStateHandle))
        }
    }

    private fun startPlaceRun(text: String, run: PlaceRun) {
        val previous = placeJob
        placeRun = run
        placeJob = viewModelScope.launch {
            // A cancelled run still clears resolvingLink on its way out; let it finish before this one starts.
            previous?.join()
            openPlace(text, run)
        }
    }

    /**
     * Opens the Maps link on the clipboard, read now because the user tapped to paste. True when it
     * held one and the place is being opened; otherwise the user is asked to paste a link by hand.
     */
    override suspend fun pasteMapsLink(): Boolean {
        val text = clipboardReader.text()?.takeIf { MapsLink.find(it) != null }
        if (text != null) {
            startPaste(text)
        } else {
            _uiState.update { it.copy(pasteDialog = PasteTarget.Select) }
        }
        return text != null
    }

    /** What the user pasted into the dialog. Text with no Maps link says so, the way a share does. */
    override fun pasteText(text: String) {
        val target = _uiState.value.pasteDialog
        dismissPasteDialog()
        when (target) {
            PasteTarget.Select -> startPaste(text)
            is PasteTarget.Reposition -> startReposition(text, target.venue)
            null -> Unit
        }
    }

    override fun dismissPasteDialog() = _uiState.update { it.copy(pasteDialog = null) }

    // A paste replaces the chosen venue, works when editing an old meal too, and like a share never
    // touches the meal's own location.
    private fun startPaste(text: String) {
        dropSharedPlace()
        _uiState.update { it.copy(venue = null) }
        startPlaceRun(text, PlaceRun.Paste())
    }

    /**
     * A place shared from Google Maps (or pasted) becomes this meal's venue, at the position Maps
     * gives — never the phone's fix. Any failure only leaves a notice under the venue row: the editor
     * stays usable and saving is never held up by it.
     */
    private suspend fun openPlace(text: String, run: PlaceRun) {
        _uiState.update { it.copy(resolvingLink = true) }
        try {
            val link = MapsLink.find(text)
            val place = link?.let { readPlace(it) }
            val name = place?.name
            when {
                link == null -> showNotice(ShareNotice.NotAMapLink)
                // A place without a name is unusable: coordinates alone name no venue.
                place == null || name == null -> showNotice(ShareNotice.MapLinkUnreadable)
                else -> applySharedPlace(name, place.position(), run)
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
    private suspend fun applySharedPlace(name: String, position: GeoPoint?, run: PlaceRun) {
        val venues = venueRepository.observeAll().first()
        when (val match = SharedVenue.match(name, position, venues)) {
            is SharedVenueMatch.Create -> createSharedVenue(match)
            is SharedVenueMatch.Use -> useSharedVenue(match)
            is SharedVenueMatch.AskWhichStore -> askWhichStore(name, match, run)
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
    // Asked once per run: a share re-run after process death must not re-ask what was answered.
    private suspend fun askWhichStore(sharedName: String, match: SharedVenueMatch.AskWhichStore, run: PlaceRun) {
        if (run.answered) {
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

    // The user's own venue choice (or saving, or a new paste) wins over a share or paste: once
    // dropped, one still resolving creates, moves or asks nothing, a question still open is
    // withdrawn, and a notice about the shared text has nothing left to explain.
    private fun dropSharedPlace() {
        placeJob?.cancel()
        _uiState.update { it.copy(pendingStore = null, shareNotice = null) }
    }

    // Clear first, so a second tap finds nothing pending; remembered so a re-run share won't re-ask.
    private fun takePendingStore(): EatTimeEditorUiState.PendingStore? {
        val pending = _uiState.value.pendingStore
        if (pending != null) {
            _uiState.update { it.copy(pendingStore = null) }
            placeRun.answered = true
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
        viewModelScope.launch { moveVenue(pending.venue, pending.to) }
    }

    /** Same store, and it stays where it is; it stays selected for this meal. Also what dismissing means. */
    fun answerSameStoreKeep() {
        takePendingStore()
    }

    // Moves the venue's marker for every record at it, and its old address goes with the old position.
    // The meal's own venue choice is left as it is; a selected copy of this venue is only refreshed.
    private suspend fun moveVenue(venue: Venue, to: GeoPoint) {
        venueRepository.setPosition(venue.id, to.lat, to.lng)
        val moved = venue.copy(lat = to.lat, lng = to.lng, address = null)
        showVenueInPlaceOf(venue.id, moved)
        fillAddress(moved)
    }

    /**
     * Moves [venue] to the pin of the Maps link on the clipboard, read now because the user tapped.
     * Only the pin is taken, never the name. A venue with records asks first
     * ([EatTimeEditorUiState.pendingMove]); a link without a pin says so in
     * [EatTimeEditorUiState.repositionNotice]. False when the clipboard held no Maps link: the user is
     * then asked to paste one by hand.
     */
    override suspend fun repositionFromClipboard(venue: Venue): Boolean {
        val text = clipboardReader.text()?.takeIf { MapsLink.find(it) != null }
        if (text != null) {
            startReposition(text, venue)
        } else {
            _uiState.update { it.copy(pasteDialog = PasteTarget.Reposition(venue)) }
        }
        return text != null
    }

    private fun startReposition(text: String, venue: Venue) {
        val previous = repositionJob
        previous?.cancel()
        _uiState.update { it.copy(repositionNotice = null) }
        repositionJob = viewModelScope.launch {
            // A cancelled run still clears repositioning on its way out; let it finish before this one starts.
            previous?.join()
            reposition(text, venue)
        }
    }

    private suspend fun reposition(text: String, venue: Venue) {
        _uiState.update { it.copy(repositioning = true) }
        try {
            val link = MapsLink.find(text)
            val to = link?.let { readPlace(it) }?.position()
            when {
                link == null -> showRepositionNotice(ShareNotice.NotAMapLink)
                to == null -> showRepositionNotice(ShareNotice.MapLinkUnreadable)
                else -> askOrMove(venue, to)
            }
        } finally {
            _uiState.update { it.copy(repositioning = false) }
        }
    }

    private suspend fun askOrMove(venue: Venue, to: GeoPoint) {
        val records = venueRepository.recordCount(venue.id)
        if (records > 0) {
            _uiState.update { it.copy(pendingMove = EatTimeEditorUiState.PendingMove(venue, to, records)) }
        } else {
            moveVenue(venue, to)
        }
    }

    private fun showRepositionNotice(notice: ShareNotice) = _uiState.update { it.copy(repositionNotice = notice) }

    /**
     * The edit dialog is gone: a map link still being read for it moves nothing, and the question,
     * paste dialog or notice it led to goes with it.
     */
    override fun closeVenueEdit() {
        repositionJob?.cancel()
        _uiState.update { state ->
            state.copy(
                repositionNotice = null,
                repositioning = false,
                pendingMove = null,
                pasteDialog = state.pasteDialog.takeUnless { it is PasteTarget.Reposition },
            )
        }
    }

    /** Move the venue to the link's pin — for every record at it. */
    override fun confirmMove() {
        val pending = _uiState.value.pendingMove ?: return
        // Clear first, so a second tap finds nothing pending.
        _uiState.update { it.copy(pendingMove = null) }
        viewModelScope.launch { moveVenue(pending.venue, pending.to) }
    }

    /** Keep the venue where it is; a map link still being read for it moves nothing either. */
    override fun cancelMove() {
        repositionJob?.cancel()
        _uiState.update { it.copy(pendingMove = null) }
    }

    // The suggestion follows the note as it is typed, so the order is recomputed with it.
    private fun EatTimeEditorUiState.withVenueOptions() =
        copy(venueOptions = VenuePicker.order(allVenues, note))

    fun setNote(value: String) = _uiState.update { it.copy(note = value).withVenueOptions() }

    override fun selectVenue(venue: Venue) {
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
    override fun createVenue(name: String) {
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

    /** The picker opened: look up the addresses its venues are missing, without holding anything up. */
    fun onPickerShown() {
        if (backfillJob?.isActive == true) return
        val shown = _uiState.value.venueOptions
        backfillJob = viewModelScope.launch { venueAddressFiller.fill(shown) }
    }

    /**
     * Save the edit dialog: [venue] gets [name] and [address]. When [name] is another venue's (and
     * not [venue]'s own), nothing is saved yet: the user chooses between merging into it — which
     * rewrites other records, so [EatTimeEditorUiState.pendingMerge] carries the count — and keeping
     * both. A name that is blank once normalized is refused.
     */
    override fun saveVenue(venue: Venue, name: String, address: String?) {
        if (VenueName.normalize(name).isEmpty()) return
        viewModelScope.launch {
            val venues = venueRepository.observeAll().first()
            // Gone meanwhile: there is nothing left to save.
            val current = venues.firstOrNull { it.id == venue.id } ?: return@launch
            val into = VenueMerge.target(current, name, venues)
            // Saving what is already stored writes nothing, so the venue does not jump up the list.
            val unchanged = current.name == VenueName.normalize(name) &&
                current.address == address?.trim()?.ifEmpty { null }
            when {
                into != null -> {
                    val pending = EatTimeEditorUiState.PendingMerge(
                        from = current,
                        into = into,
                        movedRecords = venueRepository.recordCount(venue.id),
                        newName = VenueName.normalize(name),
                        newAddress = address,
                    )
                    _uiState.update { it.copy(pendingMerge = pending) }
                }
                !unchanged -> editVenue(venue.id, name, address)
            }
        }
    }

    override suspend fun recordCount(venue: Venue): Int = venueRepository.recordCount(venue.id)

    private suspend fun editVenue(venueId: Int, name: String, address: String?) {
        showVenueInPlaceOf(venueId, venueRepository.edit(venueId, name, address, now()))
    }

    // Clear first: a second tap must find nothing pending, not act on a venue the first one merged away.
    private fun takePendingMerge(): EatTimeEditorUiState.PendingMerge? {
        val pending = _uiState.value.pendingMerge
        if (pending != null) _uiState.update { it.copy(pendingMerge = null) }
        return pending
    }

    /** One place after all: its records move to the other venue, which keeps its own name, address and position. */
    override fun mergeVenues() {
        val pending = takePendingMerge() ?: return
        viewModelScope.launch {
            val survivor = venueRepository.merge(fromId = pending.from.id, intoId = pending.into.id, now = now())
            showVenueInPlaceOf(pending.from.id, survivor)
            // A survivor without a position adopts the merged one's, and needs an address for it.
            fillAddress(survivor)
        }
    }

    /** Two places with one name (branches): just save the edit. */
    override fun keepBothVenues() {
        val pending = takePendingMerge() ?: return
        viewModelScope.launch { editVenue(pending.from.id, pending.newName, pending.newAddress) }
    }

    override fun cancelMerge() = _uiState.update { it.copy(pendingMerge = null) }

    /** Ask before deleting [venue]: the confirmation says how many records use it. */
    override fun requestDelete(venue: Venue) {
        viewModelScope.launch {
            val records = venueRepository.recordCount(venue.id)
            _uiState.update { it.copy(pendingDelete = EatTimeEditorUiState.PendingDelete(venue, records)) }
        }
    }

    /** Delete the venue; its records stay, without a venue — this meal too, if it had that one. */
    override fun confirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        // At once, so a second tap finds nothing and a save meanwhile never stores the deleted venue.
        _uiState.update { state ->
            state.copy(pendingDelete = null, venue = state.venue?.takeUnless { it.id == pending.venue.id })
        }
        viewModelScope.launch { venueRepository.delete(pending.venue.id) }
    }

    override fun cancelDelete() = _uiState.update { it.copy(pendingDelete = null) }

    /**
     * Correct where a venue is, using this record's own fix. Because records read through
     * `venue_id`, this moves the venue's marker for **every past record at once** — the reason a
     * venue is an entity at all. Offered only when this record actually has a position.
     */
    override fun useThisPositionFor(venue: Venue) {
        val fix = _uiState.value.location ?: return
        viewModelScope.launch {
            moveVenue(venue, GeoPoint(fix.lat, fix.lng))
            // The write moves a marker the user is not looking at; say so, or it feels like a no-op.
            _uiState.update { it.copy(message = EditorMessage.VenuePositionUpdated) }
        }
    }

    /** Clear the transient message once the UI has shown it. */
    override fun consumeMessage() = _uiState.update { it.copy(message = null) }

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
