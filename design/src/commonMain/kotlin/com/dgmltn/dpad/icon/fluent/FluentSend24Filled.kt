package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Send24Filled: ImageVector
    get() {
        if (_FluentSend24Filled != null) {
            return _FluentSend24Filled!!
        }
        _FluentSend24Filled = ImageVector.Builder(
            name = "FluentSend24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveToRelative(12.815f, 12.197f)
                lineToRelative(-7.532f, 1.255f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.386f, 0.318f)
                lineTo(2.3f, 20.728f)
                curveToRelative(-0.248f, 0.64f, 0.421f, 1.25f, 1.035f, 0.942f)
                lineToRelative(18f, -9f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -1.341f)
                lineToRelative(-18f, -9f)
                curveToRelative(-0.614f, -0.307f, -1.283f, 0.303f, -1.035f, 0.942f)
                lineToRelative(2.598f, 6.958f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.386f, 0.318f)
                lineToRelative(7.532f, 1.255f)
                arcToRelative(0.2f, 0.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.395f)
            }
        }.build()

        return _FluentSend24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentSend24Filled: ImageVector? = null
