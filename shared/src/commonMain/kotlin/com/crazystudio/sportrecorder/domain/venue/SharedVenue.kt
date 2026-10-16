package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEGREES_PER_HALF_TURN = 180.0
private const val HALF = 2.0

/** Great-circle distance by the haversine formula. Longitude wraps, so the antimeridian is no special case. */
fun GeoPoint.metersTo(other: GeoPoint): Double {
    val toRadians = PI / DEGREES_PER_HALF_TURN
    val dLat = (other.lat - lat) * toRadians
    val dLng = (other.lng - lng) * toRadians
    val h = sin(dLat / HALF) * sin(dLat / HALF) +
        cos(lat * toRadians) * cos(other.lat * toRadians) * sin(dLng / HALF) * sin(dLng / HALF)
    return EARTH_RADIUS_METERS * HALF * atan2(sqrt(h), sqrt(1.0 - h))
}

/** What to do with a place shared from Google Maps, given the venues the user already has. */
sealed interface SharedVenueMatch {
    /** No venue by that name yet: create one, at [position] when the link carried it. */
    data class Create(val name: String, val position: GeoPoint?) : SharedVenueMatch

    /** The venue exists and is right as it is; [fillPosition] teaches a position-less venue where it is. */
    data class Use(val venue: Venue, val fillPosition: GeoPoint?) : SharedVenueMatch

    /** The venue exists but sits somewhere else: the user decides whether to move it to [to]. */
    data class AskToMove(val venue: Venue, val to: GeoPoint) : SharedVenueMatch
}

object SharedVenue {
    /** Within this many metres (inclusive) two positions are the same place. */
    const val SAME_PLACE_METERS = 50.0

    /** [name] is the already-normalized Maps name; venues are matched by [VenueName.sameAs]. */
    fun match(name: String, position: GeoPoint?, venues: List<Venue>): SharedVenueMatch {
        val existing = venues.firstOrNull { VenueName.sameAs(it.name, name) }
        return if (existing == null) {
            SharedVenueMatch.Create(name, position)
        } else {
            matchExisting(existing, position)
        }
    }

    private fun matchExisting(venue: Venue, position: GeoPoint?): SharedVenueMatch {
        val lat = venue.lat
        val lng = venue.lng
        return when {
            position == null -> SharedVenueMatch.Use(venue, fillPosition = null)
            lat == null || lng == null -> SharedVenueMatch.Use(venue, fillPosition = position)
            GeoPoint(lat, lng).metersTo(position) <= SAME_PLACE_METERS ->
                SharedVenueMatch.Use(venue, fillPosition = null)
            else -> SharedVenueMatch.AskToMove(venue, to = position)
        }
    }
}
