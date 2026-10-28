package com.crazystudio.sportrecorder.domain.venue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val REAL = "https://www.google.com/maps/place/%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/" +
    "@25.0335035,121.530118,17z/data=!4m6!3m5!1s0x3442a9821392326d:0x93faef9e2359365c!8m2!3d25.0335035" +
    "!4d121.530118!16s%2Fg%2F1td2dltc?entry=tts"

// Real Location headers of short links made by the Google Maps apps (2026-10-28 diagnosis). No pin, and
// the /place/ title (or the iOS q=) is the address glued to the name.
private const val ANDROID_ZH = "https://www.google.com/maps/place/106%E5%8F%B0%E7%81%A3%E8%87%BA%E5%8C%97%E5%B8%82" +
    "%E5%A4%A7%E5%AE%89%E5%8D%80%E7%A6%8F%E4%BD%8F%E9%87%8C%E4%BF%A1%E7%BE%A9%E8%B7%AF%E4%BA%8C%E6%AE%B5194%E8%99%9F" +
    "%E9%BC%8E%E6%B3%B0%E8%B1%90+%E4%BF%A1%E7%BE%A9%E5%BA%97/data=!4m2!3m1!1s0x3442a9821392326d:0x93faef9e2359365c" +
    "?utm_source=mstt_1&entry=gps&coh=192189&g_ep=CAESCjExLjEyOS4xMDMYACDXggMqGyw5NDIwNzUwNiw5NDIwODUwNiw5NDIx" +
    "ODY1M0ICVVM%3D&skid=2007c413-234c-487d-aee9-61db54793d04&g_st=ac"
private const val ANDROID_EN = "https://www.google.com/maps/place/Din+Tai+Fung+Xinyi+Branch,+No.+194%E8%99%9F," +
    "+Section+2,+Xinyi+Rd,+Fuzhu+Village,+Da%E2%80%99an+District,+Taipei+City,+Taiwan+106/data=!4m2!3m1" +
    "!1s0x3442a9821392326d:0x93faef9e2359365c?utm_source=mstt_1&entry=gps&coh=192189&g_ep=CAESCjExLjEyOS4xMDMYACDXgg" +
    "MqGyw5NDIwNzUwNiw5NDIwODUwNiw5NDIxODY1M0ICVVM%3D&skid=aef487d2-0578-40e5-a9d0-c4c93c0d3bcd&g_st=ac"
private const val ANDROID_KO = "https://www.google.com/maps/place/%E5%8D%97%E9%9F%93+Seoul,+Jung+District,+Myeongdon" +
    "g+8na-gil,+29+%EB%B6%88%ED%83%84%EC%99%95%EB%BC%88%EA%B5%AC%EC%9D%B4%26%EA%B0%90%EC%9E%90%ED%83%95+%EB%AA%85%E" +
    "B%8F%99%EC%A0%90+%7C+Korean+Restaurant+Bultan+Wang+Ribs+%26+Gamjatang+Myeongdong+Branch+%7C+%E7%BE%8E%E9%A3%9F" +
    "+%E7%81%AB%E7%82%AD%E7%8E%8B%E7%83%A4%E9%AA%A8%E8%82%89%26%E9%9F%A9%E5%BC%8F%E5%9C%9F%E8%B1%86%E6%8E%92%E9%AA%" +
    "A8%E6%B1%A4+%E6%98%8E%E6%B4%9E%E5%BA%97/data=!4m2!3m1!1s0x357ca371e83b62c7:0x768802893f6ca9bd!18m1!1e1?utm_sou" +
    "rce=mstt_1&entry=gps&coh=192189&g_ep=CAESBzI2LjI2LjMYACCenQoqiwEsOTQyNjc3MjcsOTQyOTIxOTUsOTQyOTk1MzIsMTAwNzk2N" +
    "Dk4LDEwMDc5Nzc2MSwxMDA3OTY1MzUsOTQyODA1NzYsOTQyMDczOTQsOTQyMDc1MDYsOTQyMDg1MDYsOTQyMTg2NTMsOTQyMjk4MzksOTQyNzU" +
    "xNjgsOTQyNzk2MTksMTAwODE1NjM1QgJUVw%3D%3D&skid=a2a5071b-2ed4-4598-8d55-677272b0aaca&g_st=ac"
private const val KO_TITLE = "南韓 Seoul, Jung District, Myeongdong 8na-gil, 29 불탄왕뼈구이&감자탕 명동점 | " +
    "Korean Restaurant Bultan Wang Ribs & Gamjatang Myeongdong Branch | 美食 火炭王烤骨肉&韩式土豆排骨汤 明洞店"
