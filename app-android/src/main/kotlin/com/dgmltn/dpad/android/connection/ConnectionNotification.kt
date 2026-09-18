package com.dgmltn.dpad.android.connection

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dgmltn.dpad.android.MainActivity
import com.dgmltn.dpad.R   // app namespace is dpad.appId = com.dgmltn.dpad
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteKey
import com.dgmltn.dpad.domain.TvPower

internal const val CONNECTION_NOTIFICATION_ID = 1
private const val CHANNEL_ID = "connection"
private const val REQUEST_OPEN_APP = 1
private const val REQUEST_DISCONNECT = 2
private const val REQUEST_KEY_BASE = 100

internal fun createConnectionChannel(context: Context) {
    val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName("Remote connection")
        .setDescription("Keeps the remote connected to your TV while the screen is off")
        .build()
    NotificationManagerCompat.from(context).createNotificationChannel(channel)
}

/**
 * Ongoing notification for [ConnectionService]. Actions follow TV state (standard notifications
 * show at most three): TV on/unknown → Play/Pause, Mute, Disconnect; TV off → Power, Disconnect.
 * Power is deliberately absent while the TV is on so the shade can't switch it off by accident.
 */
internal fun buildConnectionNotification(
    context: Context,
    deviceName: String?,
    connection: ConnectionState,
    power: TvPower?,
): Notification {
    val tvOff = connection == ConnectionState.Connected && power == TvPower.OFF
    val status = when {
        tvOff -> "TV is off"
        connection == ConnectionState.Connected -> "Connected"
        else -> "Reconnecting…"
    }
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(deviceName ?: "Dpad")
        .setContentText(status)
        .setContentIntent(openAppIntent(context))
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    if (tvOff) {
        builder.addAction(keyAction(context, RemoteKey.POWER, "Power"))
    } else {
        builder.addAction(keyAction(context, RemoteKey.MEDIA_PLAY_PAUSE, "Play/Pause"))
        builder.addAction(keyAction(context, RemoteKey.MUTE, "Mute"))
    }
    builder.addAction(serviceAction(context, ConnectionService.disconnectIntent(context), "Disconnect", REQUEST_DISCONNECT))
    return builder.build()
}

private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    REQUEST_OPEN_APP,
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun keyAction(context: Context, key: RemoteKey, title: String): NotificationCompat.Action =
    serviceAction(context, ConnectionService.keyIntent(context, key), title, REQUEST_KEY_BASE + key.ordinal)

// getForegroundService: a notification action may (re)start the service even if it was just
// stopped; ConnectionService calls startForeground first thing in onStartCommand.
private fun serviceAction(context: Context, intent: Intent, title: String, requestCode: Int): NotificationCompat.Action {
    val pending = PendingIntent.getForegroundService(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Action.Builder(R.drawable.ic_notification, title, pending).build()
}
