package com.dgmltn.dpad.protocol.pairing

import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.fake.FakeTvServer
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import com.dgmltn.dpad.protocol.transport.readFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class PairingClientTest {
    private val identity = ClientIdentityGenerator.generate("dpad-client")

    @Test fun fullPairingFlowSucceeds() = runTest {
        FakeTvServer(requireClientCert = false).use { server ->
            var shownCode: String? = null
            server.startPairingServer(showCode = { shownCode = it })
            val client = PairingClient(TlsSocketFactory(identity), identity)
            launch { client.start("127.0.0.1", server.port) }
            client.events.filterIsInstance<PairingEvent.WaitingForCode>().first()
            client.submitCode(shownCode!!)
            assertIs<PairingEvent.Paired>(client.events.first { it !is PairingEvent.WaitingForCode })
        }
    }

    @Test fun typoCodeFailsWithWrongCode() = runTest {
        FakeTvServer(requireClientCert = false).use { server ->
            server.startPairingServer(showCode = { })
            val client = PairingClient(TlsSocketFactory(identity), identity)
            launch { client.start("127.0.0.1", server.port) }
            client.events.filterIsInstance<PairingEvent.WaitingForCode>().first()
            client.submitCode("000000")   // check byte almost certainly wrong
            val failed = client.events.filterIsInstance<PairingEvent.Failed>().first()
            assertEquals(PairingFailure.WRONG_CODE, failed.reason)
        }
    }

    @Test fun timesOutWhenServerSilent() = runTest {
        FakeTvServer(requireClientCert = false).use { server ->
            // Accepts the TLS connection and reads the pairing_request, then parks on a second
            // read instead of ever sending pairing_request_ack — the client must time out on its
            // own rather than hang forever waiting for a reply that never comes.
            server.start { pipe ->
                pipe.readFrame()
                pipe.readFrame()
            }
            val client = PairingClient(TlsSocketFactory(identity), identity, timeout = 200.milliseconds)
            launch { client.start("127.0.0.1", server.port) }
            val failed = client.events.filterIsInstance<PairingEvent.Failed>().first()
            assertEquals(PairingFailure.TIMEOUT, failed.reason)
        }
    }

    // Guards the exact bug from the whole-branch review: factory.connect() throwing wasn't caught
    // at all, so it propagated out of start() as an uncaught exception (crashing a bare
    // `launch { client.start(...) }` caller) instead of emitting PairingEvent.Failed. Real
    // TlsSocketFactory + FakeTvServer(rejectClientCerts = true) drives the exact
    // TlsHandshakeRejectedException path TlsSocketTest.rejectionSurfacesAsHandshakeRejected proves
    // connect() throws.
    @Test fun certRejectionDuringConnectEmitsFailedRejected() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.rejectClientCerts = true
            server.start { }
            val client = PairingClient(TlsSocketFactory(identity), identity)
            launch { client.start("127.0.0.1", server.port) }
            val failed = client.events.filterIsInstance<PairingEvent.Failed>().first()
            assertEquals(PairingFailure.REJECTED, failed.reason)
        }
    }

    // Same bug, other sub-case: an unreachable host / refused connection surfaces from
    // factory.connect() as a plain IOException (ConnectException), not
    // TlsHandshakeRejectedException — before the fix, that also escaped start() uncaught. Port 1 on
    // loopback is reserved and nothing listens there, so the OS refuses the connection immediately.
    @Test fun connectionRefusedEmitsFailedConnectionLost() = runTest {
        val client = PairingClient(TlsSocketFactory(identity), identity)
        launch { client.start("127.0.0.1", 1) }
        val failed = client.events.filterIsInstance<PairingEvent.Failed>().first()
        assertEquals(PairingFailure.CONNECTION_LOST, failed.reason)
    }
}
