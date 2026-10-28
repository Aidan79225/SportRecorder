package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueAddress
import com.crazystudio.sportrecorder.domain.repository.VenueRepository
import com.crazystudio.sportrecorder.platform.AddressLookup

/**
 * Fills in the address of venues that have a position but no address yet. A result is written
 * only if the venue is still at the position that was looked up and still has no address, so a
 * lookup that finishes after the venue moved (or was edited) is dropped.
 */
class VenueAddressFiller(
    private val lookup: AddressLookup,
    private val venues: VenueRepository,
) {
    /**
     * For each venue with a position and no address, look it up and write it with
     * setAddressIfUnchanged. Sequential; failures skipped.
     */
    suspend fun fill(venues: List<Venue>) {
        venues.forEach { venue -> fillOne(venue) }
    }

    private suspend fun fillOne(venue: Venue) {
        val lat = venue.lat
        val lng = venue.lng
        if (lat == null || lng == null || venue.address != null) return
        val address = VenueAddress.clean(lookup.addressAt(GeoPoint(lat, lng)))
        if (address != null) venues.setAddressIfUnchanged(venue.id, lat, lng, address)
    }
}
