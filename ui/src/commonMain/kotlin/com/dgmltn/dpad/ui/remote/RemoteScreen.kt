@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dgmltn.dpad.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.dgmltn.dpad.design.DirectionalPad
import com.dgmltn.dpad.design.DpadDirection
import com.dgmltn.dpad.design.DpadIcon
import com.dgmltn.dpad.design.DpadPreview
import com.dgmltn.dpad.design.RemoteIconButton
import com.dgmltn.dpad.design.ShortcutChip
import com.dgmltn.dpad.domain.ConnectionState
import com.dgmltn.dpad.domain.RemoteKey
import com.dgmltn.dpad.domain.Shortcut
import com.dgmltn.dpad.domain.TvPower
import com.dgmltn.dpad.domain.Volume
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: collects [RemoteViewModel.state] and wires its actions to [RemoteContent]. */
@Composable
fun RemoteScreen(
    onOpenDevices: () -> Unit,
    onEditShortcuts: () -> Unit,
    vm: RemoteViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    LifecycleStartEffect(vm) {
        vm.onConnectLastUsed()
        onStopOrDispose { }
    }

    RemoteContent(
        state = state,
        onKey = vm::onKey,
        onLaunch = vm::onLaunch,
        onText = vm::onText,
        onOpenDevices = onOpenDevices,
        onEditShortcuts = onEditShortcuts,
    )
}

/** Full-screen, edge-to-edge remote UI. Stateless: pure inputs -> UI. This is the preview surface. */
@Composable
fun RemoteContent(
    state: RemoteUiState,
    onKey: (RemoteKey) -> Unit,
    onLaunch: (String) -> Unit,
    onText: (String) -> Unit,
    onOpenDevices: () -> Unit,
    onEditShortcuts: () -> Unit,
) {
    var showTextSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val tvOff = state.connection == ConnectionState.Connected && state.power == TvPower.OFF

            TopBar(
                deviceName = state.deviceName,
                connection = state.connection,
                tvOff = tvOff,
                onDeviceNameClick = onOpenDevices,
                onKeyboardClick = { showTextSheet = true },
                onPowerClick = { onKey(RemoteKey.POWER) },
            )

            statusBannerText(state.connection, tvOff)?.let { StatusBanner(text = it) }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DirectionalPad(
                    onDirection = { onKey(it.toRemoteKey()) },
                    onCenter = { onKey(RemoteKey.DPAD_CENTER) },
                )

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    RemoteIconButton(
                        icon = DpadIcon.Back,
                        contentDescription = "Back",
                        onClick = { onKey(RemoteKey.BACK) },
                    )
                    RemoteIconButton(
                        icon = DpadIcon.Home,
                        contentDescription = "Home",
                        onClick = { onKey(RemoteKey.HOME) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteIconButton(
                        icon = DpadIcon.VolumeDown,
                        contentDescription = "Volume down",
                        onClick = { onKey(RemoteKey.VOLUME_DOWN) },
                        repeat = true,
                    )
                    RemoteIconButton(
                        icon = if (state.volume?.muted == true) DpadIcon.VolumeOff else DpadIcon.VolumeUp,
                        contentDescription = if (state.volume?.muted == true) "Unmute" else "Mute",
                        onClick = { onKey(RemoteKey.MUTE) },
                    )
                    RemoteIconButton(
                        icon = DpadIcon.VolumeUp,
                        contentDescription = "Volume up",
                        onClick = { onKey(RemoteKey.VOLUME_UP) },
                        repeat = true,
                    )
                }

                state.volume?.let { volume ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${volume.level}/${volume.max}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    RemoteIconButton(
                        icon = DpadIcon.Rewind,
                        contentDescription = "Rewind",
                        onClick = { onKey(RemoteKey.MEDIA_REWIND) },
                    )
                    RemoteIconButton(
                        icon = DpadIcon.Play,
                        contentDescription = "Play/pause",
                        onClick = { onKey(RemoteKey.MEDIA_PLAY_PAUSE) },
                    )
                    RemoteIconButton(
                        icon = DpadIcon.FastForward,
                        contentDescription = "Fast forward",
                        onClick = { onKey(RemoteKey.MEDIA_FAST_FORWARD) },
                    )
                }
            }

            ShortcutsRow(
                shortcuts = state.shortcuts,
                onLaunch = onLaunch,
                onEditShortcuts = onEditShortcuts,
            )
        }
    }

    if (showTextSheet) {
        TextInputSheet(
            onDismiss = { showTextSheet = false },
            onSend = onText,
        )
    }
}

