# Performance update — 2026-09-29

Server tick time and browser frame time, improved with ideas from desktop optimization mods, measured first on an
isolated byte copy of the live server and client (`C:\Users\AM\Documents\JasperCraft-PerfSandbox`, full numbers and
method in its `REPORT.md`).

## Measured result (sandbox, same world and plugins as live)

Server — 6 bots (4 exploring new terrain at sprint speed, 2 at spawn), 3 runs per side, medians:

| phase | mean tick | p95 tick | p99 tick | ticks > 50 ms | GC time |
| --- | --- | --- | --- | --- | --- |
| exploring new terrain | −13% | −9% | −20% | −35% | −81% |
| settled, mobs piling up | −9% | −35% | −31% | +44% (9 → 13, noise) | −81% |
| at spawn | −28% | −38% | −23% | −67% | −64% |

Client — unmodified production `client.html` on this PC's GPU, 3 rounds, main-thread time per frame:
spawn −27%, teleports into explored terrain −13%, 40-zombie horde −11%, sprint walk −6%; frames over 33 ms (visible
stutter) 44 → 13. On this PC FPS is capped by the 144 Hz display, so the saving shows up as FPS on slower devices.

## What changed

**Client** (`site/classes.js`, fenced `JASPR_PERF_V1`, applied by `scripts/perf-client-patches.cjs`):

- *glState* (BadOptimizations): the distant-terrain (DH) pass saved GL state with `getParameter` every frame; two of those
  are synchronous GPU round trips (9–11% of frame CPU). It now reads the engine's own state mirror.
- *particleCull* (Sodium): particles fully outside the view are not drawn (~70% of them).
- *particleLight* (Sodium/Lithium): particle light is looked up once per game tick instead of every frame.
- *nameCheck*: the Dinnerbone/Grumm upside-down check no longer builds every mob's translated name every frame.
- *occlusion* (EntityCulling): mobs, spawner cages, chests and signs hidden behind full opaque blocks are not drawn
  (ray-cast from the exact camera position; never players, anything within 8 blocks, third person, or beacons).
  Checked pixel-identical against occlusion-off renders.

Each can be switched off in one browser for support: `localStorage.setItem('jaspr.perf.v1','{"occlusion":false}')`;
counters: `JasprPerf.stats()` in the game frame console. **If `classes.js` is rebuilt later, run
`node scripts/perf-client-patches.cjs`** (it refuses to patch if any anchor moved).

**Server:**

- New plugin **JasprPerfTweaks 1.0.0**: mobs lose the cosmetic idle-look goals (AI Improvements); mobs far from every
  player re-plan goals less often, chasing/attacking still every tick (Pufferfish/Airplane DAB, Lithium); Netty flush
  consolidation per connection (Krypton); `/jprgen` time-budgeted pregenerator, only when an operator starts it.
- **JasprRuins 1.1.1**: `Trinkets.tick` parsed the JSON pages of every written book in every inventory slot several
  times per player per second (4–8% of all tick time). Lore is now read straight from the item's NBT, one inventory pass.
  Verified identical to the old code on 1,292 real and synthetic items.
- **JVM** (`server-tools/gateway.py`, `gateway-config.json`): Aikar's G1 flags + string deduplication, Xms = Xmx = 2G.
- **Paper/Spigot/Bukkit settings** (`scripts/perf-server-config.cjs`; the YAML files are not in git): timings off,
  queued light updates, container sync every 3 ticks, grass spread every 4 ticks, spawner tick rate 2, entity collisions
  8 → 2, armor-stand collision lookups off, chest-cat scan off, inactive villagers not ticked, faster chunk GC, Nether and
  End spawn chunks not kept loaded with nobody there. Backups: `server/*.yml.before-perf`.

## Gameplay notes

Nothing changes what players can do. Visible differences are small: idle mobs no longer glance around; big mob crowds
push each other a little less; open inventories resync every 3 ticks instead of every tick. Nether/End spawn areas pause
while nobody is there (JasprNether already skips unloaded chunks).

Not done automatically — owner decisions: **pregenerating terrain** (`/jprgen start world <radius>`) removes world
generation lag, but pregenerated chunks will not receive structure types added later (JasperCraft adds new features
only to chunks that did not exist when they shipped); **monster spawn attempts every 2 ticks** would save more but slows
horde refill.

## Rollback

Delete `server/plugins/JasprPerfTweaks.jar`, restore `server/*.yml.before-perf`, revert this commit's `gateway.py` /
`gateway-config.json` / `site/classes.js` / `site/client.html` / JasprRuins jar, restart (gateway, then Paper).

Tests: `node --test tests/perf-client.test.cjs tests/perf-server.test.cjs`.
