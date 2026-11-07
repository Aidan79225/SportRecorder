package com.crazystudio.sportrecorder.ui.diet.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazystudio.sportrecorder.data.PhotoImageSource
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.usecase.DeleteEatRecordUseCase
import com.crazystudio.sportrecorder.domain.usecase.ObserveEatRecordsUseCase
import com.crazystudio.sportrecorder.domain.venue.RecordVenueFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DietRecordViewModel constructor(
    observeEatRecords: ObserveEatRecordsUseCase,
    private val deleteEatRecord: DeleteEatRecordUseCase,
    private val photoImageSource: PhotoImageSource,
) : ViewModel() {

    // The one observation of the repository: records, hasVenues and showSearch all derive from it, so the
    // database is observed once however many of them are collected.
    private val allRecords: StateFlow<List<EatRecord>> = observeEatRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
    private val _query = MutableStateFlow("")

    /** What the search field holds. Plain in-memory state: not saved, and gone with this ViewModel. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** What the list shows: every record, or those matching [query]. Newest first, as the repository gives them. */
    val records: StateFlow<List<EatRecord>> = combine(allRecords, _query) { all, text ->
        RecordVenueFilter.matching(all, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * True when at least one record (before filtering) has a venue, so there is something to search.
     * Counted before filtering, so a search that matches nothing does not change it. The search field
     * follows [showSearch], which builds on this.
     */
    val hasVenues: StateFlow<Boolean> = allRecords
        .map { all -> all.any { it.venue != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /**
     * Whether the search field shows: when some record has a venue ([hasVenues]), and also for as long as
     * the field holds text. Otherwise deleting the last record with a venue during a search would take the
     * field away while its text still filters the list, leaving no way to clear it.
     */
    val showSearch: StateFlow<Boolean> = combine(hasVenues, _query) { anyVenue, text ->
        anyVenue || text.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Fills the search field with [venue]'s name and address, so the list shows that venue's records. */
    fun searchVenue(venue: Venue) = setQuery(RecordVenueFilter.queryFor(venue))

    fun deleteRecord(record: EatRecord) {
        viewModelScope.launch { deleteEatRecord(record.id) }
    }

    /** Resolves a stored photo's file name into a Coil-loadable model for the UI. */
    fun photoModel(fileName: String): Any? = photoImageSource.modelFor(fileName)

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
