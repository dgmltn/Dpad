package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Power24Filled: ImageVector
    get() {
        if (_FluentPower24Filled != null) {
            return _FluentPower24Filled!!
        }
        _FluentPower24Filled = ImageVector.Builder(
            name = "FluentPower24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8.205f, 4.843f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.844f, 1.813f)
                arcTo(6.997f, 6.997f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12f, 20f)
                arcToRelative(6.998f, 6.998f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.965f, -13.337f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.848f, -1.811f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 13.003f)
                curveTo(21f, 17.972f, 16.97f, 22f, 12f, 22f)
                reflectiveCurveToRelative(-9f, -4.028f, -9f, -8.997f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.205f, -8.16f)
                moveTo(12f, 2f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.993f, 0.883f)
                lineTo(13f, 3f)
                verticalLineToRelative(7f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.993f, 0.117f)
                lineTo(11f, 10f)
                verticalLineTo(3f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, -1f)
            }
        }.build()

        return _FluentPower24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentPower24Filled: ImageVector? = null
