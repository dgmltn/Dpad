package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.ChevronRight24Filled: ImageVector
    get() {
        if (_FluentChevronRight24Filled != null) {
            return _FluentChevronRight24Filled!!
        }
        _FluentChevronRight24Filled = ImageVector.Builder(
            name = "FluentChevronRight24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.293f, 4.293f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 1.414f)
                lineTo(14.586f, 12f)
                lineToRelative(-6.293f, 6.293f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = false, 1.414f, 1.414f)
                lineToRelative(7f, -7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -1.414f)
                lineToRelative(-7f, -7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.414f, 0f)
            }
        }.build()

        return _FluentChevronRight24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentChevronRight24Filled: ImageVector? = null
