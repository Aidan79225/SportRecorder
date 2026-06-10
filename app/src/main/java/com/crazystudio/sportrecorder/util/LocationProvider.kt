package com.crazystudio.sportrecorder.util

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

object LocationProvider {
    /** Returns the current (lat, lng) or null if unavailable. Caller must hold a location permission. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: client.lastLocation.await()
            loc?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
