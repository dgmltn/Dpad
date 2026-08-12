package com.dgmltn.dpad.protocol.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class MdnsBrowser(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val logger = Logger.withTag("Mdns")

    actual fun discovered(): Flow<List<DiscoveredTv>> = callbackFlow {
        val services = mutableMapOf<String, DiscoveredTv>()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                logger.i("Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                logger.i("Service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, ResolveListener(services) {
                    trySend(services.values.toList())
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                logger.i("Service lost: ${serviceInfo.serviceName}")
                services.remove(serviceInfo.serviceName)
                trySend(services.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) {
                logger.i("Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.e("Discovery failed for $serviceType: error code $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.e("Stop discovery failed for $serviceType: error code $errorCode")
            }
        }

        nsdManager.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            nsdManager.stopServiceDiscovery(discoveryListener)
        }
    }

    private inner class ResolveListener(
        private val services: MutableMap<String, DiscoveredTv>,
        private val onResolved: () -> Unit,
    ) : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            logger.w("Failed to resolve service ${serviceInfo.serviceName}: error code $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            logger.i("Service resolved: ${serviceInfo.serviceName} at ${serviceInfo.host}:${serviceInfo.port}")
            val discoveredTv = DiscoveredTv(
                name = serviceInfo.serviceName,
                host = serviceInfo.host?.hostAddress ?: return,
                port = serviceInfo.port,
            )
            services[serviceInfo.serviceName] = discoveredTv
            onResolved()
        }
    }
}
