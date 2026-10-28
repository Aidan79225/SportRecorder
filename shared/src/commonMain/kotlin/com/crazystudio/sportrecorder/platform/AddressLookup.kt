package com.crazystudio.sportrecorder.platform

import com.crazystudio.sportrecorder.domain.model.GeoPoint

/**
 * Turns a position into a short, human-readable address, and an address into a position.
 * Platform-specific (Android: Geocoder). Used only to describe or place a venue, never to decide
 * anything about it.
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
