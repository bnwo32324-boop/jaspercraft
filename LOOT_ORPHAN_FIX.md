# Empty chests, dead spawners and the spawn mix — JasperCraft

(3.20.0 covers the orphaned catalogue chests; 3.21.0 at the end covers the rest.)

Plugin `JasprHorrorBiomes 3.20.0`, deployed 2026-09-22. Live server only; the Structure
Testing Grounds world is unaffected (its chests are stocked at build time).

## The symptom

Chests inside expedition-catalogue structures opened empty. No error, no message, nothing
in the log — they simply contained nothing, for ever.

## How loot actually works

There are two completely different chest systems, and only one of them was broken.

**Stocked at generation — the 77 Claude Code set pieces and dungeons.** `Megaliths`,
`Landmarks`, `Anomalies`, `Relics`, `Metropolis`, `Wonders`, `Temples`, `Breach` and the
fifteen dungeon rooms run in a `BlockPopulator`, where real tile entities exist, and fill
their chests as they build them. Measured across all 317 captured structures: **387 chest
blocks, 387 with contents, none empty.** These were never the problem.

**Filled on opening — the 234 ChatGPT Codex catalogue designs.** These are stamped by the
`ChunkGenerator`, which can only write block ids, so their chests are placed empty and
`StructureLoot` fills them the first time a player opens one. Measured: **2,059 chest
blocks, all empty at generation, by design.** Every empty chest you found was one of these.

`StructureLoot.cache(Block)` decided whether a chest was a cache by asking
`WorldgenExpansion.sites(world, cx, cz)` for the structures in that chunk and looking for a
marker at exactly the chest's coordinates. No marker, no loot, and — importantly — no
complaint: the chest just opened as it was.

## The cause

`sites()` answers *"what may be placed here now"*. It is filtered by the admission rules,
and those rules changed long after this world was generated:

```java
/** Retain one tenth of each previous generator's candidate cells: 90% rarer. */
public static final double RELATIVE_STRUCTURE_DENSITY = 0.10;
```

plus a spawn-exclusion radius and a portal reserve. A structure built under the older,
denser rules is still standing — its blocks are on disk — but `sites()` no longer proposes
it, so every chest inside it became unrecognisable.

The evidence, in order:

- The loot journal had not been written since ~12 September.
- All **56** loot claims in 88,000 lines of server log happened under plugin v3.1–v3.2.
  From v3.3.0 onward: **98 server restarts, zero claims, zero errors.**
- Re-running today's planner against the 15 sites that had actually paid out loot:
  **15 of 15 no longer produced at all.**
- Scanning every chunk within 2,000 blocks of spawn: **0 structures that generation would
  still place, 109 structures standing there, 1,004 loot chests — 100% unrecognisable.**
  A band 3,200–4,800 blocks east: same picture, 22 structures, 188 chests, 100% dead.

## The fix

Identification and admission are now separate questions, because they always were:

- **`StructurePlanner.identify(seed, cx, cz)`** reconstructs every site a chunk could hold,
  skipping only the gates that decide *whether to build* — the density cull, the spawn
  exclusion, the portal reserve. Everything that decides *what and where* (anchor, biome,
  design choice, suitability) is untouched, so a reconstructed site is the same structure,
  in the same place, with the same markers. `plan()` and `planExpansion()` took a boolean;
  generation still calls them exactly as before.
- **`StructureLoot.cache()`** now searches those sites. It records whether generation would
  still place the site, because a reconstructed site's boss never spawns — so a vault that
  would otherwise be sealed behind "defeat the guardian first" for ever is left unlocked.
- **A last resort for design drift.** One of the 15 test sites is a cell whose *design*
  changed between generator versions, so no marker can ever line up. A chest standing on
  the architecture's own signature — a stone-brick pedestal with two blocks of clear air
  above — inside the rooms (not the approach corridor) of a structure that is demonstrably
  there is treated as a supply cache.
