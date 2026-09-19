package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Checkmark24Filled: ImageVector
    get() {
        if (_FluentCheckmark24Filled != null) {
            return _FluentCheckmark24Filled!!
        }
        _FluentCheckmark24Filled = ImageVector.Builder(
            name = "FluentCheckmark24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveToRelative(8.5f, 16.586f)
                lineToRelative(-3.793f, -3.793f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.414f, 1.414f)
                lineToRelative(4.5f, 4.5f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.414f, 0f)
                lineToRelative(11f, -11f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.414f, -1.414f)
                close()
            }
        }.build()

        return _FluentCheckmark24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentCheckmark24Filled: ImageVector? = null
