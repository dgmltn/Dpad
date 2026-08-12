package com.dgmltn.dpad.protocol.transport

interface BytePipe {
    /** Reads 1..max bytes; throws EofException when the peer closed. */
    suspend fun read(max: Int): ByteArray
    suspend fun write(bytes: ByteArray)
    fun close()
}

class EofException(message: String = "stream closed") : Exception(message)
