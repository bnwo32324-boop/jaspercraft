# JasperCraft — notes for Claude (cloud and local sessions)

JasperCraft is a private browser-playable Minecraft 1.12.2 server: Paper 1.12.2 + EaglercraftXServer + custom
"Jaspr*" plugins, browser client = TeaVM-compiled Eaglercraft (`site/classes.js`, patched in place).
The live server runs on the owner's Windows PC. **Cloud sessions cannot reach it: never try to deploy.**
Finish cloud work by committing to a branch and opening a pull request. The owner merges the PR, then
double-clicks `Deploy JasperCraft.bat` on the PC (manual one-click deployer, see "Deploying" below).

## Rules
- Never commit secrets, worlds, player data or logs (`.gitignore` is an allowlist — keep it that way).
- Gameplay goes in Paper plugins (`server/custom-plugins/<Plugin>/src` + `resources`). Touch `site/classes.js`
  only when rendering/UI needs it: binary-safe edits, fenced marker comments, CRLF preserved, bump the
  version query in `site/client.html`.
- Structure generation (JasprHorrorBiomes): anything that changes which structures get PLACED must not change
  which can be RECOGNISED (/where, loot, lighting). Keep changes additive; never move existing sites.
- Never call `BlockState.update()` after filling a chest's live inventory.
- No night vision / full-bright / glowing mob effects (owner's mood rule). No valuable blocks in structures.
- The owner prefers concise reports and low token use.

## Deploying (scripts/deploy, OWNER_DEPLOY.md)
- The deployer only ships `server/plugins/Jaspr*.jar`, `server/plugins/TestServerControl.jar` and `site/**`.
  **A PR that changes a plugin must commit the rebuilt jar in `server/plugins/`** (source alone never goes live).
- It fetches origin/main in a staging clone (`C:\Users\AM\Documents\jaspercraft-deploy`), fast-forward only, asks
  the owner Y/N, runs a throwaway test server (port 25597, `-Djaspr.gear.selftest=true`), waits until nobody is
  online and the server has run 5+ min, backs up, stages jars in `plugins\update\`, restarts via
  `.runtime\game-server-stop.request`, checks `Done (`, the changed plugins' `*_READY` lines and both health URLs,
  and restores the backup if anything fails. New plugins with a READY line: add it to `DeployLogic.ps1`.
- Result of the latest deploy/undo: branch `deploy-status`, file `DEPLOY_STATUS.md` (redacted). Read it
  (`git fetch origin deploy-status && git show origin/deploy-status:DEPLOY_STATUS.md`) before assuming a
  merged change is live. PC-side log: `.runtime\deploy\deploy.log`.
- Changes to `scripts/deploy/**` or the two `.bat` files are refused by the deployer until a local Claude session
  on the PC has reviewed them and installed them into the live folder (files identical = allowed).
- Tests: `pwsh -NoProfile -File tests/deploy-logic.test.ps1` (pure logic + Windows PowerShell 5.1 checks:
  pure ASCII, no `&&`/`??`/ternary, 2-argument Join-Path, `-UseBasicParsing`).

## Building (Linux cloud VM: OpenJDK 21 present; target Java 8 bytecode)
- Plugins: `javac --release 8 -encoding UTF-8 -cp server/cache/patched_1.12.2.jar -d out $(find <plugin>/src -name '*.java')`
  then `jar --create --file <Plugin>.jar -C out . -C <plugin>/resources .`
  (see `scripts/build-*.ps1` / `candidate/structure-audit/harness/build-tree.sh` for per-plugin details).
- Test servers: `candidate/structure-audit/testserver-template` + `candidate/structure-audit/tools/capture.py`
  (written for Windows paths — adapt the `SA`/`GAME` constants when running on Linux). Paper 1.12.2 runs on Java 17/21.

## Key docs
- Status and open work: `candidate/structure-audit/HANDOFF.md`, `candidate/structure-audit/AUDIT_LOG.md`
- Structure audit: `candidate/structure-audit/FINAL_REPORT.md`, `CONVENTIONS.md`, `OWNER_CRITERIA.md`
- Survivor Gear (trinkets): `GEAR_UPDATE.md` (Phase 2: status effects, "Adrenaline" resource + HUD, consumables;
  Phase 3: 9 races as mutations, Creative-screen gear column, trinkets on player models)
- Everything else: `MODDING_NOTES.md` and the `*_UPDATE.md` files.
