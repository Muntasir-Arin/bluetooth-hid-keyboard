package com.example.btkeyboard.bluetooth

internal enum class DiscoveryOwnership {
    NONE,
    REQUESTED_BY_APP,
    ACTIVE_BY_APP,
}

internal class DiscoveryOwnershipTracker {
    private var ownership: DiscoveryOwnership = DiscoveryOwnership.NONE

    fun current(): DiscoveryOwnership = ownership

    fun markStartRequested() {
        ownership = DiscoveryOwnership.REQUESTED_BY_APP
    }

    fun markStartedBySubmission() {
        ownership = DiscoveryOwnership.ACTIVE_BY_APP
    }

    fun onDiscoveryStartedBroadcast(): Boolean {
        return if (ownership == DiscoveryOwnership.REQUESTED_BY_APP || ownership == DiscoveryOwnership.ACTIVE_BY_APP) {
            ownership = DiscoveryOwnership.ACTIVE_BY_APP
            true
        } else {
            false
        }
    }

    fun onDiscoveryFinishedBroadcast(): Boolean {
        return if (ownership == DiscoveryOwnership.NONE) {
            false
        } else {
            ownership = DiscoveryOwnership.NONE
            true
        }
    }

    fun isAppOwnedActive(): Boolean = ownership != DiscoveryOwnership.NONE

    fun clear() {
        ownership = DiscoveryOwnership.NONE
    }
}
