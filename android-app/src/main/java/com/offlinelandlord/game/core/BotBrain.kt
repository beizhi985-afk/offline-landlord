package com.offlinelandlord.game.core

object BotBrain {
    fun chooseAction(view: PlayerGameView): PlayerAction? {
        if (view.currentTurnId != view.selfPlayerId) return null
        return when (view.phase) {
            GamePhase.BIDDING -> chooseBid(view)
            GamePhase.PLAYING -> choosePlay(view)
            else -> null
        }
    }

    private fun chooseBid(view: PlayerGameView): PlayerAction {
        val grouped = view.ownHand.groupingBy { it.rank }.eachCount()
        var strength = 0
        if (grouped.containsKey(Rank.SMALL_JOKER)) strength += 2
        if (grouped.containsKey(Rank.BIG_JOKER)) strength += 3
        if (grouped.keys.containsAll(listOf(Rank.SMALL_JOKER, Rank.BIG_JOKER))) strength += 3
        strength += (grouped[Rank.TWO] ?: 0) * 2
        strength += grouped.values.count { it == 4 } * 4
        strength += (grouped[Rank.ACE] ?: 0)
        strength += LegalMoveGenerator.generate(view.ownHand)
            .count { it.pattern.type == PatternType.STRAIGHT && it.cards.size >= 6 }
            .coerceAtMost(2)

        val desired = when {
            strength >= 14 -> 3
            strength >= 9 -> 2
            strength >= 5 -> 1
            else -> 0
        }
        return PlayerAction.bid(if (desired > view.highestBid) desired else 0)
    }

    private fun choosePlay(view: PlayerGameView): PlayerAction {
        val previous = view.lastPlay
        val moves = LegalMoveGenerator.generate(view.ownHand, previous?.pattern)
        if (moves.isEmpty()) return PlayerAction.pass()

        moves.filter { it.cards.size == view.ownHand.size }
            .minByOrNull { moveScore(it, view, urgent = true) }
            ?.let { return PlayerAction.play(it.cards.map(Card::id)) }

        if (previous != null && shouldYieldToFarmerPartner(view, previous)) {
            return PlayerAction.pass()
        }

        val urgent = view.players
            .filter { it.id != view.selfPlayerId && it.role != self(view)?.role }
            .any { it.remainingCards <= 2 }
        val selected = moves.minByOrNull { moveScore(it, view, urgent) }
            ?: return PlayerAction.pass()
        return PlayerAction.play(selected.cards.map(Card::id))
    }

    private fun shouldYieldToFarmerPartner(view: PlayerGameView, previous: PublicPlay): Boolean {
        val self = self(view) ?: return false
        if (self.role != PlayerRole.FARMER) return false
        val previousPlayer = view.players.firstOrNull { it.id == previous.playerId } ?: return false
        return previousPlayer.role == PlayerRole.FARMER
    }

    private fun moveScore(move: LegalMove, view: PlayerGameView, urgent: Boolean): Int {
        val pattern = move.pattern
        var score = pattern.mainRank.power * 3 - pattern.cardCount * 12
        score += when (pattern.type) {
            PatternType.SINGLE -> 38
            PatternType.PAIR -> 24
            PatternType.TRIPLE -> 12
            PatternType.TRIPLE_WITH_SINGLE,
            PatternType.TRIPLE_WITH_PAIR,
            PatternType.STRAIGHT,
            PatternType.CONSECUTIVE_PAIRS,
            PatternType.AIRPLANE,
            PatternType.AIRPLANE_WITH_SINGLES,
            PatternType.AIRPLANE_WITH_PAIRS,
            PatternType.FOUR_WITH_TWO_SINGLES,
            PatternType.FOUR_WITH_TWO_PAIRS,
            -> 0
            PatternType.BOMB -> if (urgent) 90 else 520
            PatternType.ROCKET -> if (urgent) 120 else 650
        }

        val handCounts = view.ownHand.groupingBy { it.rank }.eachCount()
        val moveCounts = move.cards.groupingBy { it.rank }.eachCount()
        if (pattern.type != PatternType.BOMB && pattern.type != PatternType.ROCKET) {
            score += moveCounts.keys.count { handCounts[it] == 4 } * 150
            score += move.cards.count { it.rank == Rank.SMALL_JOKER || it.rank == Rank.BIG_JOKER } * 80
            score += move.cards.count { it.rank == Rank.TWO } * 24
        }

        if (move.cards.size == view.ownHand.size) score -= 10_000
        return score
    }

    private fun self(view: PlayerGameView): PlayerSummary? =
        view.players.firstOrNull { it.id == view.selfPlayerId }
}
