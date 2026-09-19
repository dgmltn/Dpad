package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.FluentEdit24Filled: ImageVector
    get() {
        if (_FluentEdit24Filled != null) {
            return _FluentEdit24Filled!!
        }
        _FluentEdit24Filled = ImageVector.Builder(
            name = "FluentEdit24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.891f, 3.048f)
                arcToRelative(3.578f, 3.578f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.061f, 5.06f)
                lineToRelative(-0.892f, 0.893f)
                lineTo(15f, 3.94f)
                close()
                moveTo(13.94f, 5.001f)
                lineTo(3.94f, 15f)
                arcToRelative(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.825f, 1.476f)
                lineTo(2.02f, 21.078f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.904f, 0.903f)
                lineToRelative(4.601f, -1.096f)
                arcToRelative(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.477f, -0.825f)
                lineTo(19f, 10.061f)
                close()
            }
        }.build()

        return _FluentEdit24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentEdit24Filled: ImageVector? = null
