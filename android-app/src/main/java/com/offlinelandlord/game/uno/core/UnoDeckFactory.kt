package com.offlinelandlord.game.uno.core

object UnoDeckFactory {
    fun createClassicDeck(): List<UnoCard> = buildList(108) {
        UnoColor.entries.forEach { color ->
            add(UnoCard("${color.name}-0", color, UnoCardType.NUMBER, 0))
            (1..9).forEach { number ->
                repeat(2) { copy ->
                    add(UnoCard("${color.name}-$number-${copy + 1}", color, UnoCardType.NUMBER, number))
                }
            }
            listOf(UnoCardType.SKIP, UnoCardType.REVERSE, UnoCardType.DRAW_TWO).forEach { type ->
                repeat(2) { copy ->
                    add(UnoCard("${color.name}-${type.name}-${copy + 1}", color, type))
                }
            }
        }
        repeat(4) { copy -> add(UnoCard("WILD-${copy + 1}", null, UnoCardType.WILD)) }
        repeat(4) { copy ->
            add(UnoCard("WILD-DRAW-FOUR-${copy + 1}", null, UnoCardType.WILD_DRAW_FOUR))
        }
    }
}
