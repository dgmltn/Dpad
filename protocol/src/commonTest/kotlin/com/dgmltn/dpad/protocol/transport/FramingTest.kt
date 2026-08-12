package com.dgmltn.dpad.protocol.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** In-memory BytePipe: what you write to one end, the other reads. Reused by later tasks. */
class InMemoryPipe : BytePipe {
    val incoming = Channel<Byte>(Channel.UNLIMITED)   // feed reads
    val outgoing = mutableListOf<Byte>()              // captures writes
    override suspend fun read(max: Int): ByteArray {
        val first = incoming.receiveCatching().getOrNull() ?: throw EofException()
        val out = mutableListOf(first)
        while (out.size < max) {
            val next = incoming.tryReceive().getOrNull() ?: break
            out.add(next)
        }
        return out.toByteArray()
    }
    override suspend fun write(bytes: ByteArray) { outgoing.addAll(bytes.toList()) }
    override fun close() { incoming.close() }
    suspend fun feed(bytes: ByteArray) = bytes.forEach { incoming.send(it) }
}

class FramingTest {
    @Test fun varintSingleByte() { assertContentEquals(byteArrayOf(0x05), encodeVarint(5)) }
    @Test fun varintMultiByte() { assertContentEquals(byteArrayOf(0xAC.toByte(), 0x02), encodeVarint(300)) }

    @Test fun frameRoundTrips() = runTest {
        val pipe = InMemoryPipe()
        val payload = ByteArray(300) { it.toByte() }
        pipe.writeFrame(payload)
        pipe.feed(pipe.outgoing.toByteArray())
        assertContentEquals(payload, pipe.readFrame())
    }

    @Test fun readFrameRejectsOversize() = runTest {
        val pipe = InMemoryPipe()
        pipe.feed(encodeVarint(1 shl 20))
        assertFailsWith<FramingException> { pipe.readFrame(maxLength = 1 shl 16) }
    }

    @Test fun readFrameThrowsEofMidFrame() = runTest {
        val pipe = InMemoryPipe()
        pipe.feed(byteArrayOf(0x05, 0x01))  // promises 5 bytes, delivers 1
        pipe.close()
        assertFailsWith<EofException> { pipe.readFrame() }
    }
}