// A pinless /place/ link whose title is only an address, ending in 之N.
private const val ADDRESS_ONLY_TITLE = "100台灣臺北市中正區重慶南路一段5號之1"
private const val ADDRESS_ONLY = "https://www.google.com/maps/place/100%E5%8F%B0%E7%81%A3%E8%87%BA%E5%8C%97%E5%B8%82" +
    "%E4%B8%AD%E6%AD%A3%E5%8D%80%E9%87%8D%E6%85%B6%E5%8D%97%E8%B7%AF%E4%B8%80%E6%AE%B55%E8%99%9F%E4%B9%8B1" +
    "/data=!4m2!3m1!1s0x3442a9821392326d:0x93faef9e2359365c?utm_source=mstt_1&entry=gps"
private const val IOS = "https://maps.google.com?q=106%E8%87%BA%E5%8C%97%E5%B8%82%E5%A4%A7%E5%AE%89%E5%8D%80" +
    "%E5%9B%9B%E7%B6%AD%E8%B7%AF208%E5%B7%B710%E8%99%9F%E6%9C%AA%E5%A4%AE%E5%92%96%E5%95%A1%E5%BA%97" +
    "&ftid=0x3442aa2dec922001:0xf1592a50aa99e9f9&entry=gps&shh=CAE&lucs=,94297695,94275415,94284472,94231188," +
    "94280568,47071704,94218641,94282134,94286869&g_ep=CAISEjI1LjQ5LjkuODM4ODk5MTgzMBgBIIgnKlEsOTQyOTc2OTUsOTQyNzU0" +
    "MTUsOTQyODQ0NzIsOTQyMzExODgsOTQyODA1NjgsNDcwNzE3MDQsOTQyMTg2NDEsOTQyODIxMzQsOTQyODY4NjlCAlRX" +
    "&skid=738591ab-314f-4265-a56e-7309aae2f9c8&g_st=ipc"
private const val IOS_QUERY = "q=106%E8%87%BA%E5%8C%97%E5%B8%82%E5%A4%A7%E5%AE%89%E5%8D%80" +
    "%E5%9B%9B%E7%B6%AD%E8%B7%AF208%E5%B7%B710%E8%99%9F%E6%9C%AA%E5%A4%AE%E5%92%96%E5%95%A1%E5%BA%97&entry=gps"
