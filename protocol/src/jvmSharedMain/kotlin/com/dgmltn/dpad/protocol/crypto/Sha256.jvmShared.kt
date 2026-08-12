package com.dgmltn.dpad.protocol.crypto

import java.security.MessageDigest

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
