package com.dgmltn.dpad.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for every icon in the app. Each entry is backed by a Material
 * [ImageVector] and resolves to a [Painter], so callers never touch `Icons.*` directly — restyling
 * the app's icons means editing this file only.
 *
 * Render inline with the [DpadIcon] composable, or grab [painter] when an API wants a [Painter].
 */
enum class DpadIcon(private val vector: ImageVector) {
    Add(Icons.Filled.Add),
    Back(Icons.AutoMirrored.Filled.ArrowBack),
    Check(Icons.Filled.Check),
    ChevronDown(Icons.Filled.KeyboardArrowDown),
    ChevronLeft(Icons.AutoMirrored.Filled.KeyboardArrowLeft),
    ChevronRight(Icons.AutoMirrored.Filled.KeyboardArrowRight),
    ChevronUp(Icons.Filled.KeyboardArrowUp),
    Delete(Icons.Filled.Delete),
    Edit(Icons.Filled.Edit),
    FastForward(Icons.Filled.FastForward),
    Home(Icons.Filled.Home),
    Keyboard(Icons.Filled.Keyboard),
    Play(Icons.Filled.PlayArrow),
    Power(Icons.Filled.PowerSettingsNew),
    Rewind(Icons.Filled.FastRewind),
    Send(Icons.AutoMirrored.Filled.Send),
    Tv(Icons.Filled.Tv),
    VolumeDown(Icons.AutoMirrored.Filled.VolumeDown),
    VolumeOff(Icons.AutoMirrored.Filled.VolumeOff),
    VolumeUp(Icons.AutoMirrored.Filled.VolumeUp),
    ;

    val painter: Painter
        @Composable get() = rememberVectorPainter(vector)

    @Composable
    operator fun invoke(
        modifier: Modifier = Modifier,
        contentDescription: String? = null,
        tint: Color = LocalContentColor.current,
    ) {
        DpadIcon(icon = this, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}

/** Renders a [DpadIcon] via Material 3 [Icon]. */
@Composable
fun DpadIcon(
    icon: DpadIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = icon.painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Preview
@Composable
private fun Preview_DpadIconGallery() {
    DpadPreview {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DpadIcon.entries.forEach { icon ->
                Column(
                    modifier = Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DpadIcon(
                        icon = icon,
                        contentDescription = icon.name,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = icon.name,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
