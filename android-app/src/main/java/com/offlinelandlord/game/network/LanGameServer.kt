package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.JoinOutcome
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LanGameServer(
    private val roomCode: String,
    private val onJoin: suspend (name: String, resumeToken: String?) -> JoinOutcome,
    private val onAction: suspend (playerId: String, action: PlayerAction, revision: Long?) -> ActionResult,
    private val onDisconnect: suspend (playerId: String) -> Unit,
    private val viewFor: (playerId: String) -> PlayerGameView?,
) : Closeable {
    private class ClientConnection(
        val socket: Socket,
        val reader: BufferedReader,
        val writer: BufferedWriter,
    ) : Closeable {
        private val writeLock = Any()

        fun send(envelope: WireEnvelope) {
            val line = wireJson.encodeToString(WireEnvelope.serializer(), envelope)
            synchronized(writeLock) {
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = ConcurrentHashMap<String, ClientConnection>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun start(preferredPort: Int = DEFAULT_TCP_PORT) {
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
                    }
                    launch { handleClient(clientSocket) }
                } catch (_: SocketException) {
                    break
                }
            }
        }
    }

    fun broadcastStates() {
        clients.forEach { (playerId, connection) ->
            val view = viewFor(playerId) ?: return@forEach
            runCatching { connection.send(WireEnvelope(WireType.STATE, view = view)) }
                .onFailure { connection.close() }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        val connection = ClientConnection(
            socket = socket,
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)),
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)),
        )
        var playerId: String? = null
        try {
            socket.soTimeout = 10_000
            val firstLine = connection.reader.readLine() ?: return
            val joinEnvelope = wireJson.decodeFromString(WireEnvelope.serializer(), firstLine)
            if (joinEnvelope.type != WireType.JOIN || joinEnvelope.roomCode != roomCode) {
                connection.send(WireEnvelope(WireType.ERROR, message = "房间码不正确"))
                return
            }
            if (joinEnvelope.protocolVersion != WIRE_PROTOCOL_VERSION) {
                connection.send(WireEnvelope(WireType.ERROR, message = "房间版本不兼容，请安装离线斗地主 V3.2"))
                return
            }

            val outcome = onJoin(joinEnvelope.playerName.orEmpty(), joinEnvelope.resumeToken)
            if (!outcome.success || outcome.playerId == null || outcome.resumeToken == null) {
                connection.send(WireEnvelope(WireType.ERROR, message = outcome.message))
                return
            }

            playerId = outcome.playerId
            val oldConnection = clients.put(playerId, connection)
            if (oldConnection != null && oldConnection !== connection) oldConnection.close()
            socket.soTimeout = 0
            connection.send(
                WireEnvelope(
                    type = WireType.JOIN_ACCEPTED,
                    protocolVersion = WIRE_PROTOCOL_VERSION,
                    requestId = joinEnvelope.requestId,
                    playerId = playerId,
                    resumeToken = outcome.resumeToken,
                    roomCode = roomCode,
                    view = viewFor(playerId),
                    message = outcome.message,
                ),
            )
            broadcastStates()

            while (scope.isActive) {
                val line = connection.reader.readLine() ?: break
                val envelope = try {
                    wireJson.decodeFromString(WireEnvelope.serializer(), line)
                } catch (_: Exception) {
                    connection.send(WireEnvelope(WireType.ERROR, message = "无法解析操作"))
                    continue
                }
                when (envelope.type) {
                    WireType.ACTION -> {
                        val action = envelope.action
                        if (action == null) {
                            connection.send(WireEnvelope(WireType.ERROR, requestId = envelope.requestId, message = "缺少操作内容"))
                            continue
                        }
                        val actionResult = onAction(playerId, action, envelope.expectedRevision)
                        if (!actionResult.success) {
                            connection.send(
                                WireEnvelope(
                                    type = WireType.ERROR,
                                    requestId = envelope.requestId,
                                    message = actionResult.message,
                                    view = viewFor(playerId),
                                ),
                            )
                        }
                    }
                    WireType.PING -> connection.send(WireEnvelope(WireType.PONG, requestId = envelope.requestId))
                    else -> Unit
                }
            }
        } catch (_: Exception) {
            // The session owner updates the visible connection state below.
        } finally {
            connection.close()
            val id = playerId
            if (id != null && clients.remove(id, connection)) onDisconnect(id)
        }
    }

    override fun close() {
        runCatching { serverSocket?.close() }
        clients.values.forEach { it.close() }
        clients.clear()
        acceptJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val DEFAULT_TCP_PORT = 39173
    }
}
