JASPR BETTER COMBAT - Better Combat Rebirth 1.5.6 Paper/browser port

This directory is the production build source for JasprBetterCombat.jar. It is
separate from the other plugins; it does not load arbitrary Forge JARs.
See THIRD_PARTY_NOTICES.txt for original attribution and per-file licenses.
Release history: plugin 1.5.6-port.3 / browser bettercombat3 (September 30,
2026), 1.5.6-port.4 / bettercombat4-8 (first-person melee motion). Candidate
1.5.6-port.5 / bettercombat9 (2026-10-11, combat rework workstream A): the
dual-wield fixes and the per-attack server verdict protocol listed in
../../../DUAL_WIELD_FIXES.md. The bettercombat2 sword-NBT crash fix remains.
Never restore bettercombat1.
See PARITY-1.5.6.txt for original source mappings and platform adaptations.

BUILD 1.5.6-port.5 / bettercombat9 (the current way)
  javac --release 8 against server/cache/patched_1.12.2.jar (see DUAL_WIELD_FIXES.md),
  jar into server/plugins/JasprBetterCombat.jar
  node scripts/build-bettercombat-client.cjs   (LIVE classes.js -> candidate/bettercombat,
  replaces only the head region; client/bettercombat.js and scripts/build-client.cjs
  below are the older 20260930 builders and are stale)

BUILD FROM THIS DIRECTORY (Java 17, bytecode target Java 8; Node.js)
  powershell -File scripts/build.ps1
  node scripts/build-client.cjs ../../../site/classes.js
  node scripts/build-assets.cjs ../../../site/assets.epk

Outputs are under build/. Building DOES NOT install or restart anything. Client
builders take the current live inputs explicitly. They verify unique anchors,
support repeat application, preserve unrelated client bytes/assets, and fail
instead of guessing if the client has changed incompatibly.

Install only build/JasprBetterCombat.jar and the matching build/client files,
after backup and coordination. Cache-bust classes.js in site/client.html and
assets.epk in site/jaspr-client.js; also bump jaspr-client.js's HTML query.
Do not overwrite another agent's pending plugin update or source.

PLAY
Left click attacks with the main hand. A melee weapon/tool in the off hand can
attack with right click. F swaps hands. Native-use tools, guns, portal devices,
shields, usable items and interactive blocks keep their ordinary interactions.
The Crosshair attack-indicator setting controls dual recharge bars.
While connected, Options > Better Combat... opens settings (read-only for normal
players). Authenticated administrators can edit toggles, numbers and lists, and
reset defaults with two clicks. Shift-click numbers for exact values via chat.
Settings persist in server/plugins/JasprBetterCombat/config.yml. YAML/reload still
works; /bettercombat settings is an alternative where the command gate permits.
Gameplay and opening the Options panel do not require administrator commands.

TESTS / RECOVERABLE CANDIDATE
C:/Users/AM/Documents/JasperCraft-BetterCombat-20260930
Contains the original references, automated unit/client/runtime/browser tests,
reports, frozen tested checkpoint, and deployment/rollback record. Do NOT deploy
CombatFixture.jar, test worlds, or test authentication configuration.

Current evidence: 526 Java checks, 12 JS/resource tests, 81 integration assertions,
14 overlapping focused visual/settings assertions, and 11 live/public checks.
Read the candidate's RELEASE-20260930-BETTERCOMBAT3.txt for evidence and recovery.
The native inventory settings panel replaces Forge GuiConfig on this platform.
No claim of mobile/Safari, every third-party enchantment/PvP edge case, long soak,
large-player performance or native-Forge pixel-perfect A/B certification.
