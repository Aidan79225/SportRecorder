package com.crazystudio.sportrecorder.data.mapper

import com.crazystudio.sportrecorder.domain.model.CustomFastingType
import com.crazystudio.sportrecorder.entity.FastingType
import org.junit.Assert.assertEquals
import org.junit.Test

class FastingTypeMappersTest {
    @Test fun toCustomType_mapsHoursAndName() {
        val type = FastingType(
            id = 1,
            fastingHours = 18,
            eatingHours = 6,
            name = "My plan",
            timestamp = 123L,
        ).toCustomType()
        assertEquals(CustomFastingType(18, 6, "My plan"), type)
    }

    @Test fun toCustomType_nullName_isKept() {
        val type = FastingType(id = 1, fastingHours = 18, eatingHours = 6, timestamp = 123L).toCustomType()
        assertEquals(CustomFastingType(18, 6, null), type)
    }
}
