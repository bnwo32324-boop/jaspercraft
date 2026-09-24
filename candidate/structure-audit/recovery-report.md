# JasprHorrorBiomes source recovery (3.19.0 tree -> live 3.23.0)

Date: 2026-09-22. `SA` = `candidate\structure-audit`, `TREE` = `server\custom-plugins\JasprHorrorBiomes`.

## Result

**Success.** The canonical source tree `TREE` now compiles to the live jar
`server\plugins\JasprHorrorBiomes.jar` (3.23.0, md5 `606115e9b4288455bd9cd7351422d123`). The only
differences left are artefacts of the compiler version. The live jar was built by **JDK 21.0.10**
(its manifest says `Created-By: 21.0.10 (Ubuntu)`); the standard build here is JDK 17.

- Backup of the 3.19.0 tree: `SA\backup-pc319\JasprHorrorBiomes`. The backup was checked file by
  file (md5) against the original before any edit: 51 files.
- Nothing was built for, or copied to, the live server. `server\plugins\update\` is empty.
  The only files changed outside `SA` are in `TREE`, as listed below.
- To re-run every check: `bash candidate/structure-audit/recovery/verify.sh`. It ends with
  `RESULT: CLEAN`, and its last output is saved in `SA\recovery\verify-output.txt`.

## What changed, file by file

All changes were ported into the original, human-written files, in the house style of each
file. Comments were added where the handoff explains the intent. The full diff is in
`SA\recovery\pc319-to-recovered.diff`.

| file | change | release |
|---|---|---|
| `resources\plugin.yml` | `version: 3.19.0` -> `3.23.0` (copied from the live jar) | 3.23.0 |
| `StructurePlanner.java` | `RELATIVE_STRUCTURE_DENSITY` 0.10 -> 0.15. New `IDENTIFY_CACHE` and `IDENTIFY_EXPANSION_CACHE` (anonymous classes `$3`/`$4`). `plan` and `planExpansion` gain an `admit` flag; the 3-argument forms call it with `true`. With `admit=false`, identification skips density, the disabled list, spawn exclusion, the portal reserve and legacy overlap. New public `identify`, `identifyRegion`, `identifyExpansion`, which catch `RuntimeException` and store the result as null. New `Marker.reconstructed(x,y,z,kind)`, whose ordinal is `abs((int)mix(x*341873128712L+y*132897987541L+z))%4096`. A disabled design makes `plan`/`planExpansion` return null for the whole cell. | 3.20 / 3.21 / 3.23 |
| `StructureLoot.java` | New fields `reconstructed`, `restocked`, `terrain`, `caves`, `terrainWorld`. `metrics()` gains `reconstructed=` and `restocked=`. `Cache` gains `reconstructed`; its claim key is now `ns:worldUID:x:y:z`, based on position. `cache()` only considers CHEST/TRAPPED_CHEST and looks sites up through `identify()`. Its pedestal fallback (smooth stone brick below, 2 air above, inside the site's room grid) builds `Marker.reconstructed(...,"supply")`. The vault of a reconstructed site is never locked. New `terrain(World)` and `restockAuthored()`: an empty, unclaimed chest inside a located register piece or dungeon room is journaled first and then filled through `Dungeons.restock`; the log line is `STRUCTURE_LOOT_RESTOCK site= slots= at=`. `open()` calls `restockAuthored` when no cache matches. The claim log adds ` reconstructed=1` and ` at=x,y,z`. The Fold door uses `identify()`. | 3.20 / 3.21 |
| `Dungeons.java` | `chest.update(true,false)` removed from `fill()`. New `public static int restock(Block,Terrain,Caves,boolean rich)`. | 3.21 |
| `Megaliths.java` | `chest.update(true,false)` removed from `graded`, `trove` and `loot`. The spawner's `cs.update` in `mob()` is kept, as live. `populate()` gates each of the 11 set pieces with `DisabledStructures.any(name[, alias])`. | 3.21 / 3.23 |
| `RuinSupplies.java` | A second `ChunkLight.initialize(c)` runs after dungeons, fauna and springs, inside try/catch; failures log `RELIGHT_FAILED`. | 3.21 |
| `TerrainLighting.java` | The site list comes from `WorldgenExpansion.identify()`. A chunk holding a CreatureSpawner or a Chest sets `tiles`, and then `low=1` (relit down to bedrock). See the finding about the dangling `else` below. | 3.21 |
| `WorldgenExpansion.java` | New `identify(World,cx,cz)` that delegates to `StructurePlanner.identify(seed,cx,cz)`. | 3.20 |
| `HorrorPlugin.java` | New fields `spawnBalance` and `spawnerDrive`. `DisabledStructures.load(dataFolder, logger)` runs before the worlds attach. SpawnBalance and SpawnerDrive are started after TerrainLighting and stopped in `onDisable`. New accessor `spawnerDrive()`. The STRUCTURES_READY line computes `relativeStructureDensity=` from the constant (prints 15). | 3.21 / 3.22 / 3.23 |
| `DisabledStructures.java` (new) | Reads `disabled-structures.txt` from the data folder and creates it with a header on first run. Names are compared trimmed and lower-cased; `#` lines are comments. Logs `STRUCTURES_DISABLED count=N[ names=[..]]`, or `DISABLED_STRUCTURES_UNREADABLE` (and then disables nothing). Provides `any(String...)` and `count()`. | 3.23 |
| `SpawnBalance.java` (new) | Cancels 65% of NATURAL / CHUNK_GEN spawns of `Animals`, except `Sheep`. | 3.21 |
| `SpawnerDrive.java` (new) | Sweeps every 40 ticks for authenticated players, covering chunks within ±2 that are already loaded and spawners within 16 blocks. A spawner that fired within the last 200 ticks is left alone. The first sighting only records the spawner. Otherwise one mob is spawned: same type, ±4 x/z and ±1 y, feet and head open (AIR/LONG_GRASS/SNOW) over a solid block, at most 6 of that type already nearby, up to 12 attempts. | 3.22 |

