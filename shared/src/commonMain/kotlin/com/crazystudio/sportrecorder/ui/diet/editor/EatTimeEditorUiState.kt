package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.venue.RoundedDistance

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
    /** True while a place shared from Google Maps is being read. Never blocks saving. */
    val resolvingLink: Boolean = false,
    /** Set while a venue would move to a place from a map link; the UI asks before it happens. */
    val pendingMove: PendingMove? = null,
    /** Set while a shared place has the name of a venue that is far away: another branch, or the same store? */
    val pendingStore: PendingStore? = null,
    /** Why a shared text gave no venue; shown under the venue row until the user picks one themselves. */
    val shareNotice: ShareNotice? = null,
    /** A transient result the UI shows once, then clears via `consumeMessage`. */
    val message: EditorMessage? = null,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }

    /** A rename that collides with an existing name: [movedRecords] records would change hands. */
    data class PendingMerge(val from: Venue, val intoName: String, val movedRecords: Int)

    /** A map link puts [venue] at [to], far from where it is: [movedRecords] records' marker would move. */
    data class PendingMove(val venue: Venue, val to: GeoPoint, val movedRecords: Int)

    /**
     * A place named [sharedName] was shared at [to], [distance] from [venue], the nearest saved venue
     * by that name. Another branch creates a venue at [to]; the same store either moves [venue] there
     * (and with it the marker of its [movedRecords] records) or keeps it where it is.
     */
    data class PendingStore(
        val venue: Venue,
        val to: GeoPoint,
        val distance: RoundedDistance,
        val movedRecords: Int,
        val sharedName: String,
    )
}

/** Semantic result — the UI maps each to a localized string (the VM holds no user-facing copy). */
enum class EditorMessage {
    /** A venue's marker moved for every record at it; nothing else on screen would show that. */
    VenuePositionUpdated,
}

/** Why a shared text left the venue empty — the UI maps each to a localized line (#84). */
enum class ShareNotice {
    /** Shared text held a Maps link, but no named place could be read from it. */
    MapLinkUnreadable,

    /** Shared text held no Google Maps link at all. */
    NotAMapLink,
}
