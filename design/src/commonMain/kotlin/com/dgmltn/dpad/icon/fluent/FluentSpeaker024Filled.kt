package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Speaker024Filled: ImageVector
    get() {
        if (_FluentSpeaker024Filled != null) {
            return _FluentSpeaker024Filled!!
        }
        _FluentSpeaker024Filled = ImageVector.Builder(
            name = "FluentSpeaker024Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(14.704f, 3.44f)
                curveToRelative(0.191f, 0.226f, 0.296f, 0.512f, 0.296f, 0.808f)
                verticalLineTo(19.75f)
                arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.058f, 0.954f)
                lineToRelative(-4.967f, -4.206f)
                horizontalLineTo(4.25f)
                arcTo(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 14.248f)
                verticalLineToRelative(-4.5f)
                arcToRelative(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.25f, -2.25f)
                horizontalLineToRelative(3.725f)
                lineToRelative(4.968f, -4.204f)
                arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.761f, 0.147f)
            }
        }.build()

        return _FluentSpeaker024Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentSpeaker024Filled: ImageVector? = null
