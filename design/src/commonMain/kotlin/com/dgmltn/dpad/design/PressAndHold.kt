package com.dgmltn.dpad.design

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Default delay before a held button starts auto-repeating. A tap shorter than this fires once. */
const val PRESS_REPEAT_INITIAL_DELAY_MS = 400L

/** Default interval between auto-repeats while a button is held. */
const val PRESS_REPEAT_INTERVAL_MS = 80L

/**
 * A remote-control press gesture: fires [onPress] on touch-**down** (not on release), for a
 * responsive physical-remote feel.
 *
 * When [repeat] is true, keeps firing while the button is held — once after [initialDelayMillis],
 * then every [repeatIntervalMillis] — until the finger lifts or the gesture is cancelled. A quick
 * tap fires exactly once. A single light haptic fires on the initial press only, never on repeats.
 *
 * Used by the d-pad arrows and the volume buttons (repeat = true) and by every other remote button
 * (repeat = false, i.e. down-to-fire with no repeat). App-launch shortcuts deliberately do NOT use
 * this — they keep normal release-click semantics so an accidental brush can't launch an app.
 */
fun Modifier.pressAndHold(
    enabled: Boolean = true,
    repeat: Boolean = false,
    initialDelayMillis: Long = PRESS_REPEAT_INITIAL_DELAY_MS,
    repeatIntervalMillis: Long = PRESS_REPEAT_INTERVAL_MS,
    onPress: () -> Unit,
): Modifier = composed {
    val currentOnPress by rememberUpdatedState(onPress)
    val haptics = LocalHapticFeedback.current
    pointerInput(enabled, repeat, initialDelayMillis, repeatIntervalMillis) {
        if (!enabled) return@pointerInput
        val pointerScope = this
        coroutineScope {
            val coScope = this
            pointerScope.awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnPress()
                val repeatJob = if (repeat) {
                    coScope.launch {
                        delay(initialDelayMillis)
                        while (isActive) {
                            currentOnPress()
                            delay(repeatIntervalMillis)
                        }
                    }
                } else {
                    null
                }
                // Suspends until the finger lifts or the gesture is cancelled; either ends the repeat.
                waitForUpOrCancellation()
                repeatJob?.cancel()
            }
        }
    }
}
