package com.crazystudio.sportrecorder.domain.model

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/** A position on the Earth, in degrees. */
data class GeoPoint(val lat: Double, val lng: Double) {

    /** Whether this can be a real place: latitude within ±90°, longitude within ±180°. */
    val isOnEarth: Boolean
        get() = lat in -MAX_LATITUDE..MAX_LATITUDE && lng in -MAX_LONGITUDE..MAX_LONGITUDE

    companion object {
        /** [lat]/[lng] as a point, or null when either is missing or the point is not [isOnEarth]. */
        fun orNull(lat: Double?, lng: Double?): GeoPoint? =
            if (lat != null && lng != null) GeoPoint(lat, lng).takeIf { it.isOnEarth } else null
    }
}
