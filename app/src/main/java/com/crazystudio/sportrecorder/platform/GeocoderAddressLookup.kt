package com.crazystudio.sportrecorder.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

private const val LOOKUP_TIMEOUT_MS = 10_000L

/**
 * Android [AddressLookup] backed by the platform [Geocoder] in the device's locale. The platform
 * decides which service answers; this adds no network host of its own. Any failure (no geocoder
 * on the device, no network, no result) and any lookup slower than [LOOKUP_TIMEOUT_MS] is a null,
 * so one stuck lookup never holds up the venues queued behind it.
 */
class GeocoderAddressLookup(private val context: Context) : AddressLookup {
    @Suppress("TooGenericExceptionCaught") // intentional safe-degrade boundary: any failure → null
    override suspend fun addressAt(point: GeoPoint): String? =
        try {
            if (Geocoder.isPresent()) {
                withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { firstAddress(Geocoder(context), point) }?.toShortAddress()
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private suspend fun firstAddress(geocoder: Geocoder, point: GeoPoint): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    point.lat,
                    point.lng,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        } else {
            blockingFirstAddress(geocoder, point)
        }

    /**
     * The blocking call cannot be interrupted, so it runs unstructured: cancelling the caller (the
     * timeout) abandons the wait at once instead of waiting for a call that may never return.
     */
    private suspend fun blockingFirstAddress(geocoder: Geocoder, point: GeoPoint): Address? {
        val pending = CoroutineScope(Dispatchers.IO).async {
            @Suppress("DEPRECATION") // the listener overload only exists from API 33
            geocoder.getFromLocation(point.lat, point.lng, 1)?.firstOrNull()
        }
        return try {
            pending.await()
        } finally {
            pending.cancel()
        }
    }

    private fun Address.toShortAddress(): String? =
        shortAddress(thoroughfare, subThoroughfare, if (maxAddressLineIndex >= 0) getAddressLine(0) else null)
}

/**
 * The part of an address worth showing under a venue name: road plus number when the geocoder
 * knows both ("信義路二段 194號"), the road alone, else the first address line. Blank is null.
 */
internal fun shortAddress(thoroughfare: String?, subThoroughfare: String?, firstLine: String?): String? {
    val road = thoroughfare?.trim()?.ifEmpty { null }
    val number = subThoroughfare?.trim()?.ifEmpty { null }
    val line = firstLine?.trim()?.ifEmpty { null }
    return if (road != null || number != null) listOfNotNull(road, number).joinToString(" ") else line
}
