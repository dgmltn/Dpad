package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.VideoPlayPause24Filled: ImageVector
    get() {
        if (_FluentVideoPlayPause24Filled != null) {
            return _FluentVideoPlayPause24Filled!!
        }
        _FluentVideoPlayPause24Filled = ImageVector.Builder(
            name = "FluentVideoPlayPause24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7.75f)
                curveToRelative(0f, -0.966f, 0.784f, -1.75f, 1.75f, -1.75f)
                horizontalLineToRelative(1.5f)
                curveToRelative(0.966f, 0f, 1.75f, 0.784f, 1.75f, 1.75f)
                verticalLineToRelative(8.5f)
                arcTo(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.25f, 18f)
                horizontalLineToRelative(-1.5f)
                arcTo(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 16.25f)
                verticalLineToRelative(-4.13f)
                arcToRelative(1.73f, 1.73f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.84f, 1.417f)
                lineToRelative(-6.5f, 3.952f)
                curveTo(3.493f, 18.197f, 2f, 17.358f, 2f, 15.993f)
                lineTo(2f, 8.004f)
                curveToRelative(0f, -1.372f, 1.507f, -2.21f, 2.673f, -1.486f)
                lineToRelative(6.502f, 4.037f)
                curveToRelative(0.526f, 0.327f, 0.8f, 0.862f, 0.825f, 1.408f)
                close()
                moveTo(18f, 7.75f)
                curveToRelative(0f, -0.966f, 0.784f, -1.75f, 1.75f, -1.75f)
                horizontalLineToRelative(1.5f)
                curveToRelative(0.966f, 0f, 1.75f, 0.784f, 1.75f, 1.75f)
                verticalLineToRelative(8.5f)
                arcTo(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.25f, 18f)
                horizontalLineToRelative(-1.5f)
                arcTo(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18f, 16.25f)
                close()
            }
        }.build()

        return _FluentVideoPlayPause24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentVideoPlayPause24Filled: ImageVector? = null
