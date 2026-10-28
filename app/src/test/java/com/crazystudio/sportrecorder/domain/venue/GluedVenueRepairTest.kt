package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.GeoPoint
import com.crazystudio.sportrecorder.domain.model.Venue
import com.crazystudio.sportrecorder.fake.FakeAddressLookup
import com.crazystudio.sportrecorder.fake.FakeVenueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// What 0.14.0 named a venue pasted from a link made by the Google Maps Android app: the whole /place/
// title of the real Location (2026-10-28 diagnosis), address and name glued together.
private const val GLUED_ZH = "106台灣臺北市大安區福住里信義路二段194號鼎泰豐 信義店"
private const val FULL_ADDRESS = "106台灣臺北市大安區福住里信義路二段194號"
private const val TRIMMED_ADDRESS = "臺北市大安區福住里信義路二段194號"

private const val GLUED_EN = "Din Tai Fung Xinyi Branch, No. 194號, Section 2, Xinyi Rd, Fuzhu Village, " +
    "Da’an District, Taipei City, Taiwan 106"
private const val EN_TRIMMED_ADDRESS = "No. 194號, Section 2, Xinyi Rd, Fuzhu Village, Da’an District, Taipei City"
private const val EN_FULL_ADDRESS = "$EN_TRIMMED_ADDRESS, Taiwan 106"

private val DIN_TAI_FUNG = GeoPoint(25.0335035, 121.530118)

@OptIn(ExperimentalCoroutinesApi::class)
class GluedVenueRepairTest {
    private val lookup = FakeAddressLookup(
        positions = mapOf(FULL_ADDRESS to DIN_TAI_FUNG, EN_FULL_ADDRESS to DIN_TAI_FUNG),
    )

    @Test fun aGluedZhVenue_isRenamedAndGetsAddressAndPin() = runTest {
        val repo = FakeVenueRepository(listOf(Venue(id = 1, name = GLUED_ZH, lat = null, lng = null, lastUsedAt = 5)))

        GluedVenueRepair(repo, lookup).run()

        // lastUsedAt is kept: a repair is not a use, so the picker's order stays as it was.
        assertEquals(
            listOf(Venue(1, "鼎泰豐 信義店", 25.0335035, 121.530118, lastUsedAt = 5, address = TRIMMED_ADDRESS)),
            repo.stored,
        )
        assertEquals(listOf(FULL_ADDRESS), lookup.queried)
    }

    @Test fun aGluedEnVenue_isRenamedAndGetsAddressAndPin() = runTest {
        val repo = FakeVenueRepository(listOf(Venue(id = 1, name = GLUED_EN, lat = null, lng = null, lastUsedAt = 5)))

        GluedVenueRepair(repo, lookup).run()

        assertEquals(
            listOf(Venue(1, "Din Tai Fung Xinyi Branch", 25.0335035, 121.530118, 5, address = EN_TRIMMED_ADDRESS)),
            repo.stored,
        )
        assertEquals(listOf(EN_FULL_ADDRESS), lookup.queried)
    }

    @Test fun aGluedVenueNearASameNameVenue_isMergedIntoIt() = runTest {
        // About 14 m from where the link's address looks up to.
        val near = Venue(id = 1, name = "鼎泰豐 信義店", lat = 25.0334, lng = 121.5302, lastUsedAt = 9)
        val glued = Venue(id = 2, name = GLUED_ZH, lat = null, lng = null, lastUsedAt = 12)
        val repo = FakeVenueRepository(listOf(near, glued))
        repo.recordCounts[1] = 3
        repo.recordCounts[2] = 2

        GluedVenueRepair(repo, lookup).run()

        // The survivor keeps its own name and position, takes the later use and the address it lacked.
        assertEquals(listOf(near.copy(lastUsedAt = 12, address = TRIMMED_ADDRESS)), repo.stored)
        assertEquals(5, repo.recordCount(1))
    }

    @Test fun theVenueItMergesInto_keepsItsOwnNameAndAddress() = runTest {
        val near = Venue(1, "DIN TAI FUNG Xinyi Branch", 25.0334, 121.5302, 9, address = "信義路二段 194 號")
        val repo = FakeVenueRepository(listOf(near, Venue(2, GLUED_EN, null, null, lastUsedAt = 4)))
        repo.recordCounts[2] = 2

        GluedVenueRepair(repo, lookup).run()

        assertEquals(listOf(near), repo.stored)
        assertEquals(2, repo.recordCount(1))
    }

    @Test fun aGluedVenueFarFromASameNameVenue_staysABranch() = runTest {
        // About 1.7 km away: another 鼎泰豐 信義店 as far as the app can tell.
        val far = Venue(1, "鼎泰豐 信義店", 25.0418, 121.5440, lastUsedAt = 9, address = "忠孝東路四段")
        val repo = FakeVenueRepository(listOf(far, Venue(2, GLUED_ZH, null, null, lastUsedAt = 7)))
        repo.recordCounts[2] = 2

        GluedVenueRepair(repo, lookup).run()

        assertEquals(
            listOf(far, Venue(2, "鼎泰豐 信義店", 25.0335035, 121.530118, 7, address = TRIMMED_ADDRESS)),
            repo.stored,
        )
        assertEquals(2, repo.recordCount(2))
        assertEquals(0, repo.recordCount(1))
    }

    @Test fun typedNames_areNeverTouched() = runTest {
        val typed = listOf(
            Venue(1, "85度C", null, null, lastUsedAt = 1),
            Venue(2, "7-11 信義店", 25.03, 121.56, lastUsedAt = 2),
            Venue(3, "Café, Bar", null, null, lastUsedAt = 3, address = "Somewhere 1"),
            Venue(4, "鼎泰豐 信義店", null, null, lastUsedAt = 4),
        )
        val repo = FakeVenueRepository(typed)
        lookup.positions = typed.associate { it.name to DIN_TAI_FUNG }

        GluedVenueRepair(repo, lookup).run()

        assertEquals(typed, repo.stored)
        assertTrue(lookup.queried.isEmpty())
    }

