package com.dgmltn.dpad.android.connection

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val TAG = "ConnectionService"

/**
 * Starts [ConnectionService] whenever the controller is connecting/connected while the app is in
 * the foreground. Starting only from the foreground satisfies Android 12+'s background-start ban;
 * background reconnects happen while the service is already running. Re-starting an already
 * running service is harmless (it just re-posts its notification).
 */
class ConnectionServiceStarter(
    private val context: Context,
    private val controller: RemoteController,
    private val appInForeground: StateFlow<Boolean>,
) {
    /** Runs until cancelled. Launch once, in the session scope. */
    suspend fun run() {
        combine(controller.connection, appInForeground) { connection, foreground ->
            foreground && (connection == ConnectionState.Connecting || connection == ConnectionState.Connected)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { start() }
    }

    private fun start() {
        try {
            ContextCompat.startForegroundService(context, ConnectionService.startIntent(context))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException, as
            // are older background-start refusals. Carry on without keep-alive (today's behavior).
            Log.w(TAG, "Couldn't start ConnectionService; continuing without keep-alive", e)
        }
    }
}
