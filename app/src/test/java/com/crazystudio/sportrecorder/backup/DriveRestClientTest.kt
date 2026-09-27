package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveRestClientTest {
    private val server = MockWebServer()
    private lateinit var client: DriveRestClient

    @Before fun setUp() {
        server.start()
        client = DriveRestClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun listAppDataFiles_followsNextPageToken() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"nextPageToken":"page-2","files":[
                {"id":"1","name":"a.webp","size":"10","appProperties":{"kind":"photo"}}]}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {"files":[{"id":"2","name":"manifest-x.json","size":"20",
                "appProperties":{"kind":"manifest","snapshotId":"x"}}]}
                """.trimIndent(),
            ),
        )

        val files = client.listAppDataFiles("tok")

        assertEquals(listOf("1", "2"), files.map { it.id })
        assertEquals(mapOf("kind" to "manifest", "snapshotId" to "x"), files[1].appProperties)
        assertEquals(20L, files[1].sizeBytes)

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer tok", first.getHeader("Authorization"))
        assertTrue(first.requestUrl!!.queryParameter("pageToken") == null)
        assertEquals("page-2", second.requestUrl!!.queryParameter("pageToken"))
        assertEquals("appDataFolder", second.requestUrl!!.queryParameter("spaces"))
    }

    @Test fun uploadMultipart_sendsMetadataThenBytes() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"new"}"""))

        client.uploadMultipart("tok", "a.webp", "image/webp", mapOf("kind" to "photo"), byteArrayOf(1, 2, 3))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("multipart", request.requestUrl!!.queryParameter("uploadType"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"name\":\"a.webp\""))
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"kind\":\"photo\""))
    }

    @Test fun failedResponse_throwsIoExceptionWithDriveReason() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"accessNotConfigured"}}"""))

        val error = runCatching { client.downloadBytes("tok", "f1") }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertTrue(error!!.message!!.contains("403"))
        assertTrue(error.message!!.contains("accessNotConfigured"))
    }
}
