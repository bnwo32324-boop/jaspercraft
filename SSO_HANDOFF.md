# JasperCraft game-side shared identity deployment

Installed and active on 2026-09-06 at https://jaspr.chat/jaspercraft/. The candidate jar, private key, Jaspr account bridge, and both admission guards were activated together with a coordinated restart. Jar SHA-256: 444E5A585CF20C87D96725E8BBFA49590B1D49D4592DBA9E889A3D7B3B338972. The build output keeps its candidate label; the installed jar is identical.

## Contract for main

- Page: `https://jaspr.chat/jaspercraft/`. Strip `/jaspercraft` when proxying static files to the unchanged site server on 3308, or serve the site directory directly. Preserve the prefix and full query on WebSocket requests to 3310.
- Static allowlist additions: `client.html`, `jaspr-sso.js`, `jaspr-profile.js`, `jaspr-client.js`, `jaspr-sso.css`, `jaspercraft-banner.png`, and `jaspercraft-cat-face.png`. Keep `index.html`, `classes.js`, `assets.epk`, `favicon.png`, and `lang/` assets available. Source maps and private files are deliberately excluded.
- `GET /api/jaspercraft/me`: `{authenticated:true,user:{username,displayName},gameName,chatUrl}`; unauthenticated is 401. `chatUrl` must be same-origin.
- `POST /api/jaspercraft/ticket`: `{ticket,gameName,serverAddress}`. `serverAddress` is the same-origin WebSocket address `/jaspercraft/socket` using `wss:` in production (an included ticket query is replaced by the returned ticket). `ticket` must be 32–512 base64url characters with no padding. Names must be 3–16 ASCII letters/digits/underscores.
- Browser POSTs include `X-Jaspergers-Client: web-v1`, JSON content type, and same-origin cookies. Browser fetch supplies the same-origin Origin automatically. Login/register call the existing `/api/auth/login` and `/api/auth/register` with username/password and optional displayName for registration.
- Internal consume: `POST http://127.0.0.1:3200/api/jaspercraft/internal/consume`, header `X-Jaspr-Craft-Key`, body `{ticket}`. Success must be status 200 with `{userId,gameName,privileged,expiresAt}`. `privileged` must be a JSON boolean. `expiresAt` accepts epoch milliseconds, epoch seconds, or an ISO instant; it must be in the future and no more than 90 seconds away. `userId` is a nonempty string or numeric identifier.
- Read the key from game project `private/jaspr-bridge.key`, outside `site/`. Create it before Paper activation; missing/invalid key leaves admission closed until plugin restart. Key is trimmed ASCII, 32–512 printable characters. Do not place it in config, URLs, logs, artifacts, or static routes. Config defaults `jaspr-bridge-port: 3200`; only the port is configurable, never the loopback host or internal route.
- The gateway must atomically consume each ticket exactly once, including concurrent requests. Game verification never caches identity by name or IP. Keep `jasper` reserved exclusively for the canonical Jaspr owner; reject case variants. Stable offline UUID for that character is `272903d5-f420-379b-bd13-d9ffd166acd3`.
- Reserve all existing AuthMe usernames case-insensitively: `crash`, `jasper`, `vooldeev1387` (display name `VoolDeev1387`). Only explicitly linked identities may inherit legacy names/inventories. The plugin trusts main's stable name allocation; it does not duplicate account/ticket/name databases.

## Required fail-closed transport guard (main owns deployment)

The plugin writes `.runtime/jaspr-sso-ready` in the game project once per existing one-second lifecycle tick. Contents are an epoch-millisecond timestamp. A transient Windows file-sharing collision during atomic replacement is retried on the next tick instead of permanently disabling the verifier; the last good marker remains valid briefly and both outer gateways still fail closed if it exceeds five seconds. Require this file to exist, contain at most 32 bytes, and have age from 0 through 5 seconds before forwarding every public game connection. Recheck before forwarding the upgrade after any backend startup wait. Reject malformed/stale/missing markers with 503. Reject non-SSO paths and absent/malformed tickets. Apply the guard to both the Jaspr route and any retained public legacy Funnel/game endpoint; otherwise legacy transport can bypass the deployment guard when the plugin is absent.

The plugin removes the marker on disable/key failure/dependency disable and closes tracked channels. A missing plugin cannot write it; stale markers expire. Main should close existing relays when the marker becomes unavailable. An already-admitted connection remains signed in after its *ticket* expires; 90 seconds is admission lifetime, not gameplay duration. Bridge consume failures reject new logins.

The lead implemented the transport guard in `server-tools/gateway.py` and the Jaspr gateway. Both reject missing/stale/malformed readiness markers and close active relays if readiness is lost. The legacy site server is unchanged; raw legacy game paths cannot bypass SSO admission. Never log WebSocket query strings or request headers containing a ticket/key.

## Exact installed API validation

Installed jars: EaglercraftXServer 1.1.1, AuthMe 5.6.0-bCUSTOM, Paper 1.12.2. `javap` confirms:

