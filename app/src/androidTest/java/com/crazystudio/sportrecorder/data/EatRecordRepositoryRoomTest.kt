package com.crazystudio.sportrecorder.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.domain.model.EatPhoto
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EatRecordRepositoryRoomTest {
    private lateinit var h: RoomHarness

    @Before fun setUp() { h = RoomHarness() }
    @After fun tearDown() { h.close() }

    private suspend fun add(time: Long, note: String? = null, photos: List<String> = emptyList()): Int =
        h.eatRepo.save(EatRecord(0, time, null, note, emptyList()), photos, emptyList())

    @Test fun observeAll_isNewestFirst() = runBlocking {
        add(1_000); add(3_000); add(2_000)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), h.eatRepo.observeAll().first().map { it.time })
    }

    @Test fun observeInWindow_isAscending_andStrictlyExclusive() = runBlocking {
        listOf(100L, 200L, 300L, 400L, 500L).forEach { add(it) }
        val inWindow = h.eatRepo.observeInWindow(after = 200, before = 400).first().map { it.time }
        assertEquals(listOf(300L), inWindow) // 200 and 400 are excluded on both bounds
        assertEquals(
            listOf(200L, 300L, 400L),
            h.eatRepo.observeInWindow(after = 100, before = 500).first().map { it.time },
        )
    }

    @Test fun save_insert_assignsId_andStampsPhotos() = runBlocking {
        val id = add(1_000, "lunch", listOf("a.webp", "b.webp"))
        assertTrue(id > 0)
        val record = h.eatRepo.findById(id)!!
        assertEquals(setOf("a.webp", "b.webp"), record.photos.map { it.fileName }.toSet())
        assertTrue(record.photos.all { it.createdAt > 0 })
        assertTrue(record.photos.all { it.id > 0 })
    }

    @Test fun save_update_removesOnlyGivenPhotos_andDeletesTheirFiles() = runBlocking {
        val id = add(1_000, "lunch", listOf("a.webp", "b.webp"))
        val before = h.eatRepo.findById(id)!!
        val toRemove = before.photos.first { it.fileName == "a.webp" }

        h.eatRepo.save(
            before.copy(note = "late lunch", location = GeoPoint(25.0, 121.5)),
            listOf("c.webp"),
            listOf(toRemove),
        )

        val after = h.eatRepo.findById(id)!!
        assertEquals("late lunch", after.note)
        assertEquals(GeoPoint(25.0, 121.5), after.location)
        assertEquals(setOf("b.webp", "c.webp"), after.photos.map { it.fileName }.toSet())
        assertEquals(listOf("a.webp"), h.files.deleted)
    }

    @Test fun delete_removesRecordPhotosAndFiles() = runBlocking {
        val id = add(1_000, "lunch", listOf("a.webp", "b.webp"))
        add(2_000)

        h.eatRepo.delete(id)

        assertEquals(listOf(2_000L), h.eatRepo.observeAll().first().map { it.time })
        assertTrue(h.photoDao.findByEatTimeId(id).isEmpty())
        assertEquals(setOf("a.webp", "b.webp"), h.files.deleted.toSet())
    }

    @Test fun replaceAll_rollsBackWhenAPhotoInsertFails() = runBlocking {
        add(1_000, "keep", listOf("k.webp"))
        val before = h.eatRepo.observeAll().first()
        // Setup's k.webp above was insert #1 (cumulative counter). Failing on #3 lets n1.webp
        // land as #2, then n2.webp throws as #3 -- proving the transaction rolls back even after
        // one of the new photos was already written.
        h.photoDao.failOnInsertNumber = 3

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                h.eatRepo.replaceAll(
                    listOf(
                        EatRecord(0, 5_000, null, "new-1", listOf(EatPhoto(0, "n1.webp", 1L))),
                        EatRecord(0, 6_000, null, "new-2", listOf(EatPhoto(0, "n2.webp", 1L))),
                    ),
                )
            }
        }

        assertTrue(error.message!!.contains("simulated"))
        assertEquals(before, h.eatRepo.observeAll().first()) // transaction rolled back
        assertEquals(listOf("k.webp"), h.photoDao.findByEatTimeId(before.single().id).map { it.fileName })
        assertTrue(h.files.deleted.isEmpty())
    }

    @Test fun replaceAll_neverDeletesFiles() = runBlocking {
        add(1_000, "old", listOf("old.webp"))
        h.eatRepo.replaceAll(listOf(EatRecord(0, 9_000, null, "new", listOf(EatPhoto(0, "new.webp", 42L)))))
        val after = h.eatRepo.observeAll().first().single()
        assertEquals("new", after.note)
        assertEquals(42L, after.photos.single().createdAt) // replaceAll keeps the backed-up timestamp
        assertTrue(h.files.deleted.isEmpty())
    }
}
