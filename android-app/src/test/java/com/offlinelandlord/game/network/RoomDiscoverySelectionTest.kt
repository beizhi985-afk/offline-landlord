package com.offlinelandlord.game.network

import com.offlinelandlord.game.network.landlord.v5.LandlordV5DiscoveryConfigCodec
import com.offlinelandlord.game.network.protocol.v5.V5DiscoveryCodec
import com.offlinelandlord.game.network.protocol.v5.V5RoomAdvertisement
import com.offlinelandlord.game.shared.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDiscoverySelectionTest {
    @Test
    fun v5ReplacesEquivalentV4RoomRegardlessOfArrivalOrder() {
        val v4 = room(protocol = LandlordProtocolVersion.V4)
        val v5 = room(protocol = LandlordProtocolVersion.V5)

        val firstOrder = linkedMapOf<String, DiscoveredRoom>()
        mergeDiscoveredRoom(firstOrder, v4)
        mergeDiscoveredRoom(firstOrder, v5)
        assertEquals(LandlordProtocolVersion.V5, firstOrder.values.single().protocol)

        val reverseOrder = linkedMapOf<String, DiscoveredRoom>()
        mergeDiscoveredRoom(reverseOrder, v5)
        mergeDiscoveredRoom(reverseOrder, v4)
        assertEquals(LandlordProtocolVersion.V5, reverseOrder.values.single().protocol)
    }

    @Test
    fun v4OnlyRoomRemainsDiscoverable() {
        val rooms = linkedMapOf<String, DiscoveredRoom>()
        mergeDiscoveredRoom(rooms, room(protocol = LandlordProtocolVersion.V4))

        assertEquals(1, rooms.size)
        assertEquals(LandlordProtocolVersion.V4, rooms.values.single().protocol)
    }

    @Test
    fun distinctHostPortOrRoomCodeAreNotDeduplicated() {
        val rooms = linkedMapOf<String, DiscoveredRoom>()
        mergeDiscoveredRoom(rooms, room(roomCode = "123456", port = 39173))
        mergeDiscoveredRoom(rooms, room(roomCode = "654321", port = 39173))
        mergeDiscoveredRoom(rooms, room(roomCode = "123456", port = 39174))

        assertEquals(3, rooms.size)
    }

    @Test
    fun landlordEntryFiltersUnoAndAcceptsLandlordV5Advertisement() {
        val uno = V5DiscoveryCodec.encodeResponse(
            V5RoomAdvertisement(
                gameType = GameType.UNO,
                roomCode = "123456",
                roomName = "UNO 占位",
                hostPort = 39173,
                playerCount = 1,
                maxPlayers = 4,
            ),
        )
        val landlord = V5DiscoveryCodec.encodeResponse(
            V5RoomAdvertisement(
                gameType = GameType.LANDLORD,
                roomCode = "123456",
                roomName = "斗地主房间",
                hostPort = 39173,
                playerCount = 1,
                maxPlayers = 3,
                gameConfig = LandlordV5DiscoveryConfigCodec.encode(12, true),
            ),
        )

        assertNull(decodeLandlordDiscoveredRoom("192.168.43.1", uno))
        val decoded = decodeLandlordDiscoveredRoom("192.168.43.1", landlord)
        assertEquals(LandlordProtocolVersion.V5, decoded?.protocol)
        assertEquals("斗地主房间", decoded?.roomName)
        assertTrue(decoded?.doublingEnabled == true)
    }

    private fun room(
        roomCode: String = "123456",
        port: Int = 39173,
        protocol: LandlordProtocolVersion = LandlordProtocolVersion.V5,
    ) = DiscoveredRoom(
        host = "192.168.43.1",
        port = port,
        roomCode = roomCode,
        roomName = "测试房间",
        totalRounds = 12,
        doublingEnabled = true,
        protocol = protocol,
    )
}
