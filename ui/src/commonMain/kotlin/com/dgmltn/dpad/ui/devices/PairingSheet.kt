@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dgmltn.dpad.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dgmltn.dpad.design.DpadTheme
import com.dgmltn.dpad.domain.PairingFailureReason
import com.dgmltn.dpad.domain.PairingProgress

private const val CODE_LENGTH = 6

/**
 * Bottom sheet driven by [PairingProgress]: a spinner while connecting, a code entry field once
 * the TV is awaiting a code, a success message once paired, or an error message with a way to
 * close/retry on failure.
 */
@Composable
fun PairingSheet(
    pairing: PairingProgress,
    onSubmitCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (pairing) {
                PairingProgress.Connecting -> ConnectingContent()
                PairingProgress.AwaitingCode -> AwaitingCodeContent(onSubmitCode = onSubmitCode, onCancel = onCancel)
                PairingProgress.Paired -> PairedContent()
                is PairingProgress.Failed -> FailedContent(reason = pairing.reason, onClose = onCancel)
            }
        }
    }
}

@Composable
private fun ConnectingContent() {
    CircularProgressIndicator()
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Connecting…",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AwaitingCodeContent(
    onSubmitCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Text(
        text = "Enter the code shown on your TV",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(16.dp))
    TextField(
        value = code,
        onValueChange = { value ->
            if (value.length <= CODE_LENGTH) code = value.uppercase()
        },
        label = { Text("Pairing code") },
        singleLine = true,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        Button(
            onClick = { onSubmitCode(code) },
            enabled = code.length == CODE_LENGTH,
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun PairedContent() {
    Icon(
        imageVector = Icons.Filled.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Paired!",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FailedContent(
    reason: PairingFailureReason,
    onClose: () -> Unit,
) {
    Text(
        text = reason.toMessage(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = onClose) {
        Text("Close")
    }
}

private fun PairingFailureReason.toMessage(): String = when (this) {
    PairingFailureReason.WRONG_CODE -> "That code didn't match. Try again."
    PairingFailureReason.REJECTED -> "Pairing was rejected on the TV."
    PairingFailureReason.CONNECTION_LOST -> "Connection to the TV was lost."
    PairingFailureReason.TIMEOUT -> "Pairing timed out."
}

@Preview
@Composable
private fun Preview_PairingSheet_Connecting() {
    DpadTheme {
        PairingSheet(
            pairing = PairingProgress.Connecting,
            onSubmitCode = {},
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun Preview_PairingSheet_AwaitingCode() {
    DpadTheme {
        PairingSheet(
            pairing = PairingProgress.AwaitingCode,
            onSubmitCode = {},
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun Preview_PairingSheet_Paired() {
    DpadTheme {
        PairingSheet(
            pairing = PairingProgress.Paired,
            onSubmitCode = {},
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun Preview_PairingSheet_Failed() {
    DpadTheme {
        PairingSheet(
            pairing = PairingProgress.Failed(PairingFailureReason.WRONG_CODE),
            onSubmitCode = {},
            onCancel = {},
        )
    }
}