@Composable
private fun TopBar(
    deviceName: String?,
    connection: ConnectionState,
    tvOff: Boolean,
    onDeviceNameClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    onPowerClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDeviceNameClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = connectionDotColor(connection, tvOff), shape = CircleShape),
            )
            Text(
                text = deviceName ?: "No device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onKeyboardClick) {
                DpadIcon(
                    icon = DpadIcon.Keyboard,
                    contentDescription = "Text input",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp),
                )
            }
            RemoteIconButton(
                icon = DpadIcon.Power,
                contentDescription = if (tvOff) "Turn TV on" else "Power",
                onClick = onPowerClick,
                tint = if (tvOff) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error,
                containerColor = if (tvOff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun connectionDotColor(connection: ConnectionState, tvOff: Boolean): Color = when {
    tvOff -> MaterialTheme.colorScheme.outline
    connection == ConnectionState.Connected -> Color(0xFF4CAF50)
    connection == ConnectionState.Connecting -> Color(0xFFFFC107)
    connection == ConnectionState.PairingRequired -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusBannerText(connection: ConnectionState, tvOff: Boolean): String? = when (connection) {
    ConnectionState.Connecting -> "Connecting…"
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.PairingRequired -> "Re-pair needed"
    ConnectionState.Connected -> if (tvOff) "TV is off" else null
}

@Composable
private fun StatusBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ShortcutsRow(
    shortcuts: ImmutableList<Shortcut>,
    onLaunch: (String) -> Unit,
    onEditShortcuts: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shortcuts, key = { it.id }) { shortcut ->
                ShortcutChip(
                    label = shortcut.label,
                    onClick = { onLaunch(shortcut.appLinkUrl) },
                )
            }
        }
        RemoteIconButton(
            icon = DpadIcon.Edit,
            contentDescription = "Edit shortcuts",
            onClick = onEditShortcuts,
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

@Composable
private fun TextInputSheet(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                label = { Text("Type to send") },
                singleLine = true,
            )
            RemoteIconButton(
                icon = DpadIcon.Send,
                contentDescription = "Send",
                onClick = {
                    onSend(text)
                    text = ""
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun DpadDirection.toRemoteKey(): RemoteKey = when (this) {
    DpadDirection.UP -> RemoteKey.DPAD_UP
    DpadDirection.DOWN -> RemoteKey.DPAD_DOWN
    DpadDirection.LEFT -> RemoteKey.DPAD_LEFT
    DpadDirection.RIGHT -> RemoteKey.DPAD_RIGHT
}

@Preview
@Composable
private fun Preview_RemoteContent_Disconnected() {
    DpadPreview {
        RemoteContent(
            state = RemoteUiState(
                deviceName = null,
                connection = ConnectionState.Disconnected,
                volume = null,
                shortcuts = persistentListOf(),
            ),
            onKey = {},
            onLaunch = {},
            onText = {},
            onOpenDevices = {},
            onEditShortcuts = {},
        )
    }
}

@Preview
@Composable
private fun Preview_RemoteContent_Connected() {
    DpadPreview {
        RemoteContent(
            state = RemoteUiState(
                deviceName = "Living Room TV",
                connection = ConnectionState.Connected,
                volume = Volume(level = 12, max = 25, muted = false),
                shortcuts = persistentListOf(
                    Shortcut(id = "netflix", label = "Netflix", appLinkUrl = "https://netflix.com"),
                    Shortcut(id = "youtube", label = "YouTube", appLinkUrl = "https://youtube.com"),
                    Shortcut(id = "disney", label = "Disney+", appLinkUrl = "https://disneyplus.com"),
                ),
            ),
            onKey = {},
            onLaunch = {},
            onText = {},
            onOpenDevices = {},
            onEditShortcuts = {},
        )
    }
}

@Preview
@Composable
private fun Preview_RemoteContent_TvOff() {
    DpadPreview {
        RemoteContent(
            state = RemoteUiState(
                deviceName = "Living Room TV",
                connection = ConnectionState.Connected,
                power = TvPower.OFF,
                volume = null,
                shortcuts = persistentListOf(),
            ),
            onKey = {},
            onLaunch = {},
            onText = {},
            onOpenDevices = {},
            onEditShortcuts = {},
        )
    }
}

@Preview
@Composable
private fun Preview_RemoteContent_PairingRequired() {
    DpadPreview {
        RemoteContent(
            state = RemoteUiState(
                deviceName = "Bedroom TV",
                connection = ConnectionState.PairingRequired,
                volume = null,
                shortcuts = persistentListOf(),
            ),
            onKey = {},
            onLaunch = {},
            onText = {},
            onOpenDevices = {},
            onEditShortcuts = {},
        )
    }
}
