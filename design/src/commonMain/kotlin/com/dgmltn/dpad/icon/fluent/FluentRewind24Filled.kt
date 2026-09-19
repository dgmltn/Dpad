package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Rewind24Filled: ImageVector
    get() {
        if (_FluentRewind24Filled != null) {
            return _FluentRewind24Filled!!
        }
        _FluentRewind24Filled = ImageVector.Builder(
            name = "FluentRewind24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.03f, 4.362f)
                curveToRelative(0.974f, -0.83f, 2.472f, -0.137f, 2.472f, 1.142f)
                verticalLineToRelative(3.99f)
                lineToRelative(6.027f, -5.13f)
                curveToRelative(0.974f, -0.83f, 2.473f, -0.138f, 2.473f, 1.142f)
                verticalLineToRelative(12.992f)
                curveToRelative(0f, 0.879f, -0.707f, 1.48f, -1.465f, 1.503f)
                horizontalLineToRelative(-0.087f)
                arcToRelative(1.48f, 1.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.92f, -0.36f)
                lineToRelative(-6.028f, -5.13f)
                verticalLineToRelative(3.986f)
                curveToRelative(0f, 1.279f, -1.498f, 1.971f, -2.472f, 1.142f)
                lineToRelative(-7.41f, -6.306f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -2.665f)
                close()
                moveTo(2.62f, 10.668f)
                lineToRelative(0.486f, 0.57f)
                close()
            }
        }.build()

        return _FluentRewind24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentRewind24Filled: ImageVector? = null
