package com.crazystudio.sportrecorder.ui.insights.map

import com.crazystudio.sportrecorder.domain.insights.LocationCount
import kotlin.math.hypot

/**
 * One marker on the map: a place, or several places whose markers would overlap. [name] is set when
 * exactly one named venue is in it (anonymous points riding along do not make that name ambiguous);
 * a marker that merged two or more differently named venues has no one name to carry.
 */
data class MapCluster(val lat: Double, val lng: Double, val count: Int, val places: Int, val name: String? = null)

/**
 * Merge places whose markers would overlap at this [viewport] (centres closer than
 * [mergeDistancePx]) into single markers with summed counts. Greedy and deterministic: biggest
 * places seed clusters first; a cluster's centre is the count-weighted mean of its members.
 */
fun clusterPlaces(places: List<LocationCount>, viewport: MapViewport, mergeDistancePx: Float): List<MapCluster> {
    class Working(var lat: Double, var lng: Double, var count: Int, var places: Int, var name: String?) {
        // Two different names met here: showing either would misattribute the other's visits, and
        // a later named place must not resurrect one of them.
        var ambiguous = false

        fun absorbName(other: String?) {
            if (other == null || other == name) return
            if (name == null && !ambiguous) {
                name = other
            } else {
                ambiguous = true
                name = null
            }
        }
    }
    val clusters = mutableListOf<Working>()
    val ordered = places.sortedWith(compareByDescending<LocationCount> { it.count }.thenBy { it.lat }.thenBy { it.lng })
    for (place in ordered) {
        val (px, py) = viewport.pixelFor(place.lat, place.lng)
        val target = clusters.firstOrNull { c ->
            val (cx, cy) = viewport.pixelFor(c.lat, c.lng)
            hypot((cx - px).toDouble(), (cy - py).toDouble()) < mergeDistancePx
        }
        if (target == null) {
            clusters += Working(place.lat, place.lng, place.count, 1, place.name)
        } else {
            val total = target.count + place.count
            target.lat = (target.lat * target.count + place.lat * place.count) / total
            target.lng = (target.lng * target.count + place.lng * place.count) / total
            target.count = total
            target.places += 1
            target.absorbName(place.name)
        }
    }
    return clusters.map { MapCluster(it.lat, it.lng, it.count, it.places, it.name) }.sortedByDescending { it.count }
}
