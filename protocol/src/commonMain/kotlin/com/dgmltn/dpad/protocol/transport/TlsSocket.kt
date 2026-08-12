package com.dgmltn.dpad.protocol.transport

import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.pairing.RsaPublicParams

/** Thrown from [TlsSocketFactory.connect] when the server rejects our client cert. Maps to PairingRequired upstream. */
class TlsHandshakeRejectedException(cause: Throwable) : Exception(cause)

interface TlsConnection : BytePipe {
    /** Captured from the handshake's peer (server) cert. */
    val serverPublicParams: RsaPublicParams
}

/** Trust-all client TLS presenting [identity] as our client cert. */
expect class TlsSocketFactory(identity: ClientIdentity) {
    /** Throws [TlsHandshakeRejectedException] when the server rejects OUR cert. */
    suspend fun connect(host: String, port: Int): TlsConnection
}
