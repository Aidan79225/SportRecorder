package com.crazystudio.sportrecorder.platform

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpMapsLinkResolverTest {
    private val server = MockWebServer()
    private lateinit var resolver: OkHttpMapsLinkResolver
    private lateinit var url: String

    @Before fun setUp() {
        server.start()
        url = server.url("/abc").toString()
        resolver = OkHttpMapsLinkResolver(OkHttpClient())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun returnsTheLocationOfA302() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", REAL))

        assertEquals(REAL, resolver.resolveUrl(url))
    }

    @Test fun doesNotFollowTheRedirect() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/second").toString()))
        server.enqueue(MockResponse().setBody("second hop"))

        resolver.resolveUrl(url)

        assertEquals(1, server.requestCount)
    }

    @Test fun nonRedirectIsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))

        assertNull(resolver.resolveUrl(url))
    }

    @Test fun redirectWithoutLocationIsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))

        assertNull(resolver.resolveUrl(url))
    }

    @Test fun unreachableHostIsNull() = runTest {
        server.shutdown()

        assertNull(resolver.resolveUrl(url))
    }

    @Test fun timesOutAfterFiveSeconds() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", REAL)
                .setHeadersDelay(6, TimeUnit.SECONDS),
        )

        val start = System.nanoTime()
        val result = resolver.resolveUrl(url)
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        assertNull(result)
        assertTrue("took ${elapsedMs}ms", elapsedMs < 6_500)
    }

    @Test fun sendsNoCookiesOrAuth() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", REAL))

        resolver.resolveUrl(url)

        val request = server.takeRequest()
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Authorization"))
    }

    private companion object {
        const val REAL = "https://www.google.com/maps/place/Foo/@25.03,121.56,17z"
    }
}
