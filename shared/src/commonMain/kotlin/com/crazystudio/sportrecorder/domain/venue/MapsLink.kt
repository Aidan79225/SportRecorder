package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.VenueName

/**
 * A Google Maps URL that passed the host allowlist (#84). The constructors are `internal` on
 * purpose: outside `:shared` the only way to obtain a [Short] is [find], so a resolver that accepts
 * only [Short] can never be pointed at an un-allowlisted host.
 */
sealed interface MapsLink {
    val url: String

    /** A shortened link that must be resolved (followed) before it can be parsed. */
    class Short internal constructor(override val url: String) : MapsLink

    /** A full Maps URL that [parse] can read directly. */
    class Full internal constructor(override val url: String) : MapsLink

    companion object {
        /** First allowlisted Maps URL in [text], or null. */
        fun find(text: String): MapsLink? {
            var from = text.indexOf(HTTPS)
            var found: MapsLink? = null
            while (found == null && from >= 0) {
                found = classify(urlAt(text, from))
                from = text.indexOf(HTTPS, from + HTTPS.length)
            }
            return found
        }

        /**
         * What a full Maps URL says about the place; null unless [fullUrl]'s host is a full-Maps host.
         * A link with a pin reads as it always has. A pinless one — what the Maps apps make — has its
         * title split by [PlaceTitle] when that is confident, and says what to look up to place it.
         */
        fun parse(fullUrl: String): MapsPlace? =
            if (classify(fullUrl) is Full) placeOf(MapsUrl(fullUrl)) else null
    }
}

/**
 * What a Maps URL says about a place: its name and, when present, where it is. A pinless link made by
 * the Maps apps also gives the [address] it carries (trimmed of postcode and country), and
 * [geocodeQuery] — the text to look up to find where the place is: the full address when the title
 * split, else the whole title; null when the link has a pin, names nothing, or has a title longer than
 * [PlaceTitle.MAX_LENGTH] (no real one is).
 */
data class MapsPlace(
    val name: String?,
    val lat: Double?,
    val lng: Double?,
    val address: String? = null,
    val geocodeQuery: String? = null,
) {
    /**
     * [name] when it is the place's name alone; null when it is a pinless link's whole title, which
     * [PlaceTitle] could not split and so may be the address glued to the name. Being placed by
     * looking that title up does not change what it is; nor does being too long to look up (it then
     * has no position either).
     */
    val cleanName: String? get() = name.takeUnless { address == null && (geocodeQuery != null || lat == null) }
}

private const val HTTPS = "https://"
private const val SHORT_HOST = "maps.app.goo.gl"
private const val LEGACY_SHORT_HOST = "goo.gl"
private val FULL_HOSTS = setOf("www.google.com", "maps.google.com")

// The iOS Maps app's links have no path, only `?q=<title>&ftid=…` (#84 addendum).
private val Q_PATHS = setOf("", "/", "/maps")
private const val PLACE = "/place/"
private const val SEARCH = "/maps/search/"

// Characters a URL may contain (RFC 3986 unreserved + reserved + `%`); anything else ends it.
private const val URL_SYMBOLS = "-._~:/?#[]@!\$&'()*+,;=%"

// Punctuation that, at the very end, belongs to the sentence around the link, not to the link.
private const val TRAILING_PUNCTUATION = ".,;:!?)'"

private val PIN = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
private val CAMERA = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")

// A title or search that is only coordinates: "42.578496,1.895566", "39.920439, 116.331538", or a
// dropped pin's 25°02'00.6"N 121°31'48.4"E. Other apps' q= may say "loc:25.0335,121.5301", or label
// them, "25.0335,121.5301(鼎泰豐)": the label is the name. One cut short — a link shared as plain text
// ends at its CJK label — names nothing.
private val DECIMAL = Regex("""(?:loc: ?)?(-?\d{1,3}(?:\.\d+)?), ?(-?\d{1,3}(?:\.\d+)?)(?: ?\((?:(.*)\))?.*)?""")
private val DMS = Regex("""\d{1,3}°\d{1,2}'\d{1,2}(?:\.\d+)?"[NS] \d{1,3}°\d{1,2}'\d{1,2}(?:\.\d+)?"[EW]""")

/** A URL cut into the parts the allowlist and the parser look at. */
private class MapsUrl(url: String) {
    private val rest = url.substring(HTTPS.length).substringBefore('#')
    val host = rest.takeWhile { it != '/' && it != '?' }.lowercase()
    val path = rest.substring(host.length).substringBefore('?')
    private val query = rest.substringAfter('?', "")

    /** `/maps…`, or the pathless `?q=` form the iOS app makes; nothing else on a full-Maps host. */
    val isFullMaps get() = path == "/maps" || path.startsWith("/maps/") || hasTitleQuery

