package com.crazystudio.sportrecorder.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeocoderAddressFormatTest {
    @Test fun roadAndNumber() {
        assertEquals("信義路二段 194號", shortAddress("信義路二段", "194號", "台灣台北市信義路二段194號"))
    }

    @Test fun roadOnly() {
        assertEquals("信義路二段", shortAddress("信義路二段", null, "台灣台北市信義路二段"))
    }

    @Test fun fallsBackToTheFirstLine() {
        assertEquals("台北市中正區", shortAddress(null, null, " 台北市中正區 "))
    }

    @Test fun numberWithoutRoad_fallsBackToTheFirstLine() {
        assertEquals("台北市中正區忠孝西路一段49號", shortAddress(null, "49號", "台北市中正區忠孝西路一段49號"))
        assertNull(shortAddress(null, "49號", null))
    }

    @Test fun nothingIsNull() {
        assertNull(shortAddress(null, " ", ""))
    }
}
