package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Pause24Filled: ImageVector
    get() {
        if (_FluentPause24Filled != null) {
            return _FluentPause24Filled!!
        }
        _FluentPause24Filled = ImageVector.Builder(
            name = "FluentPause24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.746f, 3f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.75f, 1.75f)
                verticalLineToRelative(14.5f)
                curveToRelative(0f, 0.966f, 0.784f, 1.75f, 1.75f, 1.75f)
                horizontalLineToRelative(3.5f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.75f, -1.75f)
                lineTo(10.996f, 4.75f)
                arcTo(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.246f, 3f)
                close()
                moveTo(14.746f, 3f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.75f, 1.75f)
                verticalLineToRelative(14.5f)
                curveToRelative(0f, 0.966f, 0.784f, 1.75f, 1.75f, 1.75f)
                horizontalLineToRelative(3.5f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.75f, -1.75f)
                lineTo(19.996f, 4.75f)
                arcTo(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 18.246f, 3f)
                close()
            }
        }.build()

        return _FluentPause24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentPause24Filled: ImageVector? = null
