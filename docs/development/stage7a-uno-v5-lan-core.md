# 阶段7A：UNO V5 局域网核心与权威 Host Session

状态：开发分支 `feat/uno-lan-v5-core`

本阶段只加入 UNO 的 V5 局域网核心，不打开 Compose 局域网入口，也不改变阶段6单机玩法、UNO 规则、斗地主网络逻辑或公共 V5 envelope。协议继续使用 `protocolVersion = 5`、`gameType = UNO`，UNO 数据放在 envelope `payload` 中。

## 边界

- `UnoHostSession` 是唯一的房间权威，串行校验并调用现有 `UnoEngine`。
- `UnoV5PayloadCodec` 只负责 UNO DTO 与引擎动作的映射；客户端提交 `cardId`，不会提交完整牌对象。
- `UnoV5HostServer` 是薄 TCP 适配器，复用现有 `TcpServerTransport`，不复制规则。
- 每个玩家的 STATE 只带自己的完整手牌，其他玩家只带 `handCount`；机器人同样只通过 `UnoBot` 的玩家视图行动。
- 房间支持 2～4 个座位、QUICK 与固定 500 分 POINTS_500；座位、playerId 与 resumeToken 在牌局中保持稳定。
- 当前阶段不提供 Host migration；断线玩家保留座位，由 Host 临时以机器人接管，带正确 token 的重连恢复原身份。

## 后续阶段

Compose 局域网入口仍保持“局域网游戏·敬请期待”。发现广播、客户端 UI、Host migration 与跨设备视觉验收不属于阶段7A，分别留给后续阶段。
