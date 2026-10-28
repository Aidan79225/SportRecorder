package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import com.crazystudio.sportrecorder.platform.AddressLookup
import kotlinx.coroutines.flow.first

/**
 * Repairs the venues made from links of the Google Maps apps before those links could be read: they
 * were named by the whole title, address and name glued together —
 * 「106台灣臺北市大安區福住里信義路二段194號鼎泰豐 信義店」 — with no address and no position (#89
 * addendum). Run once per app start, in the background.
 *
 * Only a name [PlaceTitle.split] is confident about is touched: the same split a paste of that link
 * makes today, so a name the user typed (「85度C」, 「7-11 信義店」) never is. The venue is renamed to
 * the place's name and keeps its own address, else takes the link's trimmed one. One with no position
 * is placed where the link's full address looks up to; a failed lookup is not retried, since the name
 * is no longer glued at the next start. If another venue with the same name then lies within
 * [SharedVenue.SAME_PLACE_METERS], they are the same store and the repaired one is merged into it, so
 * its records move and nothing is lost; otherwise both stay, as branches.
 *
 * A repair is not a use: lastUsedAt is kept, so the picker's order stays as it was. The lookup comes
 * before any write, so a process that ends while it waits leaves the venue glued for the next start;
 * and the venue is read again after it, so a rename or move the user made meanwhile is never undone.
 */
class GluedVenueRepair(
    private val venues: VenueRepository,
    private val lookup: AddressLookup,
) {
    /** Repairs every glued venue, one at a time. A second run finds none left, so it changes nothing. */
    suspend fun run() {
        venues.observeAll().first()
            .filter { PlaceTitle.split(it.name) != null }
            .forEach { stale -> current(stale.id)?.let { repair(it) } }
    }

    private suspend fun repair(glued: Venue) {
        val split = PlaceTitle.split(glued.name) ?: return
        val point = if (glued.position == null) lookup.locate(split.fullAddress) else null
        // Renamed by the user while the lookup ran (or deleted): theirs now, left alone.
        val venue = current(glued.id)?.takeIf { it.name == glued.name } ?: return
        val address = venue.address ?: split.address
        venues.edit(venue.id, split.name, address, now = venue.lastUsedAt)
        if (point != null && venue.position == null) venues.setPosition(venue.id, point.lat, point.lng, address)
        mergeIntoTheSameStore(venue.id)
    }

    /** The survivor keeps its own name and position, and takes the address it lacked and the later use. */
    private suspend fun mergeIntoTheSameStore(venueId: Int) {
        val all = venues.observeAll().first()
        val repaired = all.firstOrNull { it.id == venueId } ?: return
        val store = SharedVenue.sameStore(repaired, all) ?: return
        venues.merge(fromId = venueId, intoId = store.id, now = maxOf(repaired.lastUsedAt, store.lastUsedAt))
        repaired.address?.let { venues.setAddressIfEmpty(store.id, it) }
    }

    private suspend fun current(venueId: Int): Venue? = venues.observeAll().first().firstOrNull { it.id == venueId }
}
