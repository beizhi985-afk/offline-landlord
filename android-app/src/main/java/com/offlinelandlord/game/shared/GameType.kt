package com.offlinelandlord.game.shared

import kotlinx.serialization.Serializable

/**
 * A game identifier shared by the application shell and game-agnostic protocols.
 *
 * The enum deliberately has no dependency on a specific game's rules, UI, or transport.
 */
@Serializable
enum class GameType {
    LANDLORD,
    UNO,
}
