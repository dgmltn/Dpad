package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Speaker124Filled: ImageVector
    get() {
        if (_FluentSpeaker124Filled != null) {
            return _FluentSpeaker124Filled!!
        }
        _FluentSpeaker124Filled = ImageVector.Builder(
            name = "FluentSpeaker124Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.704f, 3.443f)
                curveToRelative(0.191f, 0.225f, 0.296f, 0.511f, 0.296f, 0.807f)
                verticalLineToRelative(15.502f)
                arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.058f, 0.954f)
                lineTo(7.975f, 16.5f)
                horizontalLineTo(4.25f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 14.25f)
                verticalLineToRelative(-4.5f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.25f, 7.5f)
                horizontalLineToRelative(3.725f)
                lineToRelative(4.968f, -4.204f)
                arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.761f, 0.147f)
                moveToRelative(2.4f, 5.197f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.03f, 0.25f)
                curveToRelative(0.574f, 0.94f, 0.862f, 1.992f, 0.862f, 3.14f)
                reflectiveCurveToRelative(-0.288f, 2.201f, -0.862f, 3.141f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, -1.28f, -0.781f)
                curveToRelative(0.428f, -0.702f, 0.642f, -1.483f, 0.642f, -2.36f)
                reflectiveCurveToRelative(-0.214f, -1.657f, -0.642f, -2.359f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, -1.03f)
            }
        }.build()

        return _FluentSpeaker124Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentSpeaker124Filled: ImageVector? = null
