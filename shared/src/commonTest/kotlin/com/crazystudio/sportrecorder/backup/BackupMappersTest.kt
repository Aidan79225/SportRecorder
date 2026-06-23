package com.crazystudio.sportrecorder.backup

import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupMappersTest {
    @Test fun meal_roundTrips() {
        val r = EatRecord(1, 1_700L, GeoPoint(25.0, 121.5), "n",
            listOf(EatPhoto(7, "a.webp", 1_701L)))
        assertEquals(r, r.toBackup().toDomain())
    }
}
