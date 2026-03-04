package com.example.btkeyboard.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryOwnershipTrackerTest {

    @Test
    fun externalBroadcastsDoNotBecomeAppOwned() {
        val tracker = DiscoveryOwnershipTracker()

        assertFalse(tracker.onDiscoveryStartedBroadcast())
        assertEquals(DiscoveryOwnership.NONE, tracker.current())

        assertFalse(tracker.onDiscoveryFinishedBroadcast())
        assertEquals(DiscoveryOwnership.NONE, tracker.current())
    }

    @Test
    fun appOwnedRequestTransitionsToActiveAndClearsOnFinish() {
        val tracker = DiscoveryOwnershipTracker()

        tracker.markStartRequested()
        assertEquals(DiscoveryOwnership.REQUESTED_BY_APP, tracker.current())

        assertTrue(tracker.onDiscoveryStartedBroadcast())
        assertEquals(DiscoveryOwnership.ACTIVE_BY_APP, tracker.current())

        assertTrue(tracker.onDiscoveryFinishedBroadcast())
        assertEquals(DiscoveryOwnership.NONE, tracker.current())
    }

    @Test
    fun directSubmissionCanMarkDiscoveryActive() {
        val tracker = DiscoveryOwnershipTracker()

        tracker.markStartRequested()
        tracker.markStartedBySubmission()

        assertTrue(tracker.isAppOwnedActive())
        assertEquals(DiscoveryOwnership.ACTIVE_BY_APP, tracker.current())
    }
}
