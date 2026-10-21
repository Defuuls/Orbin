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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Clock

@Serializable
internal data class NearbyPeer(
    val pair: String,
    val device: String,
    val name: String,
    val secret: String,
    /** The peer's last known local IPv4 address; null when it was only ever reached over Bluetooth. */
    val host: String?,
    val port: Int,
    val synced: Long? = null,
    val trusted: Boolean = false,
    /** The relay both devices meet on over the Internet, if pairing involved one. */
    val relay: String? = null,
)

@Serializable
internal data class NearbyIdentity(
    val device: String,
    val enabled: Boolean = false,
    val peers: List<NearbyPeer> = emptyList(),
    val bluetooth: Boolean = false,
    /** This device's relay server address ([normalizeRelayUrl]), or null for none. */
    val relay: String? = null,
) {
    val name: String get() = "Orbin ${device.take(5)}"
}

/**
 * A foreground-only encrypted TCP service. Pair IDs/nonces are public; all activity and device
 * identities are authenticated and encrypted. Pairing keys rotate before the first response, so
 * an invitation cannot authorize a second device after it has been used.
 */
@Suppress("TooManyFunctions", "LargeClass") // One state machine over every transport, sharing its locks.
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
    private val bluetooth: NearbyBluetooth? = secrets.bluetooth,
    private val relay: NearbyRelay? = null,
) : NearbySyncRepository {
    private val mutableState = MutableStateFlow(NearbySyncState())
    override val state = mutableState.asStateFlow()
    private val foreground = MutableStateFlow(false)
    private val connections = MutableStateFlow<Set<NearbyChannel>>(emptySet())
    private val permits = Semaphore(MAX_CONNECTIONS)
    private val configuration = Mutex()
    private val lifecycle = Mutex()
    private val listeningSocket = MutableStateFlow<io.ktor.network.sockets.ServerSocket?>(null)
    private val peerLocks = MutableStateFlow<Map<String, Mutex>>(emptyMap())
    private var identity: NearbyIdentity? = null
    private var listener: Job? = null
    private var bluetoothServer: Job? = null
    private var relayServer: Job? = null
    private var expiry: Job? = null
    private val activeCalls = MutableStateFlow(0)
    private var localNetworkPrompted = false
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

    override suspend fun setBluetoothEnabled(enabled: Boolean) =
        guarded {
            if (enabled) check(bluetooth?.prepare() == true) { "Turn on Bluetooth and allow Bluetooth access" }
            change { it.copy(bluetooth = enabled) }
            if (enabled) startBluetooth() else stopBluetooth()
        }

    override suspend fun setRelayUrl(url: String) =
        guarded {
            val normalized = if (url.isBlank()) null else normalizeRelayUrl(url)
            require(url.isBlank() || normalized != null) { "Use an https:// relay address" }
            change { it.copy(relay = normalized) }
            stopRelay()
            if (normalized != null) startRelay()
        }

    override fun refreshBluetooth() {
        mutableState.update { it.copy(bluetoothReady = bluetooth?.ready() == true) }
        if (foreground.value && state.value.listening) startBluetooth()
    }

    override suspend fun createPairingCode() =
        guarded {
            ensureListening()
            check(current().peers.size < MAX_NEARBY_DEVICES) { "Remove a device before adding another" }
            val host = addresses().firstOrNull(::localAddress)
            val overBluetooth = bluetoothOn()
            val overRelay = current().relay?.takeIf { relay != null }
            check(host != null || overBluetooth || overRelay != null) {
                "Connect to Wi-Fi, turn on Bluetooth sync or set a relay before pairing"
            }
            val invite =
                NearbyInvite(
                    NearbyCrypto.token(),
                    NearbyCrypto.token(),
                    host,
                    boundPort,
                    now() + INVITE_LIFETIME_MS,
                    bluetooth = overBluetooth,
                    relay = overRelay,
                )
            pending.value = invite
            if (!foreground.value) {
                pending.compareAndSet(invite, null)
                error("Open and unlock Orbin before pairing")
            }
            mutableState.update {
                it.copy(
                    pairingCode = invite.code().takeIf { foreground.value && pending.value?.id == invite.id },
                    // The status row shows "waiting for your other device" while the code is live.
                    message = null,
                    messageIsError = false,
                )
            }
            expiry?.cancel()
            expiry =
                scope.launch {
                    delay(INVITE_LIFETIME_MS)
                    if (pending.value?.id == invite.id) {
                        pending.value = null
                        mutableState.update {
                            it.copy(
                                pairingCode = null,
                                message = "The pairing code expired. Create a new one to add a device.",
                                messageIsError = true,
                            )
                        }
                    }
                }
        }

    override suspend fun join(code: String) =
        guarded {
            val invite =
                runCatching { NearbyInvite.parse(code.trim()) }.getOrElse {
                    throw NearbyUserException("That isn't a valid pairing code. Copy the whole code and try again.")
                }
            if (invite.expires <= now()) {
                throw NearbyUserException("That pairing code expired. Create a new one on the other device.")
            }
            ensureListening()
            require(current().peers.size < MAX_NEARBY_DEVICES) { "Too many paired devices" }
            val beacons = if (invite.bluetooth) listOf(NearbyBeacons.invite(invite.id)) else emptyList()
            val channels = invite.relay?.let { listOf(NearbyRelayChannels.invite(invite.id)) }.orEmpty()
            // Falling through is safe: a route that fails before the inviter answers leaves the
            // invitation unused, and one consumed mid-way is refused on every later route.
            val address = PeerAddress(invite.host, invite.port, invite.relay)
            tryRoutes(routes(invite.host, invite.port, beacons, invite.relay, channels)) { route ->
                exchange(invite.id, invite.secret, route, address, invitation = true)
            }
            // A device without its own relay adopts the inviter's, so the pair can meet again later.
            if (invite.relay != null && current().relay == null) {
                change { it.copy(relay = invite.relay) }
                startRelay()
            }
        }

    override suspend fun syncNow() =
        guarded {
            ensureListening()
            current().peers.forEach { peer ->
                guarded { syncPeer(peer) }
            }
        }

    /** Wi-Fi first when the saved address is on a current network, then Bluetooth, the relay, Wi-Fi anyway. */
    private suspend fun syncPeer(peer: NearbyPeer) {
        val beacons = if (bluetoothOn()) NearbyBeacons.device(peer.device, now()) else emptyList()
        val relayUrl = peer.relay ?: current().relay
        val channels = relayUrl?.let { NearbyRelayChannels.device(peer.device, now()) }.orEmpty()
        val address = PeerAddress(peer.host, peer.port, relayUrl)
        tryRoutes(routes(peer.host, peer.port, beacons, relayUrl, channels)) { route ->
            exchange(peer.pair, peer.secret, route, address)
        }
    }

    private suspend fun tryRoutes(
        routes: List<NearbyRoute>,
        attempt: suspend (NearbyRoute) -> Unit,
    ) {
        val failures = mutableListOf<Pair<NearbyRoute, Throwable>>()
        for (route in routes) {
            step(
                when (route) {
                    is NearbyRoute.Network -> "Connecting over Wi-Fi to ${route.host}…"
                    is NearbyRoute.Bluetooth -> "Looking for the other device over Bluetooth…"
                    is NearbyRoute.Relay -> "Connecting through the relay…"
                },
            )
            try {
                attempt(route)
                return
            } catch (timeout: TimeoutCancellationException) {
                failures += route to timeout
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") other: Exception,
            ) {
                failures += route to other
            }
        }
        throw NearbyRouteFailure(failures)
    }

    @Suppress("LongParameterList")
    private suspend fun routes(
        host: String?,
        port: Int,
        beacons: List<ByteArray>,
        relayUrl: String? = null,
        channels: List<String> = emptyList(),
    ): List<NearbyRoute> {
        val wifi = host?.let { NearbyRoute.Network(it, port) }
        val air = beacons.takeIf { it.isNotEmpty() && bluetoothUsable() }?.let { NearbyRoute.Bluetooth(it) }
        val internet =
            relayUrl?.takeIf { relay != null && channels.isNotEmpty() }?.let { NearbyRoute.Relay(it, channels) }
        return if (host != null && onLocalNetwork(host)) {
            listOfNotNull(wifi, air, internet)
        } else {
            listOfNotNull(air, internet, wifi)
        }
    }

    private suspend fun bluetoothOn(): Boolean = current().bluetooth && bluetoothUsable()

    /**
     * Ready now, or once the platform starts it for a user who turned Bluetooth sync on: iOS has
     * no Bluetooth state to read until its managers exist, and creating them may ask permission.
     */
    private suspend fun bluetoothUsable(): Boolean =
        bluetooth?.let { it.ready() || (current().bluetooth && it.prepare()) } == true

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
                mutableState.update {
                    it.copy(listening = true, message = null, bluetoothReady = bluetooth?.ready() == true)
                }
                if (nearbyNeedsLocalNetworkPrompt) scope.launch { promptForLocalNetwork() }
                startBluetooth()
                startRelay()
                coroutineScope {
                    launch { automaticSync() }
                    while (isActive) {
                        val socket = server.accept()
                        val connection = NearbyConnection(socket)
                        if (!localAddress(connection.host)) {
                            connection.close()
                        } else {
                            launch { incoming(connection, connection.host) }
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

    /** One accepted channel from any transport; [host] is its local IPv4 address, or null for Bluetooth. */
    private suspend fun incoming(
        channel: NearbyChannel,
        host: String?,
    ) {
        if (!permits.tryAcquire()) {
            channel.close()
            return
        }
        connections.update { it + channel }
        try {
            withTimeout(TRANSFER_TIMEOUT_MS) { receive(channel, host) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Unauthenticated traffic gets no activity data or detailed error response.
        } finally {
            channel.close()
            connections.update { it - channel }
            permits.release()
        }
    }

    /** Advertise this device (and any open invitation) over Bluetooth while the listener runs. */
    private fun startBluetooth() {
        val transport = bluetooth ?: return
        scope.launch {
            lifecycle.withLock {
                if (bluetoothServer?.isActive == true || !foreground.value) return@withLock
                if (!current().enabled || !bluetoothOn()) return@withLock
                bluetoothServer =
                    scope.launch {
                        try {
                            transport.serve(beacons()) { channel -> incoming(channel, null) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            mutableState.update {
                                it.copy(message = "Bluetooth sync stopped. Check that Bluetooth is on and try again.")
                            }
                        }
                    }
            }
        }
    }

    /** Hold a listening socket on the relay for this device (and any open invitation). */
    private fun startRelay() {
        val transport = relay ?: return
        scope.launch {
            lifecycle.withLock {
                if (relayServer?.isActive == true || !foreground.value) return@withLock
                val local = current()
                val url = local.relay ?: return@withLock
                if (!local.enabled) return@withLock
                relayServer =
                    scope.launch {
                        transport.serve(url, relayChannels()) { channel -> incoming(channel, null) }
                    }
            }
        }
    }

    private fun stopRelay() {
        relayServer?.cancel()
        relayServer = null
    }

    private fun relayChannels(): Flow<List<String>> =
        flow {
            while (true) {
                val invite = pending.value?.takeIf { it.relay != null }?.let { NearbyRelayChannels.invite(it.id) }
                val own = NearbyRelayChannels.device(current().device, now()).first()
                emit(listOfNotNull(invite, own))
                delay(BEACON_REFRESH_MS)
            }
        }.distinctUntilChanged()

    private fun stopBluetooth() {
        bluetoothServer?.cancel()
        bluetoothServer = null
    }

    /** The invitation's beacon while one is open, and this device's rotating beacon. */
    private fun beacons(): Flow<List<ByteArray>> =
        flow {
            while (true) {
                val invite = pending.value?.takeIf { it.bluetooth }?.let { NearbyBeacons.invite(it.id) }
                val own = NearbyBeacons.device(current().device, now()).first()
                emit(listOfNotNull(invite, own))
                delay(BEACON_REFRESH_MS)
            }
        }.distinctUntilChanged { old, new -> old.size == new.size && old.zip(new).all { (a, b) -> a.contentEquals(b) } }

    private fun stopListener() {
        stopBluetooth()
        stopRelay()
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
            // Automatic transfers stay on the local network; Bluetooth is only used on request.
            local.peers
                .filter { it.trusted && local.device < it.device && it.host != null && onLocalNetwork(it.host) }
                .forEach { peer ->
                    val route = NearbyRoute.Network(checkNotNull(peer.host), peer.port)
                    val address = PeerAddress(peer.host, peer.port, peer.relay)
                    guarded { exchange(peer.pair, peer.secret, route, address, automatic = true) }
                }
            delay(autoSyncMillis)
        }
    }

    // Rethrows cancellation, a final busy peer and a final failure; each needs its own catch.
    @Suppress("ThrowsCount")
    private suspend fun exchange(
        pair: String,
        secret: String,
        route: NearbyRoute,
        address: PeerAddress,
        invitation: Boolean = false,
        automatic: Boolean = false,
    ) {
        val mutex = lock(pair)
        // Bound lock waits and every retry together, rather than allowing each to take 30 seconds.
        withTimeout(TRANSFER_TIMEOUT_MS) {
            val attempts = if (automatic || invitation) 1 else MANUAL_ATTEMPTS
            repeat(attempts) { attempt ->
                if (automatic) {
                    if (!mutex.tryLock()) return@withTimeout
                } else {
                    mutex.lock()
                }
                try {
                    connectAndSend(pair, secret, route, address, invitation, automatic)
                    return@withTimeout
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (busy: NearbyPeerBusyException) {
                    if (automatic) return@withTimeout
                    if (attempt == attempts - 1) throw busy
                } catch (
                    @Suppress("TooGenericExceptionCaught") failure: Exception,
                ) {
                    if (attempt == attempts - 1) throw failure
                } finally {
                    mutex.unlock()
                }
                // Release our lock so the peer can complete its own request before we retry.
                // Jitter schedules retries only; keys and nonces still use the crypto provider.
                delay(RETRY_DELAY_MS * (attempt + 1) + Random.nextLong(RETRY_DELAY_MS))
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun connectAndSend(
        pair: String,
        secret: String,
        route: NearbyRoute,
        address: PeerAddress,
        invitation: Boolean,
        automatic: Boolean,
    ) {
        withTimeout(TRANSFER_TIMEOUT_MS) {
            check(foreground.value)
            if (!invitation) {
                val peer = current().peers.single { it.pair == pair && it.secret == secret }
                if (automatic) require(peer.trusted && route is NearbyRoute.Network && onLocalNetwork(route.host))
            }
            when (route) {
                is NearbyRoute.Network -> {
                    val sockets = NearbySockets()
                    try {
                        sendOver(
                            sockets.connect(route.host, route.port),
                            pair,
                            secret,
                            address.copy(host = route.host, port = route.port),
                            automatic,
                        )
                    } finally {
                        sockets.close()
                    }
                }
                is NearbyRoute.Bluetooth ->
                    sendOver(checkNotNull(bluetooth).connect(route.beacons), pair, secret, address, automatic)
                is NearbyRoute.Relay ->
                    sendOver(checkNotNull(relay).connect(route.url, route.channels), pair, secret, address, automatic)
            }
        }
    }

    private suspend fun sendOver(
        channel: NearbyChannel,
        pair: String,
        secret: String,
        address: PeerAddress,
        automatic: Boolean,
    ) {
        connections.update { it + channel }
        try {
            check(foreground.value)
            send(channel, pair, secret, address, automatic)
        } finally {
            channel.close()
            connections.update { it - channel }
        }
    }

    private suspend fun send(
        connection: NearbyChannel,
        pair: String,
        secret: String,
        address: PeerAddress,
        automatic: Boolean,
    ) {
        val local = current()
        val nonce = NearbyCrypto.token()
        step("Connected. Checking the pairing…")
        connection.write(NearbyJson.encodeToString(NearbyHello(pair, nonce)).encodeToByteArray())
        val challenge = rejectedOnFailure { connection.read(HELLO_LIMIT).decodeToString() }
        if (challenge == PEER_BUSY) throw NearbyPeerBusyException()
        require(validToken(challenge))
        step("Sending your reading activity…")
        val context = "orbin-nearby1|$pair|$nonce|$challenge"
        val snapshot = progress.snapshot().also(::validateNearbyDocument)
        val request = NearbyRequest(local.device, local.name, boundPort, snapshot, automatic)
        connection.write(
            NearbyCrypto.encrypt(secret, "$context|request", NearbyJson.encodeToString(request).encodeToByteArray()),
        )
        val response =
            rejectedOnFailure {
                NearbyJson.decodeFromString<NearbyResponse>(
                    NearbyCrypto.decrypt(secret, "$context|response", connection.read()).decodeToString(),
                )
            }
        step("Merging ${response.name}'s activity…")
        validateDevice(response.device, response.name)
        require(response.device != local.device && validToken(response.secret))
        val existing = local.peers.firstOrNull { it.pair == pair }
        require(existing == null || existing.device == response.device)
        validateNearbyDocument(response.document)
        check(foreground.value)
        progress.merge(snapshot, response.document)
        rememberPeer(
            NearbyPeer(
                pair,
                response.device,
                response.name,
                response.secret,
                address.host ?: existing?.host,
                address.port,
                now(),
                relay = address.relay ?: existing?.relay,
            ),
        )
        saveBase(pair, response.document)
        connection.write(NearbyCrypto.encrypt(secret, "$context|ack", ACK.encodeToByteArray()))
        require(NearbyCrypto.decrypt(secret, "$context|done", connection.read(HELLO_LIMIT)).decodeToString() == ACK)
        mutableState.update { it.copy(message = "Synced with ${response.name}", messageIsError = false) }
    }

    private suspend fun receive(
        connection: NearbyChannel,
        host: String?,
    ) {
        val hello = NearbyJson.decodeFromString<NearbyHello>(connection.read(HELLO_LIMIT).decodeToString())
        require(validToken(hello.pair) && validToken(hello.nonce))
        // Reject unknown selectors before allocating a lock or generating a challenge.
        if (current().peers.none { it.pair == hello.pair } && pending.value?.id != hello.pair) return
        val mutex = lock(hello.pair)
        if (!mutex.tryLock()) {
            connection.write(PEER_BUSY.encodeToByteArray())
            return
        }
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
            if (request.automatic) require(known?.trusted == true && host != null && onLocalNetwork(host))
            validateNearbyDocument(request.document)
            val merged = progress.merge(readBase(hello.pair), request.document)
            val permanent = known?.secret ?: NearbyCrypto.token()
            // Persist and rotate before replying. If delivery fails, the user can re-pair; the
            // consumed invitation can never enroll another device or replace the established key.
            val peerHost = host ?: known?.host
            val peerRelay = known?.relay ?: local.relay
            rememberPeer(
                NearbyPeer(
                    hello.pair,
                    request.device,
                    request.name,
                    permanent,
                    peerHost,
                    request.port,
                    relay = peerRelay,
                ),
            )
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
                NearbyPeer(
                    hello.pair,
                    request.device,
                    request.name,
                    permanent,
                    peerHost,
                    request.port,
                    now(),
                    relay = peerRelay,
                ),
            )
            connection.write(NearbyCrypto.encrypt(secret, "$context|done", ACK.encodeToByteArray()))
            mutableState.update { it.copy(message = "Synced with ${request.name}", messageIsError = false) }
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
                    (it.host == null || localAddress(it.host)) &&
                    (it.relay == null || normalizeRelayUrl(it.relay) == it.relay) &&
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
                bluetoothEnabled = value.bluetooth,
                bluetoothAvailable = bluetooth != null,
                bluetoothReady = bluetooth?.ready() == true,
                relayUrl = value.relay,
                relayAvailable = relay != null,
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
        // Nested calls (Sync now runs one per device) keep the flag on until the outermost ends.
        val depth = activeCalls.updateAndGet { it + 1 }
        mutableState.update { it.copy(busy = true, messageIsError = if (depth == 1) false else it.messageIsError) }
        try {
            block()
        } catch (timeout: TimeoutCancellationException) {
            fail(failureMessage(timeout))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            fail(failureMessage(failure))
        } finally {
            val remaining = activeCalls.updateAndGet { it - 1 }
            if (remaining == 0) mutableState.update { it.copy(busy = false, step = null) }
        }
    }

    /**
     * Reach out once to another address on this network so iOS shows its Local Network prompt
     * now, while the user is setting up sync, instead of silently refusing the other device's
     * connection. Nothing needs to answer; the attempt alone triggers the prompt.
     */
    private suspend fun promptForLocalNetwork() {
        if (localNetworkPrompted) return
        localNetworkPrompted = true
        val own = networks().firstOrNull()?.address ?: return
        val probe = own.substringBeforeLast('.') + if (own.endsWith(".1")) ".2" else ".1"
        val sockets = NearbySockets()
        try {
            withTimeoutOrNull(LOCAL_NETWORK_PROBE_MS) { sockets.connect(probe, port).close() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") _: Exception,
        ) {
            // A refused or unreachable probe is expected; only the prompt matters.
        } finally {
            sockets.close()
        }
    }

    /** The peer is reachable but dropped or refused the session part-way through. */
    private suspend fun <T> rejectedOnFailure(block: suspend () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            throw NearbyRejectedException(failure)
        }

    private fun fail(message: String) = mutableState.update { it.copy(message = message, messageIsError = true) }

    private fun step(text: String) = mutableState.update { it.copy(step = text) }

    /** Says which route failed and what to check, instead of one message for every failure. */
    private fun failureMessage(failure: Throwable): String =
        when (failure) {
            is NearbyRouteFailure ->
                if (failure.attempts.isEmpty()) {
                    NO_ROUTE
                } else {
                    failure.attempts.joinToString(" ") { (route, cause) -> routeFailure(route, cause) }
                }
            is TimeoutCancellationException -> TIMEOUT_MESSAGE
            is NearbyUserException -> failure.message.orEmpty()
            else -> failure.message?.takeIf { it in USER_MESSAGES } ?: GENERIC_FAILURE
        }

    private fun routeFailure(
        route: NearbyRoute,
        cause: Throwable,
    ): String {
        val timedOut = cause is TimeoutCancellationException
        return when (route) {
            is NearbyRoute.Network ->
                if (cause is NearbyPeerBusyException) {
                    "The other device was busy syncing; try again in a moment."
                } else if (timedOut || cause is NearbyRejectedException) {
                    "Wi-Fi: reached ${route.host} but the other device didn't finish. Keep Orbin open there, " +
                        "or create a new code if it was removed."
                } else {
                    "Wi-Fi: couldn't connect to ${route.host}. Check both devices are on the same Wi-Fi " +
                        "(not guest or mobile data). On iPhone, allow Orbin in Settings → Privacy & Security → " +
                        "Local Network."
                }
            is NearbyRoute.Bluetooth ->
                "Bluetooth: no answer nearby. Turn on Bluetooth and \"Also use Bluetooth\" on both devices."
            is NearbyRoute.Relay -> "Relay: couldn't sync through ${route.url.substringAfter("://")}."
        }
    }

    private class NearbyPeerBusyException : Exception()

    /** The peer answered but refused or dropped the session (wrong key, removed pairing, closed app). */
    private class NearbyRejectedException(
        cause: Throwable,
    ) : Exception(cause)

    /** A failure whose message is already written for the user. */
    private class NearbyUserException(
        message: String,
    ) : Exception(message)

    private class NearbyRouteFailure(
        val attempts: List<Pair<NearbyRoute, Throwable>>,
    ) : Exception()

    private sealed interface NearbyRoute {
        data class Network(
            val host: String,
            val port: Int,
        ) : NearbyRoute

        class Bluetooth(
            val beacons: List<ByteArray>,
        ) : NearbyRoute

        data class Relay(
            val url: String,
            val channels: List<String>,
        ) : NearbyRoute
    }

    /** What to remember about a peer after a transfer, whichever route carried it. */
    private data class PeerAddress(
        val host: String?,
        val port: Int,
        val relay: String?,
    )

    companion object {
        private const val PEER_BUSY = "orbin-nearby-busy"
        private const val LOCAL_NETWORK_PROBE_MS = 1_500L
        private const val TIMEOUT_MESSAGE =
            "Couldn't reach the other device in time. Keep Orbin open and unlocked on both devices and try again."
        private const val GENERIC_FAILURE =
            "Couldn't sync. Keep Orbin open on both devices, on the same Wi-Fi, within Bluetooth range or on the " +
                "same relay, then try again."
        private val USER_MESSAGES =
            setOf(
                "Open and unlock Orbin before pairing",
                "Open and unlock Orbin before syncing",
                "Remove a device before adding another",
                "Too many paired devices",
                "Turn on Bluetooth and allow Bluetooth access",
                "Use an https:// relay address",
                "Connect to Wi-Fi, turn on Bluetooth sync or set a relay before pairing",
            )
        private const val MAX_CONNECTIONS = 3
        private const val MAX_DEVICE_NAME_LENGTH = 64
        private const val TRANSFER_TIMEOUT_MS = 30_000L
        private const val START_TIMEOUT_MS = 5_000L
        private const val AUTO_SYNC_MS = 30_000L
        private const val MANUAL_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 250L
        private const val HELLO_LIMIT = 2048
        private const val ACK = "orbin-nearby-accepted"
        private const val BEACON_REFRESH_MS = 1_000L
        private const val NO_ROUTE =
            "No way to reach the other device. Connect to Wi-Fi, turn on Bluetooth sync or set a relay."
    }
}
