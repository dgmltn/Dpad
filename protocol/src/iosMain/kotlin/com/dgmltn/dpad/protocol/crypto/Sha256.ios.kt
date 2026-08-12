package com.dgmltn.dpad.protocol.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun sha256(data: ByteArray): ByteArray {
    val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { pinned ->
        out.usePinned { outPinned ->
            CC_SHA256(
                if (data.isEmpty()) null else pinned.addressOf(0),
                data.size.toUInt(),
                outPinned.addressOf(0).reinterpret(),
            )
        }
    }
    return out
}
