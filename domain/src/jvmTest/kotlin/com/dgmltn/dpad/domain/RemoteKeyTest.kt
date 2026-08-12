package com.dgmltn.dpad.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteKeyTest {
    @Test fun coversEveryControlOnTheRemoteScreen() {
        // The remote screen (spec) needs exactly these keys; guard against accidental additions/removals.
        val expected = setOf(
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER",
            "BACK", "HOME", "VOLUME_UP", "VOLUME_DOWN", "MUTE",
            "MEDIA_PLAY_PAUSE", "MEDIA_REWIND", "MEDIA_FAST_FORWARD", "POWER",
        )
        assertEquals(expected, RemoteKey.entries.map { it.name }.toSet())
    }
}
