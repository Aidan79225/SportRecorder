package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.platform.AddressLookup
import kotlinx.coroutines.CompletableDeferred

/**
 * Test [AddressLookup]: answers every point with [address] (null models "could not look it up")
 * and records the points asked about. Set [gate] to hold the answer back, like a slow geocoder,
 * until the test completes it.
 */
class FakeAddressLookup(var address: String? = null) : AddressLookup {
    val asked = mutableListOf<GeoPoint>()

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun addressAt(point: GeoPoint): String? {
        asked += point
        gate?.await()
        return address
    }
}
