# 阶段3：V5 多游戏网络协议

## 目标与范围

阶段3在阶段2已建立的公共 TCP / UDP transport 之上增加多游戏协议能力。斗地主接入 V5，同时保留 V3.7 的 V4 斗地主兼容层。

本阶段只实现协议能力。`GameType.UNO` 可以被 V5 识别，但没有 UNO 房主会话、客户端、牌组、规则、机器人或网络入口。收到 `gameType = UNO` 的 V5 JOIN 时，斗地主房主会返回明确的“不支持”错误并断开连接。

## V4 与 V5 的关系

- V4 是既有斗地主专用兼容协议，`WireEnvelope`、`WireType`、`WIRE_PROTOCOL_VERSION = 4`、`DDZ_DISCOVER_V4` 及 `DDZ_HOST_V4` 文本格式均保持不变。
- V5 是新建的多游戏协议，默认由新版斗地主客户端使用。
- 新版斗地主房主同时接受 V4 与 V5；每条已建立连接都记录自身协议版本，状态广播和错误响应按该连接的版本编码。
- V4 后续 ACTION、STATE、ERROR、PING/PONG 历史上常带 `protocolVersion = 0`。因此只用首个 JOIN 的版本识别协议，后续消息按连接元数据处理，避免误伤 V3.7 客户端。

没有删除 V4 的原因是：已安装的 V3.7 客户端仍需能发现新房主并加入斗地主房间。新功能不应把协议升级变成强制同时更新三台设备的风险。

## 中立 GameType

`GameType` 已从 UI 导航包移动到：

```text
android-app/src/main/java/com/offlinelandlord/game/shared/GameType.kt
```

它只包含可序列化的 `LANDLORD` 与 `UNO`，不依赖卡牌、`GameEngine`、Compose 或网络实现。应用壳与 V5 通用协议都可以依赖它。

## V5 通用信封

V5 通用代码位于：

```text
network/protocol/v5/V5WireProtocol.kt
```

`V5WireEnvelope` 字段为：

```text
gameType, type, protocolVersion = 5,
requestId, playerId, roomCode, resumeToken,
expectedRevision, payload, message
```

通用消息类型是 `JOIN`、`JOIN_ACCEPTED`、`ACTION`、`STATE`、`ERROR`、`PING`、`PONG`。`payload` 使用 `JsonElement`。V5 通用包只依赖 `GameType` 和 kotlinx.serialization；它不导入 `PlayerAction`、`PlayerGameView`、`Card`、`PlayerRole`、斗地主 core 或 Compose。

`V5ProtocolCodec` 对错误 JSON、缺失必需字段、未知 `gameType`、未知 `type` 和非 5 的版本安全返回失败，不会让网络协程因解析异常而崩溃。

## 斗地主 V5 adapter

斗地主专属内容位于：

```text
network/landlord/v5/LandlordV5PayloadCodec.kt
network/landlord/v5/LandlordV5DiscoveryConfig.kt
```

- JOIN payload：`LandlordV5JoinPayload(playerName)`。
- JOIN_ACCEPTED payload：`LandlordV5JoinAcceptedPayload(view)`。
- ACTION payload：斗地主 `PlayerAction` JSON。
- STATE 与携带视图的 ERROR payload：斗地主 `PlayerGameView` JSON。
- UDP `gameConfig`：斗地主 `totalRounds` 与 `doublingEnabled`，不会提升到通用广告一级字段。

`LanGameClient` 新建时默认选用 V5；手工输入 IP + 端口 + 房间码也使用 V5。发现到 V5 房间时使用 V5，发现到仅 V4 广告的历史房间时才创建 V4 兼容客户端。UI 只传递已发现房间，不直接了解 V4/V5。

`LanGameServer` 先从原始 JSON 安全读取首个 JOIN 的 `protocolVersion`，再进入 V4 或 V5 adapter。两条 adapter 路径都会调用同一个 `onJoin`、`onAction`、`onDisconnect` 与 `viewFor` 回调；斗地主规则、计分、机器人和断线接管只保留在 `HostGameSession` / `GameEngine` 中一份。

