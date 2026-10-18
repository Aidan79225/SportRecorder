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

private fun venue(position: GeoPoint?, name: String = NAME, id: Int = 1, lastUsedAt: Long = 0L) =
    Venue(id = id, name = name, lat = position?.lat, lng = position?.lng, lastUsedAt = lastUsedAt)

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

    @Test fun noSameName_creates() {
        val other = venue(MAPS, name = "路易莎")
        val result = SharedVenue.match(NAME, MAPS, listOf(other))
        assertEquals(SharedVenueMatch.Create(NAME, MAPS), result)
    }

    @Test fun nearestWithinFiftyMetres_isUsed() {
        val first = venue(northOfMaps(5_000.0), id = 1)
        val second = venue(northOfMaps(20.0), id = 2)
        val result = SharedVenue.match(NAME, MAPS, listOf(first, second))
        assertEquals(SharedVenueMatch.Use(second, fillPosition = null), result)
    }

    @Test fun noneNear_butOneWithoutPosition_isUsedAndGetsThePin() {
        val far = venue(northOfMaps(5_000.0), id = 1, lastUsedAt = 900L)
        val olderUnplaced = venue(null, id = 2, lastUsedAt = 100L)
        val recentUnplaced = venue(null, id = 3, lastUsedAt = 500L)
        val result = SharedVenue.match(NAME, MAPS, listOf(far, olderUnplaced, recentUnplaced))
        assertEquals(SharedVenueMatch.Use(recentUnplaced, fillPosition = MAPS), result)
    }

    @Test fun allFar_asksAboutTheNearest() {
        val farther = venue(northOfMaps(5_000.0), id = 1)
        val nearer = venue(northOfMaps(1_200.0), id = 2)
        val result = SharedVenue.match(NAME, MAPS, listOf(farther, nearer))
        val ask = result as SharedVenueMatch.AskWhichStore
        assertEquals(nearer, ask.venue)
        assertEquals(MAPS, ask.to)
        assertEquals(MAPS.metersTo(northOfMaps(1_200.0)), ask.meters)
    }

    @Test fun noPin_usesTheMostRecentlyUsedSameName() {
        val older = venue(northOfMaps(10.0), id = 1, lastUsedAt = 100L)
        val recent = venue(northOfMaps(9_000.0), id = 2, lastUsedAt = 500L)
        val other = venue(MAPS, name = "路易莎", id = 3, lastUsedAt = 900L)
        val result = SharedVenue.match(NAME, null, listOf(older, recent, other))
        assertEquals(SharedVenueMatch.Use(recent, fillPosition = null), result)
    }

    @Test fun noPin_noSameName_createsWithoutPosition() {
        val other = venue(MAPS, name = "路易莎")
        assertEquals(SharedVenueMatch.Create(NAME, null), SharedVenue.match(NAME, null, listOf(other)))
    }

    @Test fun roundedDistance_metresBelowOneKilometre() {
        assertEquals(RoundedDistance.Meters(350), roundedDistance(349.6))
    }

    @Test fun roundedDistance_kilometresWithOneDecimal() {
        assertEquals(RoundedDistance.Kilometers(18), roundedDistance(1834.0))
    }

    @Test fun roundedDistance_neverShowsAThousandMetres() {
        assertEquals(RoundedDistance.Meters(999), roundedDistance(999.4))
        assertEquals(RoundedDistance.Kilometers(10), roundedDistance(999.6))
        assertEquals(RoundedDistance.Kilometers(10), roundedDistance(1000.0))
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
            SharedVenueMatch.AskWhichStore(existing, to = firstOutside, meters = origin.metersTo(firstOutside)),
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
