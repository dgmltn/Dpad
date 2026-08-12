package com.dgmltn.dpad.domain

/** Supplies the app-wide client identity, generating and persisting it on first use. */
interface ClientIdentityStore {
    /** Returns the persisted identity, generating+persisting one the first time. Idempotent. */
    suspend fun getOrCreate(): ClientIdentityHandle
}

/** Opaque handle so :domain need not know :protocol's ClientIdentity type. */
data class ClientIdentityHandle(val certificatePem: String, val privateKeyPem: String)
