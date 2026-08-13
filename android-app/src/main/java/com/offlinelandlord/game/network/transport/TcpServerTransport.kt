package com.offlinelandlord.game.network.transport

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TcpServerTransport(
    private val onMessage: suspend (connectionId: String, message: String) -> Unit,
    private val onDisconnect: suspend (connectionId: String) -> Unit,
    private val initialReadTimeoutMillis: Int = DEFAULT_INITIAL_READ_TIMEOUT_MILLIS,
) : Closeable {
    private class Connection(
        val socket: Socket,
        val reader: BufferedReader,
        val writer: BufferedWriter,
    ) : Closeable {
        private val writeLock = Any()

        fun send(message: String) {
            synchronized(writeLock) {
                writer.write(message)
                writer.newLine()
                writer.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, Connection>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start(preferredPort: Int) {
        if (serverSocket != null) return
        val socket = try {
            ServerSocket(preferredPort)
        } catch (_: BindException) {
            ServerSocket(0)
        }
        socket.reuseAddress = true
        serverSocket = socket
        acceptJob = scope.launch {
            while (isActive) {
                try {
                    val clientSocket = socket.accept().apply {
                        tcpNoDelay = true
                        keepAlive = true
                        soTimeout = initialReadTimeoutMillis
                    }
                    val connectionId = UUID.randomUUID().toString()
                    launch { readConnection(connectionId, clientSocket) }
                } catch (_: SocketException) {
                    break
                }
            }
        }
    }

    fun send(connectionId: String, message: String) {
        val connection = connections[connectionId] ?: error("连接已经关闭")
        connection.send(message)
    }

    fun broadcast(message: String) {
        connections.forEach { (connectionId, connection) ->
            runCatching { connection.send(message) }
                .onFailure { disconnect(connectionId) }
        }
    }

    fun setReadTimeoutMillis(connectionId: String, timeoutMillis: Int) {
        connections[connectionId]?.socket?.soTimeout = timeoutMillis
    }

    fun disconnect(connectionId: String) {
        connections[connectionId]?.close()
    }

    private suspend fun readConnection(connectionId: String, socket: Socket) {
        val connection = Connection(
            socket = socket,
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)),
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)),
        )
        connections[connectionId] = connection
        try {
            while (scope.isActive) {
                val message = connection.reader.readLine() ?: break
                onMessage(connectionId, message)
            }
        } catch (_: Exception) {
            // Closing or losing a socket is reported through the disconnect callback.
        } finally {
            connection.close()
            if (connections.remove(connectionId, connection)) {
                withContext(NonCancellable) { onDisconnect(connectionId) }
            }
        }
    }

    override fun close() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        connections.values.forEach { it.close() }
        connections.clear()
        acceptJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_INITIAL_READ_TIMEOUT_MILLIS = 10_000
    }
}
