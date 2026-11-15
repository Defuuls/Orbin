package com.orbin.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.orbin.core.common.network.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkMonitor] backed by [ConnectivityManager] callbacks. Emits the current online, metered
 * and Data Saver state and updates as networks come and go. Uses callbackFlow so there are no
 * leaked listeners. When the metered check cannot decide, the connection is treated as metered.
 */
@Singleton
class ConnectivityNetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkMonitor {
        private val policy: Flow<Policy> =
            callbackFlow {
                val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
                if (connectivityManager == null) {
                    // No manager means we cannot tell; fail closed for previews.
                    channel.trySend(Policy(isOnline = false, isMetered = true, isDataSaver = false))
                    channel.close()
                    return@callbackFlow
                }

                // Track validated networks; online == at least one network has INTERNET + VALIDATED.
                val networks = mutableSetOf<Network>()

                fun emitPolicy() {
                    channel.trySend(currentPolicy(connectivityManager, networks))
                }

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) {
                            val validated =
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            if (validated) networks.add(network) else networks.remove(network)
                            emitPolicy()
                        }

                        override fun onLost(network: Network) {
                            networks.remove(network)
                            emitPolicy()
                        }
                    }

                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context?,
                            intent: Intent?,
                        ) {
                            emitPolicy()
                        }
                    }

                // Seed with the current capability-validated state before registering the callback.
                val activeNetwork = connectivityManager.activeNetwork
                val activeCaps = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
                val initiallyOnline =
                    activeCaps != null &&
                        activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        activeCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                activeNetwork?.takeIf { initiallyOnline }?.let(networks::add)
                emitPolicy()

                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                connectivityManager.registerNetworkCallback(request, callback)
                context.registerReceiver(
                    receiver,
                    IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
                    Context.RECEIVER_NOT_EXPORTED,
                )

                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                    context.unregisterReceiver(receiver)
                }
            }.conflate()
                .distinctUntilChanged()

        override val isOnline: Flow<Boolean> = policy.map { it.isOnline }.distinctUntilChanged()

        override val isMetered: Flow<Boolean> = policy.map { it.isMetered }.distinctUntilChanged()

        override val isDataSaver: Flow<Boolean> = policy.map { it.isDataSaver }.distinctUntilChanged()

        private data class Policy(
            val isOnline: Boolean,
            val isMetered: Boolean,
            val isDataSaver: Boolean,
        )

        private fun currentPolicy(
            connectivityManager: ConnectivityManager,
            networks: Set<Network>,
        ): Policy {
            val caps =
                connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
            // Missing capabilities means we cannot tell, so treat the connection as metered.
            val metered =
                caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val dataSaver =
                runCatching {
                    connectivityManager.restrictBackgroundStatus ==
                        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                }.getOrDefault(false)
            return Policy(
                isOnline = networks.isNotEmpty(),
                isMetered = metered,
                isDataSaver = dataSaver,
            )
        }
    }
