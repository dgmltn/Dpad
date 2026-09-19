package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Home24Filled: ImageVector
    get() {
        if (_FluentHome24Filled != null) {
            return _FluentHome24Filled!!
        }
        _FluentHome24Filled = ImageVector.Builder(
            name = "FluentHome24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.45f, 2.533f)
                arcToRelative(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.9f, 0f)
                lineTo(3.8f, 8.228f)
                arcToRelative(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.8f, 1.72f)
                verticalLineToRelative(9.305f)
                curveToRelative(0f, 0.966f, 0.784f, 1.75f, 1.75f, 1.75f)
                horizontalLineToRelative(3f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.75f, -1.75f)
                verticalLineTo(15.25f)
                curveToRelative(0f, -0.68f, 0.542f, -1.232f, 1.217f, -1.25f)
                horizontalLineToRelative(2.566f)
                arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.217f, 1.25f)
                verticalLineToRelative(4.003f)
                curveToRelative(0f, 0.966f, 0.784f, 1.75f, 1.75f, 1.75f)
                horizontalLineToRelative(3f)
                arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.75f, -1.75f)
                verticalLineTo(9.947f)
                arcToRelative(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.8f, -1.72f)
                close()
            }
        }.build()

        return _FluentHome24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentHome24Filled: ImageVector? = null
