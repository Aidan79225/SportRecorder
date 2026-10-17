package com.crazystudio.sportrecorder.platform

import com.crazystudio.sportrecorder.domain.venue.MapsLink

/**
 * Turns a shortened Google Maps link into the full URL it points at (#84). Takes only a
 * [MapsLink.Short], whose constructor is internal to `:shared`, so no host outside the allowlist
 * can ever be contacted. Platform-specific (Android: OkHttp).
 */
interface MapsLinkResolver {
    /** The 3xx `Location` for [link], or null on ANY failure. Never follows a redirect. */
    suspend fun resolve(link: MapsLink.Short): String?
}
