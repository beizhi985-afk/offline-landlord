package com.offlinelandlord.game.network.transport

import java.net.ServerSocket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpTransportTest {
    @Test(timeout = 10_000)
    fun clientMessageReachesServerUnchanged() = runBlocking {
        val received = Channel<Pair<String, String>>(Channel.UNLIMITED)
        val server = TcpServerTransport(
            onMessage = { connectionId, message -> received.send(connectionId to message) },
            onDisconnect = {},
        )
        val client = TcpClientTransport()
        try {
            server.start(0)
            client.connect(LanEndpoint("127.0.0.1", server.port))
            client.send("plain transport message")

            assertEquals("plain transport message", withTimeout(3_000) { received.receive() }.second)
        } finally {
            client.close()
            server.close()
        }
    }

    @Test(timeout = 10_000)
    fun serverMessageReachesClientUnchanged() = runBlocking {
        lateinit var server: TcpServerTransport
        server = TcpServerTransport(
            onMessage = { connectionId, message ->
                if (message == "request") server.send(connectionId, "plain response")
            },
            onDisconnect = {},
        )
        val client = TcpClientTransport()
        try {
            server.start(0)
            client.connect(LanEndpoint("127.0.0.1", server.port))
            client.send("request")

            assertEquals("plain response", withTimeout(3_000) { client.receive() })
        } finally {
            client.close()
            server.close()
        }
    }

    @Test(timeout = 10_000)
    fun clientCloseProducesServerDisconnectEvent() = runBlocking {
        val received = Channel<String>(Channel.UNLIMITED)
        val disconnected = Channel<String>(Channel.UNLIMITED)
        val server = TcpServerTransport(
            onMessage = { connectionId, _ -> received.send(connectionId) },
            onDisconnect = { connectionId -> disconnected.send(connectionId) },
        )
        val client = TcpClientTransport()
        try {
            server.start(0)
            client.connect(LanEndpoint("127.0.0.1", server.port))
            client.send("identify")
            val connectionId = withTimeout(3_000) { received.receive() }

            client.close()

            assertEquals(connectionId, withTimeout(3_000) { disconnected.receive() })
        } finally {
            client.close()
            server.close()
        }
    }

    @Test(timeout = 10_000)
    fun serverCloseReleasesListeningPort() {
        val server = TcpServerTransport(onMessage = { _, _ -> }, onDisconnect = {})
        server.start(0)
        val usedPort = server.port

        server.close()

        ServerSocket(usedPort).use { rebound ->
            assertEquals(usedPort, rebound.localPort)
        }
    }

    @Test(timeout = 10_000)
    fun twoClientsReceiveDifferentConnectionIdentifiers() = runBlocking {
        val received = Channel<Pair<String, String>>(Channel.UNLIMITED)
        val server = TcpServerTransport(
            onMessage = { connectionId, message -> received.send(connectionId to message) },
            onDisconnect = {},
        )
        val first = TcpClientTransport()
        val second = TcpClientTransport()
        try {
            server.start(0)
            first.connect(LanEndpoint("127.0.0.1", server.port))
            second.connect(LanEndpoint("127.0.0.1", server.port))
            first.send("first")
            second.send("second")

            val messages = listOf(
                withTimeout(3_000) { received.receive() },
                withTimeout(3_000) { received.receive() },
            )
            assertEquals(setOf("first", "second"), messages.map { it.second }.toSet())
            assertNotEquals(messages[0].first, messages[1].first)
            assertTrue(messages.all { it.first.isNotBlank() })
        } finally {
            first.close()
            second.close()
            server.close()
        }
    }
}
