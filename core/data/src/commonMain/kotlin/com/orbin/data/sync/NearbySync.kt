package com.orbin.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orbin.domain.repository.NearbyDevice
import com.orbin.domain.repository.NearbySyncRepository
import com.orbin.domain.repository.NearbySyncState
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
internal data class NearbyPeer(
    val pair: String,
    val device: String,
    val name: String,
    val secret: String,
    val host: String,
    val port: Int,
    val synced: Long? = null,
    val trusted: Boolean = false,
)

@Serializable
internal data class NearbyIdentity(
    val device: String,
    val enabled: Boolean = false,
    val peers: List<NearbyPeer> = emptyList(),
) {
    val name: String get() = "Orbin ${device.take(5)}"
}

/**
 * A foreground-only encrypted TCP service. Pair IDs/nonces are public; all activity and device
 * identities are authenticated and encrypted. Pairing keys rotate before the first response, so
 * an invitation cannot authorize a second device after it has been used.
 */
@Suppress("TooManyFunctions")
class NearbySync(
    private val progress: NearbyProgress,
    private val preferences: DataStore<Preferences>,
    private val secrets: NearbySyncSecrets,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val addresses: () -> List<String> = ::nearbyAddresses,
    private val port: Int = NEARBY_PORT,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val networks: () -> List<NearbyNetwork> = ::nearbyNetworks,
    private val autoSyncMillis: Long = AUTO_SYNC_MS,
) : NearbySyncRepository {
    private val mutableState = MutableStateFlow(NearbySyncState())
    override val state = mutableState.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val connections = MutableStateFlow<Set<NearbyConnection>>(emptySet())
    private val configuration = Mutex()
    private val lifecycle = Mutex()
    private val listeningSocket = MutableStateFlow<io.ktor.network.sockets.ServerSocket?>(null)
    private val peerLocks = MutableStateFlow<Map<String, Mutex>>(emptyMap())
    private var identity: NearbyIdentity? = null
    private var listener: Job? = null
    private var expiry: Job? = null
    private val pending = MutableStateFlow<NearbyInvite?>(null)
    private var boundPort = port

    override fun setActive(active: Boolean) {
        foreground.value = active
        if (active) startListener() else stopListener()
    }

    override suspend fun setEnabled(enabled: Boolean) =
        guarded {
            change { it.copy(enabled = enabled) }
            if (enabled && foreground.value) startListener() else stopListener()
        }

    override suspend fun createPairingCode() =
        guarded {
            ensureListening()
            check(current().peers.size < MAX_NEARBY_DEVICES) { "Remove a device before adding another" }
            val host = addresses().firstOrNull(::localAddress) ?: error("Connect to Wi-Fi before pairing")
            val invite =
                NearbyInvite(
                    NearbyCrypto.token(),
                    NearbyCrypto.token(),
                    host,
                    boundPort,
                    now() + INVITE_LIFETIME_MS,
                )
            pending.value = invite
            if (!foreground.value) {
                pending.compareAndSet(invite, null)
                error("Open and unlock Orbin before pairing")
            }
            mutableState.update {
                it.copy(
                    pairingCode = invite.code().takeIf { foreground.value && pending.value?.id == invite.id },
                    message = "Enter this code on your other device. It expires in 10 minutes.",
                )
            }
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(INVITE_LIFETIME_MS)
                    if (pending.value?.id == invite.id) {
                        pending.value = null
                        mutableState.update { it.copy(pairingCode = null) }
                    }
                }
        }

    override suspend fun join(code: String) =
        guarded {
            val invite = NearbyInvite.parse(code.trim())
            require(invite.expires > now()) { "Pairing code expired" }
            ensureListening()
            require(current().peers.size < MAX_NEARBY_DEVICES) { "Too many paired devices" }
            exchange(invite.id, invite.secret, invite.host, invite.port, invitation = true)
        }

    override suspend fun syncNow() =
        guarded {
            ensureListening()
            current().peers.forEach { peer ->
                guarded { exchange(peer.pair, peer.secret, peer.host, peer.port) }
            }
        }

    override suspend fun setTrusted(
        deviceId: String,
        trusted: Boolean,
    ) = guarded {
        val peer = current().peers.firstOrNull { it.device == deviceId } ?: return@guarded
        lock(peer.pair).withLock {
            change { value ->
                value.copy(
                    peers =
                        value.peers.map {
                            if (it.device ==
                                deviceId
                            ) {
                                it.copy(trusted = trusted)
                            } else {
                                it
                            }
                        },
                )
            }
        }
    }

    override suspend fun forget(deviceId: String) =
        guarded {
            val peer = current().peers.firstOrNull { it.device == deviceId } ?: return@guarded
            lock(peer.pair).withLock {
                change { value -> value.copy(peers = value.peers.filterNot { it.device == deviceId }) }
                preferences.edit { it.remove(baseKey(peer.pair)) }
            }
        }

    private fun startListener() {
        scope.launch {
            guarded {
                lifecycle.withLock {
                    if (!foreground.value || listener?.isActive == true) {
                        return@withLock
                    }
                    if (!current().enabled || !foreground.value) return@withLock
                    listener = scope.launch { runListener() }
                }
            }
        }
    }

    private suspend fun runListener() {
        val sockets = NearbySockets()
        try {
            if (!foreground.value) return
            val server = sockets.listen(port)
            listeningSocket.value = server
            boundPort = (server.localAddress as InetSocketAddress).port
            try {
                check(foreground.value)
                mutableState.update { it.copy(listening = true, message = null) }
                coroutineScope {
                    launch { automaticSync() }
                    val permits = Semaphore(MAX_CONNECTIONS)
                    while (isActive) {
                        val socket = server.accept()
                        val connection = NearbyConnection(socket)
                        if (!localAddress(connection.host) || !permits.tryAcquire()) {
                            connection.close()
                        } else {
                            connections.update { it + connection }
                            launch {
                                try {
                                    withTimeout(TRANSFER_TIMEOUT_MS) { receive(connection) }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    // Unauthenticated traffic gets no activity data or detailed error response.
                                } finally {
                                    connection.close()
                                    connections.update { it - connection }
                                    permits.release()
                                }
                            }
                        }
                    }
                }
            } finally {
                listeningSocket.compareAndSet(server, null)
                server.close()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update {
                it.copy(
                    message = "Nearby sync couldn't listen. Check local network access and try again.",
                )
            }
        } finally {
            sockets.close()
            if (listener == kotlinx.coroutines.currentCoroutineContext()[Job]) {
                mutableState.update { it.copy(listening = false) }
            }
        }
    }

    private fun stopListener() {
        expiry?.cancel()
        pending.value = null
        listeningSocket.value?.close()
        connections.value.forEach { it.close() }
        listener?.cancel()
        listener = null
        mutableState.update { it.copy(listening = false, pairingCode = null) }
    }

    private suspend fun ensureListening() {
        check(foreground.value) { "Open and unlock Orbin before syncing" }
        if (!current().enabled) change { it.copy(enabled = true) }
        startListener()
        withTimeout(START_TIMEOUT_MS) { state.first { it.listening } }
    }

    private suspend fun automaticSync() {
        while (foreground.value) {
            val local = current()
            // One automatic initiator per pair avoids both endpoints opening duplicate sessions.
            local.peers.filter { it.trusted && local.device < it.device && onLocalNetwork(it.host) }.forEach { peer ->
                guarded { exchange(peer.pair, peer.secret, peer.host, peer.port, automatic = true) }
            }
            delay(autoSyncMillis)
        }
    }

    private suspend fun exchange(
        pair: String,
        secret: String,
        host: String,
        remotePort: Int,
        invitation: Boolean = false,
        automatic: Boolean = false,
    ) {
        val mutex = lock(pair)
        if (!mutex.tryLock()) return
        try {
            withTimeout(TRANSFER_TIMEOUT_MS) {
                check(foreground.value)
                if (!invitation) {
                    val peer = current().peers.single { it.pair == pair && it.secret == secret }
                    if (automatic) require(peer.trusted && onLocalNetwork(host))
                }
                val sockets = NearbySockets()
                try {
                    val connection = sockets.connect(host, remotePort)
                    connections.update { it + connection }
                    try {
                        check(foreground.value)
                        send(connection, pair, secret, host, remotePort, automatic)
                    } finally {
                        connection.close()
                        connections.update { it - connection }
                    }
                } finally {
                    sockets.close()
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun send(
        connection: NearbyConnection,
        pair: String,
        secret: String,
        host: String,
        remotePort: Int,
        automatic: Boolean,
    ) {
        val local = current()
        val nonce = NearbyCrypto.token()
        connection.write(NearbyJson.encodeToString(NearbyHello(pair, nonce)).encodeToByteArray())
        val challenge = connection.read(HELLO_LIMIT).decodeToString()
        require(validToken(challenge))
        val context = "orbin-nearby1|$pair|$nonce|$challenge"
        val snapshot = progress.snapshot().also(::validateNearbyDocument)
        val request = NearbyRequest(local.device, local.name, boundPort, snapshot, automatic)
        connection.write(
            NearbyCrypto.encrypt(secret, "$context|request", NearbyJson.encodeToString(request).encodeToByteArray()),
        )
        val response =
            NearbyJson.decodeFromString<NearbyResponse>(
                NearbyCrypto.decrypt(secret, "$context|response", connection.read()).decodeToString(),
            )
        validateDevice(response.device, response.name)
        require(response.device != local.device && validToken(response.secret))
        val existing = local.peers.firstOrNull { it.pair == pair }
        require(existing == null || existing.device == response.device)
        validateNearbyDocument(response.document)
        check(foreground.value)
        progress.merge(snapshot, response.document)
        rememberPeer(NearbyPeer(pair, response.device, response.name, response.secret, host, remotePort, now()))
        saveBase(pair, response.document)
        connection.write(NearbyCrypto.encrypt(secret, "$context|ack", ACK.encodeToByteArray()))
        require(NearbyCrypto.decrypt(secret, "$context|done", connection.read(HELLO_LIMIT)).decodeToString() == ACK)
        mutableState.update { it.copy(message = "Synced with ${response.name}") }
    }

    private suspend fun receive(connection: NearbyConnection) {
        val hello = NearbyJson.decodeFromString<NearbyHello>(connection.read(HELLO_LIMIT).decodeToString())
        require(validToken(hello.pair) && validToken(hello.nonce))
        // Reject unknown selectors before allocating a lock or generating a challenge.
        if (current().peers.none { it.pair == hello.pair } && pending.value?.id != hello.pair) return
        val mutex = lock(hello.pair)
        if (!mutex.tryLock()) return
        try {
            val local = current()
            val known = local.peers.firstOrNull { it.pair == hello.pair }
            val invite = pending.value?.takeIf { it.id == hello.pair && it.expires > now() }
            val secret = known?.secret ?: invite?.secret ?: return
            val challenge = NearbyCrypto.token()
            connection.write(challenge.encodeToByteArray())
            val context = "orbin-nearby1|${hello.pair}|${hello.nonce}|$challenge"
            val request =
                NearbyJson.decodeFromString<NearbyRequest>(
                    NearbyCrypto.decrypt(secret, "$context|request", connection.read()).decodeToString(),
                )
            validateDevice(request.device, request.name)
            require(request.device != local.device && request.port in NEARBY_PORT_RANGE)
            require(known == null || known.device == request.device)
            check(
                foreground.value &&
                    (known != null || pending.value?.id == invite?.id && invite != null && invite.expires > now()),
            )
            if (request.automatic) require(known?.trusted == true && onLocalNetwork(connection.host))
            validateNearbyDocument(request.document)
            val merged = progress.merge(readBase(hello.pair), request.document)
            val permanent = known?.secret ?: NearbyCrypto.token()
            // Persist and rotate before replying. If delivery fails, the user can re-pair; the
            // consumed invitation can never enroll another device or replace the established key.
            rememberPeer(NearbyPeer(hello.pair, request.device, request.name, permanent, connection.host, request.port))
            if (invite != null) {
                pending.value = null
                expiry?.cancel()
                mutableState.update { it.copy(pairingCode = null) }
            }
            val response = NearbyResponse(local.device, local.name, permanent, merged)
            connection.write(
                NearbyCrypto.encrypt(
                    secret,
                    "$context|response",
                    NearbyJson.encodeToString(response).encodeToByteArray(),
                ),
            )
            val ack = NearbyCrypto.decrypt(secret, "$context|ack", connection.read(HELLO_LIMIT)).decodeToString()
            require(ack == ACK)
            saveBase(hello.pair, merged)
            rememberPeer(
                NearbyPeer(hello.pair, request.device, request.name, permanent, connection.host, request.port, now()),
            )
            connection.write(NearbyCrypto.encrypt(secret, "$context|done", ACK.encodeToByteArray()))
            mutableState.update { it.copy(message = "Synced with ${request.name}") }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun current(): NearbyIdentity = configuration.withLock { load() }

    private suspend fun load(): NearbyIdentity {
        identity?.let { return it }
        val encoded = secrets.store.read()
        val value =
            if (encoded.isNullOrEmpty()) {
                NearbyIdentity(
                    NearbyCrypto.token(),
                )
            } else {
                NearbyJson.decodeFromString(encoded)
            }
        require(validToken(value.device) && value.peers.size <= MAX_NEARBY_DEVICES)
        require(
            value.peers.all {
                validToken(it.pair) &&
                    validToken(it.secret) &&
                    localAddress(it.host) &&
                    it.port in NEARBY_PORT_RANGE
            },
        )
        if (encoded.isNullOrEmpty()) secrets.store.write(NearbyJson.encodeToString(value))
        identity = value
        publish(value)
        return value
    }

    private suspend fun change(edit: (NearbyIdentity) -> NearbyIdentity) =
        configuration.withLock {
            val value = edit(load())
            secrets.store.write(NearbyJson.encodeToString(value))
            identity = value
            publish(value)
        }

    private suspend fun rememberPeer(peer: NearbyPeer) =
        change { value ->
            // Re-pairing requires explicit removal first, so a concurrent old session cannot
            // overwrite a new device key under a different pair lock.
            require(value.peers.none { it.device == peer.device && it.pair != peer.pair })
            val peers =
                value.peers.filterNot { it.pair == peer.pair } +
                    peer.copy(trusted = value.peers.firstOrNull { it.pair == peer.pair }?.trusted ?: false)
            require(peers.size <= MAX_NEARBY_DEVICES)
            value.copy(peers = peers)
        }

    private fun publish(value: NearbyIdentity) {
        mutableState.update { state ->
            state.copy(
                enabled = value.enabled,
                devices = value.peers.map { NearbyDevice(it.device, it.name, it.synced, it.trusted) },
            )
        }
    }

    private suspend fun readBase(pair: String): SyncDocument? =
        preferences.data.first()[baseKey(pair)]?.let { NearbyJson.decodeFromString<SyncDocument>(it) }

    private suspend fun saveBase(
        pair: String,
        document: SyncDocument,
    ) {
        preferences.edit { it[baseKey(pair)] = NearbyJson.encodeToString(document) }
    }

    private fun onLocalNetwork(host: String): Boolean =
        runCatching { networks().any { it.contains(host) } }
            .getOrDefault(false)

    private fun baseKey(pair: String) = stringPreferencesKey("nearby_agreed_$pair")

    private fun lock(pair: String): Mutex {
        val mutex = Mutex()
        peerLocks.update { if (pair in it) it else it + (pair to mutex) }
        return peerLocks.value.getValue(pair)
    }

    private fun validateDevice(
        device: String,
        name: String,
    ) {
        require(validToken(device) && name.length in 1..MAX_DEVICE_NAME_LENGTH && name.none { it.isISOControl() })
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        mutableState.update { it.copy(busy = true) }
        try {
            block()
        } catch (_: TimeoutCancellationException) {
            mutableState.update {
                it.copy(message = "Couldn't reach the other device. Keep Orbin open on both devices and try again.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update {
                it.copy(
                    message =
                        "Couldn't sync. Keep both devices open on the same Wi-Fi; " +
                            "check the code and local network permission.",
                )
            }
        } finally {
            mutableState.update { it.copy(busy = false) }
        }
    }

    companion object {
        private const val MAX_CONNECTIONS = 3
        private const val MAX_DEVICE_NAME_LENGTH = 64
        private const val TRANSFER_TIMEOUT_MS = 30_000L
        private const val START_TIMEOUT_MS = 5_000L
        private const val AUTO_SYNC_MS = 30_000L
        private const val HELLO_LIMIT = 2048
        private const val ACK = "orbin-nearby-accepted"
    }
}
