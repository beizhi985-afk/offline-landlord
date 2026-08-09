package com.offlinelandlord.game.uno.core

data class UnoPlayer(
    val playerId: String,
    val playerName: String,
)

data class UnoPlayerState(
    val playerId: String,
    val playerName: String,
    val seat: Int,
    val hand: List<UnoCard>,
    val score: Int,
)

data class UnoPublicPlayerState(
    val playerId: String,
    val playerName: String,
    val seat: Int,
    val remainingCardCount: Int,
    val score: Int,
)

data class UnoGameView(
    val selfPlayerId: String,
    val ownHand: List<UnoCard>,
    val players: List<UnoPublicPlayerState>,
    val currentPlayerId: String?,
    val direction: UnoDirection,
    val topDiscardCard: UnoCard,
    val activeColor: UnoColor?,
    val phase: UnoPhase,
    val roundNumber: Int,
    val scores: Map<String, Int>,
    val unoDeclaredPlayerId: String?,
    val catchTargetPlayerId: String?,
    val drawnCardId: String?,
    val colorChooserPlayerId: String?,
    val roundWinnerId: String?,
    val matchWinnerId: String?,
    val matchMode: UnoMatchMode,
    val targetScore: Int,
)

enum class UnoDirection {
    CLOCKWISE,
    COUNTER_CLOCKWISE,
}

enum class UnoPhase {
    TURN,
    AFTER_DRAW,
    CHOOSE_COLOR,
    ROUND_FINISHED,
    MATCH_FINISHED,
}

enum class UnoMatchMode {
    QUICK,
    POINTS,
}

data class UnoCatchWindow(
    val targetPlayerId: String,
)

data class UnoGameState(
    val players: List<UnoPlayerState>,
    val currentPlayerId: String?,
    val direction: UnoDirection,
    val drawPile: List<UnoCard>,
    val discardPile: List<UnoCard>,
    val activeColor: UnoColor?,
    val phase: UnoPhase,
    val roundNumber: Int,
    val baseStartingSeat: Int,
    val scores: Map<String, Int>,
    val unoDeclaredPlayerId: String?,
    val catchWindow: UnoCatchWindow?,
    val drawnCardId: String?,
    val colorChooserPlayerId: String?,
    val colorChoiceStartsTurn: Boolean,
    val pendingRoundWinnerId: String?,
    val roundWinnerId: String?,
    val matchWinnerId: String?,
    val matchMode: UnoMatchMode,
    val targetScore: Int,
    val lastRoundScore: Int,
)

sealed interface UnoAction {
    data class PlayCard(val cardId: String) : UnoAction
    data object DrawCard : UnoAction
    data class PlayDrawnCard(val cardId: String) : UnoAction
    data object PassAfterDraw : UnoAction
    data object DeclareUno : UnoAction
    data class CatchUno(val targetPlayerId: String) : UnoAction
    data class ChooseColor(val color: UnoColor) : UnoAction
    data object StartNextRound : UnoAction
}

enum class UnoActionType {
    PLAY_CARD,
    DRAW_CARD,
    PLAY_DRAWN_CARD,
    PASS_AFTER_DRAW,
    DECLARE_UNO,
    CATCH_UNO,
    CHOOSE_COLOR,
    START_NEXT_ROUND,
}

enum class UnoErrorCode {
    INVALID_PLAYER_COUNT,
    DUPLICATE_PLAYER_ID,
    INVALID_TARGET_SCORE,
    INVALID_PLAYER,
    NOT_YOUR_TURN,
    WRONG_PHASE,
    CARD_NOT_IN_HAND,
    CARD_NOT_PLAYABLE,
    ILLEGAL_WILD_DRAW_FOUR,
    DRAW_PILE_EMPTY,
    ONLY_DRAWN_CARD_CAN_BE_PLAYED,
    CANNOT_DECLARE_UNO,
    ALREADY_DECLARED_UNO,
    UNO_CATCH_WINDOW_CLOSED,
    CANNOT_CATCH_SELF,
    INVALID_UNO_CATCH_TARGET,
    INVALID_COLOR_CHOICE,
    NEXT_ROUND_NOT_AVAILABLE,
}

data class UnoError(
    val code: UnoErrorCode,
    val message: String,
)

data class UnoActionResult(
    val success: Boolean,
    val state: UnoGameState,
    val error: UnoError? = null,
)

data class UnoStartResult(
    val engine: UnoEngine? = null,
    val error: UnoError? = null,
) {
    val success: Boolean get() = engine != null
}