1. `HTTPInitialInboundHandler` copies `HTTPMessageUtils.getURI(request)` directly into `NettyPipelineData.requestPath`. `getWebSocketPath()` returns that field including the query.
2. `EaglercraftWebSocketOpenEvent.getConnection()` provides the URI and `.netty().getChannel()`. Reject malformed/no-ticket requests here; do not consume during MOTD/status pings.
3. Asynchronous `EaglercraftLoginEvent.getLoginConnection()` supplies URI, name, UUID and the same Netty channel. Cancel by default, atomically consume at main, require exact verified name and the legacy offline UUID, and attach the verified result to a private channel attribute. Recheck event profile name/UUID. No profile rewriting on the server.
4. `PlayerLoginPostEvent.netty().getChannel()` supplies the actual login channel and `getPlayer()` the actual Player. Require that channel attribute, validate name/UUID again, and bind the exact Player object once. An identity-keyed Player map is used; names/IPs are never map keys. Vanilla and unverified channels are rejected.
5. `AuthMeApi.forceLogin(Player)` needs an AuthMe row. Preserve existing rows/passwords; create new rows using `registerPlayer(name, randomInternalCredential)` without touching Jaspr passwords. Force login after join setup; require the exact bound connection in `AuthMeAsyncPreLoginEvent` and confirm `LoginEvent` plus `isAuthenticated` before privilege elevation. Cancel AuthMe session restoration. Timeout, logout, or mismatch kicks the player.
6. Revoke op before join. After confirmed authentication call `setOp(verified.privileged)`, including false. Command authorization uses this authenticated signed result, never `name == jasper`, client roles, IP, or an AuthMe cached name alone. Existing owner inventory/UUID/password are preserved; world/game mode configuration is not changed.

## Browser behavior

Signed-in users skip the account form and see their assigned character. Play obtains a ticket only after the large game script loads. Reopening the game page or using its Play action obtains a fresh ticket; an already-used or expired ticket fails closed. Tickets exist only in frame memory and the actual socket upgrade; saved client options/server lists use the stable address.

The compiled client has no username option. `jaspr-profile.js` decodes the existing gzip/NBT profile once, splices only the top-level username, and re-encodes it. Custom skins, capes, other profile bytes, storage namespace, world/resource-pack database names, and performance options are retained. Malformed profiles fail without overwriting storage. Browser storage cannot cross origins automatically: moving from the old Tailscale origin requires the client's profile export/import. No passwords or tickets go into local storage.

The game runs in a child browsing context. The real same-tab chat anchor removes that context before normal navigation, closing game sockets/workers/audio while retaining the Jaspr cookie. Pagehide also unloads it. There are no heartbeat, interval, frame, movement, or render hooks. A WebSocket **constructor-only** proxy inserts the current ticket; returned sockets and their packet I/O remain native.

## Build and installation

From the game project, run `scripts\build-server-plugin.ps1` and `scripts\build-site.ps1`. Jar output is `candidate\jaspr-sso\TestServerControl.jar`; it never overwrites `server\plugins\TestServerControl.jar`. Main must install the candidate during its coordinated activation/restart, create the key, deploy the auth/ticket routes and guarded proxies, and then test real login/register/reconnect/return-to-chat. Do not use Bukkit `/reload`.

Validation: 40 Java contracts and eight browser-shell tests pass. The isolated real-Paper suite passes friend/owner login, exact-name/UUID binding, missing/replayed/expired ticket rejection, vanilla/mismatched-profile rejection, op revocation, readiness-marker retry, bridge outage, and plugin-disable disconnection. The live public browser joined a temporary non-admin character and Paper confirmed AuthMe sign-in without a separate game password. The QA chat identity/session was removed after each test; the canonical owner was unchanged.

The live browser reconnect also produced a second Paper/AuthMe login with the same UUID, closed the first connection, and returned to the dedicated chat server with the same account still signed in. Players press Escape to release the mouse before using the compact cat link; it removes the game frame before returning to chat. The dedicated community exposes a fixed Play/Return control. The headless harness invokes the native pointer-unlock API explicitly.

The main site integration has 31 passing focused checks. Its full build passes; its broad suite has the same 31 pre-existing failures as the baseline. On 2026-09-06 the single source-mapped first-load disclaimer branch in classes.js was hard-disabled at equal byte length and its language entries were removed, so the normal menu opens directly; assets.epk, gameplay, networking and rendering remain unchanged. No render-loop hook was added; a specific FPS or zero network overhead is not claimed.

## 2026-09-06 direct entry and shared guest identities

- The dedicated community's existing ENTER WORLD link still targets `/jaspercraft/`. A signed-in Jaspr member or returning permanent guest now goes directly from that link into the multiplayer client; the banner/account landing page and second Play button are not shown.
- Signed-out visitors retain one compact, viewport-locked fallback with the existing Jaspr local login/registration endpoints, official Google and Discord provider assets/routes, first-time Google alias completion, and `POST /api/auth/guest`.
- Guest play uses Jaspr.chat's one database-backed, server-wide lowercase `anon_####` sequence and trusted-device recovery. JasperCraft stores the game name against that same immutable Jaspr user ID, so returning guests retain the same name and multiplayer progress.
- `GET /api/jaspercraft/auth` returns only Google/Discord readiness. `GET /api/jaspercraft/me` now accepts account and guest sessions, reports identity kind/profile-setup state, and still refuses absent, banned, or invalid sessions. Ticket mint/consume/upgrade validation accepts only current account or guest sessions and remains one-use, session-bound, expiring, and fail-closed.
- The generated client bytes remain pinned. The shell replaces the compiled press-any-key audio gate immediately before `main()`, attempts Web Audio resume at creation, and retries on ordinary pointer, touch, mouse, or keyboard gameplay input. Browser autoplay policy may defer audible output until that real input, but no separate sound-confirmation screen blocks startup.
- Validation: the static build contract and eleven browser-shell regressions pass; the isolated JasperCraft gateway and shared Jaspr auth-front-door tests pass.
