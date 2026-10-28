package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.venue.PlaceTitle.Split
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * The titles in links made by the Google Maps apps glue the address to the name. The split is
 * confident or nothing: the real titles below come apart, and anything else — above all a name the
 * user typed — gives null so the caller keeps it whole.
 */
class PlaceTitleTest {

    @Test fun theRealZhAndroidTitle_splitsAddressFromName() {
        assertEquals(
            Split("鼎泰豐 信義店", "106台灣臺北市大安區福住里信義路二段194號", "臺北市大安區福住里信義路二段194號"),
            PlaceTitle.split("106台灣臺北市大安區福住里信義路二段194號鼎泰豐 信義店"),
        )
    }

    @Test fun theRealEnAndroidTitle_splitsNameFirst() {
        val title = "Din Tai Fung Xinyi Branch, No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, " +
            "Taipei City, Taiwan 106"
        assertEquals(
            Split(
                "Din Tai Fung Xinyi Branch",
                "No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, Taipei City, Taiwan 106",
                "No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, Taipei City",
            ),
            PlaceTitle.split(title),
        )
    }

    @Test fun aFloorAfterTheNumber_staysInTheAddress() {
        assertEquals(
            Split("鼎泰豐", "106台灣臺北市大安區福住里信義路二段194號2樓", "臺北市大安區福住里信義路二段194號2樓"),
            PlaceTitle.split("106台灣臺北市大安區福住里信義路二段194號2樓鼎泰豐"),
        )
        assertEquals(
            Split("一蘭", "106台灣臺北市大安區忠孝東路四段5號地下1樓", "臺北市大安區忠孝東路四段5號地下1樓"),
            PlaceTitle.split("106台灣臺北市大安區忠孝東路四段5號地下1樓一蘭"),
        )
    }

    @Test fun aFloorInAnotherForm_staysInTheAddress() {
        val expected = mapOf(
            "110台灣台北市信義區市府路45號B1鼎泰豐" to
                Split("鼎泰豐", "110台灣台北市信義區市府路45號B1", "台北市信義區市府路45號B1"),
            "106台灣臺北市大安區信義路二段194號3F鼎泰豐" to
                Split("鼎泰豐", "106台灣臺北市大安區信義路二段194號3F", "臺北市大安區信義路二段194號3F"),
            // A space after 號.
            "106台灣臺北市大安區信義路二段194號 2樓鼎泰豐" to
                Split("鼎泰豐", "106台灣臺北市大安區信義路二段194號 2樓", "臺北市大安區信義路二段194號 2樓"),
            // A Chinese-numeral floor, as the geocoder writes this building's.
            "106台灣臺北市大安區信義路二段194號三樓鼎泰豐" to
                Split("鼎泰豐", "106台灣臺北市大安區信義路二段194號三樓", "臺北市大安區信義路二段194號三樓"),
            "106台灣臺北市大安區信義路二段194號地下一樓之2鼎泰豐" to
                Split("鼎泰豐", "106台灣臺北市大安區信義路二段194號地下一樓之2", "臺北市大安區信義路二段194號地下一樓之2"),
            // A name that only starts like B1 or 3F keeps its start.
            "106台灣臺北市大安區信義路二段12號B2B咖啡" to
                Split("B2B咖啡", "106台灣臺北市大安區信義路二段12號", "臺北市大安區信義路二段12號"),
            "106台灣臺北市大安區信義路二段12號5Fun Bar" to
                Split("5Fun Bar", "106台灣臺北市大安區信義路二段12號", "臺北市大安區信義路二段12號"),
            // No basement is that deep, nor written in lower case: these are names.
            "106台灣臺北市大安區信義路二段12號B612咖啡" to
                Split("B612咖啡", "106台灣臺北市大安區信義路二段12號", "臺北市大安區信義路二段12號"),
            "106台灣臺北市大安區信義路二段12號b6咖啡" to
                Split("b6咖啡", "106台灣臺北市大安區信義路二段12號", "臺北市大安區信義路二段12號"),
        )
        expected.forEach { (title, split) -> assertEquals(split, PlaceTitle.split(title), title) }
    }

