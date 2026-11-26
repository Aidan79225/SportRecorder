package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.entity.Photo
import com.crazystudio.sportrecorder.entity.VenueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EatRecordMappersTest {
    @Test fun withPhotos_mapsAllFields() {
        val entity = EatTimeWithPhotos(
            eatTime = EatTime(id = 1, time = 100L, lat = 1.5, lng = 2.5, note = "hi"),
            photos = listOf(Photo(id = 2, eatTimeId = 1, fileName = "a.webp", createdAt = 50L)),
        )
        val record = entity.toDomain()
        assertEquals(1, record.id)
        assertEquals(100L, record.time)
        assertEquals(GeoPoint(1.5, 2.5), record.location)
        assertEquals("hi", record.note)
        assertEquals(listOf(EatPhoto(id = 2, fileName = "a.webp", createdAt = 50L)), record.photos)
    }

    @Test fun aRecordsVenue_carriesItsAddress() {
        val entity = EatTimeWithPhotos(
            eatTime = EatTime(id = 1, time = 100L, venueId = 7),
            photos = emptyList(),
            venue = VenueEntity(id = 7, name = "大戶屋", lat = 25.0, lng = 121.5, lastUsedAt = 9L, address = "信義路 1 號"),
        )
        assertEquals(
            Venue(id = 7, name = "大戶屋", lat = 25.0, lng = 121.5, lastUsedAt = 9L, address = "信義路 1 號"),
            entity.toDomain().venue,
        )
    }

    @Test fun aMealsDurationIsCarriedFromTheRowToTheRecord() {
        val entity = EatTimeWithPhotos(
            eatTime = EatTime(id = 1, time = 100L, durationMinutes = 90),
            photos = emptyList(),
        )
        assertEquals(90, entity.toDomain().durationMinutes)
        assertEquals(45, EatTime(id = 2, time = 0L, durationMinutes = 45).toDomain().durationMinutes)
    }

    @Test fun aMealsTasteRatingIsCarriedFromTheRowToTheRecord() {
        val entity = EatTimeWithPhotos(
            eatTime = EatTime(id = 1, time = 100L, tasteRating = 4),
            photos = emptyList(),
        )
        assertEquals(4, entity.toDomain().tasteRating)
        assertEquals(1, EatTime(id = 2, time = 0L, tasteRating = 1).toDomain().tasteRating)
        assertEquals(5, EatTime(id = 3, time = 0L, tasteRating = 5).toDomain().tasteRating)
    }

    @Test fun aRowWithoutARating_isAnUnratedMeal() {
        assertNull(EatTime(id = 1, time = 100L).toDomain().tasteRating)
    }

    // A row is data too (a restored file, a future bug): a value off the scale reads as unrated, not as a crash.
    @Test fun aStoredRatingOffTheScale_readsAsUnrated() {
        listOf(0, 6, -1, 99).forEach { stored ->
            assertNull("stored $stored", EatTime(id = 1, time = 100L, tasteRating = stored).toDomain().tasteRating)
        }
    }

    @Test fun aRecordReachesTheRowWithEveryFieldIntact() {
        val record = EatRecord(
            id = 4,
            time = 100L,
            location = GeoPoint(1.5, 2.5),
            note = "hi",
            photos = emptyList(),
            venue = Venue(id = 7, name = "大戶屋", lat = 25.0, lng = 121.5, lastUsedAt = 9L),
            durationMinutes = 90,
            tasteRating = 4,
        )
        assertEquals(
            EatTime(
                id = 4,
                time = 100L,
                lat = 1.5,
                lng = 2.5,
                note = "hi",
                venueId = 7,
                durationMinutes = 90,
                tasteRating = 4,
            ),
            record.toEntity(),
        )
        assertEquals(0, record.toEntity(id = 0).id)
    }

    @Test fun anUnratedRecordReachesTheRowUnrated() {
        val record = EatRecord(id = 4, time = 100L, location = null, note = null, photos = emptyList())

        assertNull(record.toEntity().tasteRating)
    }

    @Test fun aMealsDishesAreStoredOnePerLine_andReadBack() {
        val record = EatRecord(
            id = 4,
            time = 100L,
            location = null,
            note = null,
            photos = emptyList(),
            dishes = listOf("滷肉飯", "Soup"),
        )

        assertEquals("滷肉飯\nSoup", record.toEntity().dishes)
        assertEquals(listOf("滷肉飯", "Soup"), record.toEntity().toDomain().dishes)
    }

    @Test fun aMealWithNoDishes_storesNull_andAColumnWithNoneReadsAsEmpty() {
        val record = EatRecord(id = 4, time = 100L, location = null, note = null, photos = emptyList())

        assertNull(record.toEntity().dishes)
        assertEquals(emptyList<String>(), EatTime(id = 1, time = 0L).toDomain().dishes)
    }

    // A row is data too: an unclean stored value is cleaned on the way out, and on the way in.
    @Test fun dishesAreSanitizedBothWays() {
        assertEquals(listOf("a", "b"), EatTime(id = 1, time = 0L, dishes = " a \n\nA\nb").toDomain().dishes)
        val record = EatRecord(
            id = 4,
            time = 100L,
            location = null,
            note = null,
            photos = emptyList(),
            dishes = listOf("a\nb", "A B", " "),
        )
        assertEquals("a b", record.toEntity().dishes)
    }

    @Test fun nullLatLng_mapsToNullLocation() {
        val record = EatTime(id = 3, time = 0L, lat = null, lng = null, note = null).toDomain()
        assertNull(record.location)
        assertEquals(emptyList<EatPhoto>(), record.photos)
    }
}
