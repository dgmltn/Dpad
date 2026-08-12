package com.dgmltn.dpad.protocol.transport

class FramingException(message: String) : Exception(message)

fun encodeVarint(value: Int): ByteArray {
    require(value >= 0)
    var v = value
    val out = mutableListOf<Byte>()
    do {
        val byte = v and 0x7F
        v = v ushr 7
        out.add(((if (v != 0) byte or 0x80 else byte)).toByte())
    } while (v != 0)
    return out.toByteArray()
}

suspend fun BytePipe.readVarint(): Int {
    var shift = 0
    var result = 0
    while (shift < 32) {
        val byte = readExactly(1)[0].toInt() and 0xFF
        result = result or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0) return result
        shift += 7
    }
    throw FramingException("varint too long")
}

suspend fun BytePipe.readExactly(count: Int): ByteArray {
    val out = ByteArray(count)
    var have = 0
    while (have < count) {
        val chunk = read(count - have)
        chunk.copyInto(out, have)
        have += chunk.size
    }
    return out
}

suspend fun BytePipe.writeFrame(payload: ByteArray) = write(encodeVarint(payload.size) + payload)

suspend fun BytePipe.readFrame(maxLength: Int = 1 shl 16): ByteArray {
    val length = readVarint()
    if (length > maxLength) throw FramingException("frame of $length exceeds $maxLength")
    return readExactly(length)
}
