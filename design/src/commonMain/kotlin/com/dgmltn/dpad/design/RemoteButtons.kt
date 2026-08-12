package com.dgmltn.dpad.design

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Round icon button used for remote actions (back/home/volume/transport/power). */
@Composable
fun RemoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(percent = 50),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Labeled pill button used for app/shortcut launch targets. */
@Composable
fun ShortcutChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Preview
@Composable
private fun Preview_RemoteIconButton() {
    DpadTheme {
        RemoteIconButton(
            icon = Icons.Filled.Home,
            contentDescription = "Home",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun Preview_RemoteIconButton_Tinted() {
    DpadTheme {
        RemoteIconButton(
            icon = Icons.Filled.PowerSettingsNew,
            contentDescription = "Power",
            onClick = {},
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview
@Composable
private fun Preview_ShortcutChip() {
    DpadTheme {
        ShortcutChip(label = "Netflix", onClick = {})
    }
}

@Preview
@Composable
private fun Preview_ShortcutChip_LongLabel() {
    DpadTheme {
        ShortcutChip(label = "Paramount+", onClick = {})
    }
}
