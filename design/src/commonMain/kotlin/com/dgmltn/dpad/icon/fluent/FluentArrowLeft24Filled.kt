package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.ArrowLeft24Filled: ImageVector
    get() {
        if (_FluentArrowLeft24Filled != null) {
            return _FluentArrowLeft24Filled!!
        }
        _FluentArrowLeft24Filled = ImageVector.Builder(
            name = "FluentArrowLeft24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.295f, 19.715f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.404f, -1.424f)
                lineToRelative(-5.37f, -5.292f)
                horizontalLineToRelative(13.67f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -2f)
                horizontalLineTo(6.336f)
                lineTo(11.7f, 5.714f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.404f, -1.424f)
                lineTo(3.37f, 11.112f)
                arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 1.78f)
                close()
            }
        }.build()

        return _FluentArrowLeft24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentArrowLeft24Filled: ImageVector? = null
