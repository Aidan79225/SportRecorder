package com.crazystudio.sportrecorder.platform

import com.crazystudio.sportrecorder.domain.model.GeoPoint

/**
 * Turns a position into a short, human-readable address, and an address into a position.
 * Platform-specific (Android: Geocoder). [addressAt] only ever describes a venue. [positionOf] places
 * one, and its estimate is then used as the pin a pinless Maps link lacks: it picks which same-name
 * venue such a link means (`SharedVenue.match`), and it can decide the glued-venue repair's automatic
 * merge (`GluedVenueRepair`: the same name within `SharedVenue.SAME_PLACE_METERS`). That merge is the
 * caveat in the 初衷對照 / North-Star check of the 2026-10-28 addendum to
 * `docs/superpowers/specs/2026-10-18-venue-branches-design.md`.
 */
interface AddressLookup {
    /** Short address at [point], or null on any failure. Never throws. */
    suspend fun addressAt(point: GeoPoint): String?

    /**
     * Where [query] is — the address a Maps link carries when it has no pin — or null on any failure.
     * Never throws. It asks the same platform service as [addressAt], with the link's own text: never
     * the user's position.
     */
    suspend fun positionOf(query: String): GeoPoint?
}
