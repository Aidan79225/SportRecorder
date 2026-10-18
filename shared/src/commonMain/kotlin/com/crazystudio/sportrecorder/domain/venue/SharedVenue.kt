package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.domain.model.VenueName
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val DEGREES_PER_HALF_TURN = 180.0
private const val HALF = 2.0
private const val METERS_PER_KILOMETER = 1000
private const val METERS_PER_TENTH_KILOMETER = 100

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

    /** Same name, but every same-name venue is more than 50 m away: ask about [venue], the nearest, [meters] off. */
    data class AskWhichStore(val venue: Venue, val to: GeoPoint, val meters: Double) : SharedVenueMatch
}

/** A distance as the user reads it: whole metres below a kilometre, else kilometres to one decimal. */
sealed interface RoundedDistance {
    data class Meters(val value: Int) : RoundedDistance

    /** [tenths] of a kilometre: 1.8 km is 18. */
    data class Kilometers(val tenths: Int) : RoundedDistance
}

fun roundedDistance(meters: Double): RoundedDistance {
    val wholeMeters = meters.roundToInt()
    // 999.6 m rounds up to 1000, which would read "1000 m": it is already a kilometre.
    return if (wholeMeters < METERS_PER_KILOMETER) {
        RoundedDistance.Meters(wholeMeters)
    } else {
        RoundedDistance.Kilometers((meters / METERS_PER_TENTH_KILOMETER).roundToInt())
    }
}

object SharedVenue {
    /** Within this many metres (inclusive) two positions are the same place. */
    const val SAME_PLACE_METERS = 50.0

    /**
     * [name] is the already-normalized Maps name; venues are matched by [VenueName.sameAs], and several
     * venues may share a name (branches), so the pin, not the name, says which one is meant.
     */
    fun match(name: String, position: GeoPoint?, venues: List<Venue>): SharedVenueMatch {
        val sameName = venues.filter { VenueName.sameAs(it.name, name) }
        return when {
            sameName.isEmpty() -> SharedVenueMatch.Create(name, position)
            // No pin to tell the branches apart: the one used most recently.
            position == null -> SharedVenueMatch.Use(sameName.mostRecentlyUsed(), fillPosition = null)
            else -> matchByPlace(sameName, position)
        }
    }

    private fun matchByPlace(sameName: List<Venue>, pin: GeoPoint): SharedVenueMatch {
        val nearest = sameName.nearestTo(pin)
        return when {
            // Every same-name venue is still waiting to learn where it is.
            nearest == null -> SharedVenueMatch.Use(sameName.mostRecentlyUsed(), fillPosition = pin)
            nearest.second <= SAME_PLACE_METERS -> SharedVenueMatch.Use(nearest.first, fillPosition = null)
            else -> {
                val unplaced = sameName.filter { it.lat == null || it.lng == null }.maxByOrNull { it.lastUsedAt }
                if (unplaced != null) {
                    SharedVenueMatch.Use(unplaced, fillPosition = pin)
                } else {
                    SharedVenueMatch.AskWhichStore(nearest.first, to = pin, meters = nearest.second)
                }
            }
        }
    }

    /** The venue with a position closest to [pin], with its distance; null when none has a position. */
    private fun List<Venue>.nearestTo(pin: GeoPoint): Pair<Venue, Double>? =
        mapNotNull { venue ->
            val lat = venue.lat
            val lng = venue.lng
            if (lat != null && lng != null) venue to GeoPoint(lat, lng).metersTo(pin) else null
        }.minByOrNull { it.second }

    /** Only called on non-empty lists. */
    private fun List<Venue>.mostRecentlyUsed(): Venue = maxBy { it.lastUsedAt }
}
