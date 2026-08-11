package com.offlinelandlord.game.uno.core

import kotlin.random.Random

class UnoEngine private constructor(
    playerDefinitions: List<UnoPlayer>,
    private val random: Random,
    private val matchMode: UnoMatchMode,
    private val targetScore: Int,
    initialState: UnoGameState? = null,
) {
    private data class MutablePlayer(
        val playerId: String,
        val playerName: String,
        val seat: Int,
        val hand: MutableList<UnoCard> = mutableListOf(),
        var score: Int = 0,
    )

    private val players = playerDefinitions.mapIndexed { seat, player ->
        MutablePlayer(player.playerId, player.playerName, seat)
    }.toMutableList()
    private val scores = linkedMapOf<String, Int>()
    private val drawPile = mutableListOf<UnoCard>()
    private val discardPile = mutableListOf<UnoCard>()

    private var currentSeat: Int? = null
    private var direction = UnoDirection.CLOCKWISE
    private var activeColor: UnoColor? = null
    private var phase = UnoPhase.TURN
    private var roundNumber = 1
    private var baseStartingSeat = 0
    private var unoDeclaredPlayerId: String? = null
    private var catchWindow: UnoCatchWindow? = null
    private var drawnCardId: String? = null
    private var colorChooserPlayerId: String? = null
    private var colorChoiceStartsTurn = false
    private var pendingRoundWinnerId: String? = null
    private var pendingWildEffect: UnoCardType? = null
    private var roundWinnerId: String? = null
    private var matchWinnerId: String? = null
    private var lastRoundScore = 0

    init {
        if (initialState == null) {
            players.forEach { scores[it.playerId] = 0 }
            baseStartingSeat = random.nextInt(players.size)
            beginRound(round = 1, startingSeat = baseStartingSeat)
        } else {
            loadState(initialState)
        }
    }

    val state: UnoGameState
        @Synchronized get() = snapshot()

    @Synchronized
    fun viewFor(playerId: String): UnoGameView? {
        val self = players.firstOrNull { it.playerId == playerId } ?: return null
        return UnoGameView(
            selfPlayerId = self.playerId,
            ownHand = self.hand.toList(),
            players = players.map {
                UnoPublicPlayerState(
                    playerId = it.playerId,
                    playerName = it.playerName,
                    seat = it.seat,
                    remainingCardCount = it.hand.size,
                    score = it.score,
                )
            },
            currentPlayerId = currentSeat?.let { players[it].playerId },
            direction = direction,
            topDiscardCard = discardPile.last(),
            drawPileCount = drawPile.size,
            activeColor = activeColor,
            phase = phase,
            roundNumber = roundNumber,
            scores = scores.toMap(),
            unoDeclaredPlayerId = unoDeclaredPlayerId,
            catchTargetPlayerId = catchWindow?.targetPlayerId,
            drawnCardId = drawnCardId.takeIf { currentSeat == self.seat },
            colorChooserPlayerId = colorChooserPlayerId,
            roundWinnerId = roundWinnerId,
            matchWinnerId = matchWinnerId,
            matchMode = matchMode,
            targetScore = targetScore,
            lastRoundScore = lastRoundScore,
        )
    }

    @Synchronized
    fun applyAction(playerId: String, action: UnoAction): UnoActionResult {
        val seat = players.indexOfFirst { it.playerId == playerId }
        if (seat < 0) return failure(UnoErrorCode.INVALID_PLAYER, "Player does not exist")

        return when (action) {
            is UnoAction.PlayCard -> playCard(seat, action.cardId, drawnOnly = false)
            UnoAction.DrawCard -> drawCard(seat)
            is UnoAction.PlayDrawnCard -> playCard(seat, action.cardId, drawnOnly = true)
            UnoAction.PassAfterDraw -> passAfterDraw(seat)
            UnoAction.DeclareUno -> declareUno(seat)
            is UnoAction.CatchUno -> catchUno(seat, action.targetPlayerId)
            is UnoAction.ChooseColor -> chooseColor(seat, action.color)
            UnoAction.StartNextRound -> startNextRound()
        }
    }

    @Synchronized
    fun legalPlayableCards(playerId: String): List<UnoCard> {
        val seat = players.indexOfFirst { it.playerId == playerId }
        if (seat < 0 || seat != currentSeat) return emptyList()
        val player = players[seat]
        val top = discardPile.lastOrNull() ?: return emptyList()
        return when (phase) {
            UnoPhase.TURN -> UnoRules.legalPlayableCards(player.hand, activeColor, top)
            UnoPhase.AFTER_DRAW -> player.hand.filter {
                it.cardId == drawnCardId && UnoRules.canPlayCard(it, player.hand, activeColor, top)
            }
            else -> emptyList()
        }
    }

    @Synchronized
    fun canPlayCard(playerId: String, cardId: String): Boolean =
        legalPlayableCards(playerId).any { it.cardId == cardId }

    @Synchronized
    fun availableActions(playerId: String): Set<UnoActionType> {
        val seat = players.indexOfFirst { it.playerId == playerId }
        if (seat < 0) return emptySet()
        val actions = linkedSetOf<UnoActionType>()
        catchWindow?.takeIf { it.targetPlayerId != playerId }?.let { actions += UnoActionType.CATCH_UNO }
        if (seat != currentSeat) {
            if (phase == UnoPhase.ROUND_FINISHED && matchMode == UnoMatchMode.POINTS) {
                actions += UnoActionType.START_NEXT_ROUND
            }
            return actions
        }

        when (phase) {
            UnoPhase.TURN -> {
                actions += UnoActionType.DRAW_CARD
                if (legalPlayableCards(playerId).isNotEmpty()) actions += UnoActionType.PLAY_CARD
                if (players[seat].hand.size == 2 && unoDeclaredPlayerId != playerId) {
                    actions += UnoActionType.DECLARE_UNO
                }
            }

            UnoPhase.AFTER_DRAW -> {
                actions += UnoActionType.PASS_AFTER_DRAW
                if (legalPlayableCards(playerId).isNotEmpty()) actions += UnoActionType.PLAY_DRAWN_CARD
            }

            UnoPhase.CHOOSE_COLOR -> {
                if (colorChooserPlayerId == playerId) actions += UnoActionType.CHOOSE_COLOR
            }

            UnoPhase.ROUND_FINISHED -> if (matchMode == UnoMatchMode.POINTS) {
                actions += UnoActionType.START_NEXT_ROUND
            }

            UnoPhase.MATCH_FINISHED -> Unit
        }
        return actions
    }

    private fun playCard(seat: Int, cardId: String, drawnOnly: Boolean): UnoActionResult {
        val expectedPhase = if (drawnOnly) UnoPhase.AFTER_DRAW else UnoPhase.TURN
        if (phase != expectedPhase) return failure(UnoErrorCode.WRONG_PHASE, "Card cannot be played now")
        if (seat != currentSeat) return failure(UnoErrorCode.NOT_YOUR_TURN, "It is not this player's turn")
        if (drawnOnly && cardId != drawnCardId) {
            return failure(
                UnoErrorCode.ONLY_DRAWN_CARD_CAN_BE_PLAYED,
                "Only the card drawn this turn can be played",
            )
        }

        val player = players[seat]
        val card = player.hand.firstOrNull { it.cardId == cardId }
            ?: return failure(UnoErrorCode.CARD_NOT_IN_HAND, "Card is not in the player's hand")
        val top = discardPile.last()
        if (!UnoRules.canPlayCard(card, player.hand, activeColor, top)) {
            val code = if (card.type == UnoCardType.WILD_DRAW_FOUR) {
                UnoErrorCode.ILLEGAL_WILD_DRAW_FOUR
            } else {
                UnoErrorCode.CARD_NOT_PLAYABLE
            }
            return failure(code, "Card is not playable")
        }

        closePreviousCatchWindow()
        executePlay(seat, card)
        return success()
    }

    private fun executePlay(seat: Int, card: UnoCard) {
        val player = players[seat]
        val declaredCorrectly = unoDeclaredPlayerId == player.playerId
        check(player.hand.removeAll { it.cardId == card.cardId }) { "Playable card disappeared from hand" }
        discardPile += card
        if (card.color != null) activeColor = card.color
        drawnCardId = null
        unoDeclaredPlayerId = null
        catchWindow = if (player.hand.size == 1 && !declaredCorrectly) {
            UnoCatchWindow(player.playerId)
        } else {
            null
        }

        if (player.hand.isEmpty()) {
            when (card.type) {
                UnoCardType.DRAW_TWO -> {
                    drawCards(nextSeat(seat), 2)
                    finishRound(player.playerId)
                }

                UnoCardType.WILD_DRAW_FOUR -> {
                    enterColorChoice(seat, card.type, winnerId = player.playerId)
                }

                UnoCardType.WILD -> finishRound(player.playerId)
                else -> finishRound(player.playerId)
            }
            return
        }

        when (card.type) {
            UnoCardType.NUMBER -> setTurn(advanceSeat(seat, 1))
            UnoCardType.SKIP -> setTurn(advanceSeat(seat, 2))
            UnoCardType.REVERSE -> {
                if (players.size == 2) {
                    setTurn(advanceSeat(seat, 2))
                } else {
                    direction = direction.reversed()
                    setTurn(advanceSeat(seat, 1))
                }
            }

            UnoCardType.DRAW_TWO -> {
                drawCards(nextSeat(seat), 2)
                setTurn(advanceSeat(seat, 2))
            }

            UnoCardType.WILD,
            UnoCardType.WILD_DRAW_FOUR,
            -> enterColorChoice(seat, card.type, winnerId = null)
        }
    }

    private fun drawCard(seat: Int): UnoActionResult {
        if (phase != UnoPhase.TURN) return failure(UnoErrorCode.WRONG_PHASE, "A card cannot be drawn now")
        if (seat != currentSeat) return failure(UnoErrorCode.NOT_YOUR_TURN, "It is not this player's turn")
        if (!hasDrawableCard()) return failure(UnoErrorCode.DRAW_PILE_EMPTY, "No card can be drawn")

        closePreviousCatchWindow()
        unoDeclaredPlayerId = null
        val drawn = requireNotNull(drawOne())
        players[seat].hand += drawn
        val top = discardPile.last()
        if (UnoRules.canPlayCard(drawn, players[seat].hand, activeColor, top)) {
            phase = UnoPhase.AFTER_DRAW
            drawnCardId = drawn.cardId
        } else {
            drawnCardId = null
            setTurn(advanceSeat(seat, 1))
        }
        return success()
    }

    private fun passAfterDraw(seat: Int): UnoActionResult {
        if (phase != UnoPhase.AFTER_DRAW) {
            return failure(UnoErrorCode.WRONG_PHASE, "There is no drawn card to pass")
        }
        if (seat != currentSeat) return failure(UnoErrorCode.NOT_YOUR_TURN, "It is not this player's turn")
        drawnCardId = null
        unoDeclaredPlayerId = null
        setTurn(advanceSeat(seat, 1))
        return success()
    }

    private fun declareUno(seat: Int): UnoActionResult {
        if (phase != UnoPhase.TURN) return failure(UnoErrorCode.WRONG_PHASE, "UNO cannot be declared now")
        if (seat != currentSeat) return failure(UnoErrorCode.NOT_YOUR_TURN, "It is not this player's turn")
        val player = players[seat]
        if (player.hand.size != 2) {
            return failure(UnoErrorCode.CANNOT_DECLARE_UNO, "UNO can only be declared with two cards")
        }
        if (unoDeclaredPlayerId == player.playerId) {
            return failure(UnoErrorCode.ALREADY_DECLARED_UNO, "UNO was already declared")
        }
        closePreviousCatchWindow()
        unoDeclaredPlayerId = player.playerId
        return success()
    }

    private fun catchUno(catcherSeat: Int, targetPlayerId: String): UnoActionResult {
        val catcher = players[catcherSeat]
        if (catcher.playerId == targetPlayerId) {
            return failure(UnoErrorCode.CANNOT_CATCH_SELF, "A player cannot catch themselves")
        }
        val window = catchWindow
            ?: return failure(UnoErrorCode.UNO_CATCH_WINDOW_CLOSED, "The UNO catch window is closed")
        if (window.targetPlayerId != targetPlayerId) {
            return failure(UnoErrorCode.INVALID_UNO_CATCH_TARGET, "This player cannot be caught")
        }
        val targetSeat = players.indexOfFirst { it.playerId == targetPlayerId }
        if (targetSeat < 0 || players[targetSeat].hand.size != 1) {
            return failure(UnoErrorCode.INVALID_UNO_CATCH_TARGET, "The target is not catchable")
        }
        drawCards(targetSeat, 2)
        catchWindow = null
        return success()
    }

    private fun chooseColor(seat: Int, color: UnoColor): UnoActionResult {
        if (phase != UnoPhase.CHOOSE_COLOR) {
            return failure(UnoErrorCode.WRONG_PHASE, "No color choice is pending")
        }
        val chooser = colorChooserPlayerId
        if (players[seat].playerId != chooser) {
            return failure(UnoErrorCode.NOT_YOUR_TURN, "Only the Wild player can choose the color")
        }

        activeColor = color
        colorChooserPlayerId = null
        if (colorChoiceStartsTurn) {
            colorChoiceStartsTurn = false
            pendingWildEffect = null
            setTurn(seat)
            return success()
        }

        when (pendingWildEffect) {
            UnoCardType.WILD -> {
                pendingWildEffect = null
                setTurn(advanceSeat(seat, 1))
            }

            UnoCardType.WILD_DRAW_FOUR -> {
                drawCards(nextSeat(seat), 4)
                pendingWildEffect = null
                val winner = pendingRoundWinnerId
                if (winner != null) {
                    finishRound(winner)
                } else {
                    setTurn(advanceSeat(seat, 2))
                }
            }

            else -> check(false) { "Color choice has no pending Wild effect" }
        }
        return success()
    }

    private fun startNextRound(): UnoActionResult {
        if (matchMode != UnoMatchMode.POINTS || phase != UnoPhase.ROUND_FINISHED) {
            return failure(UnoErrorCode.NEXT_ROUND_NOT_AVAILABLE, "The next round is not available")
        }
        beginRound(roundNumber + 1, (baseStartingSeat + 1) % players.size)
        return success()
    }

    private fun enterColorChoice(seat: Int, effect: UnoCardType, winnerId: String?) {
        phase = UnoPhase.CHOOSE_COLOR
        currentSeat = seat
        colorChooserPlayerId = players[seat].playerId
        colorChoiceStartsTurn = false
        pendingWildEffect = effect
        pendingRoundWinnerId = winnerId
    }

    private fun finishRound(winnerId: String) {
        val points = players.filterNot { it.playerId == winnerId }.sumOf { UnoScoring.handPoints(it.hand) }
        scores[winnerId] = scores.getValue(winnerId) + points
        players.forEach { it.score = scores.getValue(it.playerId) }
        lastRoundScore = points
        roundWinnerId = winnerId
        currentSeat = null
        drawnCardId = null
        colorChooserPlayerId = null
        colorChoiceStartsTurn = false
        pendingRoundWinnerId = null
        pendingWildEffect = null
        unoDeclaredPlayerId = null
        catchWindow = null

        val matchComplete = matchMode == UnoMatchMode.QUICK || scores.getValue(winnerId) >= targetScore
        if (matchComplete) {
            phase = UnoPhase.MATCH_FINISHED
            matchWinnerId = winnerId
        } else {
            phase = UnoPhase.ROUND_FINISHED
            matchWinnerId = null
        }
    }

    private fun beginRound(round: Int, startingSeat: Int) {
        roundNumber = round
        baseStartingSeat = startingSeat
        direction = UnoDirection.CLOCKWISE
        activeColor = null
        phase = UnoPhase.TURN
        currentSeat = startingSeat
        unoDeclaredPlayerId = null
        catchWindow = null
        drawnCardId = null
        colorChooserPlayerId = null
        colorChoiceStartsTurn = false
        pendingRoundWinnerId = null
        pendingWildEffect = null
        roundWinnerId = null
        matchWinnerId = null
        lastRoundScore = 0
        players.forEach { it.hand.clear() }
        drawPile.clear()
        discardPile.clear()
        drawPile += UnoDeckFactory.createClassicDeck().shuffled(random)

        repeat(7) {
            repeat(players.size) { offset ->
                val seat = (startingSeat + offset) % players.size
                players[seat].hand += drawPile.removeAt(drawPile.lastIndex)
            }
        }

        val initialIndex = drawPile.indexOfLast { it.type != UnoCardType.WILD_DRAW_FOUR }
        check(initialIndex >= 0) { "Classic deck must contain an eligible initial discard" }
        val initialDiscard = drawPile.removeAt(initialIndex)
        if (initialIndex != drawPile.size) drawPile.shuffle(random)
        discardPile += initialDiscard
        activeColor = initialDiscard.color
        applyInitialDiscard(initialDiscard, startingSeat)
    }

    private fun applyInitialDiscard(card: UnoCard, startingSeat: Int) {
        when (card.type) {
            UnoCardType.NUMBER -> setTurn(startingSeat)
            UnoCardType.SKIP -> setTurn(advanceSeat(startingSeat, 1))
            UnoCardType.REVERSE -> {
                if (players.size == 2) {
                    setTurn(advanceSeat(startingSeat, 1))
                } else {
                    direction = direction.reversed()
                    setTurn(startingSeat)
                }
            }

            UnoCardType.DRAW_TWO -> {
                drawCards(startingSeat, 2)
                setTurn(advanceSeat(startingSeat, 1))
            }

            UnoCardType.WILD -> {
                phase = UnoPhase.CHOOSE_COLOR
                currentSeat = startingSeat
                colorChooserPlayerId = players[startingSeat].playerId
                colorChoiceStartsTurn = true
                pendingWildEffect = UnoCardType.WILD
            }

            UnoCardType.WILD_DRAW_FOUR -> check(false) { "Wild Draw Four cannot be the initial discard" }
        }
    }

    private fun hasDrawableCard(): Boolean = drawPile.isNotEmpty() || discardPile.size > 1

    private fun drawOne(): UnoCard? {
        if (drawPile.isEmpty()) recycleDiscardPile()
        return if (drawPile.isEmpty()) null else drawPile.removeAt(drawPile.lastIndex)
    }

    private fun drawCards(seat: Int, count: Int) {
        repeat(count) {
            val card = drawOne() ?: return
            players[seat].hand += card
        }
    }

    private fun recycleDiscardPile() {
        if (discardPile.size <= 1) return
        val top = discardPile.removeAt(discardPile.lastIndex)
        val recycled = discardPile.toMutableList()
        discardPile.clear()
        discardPile += top
        recycled.shuffle(random)
        drawPile += recycled
    }

    private fun closePreviousCatchWindow() {
        catchWindow = null
    }

    private fun setTurn(seat: Int) {
        currentSeat = seat
        phase = UnoPhase.TURN
    }

    private fun nextSeat(fromSeat: Int): Int = advanceSeat(fromSeat, 1)

    private fun advanceSeat(fromSeat: Int, steps: Int): Int {
        val delta = if (direction == UnoDirection.CLOCKWISE) steps else -steps
        return ((fromSeat + delta) % players.size + players.size) % players.size
    }

    private fun UnoDirection.reversed(): UnoDirection =
        if (this == UnoDirection.CLOCKWISE) UnoDirection.COUNTER_CLOCKWISE else UnoDirection.CLOCKWISE

    private fun snapshot(): UnoGameState = UnoGameState(
        players = players.map {
            UnoPlayerState(it.playerId, it.playerName, it.seat, it.hand.toList(), it.score)
        },
        currentPlayerId = currentSeat?.let { players[it].playerId },
        direction = direction,
        drawPile = drawPile.toList(),
        discardPile = discardPile.toList(),
        activeColor = activeColor,
        phase = phase,
        roundNumber = roundNumber,
        baseStartingSeat = baseStartingSeat,
        scores = scores.toMap(),
        unoDeclaredPlayerId = unoDeclaredPlayerId,
        catchWindow = catchWindow,
        drawnCardId = drawnCardId,
        colorChooserPlayerId = colorChooserPlayerId,
        colorChoiceStartsTurn = colorChoiceStartsTurn,
        pendingRoundWinnerId = pendingRoundWinnerId,
        roundWinnerId = roundWinnerId,
        matchWinnerId = matchWinnerId,
        matchMode = matchMode,
        targetScore = targetScore,
        lastRoundScore = lastRoundScore,
    )

    private fun loadState(state: UnoGameState) {
        check(state.players.map { it.playerId } == players.map { it.playerId }) {
            "State players must match engine players"
        }
        players.forEachIndexed { index, player ->
            player.hand += state.players[index].hand
            player.score = state.players[index].score
        }
        scores += state.scores
        drawPile += state.drawPile
        discardPile += state.discardPile
        currentSeat = state.currentPlayerId?.let { id -> players.indexOfFirst { it.playerId == id } }
        direction = state.direction
        activeColor = state.activeColor
        phase = state.phase
        roundNumber = state.roundNumber
        baseStartingSeat = state.baseStartingSeat
        unoDeclaredPlayerId = state.unoDeclaredPlayerId
        catchWindow = state.catchWindow
        drawnCardId = state.drawnCardId
        colorChooserPlayerId = state.colorChooserPlayerId
        colorChoiceStartsTurn = state.colorChoiceStartsTurn
        pendingRoundWinnerId = state.pendingRoundWinnerId
        pendingWildEffect = if (state.phase == UnoPhase.CHOOSE_COLOR) {
            state.discardPile.lastOrNull()?.type
        } else {
            null
        }
        roundWinnerId = state.roundWinnerId
        matchWinnerId = state.matchWinnerId
        lastRoundScore = state.lastRoundScore
    }

    private fun success(): UnoActionResult = UnoActionResult(true, snapshot())

    private fun failure(code: UnoErrorCode, message: String): UnoActionResult =
        UnoActionResult(false, snapshot(), UnoError(code, message))

    companion object {
        fun start(
            players: List<UnoPlayer>,
            random: Random = Random.Default,
            matchMode: UnoMatchMode = UnoMatchMode.QUICK,
            targetScore: Int = 500,
        ): UnoStartResult {
            if (players.size !in 2..4) {
                return UnoStartResult(
                    error = UnoError(UnoErrorCode.INVALID_PLAYER_COUNT, "UNO requires 2 to 4 players"),
                )
            }
            if (players.map { it.playerId }.any { it.isBlank() } ||
                players.map { it.playerId }.distinct().size != players.size
            ) {
                return UnoStartResult(
                    error = UnoError(UnoErrorCode.DUPLICATE_PLAYER_ID, "Player IDs must be unique and non-blank"),
                )
            }
            if (targetScore <= 0) {
                return UnoStartResult(
                    error = UnoError(UnoErrorCode.INVALID_TARGET_SCORE, "Target score must be positive"),
                )
            }
            return UnoStartResult(UnoEngine(players, random, matchMode, targetScore))
        }

        internal fun fromState(state: UnoGameState, random: Random = Random(0)): UnoEngine {
            val definitions = state.players.sortedBy { it.seat }.map { UnoPlayer(it.playerId, it.playerName) }
            return UnoEngine(definitions, random, state.matchMode, state.targetScore, state)
        }
    }
}
