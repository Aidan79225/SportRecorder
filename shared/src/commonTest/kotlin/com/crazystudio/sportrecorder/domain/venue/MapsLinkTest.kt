package com.crazystudio.sportrecorder.domain.venue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val REAL = "https://www.google.com/maps/place/%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/" +
    "@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035" +
    "!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts"

/**
 * The parser is the gate between arbitrary shared text and the network: only exact allowlisted
 * hosts over https may come out of [MapsLink.find], and [MapsLink.parse] reads only full Maps URLs.
 */
class MapsLinkTest {

    @Test fun parsesTheRealSharedLink() {
        assertEquals(MapsPlace("鼎泰豐 信義店", 25.0335035, 121.530118), MapsLink.parse(REAL))
    }

    @Test fun thePinBeatsTheCamera() {
        val panned = REAL.replace("@25.0335035,121.530118", "@25.1,121.6")
        assertEquals(25.0335035, MapsLink.parse(panned)!!.lat)
        assertEquals(121.530118, MapsLink.parse(panned)!!.lng)
    }

    @Test fun fallsBackToTheCameraWithoutAPin() {
        val place = MapsLink.parse("https://www.google.com/maps/place/X/@25.04,121.53,17z")
        assertEquals(MapsPlace("X", 25.04, 121.53), place)
    }

    @Test fun noCoordinatesStillGivesTheName() {
        assertEquals(MapsPlace("X", null, null), MapsLink.parse("https://www.google.com/maps/place/X/"))
    }

    @Test fun outOfRangeCoordinatesAreDiscarded() {
        val place = MapsLink.parse("https://www.google.com/maps/place/X/data=!3d95.0!4d121.5")
        assertNull(place!!.lat)
        assertNull(place.lng)
        val wideLng = MapsLink.parse("https://www.google.com/maps/place/X/data=!3d25.0!4d181.5")
        assertNull(wideLng!!.lat)
        assertNull(wideLng.lng)
    }

    @Test fun decodesPunctuationAndMixedScripts() {
        val url = "https://www.google.com/maps/place/MOS+Burger+%E6%91%A9%E6%96%AF+(%E4%BF%A1%E7%BE%A9)+%26+Co/"
        assertEquals("MOS Burger 摩斯 (信義) & Co", MapsLink.parse(url)!!.name)
    }

    @Test fun aBlankNameBecomesNull() {
        assertNull(MapsLink.parse("https://www.google.com/maps/place/+%20+/")!!.name)
    }

    @Test fun aMapsUrlWithoutAPlaceHasNoName() {
        assertNull(MapsLink.parse("https://maps.google.com/maps?q=1,2")!!.name)
    }

    @Test fun findsAShortLink() {
        assertIs<MapsLink.Short>(MapsLink.find("https://maps.app.goo.gl/3oUGbkxLYE6jt7L29"))
    }

    @Test fun findsALegacyShortLink() {
        assertIs<MapsLink.Short>(MapsLink.find("https://goo.gl/maps/abc123"))
    }

    @Test fun findsAFullLink() {
        val link = MapsLink.find(REAL)
        assertIs<MapsLink.Full>(link)
        assertEquals(REAL, link.url)
    }

    @Test fun findsTheLinkInsideARealMultiLineShare() {
        val text = "鼎泰豐 信義店\n106臺北市大安區信義路二段194號\nhttps://maps.app.goo.gl/3oUGbkxLYE6jt7L29"
        assertEquals("https://maps.app.goo.gl/3oUGbkxLYE6jt7L29", MapsLink.find(text)!!.url)
    }

    @Test fun skipsAnUnlistedLinkToFindALaterAllowedOne() {
        val text = "https://evil.example/x https://maps.app.goo.gl/abc"
        assertEquals("https://maps.app.goo.gl/abc", MapsLink.find(text)!!.url)
    }

    @Test fun rejectsOtherHosts() {
        assertNull(MapsLink.find("see https://evil.example/maps/place/X/@1,2"))
        assertNull(MapsLink.find("https://maps.app.goo.gl.evil.example/abc"))
        assertNull(MapsLink.find("https://www.google.com.evil.example/maps/place/X/"))
        assertNull(MapsLink.find("https://maps.app.goo.gl@evil.example/abc"))
        assertNull(MapsLink.find("https://goo.gl/other/abc"))
        assertNull(MapsLink.find("https://www.google.com/search?q=maps"))
        assertNull(MapsLink.parse("https://consent.google.com/maps/place/X/"))
        assertNull(MapsLink.parse("https://maps.app.goo.gl/abc"))
        assertNull(MapsLink.find("http://maps.app.goo.gl/abc"))
    }

    @Test fun plainTextHasNoLink() {
        assertNull(MapsLink.find("今天吃了很多"))
    }
}
