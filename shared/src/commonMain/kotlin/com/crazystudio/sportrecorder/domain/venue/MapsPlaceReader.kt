package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.platform.AddressLookup
import com.crazystudio.sportrecorder.platform.MapsLinkResolver

/**
 * Reads what a Maps link says about a place — the one way paste, share and 「貼上地圖連結更新」 read
 * a link. A short link is resolved first (one request, no redirects, #84) and [MapsLink.parse] then
 * vets the host it lands on. A link with a pin reads exactly as the link says. A pinless one — what
 * the Maps apps make — is placed by looking up its [MapsPlace.geocodeQuery] with the platform
 * geocoder (no second request to Google); when that finds nothing usable, the place keeps its name
 * and address with no position.
 */
class MapsPlaceReader(
    private val resolver: MapsLinkResolver,
    private val lookup: AddressLookup,
) {
    /** The place [link] names, or null when it can't be resolved or doesn't land on a full Maps URL. */
    suspend fun read(link: MapsLink): MapsPlace? {
        val fullUrl = when (link) {
            is MapsLink.Short -> resolver.resolve(link)
            is MapsLink.Full -> link.url
        }
        return fullUrl?.let(MapsLink::parse)?.let { placed(it) }
    }

    /** [place] with the position its address looks up to, when it has no pin of its own. */
    private suspend fun placed(place: MapsPlace): MapsPlace {
        val query = place.geocodeQuery
        if (place.position != null || query == null) return place
        val point = lookup.locate(query)
        return if (point != null) place.copy(lat = point.lat, lng = point.lng) else place
    }
}

/** Where the platform geocoder puts [query], or null when it finds nothing that is on the Earth. */
internal suspend fun AddressLookup.locate(query: String): GeoPoint? = positionOf(query)?.takeIf { it.isOnEarth }
