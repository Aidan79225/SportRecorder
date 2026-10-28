package com.crazystudio.sportrecorder.domain.model

/**
 * A place food came from. [lat]/[lng] are null until something teaches the venue where it is —
 * the first record captured there, or a Google Maps URL (#84).
 *
 * A venue is NOT where the user was: that stays on [EatRecord.location]. Takeaway eaten at home is
 * "at home + that restaurant", and both facts are kept.
 *
 * Names are not unique: two branches of a chain are two venues with the same [name], told apart by
 * [address] (#89). Identity is [id]. [address] is a short human-readable line, null when unknown;
 * it describes the current position, so it goes with that position: a move (or a venue adopting its
 * first position in findOrCreate) replaces it with the address the move brings — a Maps link's own —
 * and clears it when none is given, as a merge that adopts a position always does. Re-applying the
 * same position is no move and keeps it. Otherwise an address is only filled in where there is none,
 * or changed by the user's own edit.
 */
data class Venue(
    val id: Int,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val lastUsedAt: Long,
    val address: String? = null,
) {
    /** Where the venue is, or null until it learns that. */
    val position: GeoPoint?
        get() = if (lat != null && lng != null) GeoPoint(lat, lng) else null
}

/** How two venue names are compared and stored. Pure, so it is identical on every platform. */
object VenueName {
    /**
     * Trimmed, with runs of whitespace (including Unicode whitespace like U+3000) collapsed
     * to single ASCII spaces. Case is the user's and is left alone.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val result = StringBuilder()
        var lastWasWhitespace = false
        for (c in trimmed) {
            if (c.isWhitespace()) {
                if (!lastWasWhitespace) {
                    result.append(' ')
                    lastWasWhitespace = true
                }
            } else {
                result.append(c)
                lastWasWhitespace = false
            }
        }
        return result.toString()
    }

    /**
     * Whether two names are the same name, case-insensitively: `Starbucks` and `starbucks`. Since
     * #89 the same name no longer means the same venue (branches share a name).
     */
    fun sameAs(a: String, b: String): Boolean =
        normalize(a).equals(normalize(b), ignoreCase = true)
}

/** How a venue's address is stored. Pure, so it is identical on every platform. */
object VenueAddress {
    /** [raw] as stored: trimmed, and blank is none. */
    fun clean(raw: String?): String? = raw?.trim()?.ifEmpty { null }
}