- **Claim keys are now the chest's position**, `namespace:worldUID:x:y:z`, instead of the
  site key and marker ordinal. Those moved every time the region layer tag advanced
  (`v1`→`v3`→`v4`→`v7`), which is how a looted chest could silently become unclaimed and an
  unlooted one unrecognisable. Coordinates do not move. The 56 historical claims used the
  old key shape, so those specific chests will refill once.
- The Fold threshold (`door` marker) had the identical bug and now uses the same path.
- `STRUCTURE_LOOT_CLAIM` log lines carry `reconstructed=1` and the chest coordinates, and
  `/wasteland status` reports a `reconstructed=` counter, so this is measurable from now on.

## What was verified

- **Generation is byte-for-byte unchanged**: every site over 169 legacy and 841 expansion
  cells is identical between the old jar and the new one — same key, position, floor and
  marker count. The world will not change.
- 14 of the 15 sites that historically paid out loot are recovered exactly, by design id and
  region; the 15th is the design-drift case the pedestal rule covers.
- 1,004 chests recovered near spawn, 188 in the eastern band, **0 chests lost.**
- `cache()` returns early for any block that is not a chest, so the wider search costs
  nothing in the explosion and hopper guards.

## If empty chests turn up again

Check `STRUCTURE_LOOT_CLAIM` in `.runtime\paper-console.log` first. Silence there while
players are opening chests means identification is failing again — most likely because
another admission rule was added. The rule to keep: **anything that changes which
structures get placed must not be allowed to change which structures can be recognised.**


---

# 3.21.0 — the second empty-chest cause, dead spawners, and the spawn mix

Deployed 2026-09-22. 3.20.0 fixed the *catalogue* chests, which are filled on opening.
It did not fix the *authored* chests, which are filled as they are built — and those were
broken by something else entirely.

## 1. Every authored chest was emptied by one line

`Megaliths` and `Dungeons` fill a chest like this:

```java
Chest chest = (Chest) b.getState();
Inventory inv = chest.getBlockInventory();   // the LIVE tile inventory
inv.setItem(...);                            // items go into the world
chest.update(true, false);                   // <- copies the SNAPSHOT back over them
```

`getState()` takes a snapshot *before* anything is added. `update()` writes that snapshot
back. Everything just placed is erased. `StructureLoot` documents this exact trap in its own
comment and avoids it; the four fill sites did not.

Measured on a real Paper 1.12.2 server:

| what the code does | items that survive |
|---|---|
| live inventory, then `update()` — what every set piece did | **0** |
| live inventory, no `update()` | 2 |
| snapshot inventory, then `update()` | 0 |
| `update()` first, then live inventory | 2 |

Fixed by removing the four `chest.update(...)` calls. The block type is already set before
the state is taken, so the call was doing nothing except destroying the contents.

**Before and after, same seed, same terrain, real server:** 3.20.0 → 4 chests found, **4
empty**. 3.21.0 → 4 chests found, **0 empty**.

Chests already in the world stay empty until touched, so `StructureLoot.restockAuthored`
fills any empty, unclaimed chest inside a located Megalith or dungeon room on first open,
from the same table, keyed to the chest's position. Logged as `STRUCTURE_LOOT_RESTOCK`.

## 2. Spawners did nothing because the rooms were daylit

A hostile mob will not spawn above light level 7 — lighting a spawner room is how you
disable it. The rooms were lit, and the reason is ordering:

```java
public void populate(World w, Random ignored, Chunk c) {
    ChunkLight.initialize(c);     // floods light for the terrain as it is now
    Dungeons.populate(...);       // *then* carves the rooms and builds the set pieces
```

Every room placed into ground that had been open kept that ground's sky light — 15, inside a
sealed chamber. Read straight out of the live world in region `r.-1.-2`: **34 of 58 spawners
sit in light above 7**, and the three nearest the player had `skyLight = 15, 7, 15`. That is
also why those rooms look daylit in-game.

Two changes:

- `RuinSupplies.populate` floods the light **again** after everything is built.
- `TerrainLighting` relights a chunk down to bedrock when it contains a spawner or a chest
  (a cheap, exact signal for "there is a room in here"), and asks `identify()` rather than
  `sites()`, so structures generation would no longer place still get relit. Existing rooms
  are corrected as chunks load.

