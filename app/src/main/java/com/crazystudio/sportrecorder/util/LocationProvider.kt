package com.crazystudio.sportrecorder.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

object LocationProvider {
    private const val TAG = "LocationProvider"

    /** Returns the current (lat, lng) or null if unavailable. Caller must hold a location permission. */
    @SuppressLint("MissingPermission")
    @Suppress("TooGenericExceptionCaught") // intentional safe-degrade boundary: any failure → null
    suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: client.lastLocation.await()
            loc?.let { it.latitude to it.longitude }
        } catch (e: SecurityException) {
            Log.w(TAG, "location unavailable: missing permission", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "location unavailable", e)
            null
        }
    }
}
