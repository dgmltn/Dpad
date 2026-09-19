package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.ChevronUp24Filled: ImageVector
    get() {
        if (_FluentChevronUp24Filled != null) {
            return _FluentChevronUp24Filled!!
        }
        _FluentChevronUp24Filled = ImageVector.Builder(
            name = "FluentChevronUp24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.293f, 15.707f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.414f, 0f)
                lineTo(12f, 9.414f)
                lineToRelative(6.293f, 6.293f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.414f, -1.414f)
                lineToRelative(-7f, -7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.414f, 0f)
                lineToRelative(-7f, 7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 1.414f)
            }
        }.build()

        return _FluentChevronUp24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentChevronUp24Filled: ImageVector? = null
