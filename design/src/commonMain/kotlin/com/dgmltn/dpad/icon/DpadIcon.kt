package com.dgmltn.dpad.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import com.dgmltn.dpad.design.DpadPreview
import com.dgmltn.dpad.icon.fluent.Add24Filled
import com.dgmltn.dpad.icon.fluent.ArrowLeft24Filled
import com.dgmltn.dpad.icon.fluent.Checkmark24Filled
import com.dgmltn.dpad.icon.fluent.ChevronDown24Filled
import com.dgmltn.dpad.icon.fluent.ChevronLeft24Filled
import com.dgmltn.dpad.icon.fluent.ChevronRight24Filled
import com.dgmltn.dpad.icon.fluent.ChevronUp24Filled
import com.dgmltn.dpad.icon.fluent.Delete24Filled
import com.dgmltn.dpad.icon.fluent.FastForward24Filled
import com.dgmltn.dpad.icon.fluent.Fluent
import com.dgmltn.dpad.icon.fluent.FluentEdit24Filled
import com.dgmltn.dpad.icon.fluent.Home24Filled
import com.dgmltn.dpad.icon.fluent.Keyboard24Filled
import com.dgmltn.dpad.icon.fluent.Play24Filled
import com.dgmltn.dpad.icon.fluent.Power24Filled
import com.dgmltn.dpad.icon.fluent.Rewind24Filled
import com.dgmltn.dpad.icon.fluent.Send24Filled
import com.dgmltn.dpad.icon.fluent.Speaker124Filled
import com.dgmltn.dpad.icon.fluent.Speaker224Filled
import com.dgmltn.dpad.icon.fluent.SpeakerMute24Filled
import com.dgmltn.dpad.icon.fluent.Tv24Filled

/**
 * Single source of truth for every icon in the app. Each entry is backed by a Material
 * [androidx.compose.ui.graphics.vector.ImageVector] and resolves to a [androidx.compose.ui.graphics.painter.Painter], so callers never touch `Icons.*` directly — restyling
 * the app's icons means editing this file only.
 *
 * Render inline with the [DpadIcon] composable, or grab [painter] when an API wants a [androidx.compose.ui.graphics.painter.Painter].
 */
enum class DpadIcon(private val vector: ImageVector) {
    Add(Fluent.Add24Filled),
    Back(Fluent.ArrowLeft24Filled),
    Check(Fluent.Checkmark24Filled),
    ChevronDown(Fluent.ChevronDown24Filled),
    ChevronLeft(Fluent.ChevronLeft24Filled),
    ChevronRight(Fluent.ChevronRight24Filled),
    ChevronUp(Fluent.ChevronUp24Filled),
    Delete(Fluent.Delete24Filled),
    Edit(Fluent.FluentEdit24Filled),
    FastForward(Fluent.FastForward24Filled),
    Home(Fluent.Home24Filled),
    Keyboard(Fluent.Keyboard24Filled),
    Play(Fluent.Play24Filled),
    Power(Fluent.Power24Filled),
    Rewind(Fluent.Rewind24Filled),
    Send(Fluent.Send24Filled),
    Tv(Fluent.Tv24Filled),
    VolumeDown(Fluent.Speaker124Filled),
    VolumeMute(Fluent.SpeakerMute24Filled),
    VolumeUp(Fluent.Speaker224Filled),
    ;

    val painter: Painter
        @Composable get() = rememberVectorPainter(vector)

    @Composable
    operator fun invoke(
        modifier: Modifier = Modifier.Companion,
        contentDescription: String? = null,
        tint: Color = LocalContentColor.current,
    ) {
        DpadIcon(icon = this, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}

/** Renders a [DpadIcon] via Material 3 [androidx.compose.material3.Icon]. */
@Composable
fun DpadIcon(
    icon: DpadIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier.Companion,
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