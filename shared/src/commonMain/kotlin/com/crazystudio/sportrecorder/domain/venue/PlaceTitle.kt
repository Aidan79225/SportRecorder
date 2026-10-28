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
     * The longest title worth reading, normalized. Every real one is under 200 characters; the patterns
     * below backtrack quadratically on crafted text, and a shared link is parsed on the main thread.
     */
    const val MAX_LENGTH = 300

    /**
     * [fullAddress] is the address exactly as the title gives it (what to geocode); [address] is the
     * same without the leading postcode and 台灣/臺灣, or the trailing ", Taiwan <postcode>" (what a
     * venue stores, as [MapsPlace.address] and `Venue.address` are).
     */
    data class Split(val name: String, val fullAddress: String, val address: String)

    /**
     * The name and address in [segment], or null when it is not in a shape the Maps apps make — or is
     * longer than [MAX_LENGTH], which no real title is.
     */
    fun split(segment: String): Split? {
        val title = VenueName.normalize(segment).takeIf { it.length <= MAX_LENGTH }
        val split = title?.let { addressFirst(it) ?: nameFirst(it) }
        return split?.takeIf { it.name.any(Char::isLetterOrDigit) && !FLOOR_ONLY.matches(it.name) }
    }

    /**
     * Whether [title] is only an address, with no name, in the shapes above (the postcode optional): the
     * title of a link to an address searched on the Maps website, 「106台北市大安區信義路二段194號」.
     */
    fun isAddressOnly(title: String): Boolean {
        val normalized = VenueName.normalize(title)
        return normalized.length <= MAX_LENGTH && ADDRESS_ONLY.any { it.matches(normalized) }
    }
}

// A Taiwanese postcode: 3 digits, or the 3+2 / 3+3 forms.
private const val POSTCODE = """\d{3}(?:\d{2,3})?"""

// The house number ends the address: …號, then maybe 之N, then maybe a floor.
// A 之N is taken whole — never 之1 out of 之12 — so the name can't start inside it.
private const val ZHI = """之\d+(?!\d)"""
private const val NUMBER = """\d+號(?:$ZHI)?"""

// A floor as the apps and the businesses write it: 2樓, 1 樓, 地下1樓, 三樓, B1, 3F, maybe then 之N.
// B1 and 3F must end there, so a name that only starts like one (「B2B咖啡」, "5Fun Bar") keeps its start.
private const val LATIN_FLOOR = """(?:[Bb]\d+(?![\dA-Za-z])|\d+[Ff](?![A-Za-z]))"""
private const val DIGIT_FLOOR = """(?:地下)?\d+ ?樓"""
private const val CJK_NUMERAL_FLOOR = """(?:地下)?[一二三四五六七八九十]+ ?樓"""
private const val FLOOR_FORM = """(?:$LATIN_FLOOR|$DIGIT_FLOOR|$CJK_NUMERAL_FLOOR)(?:$ZHI)?"""

// Maybe a floor after the number, right after it or after a space: 「194號2樓」, 「194號 B1」.
private const val FLOOR = """(?: ?$FLOOR_FORM)?"""

// The name glued on after the address. It never starts with a 之N: that is the address's own tail,
// left over when the title is only an address — 「…5號之1」 names nothing.
private const val NAME = """(?! ?之\d)(.+)"""

// The addresses the apps write, each spelled once so that splitting a title and recognising a title
// that is only an address can never drift apart.
private const val TAIWAN = """(?:台灣|臺灣)"""

// zh: a city or county (臺北市, 屏東縣), then the rest of the address with no space up to its first 號.
private const val ZH_ADDRESS = """[^\d\s,]{1,3}[市縣]\S*?$NUMBER$FLOOR"""

// en, city first: "Taichung City, …, Taiping Rd, 15號".
private const val EN_CITY_ADDRESS = """[A-Z][A-Za-z’' -]* (?:City|County), .*?$NUMBER$FLOOR"""

// en, number first: "No. …" — or its floor first, "2F, No. …" — up to the country.
private const val EN_NUMBER_ADDRESS = """(?:$FLOOR_FORM, )?No\. .+?"""
private const val EN_COUNTRY_TAIL = """, Taiwan(?: \d{3,6})?"""

// zh: postcode, maybe 台灣/臺灣, the address, then the name glued on — 「106台灣臺北市大安區…194號鼎泰豐 信義店」.
private val ZH_ADDRESS_FIRST = Regex("""$POSTCODE$TAIWAN?($ZH_ADDRESS)$NAME""")

// The same order with the address in English — 「404, Taichung City, …, Taiping Rd, 15號高沐手作料理餐廳」.
private val EN_ADDRESS_FIRST = Regex("""$POSTCODE, ($EN_CITY_ADDRESS)$NAME""")

// en: the name, then the address, and it ends with the country and postcode.
private val NAME_FIRST = Regex("""(.+?), ($EN_NUMBER_ADDRESS)($EN_COUNTRY_TAIL)""")

// A floor the app put after the name belongs to the address: 「…7號癒所vegvísir｜預約制私廚1 樓」, or
// 「…私廚地下1樓」, 「…私廚 B1」. The name is the shortest start that leaves only a floor, so a 地下 goes
// with its floor; B1 or 3F must not be glued to letters or digits before it (「CLUB1」 is a name). A
// Chinese-numeral floor is not taken from the end of a name, which may well end in one (「第一樓」).
private val TRAILING_FLOORS = listOf(
    Regex("""(.*?\D) ?($DIGIT_FLOOR(?:$ZHI)?)"""),
    Regex("""(.*?[^\dA-Za-z]) ?($LATIN_FLOOR(?:$ZHI)?)"""),
)

// What is left after an address that has no name: 「…194號2樓」 names nothing.
private val FLOOR_ONLY = Regex(FLOOR_FORM)

// A whole title that is the address alone, in each of the orders above (the postcode optional).
private val ADDRESS_ONLY = listOf(
    Regex("""(?:$POSTCODE)?$TAIWAN?$ZH_ADDRESS"""),
    Regex("""(?:$POSTCODE, )?$EN_CITY_ADDRESS"""),
    Regex("""$EN_NUMBER_ADDRESS$EN_COUNTRY_TAIL"""),
)

// What follows the address may be only a floor (「…194號 地下1樓」): that is no name, not even in part.
private fun addressFirst(title: String): PlaceTitle.Split? {
    val match = ZH_ADDRESS_FIRST.matchEntire(title) ?: EN_ADDRESS_FIRST.matchEntire(title)
    return match?.takeUnless { FLOOR_ONLY.matches(it.groupValues[2].trim()) }?.let {
        val (address, glued) = it.destructured
        val floored = TRAILING_FLOORS.firstNotNullOfOrNull { floor -> floor.matchEntire(glued.trim()) }
        val name = floored?.groupValues?.get(1) ?: glued
        val floor = floored?.groupValues?.get(2).orEmpty()
        PlaceTitle.Split(name.trim(), fullAddress = title.dropLast(glued.length) + floor, address = address + floor)
    }
}

private fun nameFirst(title: String): PlaceTitle.Split? =
    NAME_FIRST.matchEntire(title)?.let {
        val (name, address, country) = it.destructured
        PlaceTitle.Split(name, fullAddress = address + country, address = address)
    }
