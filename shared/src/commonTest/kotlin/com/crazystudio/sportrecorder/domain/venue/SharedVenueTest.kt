package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import kotlin.math.abs
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NAME = "鼎泰豐 信義店"
private const val METERS_PER_DEGREE_LAT = 111_194.9266
private val MAPS = GeoPoint(25.03, 121.53)

private fun venue(position: GeoPoint?, name: String = NAME) =
    Venue(id = 1, name = name, lat = position?.lat, lng = position?.lng, lastUsedAt = 0L)

/** [meters] due north of [MAPS], i.e. the venue's stored position when the user's pin is that far off. */
private fun northOfMaps(meters: Double) = GeoPoint(MAPS.lat + meters / METERS_PER_DEGREE_LAT, MAPS.lng)

class SharedVenueTest {

    @Test fun noSuchVenue_createsItWithTheMapsPosition() {
        val result = SharedVenue.match(NAME, MAPS, emptyList())
        assertEquals(SharedVenueMatch.Create(NAME, MAPS), result)
    }

    @Test fun matchesByVenueNameRules() {
        val existing = venue(MAPS)
        val result = SharedVenue.match(" 鼎泰豐  信義店 ", MAPS, listOf(existing))
        assertEquals(SharedVenueMatch.Use(existing, fillPosition = null), result)
    }

    @Test fun existingWithoutAPosition_getsTheMapsOne() {
        val existing = venue(null)
        val result = SharedVenue.match(NAME, MAPS, listOf(existing))
        assertEquals(SharedVenueMatch.Use(existing, fillPosition = MAPS), result)
    }

    @Test fun existingWithinFiftyMetres_isUsedUntouched() {
        val existing = venue(northOfMaps(30.0))
        val result = SharedVenue.match(NAME, MAPS, listOf(existing))
        assertEquals(SharedVenueMatch.Use(existing, fillPosition = null), result)
    }

    @Test fun existingFurtherThanFiftyMetres_asksToMove() {
        val existing = venue(northOfMaps(120.0))
        val result = SharedVenue.match(NAME, MAPS, listOf(existing))
        assertEquals(SharedVenueMatch.AskToMove(existing, to = MAPS), result)
    }

    @Test fun exactlyFiftyMetres_countsAsTheSamePlace() {
        val origin = GeoPoint(0.0, 0.0)
        val existing = venue(origin)
        // Walk east one float step at a time to the very edge of the 50 m circle.
        var lng = 50.0 / METERS_PER_DEGREE_LAT
        while (origin.metersTo(GeoPoint(0.0, lng)) > SharedVenue.SAME_PLACE_METERS) lng = lng.nextDown()
        while (origin.metersTo(GeoPoint(0.0, lng.nextUp())) <= SharedVenue.SAME_PLACE_METERS) lng = lng.nextUp()
        val lastInside = GeoPoint(0.0, lng)
        val firstOutside = GeoPoint(0.0, lng.nextUp())

        assertEquals(
            SharedVenueMatch.Use(existing, fillPosition = null),
            SharedVenue.match(NAME, lastInside, listOf(existing)),
        )
        assertEquals(
            SharedVenueMatch.AskToMove(existing, to = firstOutside),
            SharedVenue.match(NAME, firstOutside, listOf(existing)),
        )
    }

    @Test fun sharedLinkWithoutCoordinates_neverAsks() {
        val existing = venue(northOfMaps(5_000.0))
        val result = SharedVenue.match(NAME, null, listOf(existing))
        assertEquals(SharedVenueMatch.Use(existing, fillPosition = null), result)
    }

    @Test fun noSuchVenue_withoutCoordinates_createsItPositionless() {
        assertEquals(SharedVenueMatch.Create(NAME, null), SharedVenue.match(NAME, null, emptyList()))
    }

    @Test fun distanceIsCorrectAcrossTheAntimeridian() {
        val metres = GeoPoint(0.0, 179.9995).metersTo(GeoPoint(0.0, -179.9995))
        assertTrue(metres < 120.0, "was $metres")
    }

    @Test fun distanceSanity() {
        val metres = GeoPoint(0.0, 0.0).metersTo(GeoPoint(1.0, 0.0))
        assertTrue(abs(metres - 111_195.0) <= 50.0, "was $metres")
    }
}
