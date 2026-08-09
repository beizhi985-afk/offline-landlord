package com.offlinelandlord.game.uno.core

object UnoScoring {
    fun cardPoints(card: UnoCard): Int = when (card.type) {
        UnoCardType.NUMBER -> requireNotNull(card.number)
        UnoCardType.SKIP,
        UnoCardType.REVERSE,
        UnoCardType.DRAW_TWO,
        -> 20

        UnoCardType.WILD,
        UnoCardType.WILD_DRAW_FOUR,
        -> 50
    }

    fun handPoints(hand: List<UnoCard>): Int = hand.sumOf(::cardPoints)
}
