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
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VenueAddressFillerTest {
    private val withPosition = Venue(id = 1, name = "A", lat = 25.0, lng = 121.5, lastUsedAt = 1)
    private val noPosition = Venue(id = 2, name = "B", lat = null, lng = null, lastUsedAt = 2)
    private val alreadyHasOne = Venue(id = 3, name = "C", lat = 24.0, lng = 120.0, lastUsedAt = 3, address = "Old 1")

    @Test fun fillsOnlyVenuesWithAPositionAndNoAddress() = runTest {
        val repo = FakeVenueRepository(listOf(withPosition, noPosition, alreadyHasOne))
        val lookup = FakeAddressLookup("信義路二段 194號")

        VenueAddressFiller(lookup, repo).fill(repo.stored)

        assertEquals(listOf(GeoPoint(25.0, 121.5)), lookup.asked)
        assertEquals("信義路二段 194號", repo.stored.first { it.id == 1 }.address)
        assertNull(repo.stored.first { it.id == 2 }.address)
        assertEquals("Old 1", repo.stored.first { it.id == 3 }.address)
    }

    @Test fun aFailedLookupLeavesItEmpty() = runTest {
        val repo = FakeVenueRepository(listOf(withPosition))

        VenueAddressFiller(FakeAddressLookup(null), repo).fill(repo.stored)
        assertNull(repo.stored.single().address)

        VenueAddressFiller(FakeAddressLookup("  "), repo).fill(repo.stored)
        assertNull(repo.stored.single().address)
    }

    @Test fun aLookupThatFinishesAfterAMove_isDropped() = runTest {
        val repo = FakeVenueRepository(listOf(withPosition))
        val lookup = FakeAddressLookup("信義路二段 194號").apply { gate = CompletableDeferred() }

        val job = launch { VenueAddressFiller(lookup, repo).fill(repo.stored) }
        runCurrent()
        repo.setPosition(1, 22.0, 120.3)
        lookup.gate?.complete(Unit)
        job.join()

        assertNull(repo.stored.single().address)
    }
}
