package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.platform.LocationProvider

/** Test [LocationProvider] returning a canned point, or null to model "location unavailable". */
class FakeLocationProvider(
    var location: GeoPoint? = null,
) : LocationProvider {
    var requestCount = 0
        private set

    override suspend fun currentLocation(): GeoPoint? {
        requestCount++
        return location
    }
}
