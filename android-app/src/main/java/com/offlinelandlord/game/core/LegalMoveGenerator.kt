package com.offlinelandlord.game.core

data class LegalMove(
    val cards: List<Card>,
    val pattern: CardPattern,
)

object LegalMoveGenerator {
    fun generate(hand: List<Card>, previous: CardPattern? = null): List<LegalMove> {
        if (hand.isEmpty()) return emptyList()

        val grouped = hand.sortedWith(cardComparator).groupBy { it.rank }
        val moves = linkedMapOf<String, LegalMove>()

        fun add(cards: List<Card>) {
            if (cards.isEmpty()) return
            val pattern = HandRules.analyze(cards) ?: return
            if (previous != null && !HandRules.beats(pattern, previous)) return
            val signature = cards.groupingBy { it.rank }
                .eachCount()
                .entries
                .sortedBy { it.key.power }
                .joinToString("|") { "${it.key.name}:${it.value}" }
            moves.putIfAbsent(signature, LegalMove(cards.sortedWith(cardComparator), pattern))
        }

        grouped.values.forEach { cards ->
            add(cards.take(1))
            if (cards.size >= 2) add(cards.take(2))
            if (cards.size >= 3) add(cards.take(3))
            if (cards.size == 4) add(cards)
        }

        val rocket = listOfNotNull(
            grouped[Rank.SMALL_JOKER]?.firstOrNull(),
            grouped[Rank.BIG_JOKER]?.firstOrNull(),
        )
        if (rocket.size == 2) add(rocket)

        val ranks = grouped.keys.sortedBy { it.power }
        val tripleRanks = ranks.filter { grouped.getValue(it).size >= 3 }
        tripleRanks.forEach { tripleRank ->
            val triple = grouped.getValue(tripleRank).take(3)
            ranks.filter { it != tripleRank }.forEach { wingRank ->
                add(triple + grouped.getValue(wingRank).take(1))
                if (grouped.getValue(wingRank).size >= 2) {
                    add(triple + grouped.getValue(wingRank).take(2))
                }
            }
        }

        addRankChains(grouped, copiesPerRank = 1, minimumLength = 5, ::add)
        addRankChains(grouped, copiesPerRank = 2, minimumLength = 3, ::add)

        val airplaneBodies = rankChains(grouped, copiesPerRank = 3, minimumLength = 2)
        airplaneBodies.forEach { bodyRanks ->
            val body = bodyRanks.flatMap { grouped.getValue(it).take(3) }
            add(body)

            val outsideCards = ranks
                .filterNot { it in bodyRanks }
                .flatMap { grouped.getValue(it) }
            combinations(outsideCards, bodyRanks.size).forEach { wings ->
                add(body + wings)
            }

            val pairRanks = ranks.filter { it !in bodyRanks && grouped.getValue(it).size >= 2 }
            combinations(pairRanks, bodyRanks.size).forEach { wingRanks ->
                add(body + wingRanks.flatMap { grouped.getValue(it).take(2) })
            }
        }

        ranks.filter { grouped.getValue(it).size == 4 }.forEach { fourRank ->
            val body = grouped.getValue(fourRank)
            val outsideCards = ranks.filterNot { it == fourRank }.flatMap { grouped.getValue(it) }
            combinations(outsideCards, 2).forEach { wings -> add(body + wings) }

            val pairRanks = ranks.filter { it != fourRank && grouped.getValue(it).size >= 2 }
            combinations(pairRanks, 2).forEach { wingRanks ->
                add(body + wingRanks.flatMap { grouped.getValue(it).take(2) })
            }
        }

        return moves.values.sortedWith(
            compareBy<LegalMove> { it.pattern.type.ordinal }
                .thenBy { it.pattern.cardCount }
                .thenBy { it.pattern.mainRank.power },
        )
    }

    private fun addRankChains(
        grouped: Map<Rank, List<Card>>,
        copiesPerRank: Int,
        minimumLength: Int,
        add: (List<Card>) -> Unit,
    ) {
        rankChains(grouped, copiesPerRank, minimumLength).forEach { chain ->
            add(chain.flatMap { grouped.getValue(it).take(copiesPerRank) })
        }
    }

    private fun rankChains(
        grouped: Map<Rank, List<Card>>,
        copiesPerRank: Int,
        minimumLength: Int,
    ): List<List<Rank>> {
        val eligible = Rank.entries
            .filter { it.power <= Rank.ACE.power && (grouped[it]?.size ?: 0) >= copiesPerRank }
            .sortedBy { it.power }
        val result = mutableListOf<List<Rank>>()

        eligible.indices.forEach { start ->
            var end = start
            while (
                end + 1 < eligible.size &&
                eligible[end + 1].power == eligible[end].power + 1
            ) {
                end++
            }
            val runLength = end - start + 1
            if (runLength >= minimumLength) {
                for (length in minimumLength..runLength) {
                    for (offset in 0..runLength - length) {
                        result += eligible.subList(start + offset, start + offset + length)
                    }
                }
            }
        }

        return result.distinctBy { chain -> chain.joinToString(",") { it.name } }
    }

    private fun <T> combinations(items: List<T>, count: Int): List<List<T>> {
        if (count < 0 || count > items.size) return emptyList()
        if (count == 0) return listOf(emptyList())
        val result = mutableListOf<List<T>>()
        val current = ArrayList<T>(count)

        fun visit(start: Int) {
            if (current.size == count) {
                result += current.toList()
                return
            }
            val remaining = count - current.size
            for (index in start..items.size - remaining) {
                current += items[index]
                visit(index + 1)
                current.removeAt(current.lastIndex)
            }
        }

        visit(0)
        return result
    }
}
