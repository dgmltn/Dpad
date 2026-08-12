package com.dgmltn.dpad.data.mapping

import remote.RemoteKeyCode

/** Maps a single character to the RemoteKeyCode(s) to inject, or empty if unsupported.
 *  Letters a-z/A-Z, digits 0-9, space, and a few punctuation keys map; anything else returns emptyList (silently skipped). */
fun charToKeyCodes(c: Char): List<RemoteKeyCode> = when (c.lowercaseChar()) {
    in 'a'..'z' -> listOf(RemoteKeyCode.valueOf("KEYCODE_${c.uppercaseChar()}"))
    in '0'..'9' -> listOf(RemoteKeyCode.valueOf("KEYCODE_$c"))
    ' ' -> listOf(RemoteKeyCode.KEYCODE_SPACE)
    else -> emptyList()
}
