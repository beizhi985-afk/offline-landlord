# 阶段4：UNO 纯 Kotlin 核心引擎

## 目标与边界

阶段4建立了独立的 com.offlinelandlord.game.uno.core 规则引擎。它只依赖 Kotlin/JVM，不导入 Android、Compose、斗地主核心、LAN transport、V4/V5 协议或任何网络实现。

本阶段没有把 UNO 接入应用页面。游戏选择页中的 UNO 仍显示“开发中”；没有 UNO 房间、网络 adapter、机器人、AI 或策略类。

## UNO V1 冻结规则

- 玩家数为 2～4 人。
- 使用经典 108 张牌：四种颜色各含一个 0、两个 1～9、两个 Skip、两个 Reverse、两个 Draw Two，另有 4 张 Wild 和 4 张 Wild Draw Four。
- 每张牌都有稳定且唯一的 cardId，完全相同的实体牌仍可被单独识别。
- 不支持 +2/+4 叠加、Jump-In、多牌齐出、7-0、摸到能出为止、换手或任何自定义牌。
- Wild Draw Four 仅在手中没有与当前 activeColor 相同颜色的牌时合法；不实现 +4 挑战。
- 玩家正常回合即使有合法牌也可主动摸 1 张。摸到可出的牌后只能立即出这张牌或放弃；摸到不可出的牌自动结束回合。
- 普通牌可按当前颜色、相同数字或相同功能符号匹配；Wild 始终可出，Wild Draw Four 额外受颜色限制。
- activeColor 独立于弃牌堆顶牌保存，Wild 选色后才更新。

## 状态机

核心阶段为 TURN、AFTER_DRAW、CHOOSE_COLOR、ROUND_FINISHED 和 MATCH_FINISHED。积分赛在 ROUND_FINISHED 后可开始新一局。

所有状态变化只通过 UnoEngine.applyAction(playerId, action) 发生。动作包括 PlayCard、DrawCard、PlayDrawnCard、PassAfterDraw、DeclareUno、CatchUno、ChooseColor 和 StartNextRound。

非法动作返回 UnoActionResult 与明确的 UnoErrorCode，不会把规则错误当异常抛出。只有内部不变量被破坏时才使用异常。

UnoGameState 明确保存玩家与手牌、当前玩家、方向、摸牌堆、弃牌堆、活动颜色、阶段、局数、累计分、UNO 宣告、抓 UNO 窗口、摸到的牌、选色玩家以及单局/整场胜者。

## 功能牌与回合推进

- Skip 跳过下一名玩家。
- 3～4 人 Reverse 翻转方向后按新方向推进。
- 2 人 Reverse 等同 Skip，出牌者再次行动。
- Draw Two 让下一名玩家立即摸 2 张并跳过。
- Wild 由出牌者选择四种颜色之一，再推进回合。
- Wild Draw Four 先选色，再让下一名玩家摸 4 张并跳过。
- 首张 Skip 跳过首位基准玩家；首张 Reverse 改变初始方向，2 人局按 Skip 等效；首张 Draw Two 让基准玩家摸 2 张并跳过；首张 Wild 由基准玩家选色后仍由其开始。
- Wild Draw Four 不允许成为首张弃牌。引擎从牌堆中安全选择其他牌并重新洗匀剩余牌，避免重抽死循环。
- 最后一张 Draw Two 与 Wild Draw Four 会先执行罚牌，再按罚牌后的手牌计分。
- 最后一张普通 Wild 直接结束本局，不产生无意义的选色等待。

回合位置计算集中在 advanceSeat、nextSeat 和 setTurn，功能牌不会各自直接修改索引。

## UNO 宣告与抓 UNO

当前玩家仅能在 TURN 且手牌恰好为 2 张时执行 DeclareUno。正确宣告后出到 1 张不会打开抓牌窗口。

未宣告而出到 1 张时建立 UnoCatchWindow。其他玩家可以执行 CatchUno(targetPlayerId)；不能抓自己。成功后目标摸 2 张，窗口立即关闭。

抓牌窗口不依赖系统时间。它一直保留到下一位应行动玩家的第一个有效 DeclareUno、PlayCard 或 DrawCard；引擎在执行该有效动作前关闭旧窗口。非法动作不会误关窗口。

## 牌堆与守恒

摸牌堆为空时，引擎保留弃牌堆顶牌，把其余弃牌使用注入的 Random 重新洗入摸牌堆。原 cardId 不变，不复制、不丢失。

生产牌局和随机压力测试均检查 108 张牌只存在于一个位置：某位玩家手牌、摸牌堆或弃牌堆。

## Round、Match 与计分

- QUICK：一局结束即结束整场。
- POINTS：默认目标为 500 分。每局胜者获得其他玩家剩余手牌总分；未达到目标时可执行 StartNextRound。
- 数字牌按面值计分；Skip、Reverse、Draw Two 各 20 分；Wild、Wild Draw Four 各 50 分。
- 下一积分局重新创建并洗匀完整 108 张牌，清空手牌、牌堆、活动颜色、宣告与抓牌窗口，只保留累计分。
- 第一局基准起始座位由注入的 Random 决定；后续积分局按座位顺时针轮换一位。

## 合法动作查询

UnoEngine.legalPlayableCards、canPlayCard 与 availableActions 复用 UnoRules 的唯一合法性判断。未来 UI、网络验证和阶段5机器人必须调用这些入口，禁止复制一套规则。

## 测试与验证

- 阶段4新增 92 项 UNO JVM 测试，全部通过。
- 原 93 项斗地主、V4/V5、transport 与导航测试全部保留，完整测试共 185/185 通过，0 失败、0 错误、0 跳过。
- UNO 测试覆盖牌组分布、唯一 ID、2～4 人开局、固定随机数、首张功能牌、匹配与 +4 限制、摸牌后阶段、功能牌、宣告与抓牌、结束罚牌、计分、Quick/500 分模式、下一局轮换、重洗和状态不变量。
- 测试目录的随机合法动作驱动完成 2000/2000 局快速对局；每步只使用引擎的合法动作查询，并检查自然结束、胜者和 108 张牌守恒。
- 斗地主现有机器人压力入口以 BOT_SIMULATION_GAMES=2000 强制执行，2000/2000 局通过。
- Android Debug 完整构建通过。

## 尚未实现与下一阶段

阶段4没有创建 UnoBot、UnoAi、UnoStrategy，也没有 UNO UI、房间、局域网会话或 V5 adapter。

阶段5目标是 UNO 机器人。机器人必须复用 UnoEngine、UnoRules 和 legalPlayableCards，不得复制牌面匹配、+4 合法性、功能牌或回合推进规则。阶段5尚未开始。
