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

## Stage7A.1 validation matrix (2026-08-11)

This is a test-gap completion record. It does not open Stage7B, add a LAN Compose entry, or change the V5 envelope, shared transport, UNO rules, or bot strategy.

### Added drivers and coverage

`Stage7A1UnoLanValidationTest` adds seven deterministic JUnit tests:

1. `UnoHostSession` Quick: 1,000 complete matches.
2. `UnoHostSession` Points: 100 complete matches reaching the 500-point target.
3. Host-session reconnect identity/seat/token pressure: 200 sessions.
4. Real TCP session lifecycle and port reuse: 100 sessions.
5. Two-human full Quick match over two real TCP clients.
6. Human plus bot full Quick match through the V5 host server.
7. Four-seat mixed full Quick match with stable seats and private hands.

The human driver selects only from the public `legalActions`, `legalPlayableCardIds`, `phase`, and its own hand. It never calls `UnoEngine.applyAction` directly. All network waits use bounded five-second timeouts; seeds are deterministic and recorded in the test source.

### Results

- New tests: 7 / 7 passed.
- Full Android JUnit suite: 331 / 331 passed, 0 failed, 0 errors, 0 skipped.
- Existing HostSession tests had nondeterministic assumptions about whose turn it was and about disconnecting the current player. The test-only correction fixes the seed and chooses the public current/non-current player without changing production behavior.
- Existing UNO stress tests remain in the suite and ran as part of the 331-test run: random Quick 2,000; bot Quick 5,000; bot Points 500; single-player Quick 1,000; single-player Points 100.
- No executable Landlord 2,000 stress test is present in the current test source; it is not claimed as run by this matrix.

### Minimal adapter fix found by the real TCP flow

The full TCP flow exposed that `UnoV5HostServer` decoded room-management actions (`READY`, `UNREADY`, `START_GAME`, `ADD_BOT`, `REMOVE_BOT`) as engine card actions, so a real TCP room could not progress from JOIN to START. The adapter now routes these five existing room actions to `UnoHostSession` APIs and leaves all card actions on the existing session path. No protocol fields, engine rules, shared transport, or bot logic changed.

### Build record

- Android task: `:android-app:assembleDebug --rerun-tasks`
- Result: `BUILD SUCCESSFUL`
- APK: `android-app/build/outputs/apk/debug/android-app-debug.apk`
- SHA-256: `08E0C9E32643F353293DF482738B276BD5E108A74CC282125E5F78BDD4091B08`
- `applicationId`: `com.offlinelandlord.game.v2`
- `versionName`: `0.3.7`
- `versionCode`: `10`
- `compileSdk`: `37`, `minSdk`: `26`, `targetSdk`: `36`

This record was produced on branch `feat/uno-lan-v5-core`. The branch is not merged to `main` and no Stage7B work is included.
