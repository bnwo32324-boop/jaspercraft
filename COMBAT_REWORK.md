# Combat animation rework (owner request 2026-10-11) - shared contract for the three workstreams

## The owner's words (verbatim)
- "Completely redo the animations for the sword swings, the axe swings, and other weapons, etc. Every type of melee weapon
  should have a different type of animation that looks fluid and epic and have animatic effects it's the cherry on top. The
  axe should have a different animation than a sword, for example. The animation should be more complex, and you should also
  fix dual wielding. Dual wielding should have its own animation for different combinations of weapons, it should be specific
  to every combination. The dual-wielding animations should jive well with each other. Both melee weapons should jive well
  with each other, animation-wise. Dual buildings [wieldings] also broke on a programmatic level. There are some bugs, and
  it's finicky. Make sure it's in working order before you apply the animations. Also give gun animations when they shoot,
  such as recoil, etc."
- "Try to make the combat for melee weapons and gunplay extremely satisfying when it comes to the animations."

## What exists (read before changing anything)
- Server: plugin `JasprBetterCombat` (port of Better Combat Rebirth 1.5.6: off-hand attacks with right click, separate
  main/off energy, sweeps, crits, crosshair bars, settings panel). Source `server/custom-plugins/JasprBetterCombat`
  (README.txt, PARITY-1.5.6.txt, MELEE-REWORK-20261002.txt), jar `server/plugins/JasprBetterCombat.jar` (1.5.6-port.4).
  It was never committed before c449ae8 (imported exactly as deployed).
- Client: one region of the live `site/classes.js` (`C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\site\classes.js`,
  READ ONLY):
  - `/* JASPR_BETTERCOMBAT_V1_BEGIN ... */` head = the Better Combat client adapter (JasprBetterCombat, JasprCombatAction,
    JasprCombatTick, JasprCombatTransform, JasprCombatSweep, JasprCombatIndicators, JasprCombatPick/Box, options button...).
    Its live text is extracted to `client-mods/bettercombat/bettercombat-live-head.js` (the stale
    `server/custom-plugins/JasprBetterCombat/client/bettercombat.js` is an older version - do not build from it).
  - nested inside it, `/* JASPR_MELEE_FP_BEGIN */ ... /* JASPR_MELEE_FP_END */` = the first-person melee motion
    (`client-mods/melee/melee-motion.js` + `melee-native.js`, builder `scripts/build-melee-client.cjs`, offline preview
    `scripts/melee-preview*.cjs` + `melee-preview-render.cjs`, tests `tests/melee-motion.test.cjs`, `tests/melee-native.test.cjs`).
  - Engine call sites (outside the region, unchanged by default): JasprCombatAction x2 (left/right click), JasprCombatTick,
    JasprCombatIndicators, JasprCombatTransform (first-person item transform), JasprEpicSwingOffset, JasprEpicEquipMatch x2,
    JasprEpicEquipFull, JasprMeleeEmptyArm, JasprCombatOptionsButton/OpenOptions, JasprBetterCombat.receive (channel
    `jaspr:combat`).
- Third person: Mo' Bends only (owner 2026-10-02: third-person attacks "always Mo' Bends and nothing more"). Do not add a
  third-person attack layer. Check that off-hand attacks still read correctly in Mo' Bends; fix only inside Mo' Bends if broken.
- Engine map: `MUTANTS_CLIENT_INTERNALS.md` (TeaVM conventions, resumable functions, model/GL/particle/sound/network APIs).
- Guns: JasprApocalypse `Arsenal.java` and JasprDungeon `Arsenal.java` keep the loaded count as NBT `rounds` (plus a `uuid`)
  on the held gun; the server fires on right click and updates the stack.

## Ownership (one owner per file/region; never edit another's)
| Workstream | Owns | Branch / worktree |
| --- | --- | --- |
| A Dual wielding (first) | BC head region (everything inside JASPR_BETTERCOMBAT_V1 except the nested JASPR_MELEE_FP module), its new source `client-mods/bettercombat/` + builder `scripts/build-bettercombat-client.cjs` (replaces only the head region), the server plugin `server/custom-plugins/JasprBetterCombat` + jar, `tests/bettercombat-*` | claude/combat-dual, `C:\Users\AM\Documents\JasperCraft-Combat-Dual` |
| B Melee animation | JASPR_MELEE_FP module (`client-mods/melee/`, `scripts/build-melee-client.cjs` adapted to replace only the module region), a NEW outer stage `JASPR_COMBATFX` (its own fence, builder `scripts/build-combatfx-client.cjs` with `--unpatch`) for any new engine hook (camera kick, trails in world space ...), `tests/melee-*`, `tests/combatfx-*` | claude/combat-melee, `C:\Users\AM\Documents\JasperCraft-Combat-Melee` |
| C Gun animation | a NEW outer stage `JASPR_GUNFX` (`client-mods/gunfx/`, `scripts/build-gunfx-client.cjs` with `--unpatch`), `tests/gunfx-*` | claude/combat-guns, `C:\Users\AM\Documents\JasperCraft-Combat-Guns` |
| Lead | merging, final client assembly from the LIVE file (order below), lean browser verification, deploy | claude/combat |

