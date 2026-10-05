package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName

/**
 * The order the venue picker shows: most recently used first, because eating has strong recency.
 *
 * When the record's [note] already contains the name of a venue the user created, that venue is
 * offered first. This is a plain substring match, never an inference: the app does not decide what
 * the user meant, it only puts a name they already wrote within one tap.
 */
object VenuePicker {

    fun order(venues: List<Venue>, note: String?): List<Venue> {
        val byRecency = venues.sortedByDescending { it.lastUsedAt }
        val haystack = note?.let { VenueName.normalize(it) }?.lowercase()
        if (haystack.isNullOrEmpty()) return byRecency
        // The most specific match wins: "大戶屋 信義店" beats "大戶屋".
        val suggested = byRecency
            .filter { haystack.contains(VenueName.normalize(it.name).lowercase()) }
            .maxByOrNull { VenueName.normalize(it.name).length }
            ?: return byRecency
        return listOf(suggested) + byRecency.filterNot { it.id == suggested.id }
    }
}
