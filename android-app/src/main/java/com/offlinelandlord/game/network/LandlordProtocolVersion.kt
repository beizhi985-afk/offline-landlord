package com.offlinelandlord.game.network

import com.offlinelandlord.game.network.protocol.v5.V5_PROTOCOL_VERSION

/** The Landlord compatibility adapter selected for one TCP connection. */
enum class LandlordProtocolVersion(val wireVersion: Int) {
    V4(WIRE_PROTOCOL_VERSION),
    V5(V5_PROTOCOL_VERSION),
}
