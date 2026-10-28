package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.VenueName

/**
 * The title a link made by the Google Maps **apps** carries is the place's address glued to its name:
 * 「106台灣臺北市大安區福住里信義路二段194號鼎泰豐 信義店」 (zh-TW), or "Din Tai Fung Xinyi Branch,
 * No. 194號, …, Taipei City, Taiwan 106" (en). [split] takes it apart.
 *
 * Confident or nothing: only the exact shapes the apps write for a Taiwanese place are split, and
 * anything else gives null so the caller keeps the whole title as the name. That is what keeps a name
 * the user typed (「85度C」, 「7-11 信義店」, "Café, Bar") from ever being split.
 */
object PlaceTitle {

    /**
     * [address] is the address exactly as the title gives it (what to geocode); [trimmedAddress] is the
     * same without the leading postcode and 台灣/臺灣, or the trailing ", Taiwan <postcode>" (what a
     * venue stores).
     */
    data class Split(val name: String, val address: String, val trimmedAddress: String)

    /** The name and address in [segment], or null when it is not in a shape the Maps apps make. */
    fun split(segment: String): Split? {
        val title = VenueName.normalize(segment)
        val split = addressFirst(title) ?: nameFirst(title)
        return split?.takeIf { it.name.any(Char::isLetterOrDigit) && !FLOOR_ONLY.matches(it.name) }
    }
}

// A Taiwanese postcode: 3 digits, or the 3+2 / 3+3 forms.
private const val POSTCODE = """\d{3}(?:\d{2,3})?"""

// The house number ends the address: …號, then maybe 之N, then maybe a floor (2樓, 地下1樓).
private const val NUMBER = """\d+號(?:之\d+)?"""
private const val FLOOR = """(?:(?:地下)?\d+樓(?:之\d+)?)?"""

// zh: postcode, maybe 台灣/臺灣, a city or county (臺北市, 屏東縣), the rest of the address with no space
// up to its first 號, then the name glued on — 「106台灣臺北市大安區…194號鼎泰豐 信義店」.
private val ZH_ADDRESS_FIRST = Regex("""$POSTCODE(?:台灣|臺灣)?([^\d\s,]{1,3}[市縣]\S*?$NUMBER$FLOOR)(.+)""")

// The same order with the address in English — 「404, Taichung City, …, Taiping Rd, 15號高沐手作料理餐廳」.
private val EN_ADDRESS_FIRST = Regex("""$POSTCODE, ([A-Z][A-Za-z’' -]* (?:City|County), .*?$NUMBER)(.+)""")

// en: the name, then an address that starts with "No." and ends with the country and postcode.
private val NAME_FIRST = Regex("""(.+?), (No\. .+?)(, Taiwan(?: \d{3,6})?)""")

// A floor the app put after the name belongs to the address: 「…7號癒所vegvísir｜預約制私廚1 樓」.
private val TRAILING_FLOOR = Regex("""(.*\D) ?(\d+ ?樓)""")

// What is left after an address that has no name: 「…194號2樓」 names nothing.
private val FLOOR_ONLY = Regex("""(?:地下)?\d+ ?樓(?:之\d+)?""")

private fun addressFirst(title: String): PlaceTitle.Split? {
    val match = ZH_ADDRESS_FIRST.matchEntire(title) ?: EN_ADDRESS_FIRST.matchEntire(title)
    return match?.let {
        val (trimmed, glued) = it.destructured
        val floored = TRAILING_FLOOR.matchEntire(glued.trim())
        val name = floored?.groupValues?.get(1) ?: glued
        val floor = floored?.groupValues?.get(2).orEmpty()
        PlaceTitle.Split(name.trim(), title.dropLast(glued.length) + floor, trimmed + floor)
    }
}

private fun nameFirst(title: String): PlaceTitle.Split? =
    NAME_FIRST.matchEntire(title)?.let {
        val (name, trimmed, country) = it.destructured
        PlaceTitle.Split(name, trimmed + country, trimmed)
    }
