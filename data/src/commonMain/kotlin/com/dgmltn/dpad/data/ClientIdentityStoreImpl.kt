package com.dgmltn.dpad.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dgmltn.dpad.domain.ClientIdentityHandle
import com.dgmltn.dpad.domain.ClientIdentityStore
import com.dgmltn.dpad.protocol.crypto.ClientIdentity
import com.dgmltn.dpad.protocol.crypto.ClientIdentityGenerator
import kotlinx.coroutines.flow.first

class ClientIdentityStoreImpl(
    private val store: DataStore<Preferences>,
    private val commonName: String = "Dpad",
) : ClientIdentityStore {
    private val certKey = stringPreferencesKey("client_cert_pem")
    private val keyKey = stringPreferencesKey("client_key_pem")

    override suspend fun getOrCreate(): ClientIdentityHandle {
        store.data.first().let { prefs ->
            val cert = prefs[certKey]; val key = prefs[keyKey]
            if (cert != null && key != null) return ClientIdentityHandle(cert, key)
        }
        val generated = ClientIdentityGenerator.generate(commonName)
        store.edit { it[certKey] = generated.certificatePem; it[keyKey] = generated.privateKeyPem }
        return ClientIdentityHandle(generated.certificatePem, generated.privateKeyPem)
    }
}

/** Rebuild the :protocol ClientIdentity from the persisted handle (validates cert/key match). */
suspend fun ClientIdentityStore.protocolIdentity(): ClientIdentity {
    val handle = getOrCreate()
    return ClientIdentityGenerator.fromPem(handle.certificatePem, handle.privateKeyPem)
}
