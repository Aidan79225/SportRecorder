package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.entity.FastingType
import org.junit.Assert.assertEquals
import org.junit.Test

class FastingTypeMappersTest {
    @Test fun toDomain_mapsHours() {
        val window = FastingType(id = 1, fastingHours = 18, eatingHours = 6, timestamp = 123L).toDomain()
        assertEquals(FastingWindow(18, 6), window)
    }
}