private val IOS_PLACE = MapsPlace("未央咖啡店", null, null, "臺北市大安區四維路208巷10號", "106臺北市大安區四維路208巷10號")

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

    @Test fun noCoordinatesStillGivesTheName_andSaysWhatToLookUp() {
        assertEquals(
            MapsPlace("X", null, null, geocodeQuery = "X"),
            MapsLink.parse("https://www.google.com/maps/place/X/"),
        )
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

    @Test fun androidAppLink_zhTW_splitsAddressAndName() {
        assertEquals(
            MapsPlace(
                name = "鼎泰豐 信義店",
                lat = null,
                lng = null,
                address = "臺北市大安區福住里信義路二段194號",
                geocodeQuery = "106台灣臺北市大安區福住里信義路二段194號",
            ),
            MapsLink.parse(ANDROID_ZH),
        )
    }

    @Test fun androidAppLink_enUS_splitsNameFirst() {
        assertEquals(
            MapsPlace(
                name = "Din Tai Fung Xinyi Branch",
                lat = null,
                lng = null,
                address = "No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, Taipei City",
                geocodeQuery = "No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, Taipei City, Taiwan 106",
            ),
            MapsLink.parse(ANDROID_EN),
        )
    }

    @Test fun androidAppLinks_areFoundAsShortLinks() {
        assertIs<MapsLink.Short>(MapsLink.find("https://maps.app.goo.gl/AaeeX4FWDAMo4Ss67?g_st=ac"))
        assertIs<MapsLink.Full>(MapsLink.find(ANDROID_ZH))
    }

    @Test fun unrecognisedPinlessSegment_staysWhole() {
        assertEquals(
            MapsPlace(KO_TITLE, null, null, address = null, geocodeQuery = KO_TITLE),
            MapsLink.parse(ANDROID_KO),
        )
    }

    @Test fun pinlessAddressOnlySegment_staysWhole() {
        assertEquals(
            MapsPlace(ADDRESS_ONLY_TITLE, null, null, address = null, geocodeQuery = ADDRESS_ONLY_TITLE),
            MapsLink.parse(ADDRESS_ONLY),
        )
    }

    @Test fun webLink_isUnchanged() {
        assertEquals(
            MapsPlace("鼎泰豐 信義店", 25.0335035, 121.530118, address = null, geocodeQuery = null),
            MapsLink.parse(REAL),
        )
    }

    @Test fun aLinkWithAPin_isNeverSplit() {
        val glued = "https://www.google.com/maps/place/106%E5%8F%B0%E7%81%A3%E8%87%BA%E5%8C%97%E5%B8%82" +
            "%E5%A4%A7%E5%AE%89%E5%8D%80%E4%BF%A1%E7%BE%A9%E8%B7%AF194%E8%99%9F%E9%BC%8E%E6%B3%B0%E8%B1%90" +
            "/@25.03,121.53,17z"
        assertEquals(MapsPlace("106台灣臺北市大安區信義路194號鼎泰豐", 25.03, 121.53), MapsLink.parse(glued))
        val withComma = "https://www.google.com/maps/place/Caf%C3%A9,+Bar/data=!3d25.03!4d121.53"
        assertEquals(MapsPlace("Café, Bar", 25.03, 121.53), MapsLink.parse(withComma))
    }

    @Test fun iosLink_readsQ() {
        assertIs<MapsLink.Full>(MapsLink.find("看看 $IOS"))
        assertEquals(IOS, MapsLink.find(IOS)!!.url)
        assertEquals(IOS_PLACE, MapsLink.parse(IOS))
        assertEquals(IOS_PLACE, MapsLink.parse("https://maps.google.com/?$IOS_QUERY"))
        assertEquals(IOS_PLACE, MapsLink.parse("https://www.google.com?$IOS_QUERY"))
        assertEquals(IOS_PLACE, MapsLink.parse("https://www.google.com/maps?$IOS_QUERY"))
    }

    @Test fun aPathlessLinkNeedsAQ() {
        assertNull(MapsLink.find("https://maps.google.com"))
        assertNull(MapsLink.find("https://maps.google.com/"))
        assertNull(MapsLink.find("https://maps.google.com?ftid=0x1:0x2&entry=gps"))
        assertNull(MapsLink.find("https://maps.google.com/?q="))
        assertNull(MapsLink.parse("https://www.google.com/?entry=gps"))
        assertNull(MapsLink.find("https://maps.google.com/search?q=x"))
        assertNull(MapsLink.find("https://maps.google.com.evil.example?q=x"))
    }

    @Test fun searchCoordinates_giveAPin() {
        val search = "https://www.google.com/maps/search/39.920439,+116.331538?entry=tts" +
            "&g_ep=EgoyMDI1MTEyMy4xIPu8ASoASAFQAw%3D%3D&skid=23b67bbf-4d82-473d-b6b3-e738223d1387"
        assertEquals(MapsPlace(null, 39.920439, 116.331538), MapsLink.parse(search))
        val negative = "https://www.google.com/maps/search/5.811698,+-55.118891?entry=tts"
        assertEquals(MapsPlace(null, 5.811698, -55.118891), MapsLink.parse(negative))
        val q = MapsPlace(null, 25.0335, 121.5301)
        assertEquals(q, MapsLink.parse("https://maps.google.com/maps?q=25.0335,121.5301"))
        assertEquals(q, MapsLink.parse("https://maps.google.com?q=25.0335,+121.5301"))
        assertEquals(MapsPlace(null, null, null), MapsLink.parse("https://www.google.com/maps/search/95.0,+121.5"))
        assertEquals(MapsPlace(null, null, null), MapsLink.parse("https://www.google.com/maps/search/%E9%BC%8E"))
    }

    @Test fun coordinatesOnlyPlace_hasNoName() {
        val droppedPin = "https://www.google.com/maps/place/42.578496,1.895566/data=!4m6!3m5!1s0!7e2!8m2" +
            "!3d42.5784957!4d1.8955661?utm_source=mstt_1&entry=gps&coh=192189"
        assertEquals(MapsPlace(null, 42.5784957, 1.8955661), MapsLink.parse(droppedPin))
        val bare = "https://www.google.com/maps/place/42.578496,1.895566/"
        assertEquals(MapsPlace(null, 42.578496, 1.895566), MapsLink.parse(bare))
        val dms = "https://www.google.com/maps/place/25%C2%B002'00.6%22N+121%C2%B031'48.4%22E" +
            "/@25.0335,121.5301,17z/data=!3m1!4b1!4m4!3m3!8m2!3d25.0335!4d121.5301"
        assertEquals(MapsPlace(null, 25.0335, 121.5301), MapsLink.parse(dms))
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

    @Test fun theLinkEndsAtCjkTextOrPunctuation() {
        assertEquals("https://maps.app.goo.gl/abc", MapsLink.find("https://maps.app.goo.gl/abc。")!!.url)
        assertEquals("https://maps.app.goo.gl/abc", MapsLink.find("https://maps.app.goo.gl/abc好吃")!!.url)
        assertEquals("https://maps.app.goo.gl/abc", MapsLink.find("(https://maps.app.goo.gl/abc)")!!.url)
        assertEquals("https://maps.app.goo.gl/abc", MapsLink.find("去:https://maps.app.goo.gl/abc, 好!")!!.url)
    }

    @Test fun aFullLinkKeepsItsPinAndQuery() {
        val link = MapsLink.find("看這間 $REAL 好吃")
        assertEquals(REAL, link!!.url)
        assertEquals(25.0335035, MapsLink.parse(link.url)!!.lat)
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
