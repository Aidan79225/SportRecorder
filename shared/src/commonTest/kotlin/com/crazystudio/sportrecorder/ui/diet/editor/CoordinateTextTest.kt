package com.crazystudio.sportrecorder.ui.diet.editor

import kotlin.test.Test
import kotlin.test.assertEquals

/** The edit dialog's position line: four decimals, the same on every platform and locale. */
class CoordinateTextTest {

    @Test fun roundsToFourDecimals() {
        assertEquals("25.0335", coordinateText(25.0335035))
        assertEquals("121.5301", coordinateText(121.530118))
    }

    @Test fun padsShortFractions() {
        assertEquals("25.0000", coordinateText(25.0))
        assertEquals("0.0500", coordinateText(0.05))
    }

    @Test fun keepsTheSign_butNeverShowsMinusZero() {
        assertEquals("-33.8688", coordinateText(-33.86882))
        assertEquals("-0.5000", coordinateText(-0.5))
        assertEquals("0.0000", coordinateText(-0.00001))
    }

    @Test fun roundingCarriesIntoTheWholePart() {
        assertEquals("26.0000", coordinateText(25.99999))
        assertEquals("-180.0000", coordinateText(-179.99996))
    }
}
