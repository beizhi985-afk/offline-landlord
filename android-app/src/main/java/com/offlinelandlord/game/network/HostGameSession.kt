package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.GameEngine
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import java.io.Closeable
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HostGameSession(
    hostName: String,
    roomName: String = "${hostName.ifBlank { "房主" }}的房间",
) : Closeable {
    val roomCode: String = Random.nextInt(100000, 1000000).toString()
    private val engine = GameEngine(roomCode, roomName, hostName)
    private val mutex = Mutex()
    private val server = LanGameServer(
        roomCode = roomCode,
        onJoin = { name, token ->
            mutex.withLock {
                val outcome = engine.join(name, token)
                publish()
                outcome
            }
        },
        onAction = { playerId, action, revision ->
            mutex.withLock {
                val result = engine.applyAction(playerId, action, revision)
                if (result.success) publish()
                result
            }
        },
        onDisconnect = { playerId ->
            mutex.withLock {
                engine.disconnect(playerId)
                publish()
            }
        },
        viewFor = engine::viewFor,
    )
    private var advertiser: RoomAdvertiser? = null
    private val _viewState = MutableStateFlow(engine.viewFor(engine.hostPlayerId))

    val viewState: StateFlow<PlayerGameView?> = _viewState.asStateFlow()
    val hostPlayerId: String = engine.hostPlayerId
    val port: Int
        get() = server.port
    val hostAddress: String
        get() = LocalAddressFinder.bestIpv4Address()

    fun start() {
        server.start()
        advertiser = RoomAdvertiser(roomCode, engine.viewFor(hostPlayerId)?.roomName.orEmpty(), server.port).also {
            it.start()
        }
        publish()
    }

    suspend fun sendAction(action: PlayerAction): ActionResult = mutex.withLock {
        val result = engine.applyAction(hostPlayerId, action, _viewState.value?.revision)
        if (result.success) publish()
        result
    }

    private fun publish() {
        _viewState.value = engine.viewFor(hostPlayerId)
        server.broadcastStates()
    }

    override fun close() {
        advertiser?.close()
        server.close()
    }
}

