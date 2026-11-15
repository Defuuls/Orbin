package com.orbin.ios

import com.orbin.core.common.network.NetworkMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_t
import platform.darwin.dispatch_queue_create

/**
 * [NetworkMonitor] backed by `NWPathMonitor` (`nw_path_monitor_t`). [isMetered] follows
 * `nw_path_is_expensive`; when the path cannot be read it is treated as metered. [isDataSaver]
 * follows `nw_path_is_constrained` (Low Data Mode).
 */
internal class IosNetworkMonitor : NetworkMonitor {
    private val policy: Flow<Policy> =
        callbackFlow {
            val monitor = nw_path_monitor_create()
            if (monitor == null) {
                channel.trySend(Policy(isOnline = false, isMetered = true, isDataSaver = false))
                channel.close()
                return@callbackFlow
            }
            val queue = dispatch_queue_create(QUEUE_LABEL, null)
            nw_path_monitor_set_update_handler(monitor) { path ->
                channel.trySend(policyFrom(path))
            }
            // Fail closed until the first path arrives, in case that callback is delayed.
            channel.trySend(Policy(isOnline = false, isMetered = true, isDataSaver = false))
            nw_path_monitor_set_queue(monitor, queue)
            nw_path_monitor_start(monitor)
            awaitClose { nw_path_monitor_cancel(monitor) }
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

    private fun policyFrom(path: nw_path_t): Policy {
        if (path == null) {
            return Policy(isOnline = false, isMetered = true, isDataSaver = false)
        }
        return Policy(
            isOnline = nw_path_get_status(path) == nw_path_status_satisfied,
            isMetered = nw_path_is_expensive(path),
            isDataSaver = nw_path_is_constrained(path),
        )
    }

    private companion object {
        const val QUEUE_LABEL = "com.orbin.network"
    }
}
