package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the V1.6x tap-to-locate decision ([BuddyLocate.decide]). Mirrors
 * the [BuddyLinkDecisionsTest] style: construct [BuddyView]/[BuddyUser]/[BuddyLocation]
 * directly and inject a fixed `nowMs` so the decision is deterministic.
 *
 * NOTE on the decide/isLiveNow relationship: [BuddyLocate.decide] gates on
 * `buddyEnabled` while [BuddyView.isLiveNow] does NOT, so the two are NOT a strict
 * biconditional over arbitrary hand-built states — a `{buddyEnabled=false, sharing=true,
 * fresh}` view would be `isLiveNow==true` yet `decide==REASON_BUDDY_LINK_OFF`. That state
 * is unreachable in production because `disable()` writes `buddyEnabled=false` together
 * with `sharing=false`. These tests therefore exercise `decide()` on explicit inputs and
 * only relate it to `isLiveNow()` over production-reachable states (where
 * `!buddyEnabled ⇒ !sharing`). Do not "fix" this into a false biconditional.
 */
class BuddyLocateTest {

    private val appId = "0123456789abcdef0123456789abcdef"
    private val now = 1_000_000_000_000L

    private fun user(buddyEnabled: Boolean, sharing: Boolean) =
        BuddyUser(appId = appId, displayName = "Sam", buddyEnabled = buddyEnabled, sharing = sharing)

    private fun view(
        buddyEnabled: Boolean,
        sharing: Boolean,
        locationAgeMs: Long? // null -> no fix
    ): BuddyView {
        val loc = locationAgeMs?.let { BuddyLocation(lat = 51.5, lon = -0.12, at = now - it) }
        return BuddyView(user = user(buddyEnabled, sharing), location = loc)
    }

    @Test
    fun buddyLinkOff_returnsLinkOffReason() {
        // buddyEnabled is the outermost gate; sharing value is irrelevant.
        assertEquals(
            BuddyLocate.Outcome.REASON_BUDDY_LINK_OFF,
            BuddyLocate.decide(view(buddyEnabled = false, sharing = true, locationAgeMs = 0L), now)
        )
    }

    @Test
    fun linkOnButNotSharing_returnsSharingOffReason() {
        assertEquals(
            BuddyLocate.Outcome.REASON_SHARING_OFF,
            BuddyLocate.decide(view(buddyEnabled = true, sharing = false, locationAgeMs = 0L), now)
        )
    }

    @Test
    fun sharingButNoFix_returnsStaleOrNoFix() {
        assertEquals(
            BuddyLocate.Outcome.REASON_STALE_OR_NO_FIX,
            BuddyLocate.decide(view(buddyEnabled = true, sharing = true, locationAgeMs = null), now)
        )
    }

    @Test
    fun sharingButStaleFix_returnsStaleOrNoFix() {
        // 1 ms older than STALE_MS -> stale.
        assertEquals(
            BuddyLocate.Outcome.REASON_STALE_OR_NO_FIX,
            BuddyLocate.decide(view(buddyEnabled = true, sharing = true, locationAgeMs = BuddyConstants.STALE_MS + 1), now)
        )
    }

    @Test
    fun sharingWithFreshFix_returnsLocate() {
        assertEquals(
            BuddyLocate.Outcome.LOCATE,
            BuddyLocate.decide(view(buddyEnabled = true, sharing = true, locationAgeMs = 0L), now)
        )
    }

    @Test
    fun exactlyStaleMsBoundary_isStillLocate() {
        // Age == STALE_MS exactly is still live (decide uses `>`, isLiveNow uses `<=`).
        val v = view(buddyEnabled = true, sharing = true, locationAgeMs = BuddyConstants.STALE_MS)
        assertEquals(BuddyLocate.Outcome.LOCATE, BuddyLocate.decide(v, now))
    }

    @Test
    fun decideLocate_agreesWithIsLiveNow_onReachableStates() {
        // Over production-reachable states (buddyEnabled==true when sharing==true),
        // decide()==LOCATE iff isLiveNow().
        val fresh = view(buddyEnabled = true, sharing = true, locationAgeMs = 0L)
        val boundary = view(buddyEnabled = true, sharing = true, locationAgeMs = BuddyConstants.STALE_MS)
        val stale = view(buddyEnabled = true, sharing = true, locationAgeMs = BuddyConstants.STALE_MS + 1)
        val noFix = view(buddyEnabled = true, sharing = true, locationAgeMs = null)

        for (v in listOf(fresh, boundary, stale, noFix)) {
            val locate = BuddyLocate.decide(v, now) == BuddyLocate.Outcome.LOCATE
            assertEquals("decide==LOCATE must match isLiveNow for $v", v.isLiveNow(now), locate)
        }
        // And at least one of each truth value is present so the check is meaningful.
        assertTrue(fresh.isLiveNow(now))
        assertTrue(!noFix.isLiveNow(now))
    }
}
