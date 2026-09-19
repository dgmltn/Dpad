package com.dgmltn.dpad.icon.fluent

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Fluent.Play24Filled: ImageVector
    get() {
        if (_FluentPlay24Filled != null) {
            return _FluentPlay24Filled!!
        }
        _FluentPlay24Filled = ImageVector.Builder(
            name = "FluentPlay24Filled",
            defaultWidth = 1.dp,
            defaultHeight = 1.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 5.274f)
                curveToRelative(0f, -1.707f, 1.826f, -2.792f, 3.325f, -1.977f)
                lineToRelative(12.362f, 6.727f)
                curveToRelative(1.566f, 0.852f, 1.566f, 3.1f, 0f, 3.952f)
                lineTo(8.325f, 20.702f)
                curveTo(6.826f, 21.518f, 5f, 20.432f, 5f, 18.726f)
                close()
            }
        }.build()

        return _FluentPlay24Filled!!
    }

@Suppress("ObjectPropertyName")
private var _FluentPlay24Filled: ImageVector? = null