    @Test fun aBasementWithItsOwnFloorWord_staysWholeInTheAddress() {
        // 「B1樓」 and 「B1F」 are one floor each: never B1 and a name that starts with 樓 or F.
        listOf("B1樓", "B1F", "B1 樓").forEach { floor ->
            assertEquals(
                Split("鼎泰豐", "110台灣台北市信義區松壽路11號$floor", "台北市信義區松壽路11號$floor"),
                PlaceTitle.split("110台灣台北市信義區松壽路11號${floor}鼎泰豐"),
                floor,
            )
            assertEquals(
                Split("鼎泰豐", "110台灣台北市信義區松壽路11號$floor", "台北市信義區松壽路11號$floor"),
                PlaceTitle.split("110台灣台北市信義區松壽路11號鼎泰豐$floor"),
                "after the name: $floor",
            )
            assertNull(PlaceTitle.split("110台灣台北市信義區松壽路11號$floor"), "no name: $floor")
            assertTrue(PlaceTitle.isAddressOnly("110台北市信義區松壽路11號$floor"), floor)
        }
    }

    @Test fun aFloorAfterTheName_joinsTheAddress_inAnyForm() {
        val address = "108台北市萬華區雙園街49巷7號"
        val trimmed = "台北市萬華區雙園街49巷7號"
        val name = "癒所vegvísir｜預約制私廚"
        listOf("地下1樓", "2樓之1", "B1", "3F").forEach { floor ->
            assertEquals(Split(name, address + floor, trimmed + floor), PlaceTitle.split(address + name + floor), floor)
        }
        assertEquals(
            Split(name, address + "B1", trimmed + "B1"),
            PlaceTitle.split("$address$name B1"),
        )
        // Only letters and digits before it: the name's own end, not a floor.
        assertEquals(Split("CLUB1", address, trimmed), PlaceTitle.split("${address}CLUB1"))
    }

    @Test fun enTitles_keepTheirFloorInTheAddress() {
        listOf("2F", "1樓", "B1", "地下1樓").forEach { floor ->
            val address = "$floor, No. 194號, Section 2, Xinyi Rd, Da’an District, Taipei City"
            assertEquals(
                Split("Din Tai Fung", "$address, Taiwan 106", address),
                PlaceTitle.split("Din Tai Fung, $address, Taiwan 106"),
                floor,
            )
        }
        listOf("2樓", "地下1樓", "B1").forEach { floor ->
            val address = "Taichung City, North District, Taiping Rd, 15號$floor"
            assertEquals(
                Split("高沐手作料理餐廳", "404, $address", address),
                PlaceTitle.split("404, ${address}高沐手作料理餐廳"),
                floor,
            )
        }
    }

    @Test fun zhiAfterTheNumber_staysInTheAddress() {
        assertEquals(
            Split("小王子咖啡", "100台灣臺北市中正區重慶南路一段5號之1", "臺北市中正區重慶南路一段5號之1"),
            PlaceTitle.split("100台灣臺北市中正區重慶南路一段5號之1小王子咖啡"),
        )
        assertEquals(
            Split("小王子咖啡", "100台灣臺北市中正區重慶南路一段5號之12", "臺北市中正區重慶南路一段5號之12"),
            PlaceTitle.split("100台灣臺北市中正區重慶南路一段5號之12小王子咖啡"),
        )
        assertEquals(
            Split("鼎泰豐", "106台灣臺北市大安區福住里信義路二段194號2樓之3", "臺北市大安區福住里信義路二段194號2樓之3"),
            PlaceTitle.split("106台灣臺北市大安區福住里信義路二段194號2樓之3鼎泰豐"),
        )
    }

    @Test fun aNameStartingWithDigits_isKeptWhole() {
        assertEquals(
            Split("85度C", "106台灣臺北市大安區忠孝東路三段12號", "臺北市大安區忠孝東路三段12號"),
            PlaceTitle.split("106台灣臺北市大安區忠孝東路三段12號85度C"),
        )
    }

