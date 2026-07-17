package com.offlinelandlord.game.core

import kotlinx.serialization.Serializable

@Serializable
enum class PatternType(val displayName: String) {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三张"),
    TRIPLE_WITH_SINGLE("三带一"),
    TRIPLE_WITH_PAIR("三带二"),
    STRAIGHT("顺子"),
    CONSECUTIVE_PAIRS("连对"),
    AIRPLANE("飞机"),
    AIRPLANE_WITH_SINGLES("飞机带单"),
    AIRPLANE_WITH_PAIRS("飞机带对"),
    FOUR_WITH_TWO_SINGLES("四带二"),
    FOUR_WITH_TWO_PAIRS("四带两对"),
    BOMB("炸弹"),
    ROCKET("王炸"),
}

@Serializable
data class CardPattern(
    val type: PatternType,
    val mainRank: Rank,
    val cardCount: Int,
    val chainLength: Int = 1,
)

object HandRules {
    fun analyze(cards: List<Card>): CardPattern? {
        if (cards.isEmpty()) return null

        val counts = cards.groupingBy { it.rank }.eachCount()
        val size = cards.size

        if (size == 2 && counts.keys == setOf(Rank.SMALL_JOKER, Rank.BIG_JOKER)) {
            return CardPattern(PatternType.ROCKET, Rank.BIG_JOKER, size)
        }

        if (size == 4 && counts.size == 1) {
            return CardPattern(PatternType.BOMB, counts.keys.single(), size)
        }

        if (size == 1) return CardPattern(PatternType.SINGLE, cards.single().rank, size)
        if (size == 2 && counts.size == 1) return CardPattern(PatternType.PAIR, counts.keys.single(), size)
        if (size == 3 && counts.size == 1) return CardPattern(PatternType.TRIPLE, counts.keys.single(), size)

        if (size == 4) {
            counts.entries.firstOrNull { it.value == 3 }?.let {
                return CardPattern(PatternType.TRIPLE_WITH_SINGLE, it.key, size)
            }
        }

        if (size == 5 && counts.values.sorted() == listOf(2, 3)) {
            val tripleRank = counts.entries.single { it.value == 3 }.key
            return CardPattern(PatternType.TRIPLE_WITH_PAIR, tripleRank, size)
        }

        analyzeStraight(cards, counts)?.let { return it }
        analyzeConsecutivePairs(cards, counts)?.let { return it }
        analyzeAirplane(cards, counts, WingMode.NONE)?.let { return it }
        analyzeAirplane(cards, counts, WingMode.SINGLES)?.let { return it }
        analyzeAirplane(cards, counts, WingMode.PAIRS)?.let { return it }
        analyzeFourWithWings(cards, counts)?.let { return it }

        return null
    }

    fun beats(candidate: CardPattern, previous: CardPattern): Boolean {
        if (candidate.type == PatternType.ROCKET) return previous.type != PatternType.ROCKET
        if (previous.type == PatternType.ROCKET) return false

        if (candidate.type == PatternType.BOMB) {
            return previous.type != PatternType.BOMB || candidate.mainRank.power > previous.mainRank.power
        }
        if (previous.type == PatternType.BOMB) return false

        return candidate.type == previous.type &&
            candidate.cardCount == previous.cardCount &&
            candidate.chainLength == previous.chainLength &&
            candidate.mainRank.power > previous.mainRank.power
    }

    private fun analyzeStraight(
        cards: List<Card>,
        counts: Map<Rank, Int>,
    ): CardPattern? {
        if (cards.size < 5 || counts.values.any { it != 1 }) return null
        val ranks = counts.keys.sortedBy { it.power }
        if (!isConsecutive(ranks) || ranks.last().power > Rank.ACE.power) return null
        return CardPattern(PatternType.STRAIGHT, ranks.last(), cards.size, ranks.size)
    }

    private fun analyzeConsecutivePairs(
        cards: List<Card>,
        counts: Map<Rank, Int>,
    ): CardPattern? {
        if (cards.size < 6 || cards.size % 2 != 0 || counts.values.any { it != 2 }) return null
        val ranks = counts.keys.sortedBy { it.power }
        if (ranks.size < 3 || !isConsecutive(ranks) || ranks.last().power > Rank.ACE.power) return null
        return CardPattern(PatternType.CONSECUTIVE_PAIRS, ranks.last(), cards.size, ranks.size)
    }

    private fun analyzeAirplane(
        cards: List<Card>,
        counts: Map<Rank, Int>,
        wingMode: WingMode,
    ): CardPattern? {
        val unitSize = when (wingMode) {
            WingMode.NONE -> 3
            WingMode.SINGLES -> 4
            WingMode.PAIRS -> 5
        }
        if (cards.size % unitSize != 0) return null
        val bodyLength = cards.size / unitSize
        if (bodyLength < 2) return null

        val possibleStarts = Rank.entries.filter { rank ->
            rank.power >= Rank.THREE.power &&
                rank.power + bodyLength - 1 <= Rank.ACE.power
        }

        for (start in possibleStarts) {
            val bodyRanks = (0 until bodyLength).mapNotNull { offset ->
                Rank.entries.firstOrNull { it.power == start.power + offset }
            }
            if (bodyRanks.size != bodyLength || bodyRanks.any { (counts[it] ?: 0) < 3 }) continue

            val remainder = counts.toMutableMap()
            bodyRanks.forEach { rank -> remainder[rank] = (remainder[rank] ?: 0) - 3 }
            remainder.entries.removeAll { it.value == 0 }

            // The fourth card of a body rank cannot be reused as a wing.
            if (remainder.keys.any { it in bodyRanks }) continue

            val matches = when (wingMode) {
                WingMode.NONE -> remainder.isEmpty()
                WingMode.SINGLES -> remainder.values.sum() == bodyLength
                WingMode.PAIRS -> remainder.size == bodyLength && remainder.values.all { it == 2 }
            }
            if (!matches) continue

            val type = when (wingMode) {
                WingMode.NONE -> PatternType.AIRPLANE
                WingMode.SINGLES -> PatternType.AIRPLANE_WITH_SINGLES
                WingMode.PAIRS -> PatternType.AIRPLANE_WITH_PAIRS
            }
            return CardPattern(type, bodyRanks.last(), cards.size, bodyLength)
        }

        return null
    }

    private fun analyzeFourWithWings(
        cards: List<Card>,
        counts: Map<Rank, Int>,
    ): CardPattern? {
        val fourRank = counts.entries.firstOrNull { it.value == 4 }?.key ?: return null
        val remainder = counts.filterKeys { it != fourRank }

        if (cards.size == 6 && remainder.values.sum() == 2) {
            return CardPattern(PatternType.FOUR_WITH_TWO_SINGLES, fourRank, cards.size)
        }
        if (cards.size == 8 && remainder.size == 2 && remainder.values.all { it == 2 }) {
            return CardPattern(PatternType.FOUR_WITH_TWO_PAIRS, fourRank, cards.size)
        }
        return null
    }

    private fun isConsecutive(ranks: List<Rank>): Boolean =
        ranks.zipWithNext().all { (left, right) -> right.power == left.power + 1 }

    private enum class WingMode {
        NONE,
        SINGLES,
        PAIRS,
    }
}

