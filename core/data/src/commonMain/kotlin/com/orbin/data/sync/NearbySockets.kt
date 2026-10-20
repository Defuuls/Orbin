package com.orbin.data.sync

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal class NearbySockets {
    val selector = SelectorManager(Dispatchers.IO)

    suspend fun listen(port: Int) = aSocket(selector).tcp().bind("0.0.0.0", port)

    suspend fun connect(
        host: String,
        port: Int,
    ): NearbyConnection {
        require(localAddress(host)) { "Nearby sync only connects to local addresses" }
        return NearbyConnection(aSocket(selector).tcp().connect(host, port))
    }

    fun close() = selector.close()
}

/**
 * One length-prefixed, bounded frame stream between two Orbin instances. The nearby protocol runs
 * unchanged over every transport (local-network TCP, Bluetooth LE L2CAP); each frame after the
 * hello is AES-GCM authenticated, so a transport only has to deliver bytes.
 */
interface NearbyChannel {
    suspend fun read(limit: Int = MAX_NEARBY_BYTES): ByteArray

    suspend fun write(bytes: ByteArray)

    fun close()
}

internal class NearbyConnection(
    private val socket: Socket,
) : NearbyChannel {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel(autoFlush = true)

    // Use the numeric peer address; hostname performs reverse DNS on the JVM.
    val host: String
        get() {
            val address = (socket.remoteAddress as InetSocketAddress).resolveAddress()
            require(address != null && address.size == 4)
            return address.joinToString(".") { (it.toInt() and 255).toString() }
        }

    override suspend fun read(limit: Int): ByteArray {
        val length = input.readInt()
        require(length in 1..limit) { "Invalid nearby frame size" }
        return ByteArray(length).also { input.readFully(it) }
    }

    override suspend fun write(bytes: ByteArray) {
        require(bytes.size in 1..MAX_NEARBY_BYTES) { "Too much activity for one nearby sync" }
        output.writeInt(bytes.size)
        output.writeFully(bytes)
        output.flush()
    }

    override fun close() = socket.close()
}
