# 阶段5：UNO NORMAL机器人

## 目标与边界

阶段5在阶段4的UNO纯Kotlin核心上增加第一版正式机器人。机器人只负责从核心已经判定合法的动作中做选择；状态变更、出牌合法性、回合推进、Wild Draw Four限制、计分和下一局发牌仍完全由`UnoEngine`负责。

本阶段没有新增UNO Compose牌桌、单机入口、房间、LAN、V5 adapter或真人联机，也没有修改斗地主业务、V4/V5公共协议、应用ID、版本号或SDK。应用中的UNO仍显示“开发中”，当前仍不可游玩。

## 结构

- `UnoBot`：按玩家ID从Engine取得公平视图、可用动作与合法牌，调用策略选择，并在返回前再次确认动作属于Engine许可集合。
- `UnoBotObservation`：机器人决策使用的只读公平观察。
- `UnoBotStrategy`：可扩展策略边界；阶段5只提供`NormalUnoBotStrategy`，没有EASY、HARD或EXPERT。
- `UnoBotGameRunner`：纯Kotlin调度器，推进全机器人或人机混合牌局，不判断UNO规则。

## 公平信息边界

`UnoEngine.viewFor(playerId)`提供通用玩家视图。本人可见自己的完整手牌；每名玩家的公共字段只有玩家ID、名称、座位、剩余牌数和分数。公共牌局信息包括当前玩家、方向、活动颜色、弃牌顶牌、阶段、局数、分数、UNO抓取目标和比赛结果。

其他玩家的实际手牌、牌色、牌型和`cardId`不会进入`UnoGameView`或`UnoBotObservation`。`drawnCardId`只对当前摸牌玩家本人可见。测试用两个只改变对手隐藏牌面、但保持所有公共信息和手牌数量一致的状态，证明观察完全相同，固定seed下决策也相同。

## Engine合法动作边界

机器人只接收：

- `UnoEngine.availableActions(playerId)`返回的动作类型；
- `UnoEngine.legalPlayableCards(playerId)`返回的合法牌。

`UnoBot`会拒绝策略返回的非许可动作、非本人牌ID、非合法牌ID或错误抓取目标。正式机器人没有调用`UnoRules`，没有复制同色/同数字规则，也没有自行实现Wild Draw Four合法性判断。所有选择最终仍通过`UnoEngine.applyAction`执行，机器人不直接修改`UnoGameState`。

## NORMAL策略

NORMAL使用轻量、接近O(手牌数)的启发式：

1. 有合法抓UNO动作时优先抓取。
2. Wild等待选色时，按自己剩余手牌的颜色数量选最多色；数量并列时比较该色牌面总分，仍并列时使用注入Random。
3. Points局结束且Engine允许时开始下一局。
4. 自己有2张牌且准备合法出牌时，先`DeclareUno`，Runner随后允许同一机器人继续`PlayCard`。
5. 下一名玩家只剩2张或更少时，提高Draw Two、Skip、Wild Draw Four和Reverse等阻断牌优先级。
6. 安全局面优先处理高分普通牌并保持手中优势颜色；尽量保留Wild和Wild Draw Four。
7. 无合法出牌时摸牌。AFTER_DRAW通常打出刚摸到的普通牌；安全局面可保留Wild/Wild Draw Four并`PassAfterDraw`，危险局面则打出。

二人局Reverse的实际效果完全由Engine处理；Bot只将已被Engine列为合法的Reverse作为可能的阻断选择。

## Random注入与确定性

`UnoBot`构造时接受`kotlin.random.Random`，并把它交给NORMAL策略用于完全同分候选的tie-break。生产调用可以使用默认随机源，测试传入固定seed。相同观察、相同合法动作顺序和相同seed会得到相同决策序列。

## 人机混合准备

`UnoBotGameRunner`只接管明确注册为Bot的玩家ID，不假设所有座位都是机器人。轮到没有注册Bot的玩家时返回`WAITING_FOR_EXTERNAL_PLAYER`，等待未来UI或控制器提交外部动作。网络断线、`resumeToken`和自动托管不属于阶段5。

Runner支持同一Bot连续执行`DeclareUno`再出牌、优先处理合法`CatchUno`、在Points局`ROUND_FINISHED`时执行`StartNextRound`，并通过最大动作数返回`ACTION_LIMIT_REACHED`，使死循环在测试中明确失败，而不是修改生产Engine强制终局。

## 自动牌局与测试结果

提交前强制重跑结果：

- 原有自动测试：185/185通过；
- 阶段5新增自动测试：41/41通过；
- 总自动测试：226/226通过，0失败、0错误、0跳过；
- 阶段4 UNO随机合法动作：2000/2000局通过；
- UNO Bot Quick：5000/5000局通过，覆盖2、3、4名Bot；
- UNO Bot Points：500/500场500分比赛通过，覆盖2～4名Bot；
- 斗地主机器人：2000/2000局通过；
- Android Debug：`BUILD SUCCESSFUL`。

完整牌局均要求自然结束并产生胜者。Bot压力测试每个动作后检查经典108张牌守恒和唯一`cardId`；Points比赛还检查`matchWinner`、至少一名玩家达到500分、累计分及跨局推进。

## 阶段6目标

下一阶段是UNO单机Compose UI：从GameSelection进入UNO单机，配置2～4名玩家，支持真人加Bot，并提供完整可玩牌桌。阶段6暂不接LAN，阶段5完成后不提前开发该界面。
