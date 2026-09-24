# JasperCraft 1.12.2 Tailscale deployment

This is the active, separate JasperCraft 1.12.2 browser client and multiplayer server. The previous 1.8 deployment remains on disk as a dormant rollback copy, but ports 8443 and 10000 now belong to this project. The Jaspergers site on port 443 was not changed.

## Public addresses

- Browser client: https://jaspergers.tail3bd959.ts.net:8443/
- Multiplayer endpoint (preloaded in the client): wss://jaspergers.tail3bd959.ts.net:10000/
- Existing Jaspergers root: https://jaspergers.tail3bd959.ts.net/ (still redirects to https://jaspr.chat/)

## Availability

Tailscale Funnel is configured persistently and the local site and gateway are scheduled at Windows logon. Each scheduled task runs a lightweight supervisor that restarts its child service within about two seconds after a crash; an additional one-minute trigger is a fallback if the supervisor itself is not running. The gateway starts Paper automatically, keeps it running with no empty-player shutdown, and restarts it if Paper exits.

A separate Funnel watchdog checks ports 8443 and 10000 every 15 seconds. If another process clears either Eaglercraft mapping, the watchdog restores that mapping after confirming its local service is healthy. It never performs a global Funnel reset and never owns or changes the Jaspergers mapping on port 443.

The September 6 outage was traced to a different link-recovery process clearing the shared Funnel configuration while the Eaglercraft site, gateway, and Paper server remained online locally. Recovery was verified by deliberately removing each Eaglercraft mapping and observing the watchdog restore it while port 443 stayed intact.

This deployment is hosted on this PC. The links remain usable only while the PC is powered on, awake, connected to the internet, signed into the AM Windows account, and Tailscale is running. Moving the project to a VPS or cloud host would be required for availability while this PC is off.

## Owner/admin login

The sole operator account is `jasper`. Its user-selected AuthMe password is stored only in:

`private\OWNER-CREDENTIALS.txt`

Do not share that file or password. In the client, choose the exact username `jasper`, join the preloaded server, and use `/login <password-from-file>`. The account has operator level 4 and can run all server commands. Useful examples are `/gamemode survival`, `/gamemode creative`, `/survival`, and `/creative`.

Friends must choose different usernames. On their first join they use `/register <password> <password>`; later they use `/login <password>`. They are not operators, and the custom server-control plugin blocks every non-authentication command for non-owner players.

## Normal operation

Run these from PowerShell:

- `scripts\status.ps1` — show site, gateway, Paper, task, and port status.
- `scripts\start-game-server.ps1` — clear maintenance mode and restore always-on operation.
- `scripts\stop-game-server.ps1` — gracefully stop Paper and leave it in explicit maintenance mode.
- `scripts\start-site.ps1` — start the browser site if needed.
- `scripts\stop-site.ps1` — stop the browser site.
- `scripts\install-autostart.ps1` — reinstall/repoint all three scheduled tasks.
- `scripts\build-server-plugin.ps1` — rebuild the local owner/lifecycle plugin after editing it.
- `scripts\build-site.ps1` — validate the precompiled browser distribution and its version marker.

## Server behavior

- Paper version: Minecraft 1.12.2
- Default game mode: Creative
- Owner may switch to Survival or any other mode with commands
- Maximum players: 12
- Idle shutdown: disabled
- Paper crash/exit recovery: automatic
- Account protection: AuthMe
- Public Java/RCON port: none
- Eaglercraft WebSocket protocol range includes protocol 340 (Minecraft 1.12.2)

Worlds, inventories, player data, authentication records, and plugins are under `server\`. Back up that folder before major plugin or world changes.

## Client provenance and the mislabeled download

The supplied folder `C:\Users\AM\Downloads\EaglercraftX-1.12.2-workspace-master\EaglercraftX-1.12.2-workspace-master` is not a 1.12.2 client. Its source identifies Minecraft 1.8.8, its HTML advertises 1.8/1.8.8, and it lacks 1.12-era classes. It was intentionally not deployed.

The active browser distribution is a JasperCraft-branded Minecraft 1.12.2 JavaScript build (`u1`) from `TwoMuchNerdo/1.12.2-WASM`, pinned by commit and SHA-256 hashes in `deployment-manifest.json` and `client-reference\SOURCE.md`. It was tested by loading in Chrome, querying the secure endpoint, negotiating Eagler protocol 4, logging into Paper protocol 340, and rendering the world.

For future modifications, server-side Bukkit/Paper plugins are the most maintainable path and do not require Forge. Client-side changes require a genuine buildable 1.12.2 source tree; the active distribution includes a source map but not embedded source contents. See `MODDING_NOTES.md` before attempting client patches.

The client includes third-party game code/assets. Confirm that you have the rights needed before distributing it beyond private testing.
