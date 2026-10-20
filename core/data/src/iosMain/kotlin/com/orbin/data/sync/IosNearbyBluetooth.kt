package com.orbin.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBL2CAPChannel
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralManager
import platform.CoreBluetooth.CBPeripheralManagerDelegateProtocol
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSError
import platform.Foundation.NSInputStream
import platform.Foundation.NSNumber
import platform.Foundation.NSOutputStream
import platform.Foundation.NSRunLoop
import platform.darwin.NSObject

/**
 * Bluetooth LE for iOS, wire-compatible with Android's: the same service-UUID advertisement
 * ([NearbyBleAdvert]), an unencrypted L2CAP channel (the nearby protocol encrypts every frame), and
 * four-byte big-endian length-prefixed frames. CoreBluetooth calls back on the main queue, so every
 * call into it and every stream operation runs on [Dispatchers.Main].
 *
 * iOS fits only one 128-bit service UUID in a foreground advertisement, so while an invitation is
 * open only the invitation's beacon is advertised; the device beacon returns once it closes.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNearbyBluetooth : NearbyBluetooth {
    private val changes = MutableStateFlow(0)
    private val published = MutableStateFlow<Int?>(null)
    private val opened = Channel<CBL2CAPChannel>(Channel.UNLIMITED)
    private var discovery: ((CBPeripheral, Int) -> Unit)? = null
    private var discoveryBeacons: List<ByteArray> = emptyList()
    private val connections = mutableMapOf<CBPeripheral, CompletableDeferred<Unit>>()
    private val channels = mutableMapOf<CBPeripheral, CompletableDeferred<CBL2CAPChannel>>()

    private val peripheralDelegate = PeripheralManagerDelegate()
    private val centralDelegate = CentralDelegate()
    private val peripheralLinkDelegate = PeripheralLinkDelegate()
    private var peripheralManager: CBPeripheralManager? = null
    private var centralManager: CBCentralManager? = null

    override fun ready(): Boolean =
        CBManager.authorization == CBManagerAuthorizationAllowedAlways &&
            peripheralManager?.state == CBManagerStatePoweredOn &&
            centralManager?.state == CBManagerStatePoweredOn

    /** Creating the managers is what shows iOS's Bluetooth permission prompt the first time. */
    override suspend fun prepare(): Boolean =
        withContext(Dispatchers.Main) {
            if (peripheralManager == null) peripheralManager = CBPeripheralManager(peripheralDelegate, null)
            if (centralManager == null) centralManager = CBCentralManager(centralDelegate, null)
            withTimeoutOrNull(PROMPT_TIMEOUT_MS) {
                changes.first {
                    peripheralManager?.state != CBManagerStateUnknown &&
                        centralManager?.state != CBManagerStateUnknown
                }
            }
            ready()
        }

    override suspend fun serve(
        beacons: Flow<List<ByteArray>>,
        accept: suspend (NearbyChannel) -> Unit,
    ) = withContext(Dispatchers.Main) {
        check(prepare()) { "Bluetooth is off or not permitted" }
        val manager = checkNotNull(peripheralManager)
        published.value = null
        manager.publishL2CAPChannelWithEncryption(false)
        val psm = withTimeout(PUBLISH_TIMEOUT_MS) { published.first { it != null } }!!
        try {
            coroutineScope {
                launch {
                    beacons.collectLatest { list ->
                        val beacon = list.firstOrNull() ?: return@collectLatest
                        val uuid = CBUUID.UUIDWithString(NearbyBleAdvert.text(NearbyBleAdvert.encode(beacon, psm)))
                        manager.stopAdvertising()
                        manager.startAdvertising(mapOf<Any?, Any?>(CBAdvertisementDataServiceUUIDsKey to listOf(uuid)))
                    }
                }
                while (isActive) {
                    val channel = opened.receive()
                    launch { accept(L2capChannel(channel) { }) }
                }
            }
        } finally {
            manager.stopAdvertising()
            manager.unpublishL2CAPChannel(psm.toUShort())
        }
    }

    override suspend fun connect(beacons: List<ByteArray>): NearbyChannel =
        withContext(Dispatchers.Main) {
            check(prepare()) { "Bluetooth is off or not permitted" }
            val central = checkNotNull(centralManager)
            val found = CompletableDeferred<Pair<CBPeripheral, Int>>()
            discoveryBeacons = beacons
            discovery = { peripheral, psm -> found.complete(peripheral to psm) }
            central.scanForPeripheralsWithServices(null, null)
            val (peripheral, psm) =
                try {
                    withTimeout(SCAN_TIMEOUT_MS) { found.await() }
                } finally {
                    central.stopScan()
                    discovery = null
                }
            val connected = CompletableDeferred<Unit>().also { connections[peripheral] = it }
            central.connectPeripheral(peripheral, null)
            var linked: NearbyChannel? = null
            try {
                withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }
                val open = CompletableDeferred<CBL2CAPChannel>().also { channels[peripheral] = it }
                peripheral.delegate = peripheralLinkDelegate
                peripheral.openL2CAPChannel(psm.toUShort())
                val channel = withTimeout(CONNECT_TIMEOUT_MS) { open.await() }
                L2capChannel(channel) { central.cancelPeripheralConnection(peripheral) }.also { linked = it }
            } finally {
                connections.remove(peripheral)
                channels.remove(peripheral)
                if (linked == null) central.cancelPeripheralConnection(peripheral)
            }
        }

    private inner class PeripheralManagerDelegate :
        NSObject(),
        CBPeripheralManagerDelegateProtocol {
        override fun peripheralManagerDidUpdateState(peripheral: CBPeripheralManager) {
            changes.value++
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didPublishL2CAPChannel: UShort,
            error: NSError?,
        ) {
            if (error == null) published.value = didPublishL2CAPChannel.toInt()
        }

        override fun peripheralManager(
            peripheral: CBPeripheralManager,
            didOpenL2CAPChannel: CBL2CAPChannel?,
            error: NSError?,
        ) {
            if (error == null && didOpenL2CAPChannel != null) opened.trySend(didOpenL2CAPChannel)
        }
    }

    private inner class CentralDelegate :
        NSObject(),
        CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            changes.value++
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            val callback = discovery ?: return
            val uuids = advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<*> ?: return
            uuids
                .mapNotNull { (it as? CBUUID)?.UUIDString?.let(NearbyBleAdvert::parse)?.let(NearbyBleAdvert::decode) }
                .firstOrNull { (tag, _) -> discoveryBeacons.any { it.contentEquals(tag) } }
                ?.let { (_, psm) -> callback(didDiscoverPeripheral, psm) }
        }

        override fun centralManager(
            central: CBCentralManager,
            didConnectPeripheral: CBPeripheral,
        ) {
            connections[didConnectPeripheral]?.complete(Unit)
        }

        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            connections[didFailToConnectPeripheral]?.completeExceptionally(
                IllegalStateException(error?.localizedDescription ?: "Bluetooth connection failed"),
            )
        }
    }

    private inner class PeripheralLinkDelegate :
        NSObject(),
        CBPeripheralDelegateProtocol {
        override fun peripheral(
            peripheral: CBPeripheral,
            didOpenL2CAPChannel: CBL2CAPChannel?,
            error: NSError?,
        ) {
            val waiting = channels[peripheral] ?: return
            if (error == null && didOpenL2CAPChannel != null) {
                waiting.complete(didOpenL2CAPChannel)
            } else {
                waiting.completeExceptionally(
                    IllegalStateException(error?.localizedDescription ?: "Bluetooth channel failed"),
                )
            }
        }
    }

    /** Frames over the channel's streams, polled on the main run loop they are scheduled on. */
    private class L2capChannel(
        private val channel: CBL2CAPChannel,
        private val onClose: () -> Unit,
    ) : NearbyChannel {
        private val input: NSInputStream = checkNotNull(channel.inputStream)
        private val output: NSOutputStream = checkNotNull(channel.outputStream)

        init {
            input.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
            output.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
            input.open()
            output.open()
        }

        override suspend fun read(limit: Int): ByteArray =
            withContext(Dispatchers.Main) {
                val header = readFully(HEADER_BYTES)
                val length =
                    header.fold(0) { value, byte -> (value shl BYTE) or (byte.toInt() and BYTE_MASK) }
                require(length in 1..limit) { "Invalid nearby frame size" }
                readFully(length)
            }

        override suspend fun write(bytes: ByteArray) =
            withContext(Dispatchers.Main) {
                require(bytes.size in 1..MAX_NEARBY_BYTES) { "Too much activity for one nearby sync" }
                val size = bytes.size
                val header = ByteArray(HEADER_BYTES) { (size shr (BYTE * (HEADER_BYTES - 1 - it))).toByte() }
                writeFully(header + bytes)
            }

        private suspend fun readFully(count: Int): ByteArray {
            val buffer = ByteArray(count)
            var offset = 0
            while (offset < count) {
                if (input.hasBytesAvailable) {
                    val read =
                        buffer.usePinned {
                            input.read(it.addressOf(offset).reinterpret(), (count - offset).toULong())
                        }
                    check(read > 0) { "Bluetooth channel closed" }
                    offset += read.toInt()
                } else {
                    check(input.streamError == null) { "Bluetooth channel failed" }
                    delay(POLL_MS)
                }
            }
            return buffer
        }

        private suspend fun writeFully(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                if (output.hasSpaceAvailable) {
                    val written =
                        bytes.usePinned {
                            output.write(it.addressOf(offset).reinterpret(), (bytes.size - offset).toULong())
                        }
                    check(written > 0) { "Bluetooth channel closed" }
                    offset += written.toInt()
                } else {
                    check(output.streamError == null) { "Bluetooth channel failed" }
                    delay(POLL_MS)
                }
            }
        }

        override fun close() {
            input.close()
            output.close()
            input.removeFromRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
            output.removeFromRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
            onClose()
        }
    }

    private companion object {
        const val PROMPT_TIMEOUT_MS = 30_000L
        const val PUBLISH_TIMEOUT_MS = 5_000L
        const val SCAN_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val POLL_MS = 5L
        const val HEADER_BYTES = 4
        const val BYTE = 8
        const val BYTE_MASK = 0xff
    }
}
