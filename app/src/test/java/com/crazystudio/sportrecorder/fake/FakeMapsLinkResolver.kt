package com.crazystudio.sportrecorder.fake

import com.crazystudio.sportrecorder.domain.venue.MapsLink
import com.crazystudio.sportrecorder.platform.MapsLinkResolver
import kotlinx.coroutines.CompletableDeferred

/**
 * Test [MapsLinkResolver]: answers every short link with [location] (null models "could not
 * resolve") and counts the calls. Set [gate] to hold the answer back, like a slow network, until
 * the test completes it.
 */
class FakeMapsLinkResolver(var location: String? = null) : MapsLinkResolver {
    var calls = 0
        private set

    var gate: CompletableDeferred<Unit>? = null

    override suspend fun resolve(link: MapsLink.Short): String? {
        calls++
        gate?.await()
        return location
    }
}
