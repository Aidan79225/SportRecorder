package com.crazystudio.sportrecorder.domain.model

/**
 * A place food came from. [lat]/[lng] are null until something teaches the venue where it is —
 * the first record captured there, or a Google Maps URL (#84).
 *
 * A venue is NOT where the user was: that stays on [EatRecord.location]. Takeaway eaten at home is
 * "at home + that restaurant", and both facts are kept.
 */
data class Venue(
    val id: Int,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val lastUsedAt: Long,
)

/** How two venue names are compared and stored. Pure, so it is identical on every platform. */
object VenueName {
    /** Trimmed, with runs of whitespace collapsed. Case is the user's and is left alone. */
    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE_RUN, " ")

    /** Uniqueness is case-insensitive: `Starbucks` and `starbucks` are one venue. */
    fun sameAs(a: String, b: String): Boolean =
        normalize(a).equals(normalize(b), ignoreCase = true)

    private val WHITESPACE_RUN = Regex("\\s+")
}
