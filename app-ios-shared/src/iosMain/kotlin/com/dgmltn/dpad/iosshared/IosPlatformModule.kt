package com.dgmltn.dpad.iosshared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dgmltn.dpad.data.store.createDataStore
import com.dgmltn.dpad.protocol.discovery.MdnsBrowser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * Provides the four platform singletons `:data`'s `dataModule` requires on iOS: the Preferences
 * DataStore (backed by a file in the app's Documents directory), the Bonjour-backed [MdnsBrowser],
 * the shared session scope, and the app-in-foreground signal.
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
    // Starts true: Koin starts from DpadApp.init while the app is launching into the foreground.
    // Observers live as long as the process (this is a singleton), so they're never removed.
    single<StateFlow<Boolean>>(named("appInForeground")) {
        val state = MutableStateFlow(true)
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            state.value = true
        }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            state.value = false
        }
        state.asStateFlow()
    }
}
