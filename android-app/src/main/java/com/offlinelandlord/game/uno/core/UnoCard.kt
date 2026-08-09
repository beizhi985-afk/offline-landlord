package com.offlinelandlord.game.uno.core

enum class UnoColor {
    RED,
    YELLOW,
    GREEN,
    BLUE,
}

enum class UnoCardType {
    NUMBER,
    SKIP,
    REVERSE,
    DRAW_TWO,
    WILD,
    WILD_DRAW_FOUR,
}

data class UnoCard(
    val cardId: String,
    val color: UnoColor?,
    val type: UnoCardType,
    val number: Int? = null,
) {
    init {
        require(cardId.isNotBlank()) { "cardId must not be blank" }
        when (type) {
            UnoCardType.NUMBER -> {
                require(color != null) { "Number cards require a color" }
                require(number in 0..9) { "Number cards require a value from 0 to 9" }
            }

            UnoCardType.SKIP,
            UnoCardType.REVERSE,
            UnoCardType.DRAW_TWO,
            -> {
                require(color != null) { "$type cards require a color" }
                require(number == null) { "$type cards cannot have a number" }
            }

            UnoCardType.WILD,
            UnoCardType.WILD_DRAW_FOUR,
            -> {
                require(color == null) { "$type cards cannot have a fixed color" }
                require(number == null) { "$type cards cannot have a number" }
            }
        }
    }
}
