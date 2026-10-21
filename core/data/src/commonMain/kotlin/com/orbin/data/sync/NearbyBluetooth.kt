package com.orbin.data.sync

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.flow.Flow

/** Bytes of an advertised beacon tag. Short enough to fit a legacy 31-byte BLE advertisement. */
const val NEARBY_BEACON_BYTES = 6

/**
 * A short-range transport for the nearby protocol when the devices share no local network.
 *
 * Android and iOS implement it with Bluetooth LE: a connectable advertisement carries one 128-bit
 * service UUID ([NearbyBleAdvert]) holding a beacon tag and an L2CAP channel number, and the peer
 * connects to that channel. A service UUID is the only payload iOS can advertise, so both
 * platforms use it and pair with each other. The tags only let a device find a
 * peer; they carry no secret, and every frame on the channel is still authenticated and encrypted
 * with the pair key exactly as it is over Wi-Fi. Platforms without an implementation pass null.
 */
interface NearbyBluetooth {
    /** Hardware present, adapter switched on and runtime permissions granted. */
    fun ready(): Boolean

    /**
     * Ask for whatever the platform still needs (iOS shows its Bluetooth prompt the first time a
     * manager is created) and report whether Bluetooth is then [ready].
     */
    suspend fun prepare(): Boolean = ready()

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

/**
 * The advertised service UUID: `4f 52 42 4e 01 00` ("ORBN", version 1), the six-byte beacon, the
 * two-byte big-endian L2CAP PSM, then two zero bytes. Scanners match the first twelve bytes.
 */
@Suppress("MagicNumber") // The RFC 4122 text layout: 8-4-4-4-12 hex digits.
object NearbyBleAdvert {
    const val UUID_BYTES = 16
    const val MATCH_BYTES = 12
    private val PREFIX = byteArrayOf(0x4f, 0x52, 0x42, 0x4e, 0x01, 0x00)
    private const val PSM_OFFSET = 12
    private const val BYTE = 8
    private const val BYTE_MASK = 0xff

    fun encode(
        beacon: ByteArray,
        psm: Int,
    ): ByteArray {
        require(beacon.size == NEARBY_BEACON_BYTES && psm in 1..0xffff)
        return PREFIX + beacon + byteArrayOf((psm shr BYTE).toByte(), psm.toByte(), 0, 0)
    }

    /** The UUID bytes a scanner filters on for [beacon], with any PSM: compare the first [MATCH_BYTES]. */
    fun match(beacon: ByteArray): ByteArray = encode(beacon, 1).copyOf(MATCH_BYTES)

    /** The beacon and PSM in an advertised UUID, or null for anything that isn't Orbin's. */
    fun decode(uuid: ByteArray): Pair<ByteArray, Int>? {
        if (uuid.size != UUID_BYTES || !uuid.copyOf(PREFIX.size).contentEquals(PREFIX)) return null
        val psm =
            ((uuid[PSM_OFFSET].toInt() and BYTE_MASK) shl BYTE) or (uuid[PSM_OFFSET + 1].toInt() and BYTE_MASK)
        return uuid.copyOfRange(PREFIX.size, PREFIX.size + NEARBY_BEACON_BYTES) to psm
    }

    /** RFC 4122 text form, which both CoreBluetooth and Android's ParcelUuid parse. */
    fun text(uuid: ByteArray): String {
        val hex = uuid.joinToString("") { (it.toInt() and BYTE_MASK).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    fun parse(text: String): ByteArray? {
        val hex = text.replace("-", "")
        if (hex.length != UUID_BYTES * 2) return null
        return runCatching {
            ByteArray(
                UUID_BYTES,
            ) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }
}
