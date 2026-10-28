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
    /** Set while a rename takes another venue's name: merge into it, or keep both? The UI shows the count. */
    val pendingMerge: PendingMerge? = null,
    /** Set while the user is asked to confirm deleting a venue; the UI shows how many records use it. */
    val pendingDelete: PendingDelete? = null,
    /** True while a place shared from Google Maps is being read. Never blocks saving. */
    val resolvingLink: Boolean = false,
    /** Set while a venue with records would move to the pin of a map link; the picker asks before it happens. */
    val pendingMove: PendingMove? = null,
    /** Set while a shared place has the name of a venue that is far away: another branch, or the same store? */
    val pendingStore: PendingStore? = null,
    /** Why a shared text gave no venue; shown under the venue row until the user picks one themselves. */
    val shareNotice: ShareNotice? = null,
    /** Set while the user is asked to paste a Maps link by hand: the clipboard held none (#89). */
    val pasteDialog: PasteTarget? = null,
    /** Why a map link moved no venue; shown inside the edit dialog, never under the editor's venue row. */
    val repositionNotice: RepositionNotice? = null,
    /** True while the edit dialog's map link is being read; said inside that dialog, whose 儲存 waits for it. */
    val repositioning: Boolean = false,
    /** What the edit dialog's map link offers its drafts; the dialog takes it once via `consumeRepositionDraft`. */
    val repositionDraft: RepositionDraft? = null,
    /** A transient result the UI shows once, then clears via `consumeMessage`. */
    val message: EditorMessage? = null,
) {
    data class LatLng(val lat: Double, val lng: Double)
    enum class LocationStatus { IDLE, LOADING, AVAILABLE, UNAVAILABLE }

    /**
     * Saving [from] as [newName] / [newAddress] takes the name of [into]: merging moves [from]'s
     * [movedRecords] records to [into] (which keeps its own name, address and position); keeping both
     * just saves the edit.
     */
    data class PendingMerge(
        val from: Venue,
        val into: Venue,
        val movedRecords: Int,
        val newName: String,
        val newAddress: String?,
    )

    /** Deleting [venue] leaves its [records] records without a venue; none of them is deleted. */
    data class PendingDelete(val venue: Venue, val records: Int)

    /**
     * A map link puts [venue] at [to], far from where it is: [movedRecords] records' marker would move.
     * The venue takes [address], the link's own, with the move; without one it is looked up again.
     */
    data class PendingMove(val venue: Venue, val to: GeoPoint, val movedRecords: Int, val address: String? = null)

    /**
     * The map link read by 「貼上地圖連結更新」 for venue [venueId] names it [name], which fills the
     * edit dialog's 店名 draft (儲存 keeps it, 取消 the old one). [address] fills the 地址 draft only
     * when the link could not be placed: a link that moves the venue writes its address with the move.
     */
    data class RepositionDraft(val venueId: Int, val name: String, val address: String? = null)

    /**
     * A place named [sharedName] was shared at [to], [distance] from [venue], the nearest saved venue
     * by that name. Another branch creates a venue at [to]; the same store either moves [venue] there
     * (and with it the marker of its [movedRecords] records) or keeps it where it is. Either way a venue
     * that ends up at [to] gets [sharedAddress], the link's own address, when it carried one.
     */
    data class PendingStore(
        val venue: Venue,
        val to: GeoPoint,
        val distance: RoundedDistance,
        val movedRecords: Int,
        val sharedName: String,
        val sharedAddress: String? = null,
    )
}

/** What a Maps link pasted by hand is for (#89). */
sealed interface PasteTarget {
    /** Opens the place as this meal's venue, like a share does. */
    data object Select : PasteTarget

    /** Puts [venue] at the place's position. */
    data class Reposition(val venue: Venue) : PasteTarget
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

/**
 * Why the edit dialog's map link (「貼上地圖連結更新」) moved no venue — the UI maps each to a localized
 * line inside that dialog (#89).
 */
enum class RepositionNotice {
    /** The text held no Google Maps link at all. */
    NotAMapLink,

    /** It held a Maps link, but no place could be read from it. */
    Unreadable,

    /**
     * The link names a place whose position could not be found: its name and address fill the drafts,
     * and nothing moves. (A share needs no such line: a venue can do without a position.)
     */
    NotLocated,

    /**
     * As [NotLocated], for a link whose title could not be split into a name and an address: that whole
     * title is no 店名, so nothing fills the drafts, and nothing moves.
     */
    NotLocatedUnchanged,
}
