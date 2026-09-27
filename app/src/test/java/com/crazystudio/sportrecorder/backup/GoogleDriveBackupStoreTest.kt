package com.crazystudio.sportrecorder.backup

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoogleDriveBackupStoreTest {
    @get:Rule val photosDir = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var store: GoogleDriveBackupStore
    private val reports = mutableListOf<Triple<BackupStep, Int, Int>>()
    private val progress = BackupProgress { step, done, total ->
        synchronized(reports) { reports.add(Triple(step, done, total)) }
    }

    @Before fun setUp() {
        server.start()
        val drive = DriveRestClient(OkHttpClient(), server.url("/").toString().trimEnd('/'))
        store = GoogleDriveBackupStore({ "tok" }, { name -> File(photosDir.root, name) }, drive)
    }

    @After fun tearDown() { server.shutdown() }

    private fun manifestJson(photoNames: List<String>): String = BackupJson.encodeToString(
        BackupDocument.serializer(),
        BackupDocument(
            schemaVersion = BackupDocument.SCHEMA_VERSION,
            createdAt = 42L,
            appVersionName = "0.7.1",
            meals = photoNames.mapIndexed { i, name ->
                BackupMeal(i + 1, 1_000L + i, null, null, listOf(BackupPhoto(i + 1, name, 1L)))
            },
            fastingTypes = emptyList(),
            dietSettings = BackupDietSettings(16, 8),
            reminderPrefs = BackupReminderPrefs(false, false, 30, false, 1320, 480),
        ),
    )

    private fun drain(): List<RecordedRequest> = buildList {
        while (true) add(server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break)
    }

    @Test fun uploadSnapshot_uploadsOnlyMissingPhotos_manifestLast_withMealCount() = runTest {
        listOf("a.webp", "b.webp", "c.webp").forEach { File(photosDir.root, it).writeBytes(byteArrayOf(1)) }
        server.enqueue(
            MockResponse().setBody(
                """
                {"files":[{"id":"p-a","name":"a.webp","appProperties":{"kind":"photo"}}]}
                """.trimIndent(),
            ),
        )
        repeat(3) { server.enqueue(MockResponse().setBody("""{"id":"x"}""")) }

        val info = store.uploadSnapshot(
            manifestJson(listOf("a.webp", "b.webp", "c.webp")),
            listOf("a.webp", "b.webp", "c.webp"),
            progress,
        )

        val requests = drain()
        assertEquals(4, requests.size) // list + b + c + manifest
        assertEquals("GET", requests[0].method)
        val bodies = requests.drop(1).map { it.body.readUtf8() }
        assertTrue(bodies.last().contains("\"kind\":\"manifest\""))
        assertTrue(bodies.last().contains("\"mealCount\":\"3\""))
        val uploadedNames = bodies.dropLast(1).map { body ->
            Regex("\"name\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        }.toSet()
        assertEquals(setOf("b.webp", "c.webp"), uploadedNames)
        assertEquals(3, info.mealCount)
        assertEquals(42L, info.createdAt)
        assertTrue(reports.contains(Triple(BackupStep.UploadingPhotos, 2, 2)))
        assertTrue(reports.contains(Triple(BackupStep.UploadingManifest, 1, 1)))
    }

    @Test fun downloadPhotos_skipsPhotosAlreadyOnDevice() = runTest {
        File(photosDir.root, "a.webp").writeBytes(byteArrayOf(9))
        server.enqueue(
            MockResponse().setBody(
                """
                {"files":[{"id":"p-a","name":"a.webp","appProperties":{"kind":"photo"}},
                {"id":"p-b","name":"b.webp","appProperties":{"kind":"photo"}}]}
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("BBB"))

        store.downloadPhotos("snap", listOf("a.webp", "b.webp"), progress)

        val requests = drain()
        assertEquals(2, requests.size) // list + b only
        assertEquals("/drive/v3/files/p-b", requests[1].requestUrl!!.encodedPath)
        assertArrayEquals(byteArrayOf(9), File(photosDir.root, "a.webp").readBytes())
        assertEquals("BBB", File(photosDir.root, "b.webp").readText())
        assertTrue(reports.contains(Triple(BackupStep.DownloadingPhotos, 1, 1)))
    }

    @Test fun downloadPhotos_withNothingMissing_makesNoNetworkCall() = runTest {
        File(photosDir.root, "a.webp").writeBytes(byteArrayOf(9))

        store.downloadPhotos("snap", listOf("a.webp"), progress)

        assertTrue(drain().isEmpty())
        assertTrue(reports.contains(Triple(BackupStep.DownloadingPhotos, 0, 0)))
    }

    @Test fun downloadPhotos_rejectsPathTraversalNames() = runTest {
        val error = runCatching { store.downloadPhotos("snap", listOf("../evil.webp"), progress) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(File(photosDir.root.parentFile, "evil.webp").exists())
    }

    @Test fun listSnapshots_readsMealCountWhenPresent() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"files":[
                    {"id":"m1","name":"manifest-1.json","size":"5","appProperties":{"kind":"manifest","snapshotId":"s1","createdAt":"10","appVersionName":"0.7.1","mealCount":"12"}},
                    {"id":"m2","name":"manifest-2.json","size":"5","appProperties":{"kind":"manifest","snapshotId":"s2","createdAt":"20","appVersionName":"0.7.0"}}
                ]}""",
            ),
        )

        val snapshots = store.listSnapshots()

        assertEquals(listOf("s2", "s1"), snapshots.map { it.id })
        assertEquals(null, snapshots[0].mealCount)
        assertEquals(12, snapshots[1].mealCount)
    }
}
