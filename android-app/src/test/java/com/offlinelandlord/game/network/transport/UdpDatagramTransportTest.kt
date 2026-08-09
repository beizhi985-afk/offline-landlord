package com.offlinelandlord.game.network.transport

import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class UdpDatagramTransportTest {
    @Test(timeout = 10_000)
    fun datagramPayloadTravelsAcrossLoopbackUnchanged() = runBlocking {
        val receiver = UdpDatagramTransport.bind(0)
        val sender = UdpDatagramTransport.open()
        try {
            val pending = async(Dispatchers.IO) { receiver.receive(128) }
            sender.send(
                bytes = "plain udp payload".encodeToByteArray(),
                address = InetAddress.getLoopbackAddress(),
                port = receiver.localPort,
            )

            val received = withTimeout(3_000) { pending.await() }
            assertEquals("plain udp payload", received.bytes.decodeToString())
        } finally {
            sender.close()
            receiver.close()
        }
    }
}
