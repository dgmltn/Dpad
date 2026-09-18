package com.dgmltn.dpad.data

import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.domain.TvPower
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Ends a forgotten background connection. While the app is in the background it disconnects when
 * the TV has reported standby for [Timings.tvOff], the session has been stuck reconnecting for
 * [Timings.unreachable], or no command has been sent for [Timings.idle]. While the app is in the
 * foreground it never disconnects — the user may be about to press Power. Every timer starts
 * fresh when the app is backgrounded.
 */
class AutoDisconnectPolicy(
    private val controller: RemoteController,
    private val appInForeground: StateFlow<Boolean>,
    private val timings: Timings = Timings(),
) {
    data class Timings(
        val tvOff: Duration = 2.minutes,
        val unreachable: Duration = 2.minutes,
        val idle: Duration = 30.minutes,
    )

    private enum class Rule { NONE, TV_OFF, UNREACHABLE, IDLE }

    /** Runs until cancelled. Launch once, in the session scope. */
    suspend fun run() {
        appInForeground.collectLatest { foreground ->
            if (foreground) return@collectLatest
            combine(controller.connection, controller.power, ::ruleFor)
                .distinctUntilChanged()
                .collectLatest { rule ->
                    when (rule) {
                        Rule.NONE -> Unit
                        Rule.TV_OFF -> disconnectAfter(timings.tvOff)
                        Rule.UNREACHABLE -> disconnectAfter(timings.unreachable)
                        Rule.IDLE -> controller.interactions
                            .onStart { emit(Unit) }
                            .collectLatest { disconnectAfter(timings.idle) }
                    }
                }
        }
    }

    private suspend fun disconnectAfter(duration: Duration) {
        delay(duration)
        controller.disconnect()
    }

    private fun ruleFor(connection: ConnectionState, power: TvPower?): Rule = when (connection) {
        ConnectionState.Connected -> if (power == TvPower.OFF) Rule.TV_OFF else Rule.IDLE
        ConnectionState.Connecting -> Rule.UNREACHABLE
        ConnectionState.Disconnected, ConnectionState.PairingRequired -> Rule.NONE
    }
}
