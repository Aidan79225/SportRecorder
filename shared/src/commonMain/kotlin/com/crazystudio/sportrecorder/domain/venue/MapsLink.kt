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

        /** Name + pin from a full Maps URL; null unless [fullUrl]'s host is a full-Maps host. */
        fun parse(fullUrl: String): MapsPlace? {
            if (classify(fullUrl) !is Full) return null
            val path = fullUrl.substring(HTTPS.length).substringAfter('/').substringBefore('?').substringBefore('#')
            val (lat, lng) = coordinates(path)
            return MapsPlace(placeName(path), lat, lng)
        }
    }
}

/** What a Maps URL says about a place: its name and, when present, where it is. */
data class MapsPlace(val name: String?, val lat: Double?, val lng: Double?)

private const val HTTPS = "https://"
private const val SHORT_HOST = "maps.app.goo.gl"
private const val LEGACY_SHORT_HOST = "goo.gl"
private val FULL_HOSTS = setOf("www.google.com", "maps.google.com")

// Characters a URL may contain (RFC 3986 unreserved + reserved + `%`); anything else ends it.
private const val URL_SYMBOLS = "-._~:/?#[]@!\$&'()*+,;=%"

// Punctuation that, at the very end, belongs to the sentence around the link, not to the link.
private const val TRAILING_PUNCTUATION = ".,;:!?)'"

private val PIN = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
private val CAMERA = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")

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
    val rest = candidate.substring(HTTPS.length)
    val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }.lowercase()
    val path = rest.substring(host.length).substringBefore('?').substringBefore('#')
    return when {
        host == SHORT_HOST && path.length > 1 -> MapsLink.Short(candidate)
        host == LEGACY_SHORT_HOST && path.startsWith("/maps/") -> MapsLink.Short(candidate)
        host in FULL_HOSTS && (path == "/maps" || path.startsWith("/maps/")) -> MapsLink.Full(candidate)
        else -> null
    }
}

/** The pin (`!3d…!4d…`) is the place itself; the `@` camera is only where the map was looking. */
private fun coordinates(path: String): Pair<Double?, Double?> {
    val match = PIN.find(path) ?: CAMERA.find(path)
    val lat = match?.groupValues?.get(1)?.toDoubleOrNull()
    val lng = match?.groupValues?.get(2)?.toDoubleOrNull()
    val valid = lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0
    return if (valid) lat to lng else null to null
}

private fun placeName(path: String): String? {
    if (!path.contains("/place/")) return null
    val segment = path.substringAfter("/place/").substringBefore('/')
    return percentDecode(segment).let(VenueName::normalize).ifEmpty { null }
}

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
