package com.crazystudio.sportrecorder.ui.diet.editor

import com.crazystudio.sportrecorder.domain.model.Venue

/**
 * What the venue picker and its dialogs ask of their host — [EatTimeEditorViewModel] in the app, a
 * recording fake in UI tests. Each call is one user action; the outcome comes back through
 * [EatTimeEditorUiState].
 */
@Suppress("TooManyFunctions") // one handler per picker interaction
interface VenuePickerActions {
    fun selectVenue(venue: Venue)

    fun createVenue(name: String)

    /** True when the clipboard held a Maps link and its place is being opened; else the paste dialog asks. */
    suspend fun pasteMapsLink(): Boolean

    fun pasteText(text: String)

    fun dismissPasteDialog()

    fun consumeMessage()

    /** How many records use [venue], for the edit dialog's position line. */
    suspend fun recordCount(venue: Venue): Int

    fun useThisPositionFor(venue: Venue)

    /** True when the clipboard held a Maps link and [venue] is being moved to its pin; else the paste dialog asks. */
    suspend fun repositionFromClipboard(venue: Venue): Boolean

    /** The edit dialog closed: drops a map link still being read for it, and what that link asked or said. */
    fun closeVenueEdit()

    fun confirmMove()

    fun cancelMove()

    fun saveVenue(venue: Venue, name: String, address: String?)

    fun mergeVenues()

    fun keepBothVenues()

    fun cancelMerge()

    fun requestDelete(venue: Venue)

    fun confirmDelete()

    fun cancelDelete()
}
