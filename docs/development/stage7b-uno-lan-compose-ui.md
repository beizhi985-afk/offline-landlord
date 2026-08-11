# Stage7B: UNO LAN rooms and Compose multiplayer table

Status: implementation branch `feat/uno-lan-compose-ui` (not merged to `main`).

## Scope

Stage7B adds the UNO LAN experience on top of the Stage7A V5 host/session/client. It does not add a new protocol, change UNO rules, or replace TCP/UDP transport.

The runtime boundary is:

```text
Compose screens -> UnoLanViewModel -> UnoLanController
               -> UnoV5Client / UnoV5HostServer -> UnoHostSession -> UnoEngine
```

`UnoHostSession` remains authoritative. The UI receives `UnoV5RoomView` projections; only the local player's full hand is exposed to a game view. Opponents expose seat, name, connection, score and hand count, never their cards.

## User flows

- The UNO home screen exposes a working LAN entry in addition to single-player mode.
- A host chooses 2/3/4 seats and Quick or Points 500, starts one guarded V5 server/session, and enters a lobby.
- A guest joins with nearby UNO-only V5 discovery or manual host IP, TCP port and six-digit room code.
- The lobby shows room code, host endpoint, seats, bot/human status, readiness and connection state. Only the host can add/remove bots or start.
- START_GAME state updates move all connected clients to the LAN table. Actions are sent through the controller and validated again by the host.
- The table supports play, draw, after-draw pass, Wild colour selection, UNO and Catch UNO. Reconnect uses the existing player ID/resume token and bounded retries; no host migration is introduced.

## Recomposition and lifecycle rules

`UnoLanController` owns the server, advertiser, client, receive loop and reconnect job. `createRoom` and discovery are guarded against duplicate work; `closeRoom` closes all sockets and jobs. `UnoLanViewModel` owns the controller for the navigation lifetime.

## Validation

- Existing Stage7A tests remain unchanged.
- Stage7B adds controller/state contract coverage and a loopback host/guest flow in `UnoLanControllerTest`.
- The source tree contains 371 `@Test` methods after Stage7B test additions.
- Android `compileDebugKotlin` passed on the ASCII-path build copy. The Windows test task reached Java compilation but was blocked by the known SDK/JDK `AccessDenied` lock on `core-for-system-modules.jar`; this is an environment result, not a claimed test pass.

## Device acceptance boundary

No physical multi-device test is claimed for Stage7B. The loopback test is not a substitute for three Android phones on one hotspot. Stage7C must perform real-device discovery, room join, readiness, complete match, disconnect/reconnect and visual acceptance before production release.

## Explicit non-goals

- no Stage7C work;
- no application version or applicationId change;
- no V5/V4 protocol change;
- no UNO engine/rule change;
- no merge to `main` or tag creation in this implementation step.
