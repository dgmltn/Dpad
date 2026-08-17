package com.dgmltn.dpad.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class DpadDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Circular pad: four directional wedges around a center OK button.
 *
 * Implemented as a layout (not hit-testing math): a circular background sized [PadSize] holds a
 * center OK [Surface] plus four [IconButton]s pinned to the N/S/E/W edges via [Alignment].
 */
@Composable
fun DirectionalPad(
    onDirection: (DpadDirection) -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(PadSize)
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DpadArrowButton(
            icon = Icons.Filled.KeyboardArrowUp,
            contentDescription = "Up",
            onPress = { onDirection(DpadDirection.UP) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = EdgeInset),
        )
        DpadArrowButton(
            icon = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Down",
            onPress = { onDirection(DpadDirection.DOWN) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = EdgeInset),
        )
        DpadArrowButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Left",
            onPress = { onDirection(DpadDirection.LEFT) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = EdgeInset),
        )
        DpadArrowButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Right",
            onPress = { onDirection(DpadDirection.RIGHT) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = EdgeInset),
        )

        // Center OK: fire on down, no repeat.
        Surface(
            modifier = Modifier.size(CenterButtonSize).pressAndHold(onPress = onCenter),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "OK", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A d-pad arrow: fires on down and auto-repeats while held (hold to keep moving the cursor). */
@Composable
private fun DpadArrowButton(
    icon: ImageVector,
    contentDescription: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .pressAndHold(repeat = true, onPress = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

private val PadSize = 280.dp
private val CenterButtonSize = 96.dp
private val EdgeInset = 12.dp

@Preview
@Composable
private fun Preview_DirectionalPad() {
    DpadTheme {
        DirectionalPad(onDirection = {}, onCenter = {})
    }
}
