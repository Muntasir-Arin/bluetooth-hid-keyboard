package com.example.btkeyboard.service

import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.ErrorCode
import com.example.btkeyboard.model.HostDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceIdlePolicyTest {

    @Test
    fun shouldArmTimerWhenBackgroundAndDisconnectedLike() {
        assertTrue(ServiceIdlePolicy.shouldArmIdleTimer(appInForeground = false, state = ConnectionState.Idle))
        assertTrue(ServiceIdlePolicy.shouldArmIdleTimer(appInForeground = false, state = ConnectionState.Discovering))
        assertTrue(
            ServiceIdlePolicy.shouldArmIdleTimer(
                appInForeground = false,
                state = ConnectionState.Error(
                    code = ErrorCode.CONNECTION_FAILED,
                    message = "failure",
                ),
            ),
        )
    }

    @Test
    fun shouldNotArmTimerWhenForeground() {
        assertFalse(ServiceIdlePolicy.shouldArmIdleTimer(appInForeground = true, state = ConnectionState.Idle))
        assertFalse(
            ServiceIdlePolicy.shouldArmIdleTimer(
                appInForeground = true,
                state = ConnectionState.Connected(
                    device = HostDevice(
                        name = "Desk",
                        address = "00:11:22:33:44:55",
                        bonded = true,
                        connected = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun shouldNotArmTimerWhenBackgroundAndConnected() {
        val connectedState = ConnectionState.Connected(
            device = HostDevice(
                name = "Desk",
                address = "00:11:22:33:44:55",
                bonded = true,
                connected = true,
            ),
        )
        assertFalse(ServiceIdlePolicy.shouldArmIdleTimer(appInForeground = false, state = connectedState))
    }
}
