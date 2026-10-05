package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.Venue

data class EatTimeEditorUiState(
    val dateMillis: Long = 0L, // the meal's date+time as epoch millis; formatted for display
    val isEditMode: Boolean = false,
    val note: String = "",
    val existingPhotos: List<EatPhoto> = emptyList(), // already-saved photos (edit mode)
    val pendingPhotos: List<String> = emptyList(), // newly captured webp file names
    val location: LatLng? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    /** The venue attached to this record, or null — always optional. */
    val venue: Venue? = null,
    /** Picker contents, already ordered by [com.crazystudio.sportrecorder.domain.venue.VenuePicker]. */
    val venueOptions: List<Venue> = emptyList(),
    /** Set while a rename would merge two venues; the UI must show the count before it happens. */
    val pendingMerge: PendingMerge? = null,
    /** A transient result the UI shows once, then clears via `consumeMessage`. */
    val message: EditorMessage? = null,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }

    /** A rename that collides with an existing name: [movedRecords] records would change hands. */
    data class PendingMerge(val from: Venue, val intoName: String, val movedRecords: Int)
}

/** Semantic result — the UI maps each to a localized string (the VM holds no user-facing copy). */
enum class EditorMessage {
    /** A venue's marker moved for every record at it; nothing else on screen would show that. */
    VenuePositionUpdated,
}