## Interfaces between the workstreams
### Combat feed (A writes, B reads)
A defines in the BC head a global, append-only feed; B must work (degrade to swing-only) if it is missing or empty:
```
JasprCombatFeed = { seq: <int, last event number>, events: <array, the last <= 64 events, oldest first>,
                    since(seq) -> events with e.seq > seq }
event = { seq, at: Date.now(), hand: 'M'|'O', kind, item: <numeric item id of that hand, -1 empty>,
          target: <entity id or -1>, crit: bool, sweep: bool, fatal: bool, pair: {main: <item id>, off: <item id>} }
kind  = 'swing'    the client accepted an attack click for that hand and sent it (target = the entity it aimed at, or -1)
        'hit'      the server confirmed damage on target (crit/sweep/fatal from the server)
        'blocked'  the server says a shield took it
        'miss'     the server confirms no target was hit
        'denied'   the click was consumed without an attack (recharging, gated)
```
A adds whatever server->client confirmation it needs to the existing `jaspr:combat` channel (backward compatible: old
clients must ignore it). B times its impacts from 'swing' + the predicted target and upgrades them when 'hit' arrives.
`JasprBetterCombat.markSwing(off)` / `motion(main)` stay for compatibility.

### Camera impulses (B owns, C uses)
B's JASPR_COMBATFX stage defines `JasprCameraFX.kick({pitch, yaw, roll, back, ms, curve})` (degrees / blocks, additive,
decaying, bounded, never moves the real player rotation or the server-side look) plus the one camera hook that applies it.
C calls `JasprCameraFX.kick(...)` when present and does nothing camera-wise otherwise (stub it in tests).

### Guns (C, independent of A and B)
Detect a real shot from the held gun's NBT `rounds` dropping for the same `uuid` (exact; a small optional prediction on the
click is allowed only if it can never animate a shot that did not happen). Reloads can be detected from `rounds` rising (and
any reload flag the plugins already keep).

## Rules for all three
- Fluid and readable at 20 TPS and on phones: animation is clocked by player ticks + partial ticks (like melee-motion v4),
  never by wall time alone; per-frame work is bounded; few draw calls; VFX respect the client particle setting.
- Satisfying, not nauseating: impacts, hit-stop, trails, flashes and camera kicks are short and bounded; no full-screen flashes,
  no glowing/outline effects on mobs or players, no night vision/full-bright anything (owner rules).
- No gameplay change except A's bug fixes (damage, reach, cooldown, packets stay the mod's rules); B and C are visual only.
- Fenced, removable stages; anchors chosen from ORIGINAL engine text no other stage replaced; builders read the LIVE
  classes.js (read only), write `candidate/<stage>/`, check native names exist, parse the result (vm.Script), prove
  `unpatch(build(x)) === x`; latin1 in and out; never edit the live `site/`, `server/` or `.runtime`.
- Diagnostics: `jaspercraft.<stage>.state/error` events (bounded, privacy-safe) and a `Jaspr<Stage>Diagnostics.status()`;
  any thrown error disables only its own part.
- PC safety (the owner's PC hard-reboots under heavy load): one heavy process at a time through
  `bash C:/Users/AM/AppData/Local/Temp/claude/C--Users-AM-Desktop-Curser-Test-Jaspergers/83225637-b7fe-4eed-b9fb-9f20eb4aa7b0/scratchpad/titans/lockrun.sh <cmd>`,
  single core (`-XX:ActiveProcessorCount=1`, javac `-J-XX:ActiveProcessorCount=1`), low priority, node tests one at a time
  (never `node --test` with default concurrency), at most ONE short lean browser session (low resolution, render distance 2,
  low priority) and at most one short test server (port: A 25686, B 25687, C 25688; never 25565 or 25590-25599).
  Prefer the offline software preview (`scripts/melee-preview-render.cjs`, `scripts/model-preview.cjs`) to judge motion.
- Commit on your branch with `git -c user.name="JasperCraft owner" -c user.email=jaspercraft@users.noreply.github.com commit`,
  message ending `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, explicit paths only, never `candidate/`, `site/`
  or build outputs (except the plugin jar for A). Do not merge, rebase or push; never deploy.

## Lead's assembly order (for reference)
LIVE classes.js -> A's builder (BC head region) -> B's builder (melee module region) -> B's JASPR_COMBATFX stage -> C's
JASPR_GUNFX stage (outermost). Outer stages already live (Mutants is the current outermost) stay byte-identical; new stages
go on top. Then one lean browser verification and the deploy (server jar of A with one restart when empty, client files).
