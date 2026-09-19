package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.SpeakerMute24Filled: ImageVector
    get() {
        if (_FluentSpeakerMute24Filled != null) {
            return _FluentSpeakerMute24Filled!!
        }
        _FluentSpeakerMute24Filled = ImageVector.Builder(
            name = "FluentSpeakerMute24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 4.25f)
                curveToRelative(0f, -1.078f, -1.274f, -1.65f, -2.08f, -0.934f)
                lineTo(8.427f, 7.31f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.498f, 0.19f)
                lineTo(4.25f, 7.5f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 9.75f)
                verticalLineToRelative(4.497f)
                arcToRelative(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.25f, 2.25f)
                horizontalLineToRelative(3.68f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.498f, 0.19f)
                lineToRelative(4.491f, 3.993f)
                curveToRelative(0.806f, 0.717f, 2.081f, 0.145f, 2.081f, -0.934f)
                close()
                moveTo(16.22f, 9.22f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.06f, 0f)
                lineTo(19f, 10.94f)
                lineToRelative(1.72f, -1.72f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, 1.06f, 1.06f)
                lineTo(20.06f, 12f)
                lineToRelative(1.72f, 1.72f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, -1.06f, 1.06f)
                lineTo(19f, 13.062f)
                lineToRelative(-1.72f, 1.72f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, -1.06f, -1.06f)
                lineTo(17.94f, 12f)
                lineToRelative(-1.72f, -1.72f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -1.06f)
            }
        }.build()

        return _FluentSpeakerMute24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentSpeakerMute24Filled: ImageVector? = null
