# FPS update (client stage JASPR_FPS), 2026-10-12

Owner request: "massively optimized ... 165 frames consistently", with no changes to spawns, animations, gore/debris,
resolution or render distance ("preferably no compromises at all").

**Result: same picture, same game, more frames.** Every part of the stage is exact: it only stops the engine from doing the
same work twice. Nothing was turned off, capped or lowered.

## What changed (client only, `site/classes.js`, outermost stage)

| Part | What it does | Where it helps |
| --- | --- | --- |
| memo | a chunk rebuild asks each block and light value once instead of up to 7 times | chunk building |
| chunks | chunk lookups hit a small cache instead of hashing a 64-bit key (a BigInt in this client) | everything that reads blocks |
| rebuild | hidden faces skip the model lookup; the per-block scratch objects of the mesher are reused | chunk building |
| collide | collision checks read each column's chunk once and skip air | mob movement, particles |
| random | `java.util.Random` without BigInt (two 24-bit halves, bit-identical) | ticks, particles, meshing |
| brightness | each mob's light value is computed once per world change, not once per layer | mob rendering |
| entity | mob data reads use an array instead of a hash map; matrices go straight to the GPU buffer | mob rendering |
| ticks | the per-tick chunk sweep stops boxing the clock into a BigInt for every chunk | ticks |

Every part can be switched off for testing: `localStorage['jaspr.fps.v1'] = '{"memo":false}'`. `JasprFpsStatus()` in the
page shows hit/miss counters.

## Measured (this PC, 1920x1080, desktop "Performance" preset, frame cap lifted)

Interleaved A/B: the stage was switched on and off every second inside the same scene, so both modes saw the same view,
mobs and machine load.

| Scene | FPS on / off | p95 frame on / off | frames under 6.06 ms (165 FPS) |
| --- | --- | --- | --- |
| spawn (360 sweep) | 252 / 243 | 6.3 / 7.4 ms | 94% / 92% |
| horde (60 mobs + 6 mutants, night) | 187 / 168 | 7.6 / 8.8 ms | 83% / 74% |
| gore (horde killed) | 233 / 224 | 6.9 / 7.7 ms | 92% / 89% |

A full chunk reload (F3+A, 120 sections) took 387-442 ms on vs 669 ms off (35-42% faster). The worst section took
9.8 ms vs 11.7 ms.

## How it was checked

- Verify mode (`{"verify":true}`): every fast answer is also computed by the engine's own code and compared. 47 million
  checks gave 0 mismatches (spawn, horde and gore scenes).
- Mesh check: every chunk mesh uploaded to the GPU was hashed with the stage on and off. The meshes are byte-identical
  (flat and smooth lighting).
- `node --test tests/fps-client.test.cjs`: the build is reversible, the random streams match a BigInt `java.util.Random`
  (including switching modes mid-stream), and the entity cache and matrix store match the engine.

## What is left

The horde frames that still miss 165 are the 20 Hz game-tick frames, about 3 ms per tick for 66 mobs. That is ordinary
vanilla mob logic: movement, collisions, Mo' Bends ground checks and particles. Spreading that work over several frames
would change game timing, so it was not done.

GPU draw batching was measured as an upper bound: skipping every mob draw call gained only about 4%, so it was not
worth a rewrite.

## Note about this PC

The i5-12600K has P-cores and E-cores, and Windows 10 has no Thread Director. A browser tab that Windows puts on the
E-cores draws about half the frames. All the benchmarks pinned Chrome to the P-cores. Without pinning, the same build
varied between 95 and 193 FPS.

## Files

- `client-mods/fps/fps-stage.js`: the stage
- `scripts/build-fps-client.cjs`: builds it into the live `classes.js`
  - reversible: `--unpatch`
  - this must be the outermost stage, so unpatch it before rebuilding any other stage
- `tests/fps-client.test.cjs`: offline tests
- `tests/fps-bench.cjs`: the FPS benchmark
  - real GPU
  - lean fixture server with a copy of the live spawn regions
  - `--toggle`, `--profile`, scenes `meshcheck` and `rebuild`
- `tests/fps-profile-*.cjs`: CPU profile readers with Java names

LIVE 2026-10-12 14:17 (site only, no restart): key `classes.js?v=20261012-fps1`, backup
`.runtime/deploy/backup-fps1-20261012-141741`.
