package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.platform.AddressLookup
import kotlinx.coroutines.CompletableDeferred

/**
 * Test [AddressLookup]: answers every point with [address] (null models "could not look it up")
 * and records the points asked about. Answers a place query from [positions] (a missing or null
 * entry models "could not find it") and records the queries. Set [gate] to hold the address answer
 * back, like a slow geocoder, until the test completes it.
 */
class FakeAddressLookup(
    var address: String? = null,
    var positions: Map<String, GeoPoint?> = emptyMap(),
) : AddressLookup {
    val asked = mutableListOf<GeoPoint>()

    val queried = mutableListOf<String>()

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun addressAt(point: GeoPoint): String? {
        asked += point
        gate?.await()
        return address
    }

    override suspend fun positionOf(query: String): GeoPoint? {
        queried += query
        return positions[query]
    }
}
