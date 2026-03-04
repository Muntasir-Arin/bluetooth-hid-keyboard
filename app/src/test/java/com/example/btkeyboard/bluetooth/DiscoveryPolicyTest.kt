package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.ErrorCode
import com.example.btkeyboard.model.HostDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoveryPolicyTest {

    private val host = HostDevice(
        name = "Host",
        address = "AA:BB:CC:DD:EE:FF",
        bonded = true,
        connected = false,
    )

    @Test
    fun blocksDiscoveryWhileConnectedOrConnecting() {
        assertEquals(
            DiscoveryPolicy.BLOCKED_WHILE_CONNECTED_MESSAGE,
            DiscoveryPolicy.blockedReason(ConnectionState.Connected(device = host, latencyMs = 0)),
        )
        assertEquals(
            DiscoveryPolicy.BLOCKED_WHILE_CONNECTED_MESSAGE,
            DiscoveryPolicy.blockedReason(ConnectionState.Connecting(device = host)),
        )
    }

    @Test
    fun doesNotBlockDiscoveryForOtherStates() {
        assertNull(DiscoveryPolicy.blockedReason(ConnectionState.Idle))
        assertNull(DiscoveryPolicy.blockedReason(ConnectionState.Discovering))
        assertNull(DiscoveryPolicy.blockedReason(ConnectionState.Pairing(device = host)))
        assertNull(
            DiscoveryPolicy.blockedReason(
                ConnectionState.Error(code = ErrorCode.UNKNOWN, message = "x"),
            ),
        )
    }
}
