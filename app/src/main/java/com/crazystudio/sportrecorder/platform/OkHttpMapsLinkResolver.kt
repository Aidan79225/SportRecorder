package com.crazystudio.sportrecorder.platform

import com.crazystudio.sportrecorder.domain.venue.MapsLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TIMEOUT_SECONDS = 5L
private const val LOCATION_HEADER = "Location"

/**
 * Reads exactly one header: the `Location` of the redirect a short Maps link answers with. The
 * redirect itself is never followed, so the only host ever contacted is the one in the [MapsLink.Short].
 * It does not validate the `Location`; the caller does that via [MapsLink.parse].
 */
class OkHttpMapsLinkResolver(client: OkHttpClient) : MapsLinkResolver {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(link: MapsLink.Short): String? = resolveUrl(link.url)

    /** Test seam: a MockWebServer URL can never be a [MapsLink.Short]. */
    @Suppress("TooGenericExceptionCaught") // intentional safe-degrade boundary: any failure → null
    internal suspend fun resolveUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isRedirect) response.header(LOCATION_HEADER) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
