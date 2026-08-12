package com.dgmltn.dpad.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgmltn.dpad.design.DpadTheme
import com.dgmltn.dpad.domain.DiscoveredDevice
import com.dgmltn.dpad.domain.PairedDevice
import com.dgmltn.dpad.domain.PairingFailureReason
import com.dgmltn.dpad.domain.PairingProgress
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: collects [DevicesViewModel.state] and wires its actions to [DevicesContent]. */
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    vm: DevicesViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    DevicesContent(
        state = state,
        onSelect = vm::onSelect,
        onUnpair = vm::onUnpair,
        onStartPairing = vm::onStartPairing,
        onSubmitCode = vm::onSubmitCode,
        onCancelPairing = vm::onCancelPairing,
        onBack = onBack,
    )
}

/** Device list + pairing UI. Stateless: pure inputs -> UI. This is the preview surface. */
@Composable
fun DevicesContent(
    state: DevicesUiState,
    onSelect: (PairedDevice) -> Unit,
    onUnpair: (String) -> Unit,
    onStartPairing: (DiscoveredDevice) -> Unit,
    onSubmitCode: (String) -> Unit,
    onCancelPairing: () -> Unit,
    onBack: () -> Unit,
) {
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
            DevicesTopBar(onBack = onBack)

            if (state.paired.isEmpty() && state.discovered.isEmpty()) {
                EmptyDevicesMessage(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (state.paired.isNotEmpty()) {
                        item(key = "paired-header") { SectionHeader(title = "Paired") }
                        items(state.paired, key = { it.id }) { device ->
                            DeviceRow(
                                name = device.name,
                                host = device.host,
                                isLastUsed = device.id == state.lastUsedId,
                                onClick = { onSelect(device) },
                                onUnpair = { onUnpair(device.id) },
                            )
                        }
                    }

                    if (state.discovered.isNotEmpty()) {
                        item(key = "discovered-header") { SectionHeader(title = "Discovered") }
                        items(state.discovered, key = { it.name }) { device ->
                            DiscoveredDeviceRow(
                                name = device.name,
                                host = device.host,
                                onClick = { onStartPairing(device) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.pairing?.let { pairing ->
        PairingSheet(
            pairing = pairing,
            onSubmitCode = onSubmitCode,
            onCancel = onCancelPairing,
        )
    }
}

@Composable
private fun DevicesTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = "Devices",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyDevicesMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No devices found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Make sure your TV is on the same network.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A single row in the devices list. Lean params: primitives only, not the whole [PairedDevice]. */
@Composable
fun DeviceRow(
    name: String,
    host: String,
    isLastUsed: Boolean,
    onClick: () -> Unit,
    onUnpair: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (isLastUsed) {
                    Text(
                        text = "Last used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUnpair) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Unpair",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun DiscoveredDeviceRow(
    name: String,
    host: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Pair",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Preview
@Composable
private fun Preview_DevicesContent_Empty() {
    DpadTheme {
        DevicesContent(
            state = DevicesUiState(),
            onSelect = {},
            onUnpair = {},
            onStartPairing = {},
            onSubmitCode = {},
            onCancelPairing = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun Preview_DevicesContent_WithPairedAndDiscovered() {
    DpadTheme {
        DevicesContent(
            state = DevicesUiState(
                paired = persistentListOf(
                    PairedDevice(id = "1", name = "Living Room TV", host = "192.168.1.10", serviceName = "living-room"),
                    PairedDevice(id = "2", name = "Bedroom TV", host = "192.168.1.11", serviceName = "bedroom"),
                ),
                discovered = persistentListOf(
                    DiscoveredDevice(name = "Kitchen TV", host = "192.168.1.12", port = 6466),
                ),
                lastUsedId = "1",
            ),
            onSelect = {},
            onUnpair = {},
            onStartPairing = {},
            onSubmitCode = {},
            onCancelPairing = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun Preview_DeviceRow() {
    DpadTheme {
        DeviceRow(
            name = "Living Room TV",
            host = "192.168.1.10",
            isLastUsed = true,
            onClick = {},
            onUnpair = {},
        )
    }
}

@Preview
@Composable
private fun Preview_DevicesContent_PairingFailed() {
    DpadTheme {
        DevicesContent(
            state = DevicesUiState(
                paired = persistentListOf(
                    PairedDevice(id = "1", name = "Living Room TV", host = "192.168.1.10", serviceName = "living-room"),
                ),
                lastUsedId = "1",
                pairing = PairingProgress.Failed(PairingFailureReason.TIMEOUT),
            ),
            onSelect = {},
            onUnpair = {},
            onStartPairing = {},
            onSubmitCode = {},
            onCancelPairing = {},
            onBack = {},
        )
    }
}
