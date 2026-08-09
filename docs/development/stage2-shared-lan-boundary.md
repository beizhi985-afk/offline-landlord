# 阶段2：公共房间与 LAN 网络基础设施边界

## 重构前依赖分析

阶段2开始基线为 `7e063bbbb4547cbd5819c2ab1f28e6aa042e75b0`，网络协议为 V4。

1. 真实 Socket I/O：`LanGameClient` 直接创建 `Socket` 并读写换行分隔 UTF-8 文本；`LanGameServer` 直接创建 `ServerSocket`、接受连接并维护读写器；`RoomAdvertiser` 与 `RoomDiscovery` 直接创建 `DatagramSocket`。
2. JSON 编解码：`WireProtocol.kt` 中的 `wireJson` 与 `WireEnvelope` serializer 负责 TCP V4 JSON；UDP V4 原先使用 `LanDiscovery.kt` 内联的竖线分隔文本。
3. JOIN / ACTION / STATE 语义：`LanGameClient` 和 `LanGameServer` 共同实现；`LanGameServer` 校验房间码和协议版本并转发到 `GameEngine` 回调。
4. 玩家与连接映射：`LanGameServer` 的 `clients` 映射以 `playerId` 为键，并在同一玩家重连时关闭旧连接。
5. 断线重连：`LanGameClient` 负责五次重连；`LanGameServer` 报告有效玩家连接断开；`HostGameSession` 负责断线后的业务处理。
6. `resumeToken`：`GameEngine` 创建、校验并恢复玩家身份；`LanGameClient` 保存并在 JOIN 时重发；`LanGameServer` 负责在线路消息与业务回调间转发。
7. UDP 房间发现：`RoomAdvertiser` 接收 `DDZ_DISCOVER_V4` 并回复 `DDZ_HOST_V4`；`RoomDiscovery` 广播请求、收集并解析回复。
8. 直接依赖 `PlayerAction`：`WireProtocol`、`LanGameClient`、`LanGameServer`、`HostGameSession`、`GameViewModel`、`GameEngine` 与 `BotBrain`。
9. 直接依赖 `PlayerGameView`：`WireProtocol`、`LanGameClient`、`LanGameServer`、`HostGameSession`、`GameEngine` 与 `BotBrain`。
10. 直接依赖 `GameEngine`：`HostGameSession` 以及现有真实 TCP 测试；生产网络客户端/服务器通过业务类型和回调间接耦合。
11. 未来其他游戏可以复用：TCP 连接、监听、连接标识、原始文本收发、关闭与断开事件，以及 UDP datagram 收发和 LAN 广播地址枚举。
12. 必须继续属于斗地主：V4 `WireEnvelope`、JOIN/ACTION/STATE 语义、房间码与 revision 校验、`resumeToken` 身份恢复适配、斗地主 discovery codec、`HostGameSession`、`GameEngine`、`BotBrain`、计分与玩家视图。

## 阶段2边界

本阶段只抽离原始 LAN transport。不会开发 UNO 网络、不会修改 V4 线上格式、不会更换网络库，也不会改变用户可见玩法。

## 重构后架构

```text
App / Compose UI
        ↓
斗地主 GameViewModel
        ↓
HostGameSession（斗地主房主编排）/ LanGameClient（斗地主 V4 客户端）
        ↓
LanGameServer + WireProtocol + LandlordV4DiscoveryCodec（斗地主 V4 适配）
        ↓
network.transport（与游戏无关的原始 LAN 传输）
        ↓
Socket / ServerSocket / DatagramSocket
```

依赖只能从上向下。`network.transport` 不得反向依赖斗地主 core、Compose、`GameViewModel` 或 UNO。

## 公共 transport 职责

- `LanEndpoint`：保存 TCP 目标的 `host` 与 `port`。
- `TcpClientTransport`：建立 TCP 连接，收发换行分隔的原始 UTF-8 字符串，设置读取超时并关闭连接。
- `TcpServerTransport`：监听端口、接受多个连接、分配与游戏无关的连接标识、收发/广播原始字符串、关闭连接并报告断开事件。
- `UdpDatagramTransport`：绑定或打开 `DatagramSocket`，发送与接收原始 `ByteArray` datagram。
- `LanBroadcastAddresses`：枚举全局和网卡广播地址。

transport 不解释 JOIN、ACTION、STATE，不校验房间或牌局，不认识玩家、卡牌、机器人、计分与 revision。

