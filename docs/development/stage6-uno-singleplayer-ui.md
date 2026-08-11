# 阶段6：UNO单机Compose可玩UI

## 页面流程

`游戏选择 → UNO首页 → 单机配置 → UNO牌桌`。UNO首页提供可用的“单机游戏”和禁用的“局域网游戏·敬请期待”。配置页支持2～4名总玩家、Quick一局决胜或500分Points；固定1名真人“你”，其余为NORMAL机器人。

## Controller与ViewModel

`UnoGameViewModel`由Activity的ViewModelStore持有，横屏重组不会重建牌局。它把UI事件委托给纯Kotlin `UnoSinglePlayerController`，并暴露`StateFlow<UnoUiState>`。退出牌局时Controller取消当前动作链并清空状态；阶段6不持久化进程死亡后的牌局。

Controller负责创建`UnoEngine`和机器人、接收真人`UnoAction`、串行调用Engine、调度Bot连续动作、处理Round/Match结束并刷新UI状态。Composable不持有Engine，也不能移牌、改颜色或推进回合。

## UnoUiState与公平信息

UI状态包含真人完整手牌、每名玩家名称/座位/剩余牌数/分数/当前与胜者标记、弃牌顶牌、牌堆数量、activeColor、方向、phase、局数、合法牌ID、Engine提供的可用动作、UNO抓取目标、错误和事件提示。

机器人只显示背面牌与`remainingCardCount`。Bot具体手牌、牌色、牌型和`cardId`不进入`UnoUiPlayer`。核心的`viewFor`仅增加通用公开字段`drawPileCount`和`lastRoundScore`，没有向UI泄漏隐藏牌。

## Engine调用边界

合法牌和按钮状态只来自`legalPlayableCards`与`availableActions`。出牌、摸牌、不出、DeclareUno、CatchUno、ChooseColor和StartNextRound全部调用`UnoEngine.applyAction`。UI和Controller没有复制颜色/数字匹配、Skip、Reverse、Draw Two、Wild Draw Four或UNO窗口规则。

## Bot调度、延迟与串行

Controller维护唯一`actionChainJob`和一个`Mutex`。真人动作提交后，同一协程链持续处理Bot的DeclareUno→PlayCard、Wild→ChooseColor或Draw→PlayDrawnCard，直到轮到真人或Round/Match结束。重复schedule和快速连点在旧动作链完成前会被拒绝。

`UnoBotDelayProvider`可注入：生产ViewModel使用450ms非阻塞协程延迟，JVM测试使用Immediate。延迟不进入Engine或UnoBot。

## 牌桌交互

- 手牌使用`LazyRow`；合法牌正常显示，非法牌降低透明度并禁用。
- TURN显示摸牌与按需出现的UNO按钮；AFTER_DRAW只允许刚摸到的合法牌，并显示“不出”。
- Wild/Wild Draw Four进入不可点外部关闭的四色选择Dialog。
- Catch窗口存在时显示“抓UNO！”，成功提示目标罚摸2张；Bot宣告以非阻塞事件文本显示。
- 顶部持续显示当前行动者、activeColor和顺/逆时针方向。
- Quick结束可重开；Points Round结束显示本局得分、累计排行榜和“下一局”，达到500分后显示整场排行榜。

## 自动测试

- 阶段6新增：36项；
- 原测试：226/226；总测试：262/262；
- Controller Quick：1000/1000局；Controller Points：100/100场；
- UNO随机：2000/2000；UNO Bot Quick：5000/5000；UNO Bot Points：500/500场；斗地主：2000/2000；
- Android Debug：BUILD SUCCESSFUL。

测试覆盖2/3/4人配置、固定单真人、Quick/Points、真人动作、Bot自动与连续动作、Wild选色、宣告UNO、Round/Match停止、下一局、重复调度、UI映射、隐藏手牌隔离与导航。

## 开发环境已知问题

在 Windows 中文仓库路径下直接运行 Gradle JVM 测试，测试运行器可能在加载测试类时报告 `ClassNotFoundException`。这是已知的构建环境路径问题，不代表 UNO 功能断言失败。使用指向同一仓库的 ASCII junction 路径 `C:\Users\Administrator\AppData\Local\Temp\offline-landlord-build` 执行相同测试，可正常完成全部测试。

## 真机与下一阶段

人工清单位于`docs/release/stage6-uno-singleplayer-device-test.md`。本阶段不伪造真机结果，清单状态默认待验收。

阶段7目标为UNO V5局域网联机，届时才允许UNO房间、权威Host会话、2～4真人/Bot混合、断线接管和重连恢复。阶段6没有新增LAN或V5实现。
