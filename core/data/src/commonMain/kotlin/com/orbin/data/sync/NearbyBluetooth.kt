package com.orbin.data.sync

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.flow.Flow

/** Bytes of an advertised beacon tag. Short enough to fit a legacy 31-byte BLE advertisement. */
const val NEARBY_BEACON_BYTES = 6

/**
 * A short-range transport for the nearby protocol when the devices share no local network.
 *
 * Android implements it with Bluetooth LE: a connectable advertisement carries a beacon tag and an
 * L2CAP channel number, and the peer connects to that channel. The tags only let a device find a
 * peer; they carry no secret, and every frame on the channel is still authenticated and encrypted
 * with the pair key exactly as it is over Wi-Fi. Platforms without an implementation pass null.
 */
interface NearbyBluetooth {
    /** Hardware present, adapter switched on and runtime permissions granted. */
    fun ready(): Boolean

    /**
     * Advertise the latest [beacons] and hand every accepted channel to [accept] until cancelled.
     * [accept] is called concurrently and owns (and closes) the channel it gets.
     */
    suspend fun serve(
        beacons: Flow<List<ByteArray>>,
        accept: suspend (NearbyChannel) -> Unit,
    )

    /** Scan for a device advertising any of [beacons] and open a channel to the first match. */
    suspend fun connect(beacons: List<ByteArray>): NearbyChannel
}

/**
 * Beacon tags. An invitation's tag comes from its random selector, which only the QR/pairing code
 * holder knows. A paired device's tag comes from its private device id and the current ten-minute
 * window, so it rotates and an onlooker cannot follow one device across windows.
 */
internal object NearbyBeacons {
    const val WINDOW_MS = 10 * 60 * 1000L

    suspend fun invite(selector: String): ByteArray = tag("orbin-ble1|invite|$selector")

    suspend fun device(
        device: String,
        nowMillis: Long,
    ): List<ByteArray> {
        val window = nowMillis / WINDOW_MS
        // The previous window too, so two clocks a few minutes apart still find each other.
        return listOf(tag("orbin-ble1|device|$device|$window"), tag("orbin-ble1|device|$device|${window - 1}"))
    }

    private suspend fun tag(input: String): ByteArray =
        CryptographyProvider.Default
            .get(SHA256)
            .hasher()
            .hash(input.encodeToByteArray())
            .copyOf(NEARBY_BEACON_BYTES)
}