## 斗地主 V4 adapter 职责

- `LanGameClient` 保持原有对 ViewModel 的公开 API，继续负责 JOIN、ACTION、STATE、requestId、playerId、resumeToken、revision、状态 Flow 和五次重连语义；底层 Socket 改由 `TcpClientTransport` 提供。
- `LanGameServer` 继续负责 V4 JSON 解析、房间码/协议检查、玩家与连接双向映射、旧连接替换、`JoinOutcome`、`PlayerAction`、`PlayerGameView` 和错误响应；底层监听及连接读写改由 `TcpServerTransport` 提供。
- `WireProtocol` 仍是斗地主协议模型，未被错误下沉为公共 transport。

## HostGameSession 职责

`HostGameSession` 仍属于斗地主。它继续协调 `GameEngine`、`BotBrain`、`LanGameServer` 和 `RoomAdvertiser`，保留机器人自动调度、断线 8 秒后托管、重连取消托管任务及房主视图发布。阶段2没有将其泛型化，也没有修改其对 UI 的公开接口。

## UDP discovery 职责

- `LandlordV4DiscoveryCodec` 独立保存斗地主 V4 请求 `DDZ_DISCOVER_V4` 与响应 `DDZ_HOST_V4|roomCode|roomName|port|totalRounds|doublingEnabled` 的编码/解析。
- `RoomAdvertiser` 与 `RoomDiscovery` 组合公共 UDP transport 和 V4 codec，继续完成原有广播发现流程。
- 公共 UDP transport 不知道 `totalRounds`、`doublingEnabled`、斗地主或 UNO。

## V4兼容保证

- `WIRE_PROTOCOL_VERSION` 保持 `4`。
- `WireType` 仍只有 JOIN、JOIN_ACCEPTED、ACTION、STATE、ERROR、PING、PONG。
- `WireEnvelope` 线上字段仍为 type、protocolVersion、requestId、playerId、playerName、roomCode、resumeToken、expectedRevision、action、view、message。
- TCP 继续使用换行分隔 UTF-8 JSON；没有增加 `gameType`。
- UDP 请求、响应、六位房间码及房间字段顺序均未改变。
- `GameEngine`、`GameModels`、`BotBrain`、计分、12/24 局、加倍及 8 秒托管代码均未修改。

## 阶段2新增测试

阶段2新增 16 项自动测试：

- 5 项纯 TCP transport 测试：客户端到服务器、服务器到客户端、客户端断开、服务器释放端口、双客户端连接标识隔离。
- 1 项纯 UDP transport 回环测试：验证 datagram 原始字节内容保持不变。
- 6 项 V4 golden 测试：固定 JOIN、JOIN_ACCEPTED、ACTION、STATE、ERROR 历史 JSON，以及 WireType/字段集合保护。
- 3 项 UDP V4 discovery codec 测试：固定请求、固定响应编码、历史响应解析。
- 1 项真实 TCP 重连映射测试：同一 resumeToken 恢复同一 playerId，旧连接不覆盖新连接且不生成重复玩家。

原 49 项回归测试继续保留；阶段2测试总数为 65 项。

最终验证中 65/65 项通过，0失败、0错误、0跳过；现有机器人压力入口完成 2000/2000 局，无死锁、超时、异常或非法动作；仓库正式 Android 构建脚本执行成功。

阶段2已完成自动测试、本机真实 TCP 回环测试及 2000 局机器人压力测试。本轮未重新执行三台 Android 真机热点验收，该风险已知并接受。

## Android 17 局域网权限技术债

当前继续使用 `compileSdk = 37`、`targetSdk = 36`，本阶段不增加 `ACCESS_LOCAL_NETWORK`，也不增加权限弹窗。未来若把 targetSdk 提升到 37 或更高，必须专项评估 Android 17 的本地网络运行时权限、拒绝路径和热点局域网兼容性。

## NSD未来候选

未来可评估 Android Network Service Discovery / DNS-SD，用于局域网服务注册与发现。当前继续只运行已有 UDP V4 discovery，不实施 NSD，也不并行运行两套发现机制。

## 阶段3目标

阶段3目标是：增加真正的多游戏网络协议 V5，让房间发现和 TCP 消息具有 `gameType`，并让斗地主与未来 UNO 通过同一公共 transport 运行。

该目标不属于阶段2；阶段2结束时 UNO 仍只有选择入口和占位页。
