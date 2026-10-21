package com.orbin.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bluetooth LE transport: a connectable advertisement whose service data is a beacon tag followed
 * by a two-byte L2CAP PSM, and an insecure (unbonded) L2CAP connection-oriented channel to that
 * PSM. Bonding is unnecessary because the nearby protocol authenticates and encrypts every frame
 * with the pair key; skipping it avoids a second system pairing dialog on top of the QR code.
 *
 * Needs BLUETOOTH_SCAN (declared neverForLocation), BLUETOOTH_ADVERTISE and BLUETOOTH_CONNECT,
 * which the settings screen requests before enabling Bluetooth sync.
 */
class AndroidNearbyBluetooth(
    context: Context,
) : NearbyBluetooth {
    private val context = context.applicationContext

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun ready(): Boolean {
        val adapter = adapter ?: return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) &&
            PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } &&
            adapter.isEnabled
    }

    @SuppressLint("MissingPermission") // ready() checks every runtime permission first.
    override suspend fun serve(
        beacons: Flow<List<ByteArray>>,
        accept: suspend (NearbyChannel) -> Unit,
    ) {
        check(ready()) { "Bluetooth is off or not permitted" }
        val adapter = checkNotNull(adapter)
        val server = withContext(Dispatchers.IO) { adapter.listenUsingInsecureL2capChannel() }
        try {
            coroutineScope {
                // accept() blocks and ignores interruption; closing the socket is what ends it.
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        server.close()
                    }
                }
                launch { beacons.collectLatest { advertise(adapter, it, server.psm) } }
                while (isActive) {
                    val socket =
                        try {
                            withContext(Dispatchers.IO) { server.accept() }
                        } catch (closed: IOException) {
                            if (!isActive) break
                            throw closed
                        }
                    launch { accept(BluetoothChannel(socket)) }
                }
            }
        } finally {
            server.close()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun advertise(
        adapter: BluetoothAdapter,
        beacons: List<ByteArray>,
        psm: Int,
    ) {
        val advertiser = adapter.bluetoothLeAdvertiser ?: error("This device can't advertise over Bluetooth LE")
        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()
        val callbacks =
            beacons.map { beacon ->
                val data =
                    AdvertiseData
                        .Builder()
                        .setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false)
                        .addServiceUuid(parcelUuid(NearbyBleAdvert.encode(beacon, psm)))
                        .build()
                object : AdvertiseCallback() {}.also { advertiser.startAdvertising(settings, data, it) }
            }
        try {
            awaitCancellation()
        } finally {
            callbacks.forEach { runCatching { advertiser.stopAdvertising(it) } }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(beacons: List<ByteArray>): NearbyChannel {
        check(ready()) { "Bluetooth is off or not permitted" }
        val adapter = checkNotNull(adapter)
        val (device, psm) = withTimeout(SCAN_TIMEOUT_MS) { scan(adapter, beacons) }
        return withContext(Dispatchers.IO) {
            val socket = device.createInsecureL2capChannel(psm)
            try {
                socket.connect()
            } catch (failure: IOException) {
                socket.close()
                throw failure
            }
            BluetoothChannel(socket)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scan(
        adapter: BluetoothAdapter,
        beacons: List<ByteArray>,
    ): Pair<BluetoothDevice, Int> {
        val scanner = adapter.bluetoothLeScanner ?: error("Bluetooth LE scanning is unavailable")
        val mask = ByteArray(NearbyBleAdvert.UUID_BYTES) { if (it < NearbyBleAdvert.MATCH_BYTES) FULL_MASK else 0 }
        val filters =
            beacons.map { beacon ->
                val match = NearbyBleAdvert.match(beacon).copyOf(NearbyBleAdvert.UUID_BYTES)
                ScanFilter
                    .Builder()
                    .setServiceUuid(parcelUuid(match), parcelUuid(mask))
                    .build()
            }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return suspendCancellableCoroutine { continuation ->
            val callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        val (_, psm) =
                            result.scanRecord
                                ?.serviceUuids
                                .orEmpty()
                                .mapNotNull { NearbyBleAdvert.parse(it.uuid.toString())?.let(NearbyBleAdvert::decode) }
                                .firstOrNull { (tag, _) -> beacons.any { it.contentEquals(tag) } }
                                ?: return
                        runCatching { scanner.stopScan(this) }
                        if (continuation.isActive) continuation.resume(result.device to psm)
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("Bluetooth scan failed ($errorCode)"))
                        }
                    }
                }
            scanner.startScan(filters, settings, callback)
            continuation.invokeOnCancellation { runCatching { scanner.stopScan(callback) } }
        }
    }

    /** Length-prefixed frames over the L2CAP socket, bounded the same way as the TCP transport. */
    private class BluetoothChannel(
        private val socket: BluetoothSocket,
    ) : NearbyChannel {
        private val input = DataInputStream(socket.inputStream.buffered())
        private val output = DataOutputStream(socket.outputStream.buffered())

        override suspend fun read(limit: Int): ByteArray =
            withContext(Dispatchers.IO) {
                val length = input.readInt()
                require(length in 1..limit) { "Invalid nearby frame size" }
                ByteArray(length).also { input.readFully(it) }
            }

        override suspend fun write(bytes: ByteArray) =
            withContext(Dispatchers.IO) {
                require(bytes.size in 1..MAX_NEARBY_BYTES) { "Too much activity for one nearby sync" }
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }

        override fun close() {
            try {
                socket.close()
            } catch (_: IOException) {
                // Already closed by the peer.
            }
        }
    }

    private fun parcelUuid(bytes: ByteArray) = ParcelUuid(UUID.fromString(NearbyBleAdvert.text(bytes)))

    companion object {
        /** Runtime permissions Bluetooth sync needs; the settings screen requests exactly these. */
        val PERMISSIONS =
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )

        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val FULL_MASK = 0xff.toByte()
    }
}
