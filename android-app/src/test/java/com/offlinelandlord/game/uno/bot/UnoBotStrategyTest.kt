package com.offlinelandlord.game.uno.bot

import com.offlinelandlord.game.uno.core.UnoAction
import com.offlinelandlord.game.uno.core.UnoActionType
import com.offlinelandlord.game.uno.core.UnoCard
import com.offlinelandlord.game.uno.core.UnoCardType
import com.offlinelandlord.game.uno.core.UnoColor
import com.offlinelandlord.game.uno.core.UnoMatchMode
import com.offlinelandlord.game.uno.core.UnoPhase
import com.offlinelandlord.game.uno.core.UnoTestFixtures.action
import com.offlinelandlord.game.uno.core.UnoTestFixtures.engine
import com.offlinelandlord.game.uno.core.UnoTestFixtures.number
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wild
import com.offlinelandlord.game.uno.core.UnoTestFixtures.wildDrawFour
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoBotStrategyTest {
    private val top = number("top", UnoColor.RED, 2)

    @Test
    fun botCatchesOpenUnoWindowWithPriority() {
        val game = engine(
            hands = listOf(
                listOf(number("p0", UnoColor.BLUE, 3)),
                listOf(number("caught", UnoColor.GREEN, 1)),
            ),
            top = top,
            catchTargetId = "p1",
        )
        assertEquals(UnoAction.CatchUno("p1"), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botCannotCatchItself() {
        val game = engine(
            hands = listOf(
                listOf(number("caught", UnoColor.BLUE, 3)),
                listOf(number("p1", UnoColor.GREEN, 1)),
            ),
            top = top,
            catchTargetId = "p0",
        )
        assertFalse(UnoActionType.CATCH_UNO in game.availableActions("p0"))
        assertFalse(UnoBot("p0", Random(1)).chooseAction(game) is UnoAction.CatchUno)
    }

    @Test
    fun botDeclaresUnoBeforePlayingFromTwoCards() {
        val game = twoCardGame()
        assertEquals(UnoAction.DeclareUno, UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botPlaysAfterItsDeclaration() {
        val game = twoCardGame()
        val bot = UnoBot("p0", Random(1))
        assertTrue(game.applyAction("p0", requireNotNull(bot.chooseAction(game))).success)
        val second = bot.chooseAction(game)
        assertTrue(second is UnoAction.PlayCard)
        assertTrue(game.applyAction("p0", requireNotNull(second)).success)
        assertNull(game.state.catchWindow)
    }

    @Test
    fun botChoosesRedWhenRedIsMostCommon() {
        val game = colorChoiceGame(
            listOf(
                number("r1", UnoColor.RED, 1),
                number("r2", UnoColor.RED, 7),
                number("b1", UnoColor.BLUE, 9),
            ),
        )
        assertEquals(UnoAction.ChooseColor(UnoColor.RED), UnoBot("p0", Random(2)).chooseAction(game))
    }

    @Test
    fun botChoosesBlueWhenBlueIsMostCommon() {
        val game = colorChoiceGame(
            listOf(
                number("b1", UnoColor.BLUE, 1),
                number("b2", UnoColor.BLUE, 7),
                number("r1", UnoColor.RED, 9),
            ),
        )
        assertEquals(UnoAction.ChooseColor(UnoColor.BLUE), UnoBot("p0", Random(2)).chooseAction(game))
    }

    @Test
    fun colorCountTieUsesHigherRemainingColorValue() {
        val game = colorChoiceGame(
            listOf(
                number("r9", UnoColor.RED, 9),
                number("b1", UnoColor.BLUE, 1),
            ),
        )
        assertEquals(UnoAction.ChooseColor(UnoColor.RED), UnoBot("p0", Random(2)).chooseAction(game))
    }

    @Test
    fun colorTieBreakIsStableForFixedSeed() {
        val first = UnoBot("p0", Random(123)).chooseAction(colorChoiceGame(listOf(wild("only-wild"))))
        val second = UnoBot("p0", Random(123)).chooseAction(colorChoiceGame(listOf(wild("only-wild"))))
        assertEquals(first, second)
        assertTrue(first is UnoAction.ChooseColor)
    }

    @Test
    fun botDoesNotActNormallyOutsideItsTurn() {
        val game = engine(
            listOf(
                listOf(number("p0", UnoColor.RED, 5)),
                listOf(number("p1", UnoColor.BLUE, 1)),
            ),
            top,
            currentSeat = 1,
        )
        assertNull(UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botReturnsNullAfterMatchIsFinished() {
        val game = engine(
            hands = listOf(emptyList(), listOf(number("p1", UnoColor.BLUE, 1))),
            top = top,
            currentSeat = null,
            phase = UnoPhase.MATCH_FINISHED,
            roundWinnerId = "p0",
            matchWinnerId = "p0",
        )
        assertNull(UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botDrawsWhenNoCardIsPlayable() {
        val game = engine(
            listOf(
                listOf(number("blocked", UnoColor.BLUE, 8)),
                listOf(number("p1", UnoColor.YELLOW, 1)),
            ),
            top,
        )
        assertEquals(UnoAction.DrawCard, UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun maliciousStrategyCannotReturnOpponentsCardId() {
        val strategy = fixedStrategy(UnoAction.PlayCard("opponent-card"))
        val game = engine(
            listOf(
                listOf(number("own", UnoColor.RED, 5)),
                listOf(number("opponent-card", UnoColor.RED, 7)),
            ),
            top,
        )
        assertNull(UnoBot("p0", Random(1), strategy).chooseAction(game))
    }

    @Test
    fun maliciousStrategyCannotReturnUnavailableAction() {
        val game = engine(
            listOf(
                listOf(number("blocked", UnoColor.BLUE, 8)),
                listOf(number("p1", UnoColor.YELLOW, 1)),
            ),
            top,
        )
        assertNull(UnoBot("p0", Random(1), fixedStrategy(UnoAction.PlayCard("blocked"))).chooseAction(game))
    }

    @Test
    fun botUsesWildDrawFourOnlyWhenEngineListsIt() {
        val four = wildDrawFour()
        val game = engine(
            listOf(
                listOf(four, number("blue", UnoColor.BLUE, 8), number("green", UnoColor.GREEN, 9)),
                manyCards("p1", 5),
            ),
            top,
        )
        assertEquals(listOf(four), game.legalPlayableCards("p0"))
        assertEquals(UnoAction.PlayCard(four.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botNeverReimplementsIllegalWildDrawFour() {
        val four = wildDrawFour()
        val red = number("red", UnoColor.RED, 9)
        val game = engine(
            listOf(listOf(four, red, number("blue", UnoColor.BLUE, 8)), manyCards("p1", 5)),
            top,
        )
        assertFalse(game.canPlayCard("p0", four.cardId))
        assertEquals(UnoAction.PlayCard(red.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botAttacksOneCardOpponentWithSkip() {
        val skip = action("skip", UnoColor.RED, UnoCardType.SKIP)
        val ordinary = number("ordinary", UnoColor.RED, 9)
        val game = engine(
            listOf(
                listOf(skip, ordinary, number("extra", UnoColor.BLUE, 4)),
                listOf(number("danger", UnoColor.GREEN, 1)),
                manyCards("p2", 4),
            ),
            top,
        )
        assertEquals(UnoAction.PlayCard(skip.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botAttacksOneCardOpponentWithDrawTwo() {
        val drawTwo = action("draw-two", UnoColor.RED, UnoCardType.DRAW_TWO)
        val ordinary = number("ordinary", UnoColor.RED, 9)
        val game = engine(
            listOf(
                listOf(drawTwo, ordinary, number("extra", UnoColor.BLUE, 4)),
                listOf(number("danger", UnoColor.GREEN, 1)),
                manyCards("p2", 4),
            ),
            top,
        )
        assertEquals(UnoAction.PlayCard(drawTwo.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botDiscardsClearlyHigherValueCardWhenNoOpponentIsDangerous() {
        val reverse = action("reverse", UnoColor.RED, UnoCardType.REVERSE)
        val low = number("low", UnoColor.RED, 1)
        val game = engine(
            listOf(listOf(reverse, low, number("extra", UnoColor.BLUE, 4)), manyCards("p1", 5)),
            top,
        )
        assertEquals(UnoAction.PlayCard(reverse.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botPrefersItsStrongerColorWhenValuesTie() {
        val red = number("red-five", UnoColor.RED, 5)
        val blue = number("blue-five", UnoColor.BLUE, 5)
        val game = engine(
            listOf(
                listOf(red, blue, number("red-seven", UnoColor.RED, 7), number("red-eight", UnoColor.RED, 8)),
                manyCards("p1", 5),
            ),
            number("yellow-five", UnoColor.YELLOW, 5),
            activeColor = UnoColor.YELLOW,
        )
        assertEquals(UnoAction.PlayCard(red.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun botConservesWildWhenOrdinaryCardIsAvailable() {
        val ordinary = number("ordinary", UnoColor.RED, 1)
        val game = engine(
            listOf(listOf(wild(), ordinary, number("extra", UnoColor.BLUE, 4)), manyCards("p1", 5)),
            top,
        )
        assertEquals(UnoAction.PlayCard(ordinary.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun afterDrawBotPlaysOrdinaryDrawnCard() {
        val drawn = number("drawn", UnoColor.RED, 5)
        val game = afterDrawGame(drawn, nextPlayerCount = 5)
        assertEquals(UnoAction.PlayDrawnCard(drawn.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun afterDrawBotPassesToSaveWildWhenSituationIsSafe() {
        val drawn = wild("drawn-wild")
        val game = afterDrawGame(drawn, nextPlayerCount = 5)
        assertEquals(UnoAction.PassAfterDraw, UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun afterDrawBotUsesWildAgainstDangerousOpponent() {
        val drawn = wild("drawn-wild")
        val game = afterDrawGame(drawn, nextPlayerCount = 1)
        assertEquals(UnoAction.PlayDrawnCard(drawn.cardId), UnoBot("p0", Random(1)).chooseAction(game))
    }

    @Test
    fun equalCardTieBreakIsStableForFixedSeed() {
        val firstCard = number("first", UnoColor.RED, 5)
        val secondCard = number("second", UnoColor.RED, 5)
        fun game() = engine(
            listOf(listOf(firstCard, secondCard, number("extra", UnoColor.BLUE, 4)), manyCards("p1", 5)),
            top,
        )
        assertEquals(
            UnoBot("p0", Random(123)).chooseAction(game()),
            UnoBot("p0", Random(123)).chooseAction(game()),
        )
    }

    private fun twoCardGame() = engine(
        listOf(
            listOf(number("playable", UnoColor.RED, 5), number("remaining", UnoColor.BLUE, 8)),
            manyCards("p1", 4),
        ),
        top,
    )

    private fun colorChoiceGame(hand: List<UnoCard>) = engine(
        hands = listOf(hand, manyCards("p1", 4)),
        top = wild("top-wild"),
        phase = UnoPhase.CHOOSE_COLOR,
        currentSeat = 0,
        activeColor = UnoColor.RED,
        colorChooserId = "p0",
    )

    private fun afterDrawGame(drawn: UnoCard, nextPlayerCount: Int) = engine(
        hands = listOf(
            listOf(drawn, number("old-a", UnoColor.BLUE, 3), number("old-b", UnoColor.GREEN, 4)),
            manyCards("p1", nextPlayerCount),
        ),
        top = top,
        phase = UnoPhase.AFTER_DRAW,
        currentSeat = 0,
        drawnCardId = drawn.cardId,
    )

    private fun manyCards(prefix: String, count: Int): List<UnoCard> =
        (0 until count).map { number("$prefix-$it", UnoColor.YELLOW, it % 10) }

    private fun fixedStrategy(action: UnoAction) = object : UnoBotStrategy {
        override fun chooseAction(
            observation: UnoBotObservation,
            availableActions: Set<UnoActionType>,
            legalPlayableCards: List<UnoCard>,
        ): UnoAction = action
    }
}
