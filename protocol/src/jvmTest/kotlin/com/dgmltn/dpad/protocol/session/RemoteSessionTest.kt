package com.dgmltn.dpad.protocol.session

import app.cash.turbine.test
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import com.dgmltn.dpad.protocol.fake.FakeTvServer
import com.dgmltn.dpad.protocol.transport.TlsSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import remote.RemoteKeyCode
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class RemoteSessionTest {
    private val identity = ClientIdentityGenerator.generate("dpad-client")

    private fun session(server: FakeTvServer, scope: kotlinx.coroutines.CoroutineScope) = RemoteSession(
        scope = scope, factory = TlsSocketFactory(identity),
        resolveHost = { HostAddress("127.0.0.1", server.port) },
        backoffMillis = listOf(10, 20),   // fast for tests
    )

    @Test fun handshakesToConnected() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
        }
    }

    @Test fun answersPingAndTracksVolume() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            server.sendPing(42)
            server.awaitPingResponse(42)          // fake records ping responses; suspends until seen
            server.sendVolume(level = 7, max = 100, muted = false)
            s.volume.test { awaitItemUntil { it?.level == 7 && it.max == 100 } }
        }
    }

    @Test fun keySentWhenConnectedDroppedWhenNot() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.sendKey(RemoteKeyCode.KEYCODE_DPAD_UP)      // before connect: dropped
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            s.sendKey(RemoteKeyCode.KEYCODE_DPAD_DOWN)
            server.awaitKeyInject(RemoteKeyCode.KEYCODE_DPAD_DOWN)
            assertTrue(server.receivedMessages.none { it.remote_key_inject?.key_code == RemoteKeyCode.KEYCODE_DPAD_UP })
        }
    }

    @Test fun reconnectsAfterDrop() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test {
                awaitItemUntil { it == SessionState.Connected }
                server.dropConnection()
                awaitItemUntil { it == SessionState.Connecting }
                awaitItemUntil { it == SessionState.Connected }   // fake auto-accepts next connection
            }
        }
    }

    // Fix Round 1 / FIX 1 regression test. A prior version only set state -> Connecting at the TOP
    // of the next loop iteration, i.e. AFTER the backoff delay ran — so `state` kept lying
    // `Connected` for the entire backoff window following a drop. Uses a backoff large enough
    // (300ms) that "Connecting reported immediately on loss" and "Connecting reported only after
    // backoff" are trivially distinguishable with a timeout well under that window.
    @Test fun connectingReportedImmediatelyOnDrop_notAfterBackoffDelay() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = RemoteSession(
                scope = backgroundScope, factory = TlsSocketFactory(identity),
                resolveHost = { HostAddress("127.0.0.1", server.port) },
                backoffMillis = listOf(300),
            )
            s.connect()
            s.state.test {
                awaitItemUntil { it == SessionState.Connected }
                server.dropConnection()
                // Must observe Connecting well before the 300ms backoff elapses. `withTimeout`'s
                // deadline is a `delay` on the CALLING coroutine's dispatcher — under runTest
                // that's the virtual-time test dispatcher, which fires the deadline the instant it
                // looks idle (the instant this suspends waiting on a Flow fed by a background
                // coroutine doing real socket I/O, invisible to the virtual clock) rather than
                // after 150 real ms. Same fix as PairingClient's withRealTimeout: run the deadline
                // on Dispatchers.Default, which isn't part of that virtual clock.
                val next = withContext(Dispatchers.Default) { withTimeout(150.milliseconds) { awaitItem() } }
                assertEquals(SessionState.Connecting, next)
                awaitItemUntil { it == SessionState.Connected }   // fake auto-accepts the retry
            }
        }
    }

    // Fix Round 1 / FIX 2 regression test. A prior version had no guard against a disconnect()
    // immediately followed by connect(): if the OLD runLoop was still unwinding (e.g. parked in
    // the not-reliably-cancellable factory.connect()) when the NEW runLoop set up its own
    // activeChannel, the old generation's eventual writes/cleanup could clobber the new
    // generation's live channel, silently dropping sends despite `state == Connected`. Exercises
    // the disconnect()-then-immediate-connect() path (this file previously had no test calling
    // disconnect() at all) and proves the fresh generation's channel survives by asserting the
    // fake server actually receives a key sent after the second connect.
    @Test fun disconnectThenImmediateConnectDoesNotLoseTheNewChannel() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.Connected } }

            s.disconnect()
            assertEquals(SessionState.Disconnected, s.state.value)
            s.connect()

            s.state.test { awaitItemUntil { it == SessionState.Connected } }
            s.sendKey(RemoteKeyCode.KEYCODE_DPAD_UP)
            server.awaitKeyInject(RemoteKeyCode.KEYCODE_DPAD_UP)
        }
    }

    // Per Task 6's review, TlsHandshakeRejectedException can't be trusted on a single occurrence —
    // a server-side cert rejection is indistinguishable at the TLS layer from a broken-pipe RST
    // caused by a transient network blip. RemoteSession therefore only surfaces PairingRequired
    // after PAIRING_REQUIRED_THRESHOLD *consecutive* rejections on the same logical connect loop.
    // rejectClientCerts stays true across every reconnect attempt FakeTvServer accepts here, so the
    // threshold is naturally driven by RemoteSession's own retry loop — no extra scripting needed.
    @Test fun certRejectionBecomesPairingRequired() = runTest {
        FakeTvServer(requireClientCert = true).use { server ->
            server.rejectClientCerts = true
            server.startSessionServer()
            val s = session(server, backgroundScope)
            s.connect()
            s.state.test { awaitItemUntil { it == SessionState.PairingRequired } }
        }
    }
}

/** Awaits items until predicate matches (Room-style condition awaiting, per Doug's test prefs). */
suspend fun <T> app.cash.turbine.TurbineTestContext<T>.awaitItemUntil(predicate: (T) -> Boolean): T {
    while (true) { val item = awaitItem(); if (predicate(item)) return item }
}
