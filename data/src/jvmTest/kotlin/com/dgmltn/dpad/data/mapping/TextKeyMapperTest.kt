package com.dgmltn.dpad.data.mapping

import remote.RemoteKeyCode
import kotlin.test.*

class TextKeyMapperTest {
    @Test fun lowercaseLettersMapToLetterKeycodes() {
        assertEquals(listOf(RemoteKeyCode.KEYCODE_A), charToKeyCodes('a'))
        assertEquals(listOf(RemoteKeyCode.KEYCODE_Z), charToKeyCodes('z'))
    }
    @Test fun uppercaseLettersMapToTheSameLetterKey() {
        // No shift modeling for now — 'A' and 'a' both inject KEYCODE_A (TV search is case-insensitive).
        assertEquals(charToKeyCodes('a'), charToKeyCodes('A'))
    }
    @Test fun digitsMap() {
        assertEquals(listOf(RemoteKeyCode.KEYCODE_0), charToKeyCodes('0'))
        assertEquals(listOf(RemoteKeyCode.KEYCODE_9), charToKeyCodes('9'))
    }
    @Test fun spaceMaps() { assertEquals(listOf(RemoteKeyCode.KEYCODE_SPACE), charToKeyCodes(' ')) }
    @Test fun unsupportedCharReturnsEmpty() { assertEquals(emptyList(), charToKeyCodes('€')) }
}
