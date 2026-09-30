# Light update — 2026-09-30

New chunks were sent to players **before the server had worked out their block light**, so lava, glowstone, lanterns and
lamps arrived dark until the chunk happened to be sent again (a relog, or walking away and back). The Nether, Atlas and
Drownhollow now wait for the light first (JasprNether 1.2.1, JasprAtlas 1.0.1, JasprRuins 1.1.2), as the Backrooms already
did. The overworld already arrives lit and was left alone; `spigot.yml` was not changed.

## Cause

`server/spigot.yml` has `random-light-updates: false` (the Spigot default). In this Paper build `Chunk.isReady()` then returns
true at once, so a new chunk goes out before its light exists, and light worked out later is never sent (the browser only
runs its own light engine for block changes). With the setting on, a chunk is ready only once it is populated, has ticked and
is lit, as in the game itself. The setting lives on each world (`spigotConfig.randomLightUpdates`), so the plugin that owns a
world switches it on for that world alone, at world init (before its first chunk) and whenever it loads or creates the world
(Atlas and Drownhollow unload when empty). Each logs one line per world load: `NETHER_LIGHT`, `ATLAS_LIGHT`, `RUINS_LIGHT mode=wait world=<name>`.

## Measured

`scripts/light-lab` (README there): a throwaway Paper with the live server jar, live settings and live seed, and a bot that
visits never-seen terrain, leaves, and comes back. It parses the chunk packets and counts light emitters (lava, glowstone,
lanterns, torches, ...) whose own block light is below their emission ("dark"). View distance 4, four sites per world.

| world | dark on a first visit, before | after | dark on a revisit, after | chunk packets per first visit |
| --- | --- | --- | --- | --- |
| overworld | 0.0% of 113,000 | not changed | 0.0% | — |
| Nether | 14.5% of 185,000 (lava seas; fixed a few seconds later by re-sent chunks) | 0.0% of 154,000 | 0.0% | 210 → 102 |
| Atlas | 98.8% of 648 | 36.6% of 513 | 36.6% | 128 → 49 |
| Drownhollow | 100% of 195 | 38.6% of 140 | 38.3% | 129 → 49 |
| Backrooms (control) | 96.6% with the flag forced off | 0.0% | 0.0% | 60 → 42 |

The control proves the method: Backrooms with the flag forced off reproduces the original bug (a first visit arrives 97% dark,
the same chunks on a revisit 12%). The overworld needs nothing: JasprHorrorBiomes' `TerrainLighting`/`ChunkLight` floods every
new chunk's light before it is sent. The End had no light sources at the sampled sites and was not touched.

## Not fixed here (owner decision)

In Atlas and Drownhollow 37–39% of the lights in sent chunks are dark **on a revisit too**: the server never lit them. The
generators draw chunks from ChunkData, which carries no light, and the game's first light pass only reaches some sources:
mostly those under a roof that let light through. Lamps in the open (85% dark in Atlas) and solid ones such as sea lanterns
(71% dark in Atlas, 80% in Drownhollow) are missed. That is not a sending problem, and lighting them changes the game: mobs spawn
less beside them, and Drownhollow's Dread reads the server's light (`Horrors.lit`: block light >= 8), so its lanterns would
protect. JasprHorrorBiomes already does this for the overworld (`ChunkLight.initialize`); the same seeding for Atlas and
Drownhollow is about 60 lines per plugin, plus a pass that heals the chunks already saved, if you want it.

## Costs and side effects

- **One ring less of chunks.** The outermost ring waits until it is populated and lit, as in the game itself: with view
  distance 4 the client holds 7x7 chunks in Atlas and Drownhollow (was 9x9) and 72 of 81 in the Nether; at the live view
  distance of 6 that is 11x11 instead of 13x13. Raising `view-distance` for one world in `spigot.yml` buys it back at about a
  third more chunks per player there.
- **CPU: none measurable.** The setting adds one random light check per tick in a world that has players. Idle ticks with the
  flag off and on, alternating, were 0.9–1.7 ms either way in the Nether and 0.8–1.0 ms in Atlas (warm-up drift is larger than any difference).
- **Lamps the server never lit now get lit, out of sight.** The setting also turns on the game's random light checks (one per
  tick, within 5 blocks of a player). Measured in Atlas: a lamp the server had never lit was lit in the server's light within
  30 s of a player standing beside it (and stayed dark with the setting off). The browser does not see that light, but mobs
  spawn less beside such lamps and Drownhollow's Dread eases near its lanterns, slowly, where players linger.
- **About half as many chunk packets** in the Nether: its chunks no longer have to be sent twice.

## Re-check and roll back

- Live log after a restart: `NETHER_LIGHT mode=wait world=world_nether`; the first time Atlas or Drownhollow loads: `ATLAS_LIGHT` / `RUINS_LIGHT`.
- `node scripts/light-lab/light-lab.cjs --worlds nether,atlas,ruins --sites 4` (needs `bash scripts/light-lab/build-probe.sh` once).
- `node --test tests/light-before-sending.test.cjs` (the source and the shipped jars switch the flag on; nothing switches it off or writes `spigot.yml`).
- Roll back: restore `server/plugins/JasprNether.jar`, `JasprAtlas.jar`, `JasprRuins.jar` from `.runtime/deploy/backup-light-*` (or
  `Undo last deploy.bat`) and restart.
