package com.crazystudio.sportrecorder.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazystudio.sportrecorder.data.PhotoFileStore
import com.crazystudio.sportrecorder.data.repository.EatRecordRepositoryImpl
import com.crazystudio.sportrecorder.data.repository.VenueRepositoryImpl
import com.crazystudio.sportrecorder.domain.model.EatRecord
import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.entity.EatTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against real Room: the merge is SQL, so a fake would prove nothing. */
@RunWith(AndroidJUnit4::class)
class VenueRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: VenueRepositoryImpl
    private lateinit var eatRepo: EatRecordRepositoryImpl

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()).build()
        repo = VenueRepositoryImpl(db, db.getVenueDao())
        eatRepo = EatRecordRepositoryImpl(
            db,
            db.getEatTimeDao(),
            db.getPhotoDao(),
            object : PhotoFileStore { override fun delete(fileName: String) = Unit },
        )
    }

    @After fun tearDown() = db.close()

    @Test fun findOrCreate_isIdempotentAcrossCaseAndSpacing() = runBlocking {
        val a = repo.findOrCreate("Starbucks", lat = 25.0, lng = 121.0, now = 1L)
        val b = repo.findOrCreate("  starbucks ", lat = null, lng = null, now = 2L)

        assertEquals(a.id, b.id)
        assertEquals(1, repo.observeAll().first().size)
        // The first position wins; a later mention without coordinates must not erase it.
        assertEquals(25.0, repo.observeAll().first().single().lat!!, 0.0001)
    }

    @Test fun findOrCreate_foldsNonAsciiCaseToo() = runBlocking {
        // SQLite NOCASE folds ASCII only; VenueName (the spec's definition) folds all of Unicode.
        val a = repo.findOrCreate("Café", null, null, now = 1L)
        val b = repo.findOrCreate("CAFÉ", null, null, now = 2L)

        assertEquals(a.id, b.id)
        assertEquals(1, repo.observeAll().first().size)
    }

    @Test fun observeAll_putsMostRecentlyUsedFirst() = runBlocking {
        repo.findOrCreate("A", null, null, now = 1L)
        val b = repo.findOrCreate("B", null, null, now = 2L)
        repo.touch(b.id, now = 9L)

        assertEquals(listOf("B", "A"), repo.observeAll().first().map { it.name })
    }

    @Test fun create_allowsASecondVenueWithTheSameName() = runBlocking {
        val first = repo.create("麥當勞", lat = 25.03, lng = 121.56, now = 1L)
        val second = repo.create("麥當勞", lat = 25.05, lng = 121.52, now = 2L)

        assertNotEquals(first.id, second.id)
        assertEquals(listOf("麥當勞", "麥當勞"), repo.observeAll().first().map { it.name })
        assertEquals(25.05, db.getVenueDao().findById(second.id)!!.lat!!, 0.0)
    }

    @Test fun findOrCreate_picksTheMostRecentlyUsedOfSameNames() = runBlocking {
        val older = repo.create("麥當勞", lat = 25.03, lng = 121.56, now = 1L)
        val newer = repo.create("麥當勞", lat = 25.05, lng = 121.52, now = 2L)
        repo.touch(older.id, now = 3L)

        val picked = repo.findOrCreate("麥當勞", lat = null, lng = null, now = 4L)

        assertEquals(older.id, picked.id)
        assertEquals(2, repo.observeAll().first().size)
        assertEquals(2L, db.getVenueDao().findById(newer.id)!!.lastUsedAt)
    }

    @Test fun findOrCreate_adoptingAFirstPosition_clearsTheAddress() = runBlocking {
        val v = repo.create("麥當勞", null, null, now = 1L)
        repo.edit(v.id, "麥當勞", address = "沒有位置時打的地址", now = 2L)

        val adopted = repo.findOrCreate("麥當勞", lat = 25.03, lng = 121.56, now = 3L)

        assertEquals(v.id, adopted.id)
        assertEquals(25.03, adopted.lat!!, 0.0)
        assertNull(adopted.address)
        assertNull(db.getVenueDao().findById(v.id)!!.address)
    }

    @Test fun findOrCreate_writesTheAddressOnInsert_andOnlyFillsAnEmptyOneOnReuse() = runBlocking {
        // Inserted: with the link's address, trimmed.
        val created = repo.findOrCreate("鼎泰豐 信義店", 25.03, 121.53, now = 1L, address = " 臺北市大安區信義路二段194號 ")
        assertEquals("臺北市大安區信義路二段194號", created.address)
        assertEquals("臺北市大安區信義路二段194號", db.getVenueDao().findById(created.id)!!.address)

        // Reused: an address it already has is never replaced.
        val again = repo.findOrCreate("鼎泰豐 信義店", 25.03, 121.53, now = 2L, address = "別的地址")
        assertEquals(created.id, again.id)
        assertEquals("臺北市大安區信義路二段194號", again.address)
        assertEquals("臺北市大安區信義路二段194號", db.getVenueDao().findById(created.id)!!.address)

        // Reused with none: it takes this one, and its position stays as it was.
        val empty = repo.create("一蘭", lat = 25.04, lng = 121.55, now = 3L)
        val filled = repo.findOrCreate("一蘭", lat = 25.0, lng = 121.0, now = 4L, address = "松壽路11號")
        assertEquals(empty.id, filled.id)
        assertEquals("松壽路11號", filled.address)
        val stored = db.getVenueDao().findById(empty.id)!!
        assertEquals("松壽路11號", stored.address)
        assertEquals(25.04, stored.lat!!, 0.0)

        // Learning its first position: the old address goes with the old (no) position, the link's comes with the new.
        val unplaced = repo.create("摩斯", null, null, now = 5L, address = "沒有位置時打的地址")
        val placed = repo.findOrCreate("摩斯", lat = 25.05, lng = 121.52, now = 6L, address = "忠孝東路四段5號")
        assertEquals(unplaced.id, placed.id)
        assertEquals("忠孝東路四段5號", db.getVenueDao().findById(unplaced.id)!!.address)

        // A blank address is none.
        assertNull(repo.findOrCreate("大戶屋", null, null, now = 7L, address = "   ").address)
        assertEquals(4, repo.observeAll().first().size)
    }

    @Test fun setPosition_clearsTheAddress() = runBlocking {
        val v = repo.create("麥當勞", lat = 25.03, lng = 121.56, now = 1L)
        repo.edit(v.id, "麥當勞", address = "信義路二段 194 號", now = 2L)

        repo.setPosition(v.id, lat = 25.05, lng = 121.52)

        val moved = repo.observeAll().first().single()
        assertEquals(25.05, moved.lat!!, 0.0)
        assertEquals(121.52, moved.lng!!, 0.0)
        assertNull("the old address described the old position", moved.address)
    }

    @Test fun setPosition_toTheSameCoordinates_keepsTheAddress() = runBlocking {
        val v = repo.create("麥當勞", lat = 25.03, lng = 121.56, now = 1L)
        repo.edit(v.id, "麥當勞", address = "信義路二段 194 號", now = 2L)

        repo.setPosition(v.id, lat = 25.03, lng = 121.56)

        val same = repo.observeAll().first().single()
        assertEquals(25.03, same.lat!!, 0.0)
        assertEquals("re-applying the same position is not a move", "信義路二段 194 號", same.address)
    }

    @Test fun setPosition_fromNoPosition_placesTheVenue() = runBlocking {
        val v = repo.create("麥當勞", lat = null, lng = null, now = 1L)

        repo.setPosition(v.id, lat = 25.03, lng = 121.56)

        val placed = repo.observeAll().first().single()
        assertEquals(25.03, placed.lat!!, 0.0)
        assertEquals(121.56, placed.lng!!, 0.0)
    }

    @Test fun create_withAnAddress_keepsItTrimmed() = runBlocking {
        val v = repo.create("鼎泰豐 信義店", lat = 25.03, lng = 121.53, now = 1L, address = " 臺北市大安區信義路二段194號 ")
        val blank = repo.create("一蘭", lat = null, lng = null, now = 2L, address = "   ")

        assertEquals("臺北市大安區信義路二段194號", v.address)
        assertEquals("臺北市大安區信義路二段194號", db.getVenueDao().findById(v.id)!!.address)
        assertNull(blank.address)
        assertNull(db.getVenueDao().findById(blank.id)!!.address)
    }

    @Test fun setPosition_withAnAddress_writesItWithTheMove() = runBlocking {
        val v = repo.create("鼎泰豐 信義店", lat = 25.0, lng = 121.0, now = 1L, address = "舊地址")

        // A Maps link's address belongs to the link's pin: it moves in with it.
        repo.setPosition(v.id, lat = 25.03, lng = 121.53, address = "臺北市大安區信義路二段194號")

        val moved = db.getVenueDao().findById(v.id)!!
        assertEquals(25.03, moved.lat!!, 0.0)
        assertEquals(121.53, moved.lng!!, 0.0)
        assertEquals("臺北市大安區信義路二段194號", moved.address)

        // Re-applying the same position is no move, so it writes nothing — not even the address.
        repo.setPosition(v.id, lat = 25.03, lng = 121.53, address = "別的地址")
        assertEquals("臺北市大安區信義路二段194號", db.getVenueDao().findById(v.id)!!.address)

        // Placing an unplaced venue with an address writes both.
        val unplaced = repo.create("一蘭", lat = null, lng = null, now = 2L, address = "沒有位置時打的地址")
        repo.setPosition(unplaced.id, lat = 25.04, lng = 121.55, address = "松壽路11號")
        assertEquals("松壽路11號", db.getVenueDao().findById(unplaced.id)!!.address)
    }

    @Test fun setAddressIfEmpty_writesOnlyIntoAnEmptyAddress() = runBlocking {
        val empty = repo.create("鼎泰豐 信義店", lat = 25.03, lng = 121.53, now = 1L)
        val typed = repo.create("一蘭", lat = null, lng = null, now = 2L, address = "我打的地址")

        assertTrue(repo.setAddressIfEmpty(empty.id, "臺北市大安區信義路二段194號"))
        assertEquals("臺北市大安區信義路二段194號", db.getVenueDao().findById(empty.id)!!.address)
        // The position is left exactly as it was.
        assertEquals(25.03, db.getVenueDao().findById(empty.id)!!.lat!!, 0.0)

        // Never over an address the venue already has — typed, or written a moment ago.
        assertFalse(repo.setAddressIfEmpty(typed.id, "松壽路11號"))
        assertEquals("我打的地址", db.getVenueDao().findById(typed.id)!!.address)
        assertFalse(repo.setAddressIfEmpty(empty.id, "別的地址"))
        assertEquals("臺北市大安區信義路二段194號", db.getVenueDao().findById(empty.id)!!.address)
        // A venue that is gone is no write.
        assertFalse(repo.setAddressIfEmpty(9_999, "松壽路11號"))
    }

    @Test fun setAddressIfUnchanged_writesOnlyWhenPositionAndEmptyAddressStillMatch() = runBlocking {
        // Matches: still at the looked-up position, address still empty → written.
        val a = repo.create("A", lat = 25.0, lng = 121.0, now = 1L)
        assertTrue(repo.setAddressIfUnchanged(a.id, lat = 25.0, lng = 121.0, address = "一路 1 號"))
        assertEquals("一路 1 號", db.getVenueDao().findById(a.id)!!.address)

        // The venue moved while the lookup ran → the result describes a stale position.
        val b = repo.create("B", lat = 25.0, lng = 121.0, now = 1L)
        repo.setPosition(b.id, lat = 25.1, lng = 121.1)
        assertFalse(repo.setAddressIfUnchanged(b.id, lat = 25.0, lng = 121.0, address = "二路 2 號"))
        assertNull(db.getVenueDao().findById(b.id)!!.address)

        // The user typed an address meanwhile → a lookup never overwrites it.
        val c = repo.create("C", lat = 25.0, lng = 121.0, now = 1L)
        repo.edit(c.id, "C", address = "我打的地址", now = 2L)
        assertFalse(repo.setAddressIfUnchanged(c.id, lat = 25.0, lng = 121.0, address = "三路 3 號"))
        assertEquals("我打的地址", db.getVenueDao().findById(c.id)!!.address)
    }

    @Test fun edit_savesNameAndAddress_blankAddressBecomesNull_andNeverMerges() = runBlocking {
        val other = repo.create("大戶屋", null, null, now = 1L)
        val v = repo.create("大戶屋 信義店", lat = 25.0, lng = 121.0, now = 2L)
        db.getEatTimeDao().insert(EatTime(time = 100L, venueId = v.id))

        val edited = repo.edit(v.id, "  大戶屋　", address = "信義路 1 號", now = 3L)

        assertEquals(v.id, edited.id)
        assertEquals("大戶屋", edited.name)
        assertEquals("信義路 1 號", edited.address)
        assertEquals(3L, edited.lastUsedAt)
        // Same name as another venue now, but both stay; nothing moved.
        assertEquals(setOf(other.id, v.id), repo.observeAll().first().map { it.id }.toSet())
        assertEquals(1, repo.recordCount(v.id))
        assertEquals(0, repo.recordCount(other.id))

        val cleared = repo.edit(v.id, "大戶屋", address = "   ", now = 4L)

        assertNull(cleared.address)
        assertNull(db.getVenueDao().findById(v.id)!!.address)
    }

    @Test fun merge_repointsRecordsAndDeletesTheOther_survivorKeepsItsOwnFields() = runBlocking {
        val typo = repo.create("大戶屋 信義店", lat = 25.0, lng = 121.0, now = 1L)
        repo.edit(typo.id, "大戶屋 信義店", address = "舊地址", now = 1L)
        val good = repo.create("大戶屋", lat = 25.1, lng = 121.1, now = 2L)
        repo.edit(good.id, "大戶屋", address = "好地址", now = 2L)
        db.getEatTimeDao().insert(EatTime(time = 100L, venueId = typo.id))
        db.getEatTimeDao().insert(EatTime(time = 200L, venueId = good.id))

        val survivor = repo.merge(fromId = typo.id, intoId = good.id, now = 3L)

        assertEquals(good.id, survivor.id)
        assertEquals("大戶屋", survivor.name)
        assertEquals("好地址", survivor.address)
        assertEquals(25.1, survivor.lat!!, 0.0)
        assertEquals(121.1, survivor.lng!!, 0.0)
        assertEquals(3L, survivor.lastUsedAt)
        assertEquals(2, repo.recordCount(good.id))
        assertNull(db.getVenueDao().findById(typo.id))
        assertEquals(listOf(survivor), repo.observeAll().first())
    }

    @Test fun merge_survivorAdoptingAPosition_losesItsAddress() = runBlocking {
        val from = repo.create("大戶屋 信義店", lat = 25.0, lng = 121.0, now = 1L)
        val into = repo.create("大戶屋", null, null, now = 2L)
        repo.edit(into.id, "大戶屋", address = "沒有位置時打的地址", now = 2L)

        val survivor = repo.merge(fromId = from.id, intoId = into.id, now = 3L)

        // It had no position, so it adopts the merged venue's — a position change, so the address goes.
        assertEquals(25.0, survivor.lat!!, 0.0)
        assertEquals(121.0, survivor.lng!!, 0.0)
        assertNull(survivor.address)
        assertNull(db.getVenueDao().findById(into.id)!!.address)
    }

    @Test fun delete_clearsRecordsVenueAndRemovesIt() = runBlocking {
        val v = repo.create("麥當勞", null, null, now = 1L)
        val kept = repo.create("摩斯", null, null, now = 2L)
        db.getEatTimeDao().insert(EatTime(time = 100L, note = "lunch", venueId = v.id))
        db.getEatTimeDao().insert(EatTime(time = 200L, venueId = kept.id))

        repo.delete(v.id)

        assertNull(db.getVenueDao().findById(v.id))
        val records = db.getEatTimeDao().flowAll().first().sortedBy { it.time }
        // The meal survives; only its venue is gone.
        assertEquals(2, records.size)
        assertEquals("lunch", records[0].note)
        assertNull(records[0].venueId)
        assertEquals(kept.id, records[1].venueId)
    }

    @Test fun replaceAll_returnsInsertedVenuesInOrderWithFreshIds() = runBlocking {
        val stale = repo.create("stale", null, null, now = 1L)

        val inserted = repo.replaceAll(
            listOf(
                Venue(id = 0, name = " 麥當勞 ", lat = 25.0, lng = 121.0, lastUsedAt = 5L, address = "一路 1 號"),
                Venue(id = 0, name = "麥當勞", lat = null, lng = null, lastUsedAt = 6L),
            ),
        )

        assertEquals(listOf("麥當勞", "麥當勞"), inserted.map { it.name })
        assertEquals(listOf("一路 1 號", null), inserted.map { it.address })
        assertEquals(listOf(5L, 6L), inserted.map { it.lastUsedAt })
        assertEquals(2, inserted.map { it.id }.toSet().size)
        assertFalse(inserted.any { it.id == 0 || it.id == stale.id })
        assertEquals(inserted.toSet(), repo.observeAll().first().toSet())
    }

    @Test fun aSavedRecordComesBackWithItsVenue() = runBlocking {
        val venue = repo.findOrCreate("大戶屋", lat = 25.0, lng = 121.0, now = 1L)
        db.getEatTimeDao().insert(
            EatTime(time = 100L, venueId = venue.id),
        )

        val loaded = db.getEatTimeDao().flowAllWithPhotos().first().single()

        assertEquals("大戶屋", loaded.venue?.name)
        // The record's own position is untouched by the venue — two different facts.
        assertNull(loaded.eatTime.lat)
    }

    // Takeaway eaten at home: the record is where you were (home), the venue is where the food
    // came from (the restaurant). The coordinates are deliberately far apart so a mix-up shows.
    private val home = GeoPoint(lat = 24.1, lng = 120.6)

    private suspend fun restaurant() = repo.findOrCreate("大戶屋", lat = 25.0, lng = 121.5, now = 1L)

    private fun assertHomeAndVenue(record: EatRecord?, venueId: Int) {
        assertNotNull(record)
        assertEquals(venueId, record!!.venue?.id)
        assertEquals("the venue's position must not leak onto the record", home, record.location)
    }

    @Test fun save_insert_persistsVenue_andKeepsRecordLocationSeparate() = runBlocking {
        val v = restaurant()

        val id = eatRepo.save(EatRecord(0, 100L, home, null, emptyList(), venue = v), emptyList(), emptyList())

        assertHomeAndVenue(eatRepo.findById(id), v.id)
    }

    @Test fun save_update_keepsTheVenue_whenOnlyTheNoteChanges() = runBlocking {
        val v = restaurant()
        val id = eatRepo.save(EatRecord(0, 100L, home, null, emptyList(), venue = v), emptyList(), emptyList())

        // What the editor does: load, change the note, save again.
        val loaded = eatRepo.findById(id)!!
        eatRepo.save(loaded.copy(note = "edited"), emptyList(), emptyList())

        val reloaded = eatRepo.findById(id)
        assertEquals("edited", reloaded?.note)
        assertHomeAndVenue(reloaded, v.id)
    }

    @Test fun save_update_canClearTheVenue() = runBlocking {
        val v = restaurant()
        val id = eatRepo.save(EatRecord(0, 100L, home, null, emptyList(), venue = v), emptyList(), emptyList())

        eatRepo.save(eatRepo.findById(id)!!.copy(venue = null), emptyList(), emptyList())

        assertNull(eatRepo.findById(id)?.venue)
    }

    @Test fun replaceAll_restoresTheVenue() = runBlocking {
        val v = restaurant()

        eatRepo.replaceAll(listOf(EatRecord(0, 100L, home, null, emptyList(), venue = v)))

        assertHomeAndVenue(eatRepo.observeAll().first().single(), v.id)
    }
}
