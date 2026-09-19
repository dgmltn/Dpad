package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Tv24Filled: ImageVector
    get() {
        if (_FluentTv24Filled != null) {
            return _FluentTv24Filled!!
        }
        _FluentTv24Filled = ImageVector.Builder(
            name = "FluentTv24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.75f, 4f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 6.75f)
                verticalLineToRelative(8.5f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.75f, 18f)
                horizontalLineToRelative(14.5f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22f, 15.25f)
                verticalLineToRelative(-8.5f)
                arcTo(2.75f, 2.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.25f, 4f)
                close()
                moveTo(5f, 20.25f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.75f, -0.75f)
                horizontalLineToRelative(12.5f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 1.5f)
                horizontalLineTo(5.75f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.75f, -0.75f)
            }
        }.build()

        return _FluentTv24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentTv24Filled: ImageVector? = null