Same-seed comparison: 3.20.0 → 1 of 2 spawners in light > 7. 3.21.0 → **0 of 2**, sky light 0
above the buried one.

## 3. Structures 1.5× more common

`RELATIVE_STRUCTURE_DENSITY` 0.10 → 0.15. The gate is a threshold on a fixed per-cell random,
so raising it only admits cells that were previously refused — **nothing already standing
moves, changes design or changes seed.** The legacy-overlap check inside `planExpansion` is
now skipped when identifying, because it consults which legacy sites are *admitted*, and that
answer moves with the density; without this, raising the density would have re-orphaned the
chests of everything already built.

## 4. Fewer animals, same sheep, more monsters

The world generated with no passive animals at all, because a custom `ChunkGenerator` never
runs vanilla's world-gen animal pass. `VanillaFauna` put that pass back and ran it twice, and
the surface filled with everything.

Thinning by count would have taken the sheep with it, so `SpawnBalance` thins by species
instead: **sheep are never touched**, and 65% of other passive spawns are dropped. Only the
world's own decisions are affected — world generation and the natural spawn tick. Breeding,
eggs, spawn eggs, spawners and plugin-placed mobs are never cancelled, so player farms are
untouched.

`bukkit.yml`: `monsters` 70 → 100, `ambient` 15 → 8 (bats), `ticks-per.animal-spawns`
400 → 600. Animal cap left at 15 so the sheep still have room.

## Metrics

`/wasteland status` now reports `reconstructed=` and `restocked=` alongside `lootClaims=`.
`STRUCTURE_LOOT_RESTOCK` lines carry the structure name and the chest coordinates.


---

# 3.22.0 — spawners that work in daylight

3.21.0 relit the rooms, which was necessary but not sufficient. Two things were still wrong.

**Relighting cannot fix a torch.** Measured across the live region the player is standing in,
the 58 spawners break down as: 37 in the dark, **19 sitting in *block* light 9–12** — actual
light sources built into the rooms — and the rest in stale sky light. Recomputing lighting
removes the stale sky light and nothing else. A torch three blocks from a spawner disables it
permanently, and always did.

**Vanilla will not do what was asked.** A hostile mob is refused above light level 7, and
between 1 and 7 it is a roll (`light <= random(8)`), not a threshold — so a dim room trickles
and a lit one gives nothing, at any hour. "All spawners should spawn mobs during daytime and
nighttime" is not achievable by adjusting light.

`SpawnerDrive` covers for the vanilla spawner rather than replacing it:

- Every real spawner firing is recorded from `SpawnerSpawnEvent`.
- Every 2 seconds, spawners within 16 blocks of a player — vanilla's own `RequiredPlayerRange`
  — are checked. One that has been silent for 10 seconds is driven by hand: the same mob, in
  vanilla's own box (±4 x/z, ±1 y), needing air at head and feet and something solid to stand
  on, under vanilla's `MaxNearbyEntities` cap of 6.
- Where vanilla works, it is left alone and the rate is unchanged.
- Nothing is written to any spawner's data, so deleting this class restores stock behaviour.

**Verified on a real 1.12.2 server**: a zombie spawner at noon, on open ground with sky light
15 and a torch beside it — the worst case vanilla has — produced **6 zombies**, stopping
exactly at the nearby cap. Vanilla's contribution there is zero by rule.

Driven spawns use `SpawnReason.CUSTOM`, so `LiminalWorld`'s foyer ban and `StructureEncounters`'
tagging rules still apply, and `SpawnBalance` ignores them (it only touches natural and
world-gen passive spawns). `/wasteland status` reports `spawnersDriven=` and `spawnersCovered=`.

One consequence worth knowing: a zombie driven into a room that is genuinely open to the sky
will burn, exactly as a vanilla one would. Enclosed rooms are unaffected — burning is decided
by whether the sky is visible, not by the light array, so it is not affected by stale light.
