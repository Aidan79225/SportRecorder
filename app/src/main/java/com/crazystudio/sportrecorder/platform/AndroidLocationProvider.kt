package com.crazystudio.sportrecorder.platform

import android.content.Context
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.util.LocationProvider as LocationUtil

/** Android [LocationProvider] backed by FusedLocationProvider (via [LocationUtil]). */
class AndroidLocationProvider(private val context: Context) : LocationProvider {
    override suspend fun currentLocation(): GeoPoint? =
        LocationUtil.currentLocation(context)?.let { (lat, lng) -> GeoPoint(lat, lng) }
}
