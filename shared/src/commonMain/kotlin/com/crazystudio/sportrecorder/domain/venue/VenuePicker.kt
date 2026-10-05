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

    /**
     * What the picker's search box shows: venues whose name contains [query], in the order given.
     * Both sides are normalized first, so stray or full-width (U+3000) spaces never hide a match,
     * and case is ignored. A blank query matches everything.
     */
    fun matching(venues: List<Venue>, query: String): List<Venue> {
        val typed = VenueName.normalize(query)
        if (typed.isEmpty()) return venues
        return venues.filter { VenueName.normalize(it.name).contains(typed, ignoreCase = true) }
    }

    /**
     * Whether to offer "use <query>" as a new venue: only for a name that is not blank once
     * normalized and is not already one of [venues] (names are unique case-insensitively).
     */
    fun canCreate(venues: List<Venue>, query: String): Boolean {
        val typed = VenueName.normalize(query)
        return typed.isNotEmpty() && venues.none { VenueName.sameAs(it.name, typed) }
    }
}
