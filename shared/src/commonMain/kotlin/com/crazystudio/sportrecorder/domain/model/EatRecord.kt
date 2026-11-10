package com.crazystudio.sportrecorder.domain.model

/**
 * A single eating record. [id] == 0 means not-yet-persisted.
 * [time] is epoch millis. [photos] is empty when loaded without its relation.
 * [durationMinutes] is how long the meal lasted; [endTime] is when it ended.
 */
data class EatRecord(
    val id: Int,
    val time: Long,
    val location: GeoPoint?,
    val note: String?,
    val photos: List<EatPhoto>,
    /** Where the food came from. Null when unknown — it is never required. */
    val venue: Venue? = null,
    /** How long the meal lasted. Never asked for: every way of recording gets the default hour. */
    val durationMinutes: Int = MealDuration.DEFAULT_MINUTES,
) {
    /** Epoch millis when the meal ended: [time] plus [durationMinutes]. */
    val endTime: Long get() = time + durationMinutes * MealDuration.MILLIS_PER_MINUTE
}

data class EatPhoto(val id: Int, val fileName: String, val createdAt: Long)
