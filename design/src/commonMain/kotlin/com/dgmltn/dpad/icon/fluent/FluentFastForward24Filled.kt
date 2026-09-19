package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.FastForward24Filled: ImageVector
    get() {
        if (_FluentFastForward24Filled != null) {
            return _FluentFastForward24Filled!!
        }
        _FluentFastForward24Filled = ImageVector.Builder(
            name = "FluentFastForward24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.97f, 4.363f)
                curveToRelative(-0.974f, -0.83f, -2.472f, -0.137f, -2.472f, 1.142f)
                verticalLineToRelative(3.988f)
                lineTo(5.47f, 4.363f)
                curveToRelative(-0.974f, -0.829f, -2.472f, -0.136f, -2.472f, 1.143f)
                verticalLineToRelative(12.993f)
                curveToRelative(0f, 0.878f, 0.707f, 1.48f, 1.465f, 1.502f)
                horizontalLineToRelative(0.087f)
                curveToRelative(0.318f, -0.01f, 0.64f, -0.122f, 0.92f, -0.36f)
                lineToRelative(6.028f, -5.13f)
                verticalLineToRelative(3.987f)
                curveToRelative(0f, 1.279f, 1.498f, 1.971f, 2.472f, 1.142f)
                lineToRelative(7.41f, -6.306f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -2.665f)
                close()
            }
        }.build()

        return _FluentFastForward24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentFastForward24Filled: ImageVector? = null
