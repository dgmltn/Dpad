package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.ChevronDown24Filled: ImageVector
    get() {
        if (_FluentChevronDown24Filled != null) {
            return _FluentChevronDown24Filled!!
        }
        _FluentChevronDown24Filled = ImageVector.Builder(
            name = "FluentChevronDown24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.293f, 8.293f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.414f, 0f)
                lineTo(12f, 14.586f)
                lineToRelative(6.293f, -6.293f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 1.414f, 1.414f)
                lineToRelative(-7f, 7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.414f, 0f)
                lineToRelative(-7f, -7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -1.414f)
            }
        }.build()

        return _FluentChevronDown24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentChevronDown24Filled: ImageVector? = null
