package com.dgmltn.dpad.android.connection

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.DeviceRepository
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.domain.RemoteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the process in the foreground-service state while the remote is connected, so Android
 * doesn't cache/freeze it on screen-off and the TV's keepalive pings keep getting answered.
 *
 * It does NOT own the connection: [RemoteController] does. This service mirrors
 * [RemoteController.connection] — it stops itself on Disconnected or PairingRequired — and renders
 * the ongoing notification. Started by [ConnectionServiceStarter] and by notification actions.
 */
class ConnectionService : Service() {
    private val controller: RemoteController by inject()
    private val deviceRepository: DeviceRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    private var lastNotification: Notification? = null
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        // First, always: startForegroundService() only allows a few seconds before this call.
        postForeground(
            lastNotification
                ?: buildConnectionNotification(this, deviceName = null, controller.connection.value, controller.power.value),
        )
        when (intent?.action) {
            ACTION_KEY -> intent.getStringExtra(EXTRA_KEY)
                ?.let { name -> RemoteKey.entries.firstOrNull { it.name == name } }
                ?.let(controller::press)
            ACTION_DISCONNECT -> controller.disconnect()
        }
        if (!observing) {
            observing = true
            observe()
        }
        // The connection StateFlow won't re-emit a value the collector already saw, so a start
        // arriving after it already observed Disconnected/PairingRequired needs its own stop here.
        val connection = controller.connection.value
        if (connection == ConnectionState.Disconnected || connection == ConnectionState.PairingRequired) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun observe() {
        // Separate from the notification-update collector below: stopping must be decided from
        // controller.connection alone, so a slow or failed DataStore-backed device-name read can
        // never delay or block it.
        scope.launch {
            controller.connection.collect { connection ->
                if (connection == ConnectionState.Disconnected || connection == ConnectionState.PairingRequired) {
                    stop()
                }
            }
        }
        val deviceName = combine(deviceRepository.devices, deviceRepository.lastUsedDeviceId) { devices, id ->
            devices.firstOrNull { it.id == id }?.name
        }
        scope.launch {
            combine(controller.connection, controller.power, deviceName, ::Triple).collect { (connection, power, name) ->
                // The stop collector above owns tearing the service down; this one must never
                // post for Disconnected/PairingRequired.
                if (connection != ConnectionState.Disconnected && connection != ConnectionState.PairingRequired) {
                    postNotification(buildConnectionNotification(this@ConnectionService, name, connection, power))
                }
            }
        }
    }

    // stopSelf(startId), not stopSelf(): if a new start is already queued (e.g. a notification-
    // action tap racing this collector), the system ignores the stop instead of tearing the
    // service down mid-start and crashing with "did not then call Service.startForeground()".
    // stopSelfResult reports whether the stop actually took effect; when it did, cancel the other
    // running collectors so a notify() from the notification-update collector can't race the
    // teardown and post a notification that outlives the service.
    private fun stop() {
        if (stopSelfResult(latestStartId)) {
            scope.coroutineContext.cancelChildren()
        }
    }

    // Only onStartCommand may call this. startForeground may be called only from onStartCommand;
    // a later call from the background (i.e. from the collector below) can throw
    // ForegroundServiceStartNotAllowedException on API 31+. Background updates go through
    // postNotification instead.
    private fun postForeground(notification: Notification) {
        lastNotification = notification
        ServiceCompat.startForeground(
            this,
            CONNECTION_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    // Background-safe notification update for an already-foregrounded service: plain
    // NotificationManagerCompat.notify, not startForeground. Needs a POST_NOTIFICATIONS check on
    // API 33+; if denied, the notification stays hidden but the service (and connection) carry on
    // regardless. lastNotification is still updated so onStartCommand re-posts the freshest one.
    private fun postNotification(notification: Notification) {
        lastNotification = notification
        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationsAllowed) {
            NotificationManagerCompat.from(this).notify(CONNECTION_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val ACTION_KEY = "com.dgmltn.dpad.action.KEY"
        private const val ACTION_DISCONNECT = "com.dgmltn.dpad.action.DISCONNECT"
        private const val EXTRA_KEY = "key"

        fun startIntent(context: Context): Intent = Intent(context, ConnectionService::class.java)

        fun keyIntent(context: Context, key: RemoteKey): Intent =
            startIntent(context).setAction(ACTION_KEY).putExtra(EXTRA_KEY, key.name)

        fun disconnectIntent(context: Context): Intent =
            startIntent(context).setAction(ACTION_DISCONNECT)
    }
}
