package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Speaker224Filled: ImageVector
    get() {
        if (_FluentSpeaker224Filled != null) {
            return _FluentSpeaker224Filled!!
        }
        _FluentSpeaker224Filled = ImageVector.Builder(
            name = "FluentSpeaker224Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15f, 4.25f)
                verticalLineToRelative(15.496f)
                curveToRelative(0f, 1.079f, -1.274f, 1.651f, -2.08f, 0.934f)
                lineToRelative(-4.492f, -3.994f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.498f, -0.189f)
                horizontalLineTo(4.25f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 14.247f)
                verticalLineTo(9.749f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.25f, 7.5f)
                horizontalLineToRelative(3.68f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.498f, -0.19f)
                lineToRelative(4.491f, -3.993f)
                curveTo(13.726f, 2.6f, 15f, 3.172f, 15f, 4.25f)
                moveToRelative(3.992f, 1.648f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.049f, 0.156f)
                arcTo(9.96f, 9.96f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 12.001f)
                arcToRelative(9.96f, 9.96f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.96f, 5.946f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.205f, -0.893f)
                arcToRelative(8.46f, 8.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.665f, -5.053f)
                arcToRelative(8.46f, 8.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.665f, -5.054f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.157f, -1.05f)
                moveTo(17.143f, 8.37f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.017f, 0.302f)
                curveToRelative(0.536f, 0.99f, 0.84f, 2.125f, 0.84f, 3.329f)
                arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.84f, 3.328f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.32f, -0.714f)
                arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.66f, -2.614f)
                curveToRelative(0f, -0.948f, -0.24f, -1.838f, -0.66f, -2.615f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.303f, -1.016f)
            }
        }.build()

        return _FluentSpeaker224Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentSpeaker224Filled: ImageVector? = null
