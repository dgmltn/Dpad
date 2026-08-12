package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.store.tempDataStore
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ClientIdentityStoreImplTest {
    @Test fun generatesAndPersistsOnFirstCall() = runTest {
        val store = tempDataStore()
        val handle = ClientIdentityStoreImpl(store).getOrCreate()
        assertTrue(handle.certificatePem.contains("BEGIN CERTIFICATE"))
        assertTrue(handle.privateKeyPem.contains("BEGIN PRIVATE KEY"))
    }

    @Test fun returnsTheSameIdentityAcrossCallsAndInstances() = runTest {
        val store = tempDataStore()
        val first = ClientIdentityStoreImpl(store).getOrCreate()
        // A fresh store instance over the SAME backing file must reload, not regenerate.
        val second = ClientIdentityStoreImpl(store).getOrCreate()
        assertEquals(first.certificatePem, second.certificatePem)
        assertEquals(first.privateKeyPem, second.privateKeyPem)
    }

    @Test fun protocolIdentityRebuildsFromPersistedPems() = runTest {
        val store = tempDataStore()
        val impl = ClientIdentityStoreImpl(store)
        impl.getOrCreate()
        val identity = impl.protocolIdentity()   // must not throw (fromPem validates cert/key match)
        assertTrue(identity.certificatePem.contains("BEGIN CERTIFICATE"))
    }
}
