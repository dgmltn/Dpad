package com.dgmltn.dpad.data

import com.dgmltn.dpad.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDisconnectPolicyTest {
    private class FakeController : RemoteController {
        val conn = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        val pwr = MutableStateFlow<TvPower?>(TvPower.ON)
        private val _interactions = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val connection = conn.asStateFlow()
        override val volume = MutableStateFlow<Volume?>(null).asStateFlow()
        override val power = pwr.asStateFlow()
        override val interactions = _interactions.asSharedFlow()
        var disconnects = 0
        override fun connect(device: PairedDevice) {}
        override fun disconnect() {
            disconnects++
            conn.value = ConnectionState.Disconnected
            pwr.value = null
        }
        override fun press(key: RemoteKey) { _interactions.tryEmit(Unit) }
        override fun launchApp(appLinkUrl: String) {}
        override fun sendText(text: String) {}
    }

    private val controller = FakeController()
    private val foreground = MutableStateFlow(false)

    private fun TestScope.startPolicy() {
        backgroundScope.launch { AutoDisconnectPolicy(controller, foreground).run() }
        runCurrent()
    }

    @Test fun tvOffInBackgroundDisconnectsAfterTwoMinutes() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun tvBackOnBeforeDeadlineCancelsTvOffTimer() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        controller.pwr.value = TvPower.ON
        runCurrent()
        advanceTimeBy(2.seconds)
        assertEquals(0, controller.disconnects)
    }

    @Test fun stuckReconnectingInBackgroundDisconnectsAfterTwoMinutes() = runTest {
        controller.conn.value = ConnectionState.Connecting
        controller.pwr.value = null
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun unreachableTimerResetsWhenConnectionLeavesConnecting() = runTest {
        controller.conn.value = ConnectionState.Connecting
        controller.pwr.value = null
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        controller.conn.value = ConnectionState.Connected
        controller.pwr.value = TvPower.ON
        runCurrent()
        advanceTimeBy(2.seconds)
        assertEquals(0, controller.disconnects)
    }

    @Test fun idleInBackgroundDisconnectsAfterThirtyMinutes() = runTest {
        startPolicy()
        advanceTimeBy(30.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun interactionRestartsIdleTimer() = runTest {
        startPolicy()
        advanceTimeBy(29.minutes)
        controller.press(RemoteKey.MEDIA_PLAY_PAUSE)
        runCurrent()
        advanceTimeBy(29.minutes)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.minutes)
        assertEquals(1, controller.disconnects)
    }

    @Test fun interactionDoesNotResetTvOffTimer() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(1.minutes)
        controller.press(RemoteKey.MEDIA_PLAY_PAUSE)
        runCurrent()
        advanceTimeBy(1.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun foregroundingCancelsPendingTimerAndBackgroundingRestartsIt() = runTest {
        controller.pwr.value = TvPower.OFF
        startPolicy()
        advanceTimeBy(2.minutes - 1.seconds)
        foreground.value = true
        runCurrent()
        advanceTimeBy(10.minutes)
        assertEquals(0, controller.disconnects)

        foreground.value = false
        runCurrent()
        advanceTimeBy(2.minutes - 1.seconds)
        assertEquals(0, controller.disconnects)
        advanceTimeBy(2.seconds)
        assertEquals(1, controller.disconnects)
    }

    @Test fun neverDisconnectsWhileInForeground() = runTest {
        foreground.value = true
        controller.conn.value = ConnectionState.Connecting
        startPolicy()
        advanceTimeBy(1.hours)
        controller.conn.value = ConnectionState.Connected
        controller.pwr.value = TvPower.OFF
        runCurrent()
        advanceTimeBy(1.hours)
        assertEquals(0, controller.disconnects)
    }
}
