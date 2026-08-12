package com.dgmltn.dpad.protocol.discovery

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject

private const val TAG = "Mdns"
private const val SERVICE_TYPE = "_androidtvremote2._tcp."
private const val SERVICE_DOMAIN = "local."
private const val RESOLVE_TIMEOUT_SEC = 10.0

/**
 * `NSNetServiceBrowser`/`NSNetService` mirror of the Android actual's `NsdManager` discover+resolve
 * loop for `_androidtvremote2._tcp`. `NSNetService` is used over the lower-level `NWBrowser` C API
 * (`platform.Network`) because it resolves straight to a connectable `hostName`/`port` — the same
 * shape `NsdManager.resolveService` gives `onServiceResolved` — where `NWBrowser`'s bonjour browse
 * results hand back an unresolved `nw_endpoint_t` that needs a separate connect-time resolution any
 * consumer of the plain `host: String, port: Int` in [DiscoveredTv] can't use directly.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MdnsBrowser {
    actual fun discovered(): Flow<List<DiscoveredTv>> = callbackFlow {
        val logger = Logger.withTag(TAG)
        val services = mutableMapOf<String, DiscoveredTv>()
        // NSNetService/NSNetServiceBrowser don't retain their delegate — keep strong refs here for
        // the lifetime of discovery, or ARC would deallocate them mid-resolve.
        val resolvingServices = mutableListOf<NSNetService>()

        val netServiceDelegate = object : NSObject(), NSNetServiceDelegateProtocol {
            override fun netServiceDidResolveAddress(sender: NSNetService) {
                val host = sender.hostName
                if (host == null) {
                    logger.w { "Resolved ${sender.name} with no hostName" }
                    resolvingServices.remove(sender)
                    return
                }
                val port = sender.port.toInt()
                logger.i { "Service resolved: ${sender.name} at $host:$port" }
                services[sender.name] = DiscoveredTv(name = sender.name, host = host, port = port)
                resolvingServices.remove(sender)
                trySend(services.values.toList())
            }

            override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
                logger.w { "Failed to resolve service ${sender.name}: $didNotResolve" }
                resolvingServices.remove(sender)
            }
        }

        val browserDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
            @kotlinx.cinterop.ObjCSignatureOverride
            override fun netServiceBrowser(
                browser: NSNetServiceBrowser,
                didFindService: NSNetService,
                moreComing: Boolean,
            ) {
                logger.i { "Service found: ${didFindService.name}" }
                didFindService.setDelegate(netServiceDelegate)
                resolvingServices += didFindService
                didFindService.resolveWithTimeout(RESOLVE_TIMEOUT_SEC)
            }

            @kotlinx.cinterop.ObjCSignatureOverride
            override fun netServiceBrowser(
                browser: NSNetServiceBrowser,
                didRemoveService: NSNetService,
                moreComing: Boolean,
            ) {
                logger.i { "Service lost: ${didRemoveService.name}" }
                services.remove(didRemoveService.name)
                trySend(services.values.toList())
            }
        }

        val browser = NSNetServiceBrowser()
        browser.delegate = browserDelegate
        logger.i { "Discovery started for $SERVICE_TYPE" }
        browser.searchForServicesOfType(SERVICE_TYPE, inDomain = SERVICE_DOMAIN)

        awaitClose {
            logger.i { "Discovery stopped for $SERVICE_TYPE" }
            browser.stop()
        }
    }
}
