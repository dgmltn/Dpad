package com.dgmltn.dpad.iosshared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dgmltn.dpad.data.store.createDataStore
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Provides the three platform singletons `:data`'s `dataModule` requires on iOS: the Preferences
 * DataStore (backed by a file in the app's Documents directory), the Bonjour-backed [MdnsBrowser],
 * and the shared session scope.
 *
 * The session scope is BOTH single-threaded-confined (`Dispatchers.Main`, which on Kotlin/Native is
 * confined to the main queue and backs RemoteSession's generation guard in `:data`) AND a
 * `SupervisorJob` (so a rare DataStore/identity failure in one child doesn't cancel the whole shared
 * scope). Do not swap in a plain `Job()` or a background dispatcher here — both properties are
 * load-bearing (Plan 2, carried forward into Plan 3's Task 12).
 */
@OptIn(ExperimentalForeignApi::class)
val iosPlatformModule = module {
    single<DataStore<Preferences>> {
        val documentsUrl = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? NSURL
        val documentsPath = documentsUrl?.path ?: error("Unable to resolve Documents directory")
        createDataStore("$documentsPath/dpad.preferences_pb".toPath())
    }
    single { MdnsBrowser() }
    single<CoroutineScope>(named("session")) { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
}
