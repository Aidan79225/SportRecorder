package com.crazystudio.sportrecorder.domain.model

/**
 * A single eating record. [id] == 0 means not-yet-persisted.
 * [time] is epoch millis. [photos] is empty when loaded without its relation.
 */
data class EatRecord(
    val id: Int,
    val time: Long,
    val location: GeoPoint?,
    val note: String?,
    val photos: List<EatPhoto>,
    /** Where the food came from. Null when unknown — it is never required. */
    val venue: Venue? = null,
)

data class GeoPoint(val lat: Double, val lng: Double)

data class EatPhoto(val id: Int, val fileName: String, val createdAt: Long)
