package com.offlinelandlord.game.network

import com.offlinelandlord.game.core.ActionResult
import com.offlinelandlord.game.core.BotBrain
import com.offlinelandlord.game.core.GameEngine
import com.offlinelandlord.game.core.PlayerAction
import com.offlinelandlord.game.core.PlayerGameView
import java.io.Closeable
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HostGameSession(
    hostName: String,
    roomName: String = "${hostName.ifBlank { "房主" }}的房间",
    private val totalRounds: Int = 12,
    private val doublingEnabled: Boolean = true,
) : Closeable {
    val roomCode: String = Random.nextInt(100000, 1000000).toString()
    private val engine = GameEngine(
        roomCode = roomCode,
        roomName = roomName,
        hostName = hostName,
        totalRounds = totalRounds,
        doublingEnabled = doublingEnabled,
    )
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val automationLock = Any()
    private val disconnectJobs = mutableMapOf<String, Job>()
    private var botJob: Job? = null
    private val server = LanGameServer(
        roomCode = roomCode,
        onJoin = { name, token ->
            val outcome = mutex.withLock {
                val outcome = engine.join(name, token)
                outcome.playerId?.let { disconnectJobs.remove(it)?.cancel() }
                publish()
                outcome
            }
            if (outcome.success) rescheduleAutomation()
            outcome
        },
        onAction = { playerId, action, revision ->
            val result = mutex.withLock {
                val result = engine.applyAction(playerId, action, revision)
                if (result.success) publish()
                result
            }
            if (result.success) rescheduleAutomation()
            result
        },
        onDisconnect = { playerId ->
            mutex.withLock {
                engine.disconnect(playerId)
                publish()
            }
            scheduleDisconnectedTakeover(playerId)
            rescheduleAutomation()
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
        advertiser = RoomAdvertiser(
            roomCode = roomCode,
            roomName = engine.viewFor(hostPlayerId)?.roomName.orEmpty(),
            tcpPort = server.port,
            totalRounds = totalRounds,
            doublingEnabled = doublingEnabled,
        ).also {
            it.start()
        }
        publish()
        rescheduleAutomation()
    }

    suspend fun sendAction(action: PlayerAction): ActionResult {
        val result = mutex.withLock {
            val result = engine.applyAction(hostPlayerId, action, _viewState.value?.revision)
            if (result.success) publish()
            result
        }
        if (result.success) rescheduleAutomation()
        return result
    }

    private fun publish() {
        _viewState.value = engine.viewFor(hostPlayerId)
        server.broadcastStates()
    }

    private fun scheduleDisconnectedTakeover(playerId: String) {
        disconnectJobs.remove(playerId)?.cancel()
        disconnectJobs[playerId] = scope.launch {
            delay(DISCONNECT_TAKEOVER_DELAY_MS)
            val result = mutex.withLock {
                val result = engine.enableAutoPlay(playerId)
                if (result.success) publish()
                result
            }
            disconnectJobs.remove(playerId)
            if (result.success) rescheduleAutomation()
        }
    }

    private fun rescheduleAutomation() {
        synchronized(automationLock) {
            botJob?.cancel()
            val playerId = engine.automatedPlayerId()
            if (playerId == null) {
                botJob = null
                return
            }
            botJob = scope.launch {
                val runningJob = coroutineContext[Job]
                delay(BOT_THINK_DELAY_MS)
                val advanced = mutex.withLock {
                    if (engine.automatedPlayerId() != playerId) return@withLock false
                    val action = engine.viewFor(playerId)?.let(BotBrain::chooseAction)
                        ?: return@withLock false
                    val result = engine.applyAction(playerId, action)
                    if (result.success) publish()
                    result.success
                }
                val shouldContinue = synchronized(automationLock) {
                    if (botJob === runningJob) {
                        botJob = null
                        advanced
                    } else {
                        false
                    }
                }
                if (shouldContinue) rescheduleAutomation()
            }
        }
    }

    override fun close() {
        advertiser?.close()
        server.close()
        disconnectJobs.values.forEach { it.cancel() }
        disconnectJobs.clear()
        botJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val BOT_THINK_DELAY_MS = 550L
        const val DISCONNECT_TAKEOVER_DELAY_MS = 8_000L
    }
}