The three new classes were written as readable sources with meaningful names. They reproduce the
decompiled logic exactly: the same constants, strings, log formats and branch structure. The
bytecode check below confirms this.

Resources: `biomes.tsv`, `biome-details.tsv` and `structures\catalog-v1.tsv` were already
identical to the live jar (md5 893dbd3a.., be63ade4.., 536cad8f..). Only `plugin.yml` differed, and
it was copied from the live jar.

## Verification evidence

Build: JDK 17 `javac --release 8 -encoding UTF-8 -cp server\cache\patched_1.12.2.jar`, output in
`SA\merged-classes` (111 classes: the live jar's 109 plus the 2 JDK 17 SwitchMap holders). The jar
with resources is `SA\merged.jar`. It was decompiled with Fernflower from the Android Studio JBR
(Java 25), flags `-dgs=1 -rsy=1`, into `SA\src-merged`.

1. **Decompiled source vs `src-live`:** 48 of 50 files are byte-identical, and both trees contain the
   same set of files. The two residues, `BiomeDetails.java` (192 raw differing lines) and
   `StructureEncounters.java` (107), are the enum-switch artefact. `recovery\switchnorm.py`
   rewrites every enum switch on both sides into one canonical form: selector `.ordinal()`, enum
   constant names turned into ordinals, fallthrough resolved, labels that behave like `default`
   dropped, labels sorted. After that, both files show **0 differing lines** (2 + 3 enum switches
   canonicalised per side). Negative controls were run on mutated copies of the live files: a
   relabelled case, a changed constant in a case body, a removed `break`, and a changed line outside
   any switch. Each was detected (2 to 17 differing lines).
2. **`javap -c -p -constants`, every class without an enum switch** (107 of 109; only BiomeDetails
   and StructureEncounters contain enum switches): **107/107 identical** to live after
   `recovery\jpnorm.py` normalisation. The rules are:
   (a) remove constant-pool indices and write `ldc_w` as `ldc`;
   (b) replace byte offsets with instruction ordinals, remapping branch, switch and
   exception-table targets, so instruction order and control flow are compared exactly;
   (c) normalise the JDK 17 vs JDK 18+ encoding of Object methods called on an interface receiver
   (`invokevirtual Object.getClass` vs `invokeinterface Iface.getClass`, JDK-8272564).
   Rule (c) changed the comparison at exactly 4 call sites, all in classes the recovery did not
   touch: `ExpeditionLoot.call` and `HorrorPlugin.authenticated` (`Plugin.getClass()`), and two in
   `VanillaFauna.resolve` (`Server`/`World.getClass()`).
   With pool indices stripped and **no other rule**, 104 of 107 are identical; the 3 exceptions are
   those classes. 67 of 109 class files are byte-identical to live.
   Negative control: the untouched PC 3.19 build (`SA\pc319`) under the same rules still shows 10
   differing and 5 missing classes, so the rules do not hide real changes.
3. **Cross-check of the two enum-switch classes:** a JDK 25 build of the same tree
   (`SA\recovery\merged-classes-jdk25`) switches on `ordinal()` like JDK 21. BiomeDetails and
   StructureEncounters with all their nested classes (15 classes) are identical to live after
   stripping pool indices and normalising lambda numbering (`lambda$bolt$0` vs `lambda$bolt$1`,
   a JDK 23+ change). JDK 25 has further artefacts of its own in 4 other classes (synthetic
   `access$`/constructor member order, anonymous-class constructor store order). Those 4 classes
   are already identical under JDK 17.
4. The tools and outputs are in `SA\recovery\`: `verify.sh`, `verify-output.txt`, `jpnorm.py`,
   `jpcompare.py` (pool indices only), `switchnorm.py`, `pc319-to-recovered.diff`, and the two
   one-off patch scripts that were used.

## Findings (behaviour as shipped in live 3.23.0, reproduced exactly, NOT fixed)

1. **TerrainLighting dangling `else`.** The live bytecode is
   `if(horror)for(site)low=min(low,site.y-12); if(tiles)low=1; else low=48;`. The old
   `else low=48` now binds to `if(tiles)`, so the site-depth floor is always overwritten. Every
   column is relit from y=1 (chunks holding a chest or spawner) or from y=48 (all other chunks),
   including buried structures deeper than y=48 that have no chest or spawner. The comment in the
   source flags this.
2. **Dungeons.restock loot style.** It calls `caves.region(x>>4, z>>4)`, passing chunk coordinates
   where block coordinates are expected, so restocked chests take their theme from the wrong cave
   region. The items are still valid; only the theme is affected. Flagged in a comment.
3. **Disabled list coverage.** Only the 11 Megaliths set pieces and all catalogue designs are
   gated. Landmarks, Anomalies, Relics, Metropolis, Wonders, Temples, Breach and the 15 dungeon
   rooms are not gated, so listing one of those names has no effect. A disabled catalogue design
   leaves its whole cell empty; no other design is tried. Recorded in the DisabledStructures
   Javadoc.
4. **`/where` uses `WorldgenExpansion.sites()`** (admission; `Where.java:64`), not `identify()`. If
   a catalogue design is ever disabled, `/where` stops naming copies of it that are already built.
   Loot still recognises them. This breaks the "placement must not change recognition" rule for
   `/where`. It is unchanged since 3.19 and was not touched.
5. **Metrics that are not wired in.** `SpawnerDrive.status()`, `SpawnBalance.status()`,
   `DisabledStructures.count()` and `HorrorPlugin.spawnerDrive()` are referenced by nothing in the
   live jar. `/wasteland status` does **not** show `spawnersDriven=`/`spawnersCovered=`; handoff
   §8.7 is wrong on that point. `restocked=` and `reconstructed=` do appear, through
   `loot.metrics()`.
6. **Stale strings kept byte-identical:** the `/wasteland status` text says "90% rarer globally",
   and HORROR_BIOMES_READY says `structureDensity=10%`. The real density is 0.15.
   STRUCTURES_READY prints 15%.

## JasprApocalypse (report only; nothing modified)

- Source: `server\custom-plugins\JasprApocalypse` (24 classes in `src`, plus `resources\plugin.yml`
  and `config.yml`). Live jar: `server\plugins\JasprApocalypse.jar`, 3.6.0, md5
  `07da773b8f99d9a78c929b980b7efb32`, also built with JDK 21.0.10.
- Compile classpath, as in `scripts\build-apocalypse.ps1`:
  `server\cache\patched_1.12.2.jar;server\plugins\AuthMe.jar`. In Git Bash, pass it with
  Windows-form paths (`C:/...;C:/...`).
- **The sources match the live jar**, and `Ruins.java` (dated 2026-09-06) matches too.
  - The Fernflower decompiles of the JDK 17 build and the live jar are byte-identical for all
    24 source files.
  - Under the JDK 17 bytecode comparison, 72 of 75 classes are identical. The others are the
    enum-switch artefact only: the `Ruins.Family` switch, where JDK 17 adds a second SwitchMap to
    `Ruins$2`, and `Arsenal`'s switches over its nested `Pattern`/`Gun` enums, where JDK 17 adds
    the extra class `Arsenal$4`.
  - A JDK 25 build differs only by synthetic member order and anonymous-class constructor order,
    in 7 classes. Each of those 7 diffs contains the same instructions on both sides, only in a
    different order.
  - `plugin.yml` and `config.yml` are identical to the jar's.
- Working files: `SA\apocalypse-check\` (live extract, JDK 17 and JDK 25 builds, decompiles, javap
  diffs).

## Status for the audit

`TREE` is now at 3.23.0 and may be used as the base for the structure repairs, including the
valuable-block palette. Build it with the standard command, but deploy only through the
orchestrator's final `plugins\update\` step. The version in `plugin.yml` should be bumped when the
first repair lands.
