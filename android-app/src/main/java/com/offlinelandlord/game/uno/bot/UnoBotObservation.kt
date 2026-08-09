package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoDirection
import com.offlinelandlord.game.uno.core.UnoGameView
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase

data class UnoBotPlayerObservation(
    val playerId: String,
    val playerName: String,
    val seat: Int,
    val remainingCardCount: Int,
    val score: Int,
)

data class UnoBotObservation(
    val selfPlayerId: String,
    val ownHand: List<UnoCard>,
    val players: List<UnoBotPlayerObservation>,
    val currentPlayerId: String?,
    val activeColor: UnoColor?,
    val topDiscardCard: UnoCard,
    val direction: UnoDirection,
    val phase: UnoPhase,
    val scores: Map<String, Int>,
    val roundNumber: Int,
    val matchMode: UnoMatchMode,
    val targetScore: Int,
    val catchTargetPlayerId: String?,
    val hasDeclaredUno: Boolean,
    val drawnCardId: String?,
    val colorChooserPlayerId: String?,
) {
    companion object {
        fun from(view: UnoGameView): UnoBotObservation = UnoBotObservation(
            selfPlayerId = view.selfPlayerId,
            ownHand = view.ownHand,
            players = view.players.map {
                UnoBotPlayerObservation(
                    playerId = it.playerId,
                    playerName = it.playerName,
                    seat = it.seat,
                    remainingCardCount = it.remainingCardCount,
                    score = it.score,
                )
            },
            currentPlayerId = view.currentPlayerId,
            activeColor = view.activeColor,
            topDiscardCard = view.topDiscardCard,
            direction = view.direction,
            phase = view.phase,
            scores = view.scores,
            roundNumber = view.roundNumber,
            matchMode = view.matchMode,
            targetScore = view.targetScore,
            catchTargetPlayerId = view.catchTargetPlayerId,
            hasDeclaredUno = view.unoDeclaredPlayerId == view.selfPlayerId,
            drawnCardId = view.drawnCardId,
            colorChooserPlayerId = view.colorChooserPlayerId,
        )
    }
}