## V5 UDP 房间发现

通用发现 codec 位于 `network/protocol/v5/V5DiscoveryCodec.kt`：

- 请求：`OFFLINE_GAMES_DISCOVER_V5`
- 响应 type：`OFFLINE_GAMES_HOST_V5`
- 响应为 UTF-8 JSON，包含 `protocolVersion`、`gameType`、`roomCode`、`roomName`、`hostPort`、`playerCount`、`maxPlayers` 和 `gameConfig`。

斗地主房主的 `RoomAdvertiser` 同时回答 V5 通用请求和 V4 `DDZ_DISCOVER_V4` 请求。V4 响应仍只属于斗地主兼容层；未来 UNO 房间不得发送 V4 斗地主广告。

斗地主入口的 `RoomDiscovery` 同时发送两种请求，只展示 `GameType.LANDLORD`。它用 `host + port + roomCode` 去重；同一房间同时收到 V4 和 V5 广告时保留 V5，V4-only 房间仍保留并以 V4 加入。错误 JSON、未知游戏类型、错误版本、非法端口、人数、最大人数或房间码都会被忽略。

## 重连与依赖方向

V5 JOIN、JOIN_ACCEPTED 和 ACTION 保留 `playerId`、`resumeToken`、`expectedRevision` 语义。重连时复用同一 `resumeToken`，服务端继续替换旧连接而不创建第二个玩家；出站 STATE 根据新连接记录的协议版本编码。

依赖方向为：

```text
App shell → shared/GameType → V5 common protocol
斗地主 adapter → V5 common protocol → network.transport
斗地主 HostGameSession → 斗地主 adapter / GameEngine
```

`network.transport` 未被修改为认识 V4、V5、GameType、斗地主或 UNO 的层。

## 测试与验证

本阶段新增 28 项自动测试，原 65 项回归测试全部保留：

- 11 项 V5 golden fixture / 编码安全测试：JOIN、JOIN_ACCEPTED、ACTION、STATE、ERROR、PING、PONG、未知枚举、错误版本及错误 payload。
- 6 项 V5 discovery codec 测试：固定广告、字段边界、错误 JSON、未知游戏、错误版本和非法数据。
- 4 项 V4/V5 discovery 选择测试：去重优先级、V4-only、不同房间及 LANDLORD 过滤。
- 5 项真实 TCP 服务端测试：V5 JOIN、V5 ACTION → revision STATE、resumeToken 重连、V4 客户端加入新服务端，以及混合 V4/V5 客户端各收各自格式的 STATE。
- 2 项客户端协议选择测试：新版默认 V5 与 V4 广告房间的兼容客户端。

本次完整单元测试结果：**93 / 93 通过，0 失败，0 错误**。V4 historical JSON / UDP fixture 继续通过。机器人压力测试完成 **2000 / 2000** 局；Android Debug 完整构建 **BUILD SUCCESSFUL**。版本与 SDK 保持 `applicationId = com.offlinelandlord.game.v2`、`versionName = 0.3.7`、`versionCode = 10`、`targetSdk = 36`、`compileSdk = 37`。

## 尚未实现与下一阶段

UNO 仍只有应用选择入口和占位页，尚未定义 UNO `gameConfig`，也没有任何 UNO 网络会话。

阶段4的目标是 UNO 纯 Kotlin 游戏核心（如 UnoCard、UnoDeck、UnoGameState、UnoEngine、UnoRules）。阶段4开始前不得把这些模型、规则或牌桌提前加入本阶段。

## 已知风险

本阶段已通过本机真实 TCP 回环测试，但未重新完成三台 Android 手机热点的真机验收。后续应在阶段验收中覆盖 V4/V5 混合设备、UDP 发现、断线重连与机器人接管的真实热点场景。
