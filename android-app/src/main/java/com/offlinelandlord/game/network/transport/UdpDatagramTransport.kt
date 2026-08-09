package com.offlinelandlord.game.network.transport

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections

data class UdpDatagram(
    val bytes: ByteArray,
    val address: InetAddress,
    val port: Int,
)

class UdpDatagramTransport private constructor(
    private val socket: DatagramSocket,
) : Closeable {
    val localPort: Int
        get() = socket.localPort

    fun send(bytes: ByteArray, address: InetAddress, port: Int) {
        socket.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    fun receive(maxPacketSize: Int): UdpDatagram {
        val buffer = ByteArray(maxPacketSize)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        return UdpDatagram(
            bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length),
            address = packet.address,
            port = packet.port,
        )
    }

    override fun close() {
        socket.close()
    }

    companion object {
        fun bind(port: Int, reuseAddress: Boolean = true): UdpDatagramTransport {
            val socket = DatagramSocket(null).apply {
                this.reuseAddress = reuseAddress
                bind(InetSocketAddress(port))
            }
            return UdpDatagramTransport(socket)
        }

        fun open(
            broadcast: Boolean = false,
            receiveTimeoutMillis: Int = 0,
        ): UdpDatagramTransport {
            val socket = DatagramSocket().apply {
                this.broadcast = broadcast
                soTimeout = receiveTimeoutMillis
            }
            return UdpDatagramTransport(socket)
        }
    }
}

object LanBroadcastAddresses {
    fun all(): Set<InetAddress> {
        val addresses = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(addresses) { it.broadcast }
        }
        return addresses
    }
}