    @Test fun realTaiwaneseTitlesFromTheMapsApps() {
        val expected = mapOf(
            // iOS app (q=), no country after the postcode.
            "106臺北市大安區四維路208巷10號未央咖啡店" to
                Split("未央咖啡店", "106臺北市大安區四維路208巷10號", "臺北市大安區四維路208巷10號"),
            "105台北市松山區復興北路319號LA VERY THAI" to
                Split("LA VERY THAI", "105台北市松山區復興北路319號", "台北市松山區復興北路319號"),
            "111臺北市士林區中山北路六段441巷46弄3號五年咖啡 Five Years Coffee" to
                Split("五年咖啡 Five Years Coffee", "111臺北市士林區中山北路六段441巷46弄3號", "臺北市士林區中山北路六段441巷46弄3號"),
            "105臺北市松山區塔悠路19號大盛大叔 新台式蒙古烤肉" to
                Split("大盛大叔 新台式蒙古烤肉", "105臺北市松山區塔悠路19號", "臺北市松山區塔悠路19號"),
            "900屏東縣屏東市和平路166號三越平價美食900" to
                Split("三越平價美食900", "900屏東縣屏東市和平路166號", "屏東縣屏東市和平路166號"),
            "106台灣台北市大安區羅斯福路三段283巷21弄2號祝您行運" to
                Split("祝您行運", "106台灣台北市大安區羅斯福路三段283巷21弄2號", "台北市大安區羅斯福路三段283巷21弄2號"),
            "106台灣台北市大安區建國南路二段11巷6號洒麻辣 | 大安麻辣火鍋 必吃人氣火鍋 美食火鍋餐廳推薦 海鮮精緻鍋物" to
                Split(
                    "洒麻辣 | 大安麻辣火鍋 必吃人氣火鍋 美食火鍋餐廳推薦 海鮮精緻鍋物",
                    "106台灣台北市大安區建國南路二段11巷6號",
                    "台北市大安區建國南路二段11巷6號",
                ),
            // A six-digit postcode.
            "408012台中市南屯區公益路二段919號燒究餐飲集團-台中燒肉推薦｜焼肉ショジョ Yakiniku SHOJO 台中公益店｜聚餐首選選燒烤餐廳" to
                Split(
                    "燒究餐飲集團-台中燒肉推薦｜焼肉ショジョ Yakiniku SHOJO 台中公益店｜聚餐首選選燒烤餐廳",
                    "408012台中市南屯區公益路二段919號",
                    "台中市南屯區公益路二段919號",
                ),
            // The floor came after the name: it belongs to the address.
            "108台北市萬華區雙園街49巷7號癒所vegvísir｜預約制私廚1 樓" to
                Split("癒所vegvísir｜預約制私廚", "108台北市萬華區雙園街49巷7號1 樓", "台北市萬華區雙園街49巷7號1 樓"),
            // The same order with the address in English (Android 26.6.1 and iOS).
            "404, Taichung City, North District, Alley 3, Lane 19, Taiping Rd, 15號高沐手作料理餐廳" to
                Split(
                    "高沐手作料理餐廳",
                    "404, Taichung City, North District, Alley 3, Lane 19, Taiping Rd, 15號",
                    "Taichung City, North District, Alley 3, Lane 19, Taiping Rd, 15號",
                ),
            "106, Taipei City, Da’an District, Lane 61, Section 2, Hangzhou S Rd, 43號Alchemist Daan" to
                Split(
                    "Alchemist Daan",
                    "106, Taipei City, Da’an District, Lane 61, Section 2, Hangzhou S Rd, 43號",
                    "Taipei City, Da’an District, Lane 61, Section 2, Hangzhou S Rd, 43號",
                ),
        )
        expected.forEach { (title, split) -> assertEquals(split, PlaceTitle.split(title), title) }
    }

    @Test fun typedNames_areNeverSplit() {
        listOf(
            "85度C",
            "7-11 信義店",
            "Café, Bar",
            "鼎泰豐 信義店",
            "MOS Burger 摩斯 (信義) & Co",
            "101大樓5號店",
            "100號咖啡",
            "台北市大安區信義路二段194號鼎泰豐",
            "Bar, No. 5",
            "Wikimedia Foundation, Inc., 1 Sansome St",
        ).forEach { assertNull(PlaceTitle.split(it), it) }
    }

