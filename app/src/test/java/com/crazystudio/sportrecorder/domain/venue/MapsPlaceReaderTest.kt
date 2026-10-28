package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.fake.FakeAddressLookup
import com.crazystudio.sportrecorder.fake.FakeMapsLinkResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// A short link made by the Google Maps Android app (zh-TW), and the real Location it resolved to
// (2026-10-28 diagnosis): no pin, and the /place/ title is the address glued to the name.
private const val SHORT = "https://maps.app.goo.gl/AaeeX4FWDAMo4Ss67?g_st=ac"
private const val ANDROID_ZH = "https://www.google.com/maps/place/106%E5%8F%B0%E7%81%A3%E8%87%BA%E5%8C%97%E5%B8%82" +
    "%E5%A4%A7%E5%AE%89%E5%8D%80%E7%A6%8F%E4%BD%8F%E9%87%8C%E4%BF%A1%E7%BE%A9%E8%B7%AF%E4%BA%8C%E6%AE%B5194%E8%99%9F" +
    "%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/data=!4m2!3m1!1s0x3442a9821392326d:0x93faef9e2359365c" +
    "?utm_source=mstt_1&entry=gps&coh=192189&g_ep=CAESCjExLjEyOS4xMDMYACDXggMqGyw5NDIwNzUwNiw5NDIwODUwNiw5NDIx" +
    "ODY1M0ICVVM%3D&skid=2007c413-234c-487d-aee9-61db54793d04&g_st=ac"

// The link's address exactly as the title gives it (what is looked up), and trimmed (what a venue keeps).
private const val FULL_ADDRESS = "106台灣臺北市大安區福住里信義路二段194號"
private const val TRIMMED_ADDRESS = "臺北市大安區福住里信義路二段194號"

// A link made on the Maps website (#84 spike): it carries its own pin.
private const val WEB = "https://www.google.com/maps/place/%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/" +
    "@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035" +
    "!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts"

private val DIN_TAI_FUNG = GeoPoint(25.0335035, 121.530118)

class MapsPlaceReaderTest {
    private val resolver = FakeMapsLinkResolver(location = ANDROID_ZH)
    private val lookup = FakeAddressLookup()
    private val reader = MapsPlaceReader(resolver, lookup)

    @Test fun aPinlessAppLink_isPlacedByLookingUpItsFullAddress() = runTest {
        lookup.positions = mapOf(FULL_ADDRESS to DIN_TAI_FUNG)

        val place = reader.read(link(SHORT))

        assertEquals(
            MapsPlace("鼎泰豐 信義店", 25.0335035, 121.530118, TRIMMED_ADDRESS, geocodeQuery = FULL_ADDRESS),
            place,
        )
        assertEquals(listOf(FULL_ADDRESS), lookup.queried)
        assertEquals(1, resolver.calls)
    }

    @Test fun whenTheAddressCantBeLocated_thePlaceKeepsItsNameAndAddress_withNoPin() = runTest {
        val place = reader.read(link(SHORT))

        assertEquals(MapsPlace("鼎泰豐 信義店", null, null, TRIMMED_ADDRESS, geocodeQuery = FULL_ADDRESS), place)
        assertEquals(listOf(FULL_ADDRESS), lookup.queried)
    }

    @Test fun aLookedUpLatitudeOffTheEarth_isNoPin() = runTest {
        lookup.positions = mapOf(FULL_ADDRESS to GeoPoint(95.0, 121.5))

        val place = reader.read(link(SHORT))

        assertEquals(MapsPlace("鼎泰豐 信義店", null, null, TRIMMED_ADDRESS, geocodeQuery = FULL_ADDRESS), place)
    }

    @Test fun aLookedUpLongitudeOffTheEarth_isNoPin() = runTest {
        lookup.positions = mapOf(FULL_ADDRESS to GeoPoint(25.0, 181.5))

        val place = reader.read(link(SHORT))

        assertEquals(MapsPlace("鼎泰豐 信義店", null, null, TRIMMED_ADDRESS, geocodeQuery = FULL_ADDRESS), place)
    }

    @Test fun aTitleThatDoesNotSplit_isLookedUpWhole() = runTest {
        lookup.positions = mapOf("X" to GeoPoint(25.04, 121.53))

        val place = reader.read(link("https://www.google.com/maps/place/X/"))

        assertEquals(MapsPlace("X", 25.04, 121.53, geocodeQuery = "X", nameIsWholeTitle = true), place)
        assertEquals(listOf("X"), lookup.queried)
    }

    @Test fun aWebLink_readsItsOwnPin_andIsNeverLookedUp() = runTest {
        lookup.positions = mapOf("鼎泰豐 信義店" to GeoPoint(1.0, 1.0))

        assertEquals(MapsPlace("鼎泰豐 信義店", 25.0335035, 121.530118), reader.read(link(WEB)))
        assertEquals(0, resolver.calls)

        resolver.location = WEB
        assertEquals(MapsPlace("鼎泰豐 信義店", 25.0335035, 121.530118), reader.read(link(SHORT)))

        assertTrue(lookup.queried.isEmpty())
    }

    @Test fun aLocationOffTheAllowlist_readsNothing_andLooksNothingUp() = runTest {
        resolver.location = "https://evil.example.com/maps/place/X/"
        assertNull(reader.read(link(SHORT)))

        resolver.location = "https://www.google.com.evil.example/maps/place/X/"
        assertNull(reader.read(link(SHORT)))

        assertTrue(lookup.queried.isEmpty())
    }

    @Test fun aShortLinkThatCantBeResolved_readsNothing() = runTest {
        resolver.location = null

        assertNull(reader.read(link(SHORT)))
        assertTrue(lookup.queried.isEmpty())
    }

    private fun link(text: String): MapsLink = MapsLink.find(text)!!
}
