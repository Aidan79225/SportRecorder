package com.crazystudio.sportrecorder.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

private const val LOOKUP_TIMEOUT_MS = 10_000L

/**
 * Android [AddressLookup] backed by the platform [Geocoder] in the device's locale, both ways: a
 * position to its address, and a Maps link's address to its position. The platform decides which
 * service answers; this adds no network host of its own. Any failure (no geocoder on the device, no
 * network, no result) and any lookup slower than [LOOKUP_TIMEOUT_MS] is a null, so one stuck lookup
 * never holds up whatever waits on it.
 */
class GeocoderAddressLookup(private val context: Context) : AddressLookup {
    override suspend fun addressAt(point: GeoPoint): String? =
        firstResult({ geocoder -> firstAddressAt(geocoder, point) }) { it.toShortAddress() }

    override suspend fun positionOf(query: String): GeoPoint? =
        firstResult({ geocoder -> firstAddressOf(geocoder, query) }) { it.toPosition() }

    /** The safe-degrade boundary every lookup goes through: [find] bounded in time, then [read]. */
    @Suppress("TooGenericExceptionCaught") // intentional safe-degrade boundary: any failure → null
    private suspend fun <T : Any> firstResult(find: suspend (Geocoder) -> Address?, read: (Address) -> T?): T? =
        try {
            if (Geocoder.isPresent()) {
                withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { find(Geocoder(context)) }?.let(read)
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private suspend fun firstAddressAt(geocoder: Geocoder, point: GeoPoint): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listened { listener -> geocoder.getFromLocation(point.lat, point.lng, 1, listener) }
        } else {
            @Suppress("DEPRECATION") // the listener overload only exists from API 33
            blocking { geocoder.getFromLocation(point.lat, point.lng, 1) }
        }

    private suspend fun firstAddressOf(geocoder: Geocoder, query: String): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listened { listener -> geocoder.getFromLocationName(query, 1, listener) }
        } else {
            @Suppress("DEPRECATION") // the listener overload only exists from API 33
            blocking { geocoder.getFromLocationName(query, 1) }
        }

    /**
     * Waits for the first answer [start]'s listener gives. Only one answer is ever taken: a second
     * callback, or one after the wait was cancelled, is dropped instead of resuming twice.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listened(start: (Geocoder.GeocodeListener) -> Unit): Address? =
        suspendCancellableCoroutine { continuation ->
            val answered = AtomicBoolean(false)
            fun answer(address: Address?) {
                if (answered.compareAndSet(false, true) && continuation.isActive) continuation.resume(address)
            }
            start(
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = answer(addresses.firstOrNull())

                    override fun onError(errorMessage: String?) = answer(null)
                },
            )
        }

    /**
     * The blocking call cannot be interrupted, so it runs unstructured on IO: cancelling the caller
     * (the timeout) abandons the wait at once instead of waiting for a call that may never return.
     */
    private suspend fun blocking(call: () -> List<Address>?): Address? {
        val pending = CoroutineScope(Dispatchers.IO).async { call()?.firstOrNull() }
        return try {
            pending.await()
        } finally {
            pending.cancel()
        }
    }

    private fun Address.toShortAddress(): String? =
        shortAddress(thoroughfare, subThoroughfare, if (maxAddressLineIndex >= 0) getAddressLine(0) else null)

    private fun Address.toPosition(): GeoPoint? =
        if (hasLatitude() && hasLongitude()) GeoPoint(latitude, longitude) else null
}

/**
 * The part of an address worth showing under a venue name: road plus number when the geocoder
 * knows both ("信義路二段 194號"), the road alone, else the first address line — a house number
 * without its road says nothing. Blank is null.
 */
internal fun shortAddress(thoroughfare: String?, subThoroughfare: String?, firstLine: String?): String? {
    val road = thoroughfare?.trim()?.ifEmpty { null }
    val number = subThoroughfare?.trim()?.ifEmpty { null }
    val line = firstLine?.trim()?.ifEmpty { null }
    return if (road != null) listOfNotNull(road, number).joinToString(" ") else line
}
