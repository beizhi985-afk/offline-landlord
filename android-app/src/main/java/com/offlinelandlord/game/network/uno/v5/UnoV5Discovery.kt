package com.offlinelandlord.game.network.uno.v5

import com.offlinelandlord.game.network.protocol.v5.V5DiscoveryCodec
import com.offlinelandlord.game.network.protocol.v5.V5RoomAdvertisement
import com.offlinelandlord.game.shared.GameType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Builds the existing generic V5 UDP advertisement for an UNO host. */
object UnoV5Discovery {
    fun advertisement(room: UnoV5RoomView, roomName: String, hostPort: Int): V5RoomAdvertisement =
        V5RoomAdvertisement(
            type = V5DiscoveryCodec.discoveryResponse,
            gameType = GameType.UNO,
            roomCode = room.roomCode,
            roomName = roomName,
            hostPort = hostPort,
            playerCount = room.players.size,
            maxPlayers = room.maxPlayers,
            gameConfig = JsonObject(
                mapOf(
                    "gameMode" to JsonPrimitive(room.gameMode.name),
                    "status" to JsonPrimitive(room.status.name),
                ),
            ),
        )
}