    private val hasTitleQuery get() = path in Q_PATHS && !param("q").isNullOrEmpty()

    /** The decoded, normalized text the link names the place by: the `/place/` title, or `q`. */
    val title: String? get() = decoded(segmentAfter(PLACE) ?: param("q")?.takeIf { path in Q_PATHS })

    /** The decoded `/maps/search/` text; only ever read as coordinates. */
    val search: String? get() = decoded(segmentAfter(SEARCH))

    private fun param(key: String): String? =
        query.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

    private fun segmentAfter(marker: String): String? =
        if (path.contains(marker)) path.substringAfter(marker).substringBefore('/') else null

    private fun decoded(raw: String?): String? = raw?.let { VenueName.normalize(percentDecode(it)) }?.ifEmpty { null }
}

/**
 * The URL starting at [start]: it runs until whitespace or any non-URL character (CJK text, `。`),
 * then sheds trailing sentence punctuation, so "(https://…/abc)." gives ".../abc".
 */
private fun urlAt(text: String, start: Int): String {
    var end = start
    while (end < text.length && text[end].isUrlChar()) end++
    while (end > start && text[end - 1] in TRAILING_PUNCTUATION) end--
    return text.substring(start, end)
}

private fun Char.isUrlChar() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in URL_SYMBOLS

/** Exact scheme + host + path match; never `contains`/`endsWith`, so lookalike hosts fall out. */
private fun classify(candidate: String): MapsLink? {
    if (!candidate.startsWith(HTTPS)) return null
    val url = MapsUrl(candidate)
    return when {
        url.host == SHORT_HOST && url.path.length > 1 -> MapsLink.Short(candidate)
        url.host == LEGACY_SHORT_HOST && url.path.startsWith("/maps/") -> MapsLink.Short(candidate)
        url.host in FULL_HOSTS && url.isFullMaps -> MapsLink.Full(candidate)
        else -> null
    }
}

/**
 * A title that is only coordinates is no name, though its label is; a link with a pin is never split
 * (its name may hold commas). An over-long title is neither split nor looked up: no real one is that long.
 */
private fun placeOf(url: MapsUrl): MapsPlace {
    val title = url.title
    val coordinateTitle = title?.let(DECIMAL::matchEntire)
    val name = if (coordinateTitle != null) label(coordinateTitle) else title?.takeUnless(DMS::matches)
    val (lat, lng) = coordinates(url, coordinateTitle)
    val split = if (lat == null) name?.let(PlaceTitle::split) else null
    return when {
        lat != null -> MapsPlace(name, lat, lng)
        split != null -> MapsPlace(split.name, null, null, split.trimmedAddress, geocodeQuery = split.address)
        else -> MapsPlace(name, null, null, geocodeQuery = name?.takeIf { it.length <= PlaceTitle.MAX_LENGTH })
    }
}

/**
 * The pin (`!3d…!4d…`) is the place itself, and so is a title or search that is only coordinates;
 * the `@` camera is only where the map was looking.
 */
private fun coordinates(url: MapsUrl, coordinateTitle: MatchResult?): Pair<Double?, Double?> {
    val match = PIN.find(url.path)
        ?: coordinateTitle
        ?: url.search?.let(DECIMAL::matchEntire)
        ?: CAMERA.find(url.path)
    val lat = match?.groupValues?.get(1)?.toDoubleOrNull()
    val lng = match?.groupValues?.get(2)?.toDoubleOrNull()
    val valid = lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0
    return if (valid) lat to lng else null to null
}

/** The label other apps put after the coordinates, `(鼎泰豐)`; null when there is none. */
private fun label(coordinates: MatchResult): String? = VenueName.normalize(coordinates.groupValues[3]).ifEmpty { null }

/** `%XX` runs are UTF-8 bytes and `+` is a space. Hand-rolled: there is no `java.net` in commonMain. */
private fun percentDecode(raw: String): String {
    val out = StringBuilder()
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < raw.length) {
        val byte = if (raw[i] == '%') hexByte(raw, i + 1) else null
        if (byte != null) {
            bytes.add(byte)
            i += 3
        } else {
            flush(bytes, out)
            out.append(if (raw[i] == '+') ' ' else raw[i])
            i++
        }
    }
    flush(bytes, out)
    return out.toString()
}

private fun hexByte(s: String, at: Int): Byte? {
    val hi = s.getOrNull(at)?.digitToIntOrNull(16)
    val lo = s.getOrNull(at + 1)?.digitToIntOrNull(16)
    return if (hi != null && lo != null) (hi * 16 + lo).toByte() else null
}

private fun flush(bytes: MutableList<Byte>, out: StringBuilder) {
    if (bytes.isNotEmpty()) {
        out.append(bytes.toByteArray().decodeToString())
        bytes.clear()
    }
}
