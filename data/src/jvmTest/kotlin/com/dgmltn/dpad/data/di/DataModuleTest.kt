package com.dgmltn.dpad.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dgmltn.dpad.data.AutoDisconnectPolicy
import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.*
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.*

class DataModuleTest {
    @AfterTest fun tearDown() = stopKoin()

    @Test fun everyDomainContractResolves() {
        val platform = module {
            single<DataStore<Preferences>> { tempDataStore() }
            single { MdnsBrowser() }
            single<CoroutineScope>(named("session")) { CoroutineScope(Dispatchers.Unconfined) }
            single<StateFlow<Boolean>>(named("appInForeground")) { MutableStateFlow(true) }
        }
        val koin = startKoin { modules(platform, dataModule) }.koin
        assertNotNull(koin.get<DeviceRepository>())
        assertNotNull(koin.get<ShortcutRepository>())
        assertNotNull(koin.get<ClientIdentityStore>())
        assertNotNull(koin.get<DeviceDiscovery>())
        assertNotNull(koin.get<DevicePairer>())
        assertNotNull(koin.get<RemoteController>())
        assertNotNull(koin.get<AutoDisconnectPolicy>())
    }
}
