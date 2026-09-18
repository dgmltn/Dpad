package com.dgmltn.dpad.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.dgmltn.dpad.android.connection.ConnectionService
import com.dgmltn.dpad.design.DpadTheme
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteController
import com.dgmltn.dpad.ui.nav.AppNavHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val controller: RemoteController by inject()

    // If granted, re-post the ConnectionService notification: Android 13+ silently drops the FGS
    // notification posted while permission was denied, and nothing else will re-post it until the
    // next connection/power state change. startForegroundService is allowed here because the
    // activity that just received the grant is in the foreground. If denied, ConnectionService
    // still runs with its notification hidden, and AutoDisconnectPolicy still bounds its lifetime.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val connection = controller.connection.value
            if (granted && connection != ConnectionState.Disconnected && connection != ConnectionState.PairingRequired) {
                ContextCompat.startForegroundService(this, ConnectionService.startIntent(this))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DpadTheme {
                AppNavHost()
            }
        }
        if (savedInstanceState == null) askForNotificationsOnFirstConnect()
    }

    // Only on a fresh activity creation, never on recreation (rotation/config change): re-prompting
    // on a recreation while already Connected would burn the user's second, final denial for free.
    // Waits for Connected, then requires the lifecycle to be at least STARTED before checking/
    // launching the permission request, so this can't fire from a stopped activity. One-shot:
    // withStarted runs the block (at most) once the lifecycle reaches STARTED. Android itself stops
    // showing the system prompt after the user denies it twice.
    private fun askForNotificationsOnFirstConnect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            controller.connection.first { it == ConnectionState.Connected }
            lifecycle.withStarted {
                val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
