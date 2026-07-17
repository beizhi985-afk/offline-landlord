package com.offlinelandlord.game.core

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val symbol: String) {
    CLUBS("♣"),
    DIAMONDS("♦"),
    HEARTS("♥"),
    SPADES("♠"),
    JOKER(""),
}

@Serializable
enum class Rank(val power: Int, val label: String) {
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A"),
    TWO(15, "2"),
    SMALL_JOKER(16, "小王"),
    BIG_JOKER(17, "大王"),
}

@Serializable
data class Card(
    val suit: Suit,
    val rank: Rank,
) {
    val id: String
        get() = "${suit.name}_${rank.name}"

    val displayText: String
        get() = if (suit == Suit.JOKER) rank.label else "${suit.symbol}${rank.label}"

    companion object {
        fun fromId(id: String): Card? {
            val separator = id.indexOf('_')
            if (separator <= 0 || separator >= id.lastIndex) return null
            val suit = runCatching { Suit.valueOf(id.substring(0, separator)) }.getOrNull() ?: return null
            val rank = runCatching { Rank.valueOf(id.substring(separator + 1)) }.getOrNull() ?: return null
            return Card(suit, rank)
        }
    }
}

val cardComparator: Comparator<Card> =
    compareBy<Card> { it.rank.power }.thenBy { it.suit.ordinal }

