package com.offlinelandlord.game.uno.core

import kotlin.random.Random

internal object UnoTestFixtures {
    fun number(id: String, color: UnoColor, value: Int) =
        UnoCard(id, color, UnoCardType.NUMBER, value)

    fun action(id: String, color: UnoColor, type: UnoCardType) =
        UnoCard(id, color, type)

    fun wild(id: String = "wild") = UnoCard(id, null, UnoCardType.WILD)

    fun wildDrawFour(id: String = "wild-four") =
        UnoCard(id, null, UnoCardType.WILD_DRAW_FOUR)

    fun engine(
        hands: List<List<UnoCard>>,
        top: UnoCard,
        drawPile: List<UnoCard> = listOf(number("draw-default", UnoColor.YELLOW, 9)),
        discardPile: List<UnoCard> = listOf(top),
        currentSeat: Int? = 0,
        direction: UnoDirection = UnoDirection.CLOCKWISE,
        activeColor: UnoColor? = top.color ?: UnoColor.RED,
        phase: UnoPhase = UnoPhase.TURN,
        matchMode: UnoMatchMode = UnoMatchMode.QUICK,
        targetScore: Int = 500,
        scores: Map<String, Int> = hands.indices.associate { "p$it" to 0 },
        declaredPlayerId: String? = null,
        catchTargetId: String? = null,
        drawnCardId: String? = null,
        colorChooserId: String? = null,
        colorChoiceStartsTurn: Boolean = false,
        pendingWinnerId: String? = null,
        roundWinnerId: String? = null,
        matchWinnerId: String? = null,
        roundNumber: Int = 1,
        baseStartingSeat: Int = 0,
        lastRoundScore: Int = 0,
        random: Random = Random(0),
    ): UnoEngine {
        val players = hands.mapIndexed { seat, hand ->
            UnoPlayerState(
                playerId = "p$seat",
                playerName = "Player $seat",
                seat = seat,
                hand = hand,
                score = scores.getValue("p$seat"),
            )
        }
        return UnoEngine.fromState(
            UnoGameState(
                players = players,
                currentPlayerId = currentSeat?.let { "p$it" },
                direction = direction,
                drawPile = drawPile,
                discardPile = discardPile,
                activeColor = activeColor,
                phase = phase,
                roundNumber = roundNumber,
                baseStartingSeat = baseStartingSeat,
                scores = scores,
                unoDeclaredPlayerId = declaredPlayerId,
                catchWindow = catchTargetId?.let(::UnoCatchWindow),
                drawnCardId = drawnCardId,
                colorChooserPlayerId = colorChooserId,
                colorChoiceStartsTurn = colorChoiceStartsTurn,
                pendingRoundWinnerId = pendingWinnerId,
                roundWinnerId = roundWinnerId,
                matchWinnerId = matchWinnerId,
                matchMode = matchMode,
                targetScore = targetScore,
                lastRoundScore = lastRoundScore,
            ),
            random,
        )
    }

    fun players(count: Int): List<UnoPlayer> =
        (0 until count).map { UnoPlayer("p$it", "Player $it") }

    fun allCards(state: UnoGameState): List<UnoCard> =
        state.players.flatMap { it.hand } + state.drawPile + state.discardPile
}