    @Test fun titlesInOtherShapes_areLeftWhole() {
        listOf(
            // Korean place from the zh-TW Android app: the country first, no Taiwanese address.
            "南韓 Seoul, Jung District, Myeongdong 8na-gil, 29 불탄왕뼈구이&감자탕 명동점 | Korean Restaurant " +
                "Bultan Wang Ribs & Gamjatang Myeongdong Branch | 美食 火炭王烤骨肉&韩式土豆排骨汤 明洞店",
            "Daeppang Game Room Apgujeong Branch, 26 Seolleung-ro 157-gil, Gangnam District, Seoul, 南韓",
            "日本、〒874-0023 大分県別府市上人ケ浜町６−２４ AMANE RESORT SEIKAI（潮騒の宿 晴海）",
            // An address with no name after it.
            "106台灣臺北市大安區福住里信義路二段194號",
            "106台灣臺北市大安區福住里信義路二段194號2樓",
            "106台灣臺北市大安區福住里信義路二段194號 | ",
            // The same, ending in 之N: the 之N (whole) belongs to the number, it never becomes a name.
            "100台灣臺北市中正區重慶南路一段5號之1",
            "100台灣臺北市中正區重慶南路一段5號之12",
            "106台灣臺北市大安區福住里信義路二段194號2樓之3",
            "106台灣臺北市大安區福住里信義路二段194號2樓之12",
            "106台灣臺北市大安區福住里信義路二段194號地下1樓之2",
            "106臺北市大安區四維路208巷10號之1",
            "404, Taichung City, North District, Alley 3, Lane 19, Taiping Rd, 15號之1",
            // The same, ending in a floor in another form, or after a space.
            "106台灣臺北市大安區信義路二段194號 地下1樓",
            "106台灣臺北市大安區信義路二段194號 2樓",
            "110台灣台北市信義區市府路45號B1",
            "106台灣臺北市大安區信義路二段194號3F",
            "106台灣臺北市大安區信義路二段194號三樓",
            "404, Taichung City, North District, Taiping Rd, 15號2樓",
            "2F, No. 194號, Section 2, Xinyi Rd, Da’an District, Taipei City, Taiwan 106",
        ).forEach { assertNull(PlaceTitle.split(it), it) }
    }

    @Test fun aTitleThatIsOnlyAnAddress_isKnownAsOne() {
        listOf(
            // An address searched on the Maps website, with or without its postcode and country.
            "106台北市大安區信義路二段194號",
            "台北市大安區信義路二段194號",
            "106台灣臺北市大安區福住里信義路二段194號2樓",
            "100台灣臺北市中正區重慶南路一段5號之1",
            "106台灣臺北市大安區信義路二段194號 地下1樓",
            "110台北市信義區市府路45號B1",
            "404, Taichung City, North District, Alley 3, Lane 19, Taiping Rd, 15號",
            "No. 194號, Section 2, Xinyi Rd, Da’an District, Taipei City, Taiwan 106",
            "2F, No. 194號, Section 2, Xinyi Rd, Da’an District, Taipei City, Taiwan 106",
        ).forEach { assertTrue(PlaceTitle.isAddressOnly(it), it) }
        listOf(
            "106台灣臺北市大安區福住里信義路二段194號鼎泰豐 信義店",
            "Din Tai Fung Xinyi Branch, No. 194號, Section 2, Xinyi Rd, Da’an District, Taipei City, Taiwan 106",
            "106台灣臺北市大安區福住里信義路二段194號 | ",
            "鼎泰豐 信義店",
            "85度C",
            "101大樓5號店",
            "100號咖啡",
            "Bar, No. 5",
        ).forEach { assertFalse(PlaceTitle.isAddressOnly(it), it) }
    }

    @Test fun whitespaceIsNormalized() {
        assertEquals(
            Split("鼎泰豐 信義店", "106台灣臺北市大安區福住里信義路二段194號", "臺北市大安區福住里信義路二段194號"),
            PlaceTitle.split("  106台灣臺北市大安區福住里信義路二段194號 鼎泰豐　 信義店 "),
        )
    }

    @Test fun anOverLongTitle_isNotSplit_andReturnsQuickly() {
        // Every real title is well under 200 characters: past the limit even a well-shaped one stays whole.
        val address = "106台灣臺北市大安區福住里信義路二段194號"
        val name = "鼎".repeat(PlaceTitle.MAX_LENGTH - address.length)
        assertEquals(name, PlaceTitle.split(address + name)?.name)
        assertNull(PlaceTitle.split(address + name + "鼎"))

        // Crafted so the address patterns would backtrack over every digit; it is refused unread.
        val crafted = "100臺北市" + "1".repeat(50_000 - "100臺北市".length)
        val (split, elapsed) = measureTimedValue { PlaceTitle.split(crafted) }
        assertNull(split)
        assertTrue(elapsed < 200.milliseconds, "took $elapsed")
    }
}
