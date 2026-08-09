package com.offlinelandlord.game.network.transport

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TcpClientTransport : Closeable {
    private val writeLock = Any()
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    suspend fun connect(
        endpoint: LanEndpoint,
        connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
        readTimeoutMillis: Int = 0,
    ) = withContext(Dispatchers.IO) {
        close()
        val newSocket = Socket()
        try {
            newSocket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMillis)
            newSocket.tcpNoDelay = true
            newSocket.keepAlive = true
            newSocket.soTimeout = readTimeoutMillis
            val newReader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))
            val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
            socket = newSocket
            reader = newReader
            writer = newWriter
        } catch (error: Throwable) {
            runCatching { newSocket.close() }
            throw error
        }
    }

    suspend fun receive(): String? = withContext(Dispatchers.IO) {
        reader?.readLine()
    }

    fun send(message: String) {
        synchronized(writeLock) {
            val activeWriter = writer ?: error("连接已经关闭")
            activeWriter.write(message)
            activeWriter.newLine()
            activeWriter.flush()
        }
    }

    fun setReadTimeoutMillis(timeoutMillis: Int) {
        socket?.soTimeout = timeoutMillis
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
        reader = null
        writer = null
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000
    }
}
