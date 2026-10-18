package com.crazystudio.sportrecorder.domain.venue

import com.crazystudio.sportrecorder.domain.model.Venue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which venue a rename onto a taken name would merge into (#89): the nearest, else the most recently used. */
class VenueMergeTest {

    private val here = Venue(id = 1, name = "麥當當", lat = 25.0330, lng = 121.5650, lastUsedAt = 5L)
    private val near = Venue(id = 2, name = "麥當勞", lat = 25.0335, lng = 121.5655, lastUsedAt = 1L)
    private val far = Venue(id = 3, name = "麥當勞", lat = 22.6, lng = 120.3, lastUsedAt = 9L)
    private val unplaced = Venue(id = 4, name = "麥當勞", lat = null, lng = null, lastUsedAt = 20L)

    @Test fun noOtherVenueHasTheName_isNoMerge() {
        assertNull(VenueMerge.target(here, "摩斯", listOf(here, near, far)))
    }

    @Test fun theVenuesOwnName_isNeverAMerge_evenWhenABranchSharesIt() {
        val branch = here.copy(id = 9)
        assertNull(VenueMerge.target(here, " 麥當當 ", listOf(here, branch)))
        assertNull(VenueMerge.target(here, "麥當當", listOf(here)))
    }

    @Test fun withPositions_theNearestWins_overTheMostRecentlyUsed() {
        assertEquals(near, VenueMerge.target(here, "麥當勞", listOf(here, far, near, unplaced)))
    }

    @Test fun withoutAPositionHere_theMostRecentlyUsedWins() {
        val unplacedHere = here.copy(lat = null, lng = null)
        assertEquals(unplaced, VenueMerge.target(unplacedHere, "麥當勞", listOf(unplacedHere, near, far, unplaced)))
    }

    @Test fun whenNoNamesakeHasAPosition_theMostRecentlyUsedWins() {
        val older = unplaced.copy(id = 5, lastUsedAt = 1L)
        assertEquals(unplaced, VenueMerge.target(here, "麥當勞", listOf(here, older, unplaced)))
    }

    @Test fun theNameIsComparedLikeEverywhereElse() {
        val starbucks = Venue(id = 2, name = "Starbucks", lat = null, lng = null, lastUsedAt = 1L)
        assertEquals(starbucks, VenueMerge.target(here, "  STARBUCKS ", listOf(here, starbucks)))
    }
}
