package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName

/** Renaming a venue onto a name another venue already has (#89): which one a merge would go into. */
object VenueMerge {
    /**
     * The venue [venue] would merge into if renamed to [newName], or null when that is no merge: the
     * name is [venue]'s own (same branch, new spelling or address) or no other venue has it. Of several
     * namesakes, the nearest to [venue] when both have a position, otherwise the most recently used.
     */
    fun target(venue: Venue, newName: String, venues: List<Venue>): Venue? {
        val namesakes = venues.filter { it.id != venue.id && VenueName.sameAs(it.name, newName) }
        return when {
            VenueName.sameAs(venue.name, newName) || namesakes.isEmpty() -> null
            else -> venue.position?.let { namesakes.nearestTo(it)?.first } ?: namesakes.maxBy { it.lastUsedAt }
        }
    }
}
