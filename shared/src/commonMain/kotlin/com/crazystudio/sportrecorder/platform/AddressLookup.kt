package com.crazystudio.sportrecorder.platform

import com.crazystudio.sportrecorder.domain.model.GeoPoint

/**
 * Turns a position into a short, human-readable address. Platform-specific (Android: Geocoder).
 * Used only to describe where a venue is, never to decide anything about it.
 */
interface AddressLookup {
    /** Short address at [point], or null on any failure. Never throws. */
    suspend fun addressAt(point: GeoPoint): String?
}
