package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoCardType
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoScoring
import kotlin.random.Random

interface UnoBotStrategy {
    fun chooseAction(
        observation: UnoBotObservation,
        availableActions: Set<UnoActionType>,
        legalPlayableCards: List<UnoCard>,
    ): UnoAction?
}

class NormalUnoBotStrategy(
    private val random: Random,
) : UnoBotStrategy {
    override fun chooseAction(
        observation: UnoBotObservation,
        availableActions: Set<UnoActionType>,
        legalPlayableCards: List<UnoCard>,
    ): UnoAction? {
        if (UnoActionType.CATCH_UNO in availableActions) {
            return observation.catchTargetPlayerId?.let(UnoAction::CatchUno)
        }
        if (UnoActionType.CHOOSE_COLOR in availableActions) {
            return UnoAction.ChooseColor(chooseColor(observation.ownHand))
        }
        if (UnoActionType.START_NEXT_ROUND in availableActions) {
            return UnoAction.StartNextRound
        }
        if (observation.currentPlayerId != observation.selfPlayerId) return null

        if (UnoActionType.DECLARE_UNO in availableActions &&
            observation.ownHand.size == 2 &&
            legalPlayableCards.isNotEmpty()
        ) {
            return UnoAction.DeclareUno
        }

        return when (observation.phase) {
            UnoPhase.TURN -> chooseTurnAction(observation, availableActions, legalPlayableCards)
            UnoPhase.AFTER_DRAW -> chooseAfterDrawAction(observation, availableActions, legalPlayableCards)
            UnoPhase.CHOOSE_COLOR,
            UnoPhase.ROUND_FINISHED,
            UnoPhase.MATCH_FINISHED,
            -> null
        }
    }

    private fun chooseTurnAction(
        observation: UnoBotObservation,
        availableActions: Set<UnoActionType>,
        legalCards: List<UnoCard>,
    ): UnoAction? {
        if (UnoActionType.PLAY_CARD in availableActions && legalCards.isNotEmpty()) {
            return UnoAction.PlayCard(selectCard(observation, legalCards).cardId)
        }
        return UnoAction.DrawCard.takeIf { UnoActionType.DRAW_CARD in availableActions }
    }

    private fun chooseAfterDrawAction(
        observation: UnoBotObservation,
        availableActions: Set<UnoActionType>,
        legalCards: List<UnoCard>,
    ): UnoAction? {
        val drawn = legalCards.singleOrNull { it.cardId == observation.drawnCardId }
        if (drawn != null && UnoActionType.PLAY_DRAWN_CARD in availableActions) {
            val strongWild = drawn.type == UnoCardType.WILD || drawn.type == UnoCardType.WILD_DRAW_FOUR
            if (!strongWild || isDangerousSituation(observation)) {
                return UnoAction.PlayDrawnCard(drawn.cardId)
            }
        }
        return UnoAction.PassAfterDraw.takeIf { UnoActionType.PASS_AFTER_DRAW in availableActions }
    }

    private fun selectCard(observation: UnoBotObservation, legalCards: List<UnoCard>): UnoCard {
        if (observation.ownHand.size == 1) return legalCards.random(random)
        val danger = isDangerousSituation(observation)
        val colorCounts = observation.ownHand.mapNotNull { it.color }.groupingBy { it }.eachCount()
        val scored = legalCards.map { card ->
            val attack = if (danger) {
                when (card.type) {
                    UnoCardType.DRAW_TWO -> 10_000
                    UnoCardType.SKIP -> 9_000
                    UnoCardType.WILD_DRAW_FOUR -> 8_500
                    UnoCardType.REVERSE -> 8_000
                    UnoCardType.WILD -> 7_000
                    UnoCardType.NUMBER -> 0
                }
            } else {
                when (card.type) {
                    UnoCardType.WILD_DRAW_FOUR -> -10_000
                    UnoCardType.WILD -> -8_000
                    else -> 0
                }
            }
            val colorStrength = card.color?.let { colorCounts[it] ?: 0 } ?: 0
            card to (attack + UnoScoring.cardPoints(card) * 100 + colorStrength * 20)
        }
        val bestScore = scored.maxOf { it.second }
        return scored.filter { it.second == bestScore }.map { it.first }.random(random)
    }

    private fun isDangerousSituation(observation: UnoBotObservation): Boolean {
        if (observation.ownHand.size <= 2) return true
        return nextPlayer(observation)?.remainingCardCount?.let { it <= 2 } == true
    }

    private fun nextPlayer(observation: UnoBotObservation): UnoBotPlayerObservation? {
        val ordered = observation.players.sortedBy { it.seat }
        val currentIndex = ordered.indexOfFirst { it.playerId == observation.selfPlayerId }
        if (currentIndex < 0 || ordered.size < 2) return null
        val delta = if (observation.direction == UnoDirection.CLOCKWISE) 1 else -1
        val index = ((currentIndex + delta) % ordered.size + ordered.size) % ordered.size
        return ordered[index]
    }

    private fun chooseColor(hand: List<UnoCard>): UnoColor {
        val scored = UnoColor.entries.map { color ->
            val cards = hand.filter { it.color == color }
            Triple(color, cards.size, cards.sumOf(UnoScoring::cardPoints))
        }
        val maxCount = scored.maxOf { it.second }
        val countLeaders = scored.filter { it.second == maxCount }
        val maxValue = countLeaders.maxOf { it.third }
        return countLeaders.filter { it.third == maxValue }.map { it.first }.random(random)
    }
}
