package com.dgmltn.dpad.protocol.transport

import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.fake.FakeTvServer
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TlsSocketTest {
    private val clientIdentity = ClientIdentityGenerator.generate("dpad-client")

    @Test fun handshakesAndEchoesFrames() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.start { pipe -> pipe.writeFrame(pipe.readFrame()) }   // echo one frame
            val conn = TlsSocketFactory(clientIdentity).connect("127.0.0.1", server.port)
            conn.writeFrame(byteArrayOf(1, 2, 3))
            assertContentEquals(byteArrayOf(1, 2, 3), conn.readFrame())
            // both sides captured each other's certs
            assertContentEquals(server.serverIdentity.publicParams.modulus, conn.serverPublicParams.modulus)
            assertContentEquals(clientIdentity.publicParams.modulus, server.lastClientCertParams!!.modulus)
            conn.close()
        }
    }

    @Test fun rejectionSurfacesAsHandshakeRejected() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.rejectClientCerts = true
            server.start { }
            assertFailsWith<TlsHandshakeRejectedException> {
                TlsSocketFactory(clientIdentity).connect("127.0.0.1", server.port)
            }
        }
    }

    // Guards against a handler assertion (an Error, not an Exception) silently killing the fake
    // server's accept thread — it must surface from close() instead of leaving the test hanging or
    // reporting a spurious EofException.
    @Test fun handlerFailureSurfacesFromClose() = runTest {
        assertFailsWith<AssertionError> {
            FakeTvServer(requireClientCert = true).use { server ->
                server.start { throw AssertionError("boom") }
                val conn = TlsSocketFactory(clientIdentity).connect("127.0.0.1", server.port)
                conn.close()
            }
        }
    }
}
