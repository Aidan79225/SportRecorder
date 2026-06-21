package com.crazystudio.sportrecorder.platform

import com.crazystudio.sportrecorder.domain.model.GeoPoint

/**
 * Supplies the device's current location. Platform-specific (Android: FusedLocationProvider;
 * iOS: CoreLocation). Returns null when unavailable; the caller is responsible for holding a
 * location permission. First of the Phase 4 platform-capability abstractions that let shared
 * UI/ViewModels reach device services without depending on platform APIs directly.
 */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint?
}
