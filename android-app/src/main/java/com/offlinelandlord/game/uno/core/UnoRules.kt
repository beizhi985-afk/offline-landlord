package com.offlinelandlord.game.uno.core

object UnoRules {
    fun canPlayCard(
        card: UnoCard,
        hand: List<UnoCard>,
        activeColor: UnoColor?,
        topDiscard: UnoCard,
    ): Boolean = when (card.type) {
        UnoCardType.WILD -> true
        UnoCardType.WILD_DRAW_FOUR -> activeColor == null || hand.none { it.color == activeColor }
        else -> {
            card.color == activeColor ||
                (card.type == UnoCardType.NUMBER &&
                    topDiscard.type == UnoCardType.NUMBER &&
                    card.number == topDiscard.number) ||
                (card.type != UnoCardType.NUMBER && card.type == topDiscard.type)
        }
    }

    fun legalPlayableCards(
        hand: List<UnoCard>,
        activeColor: UnoColor?,
        topDiscard: UnoCard,
    ): List<UnoCard> = hand.filter { canPlayCard(it, hand, activeColor, topDiscard) }
}
