package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.entity.EatTime
import com.crazystudio.sportrecorder.entity.EatTimeWithPhotos
import com.crazystudio.sportrecorder.entity.Photo
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

    @Test fun nullLatLng_mapsToNullLocation() {
        val record = EatTime(id = 3, time = 0L, lat = null, lng = null, note = null).toDomain()
        assertNull(record.location)
        assertEquals(emptyList<EatPhoto>(), record.photos)
    }
}