    @Test fun aGluedVenueThatHasAPosition_keepsIt_andItsAddress() = runTest {
        // It learned a position from a record and its address from the reverse lookup.
        val glued = Venue(1, GLUED_ZH, 25.0337, 121.5299, lastUsedAt = 5, address = "信義路二段 194 號")
        val repo = FakeVenueRepository(listOf(glued))

        GluedVenueRepair(repo, lookup).run()

        assertEquals(listOf(glued.copy(name = "鼎泰豐 信義店")), repo.stored)
        assertTrue(lookup.queried.isEmpty())
    }

    @Test fun runningTwice_changesNothing() = runTest {
        val repo = FakeVenueRepository(
            listOf(
                Venue(1, "鼎泰豐 信義店", 25.0334, 121.5302, lastUsedAt = 9),
                Venue(2, GLUED_ZH, null, null, lastUsedAt = 7),
                Venue(3, GLUED_EN, null, null, lastUsedAt = 6),
                Venue(4, "85度C", null, null, lastUsedAt = 5),
            ),
        )
        repo.recordCounts[2] = 2
        // The en address can't be placed: the next start must not look it up again.
        lookup.positions = mapOf(FULL_ADDRESS to DIN_TAI_FUNG)
        val repair = GluedVenueRepair(repo, lookup)

        repair.run()
        val afterFirst = repo.stored
        val queriesAfterFirst = lookup.queried.toList()
        val countsAfterFirst = repo.recordCounts.toMap()
        repair.run()

        assertEquals(listOf("鼎泰豐 信義店", "Din Tai Fung Xinyi Branch", "85度C"), afterFirst.map { it.name })
        assertEquals(afterFirst, repo.stored)
        assertEquals(queriesAfterFirst, lookup.queried)
        assertEquals(countsAfterFirst, repo.recordCounts)
    }

    @Test fun aFailedLookup_stillFixesNameAndAddress() = runTest {
        val repo = FakeVenueRepository(listOf(Venue(1, GLUED_ZH, null, null, lastUsedAt = 5)))
        lookup.positions = emptyMap()

        GluedVenueRepair(repo, lookup).run()

        assertEquals(listOf(Venue(1, "鼎泰豐 信義店", null, null, 5, address = TRIMMED_ADDRESS)), repo.stored)
        assertEquals(listOf(FULL_ADDRESS), lookup.queried)
    }

    @Test fun aVenueTheUserRenamesWhileItIsLookedUp_isLeftAlone() = runTest {
        val repo = FakeVenueRepository(listOf(Venue(1, GLUED_ZH, null, null, lastUsedAt = 5)))
        lookup.positionGate = CompletableDeferred()

        val job = launch { GluedVenueRepair(repo, lookup).run() }
        runCurrent() // waiting for the position of its address
        repo.edit(1, "我的鼎泰豐", null, now = 5)
        lookup.positionGate?.complete(Unit)
        job.join()

        assertEquals(listOf(Venue(1, "我的鼎泰豐", null, null, lastUsedAt = 5)), repo.stored)
    }

    @Test fun aVenueTheUserRenamesBeforeItsTurn_isNeverLookedUp() = runTest {
        val repo = FakeVenueRepository(
            listOf(Venue(1, GLUED_ZH, null, null, lastUsedAt = 5), Venue(2, GLUED_EN, null, null, lastUsedAt = 4)),
        )
        lookup.positionGate = CompletableDeferred()

        val job = launch { GluedVenueRepair(repo, lookup).run() }
        runCurrent() // the first venue waits for its position
        repo.edit(2, "我的鼎泰豐", null, now = 4)
        lookup.positionGate?.complete(Unit)
        job.join()

        assertEquals(listOf("鼎泰豐 信義店", "我的鼎泰豐"), repo.stored.map { it.name })
        assertEquals(listOf(FULL_ADDRESS), lookup.queried)
    }

    @Test fun aPositionTheUserSetsMeanwhile_isKept() = runTest {
        val repo = FakeVenueRepository(listOf(Venue(1, GLUED_ZH, null, null, lastUsedAt = 5)))
        lookup.positionGate = CompletableDeferred()

        val job = launch { GluedVenueRepair(repo, lookup).run() }
        runCurrent()
        repo.setPosition(1, 25.04, 121.55) // 用這筆紀錄的位置更新, while the address is looked up
        lookup.positionGate?.complete(Unit)
        job.join()

        // Like a glued venue that already had a position: its name and the link's address are fixed.
        assertEquals(listOf(Venue(1, "鼎泰豐 信義店", 25.04, 121.55, 5, address = TRIMMED_ADDRESS)), repo.stored)
    }

    @Test fun aRunThatStopsWhileLookingUp_leavesTheVenueForTheNextStart() = runTest {
        val glued = Venue(1, GLUED_ZH, null, null, lastUsedAt = 5)
        val repo = FakeVenueRepository(listOf(glued))
        lookup.positionGate = CompletableDeferred()

        val job = launch { GluedVenueRepair(repo, lookup).run() }
        runCurrent()
        job.cancel() // the process ends while the geocoder is asked
        job.join()
        assertEquals(listOf(glued), repo.stored)

        lookup.positionGate = null
        GluedVenueRepair(repo, lookup).run()

        assertEquals(
            listOf(Venue(1, "鼎泰豐 信義店", 25.0335035, 121.530118, lastUsedAt = 5, address = TRIMMED_ADDRESS)),
            repo.stored,
        )
    }
}
