# JasperCraft structure spawn-rate census (new chunks)

Live world: Minecraft 1.12.2 Paper, seed 3127727864271777472. Generators measured: **JasprHorrorBiomes 3.24.1** (the live `plugins/JasprHorrorBiomes.jar`, md5 79d6ad4c..., which differs from `SA/jars/final-3.24.1.jar` only in `WaterRepair.class`, a post-placement water fix; every placement class is byte-identical) and **JasprImportedWorldgen 1.0.0** (md5 ad09e06a..., data files copied read-only, catalog/manifest SHA-256 match the plugin's release identity). Live config honoured: `disabled-structures.txt` = *The Interceptor*; HorrorBiomes expansion boundary 0 protected chunks; imported boundary 5,865 protected chunks (all within 1,410 blocks of spawn, so they do not touch the census domain). Generated 2026-09-23 by `SA/census` (PureCensus.java, CensusProbe.java, census.py, report.py).

**Rate** = share of new chunks that are the *anchor chunk* of that structure (the lattice/region anchor, the imported plan origin, the chunk holding a spawner room). Per 1,000 x 1,000 blocks = rate x 3,906.25 chunks. Spacing = the square that holds one on average. Domain: square around (0,0) minus the 3,072-block spawn disk (chunk centres).

## Summary by source

| source | structures | generating | rate: % of new chunks hosting an anchor (sum) | chunk hosts >= 1 (Poisson est.) | per 1,000 x 1,000 blocks | spacing |
|---|---:|---:|---:|---:|---:|---|
| Claude Code set piece | 62 | 61 | 0.483% | 0.482% | 18.88 | 1 per 230 x 230 |
| Claude Code dungeon room | 15 | 15 | 3.927% | 3.851% | 154.84 | 1 per 80 x 80 |
| ChatGPT Codex catalogue (HorrorBiomes) | 234 | 234 | 0.027% | 0.027% | 1.06 | 1 per 970 x 970 |
| JasprImportedWorldgen GLM_freebuff | 45 | 45 | 0.015% | 0.015% | 0.59 | 1 per 1,300 x 1,300 |
| JasprImportedWorldgen ChatGPT_Codex_Structures | 98 | 98 | 0.009% | 0.009% | 0.36 | 1 per 1,700 x 1,700 |
| other (HorrorBiomes portal sanctuary, ChatGPT Codex) | 1 | 1 | 0.002% | 0.002% | 0.06 | 1 per 4,100 x 4,100 |
| other (JasprApocalypse legacy ruin, ChatGPT Codex) | 6 | 0 | 0.000% | 0.000% | 0.00 | never |
| other (the Fold, ChatGPT Codex) | 16 | 0 | 0.000% | 0.000% | 0.00 | never |
| **all sources** | 477 | 454 | 4.463% | **4.39%** | **175.78** | 1 per 80 x 80 |

- **Chance a random new chunk hosts any structure anchor: 4.39%** = exact union of all lattice/region/plan anchor chunks (2.743%: 682,669 of 24,884,184 chunks in the 80k x 80k-block common domain; set pieces, lattice rooms, catalogue, sanctuary, imported) combined with the measured spawner-room share (1.689%), assumed independent. Measured directly in the generated areas: 4.71% of 69,120 chunks (random areas only: 4.46% of 29,952).
- **Structures per 1,000 x 1,000 blocks: 175.78** (HorrorBiomes 174.84, JasprImportedWorldgen 0.943). Lattice rooms dominate the count; spawner rooms are counted per room.

## Collisions between JasprImportedWorldgen and HorrorBiomes structures

**How they meet.** In every chunk the HorrorBiomes catalogue is stamped by the chunk generator, then the HorrorBiomes populator (`RuinSupplies`: lattice rooms, spawner rooms, set pieces) runs, then JasprImportedWorldgen's populator stamps its tile over whatever is there (populator list on the live server: `RuinSupplies`, `ImportsPopulator`). So in any overlap the imported structure wins block by block. Its admission check (`ClaimGuard` + `conflictsWithExpeditions`) rejects plans that touch: catalogue sites (+16 blocks, all heights), register set-piece claim boxes (floor-10 to floor+height+7, +2), lattice-room boxes (floor-2 to floor+height+3, +2) and portal-sanctuary chunks (+2 chunks). It does **not** know about: vanilla-style spawner rooms (block-dependent), the ladder shafts that carry every buried lattice room up to daylight, set pieces' shafts, footings (down to floor-32) and excavation above the claim box, or the 6-block clearing round surface rooms. HorrorBiomes 3.24.1 in turn never asks about imported sites.

**Predicted from the placement functions** (all 151,009 imported plans admitted in the 400k x 400k-block domain; HorrorBiomes neighbours from the plugins' own functions):

| configuration (HorrorBiomes structure relative to the imported plan box; claim boxes) | plans | share of imported plans |
|---|---:|---:|
| buried room below import +shaft column inside import footprint | 19,991 | 13.24% |
| surface room overlapY import [cordon/clearing only] | 10,973 | 7.27% |
| buried room below import | 9,049 | 5.99% |
| buried room below import [cordon/clearing only] | 6,792 | 4.50% |
| buried set piece below import | 6,761 | 4.48% |
| surface room above import | 1,432 | 0.95% |
| surface room above import [cordon/clearing only] | 1,368 | 0.91% |
| surface set piece above import | 775 | 0.51% |
| surface room below import | 659 | 0.44% |
| buried set piece (disabled Interceptor, not built) below import | 595 | 0.39% |
| buried set piece below import [cordon/clearing only] | 447 | 0.30% |
| surface room below import [cordon/clearing only] | 414 | 0.27% |
| any HorrorBiomes set piece or lattice room within the footprint + cordon | 49,112 | 32.52% |

**Measured in generated chunks** (test server, both live jars, 30 imported cells of 48 x 48 chunks: 13 random + 17 targeted at the configurations above; 24 imported structures stamped):

| | all generated imported structures | random cells only |
|---|---:|---:|
| imported structures stamped | 24 | 7 |
| with a 3D bounding-box overlap with a HorrorBiomes structure (its solid-block extent) | 10 | 1 |
| with HorrorBiomes blocks actually overwritten by the imported stamp | 5 | 1 |
| - buried lattice room: bbox overlap / overwritten (structures) | 6 / 3 | 0 / 0 |
| - set piece: bbox overlap / overwritten (structures) | 4 / 2 | 1 / 1 |
| HorrorBiomes blocks overwritten (solid + carved air) | 138 | 12 |
| spawners / chests / ladder blocks overwritten | 0 / 0 / 32 | 0 / 0 / 3 |
| HorrorBiomes solid blocks left standing inside an imported structure's box (intrusions) | 405 | 8 |

**Examples** (world coordinates; `samples` = x,y,z, HorrorBiomes block id -> imported block id):

| imported structure (box x0,y0,z0 - x1,y1,z1) | HorrorBiomes structure | overwritten solid/air | spawner/chest/ladder lost | intrusions | sample blocks | cell |
|---|---|---:|---:|---:|---|---|
| codex:cbd_060 (146928, 61, -178432, 147025, 132, -178358) | reg:16@9183,-11148 [set piece] | 35/0 | 0/0/9 | 35 | 146948,66,-178367 251->1; 146947,66,-178366 251->1 | target: buried set piece below import | codex:cbd_060 land c |
| codex:cbd_040 (29568, 58, 139632, 29669, 138, 139740) | dun:4B@1850,8728 [buried lattice room] | 40/0 | 0/0/8 | 25 | 29603,63,139654 4->48; 29602,63,139655 4->48 | target: buried room below import +shaft column inside import |
| codex:cbd_073 (154160, 71, 33568, 154245, 137, 33676) | reg:38@9639,2098 [set piece] | 12/0 | 0/0/3 | 8 | 154226,71,33571 251->98; 154227,71,33571 65->98 | random cell 200,43 |
| codex:cbd_094 (-13936, 75, 70896, -13874, 134, 70961) | dun:2B@-871,4433 [buried lattice room] | 7/0 | 0/0/3 | 33 | -13934,75,70931 65->98; -13934,76,70931 65->98 | target: buried room below import +shaft column inside import |
| codex:cbd_067 (-157552, 61, 83824, -157474, 135, 83919) | dun:3A@-9844,5241 [buried lattice room] | 6/0 | 0/0/2 | 4 | -157497,61,83868 65->98; -157497,62,83867 98->98 | target: buried room below import +shaft column inside import |

Bounding-box overlaps where nothing was overwritten (HorrorBiomes solid blocks left standing inside the imported structure's box):

| imported structure (box) | HorrorBiomes structure | HB solid blocks inside the imported box | cell |
|---|---|---:|---|
| codex:cbd_071 (-96464, 120, -126608, -96337, 197, -126488) | reg:58@-6022,-7912 [set piece] | 106 | target: surface set piece below import | codex:cbd_071 sky c |
| codex:cbd_040 (29568, 58, 139632, 29669, 138, 139740) | dun:1B@1853,8727 [buried lattice room] | 65 | target: buried room below import +shaft column inside import |
| codex:cbd_052 (46224, 67, 39728, 46339, 138, 39822) | dun:0A@2896,2486 [buried lattice room] | 26 | target: buried room below import +shaft column inside import |
| codex:cbd_035 (8544, 59, 41872, 8675, 151, 41995) | dun:0A@542,2618 [buried lattice room] | 16 | target: buried room below import | codex:cbd_035 shore cell  |

Outcome per targeted configuration (cells generated / imported structure stamped / bbox overlap / blocks overwritten / ladder blocks lost):

| configuration targeted | cells | stamped | bbox overlap | overwritten | ladder blocks lost |
|---|---:|---:|---:|---:|---:|
| buried room below import +shaft column inside import footprint | 4 | 4 | 4 | 3 | 20 |
| buried set piece below import | 3 | 3 | 3 | 1 | 9 |
| surface room overlapY import [cordon/clearing only] | 2 | 2 | 0 | 0 | 0 |
| surface room above import | 2 | 2 | 0 | 0 | 0 |
| surface set piece above import | 2 | 2 | 0 | 0 | 0 |
| surface set piece below import | 2 | 2 | 1 | 0 | 0 |
| surface room below import | 1 | 1 | 0 | 0 | 0 |
| random cell | 13 | 7 | 1 | 1 | 3 |
| buried room below import | 1 | 1 | 1 | 0 | 0 |

No spawner room, chest, spawner, catalogue site or portal sanctuary was touched in these 24 stamps; spawner rooms sit at cave depth and most imported structures are surface builds (the 0.95% + 0.51% of plans that are underground under surface rooms/set pieces produced no overlap in the 4 targeted cells generated).

## Validation: pure prediction vs the generated test world

Every HorrorBiomes structure write in the 69,120 generated chunks was attributed to its builder (CaptureHook + stack walk) and matched to the instance the census predicts for that lattice cell / region; imported stamps were detected as block changes made by the imported populator.

| check | count |
|---|---:|
| cat built, NOT predicted | 0 |
| cat predicted+built | 21 |
| cat predicted, NOT built | 0 |
| dun built, NOT predicted | 0 |
| dun predicted+built | 1,633 |
| dun predicted, NOT built | 0 |
| reg built, NOT predicted | 0 |
| reg disabled (Interceptor) predicted, correctly not built | 5 |
| reg predicted+built | 320 |
| reg predicted, NOT built | 0 |
| sanct built, NOT predicted | 0 |
| sanct predicted+built | 1 |
| sanct predicted, NOT built | 0 |
| imported cell: no plan predicted, none stamped | 6 |
| imported cell: plan predicted and stamped at the predicted box | 24 |

## Method and sample sizes

- **Pure census** (`PureCensus.java`, standalone Java 17 with the live HorrorBiomes jar, the imported jar and Paper on the class path; private placement functions called by reflection, nothing re-implemented except where noted):
  - set pieces: `Megaliths.weather/fits/claimed/baseY` on every lattice cell in a 400k x 400k-block square (624,884,184 chunks, 6,085,400 candidate cells; per set piece 3,138-340k cells). The builders anchor exactly on this register (`register_drift.py`: 0 drift for 3.24.1), `disabled-structures.txt` honoured.
  - lattice rooms: `Dungeons.anchor/surfaceAnchor(admit=true)` (includes the yield to set pieces and catalogue) on both lattices, 120k square (56,134,184 chunks, 1,554,041 cells).
  - catalogue: `StructurePlanner.region/expansionRegion` (admit=true: density 0.20, spawn exclusion, portal reserve, legacy overlap, set-piece yield, disabled list) over 956,483 legacy + 6,801,654 expansion regions (1,000k square); expansion boundary empty live.
  - imported: `CellPlanner.choose` with the plugin's own Ground (`ClaimGuard`, `conflictsWithExpeditions`, live boundary copy) over 6,780,764 cells (2,000k square); a replica that also records why attempts fail was checked against `choose()` on 850,205 cells: 0 mismatches.
  - portal sanctuary: exact arithmetic. Anchor union for the any-structure chance: 80k square, all lattice families.
- **Empirical** (`CensusProbe` plugin on a throwaway Paper test server, port 25597/25598, live jars + read-only data copies, `disabled-structures.txt` and imported boundary copied): 30 cells of 48 x 48 chunks (69,120 chunks) generated and populated; populators `RuinSupplies -> probe(pre) -> ImportsPopulator -> probe(post)`; spawner rooms counted from `Dungeons.plain` writes; collisions = HorrorBiomes blocks whose state the imported populator changed, plus 3D bbox overlaps. Sample: see the dun:14 row. Relative standard errors per structure are in census.csv (binomial per lattice cell; 1/sqrt(n) for catalogue/imported).
- Structures whose rate still has > 10% relative error despite the large domain (too rare): 1 - glm:B44 (45%)

## Other finding: planner exceptions far from spawn

`StructurePlanner.region` (legacy catalogue grid) throws `IllegalStateException: Approach cannot reach surface` for legacy region(s) 229:242:gluttony_carrion_drain (x/z = region x 1,024; ~342,000 blocks from spawn); the expansion planner catches this exception, the legacy planner does not. It is the only one found (catalogue scanned to 500,000 blocks from spawn, imported admission checks to 1,000,000; none within 200,000). Every lookup that reaches it throws: 25 failures were logged (neighbouring expansion regions, whose legacy-overlap test calls it, and 14 imported cells whose admission checks consult it). In live generation chunks near it would fail in the chunk generator (catalogue stamping), the HorrorBiomes populator would log `DUNGEON_FAILED`, and JasprImportedWorldgen would disable itself (`IMPORTED_TILE_FAILED`). Those sites and cells are left out of the counts (negligible).

## Every structure

Full placement rules, restrictions, sample sizes and relative errors are in `census.csv` (one row per structure, same order).

### Claude Code set piece (62)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| The Lost Metro | reg:61 | 0.02259% | 0.8825 | about 1 per 1,100 x 1,100 blocks | surface: ground under the footprint within 9 blocks (4-block samples), all samples y64-136 |
| The Room | reg:60 | 0.01951% | 0.7620 | about 1 per 1,100 x 1,100 blocks | surface: ground under the footprint within 5 blocks (4-block samples), all samples y64-136 |
| The Lost Tower | reg:58 | 0.01743% | 0.6809 | about 1 per 1,200 x 1,200 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| The Lost Blocks | reg:59 | 0.01705% | 0.6658 | about 1 per 1,200 x 1,200 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Brass Foundry | reg:56 | 0.01467% | 0.5732 | about 1 per 1,300 x 1,300 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| The Village | reg:54 | 0.01412% | 0.5514 | about 1 per 1,300 x 1,300 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| The Signal | reg:24 | 0.01398% | 0.5460 | about 1 per 1,400 x 1,400 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Walking House | reg:53 | 0.01343% | 0.5246 | about 1 per 1,400 x 1,400 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| Raccoon Street | reg:57 | 0.01294% | 0.5054 | about 1 per 1,400 x 1,400 blocks | surface: ground under the footprint within 5 blocks (4-block samples), all samples y64-136 |
| The Neon Arcology | reg:49 | 0.01259% | 0.4916 | about 1 per 1,400 x 1,400 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| The Warren | reg:22 | 0.01228% | 0.4797 | about 1 per 1,400 x 1,400 blocks | buried: ceiling 10 blocks under the lowest ground sample and 12 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| Phobos Anomaly | reg:40 | 0.01220% | 0.4768 | about 1 per 1,400 x 1,400 blocks | buried: ceiling 10 blocks under the lowest ground sample and 12 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Kennels | reg:21 | 0.01188% | 0.4639 | about 1 per 1,500 x 1,500 blocks | buried: ceiling 10 blocks under the lowest ground sample and 14 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Gate | reg:39 | 0.01180% | 0.4609 | about 1 per 1,500 x 1,500 blocks | buried: ceiling 10 blocks under the lowest ground sample and 16 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Stairwell | reg:55 | 0.01144% | 0.4469 | about 1 per 1,500 x 1,500 blocks | buried: ceiling 10 blocks under the lowest ground sample and 56 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| Raccoon Precinct | reg:51 | 0.01139% | 0.4450 | about 1 per 1,500 x 1,500 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| AM | reg:38 | 0.01139% | 0.4449 | about 1 per 1,500 x 1,500 blocks | buried: ceiling 10 blocks under the lowest ground sample and 26 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| Delta Labs | reg:35 | 0.01089% | 0.4252 | about 1 per 1,500 x 1,500 blocks | buried: ceiling 10 blocks under the lowest ground sample and 11 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Viewing Room | reg:23 | 0.01079% | 0.4217 | about 1 per 1,500 x 1,500 blocks | buried: ceiling 10 blocks under the lowest ground sample and 16 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Impossible Stair | reg:44 | 0.01049% | 0.4098 | about 1 per 1,600 x 1,600 blocks | surface: ground under the footprint within 10 blocks (4-block samples), all samples y64-136 |
| The Pit | reg:20 | 0.01038% | 0.4056 | about 1 per 1,600 x 1,600 blocks | buried: ceiling 10 blocks under the lowest ground sample and 20 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Corroded Place | reg:34 | 0.01034% | 0.4040 | about 1 per 1,600 x 1,600 blocks | buried: ceiling 10 blocks under the lowest ground sample and 14 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| Spencer Manor | reg:47 | 0.01023% | 0.3995 | about 1 per 1,600 x 1,600 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| The Golden Arches | reg:50 | 0.01000% | 0.3907 | about 1 per 1,600 x 1,600 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| The Bell | reg:48 | 0.009518% | 0.3718 | about 1 per 1,600 x 1,600 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| The Hive | reg:31 | 0.009284% | 0.3626 | about 1 per 1,700 x 1,700 blocks | buried: ceiling 10 blocks under the lowest ground sample and 16 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Sundowner | reg:52 | 0.008760% | 0.3422 | about 1 per 1,700 x 1,700 blocks | surface: ground under the footprint within 5 blocks (4-block samples), all samples y64-136 |
| The Store | reg:16 | 0.008427% | 0.3292 | about 1 per 1,700 x 1,700 blocks | buried: ceiling 10 blocks under the lowest ground sample and 14 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Spire | reg:41 | 0.008406% | 0.3284 | about 1 per 1,700 x 1,700 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| The Outpost | reg:46 | 0.008045% | 0.3143 | about 1 per 1,800 x 1,800 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| Vault 44 | reg:13 | 0.007876% | 0.3077 | about 1 per 1,800 x 1,800 blocks | buried: ceiling 10 blocks under the lowest ground sample and 18 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Lodge | reg:45 | 0.007669% | 0.2996 | about 1 per 1,800 x 1,800 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| Site-19 | reg:10 | 0.007074% | 0.2763 | about 1 per 1,900 x 1,900 blocks | buried: ceiling 10 blocks under the lowest ground sample and 20 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| The Grand Meridian | reg:43 | 0.006851% | 0.2676 | about 1 per 1,900 x 1,900 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| Survival Town | reg:36 | 0.006390% | 0.2496 | about 1 per 2,000 x 2,000 blocks | surface: ground under the footprint within 6 blocks (4-block samples), all samples y64-136 |
| Flight 226 | reg:42 | 0.006202% | 0.2423 | about 1 per 2,000 x 2,000 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Buried City | reg:5 | 0.006092% | 0.2380 | about 1 per 2,000 x 2,000 blocks | buried: ceiling 10 blocks under the lowest ground sample and 26 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| Bombfall | reg:37 | 0.006068% | 0.2370 | about 1 per 2,100 x 2,100 blocks | surface: ground under the footprint within 9 blocks (4-block samples), all samples y64-136 |
| The Hallowed Reach | reg:19 | 0.004894% | 0.1912 | about 1 per 2,300 x 2,300 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| The Super Mall | reg:33 | 0.004734% | 0.1849 | about 1 per 2,300 x 2,300 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Interchange | reg:9 | 0.004608% | 0.1800 | about 1 per 2,400 x 2,400 blocks | surface: ground under the footprint within 12 blocks (4-block samples), all samples y64-136 |
| The Flatpack | reg:32 | 0.004319% | 0.1687 | about 1 per 2,400 x 2,400 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Long Choir | reg:15 | 0.003842% | 0.1501 | about 1 per 2,600 x 2,600 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Burnt Chancel | reg:12 | 0.003613% | 0.1412 | about 1 per 2,700 x 2,700 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Lagoon | reg:18 | 0.003539% | 0.1382 | about 1 per 2,700 x 2,700 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| Wonderland | reg:14 | 0.003258% | 0.1273 | about 1 per 2,800 x 2,800 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| The Garrison | reg:7 | 0.002925% | 0.1143 | about 1 per 3,000 x 3,000 blocks | surface: ground under the footprint within 9 blocks (4-block samples), all samples y64-136 |
| The Ember Ziggurat | reg:25 | 0.002923% | 0.1142 | about 1 per 3,000 x 3,000 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136; only in 'ash' biomes: Cinder Hospice, Coalbreath Ridges, B... |
| The Strip | reg:11 | 0.002757% | 0.1077 | about 1 per 3,000 x 3,000 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136 |
| The Visitor | reg:3 | 0.002616% | 0.1022 | about 1 per 3,100 x 3,100 blocks | surface: ground under the footprint within 10 blocks (4-block samples), all samples y64-136 |
| The Mastaba | reg:6 | 0.002505% | 0.0978 | about 1 per 3,200 x 3,200 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| Columbia | reg:0 | 0.002485% | 0.0971 | about 1 per 3,200 x 3,200 blocks | surface: ground under the footprint within 9 blocks (4-block samples), all samples y64-136 |
| Ostrovets Station | reg:8 | 0.002480% | 0.0969 | about 1 per 3,200 x 3,200 blocks | surface: ground under the footprint within 8 blocks (4-block samples), all samples y64-136 |
| Old Town | reg:2 | 0.002327% | 0.0909 | about 1 per 3,300 x 3,300 blocks | surface: ground under the footprint within 10 blocks (4-block samples), all samples y64-136 |
| The Green Sanctum | reg:27 | 0.001905% | 0.0744 | about 1 per 3,700 x 3,700 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136; only in 'spores' biomes: Red Blossom Hollow, Bloomfall Ten... |
| The Oxide Sepulchre | reg:29 | 0.001475% | 0.0576 | about 1 per 4,200 x 4,200 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136; only in 'rust' biomes: Rustwater Ward, Gutterglass Schoolg... |
| The Frostvault | reg:30 | 0.001419% | 0.0554 | about 1 per 4,200 x 4,200 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136; only in 'snow' biomes: Whiteout Reservoir, Frostcrown Ramp... |
| The Tidewell | reg:26 | 0.001289% | 0.0503 | about 1 per 4,500 x 4,500 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136; only in 'mist' biomes: Ashveil Coast, Signalwreck Coast, B... |
| The Dust Reliquary | reg:28 | 0.001275% | 0.0498 | about 1 per 4,500 x 4,500 blocks | surface: ground under the footprint within 7 blocks (4-block samples), all samples y64-136; only in 'dust' biomes: Bleached Expanse, Capitol Ossuary, ... |
| The Field | reg:4 | 0.001122% | 0.0438 | about 1 per 4,800 x 4,800 blocks | buried: ceiling 10 blocks under the lowest ground sample and 64 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) |
| Rapture | reg:1 | 0.0005022% | 0.0196 | about 1 per 7,100 x 7,100 blocks | sea bed: >= 80% of footprint samples at or below y60 and none above y66 |
| The Interceptor | reg:17 | 0% | 0.0000 | never | buried: ceiling 10 blocks under the lowest ground sample and 16 blocks of height must stay >= y5 (floor seeded between y5 and that ceiling) **DISABLED live (plugins/JasprHorrorBiomes/disabled-structures.txt); its 54,298 fitting sites (would be 0.008689% of chunks) still claim ground that other set pieces, rooms, catalogue and imported sites avoid** |

### Claude Code dungeon room (15)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| Spawner Room (Dungeons.plain) | dun:14 | 1.689% | 67.4256 | about 1 per 120 x 120 blocks | needs cave air in the chunk (any biome); measured empirically **rate = share of chunks holding >= 1 room; per-1,000x1,000 count uses rooms per chunk** |
| The Ward | dun:0 | 0.3952% | 15.4371 | about 1 per 250 x 250 blocks | buried: floor seeded y12..ground-34 (any terrain); ladder shaft to daylight; 10,248 of 232,085 candidates yielded to a set piece/catalogue site |
| The Warren | dun:4 | 0.3389% | 13.2375 | about 1 per 270 x 270 blocks | buried: floor seeded y12..ground-34 (any terrain); ladder shaft to daylight; 4,682 of 194,909 candidates yielded to a set piece/catalogue site |
| The Flooded Cistern | dun:2 | 0.2900% | 11.3283 | about 1 per 300 x 300 blocks | buried: floor seeded y12..ground-34 (any terrain); ladder shaft to daylight; 3,333 of 166,124 candidates yielded to a set piece/catalogue site |
| The Ossuary | dun:1 | 0.2338% | 9.1315 | about 1 per 330 x 330 blocks | buried: floor seeded y12..ground-34 (any terrain); ladder shaft to daylight; 2,662 of 133,885 candidates yielded to a set piece/catalogue site |
| The Vault | dun:3 | 0.2042% | 7.9774 | about 1 per 350 x 350 blocks | buried: floor seeded y12..ground-34 (any terrain); ladder shaft to daylight; 2,247 of 116,885 candidates yielded to a set piece/catalogue site |
| The Checkpoint | dun:7 | 0.1600% | 6.2492 | about 1 per 400 x 400 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 3,806 of 142,820 candidates yielded to a set piece/catalogue site |
| The Spire | dun:5 | 0.1329% | 5.1901 | about 1 per 440 x 440 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 3,227 of 97,261 candidates yielded to a set piece/catalogue site |
| The Cabin | dun:10 | 0.1055% | 4.1210 | about 1 per 490 x 490 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 2,957 of 89,816 candidates yielded to a set piece/catalogue site |
| The Everburning Shrine | dun:11 | 0.08489% | 3.3161 | about 1 per 550 x 550 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 2,252 of 76,849 candidates yielded to a set piece/catalogue site |
| The Wayside Chapel | dun:6 | 0.07575% | 2.9591 | about 1 per 580 x 580 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 2,658 of 77,851 candidates yielded to a set piece/catalogue site |
| The Trial Ground | dun:9 | 0.06492% | 2.5359 | about 1 per 630 x 630 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 1,717 of 66,943 candidates yielded to a set piece/catalogue site |
| The Dead Highway | dun:13 | 0.06131% | 2.3948 | about 1 per 650 x 650 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 1,989 of 58,443 candidates yielded to a set piece/catalogue site |
| The Sanatorium | dun:8 | 0.04658% | 1.8196 | about 1 per 740 x 740 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 1,723 of 51,651 candidates yielded to a set piece/catalogue site |
| The Quarantine Blockhouse | dun:12 | 0.04387% | 1.7137 | about 1 per 760 x 760 blocks | surface: 4 footprint corners within 3 blocks of each other, ground y64-132; 1,585 of 48,519 candidates yielded to a set piece/catalogue site |

### ChatGPT Codex catalogue (HorrorBiomes) (234)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| Caravan Serai and Buried Counting Rooms | cat:market_caravan_serai | 0.002517% | 0.0983 | about 1 per 3,200 x 3,200 blocks | mode surface, tier 3; biomes: Rustwater Ward, Abandoned Cordon, Bleached Expanse, Bitter Orchard, Capitol Ossuary, Neon Dust Basin, Shelterfall Terrac... |
| Ferrymen's Exchange and Bonded Vault | cat:harbor_ferrymens_exchange | 0.002251% | 0.0879 | about 1 per 3,400 x 3,400 blocks | mode surface, tier 3; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Signalwreck Coast, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire... |
| Ward Garden and Hydrotherapy Pavilion | cat:infirmary_ward_garden | 0.001972% | 0.0770 | about 1 per 3,600 x 3,600 blocks | mode surface, tier 3; biomes: Gutterglass Schoolgrounds, Cinder Hospice, Whiteout Reservoir, Red Blossom Hollow, Bloomfall Tenements, Shelterfall Terr... |
| Penitent Springs and Reliquary Baths | cat:bathhouse_penitent_springs | 0.001767% | 0.0690 | about 1 per 3,800 x 3,800 blocks | mode surface, tier 3; biomes: Drowned Borough, Penitent Spillway, Red Blossom Hollow, Blackreed Marsh, Brine Lantern Mire, Hollow Crown Borough, Rootb... |
| Observatory of the Broken Ecliptic | cat:observatory_broken_ecliptic | 0.001657% | 0.0647 | about 1 per 3,900 x 3,900 blocks | mode surface, tier 5; biomes: Tenement Wound, Whiteout Reservoir, Signalwreck Coast, Dead Array, Gravity Scar, Glass Reactor, Coalbreath Ridges, Gilde... |
| Reliquary of the Three Witnesses | cat:reliquary_three_witnesses | 0.001474% | 0.0576 | about 1 per 4,200 x 4,200 blocks | mode surface, tier 5; biomes: Palimpsest Vaultlands, Red Blossom Hollow, Sanguine Exclusion Forest, Hollow Crown Borough, Rootbound Sanctuary, Cinderl... |
| Bell-Casting Court and Workers' Barracks | cat:foundry_bell_casting_court | 0.001372% | 0.0536 | about 1 per 4,300 x 4,300 blocks | mode surface, tier 4; biomes: Oxide Railgrave, Coalbreath Ridges, Brotherhood Slagfields, Cinderlake Shelf, Ringfall Cinders, Boomtown After Midnight,... |
| Cliff Scriptorium and Wind Cloister | cat:monastery_cliff_scriptorium | 0.001168% | 0.0456 | about 1 per 4,700 x 4,700 blocks | mode surface, tier 4; biomes: Tenement Wound, Palimpsest Vaultlands, Gravity Scar, Hollow Crown Borough, Rootbound Sanctuary, Forgotten Monarch Shore,... |
| Pilgrim Refectory and Herb Garden | cat:monastery_pilgrim_refectory | 0.0009830% | 0.0384 | about 1 per 5,100 x 5,100 blocks | mode surface, tier 3; biomes: Red Blossom Hollow, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, Rootbound Sanctuary, Forgotten Monarch... |
| Siege Hospital and Sally-Port Cells | cat:barracks_siege_hospital | 0.0009229% | 0.0360 | about 1 per 5,300 x 5,300 blocks | mode surface, tier 4; biomes: Abandoned Cordon, Sanguine Exclusion Forest, Iron Convoy Barrens, Brotherhood Slagfields, Hollow Crown Borough, Forgotte... |
| Monastery of the Four Silent Seasons | cat:monastery_four_seasons | 0.0006221% | 0.0243 | about 1 per 6,400 x 6,400 blocks | mode surface, tier 4; biomes: Red Blossom Hollow, Sanguine Exclusion Forest, Bitter Orchard, Hollow Crown Borough, Rootbound Sanctuary, Forgotten Mona... |
| Polar Survey Observatory and Weather Archive | cat:observatory_polar_survey | 0.0005782% | 0.0226 | about 1 per 6,700 x 6,700 blocks | mode surface, tier 3; biomes: Whiteout Reservoir, Signalwreck Coast, Dead Array, Frostcrown Ramparts, II - Lust: The Unceasing Gale, IX - Treachery: C... |
| Viridian Tide Reactor and Storm Tower | cat:viridian_tide_reactor | 0.0004008% | 0.0157 | about 1 per 8,000 x 8,000 blocks | mode surface, tier 4, exclusive; biomes: Viridian Blastlands |
| Bloodroot Sawmill Keep and Log Barracks | cat:bloodroot_sawmill_keep | 0.0003331% | 0.0130 | about 1 per 8,800 x 8,800 blocks | mode surface, tier 4, exclusive; biomes: Bloodroot Timberland |
| Endless Night Signal Citadel | cat:endless_night_signal_citadel | 0.0003174% | 0.0124 | about 1 per 9,000 x 9,000 blocks | mode surface, tier 4, exclusive; biomes: Endless Night Boulevard |
| Grinning Hollow Amusement Hospital | cat:grinning_hollow_amusement_hospital | 0.0003173% | 0.0124 | about 1 per 9,000 x 9,000 blocks | mode surface, tier 4, exclusive; biomes: Grinning Hollow |
| Bloomfall Broadcast Theatre and Glasshouse | cat:bloomfall_broadcast_theatre | 0.0001941% | 0.0076 | about 1 per 11,500 x 11,500 blocks | mode surface, tier 3, exclusive; biomes: Bloomfall Tenements |
| Gilded Hush Resonance Hall and Gilt Archive | cat:gilded_hush_resonance_hall | 0.0001501% | 0.0059 | about 1 per 13,100 x 13,100 blocks | mode surface, tier 5, exclusive; biomes: Gilded Hush |
| Signalwreck Fog Observatory and Listening Bunker | cat:signalwreck_fog_observatory | 0.0001500% | 0.0059 | about 1 per 13,100 x 13,100 blocks | mode surface, tier 4, exclusive; biomes: Signalwreck Coast |
| Brotherhood Bell Foundry and Siege Workshops | cat:brotherhood_bell_foundry | 0.0001497% | 0.0058 | about 1 per 13,100 x 13,100 blocks | mode surface, tier 4, exclusive; biomes: Brotherhood Slagfields |
| Whiteout Meridian Observatory and Survey Vault | cat:whiteout_meridian_station | 0.0001228% | 0.0048 | about 1 per 14,400 x 14,400 blocks | mode surface, tier 4, exclusive; biomes: Whiteout Reservoir |
| Bitter Orchard Seed Abbey and Root Cellars | cat:bitter_orchard_seed_abbey | 0.0001219% | 0.0048 | about 1 per 14,500 x 14,500 blocks | mode surface, tier 3, exclusive; biomes: Bitter Orchard |
| Lust Wind-Lens Abbey and Pendulum Galleries | cat:lust_wind_lens_abbey | 0.0001206% | 0.0047 | about 1 per 14,600 x 14,600 blocks | mode surface, tier 5, exclusive; biomes: II - Lust: The Unceasing Gale |
| Kandarian Watch Abbey and Hunters' Ossuary | cat:kandarian_watch_abbey | 0.0001156% | 0.0045 | about 1 per 14,900 x 14,900 blocks | mode surface, tier 4, exclusive; biomes: Kandarian Hunting Grounds |
| Limbo Unfinished Scriptorium and Quiet Cloister | cat:limbo_unfinished_scriptorium | 0.0001082% | 0.0042 | about 1 per 15,400 x 15,400 blocks | mode surface, tier 4, exclusive; biomes: I - Limbo: Mourning Fields |
| Last Ember Bell Monastery and Choir Crypt | cat:last_ember_bell_monastery | 0.0001080% | 0.0042 | about 1 per 15,400 x 15,400 blocks | mode surface, tier 5, exclusive; biomes: Cathedral of the Last Ember |
| Violence Thorn Infirmary and Penitents' Abbey | cat:violence_thorn_infirmary | 0.0001069% | 0.0042 | about 1 per 15,500 x 15,500 blocks | mode surface, tier 4, exclusive; biomes: VII - Violence: Thornblood Reach |
| Red Blossom Mourning Monastery | cat:blossom_mourning_monastery | 0.0001038% | 0.0041 | about 1 per 15,700 x 15,700 blocks | mode surface, tier 4, exclusive; biomes: Red Blossom Hollow |
| Rootbound Herbarium Cloister and Vow Crypt | cat:rootbound_herbarium_cloister | 0.0001019% | 0.0040 | about 1 per 15,800 x 15,800 blocks | mode surface, tier 4, exclusive; biomes: Rootbound Sanctuary |
| Lost Reliquary Stair and Votive Yard | cat:lost_reliquary_stair | 0.00009841% | 0.0038 | about 1 per 16,100 x 16,100 blocks | mode surface, tier 2; biomes: Palimpsest Vaultlands, Red Blossom Hollow, Sanguine Exclusion Forest, Hollow Crown Borough, Rootbound Sanctuary, Cinderl... |
| Forgotten Monarch Tide Monastery | cat:monarch_tide_monastery | 0.00009488% | 0.0037 | about 1 per 16,400 x 16,400 blocks | mode surface, tier 4, exclusive; biomes: Forgotten Monarch Shore |
| Frostcrown Ninth-Meridian Monastery | cat:frostcrown_ninth_meridian | 0.00008983% | 0.0035 | about 1 per 16,900 x 16,900 blocks | mode surface, tier 5, exclusive; biomes: Frostcrown Ramparts |
| Surveyors' Wind Station | cat:surveyors_wind_station | 0.00008791% | 0.0034 | about 1 per 17,100 x 17,100 blocks | mode surface, tier 2; biomes: Tenement Wound, Whiteout Reservoir, Signalwreck Coast, Dead Array, Gravity Scar, Glass Reactor, Coalbreath Ridges, Black... |
| Hollow Crown Pale Monastery and Royal Ossuary | cat:hollow_crown_pale_monastery | 0.00008671% | 0.0034 | about 1 per 17,200 x 17,200 blocks | mode surface, tier 5, exclusive; biomes: Hollow Crown Borough |
| Storm-Drain Bath and Boiler | cat:storm_drain_bath | 0.00008051% | 0.0031 | about 1 per 17,800 x 17,800 blocks | mode buried, tier 1; biomes: Ashveil Coast, Rustwater Ward, Drowned Borough, Penitent Spillway, Bloomfall Tenements, Blackreed Marsh, Brine Lantern Mi... |
| Flood-Markers' Boathouse | cat:flood_markers_boathouse | 0.00008021% | 0.0031 | about 1 per 17,900 x 17,900 blocks | mode surface, tier 1; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Signalwreck Coast, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire... |
| Broken-Glass Potting House | cat:broken_glass_potting_house | 0.00007419% | 0.0029 | about 1 per 18,600 x 18,600 blocks | mode surface, tier 1; biomes: Cinder Hospice, Red Blossom Hollow, Bloomfall Tenements, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, R... |
| Wind Caravan Shelter | cat:wind_caravan_shelter | 0.00007237% | 0.0028 | about 1 per 18,800 x 18,800 blocks | mode surface, tier 2; biomes: Tenement Wound, Gravity Scar, Bleached Expanse, Neon Dust Basin, Coalbreath Ridges, Iron Convoy Barrens, Forgotten Monar... |
| Canal Lockkeepers' Recess | cat:canal_lockkeepers_recess | 0.00007009% | 0.0027 | about 1 per 19,100 x 19,100 blocks | mode surface, tier 1; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire, Leechwater Depths... |
| Gluttony Rendering Cistern and Carrion Kitchens | cat:gluttony_rendering_cistern | 0.00006889% | 0.0027 | about 1 per 19,300 x 19,300 blocks | mode buried, tier 4, exclusive; biomes: III - Gluttony: Carrion Slough |
| Penitent Ebb-Tide Prison Cistern | cat:penitent_ebb_prison | 0.00006856% | 0.0027 | about 1 per 19,300 x 19,300 blocks | mode buried, tier 4, exclusive; biomes: Penitent Spillway |
| Listening Post Recess | cat:listening_post_recess | 0.00006835% | 0.0027 | about 1 per 19,400 x 19,400 blocks | mode buried, tier 1; biomes: Signalwreck Coast, Abandoned Cordon, Dead Array, Sanguine Exclusion Forest, Gravity Scar, Glass Reactor, Viridian Blastla... |
| Meridian Milestone and Map Shelter | cat:meridian_milestone | 0.00006710% | 0.0026 | about 1 per 19,500 x 19,500 blocks | mode surface, tier 1; biomes: Tenement Wound, Whiteout Reservoir, Gravity Scar, Bleached Expanse, Neon Dust Basin, Iron Convoy Barrens, Forgotten Mona... |
| Open-Air Salvage Exchange | cat:open_air_salvage_exchange | 0.00006692% | 0.0026 | about 1 per 19,600 x 19,600 blocks | mode surface, tier 1; biomes: Rustwater Ward, Abandoned Cordon, Oxide Railgrave, Bleached Expanse, Bitter Orchard, Capitol Ossuary, Neon Dust Basin, S... |
| Blackreed Reed-Bed Sampling Cistern | cat:blackreed_sampling_cistern | 0.00006625% | 0.0026 | about 1 per 19,700 x 19,700 blocks | mode buried, tier 3, exclusive; biomes: Blackreed Marsh |
| Watchkeepers' Sally Port | cat:watchkeepers_sally_port | 0.00006618% | 0.0026 | about 1 per 19,700 x 19,700 blocks | mode surface, tier 2; biomes: Tenement Wound, Abandoned Cordon, Sanguine Exclusion Forest, Bleached Expanse, Iron Convoy Barrens, Brotherhood Slagfiel... |
| Roadside Triage Bay | cat:roadside_triage_bay | 0.00006523% | 0.0025 | about 1 per 19,800 x 19,800 blocks | mode surface, tier 1; biomes: Gutterglass Schoolgrounds, Cinder Hospice, Whiteout Reservoir, Abandoned Cordon, Sanguine Exclusion Forest, Viridian Bla... |
| Quarantine Sample Depot | cat:quarantine_sample_depot | 0.00006485% | 0.0025 | about 1 per 19,900 x 19,900 blocks | mode buried, tier 2; biomes: Cinder Hospice, Abandoned Cordon, Blackreed Marsh, Sunken Convoy, Sanguine Exclusion Forest, Glass Reactor, Viridian Blas... |
| School Broadcast Booth and Assembly Ruin | cat:school_broadcast_booth | 0.00006446% | 0.0025 | about 1 per 19,900 x 19,900 blocks | mode surface, tier 2; biomes: Gutterglass Schoolgrounds, Cinder Hospice, Whiteout Reservoir, Bloomfall Tenements, Shelterfall Terraces, Carousel Waste... |
| Wayside Bone Chapel | cat:wayside_bone_chapel | 0.00006423% | 0.0025 | about 1 per 20,000 x 20,000 blocks | mode buried, tier 2; biomes: Palimpsest Vaultlands, Capitol Ossuary, Hollow Crown Borough, Cinderlake Shelf, Ringfall Cinders, Cathedral of the Last E... |
| Blackgutter Chain Liftworks and Underchasm Cells | cat:blackgutter_chain_liftworks | 0.00006398% | 0.0025 | about 1 per 20,000 x 20,000 blocks | mode buried, tier 4, exclusive; biomes: Blackgutter Chasm |
| Abandoned Autopsy Cottage | cat:abandoned_autopsy_cottage | 0.00006395% | 0.0025 | about 1 per 20,000 x 20,000 blocks | mode buried, tier 2; biomes: Cinder Hospice, Sanguine Exclusion Forest, Cabin of the Listening Pines, Bloodroot Timberland, Deadward Asylum, Possessed... |
| Coalbreath Survey Mine and Furnace Galleries | cat:coalbreath_survey_mine | 0.00006270% | 0.0024 | about 1 per 20,200 x 20,200 blocks | mode buried, tier 4, exclusive; biomes: Coalbreath Ridges |
| Excavators' Shoring Camp | cat:excavators_shoring_camp | 0.00006262% | 0.0024 | about 1 per 20,200 x 20,200 blocks | mode surface, tier 1; biomes: Tenement Wound, Gravity Scar, Bleached Expanse, Viridian Blastlands, Coalbreath Ridges, Brotherhood Slagfields, Blackgut... |
| Gravity Scar Lens Vault and Pendulum Observatory | cat:gravity_scar_lens_vault | 0.00006254% | 0.0024 | about 1 per 20,200 x 20,200 blocks | mode buried, tier 5, exclusive; biomes: Gravity Scar |
| Tenement Counterweight Prison and Liftworks | cat:tenement_counterweight_prison | 0.00006208% | 0.0024 | about 1 per 20,300 x 20,300 blocks | mode buried, tier 4, exclusive; biomes: Tenement Wound |
| Neon Dust Counting House and Casino Service Vault | cat:neon_dust_counting_house | 0.00006144% | 0.0024 | about 1 per 20,400 x 20,400 blocks | mode buried, tier 4, exclusive; biomes: Neon Dust Basin |
| Crypt Scribes' Stair | cat:crypt_scribes_stair | 0.00005939% | 0.0023 | about 1 per 20,800 x 20,800 blocks | mode buried, tier 2; biomes: Palimpsest Vaultlands, Capitol Ossuary, Gilded Hush, Hollow Crown Borough, Frostcrown Ramparts, Ringfall Cinders, Cathedr... |
| Ashveil Customs House and Drowned Cistern | cat:ashveil_customs_cistern | 0.00005873% | 0.0023 | about 1 per 20,900 x 20,900 blocks | mode underwater, tier 4, exclusive; biomes: Ashveil Coast; needs >= 90% of room samples below y62 |
| Listening Pines Tape Vault and Buried Chapel | cat:listening_pines_tape_vault | 0.00005834% | 0.0023 | about 1 per 20,900 x 20,900 blocks | mode buried, tier 3, exclusive; biomes: Cabin of the Listening Pines |
| Telephone Battery House | cat:telephone_battery_house | 0.00005791% | 0.0023 | about 1 per 21,000 x 21,000 blocks | mode surface, tier 2; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Bloomfall Tenements, Signalwreck Coast, Dead Array, Oxide Railgrave, Shelterf... |
| Cemetery Receiving House | cat:cemetery_receiving_house | 0.00005763% | 0.0023 | about 1 per 21,100 x 21,100 blocks | mode surface, tier 1; biomes: Cinder Hospice, Palimpsest Vaultlands, Capitol Ossuary, Hollow Crown Borough, Ringfall Cinders, Cathedral of the Last Em... |
| Kiln Workers' Refuge | cat:kiln_workers_refuge | 0.00005663% | 0.0022 | about 1 per 21,300 x 21,300 blocks | mode surface, tier 2; biomes: Oxide Railgrave, Coalbreath Ridges, Brotherhood Slagfields, Cinderlake Shelf, Ringfall Cinders, Boomtown After Midnight,... |
| Sanguine Exclusion Field Autopsy Compound | cat:sanguine_field_autopsy | 0.00005650% | 0.0022 | about 1 per 21,300 x 21,300 blocks | mode buried, tier 4, exclusive; biomes: Sanguine Exclusion Forest |
| Greed Assay Catacombs and Counting Galleries | cat:greed_assay_catacombs | 0.00005619% | 0.0022 | about 1 per 21,300 x 21,300 blocks | mode buried, tier 5, exclusive; biomes: IV - Greed: The Burdened Quarry |
| Fraud Counterfeit Archive and Tribunal Cells | cat:fraud_counterfeit_archive | 0.00005599% | 0.0022 | about 1 per 21,400 x 21,400 blocks | mode buried, tier 5, exclusive; biomes: VIII - Fraud: Malebolge Trenches |
| Quarry Pay Window and Holding Cells | cat:quarry_pay_window | 0.00005584% | 0.0022 | about 1 per 21,400 x 21,400 blocks | mode surface, tier 1; biomes: Oxide Railgrave, Bleached Expanse, Neon Dust Basin, Coalbreath Ridges, Brotherhood Slagfields, Gilded Hush, Blackgutter ... |
| Mirrorward Observation Lab and Interview Rooms | cat:mirrorward_observation_lab | 0.00005548% | 0.0022 | about 1 per 21,500 x 21,500 blocks | mode buried, tier 4, exclusive; biomes: Mirrorward Thicket |
| Treachery Frozen Meridian and Witness Crypt | cat:treachery_frozen_meridian | 0.00005532% | 0.0022 | about 1 per 21,500 x 21,500 blocks | mode buried, tier 5, exclusive; biomes: IX - Treachery: Cocytus |
| Unburied Siege Catacombs and Ballista Workshops | cat:unburied_siege_catacombs | 0.00005527% | 0.0022 | about 1 per 21,500 x 21,500 blocks | mode buried, tier 5, exclusive; biomes: Bastion of the Unburied |
| Gutterglass Anatomy Theatre and Detention Annex | cat:gutterglass_anatomy_annex | 0.00005525% | 0.0022 | about 1 per 21,500 x 21,500 blocks | mode buried, tier 3, exclusive; biomes: Gutterglass Schoolgrounds |
| Vaultroot Spore Laboratory and Seed Library | cat:vaultroot_spore_laboratory | 0.00005522% | 0.0022 | about 1 per 21,500 x 21,500 blocks | mode buried, tier 4, exclusive; biomes: Vaultroot Gardens |
| Bleached Expanse Aquifer Vault | cat:bleached_aquifer_vault | 0.00005458% | 0.0021 | about 1 per 21,700 x 21,700 blocks | mode buried, tier 4, exclusive; biomes: Bleached Expanse |
| Cinderlake Ash Reliquary and Kiln Catacombs | cat:cinderlake_ash_reliquary | 0.00005450% | 0.0021 | about 1 per 21,700 x 21,700 blocks | mode buried, tier 5, exclusive; biomes: Cinderlake Shelf |
| Possessed Orchard Presshouse and Root Ossuary | cat:possessed_orchard_presshouse | 0.00005402% | 0.0021 | about 1 per 21,800 x 21,800 blocks | mode buried, tier 3, exclusive; biomes: Possessed Orchard |
| Glass Reactor Isotope Wells and Control Galleries | cat:glass_reactor_isotope_wells | 0.00005361% | 0.0021 | about 1 per 21,900 x 21,900 blocks | mode buried, tier 5, exclusive; biomes: Glass Reactor |
| Dead Array Deep Listening Bunker | cat:dead_array_listening_bunker | 0.00005317% | 0.0021 | about 1 per 21,900 x 21,900 blocks | mode buried, tier 5, exclusive; biomes: Dead Array |
| Deadward Hydrotherapy Cells and Dissection Theatre | cat:deadward_hydrotherapy_cells | 0.00005302% | 0.0021 | about 1 per 22,000 x 22,000 blocks | mode buried, tier 4, exclusive; biomes: Deadward Asylum |
| Wrath Ferrymen's Lock and Drowned Cells | cat:wrath_ferrymen_lock | 0.00005297% | 0.0021 | about 1 per 22,000 x 22,000 blocks | mode underwater, tier 5, exclusive; biomes: V - Wrath: Styx Blackwater; needs >= 90% of room samples below y62 |
| Iron Convoy Armored Rail Depot | cat:iron_convoy_armored_depot | 0.00005225% | 0.0020 | about 1 per 22,100 x 22,100 blocks | mode buried, tier 4, exclusive; biomes: Iron Convoy Barrens |
| Orchard Press and Seed Shed | cat:orchard_press_ruin | 0.00005192% | 0.0020 | about 1 per 22,200 x 22,200 blocks | mode surface, tier 1; biomes: Red Blossom Hollow, Bitter Orchard, Vaultroot Gardens, Cabin of the Listening Pines, Bloodroot Timberland, Possessed Orc... |
| Shelterfall Civic Vault and Underground Assembly | cat:shelterfall_civic_vault | 0.00005171% | 0.0020 | about 1 per 22,200 x 22,200 blocks | mode buried, tier 3, exclusive; biomes: Shelterfall Terraces |
| Carousel Midway Understage and Service Railway | cat:carousel_midway_understage | 0.00005084% | 0.0020 | about 1 per 22,400 x 22,400 blocks | mode buried, tier 3, exclusive; biomes: Carousel Wastes |
| Boomtown Midnight Subway and Municipal Lockup | cat:boomtown_midnight_subway | 0.00005064% | 0.0020 | about 1 per 22,500 x 22,500 blocks | mode buried, tier 4, exclusive; biomes: Boomtown After Midnight |
| Cordon Quarantine Research Bunker | cat:cordon_quarantine_lab | 0.00005036% | 0.0020 | about 1 per 22,500 x 22,500 blocks | mode buried, tier 4, exclusive; biomes: Abandoned Cordon |
| Ringfall Processional Vault of the Ashen Kings | cat:ringfall_processional_vault | 0.00005023% | 0.0020 | about 1 per 22,600 x 22,600 blocks | mode buried, tier 5, exclusive; biomes: Ringfall Cinders |
| Heresy Censer Catacombs and Broken Seminary | cat:heresy_censer_catacombs | 0.00005005% | 0.0020 | about 1 per 22,600 x 22,600 blocks | mode buried, tier 5, exclusive; biomes: VI - Heresy: Sepulcher of Embers |
| Cinder Hospice Mortuary and Isolation Wards | cat:cinder_hospice_morgue | 0.00005000% | 0.0020 | about 1 per 22,600 x 22,600 blocks | mode buried, tier 4, exclusive; biomes: Cinder Hospice |
| Abandoned Assay Office | cat:abandoned_assay_office | 0.00004990% | 0.0019 | about 1 per 22,700 x 22,700 blocks | mode buried, tier 2; biomes: Palimpsest Vaultlands, Bleached Expanse, Capitol Ossuary, Neon Dust Basin, Coalbreath Ridges, Iron Convoy Barrens, Gilded... |
| Roadside Pilgrim Bath | cat:roadside_pilgrim_bath | 0.00004961% | 0.0019 | about 1 per 22,700 x 22,700 blocks | mode surface, tier 2; biomes: Red Blossom Hollow, Bitter Orchard, Hollow Crown Borough, Rootbound Sanctuary, Forgotten Monarch Shore, Cathedral of the... |
| Marsh Filter Shed | cat:marsh_filter_shed | 0.00004946% | 0.0019 | about 1 per 22,800 x 22,800 blocks | mode surface, tier 1; biomes: Penitent Spillway, Blackreed Marsh, Vaultroot Gardens, Brine Lantern Mire, Leechwater Depths, Drowned Oath Fen, III - Gl... |
| Capitol Ossuary Monument Metro and Burial Line | cat:capitol_ossuary_metro | 0.00004777% | 0.0019 | about 1 per 23,100 x 23,100 blocks | mode buried, tier 5, exclusive; biomes: Capitol Ossuary |
| Midway Mask Kiosk and Prop Store | cat:midway_mask_kiosk | 0.00004772% | 0.0019 | about 1 per 23,200 x 23,200 blocks | mode surface, tier 1; biomes: Bloomfall Tenements, Neon Dust Basin, Gilded Hush, Carousel Wastes, Boomtown After Midnight, Endless Night Boulevard, Gr... |
| Leechwater Filter Basilica | cat:leechwater_filter_basilica | 0.00004731% | 0.0018 | about 1 per 23,300 x 23,300 blocks | mode underwater, tier 5, exclusive; biomes: Leechwater Depths; needs >= 90% of room samples below y62 |
| Monument Threshold Gatehouse | cat:monument_threshold | 0.00004711% | 0.0018 | about 1 per 23,300 x 23,300 blocks | mode surface, tier 4; biomes: Ashveil Coast, Drowned Borough, Blackreed Marsh, Sunken Convoy, Sanguine Exclusion Forest, Brine Lantern Mire, Hollow Cr... |
| Palimpsest Sealed Index and Ossuary Stacks | cat:palimpsest_sealed_index | 0.00004664% | 0.0018 | about 1 per 23,400 x 23,400 blocks | mode buried, tier 5, exclusive; biomes: Palimpsest Vaultlands |
| Fallen Bell Hermitage | cat:fallen_bell_hermitage | 0.00004552% | 0.0018 | about 1 per 23,700 x 23,700 blocks | mode surface, tier 1; biomes: Red Blossom Hollow, Sanguine Exclusion Forest, Hollow Crown Borough, Rootbound Sanctuary, Forgotten Monarch Shore, Cathe... |
| Salt Customs Watch and Bond Store | cat:salt_customs_watch | 0.00004513% | 0.0018 | about 1 per 23,800 x 23,800 blocks | mode surface, tier 2; biomes: Ashveil Coast, Drowned Borough, Signalwreck Coast, Sunken Convoy, Brine Lantern Mire, Forgotten Monarch Shore, V - Wrath... |
| Metro Ticket Hall and Platform Crypt | cat:metro_ticket_crypt | 0.00004503% | 0.0018 | about 1 per 23,800 x 23,800 blocks | mode buried, tier 2; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Bloomfall Tenements, Oxide Railgrave, Capitol Ossuary, Carousel Wastes, Boomto... |
| Oxide Railgrave Thirteen-Platform Terminus | cat:oxide_thirteen_platforms | 0.00004265% | 0.0017 | about 1 per 24,500 x 24,500 blocks | mode buried, tier 5, exclusive; biomes: Oxide Railgrave |
| Whisper Row Houses | cat:whisper_row_houses | 0.00004012% | 0.0016 | about 1 per 25,300 x 25,300 blocks | mode surface, tier 2; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Tenement Wound, Palimpsest Vaultlands, Bloomfall Tenements, Gravity Scar, Gla... |
| Rustwater Last-Train Metro Interchange | cat:rustwater_last_train | 0.00003848% | 0.0015 | about 1 per 25,800 x 25,800 blocks | mode buried, tier 4, exclusive; biomes: Rustwater Ward |
| Industrial Courtyard Manor | cat:industrial_courtyard_manor | 0.00003843% | 0.0015 | about 1 per 25,800 x 25,800 blocks | mode surface, tier 3; biomes: Rustwater Ward, Tenement Wound, Cinder Hospice, Bloomfall Tenements, Oxide Railgrave, Bleached Expanse, Capitol Ossuary,... |
| Catacomb Procession Loop and Embalming House | cat:catacomb_procession_loop | 0.00003820% | 0.0015 | about 1 per 25,900 x 25,900 blocks | mode buried, tier 3; biomes: Cinder Hospice, Palimpsest Vaultlands, Hollow Crown Borough, Rootbound Sanctuary, Ringfall Cinders, Cathedral of the Last... |
| Sunken Convoy Salvage Lock and Pressure Lab | cat:sunken_convoy_salvage_lock | 0.00003797% | 0.0015 | about 1 per 26,000 x 26,000 blocks | mode underwater, tier 4, exclusive; biomes: Sunken Convoy; needs >= 90% of room samples below y62 |
| Civic Filter Hall and Reservoir Archive | cat:cistern_civic_filter_hall | 0.00003725% | 0.0015 | about 1 per 26,200 x 26,200 blocks | mode buried, tier 3; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Drowned Borough, Whiteout Reservoir, Penitent Spillway, Bloomfall Tenements, C... |
| Sunken Assay Pit and Survey Galleries | cat:excavation_sunken_assay | 0.00003328% | 0.0013 | about 1 per 27,700 x 27,700 blocks | mode buried, tier 3; biomes: Tenement Wound, Gravity Scar, Bleached Expanse, Viridian Blastlands, Coalbreath Ridges, Brotherhood Slagfields, Blackgutt... |
| Motel Threshold Backroom Court | cat:motel_threshold | 0.00003246% | 0.0013 | about 1 per 28,100 x 28,100 blocks | mode surface, tier 3; biomes: Cinder Hospice, Red Blossom Hollow, Abandoned Cordon, Bleached Expanse, Bitter Orchard, Shelterfall Terraces, Vaultroot ... |
| Marsh Telephone Pole Farm | cat:marsh_phone_farm | 0.00003236% | 0.0013 | about 1 per 28,100 x 28,100 blocks | mode surface, tier 2; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire, Leechwater Depths... |
| Impossible Office Backroom Annex | cat:impossible_office | 0.00003164% | 0.0012 | about 1 per 28,400 x 28,400 blocks | mode buried, tier 4; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Tenement Wound, Palimpsest Vaultlands, Bloomfall Tenements, Dead Array, Gravit... |
| Cistern of Three Pressure Wells | cat:cistern_three_pressure_wells | 0.00003141% | 0.0012 | about 1 per 28,500 x 28,500 blocks | mode buried, tier 4; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Blackreed Marsh, Sunken Convoy, Bleached Expanse, Viridian Blastlands,... |
| Metro Signal Hut and Track Stub | cat:metro_signal_hut | 0.00003077% | 0.0012 | about 1 per 28,800 x 28,800 blocks | mode surface, tier 1; biomes: Rustwater Ward, Oxide Railgrave, Capitol Ossuary, Iron Convoy Barrens, Boomtown After Midnight, Endless Night Boulevard |
| Coastal Bell House Crescent | cat:coastal_bell_houses | 0.00003075% | 0.0012 | about 1 per 28,900 x 28,900 blocks | mode surface, tier 1; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Signalwreck Coast, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire... |
| Rail Water-Tower Pump House | cat:rail_water_tower_pump | 0.00003016% | 0.0012 | about 1 per 29,100 x 29,100 blocks | mode surface, tier 1; biomes: Abandoned Cordon, Oxide Railgrave, Bleached Expanse, Coalbreath Ridges, Iron Convoy Barrens, Boomtown After Midnight |
| Buried Station Threshold | cat:buried_station_threshold | 0.00002916% | 0.0011 | about 1 per 29,600 x 29,600 blocks | mode buried, tier 4; biomes: Whiteout Reservoir, Penitent Spillway, Signalwreck Coast, Oxide Railgrave, Capitol Ossuary, Viridian Blastlands, Coalbrea... |
| Paired Bell Battle Towers | cat:paired_bell_towers | 0.00002901% | 0.0011 | about 1 per 29,700 x 29,700 blocks | mode surface, tier 4; biomes: Palimpsest Vaultlands, Red Blossom Hollow, Sanguine Exclusion Forest, Hollow Crown Borough, Rootbound Sanctuary, Forgott... |
| Drowned Borough Municipal Bathworks | cat:drowned_borough_bathworks | 0.00002885% | 0.0011 | about 1 per 29,800 x 29,800 blocks | mode underwater, tier 4, exclusive; biomes: Drowned Borough; needs >= 90% of room samples below y62 |
| Bloodroot Sawmill Ossuary | cat:bloodroot_sawmill_ossuary | 0.00002826% | 0.0011 | about 1 per 30,100 x 30,100 blocks | mode buried, tier 4, exclusive; biomes: Bloodroot Timberland |
| Split-Level Civil Refuge | cat:split_level_refuge | 0.00002762% | 0.0011 | about 1 per 30,400 x 30,400 blocks | mode buried, tier 2; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Tenement Wound, Bloomfall Tenements, Gravity Scar, Bleached Expanse, Capitol O... |
| Spiral Prison Dungeon | cat:spiral_prison_dungeon | 0.00002729% | 0.0011 | about 1 per 30,600 x 30,600 blocks | mode buried, tier 5; biomes: Tenement Wound, Palimpsest Vaultlands, Gravity Scar, Glass Reactor, Viridian Blastlands, Coalbreath Ridges, Gilded Hush, ... |
| Brine Lantern Ferry Crypt and Pump House | cat:brine_lantern_ferry_crypt | 0.00002721% | 0.0011 | about 1 per 30,700 x 30,700 blocks | mode underwater, tier 4, exclusive; biomes: Brine Lantern Mire; needs >= 90% of room samples below y62 |
| Metro Abandoned Freight Loop | cat:metro_abandoned_freight_loop | 0.00002662% | 0.0010 | about 1 per 31,000 x 31,000 blocks | mode buried, tier 3; biomes: Abandoned Cordon, Oxide Railgrave, Bleached Expanse, Capitol Ossuary, Viridian Blastlands, Coalbreath Ridges, Iron Convoy... |
| Marsh Pump Labyrinth | cat:marsh_pump_labyrinth | 0.00002657% | 0.0010 | about 1 per 31,000 x 31,000 blocks | mode buried, tier 4; biomes: Ashveil Coast, Penitent Spillway, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire, Leechwater Depths, Drowned Oath Fen... |
| Grinning Hollow Mask Theatre and Prop Vaults | cat:grinning_hollow_mask_theatre | 0.00002647% | 0.0010 | about 1 per 31,100 x 31,100 blocks | mode buried, tier 4, exclusive; biomes: Grinning Hollow |
| Underground Furnace and Assay Galleries | cat:foundry_underground_furnace | 0.00002609% | 0.0010 | about 1 per 31,300 x 31,300 blocks | mode buried, tier 4; biomes: Gravity Scar, Glass Reactor, Viridian Blastlands, Coalbreath Ridges, Brotherhood Slagfields, Cinderlake Shelf, Blackgutte... |
| Ward Drain Network | cat:ward_drain_network | 0.00002601% | 0.0010 | about 1 per 31,400 x 31,400 blocks | mode buried, tier 3; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Drowned Borough, Penitent Spillway, Bloomfall Tenements, Oxide Railgrave, Capi... |
| Last Broadcast Auditorium and Signal Vault | cat:auditorium_last_broadcast | 0.00002593% | 0.0010 | about 1 per 31,400 x 31,400 blocks | mode buried, tier 4; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Bloomfall Tenements, Signalwreck Coast, Dead Array, Neon Dust Basin, Gilded Hu... |
| Mirror Atlas Library and Listening Rooms | cat:archive_mirror_atlas | 0.00002586% | 0.0010 | about 1 per 31,500 x 31,500 blocks | mode buried, tier 4; biomes: Palimpsest Vaultlands, Bloomfall Tenements, Dead Array, Gravity Scar, Gilded Hush, Cabin of the Listening Pines, Deadward... |
| Research Cryo Annex and Isolation Galleries | cat:research_cryo_annex | 0.00002578% | 0.0010 | about 1 per 31,500 x 31,500 blocks | mode buried, tier 4; biomes: Whiteout Reservoir, Abandoned Cordon, Dead Array, Gravity Scar, Glass Reactor, Viridian Blastlands, Shelterfall Terraces,... |
| Inverted-Tree Crypt | cat:crypt_inverted_tree | 0.00002565% | 0.0010 | about 1 per 31,600 x 31,600 blocks | mode buried, tier 4; biomes: Palimpsest Vaultlands, Sanguine Exclusion Forest, Hollow Crown Borough, Rootbound Sanctuary, Cinderlake Shelf, Forgotten ... |
| River Gate Castle | cat:river_gate_castle | 0.00002524% | 0.0010 | about 1 per 31,800 x 31,800 blocks | mode surface, tier 4; biomes: Ashveil Coast, Penitent Spillway, Blackreed Marsh, Brine Lantern Mire, Leechwater Depths, Forgotten Monarch Shore, Drown... |
| Drowned Oath Tide Scriptorium | cat:drowned_oath_tide_scriptorium | 0.00002470% | 0.0010 | about 1 per 32,200 x 32,200 blocks | mode underwater, tier 4, exclusive; biomes: Drowned Oath Fen; needs >= 90% of room samples below y62 |
| Ridge Repeater Chain | cat:ridge_repeater_chain | 0.00002389% | 0.0009 | about 1 per 32,700 x 32,700 blocks | mode surface, tier 3; biomes: Tenement Wound, Signalwreck Coast, Dead Array, Gravity Scar, Glass Reactor, Coalbreath Ridges, Brotherhood Slagfields, C... |
| Monastic School and Scriptorium | cat:monastic_school | 0.00002358% | 0.0009 | about 1 per 33,000 x 33,000 blocks | mode surface, tier 3; biomes: Palimpsest Vaultlands, Red Blossom Hollow, Hollow Crown Borough, Rootbound Sanctuary, Forgotten Monarch Shore, Frostcrow... |
| Forest Medical Plane and Field Ward | cat:forest_medical_plane | 0.00002340% | 0.0009 | about 1 per 33,100 x 33,100 blocks | mode surface, tier 2; biomes: Cinder Hospice, Red Blossom Hollow, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, Rootbound Sanctuary, C... |
| Catacomb of Seven Burial Aisles | cat:catacomb_seven_burial_aisles | 0.00002307% | 0.0009 | about 1 per 33,300 x 33,300 blocks | mode buried, tier 4; biomes: Palimpsest Vaultlands, Capitol Ossuary, Hollow Crown Borough, Cinderlake Shelf, Forgotten Monarch Shore, Ringfall Cinders... |
| Woodland Boarding School | cat:woodland_boarding_school | 0.00002291% | 0.0009 | about 1 per 33,400 x 33,400 blocks | mode surface, tier 2; biomes: Cinder Hospice, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, Cabin of the Listening Pines, Bloodroot Ti... |
| Quarantine Catacomb and Pathology Vault | cat:infirmary_quarantine_catacomb | 0.00002291% | 0.0009 | about 1 per 33,400 x 33,400 blocks | mode buried, tier 4; biomes: Cinder Hospice, Abandoned Cordon, Sanguine Exclusion Forest, Glass Reactor, Viridian Blastlands, Cabin of the Listening P... |
| Reservoir High School | cat:reservoir_high_school | 0.00002271% | 0.0009 | about 1 per 33,600 x 33,600 blocks | mode surface, tier 3; biomes: Ashveil Coast, Drowned Borough, Whiteout Reservoir, Penitent Spillway, Signalwreck Coast, Blackreed Marsh, Brine Lantern... |
| Endless Night Terminal and Telephone Catacombs | cat:endless_night_terminal | 0.00002222% | 0.0009 | about 1 per 33,900 x 33,900 blocks | mode buried, tier 4, exclusive; biomes: Endless Night Boulevard |
| Quarry Battle Spire and Barracks | cat:quarry_battle_spire | 0.00002181% | 0.0009 | about 1 per 34,300 x 34,300 blocks | mode surface, tier 4; biomes: Gravity Scar, Glass Reactor, Bleached Expanse, Viridian Blastlands, Coalbreath Ridges, Brotherhood Slagfields, Cinderlak... |
| Mountain Bridge City | cat:mountain_bridge_city | 0.00002179% | 0.0009 | about 1 per 34,300 x 34,300 blocks | mode surface, tier 5; biomes: Tenement Wound, Gravity Scar, Coalbreath Ridges, Brotherhood Slagfields, Hollow Crown Borough, Blackgutter Chasm, Frostc... |
| Ash Workers' Hamlet | cat:ash_workers_hamlet | 0.00002168% | 0.0008 | about 1 per 34,400 x 34,400 blocks | mode surface, tier 1; biomes: Cinder Hospice, Oxide Railgrave, Coalbreath Ridges, Brotherhood Slagfields, Carousel Wastes, Cinderlake Shelf, Ringfall ... |
| Listening Pines Cabin | cat:listening_pines_cabin | 0.00002166% | 0.0008 | about 1 per 34,400 x 34,400 blocks | mode surface, tier 1, exclusive; biomes: Cabin of the Listening Pines |
| Medical Bunker Compound | cat:medical_bunker_compound | 0.00002163% | 0.0008 | about 1 per 34,400 x 34,400 blocks | mode buried, tier 3; biomes: Cinder Hospice, Whiteout Reservoir, Abandoned Cordon, Glass Reactor, Viridian Blastlands, Shelterfall Terraces, Vaultroot... |
| Split-Fuselage Passenger Wreck | cat:split_airliner | 0.00002140% | 0.0008 | about 1 per 34,600 x 34,600 blocks | mode surface, tier 3; biomes: Ashveil Coast, Drowned Borough, Signalwreck Coast, Sunken Convoy, Capitol Ossuary, Neon Dust Basin, Brine Lantern Mire, ... |
| Pine Switchback Homes | cat:pine_switchback_homes | 0.00002120% | 0.0008 | about 1 per 34,800 x 34,800 blocks | mode surface, tier 1; biomes: Red Blossom Hollow, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, Rootbound Sanctuary, Cabin of the List... |
| Triple Bailey Castle | cat:triple_bailey_castle | 0.00002115% | 0.0008 | about 1 per 34,800 x 34,800 blocks | mode surface, tier 5; biomes: Sanguine Exclusion Forest, Brotherhood Slagfields, Hollow Crown Borough, Rootbound Sanctuary, Cinderlake Shelf, Forgotte... |
| Blackreed Pump and Relay Hamlet | cat:blackreed_stilt_exchange | 0.00002107% | 0.0008 | about 1 per 34,900 x 34,900 blocks | mode surface, tier 2, exclusive; biomes: Blackreed Marsh |
| Woodland Longhouse Manor | cat:woodland_longhouse_manor | 0.00002104% | 0.0008 | about 1 per 34,900 x 34,900 blocks | mode surface, tier 3; biomes: Red Blossom Hollow, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, Rootbound Sanctuary, Cabin of the List... |
| Archive of the Debt Tribunal | cat:archive_debt_tribunal | 0.00002084% | 0.0008 | about 1 per 35,000 x 35,000 blocks | mode buried, tier 4; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Palimpsest Vaultlands, Capitol Ossuary, Neon Dust Basin, Shelterfall Terraces,... |
| Marsh Sampling Dome and Tide Lab | cat:research_marsh_sampling_dome | 0.00002076% | 0.0008 | about 1 per 35,100 x 35,100 blocks | mode underwater, tier 3; biomes: Ashveil Coast, Drowned Borough, Blackreed Marsh, Sunken Convoy, Vaultroot Gardens, Brine Lantern Mire, Leechwater Dep... |
| Inverted Orchard Conservatory and Root Vault | cat:conservatory_inverted_orchard | 0.00002074% | 0.0008 | about 1 per 35,100 x 35,100 blocks | mode buried, tier 4; biomes: Red Blossom Hollow, Sanguine Exclusion Forest, Bitter Orchard, Vaultroot Gardens, Rootbound Sanctuary, Cabin of the Liste... |
| Viridian Blastlands Decontamination Well | cat:viridian_decontamination_well | 0.00002005% | 0.0008 | about 1 per 35,700 x 35,700 blocks | mode buried, tier 5, exclusive; biomes: Viridian Blastlands |
| Gluttony Carrion Slough Gutworks | cat:gluttony_carrion_drain | 0.00001997% | 0.0008 | about 1 per 35,800 x 35,800 blocks | mode buried, tier 4, exclusive; biomes: III - Gluttony: Carrion Slough |
| Command Horseshoe Bunker | cat:command_horseshoe_bunker | 0.00001966% | 0.0008 | about 1 per 36,100 x 36,100 blocks | mode buried, tier 4; biomes: Abandoned Cordon, Dead Array, Oxide Railgrave, Glass Reactor, Bleached Expanse, Viridian Blastlands, Coalbreath Ridges, I... |
| Penitent Spillway Prison Sluices | cat:penitent_spillway_prison | 0.00001956% | 0.0008 | about 1 per 36,200 x 36,200 blocks | mode buried, tier 4, exclusive; biomes: Penitent Spillway |
| Stormglass Observatory Manor | cat:stormglass_manor | 0.00001920% | 0.0008 | about 1 per 36,500 x 36,500 blocks | mode surface, tier 3; biomes: Whiteout Reservoir, Signalwreck Coast, Dead Array, Gravity Scar, Glass Reactor, Gilded Hush, Frostcrown Ramparts, Mirror... |
| Viridian Blastlands Radial Shelter | cat:viridian_blast_bunker | 0.00001894% | 0.0007 | about 1 per 36,800 x 36,800 blocks | mode buried, tier 4, exclusive; biomes: Viridian Blastlands |
| Lust Unceasing Gale Spires | cat:lust_gale_spires | 0.00001889% | 0.0007 | about 1 per 36,800 x 36,800 blocks | mode surface, tier 5, exclusive; biomes: II - Lust: The Unceasing Gale |
| Urban Technical Campus | cat:urban_technical_campus | 0.00001877% | 0.0007 | about 1 per 36,900 x 36,900 blocks | mode surface, tier 3; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Bloomfall Tenements, Abandoned Cordon, Oxide Railgrave, Capitol Ossuary, Neon... |
| Blackgutter Suspended Borough | cat:blackgutter_suspended_borough | 0.00001869% | 0.0007 | about 1 per 37,000 x 37,000 blocks | mode surface, tier 4, exclusive; biomes: Blackgutter Chasm |
| Cinderlake Triple Kiln Towers | cat:cinderlake_kiln_towers | 0.00001843% | 0.0007 | about 1 per 37,300 x 37,300 blocks | mode surface, tier 5, exclusive; biomes: Cinderlake Shelf |
| Gravity Scar Folded Laboratory | cat:gravity_scar_gate_lab | 0.00001823% | 0.0007 | about 1 per 37,500 x 37,500 blocks | mode buried, tier 4, exclusive; biomes: Gravity Scar |
| Coalbreath Mine Drain Labyrinth | cat:coalbreath_mine_sewers | 0.00001820% | 0.0007 | about 1 per 37,500 x 37,500 blocks | mode buried, tier 4, exclusive; biomes: Coalbreath Ridges |
| Dust Courtyard Homes | cat:dust_courtyard_homes | 0.00001810% | 0.0007 | about 1 per 37,600 x 37,600 blocks | mode surface, tier 1; biomes: Abandoned Cordon, Bleached Expanse, Capitol Ossuary, Neon Dust Basin, Shelterfall Terraces, Iron Convoy Barrens, Forgott... |
| Tenement Wound Stair Borough | cat:tenement_wound_steps | 0.00001772% | 0.0007 | about 1 per 38,000 x 38,000 blocks | mode surface, tier 4, exclusive; biomes: Tenement Wound |
| Signalwreck Fallen Relay and Phone Exchange | cat:signalwreck_cell_chain | 0.00001766% | 0.0007 | about 1 per 38,100 x 38,100 blocks | mode surface, tier 3, exclusive; biomes: Signalwreck Coast |
| Forgotten Monarch Shore Fort | cat:monarch_shore_fort | 0.00001749% | 0.0007 | about 1 per 38,300 x 38,300 blocks | mode surface, tier 4, exclusive; biomes: Forgotten Monarch Shore |
| Neon Dust Casino Mile | cat:neon_dust_casino | 0.00001731% | 0.0007 | about 1 per 38,500 x 38,500 blocks | mode surface, tier 4, exclusive; biomes: Neon Dust Basin |
| Frostcrown Star Bastion | cat:frostcrown_star_bastion | 0.00001692% | 0.0007 | about 1 per 38,900 x 38,900 blocks | mode surface, tier 5, exclusive; biomes: Frostcrown Ramparts |
| Bloodroot Lumber Baron's Manor | cat:bloodroot_lumber_manor | 0.00001685% | 0.0007 | about 1 per 39,000 x 39,000 blocks | mode surface, tier 3, exclusive; biomes: Bloodroot Timberland |
| Red Blossom Petal Manor | cat:blossom_shrine_manor | 0.00001672% | 0.0007 | about 1 per 39,100 x 39,100 blocks | mode surface, tier 2, exclusive; biomes: Red Blossom Hollow |
| Treachery Cocytus Icebound Vault | cat:treachery_cocytus_vault | 0.00001654% | 0.0006 | about 1 per 39,300 x 39,300 blocks | mode buried, tier 5, exclusive; biomes: IX - Treachery: Cocytus |
| Broken Delta Cargo Plane | cat:broken_cargo_delta | 0.00001654% | 0.0006 | about 1 per 39,300 x 39,300 blocks | mode surface, tier 2; biomes: Abandoned Cordon, Dead Array, Oxide Railgrave, Bleached Expanse, Viridian Blastlands, Iron Convoy Barrens, Cinderlake Sh... |
| Fraud Malebolge Ten Trench Underworks | cat:fraud_malebolge_underworks | 0.00001651% | 0.0006 | about 1 per 39,400 x 39,400 blocks | mode buried, tier 5, exclusive; biomes: VIII - Fraud: Malebolge Trenches |
| Metro Crossline Interchange and Civil Shelter | cat:metro_crossline_interchange | 0.00001646% | 0.0006 | about 1 per 39,400 x 39,400 blocks | mode buried, tier 4; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Bloomfall Tenements, Oxide Railgrave, Capitol Ossuary, Shelterfall Terraces, C... |
| Sanguine Exclusion Hunting Castle | cat:sanguine_hunting_castle | 0.00001633% | 0.0006 | about 1 per 39,600 x 39,600 blocks | mode surface, tier 4, exclusive; biomes: Sanguine Exclusion Forest |
| Brotherhood Slagfield Forge Keep | cat:brotherhood_forge_keep | 0.00001623% | 0.0006 | about 1 per 39,700 x 39,700 blocks | mode surface, tier 4, exclusive; biomes: Brotherhood Slagfields |
| Cinder Hospice Comb Ward | cat:cinder_hospice_wings | 0.00001615% | 0.0006 | about 1 per 39,800 x 39,800 blocks | mode surface, tier 3, exclusive; biomes: Cinder Hospice |
| Grinning Hollow Folded Funhouse | cat:grinning_hollow_funhouse | 0.00001613% | 0.0006 | about 1 per 39,800 x 39,800 blocks | mode surface, tier 3, exclusive; biomes: Grinning Hollow |
| Dead Array Antenna Switchyard | cat:dead_array_switchyard | 0.00001610% | 0.0006 | about 1 per 39,900 x 39,900 blocks | mode surface, tier 3, exclusive; biomes: Dead Array |
| Last Ember Cathedral and Crypts | cat:last_ember_cathedral | 0.00001610% | 0.0006 | about 1 per 39,900 x 39,900 blocks | mode surface, tier 5, exclusive; biomes: Cathedral of the Last Ember |
| Violence Thornblood Inquisition Towers | cat:violence_thornblood_towers | 0.00001610% | 0.0006 | about 1 per 39,900 x 39,900 blocks | mode surface, tier 5, exclusive; biomes: VII - Violence: Thornblood Reach |
| Bleached Expanse Vault Settlement | cat:bleached_vault_settlement | 0.00001597% | 0.0006 | about 1 per 40,000 x 40,000 blocks | mode buried, tier 3, exclusive; biomes: Bleached Expanse |
| Rootbound Sanctuary Cloister Keep | cat:rootbound_cloister | 0.00001597% | 0.0006 | about 1 per 40,000 x 40,000 blocks | mode surface, tier 3, exclusive; biomes: Rootbound Sanctuary |
| Vaultroot Conservatory Manor | cat:vaultroot_glass_manor | 0.00001595% | 0.0006 | about 1 per 40,100 x 40,100 blocks | mode surface, tier 3, exclusive; biomes: Vaultroot Gardens |
| Bastion of the Unburied Siege Castle | cat:unburied_siege_castle | 0.00001582% | 0.0006 | about 1 per 40,200 x 40,200 blocks | mode surface, tier 5, exclusive; biomes: Bastion of the Unburied |
| Gilded Hush Casino Palace | cat:gilded_hush_palace | 0.00001572% | 0.0006 | about 1 per 40,400 x 40,400 blocks | mode surface, tier 4, exclusive; biomes: Gilded Hush |
| Switchboard Town and Wire Lines | cat:switchboard_town | 0.00001567% | 0.0006 | about 1 per 40,400 x 40,400 blocks | mode surface, tier 2; biomes: Rustwater Ward, Gutterglass Schoolgrounds, Bloomfall Tenements, Abandoned Cordon, Oxide Railgrave, Shelterfall Terraces,... |
| Cordon Driver School and Checkpoint | cat:cordon_vehicle_school | 0.00001559% | 0.0006 | about 1 per 40,500 x 40,500 blocks | mode surface, tier 2, exclusive; biomes: Abandoned Cordon |
| Shelterfall Terraced Vault School | cat:shelterfall_school_vault | 0.00001557% | 0.0006 | about 1 per 40,600 x 40,600 blocks | mode buried, tier 3, exclusive; biomes: Shelterfall Terraces |
| Glass Reactor Twin Containment | cat:glass_reactor_containment | 0.00001551% | 0.0006 | about 1 per 40,600 x 40,600 blocks | mode buried, tier 5, exclusive; biomes: Glass Reactor |
| Fortified Market City | cat:fortified_market_city | 0.00001549% | 0.0006 | about 1 per 40,700 x 40,700 blocks | mode surface, tier 4; biomes: Abandoned Cordon, Bitter Orchard, Shelterfall Terraces, Rootbound Sanctuary, Forgotten Monarch Shore, Cathedral of the L... |
| Boomtown Midnight Main Street | cat:boomtown_main_street | 0.00001541% | 0.0006 | about 1 per 40,800 x 40,800 blocks | mode surface, tier 3, exclusive; biomes: Boomtown After Midnight |
| Endless Night Telephone Boulevard | cat:endless_night_phone_boulevard | 0.00001531% | 0.0006 | about 1 per 40,900 x 40,900 blocks | mode surface, tier 3, exclusive; biomes: Endless Night Boulevard |
| Whiteout Reservoir Boarding School | cat:whiteout_reservoir_school | 0.00001526% | 0.0006 | about 1 per 41,000 x 41,000 blocks | mode surface, tier 3, exclusive; biomes: Whiteout Reservoir |
| Kandarian Hunting Grounds Lodge Keep | cat:kandarian_hunt_lodge | 0.00001523% | 0.0006 | about 1 per 41,000 x 41,000 blocks | mode surface, tier 4, exclusive; biomes: Kandarian Hunting Grounds |
| Greed Burdened Quarry Vault | cat:greed_quarry_vault | 0.00001521% | 0.0006 | about 1 per 41,000 x 41,000 blocks | mode buried, tier 5, exclusive; biomes: IV - Greed: The Burdened Quarry |
| Bitter Orchard Seven-House Homestead | cat:bitter_orchard_homestead | 0.00001516% | 0.0006 | about 1 per 41,100 x 41,100 blocks | mode surface, tier 1, exclusive; biomes: Bitter Orchard |
| Limbo Mourning Fields Academy | cat:limbo_mourning_academy | 0.00001500% | 0.0006 | about 1 per 41,300 x 41,300 blocks | mode surface, tier 3, exclusive; biomes: I - Limbo: Mourning Fields |
| Possessed Orchard Four-Barn Farm | cat:possessed_orchard_farm | 0.00001493% | 0.0006 | about 1 per 41,400 x 41,400 blocks | mode surface, tier 2, exclusive; biomes: Possessed Orchard |
| Palimpsest Descending Archive | cat:palimpsest_archive | 0.00001477% | 0.0006 | about 1 per 41,600 x 41,600 blocks | mode buried, tier 4, exclusive; biomes: Palimpsest Vaultlands |
| Carousel Wastes Midway Academy | cat:carousel_midway_school | 0.00001477% | 0.0006 | about 1 per 41,600 x 41,600 blocks | mode surface, tier 2, exclusive; biomes: Carousel Wastes |
| Bloomfall Butterfly Tenements | cat:bloomfall_court | 0.00001457% | 0.0006 | about 1 per 41,900 x 41,900 blocks | mode surface, tier 3, exclusive; biomes: Bloomfall Tenements |
| Heresy Sepulcher Ember Catacombs | cat:heresy_ember_catacombs | 0.00001439% | 0.0006 | about 1 per 42,200 x 42,200 blocks | mode buried, tier 4, exclusive; biomes: VI - Heresy: Sepulcher of Embers |
| Mirrorward Loop Hospital | cat:mirrorward_loop_hospital | 0.00001423% | 0.0006 | about 1 per 42,400 x 42,400 blocks | mode buried, tier 4, exclusive; biomes: Mirrorward Thicket |
| Iron Convoy Cargo Plane Wreck | cat:iron_convoy_transport | 0.00001421% | 0.0006 | about 1 per 42,400 x 42,400 blocks | mode surface, tier 2, exclusive; biomes: Iron Convoy Barrens |
| Hollow Crown Borough Citadel | cat:hollow_crown_citadel | 0.00001393% | 0.0005 | about 1 per 42,900 x 42,900 blocks | mode surface, tier 5, exclusive; biomes: Hollow Crown Borough |
| Oxide Railgrave Freight Borough | cat:oxide_rail_terminal | 0.00001385% | 0.0005 | about 1 per 43,000 x 43,000 blocks | mode surface, tier 3, exclusive; biomes: Oxide Railgrave |
| Gutterglass Four-Court Campus | cat:gutterglass_campus | 0.00001365% | 0.0005 | about 1 per 43,300 x 43,300 blocks | mode surface, tier 3, exclusive; biomes: Gutterglass Schoolgrounds |
| Catacomb of the Drowned Bell | cat:catacomb_drowned_bell | 0.00001339% | 0.0005 | about 1 per 43,700 x 43,700 blocks | mode underwater, tier 4; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Blackreed Marsh, Brine Lantern Mire, Leechwater Depths, Forgotten ... |
| Canal Grid Metropolis | cat:canal_grid_metropolis | 0.00001331% | 0.0005 | about 1 per 43,900 x 43,900 blocks | mode surface, tier 5; biomes: Ashveil Coast, Rustwater Ward, Drowned Borough, Bloomfall Tenements, Signalwreck Coast, Capitol Ossuary, Brine Lantern M... |
| Ashveil Tidal Capital | cat:ashveil_tidal_capital | 0.00001324% | 0.0005 | about 1 per 44,000 x 44,000 blocks | mode underwater, tier 5, exclusive; biomes: Ashveil Coast; needs >= 90% of room samples below y62 |
| Capitol Ossuary Monumental City | cat:capitol_ossuary_city | 0.00001295% | 0.0005 | about 1 per 44,500 x 44,500 blocks | mode surface, tier 5, exclusive; biomes: Capitol Ossuary |
| Deadward Asylum Panopticon | cat:deadward_panopticon | 0.00001290% | 0.0005 | about 1 per 44,500 x 44,500 blocks | mode surface, tier 4, exclusive; biomes: Deadward Asylum |
| Radial Civic City | cat:radial_civic_city | 0.00001288% | 0.0005 | about 1 per 44,600 x 44,600 blocks | mode surface, tier 5; biomes: Oxide Railgrave, Bleached Expanse, Capitol Ossuary, Neon Dust Basin, Viridian Blastlands, Iron Convoy Barrens, Gilded Hu... |
| Metro Coastal Pressure Station | cat:metro_coastal_pressure_station | 0.00001254% | 0.0005 | about 1 per 45,200 x 45,200 blocks | mode underwater, tier 4; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Signalwreck Coast, Sunken Convoy, Brine Lantern Mire, Leechwater D... |
| Rustwater Split-Avenue Metropolis | cat:rustwater_metropolis | 0.00001249% | 0.0005 | about 1 per 45,300 x 45,300 blocks | mode surface, tier 5, exclusive; biomes: Rustwater Ward |
| Ringfall Ashen Ring Capital | cat:ringfall_ashen_capital | 0.00001224% | 0.0005 | about 1 per 45,700 x 45,700 blocks | mode surface, tier 5, exclusive; biomes: Ringfall Cinders |
| Wrath Styx Underbridge City | cat:wrath_styx_underbridge | 0.00001201% | 0.0005 | about 1 per 46,200 x 46,200 blocks | mode underwater, tier 5, exclusive; biomes: V - Wrath: Styx Blackwater; needs >= 90% of room samples below y62 |
| Submerged Drydock and Salvage Works | cat:harbor_submerged_drydock | 0.00001167% | 0.0005 | about 1 per 46,800 x 46,800 blocks | mode underwater, tier 4; biomes: Ashveil Coast, Drowned Borough, Sunken Convoy, Brine Lantern Mire, Leechwater Depths, Drowned Oath Fen, III - Glutton... |
| Ice Watch Hamlet | cat:ice_watch_hamlet | 0.00001157% | 0.0005 | about 1 per 47,000 x 47,000 blocks | mode surface, tier 2; biomes: Whiteout Reservoir, Viridian Blastlands, Frostcrown Ramparts, III - Gluttony: Carrion Slough, IX - Treachery: Cocytus |
| Leechwater Lantern Depths | cat:leechwater_sunken_city | 0.00001001% | 0.0004 | about 1 per 50,600 x 50,600 blocks | mode underwater, tier 5, exclusive; biomes: Leechwater Depths; needs >= 90% of room samples below y62 |
| Sunken Convoy Broken Airliner | cat:sunken_convoy_airliner | 0.000008807% | 0.0003 | about 1 per 53,900 x 53,900 blocks | mode underwater, tier 3, exclusive; biomes: Sunken Convoy; needs >= 90% of room samples below y62 |
| Research Containment Spokes | cat:research_containment_spokes | 0.000008551% | 0.0003 | about 1 per 54,700 x 54,700 blocks | mode buried, tier 5; biomes: Abandoned Cordon, Dead Array, Sanguine Exclusion Forest, Gravity Scar, Glass Reactor, Viridian Blastlands, Vaultroot Gard... |
| Sunken Dome District | cat:sunken_dome_district | 0.000007527% | 0.0003 | about 1 per 58,300 x 58,300 blocks | mode underwater, tier 5; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Blackreed Marsh, Sunken Convoy, Brine Lantern Mire, Leechwater Dep... |
| Polar Survey Wreck and Relay | cat:polar_survey_plane | 0.000007271% | 0.0003 | about 1 per 59,300 x 59,300 blocks | mode surface, tier 3; biomes: Whiteout Reservoir, Frostcrown Ramparts, II - Lust: The Unceasing Gale, IX - Treachery: Cocytus |
| Coastal Airlock Borough | cat:coastal_airlock_borough | 0.000006759% | 0.0003 | about 1 per 61,500 x 61,500 blocks | mode underwater, tier 3; biomes: Ashveil Coast, Drowned Borough, Penitent Spillway, Blackreed Marsh, Brine Lantern Mire, Leechwater Depths, Drowned Oa... |
| Brine Lantern Underport | cat:brine_lantern_underport | 0.000006733% | 0.0003 | about 1 per 61,700 x 61,700 blocks | mode underwater, tier 4, exclusive; biomes: Brine Lantern Mire; needs >= 90% of room samples below y62 |
| Metro Memorial Terminal and Catacomb Platforms | cat:metro_memorial_terminal | 0.000006656% | 0.0003 | about 1 per 62,000 x 62,000 blocks | mode buried, tier 5; biomes: Rustwater Ward, Palimpsest Vaultlands, Oxide Railgrave, Capitol Ossuary, Hollow Crown Borough, Ringfall Cinders, Boomtown... |
| Drowned Borough Arcade City | cat:drowned_borough_arcades | 0.000006503% | 0.0003 | about 1 per 62,700 x 62,700 blocks | mode underwater, tier 4, exclusive; biomes: Drowned Borough; needs >= 90% of room samples below y62 |
| Sunken Research Campus | cat:sunken_research_campus | 0.000005504% | 0.0002 | about 1 per 68,200 x 68,200 blocks | mode underwater, tier 4; biomes: Ashveil Coast, Drowned Borough, Signalwreck Coast, Sunken Convoy, Brine Lantern Mire, Leechwater Depths, Drowned Oath... |
| Drowned Oath Submerged Cloister | cat:drowned_oath_subcloister | 0.000004813% | 0.0002 | about 1 per 72,900 x 72,900 blocks | mode underwater, tier 4, exclusive; biomes: Drowned Oath Fen; needs >= 90% of room samples below y62 |

### JasprImportedWorldgen GLM_freebuff (45)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| Horror Laboratory | glm:B74 | 0.0008782% | 0.0343 | about 1 per 5,400 x 5,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 21x8x14 blocks; picked in 137,422 cel... |
| Subway Station | glm:B75 | 0.0008638% | 0.0337 | about 1 per 5,400 x 5,400 blocks | underground: top at y61, lowest of 5 samples must exceed y65; 17x11x17 blocks; picked in 135,773 cells, placed in 134,959 (failed attempts: terrain/ha... |
| Vice City Lawyer's Office | glm:B78 | 0.0008492% | 0.0332 | about 1 per 5,500 x 5,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 14x16x14 blocks; picked in 132,843 ce... |
| Satriale's Pork Store | glm:B50 | 0.0007411% | 0.0289 | about 1 per 5,900 x 5,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 34x8x30 blocks; picked in 117,073 cel... |
| Rustic Log Cabin | glm:B30 | 0.0007385% | 0.0288 | about 1 per 5,900 x 5,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 22x25x35 blocks; picked in 116,200 ce... |
| Subterranean Survival Bunker | glm:B01 | 0.0006048% | 0.0236 | about 1 per 6,500 x 6,500 blocks | underground: top at y61, lowest of 5 samples must exceed y65; 52x13x39 blocks; picked in 96,035 cells, placed in 94,485 (failed attempts: terrain/habi... |
| Detailed Medieval Residence | glm:B21 | 0.0006000% | 0.0234 | about 1 per 6,500 x 6,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 25x28x47 blocks; picked in 95,018 cel... |
| Enchanted Apprentice Tower | glm:B32 | 0.0005915% | 0.0231 | about 1 per 6,600 x 6,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 23x56x23 blocks; picked in 92,712 cel... |
| Brewery and Tavern | glm:B19 | 0.0005905% | 0.0231 | about 1 per 6,600 x 6,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 40x35x35 blocks; picked in 94,214 cel... |
| Los Pollos Hermanos | glm:B77 | 0.0005877% | 0.0230 | about 1 per 6,600 x 6,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 58x9x29 blocks; picked in 94,348 cell... |
| Addams Family Mansion | glm:B37 | 0.0005876% | 0.0230 | about 1 per 6,600 x 6,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 28x32x47 blocks; picked in 93,309 cel... |
| Wizard Tower Survival Base | glm:B31 | 0.0005829% | 0.0228 | about 1 per 6,600 x 6,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 29x103x28 blocks; picked in 91,933 ce... |
| Japanese Style Mansion | glm:B39 | 0.0005297% | 0.0207 | about 1 per 7,000 x 7,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 47x37x42 blocks; picked in 85,708 cel... |
| Industrial Factory | glm:B27 | 0.0005244% | 0.0205 | about 1 per 7,000 x 7,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 57x46x40 blocks; picked in 86,292 cel... |
| Horse Stables | glm:B20 | 0.0005208% | 0.0203 | about 1 per 7,000 x 7,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 60x18x53 blocks; picked in 89,504 cel... |
| Medium School Building | glm:B25 | 0.0005174% | 0.0202 | about 1 per 7,000 x 7,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 60x20x40 blocks; picked in 85,376 cel... |
| Hobbit Hole Mailroom | glm:B38 | 0.0003596% | 0.0140 | about 1 per 8,400 x 8,400 blocks | underground: top at y61, lowest of 5 samples must exceed y65; 20x22x95 blocks; picked in 57,460 cells, placed in 56,185 (failed attempts: terrain/habi... |
| Art and Archaeology Museum | glm:B24 | 0.0003505% | 0.0137 | about 1 per 8,500 x 8,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 43x36x51 blocks; picked in 57,513 cel... |
| Underground Storage Facility | glm:B26 | 0.0003494% | 0.0136 | about 1 per 8,600 x 8,600 blocks | underground: top at y61, lowest of 5 samples must exceed y65; 45x19x45 blocks; picked in 55,463 cells, placed in 54,592 (failed attempts: terrain/habi... |
| Rivertown Library | glm:B49 | 0.0003454% | 0.0135 | about 1 per 8,600 x 8,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 39x41x53 blocks; picked in 56,412 cel... |
| Majestic Royal Mansion | glm:B14 | 0.0003402% | 0.0133 | about 1 per 8,700 x 8,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 53x36x41 blocks; picked in 55,732 cel... |
| The Vannah Hotel | glm:B10 | 0.0003252% | 0.0127 | about 1 per 8,900 x 8,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 78x24x44 blocks; picked in 56,486 cel... |
| Pillager Stronghold Guild Hall | glm:B55 | 0.0003153% | 0.0123 | about 1 per 9,000 x 9,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 47x51x45 blocks; picked in 51,292 cel... |
| Medieval Lighthouse Dwelling | glm:B06 | 0.0003051% | 0.0119 | about 1 per 9,200 x 9,200 blocks | shore: some but not all of the 5 samples below y62; origin at waterline; 60x63x73 blocks; picked in 93,621 cells, placed in 47,670 (failed attempts: t... |
| Leniland Great Hall | glm:B56 | 0.0002808% | 0.0110 | about 1 per 9,500 x 9,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 89x59x35 blocks; picked in 49,507 cel... |
| Coastal Lighthouse Residence | glm:B05 | 0.0002265% | 0.0088 | about 1 per 10,600 x 10,600 blocks | shore: some but not all of the 5 samples below y62; origin at waterline; 20x56x20 blocks; picked in 112,149 cells, placed in 35,383 (failed attempts: ... |
| Coastal Lighthouse Residence | glm:B73 | 0.0002256% | 0.0088 | about 1 per 10,700 x 10,700 blocks | shore: some but not all of the 5 samples below y62; origin at waterline; 20x56x20 blocks; picked in 112,816 cells, placed in 35,246 (failed attempts: ... |
| The Skeld Ship Map | glm:B33 | 0.0002102% | 0.0082 | about 1 per 11,000 x 11,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 68x19x117 blocks; picked in 53,634 ce... |
| Medieval Sailing Ship | glm:B35 | 0.0001732% | 0.0068 | about 1 per 12,200 x 12,200 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 50x41x27 blocks; picked in 117,033 cells, placed in 27,053 (failed attempts: terrain/habitat 794,45... |
| Corporate Office Building | glm:B16 | 0.0001517% | 0.0059 | about 1 per 13,000 x 13,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 51x67x40 blocks; picked in 24,769 cel... |
| Haunted House with Giant Pumpkin | glm:B57 | 0.0001319% | 0.0052 | about 1 per 13,900 x 13,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 52x71x49 blocks; picked in 22,753 cel... |
| Grand Chinese Mansion | glm:B36 | 0.0001040% | 0.0041 | about 1 per 15,700 x 15,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 41x38x151 blocks; picked in 23,704 ce... |
| Japanese Feudal Fortress | glm:B53 | 0.00009763% | 0.0038 | about 1 per 16,200 x 16,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 72x73x97 blocks; picked in 22,920 cel... |
| Spacious Grand Mansion | glm:B13 | 0.00008669% | 0.0034 | about 1 per 17,200 x 17,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 55x99x135 blocks; picked in 21,858 ce... |
| Pirate Shipwreck Island Base | glm:B04 | 0.00007691% | 0.0030 | about 1 per 18,200 x 18,200 blocks | shore: some but not all of the 5 samples below y62; origin at waterline; 53x63x59 blocks; picked in 24,535 cells, placed in 12,016 (failed attempts: t... |
| Egyptian Temple Trading Hall | glm:B62 | 0.00006223% | 0.0024 | about 1 per 20,300 x 20,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 109x43x116 blocks; picked in 23,097 c... |
| Marine Temple Mansion | glm:B67 | 0.00005748% | 0.0022 | about 1 per 21,100 x 21,100 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 62x34x62 blocks; picked in 51,699 cells, placed in 8,981 (failed attempts: terrain/habitat 366,867,... |
| High Security Jail | glm:B40 | 0.00004445% | 0.0017 | about 1 per 24,000 x 24,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 131x54x119 blocks; picked in 22,852 c... |
| Underground Maze Dungeon | glm:B51 | 0.00003180% | 0.0012 | about 1 per 28,400 x 28,400 blocks | underground: top at y61, lowest of 5 samples must exceed y65; 129x32x137 blocks; picked in 8,213 cells, placed in 4,969 (failed attempts: terrain/habi... |
| Tilted Towers | glm:B07 | 0.00001392% | 0.0005 | about 1 per 42,900 x 42,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 180x49x155 blocks; picked in 23,495 c... |
| Gothic Cathedral | glm:B76 | 0.00001279% | 0.0005 | about 1 per 44,700 x 44,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 113x69x148 blocks; picked in 7,834 ce... |
| Sacred Blood Gothic Church | glm:B15 | 0.00001167% | 0.0005 | about 1 per 46,800 x 46,800 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 142x151x141 blocks; picked in 7,689 c... |
| Diamond Casino | glm:B45 | 0.000001075% | 0.0000 | about 1 per 154,300 x 154,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 218x76x206 blocks; picked in 7,926 ce... |
| Italian Vineyard Estate | glm:B61 | 0.000001056% | 0.0000 | about 1 per 155,700 x 155,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 8 blocks; origin = centre ground + 1 - surfaceAnchor; 199x67x212 blocks; picked in 6,472 ce... |
| Titanic Wreck | glm:B44 | 0.00000003200% | 0.0000 | about 1 per 894,400 x 894,400 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 511x217x93 blocks; picked in 7,669 cells, placed in 5 (failed attempts: terrain/habitat 61,265, cat... |

### JasprImportedWorldgen ChatGPT_Codex_Structures (98)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| Ultimate End Ship Survival Base | codex:cbd_058 | 0.0002048% | 0.0080 | about 1 per 11,200 x 11,200 blocks | sky: floats at max(highest sample + 20, 120); 65x63x107 blocks; picked in 32,002 cells, placed in 32,002 (failed attempts: terrain/habitat 0, catalogu... |
| Cozy Hobbit Hole Dwelling | codex:cbd_005 | 0.0002026% | 0.0079 | about 1 per 11,200 x 11,200 blocks | embedded: dry, 5 samples within 8 blocks; sunk 3 blocks; 22x13x25 blocks; picked in 31,771 cells, placed in 31,657 (failed attempts: terrain/habitat 1... |
| Six-Story Hotel with Furnished Rooms | codex:cbd_010 | 0.0002011% | 0.0079 | about 1 per 11,300 x 11,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 20x31x17 blocks; picked in 31,668 cel... |
| Enchanted Wizard Apprentice Tower | codex:cbd_029 | 0.0001998% | 0.0078 | about 1 per 11,300 x 11,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 23x56x23 blocks; picked in 31,668 cel... |
| Squidward House | codex:cbd_012 | 0.0001984% | 0.0077 | about 1 per 11,400 x 11,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 23x25x29 blocks; picked in 31,706 cel... |
| Wizard Tower Survival Base Build | codex:cbd_028 | 0.0001966% | 0.0077 | about 1 per 11,400 x 11,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 29x103x28 blocks; picked in 31,690 ce... |
| Moe's Tavern Simpsons Build | codex:cbd_027 | 0.0001963% | 0.0077 | about 1 per 11,400 x 11,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 34x10x20 blocks; picked in 31,623 cel... |
| The Simpsons Family House | codex:cbd_026 | 0.0001934% | 0.0076 | about 1 per 11,500 x 11,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 31x21x32 blocks; picked in 31,748 cel... |
| SpongeBob's Pineapple House | codex:cbd_004 | 0.0001925% | 0.0075 | about 1 per 11,500 x 11,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 25x23x39 blocks; picked in 31,533 cel... |
| Modern Police Station Structure | codex:cbd_023 | 0.0001915% | 0.0075 | about 1 per 11,600 x 11,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 27x9x36 blocks; picked in 31,479 cell... |
| Stone Fortress | codex:cbd_001 | 0.0001912% | 0.0075 | about 1 per 11,600 x 11,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 30x35x35 blocks; picked in 31,647 cel... |
| Hagrid's Magical Hut | codex:cbd_021 | 0.0001876% | 0.0073 | about 1 per 11,700 x 11,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 34x28x35 blocks; picked in 31,803 cel... |
| Spruce Tree Mansion With Interior | codex:cbd_025 | 0.0001872% | 0.0073 | about 1 per 11,700 x 11,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 45x20x30 blocks; picked in 31,802 cel... |
| Spooky Addams Family Mansion House | codex:cbd_002 | 0.0001871% | 0.0073 | about 1 per 11,700 x 11,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 28x32x47 blocks; picked in 31,700 cel... |
| Krusty Krab Restaurant Replica | codex:cbd_007 | 0.0001848% | 0.0072 | about 1 per 11,800 x 11,800 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 34x21x42 blocks; picked in 31,911 cel... |
| The Vannah Hotel Large Build | codex:cbd_022 | 0.0001838% | 0.0072 | about 1 per 11,800 x 11,800 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 37x24x40 blocks; picked in 31,800 cel... |
| Tudor Spruce Manor House | codex:cbd_013 | 0.0001787% | 0.0070 | about 1 per 12,000 x 12,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 46x28x37 blocks; picked in 31,817 cel... |
| Ultimate Underground Bunker Survival Base | codex:cbd_064 | 0.0001764% | 0.0069 | about 1 per 12,000 x 12,000 blocks | underground: top at y61, lowest of 5 samples must exceed y65; 93x45x84 blocks; picked in 31,742 cells, placed in 27,563 (failed attempts: terrain/habi... |
| Ultimate Player Favorite Giant Elytra Sky Base | codex:cbd_071 | 0.0001721% | 0.0067 | about 1 per 12,200 x 12,200 blocks | sky: floats at max(highest sample + 20, 120); 127x77x120 blocks; picked in 26,896 cells, placed in 26,891 (failed attempts: terrain/habitat 0, catalog... |
| Grand Medieval Fantasy Survival House | codex:cbd_050 | 0.0001702% | 0.0066 | about 1 per 12,300 x 12,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 40x39x50 blocks; picked in 31,512 cel... |
| Adventure Time Cartoon House | codex:cbd_008 | 0.0001667% | 0.0065 | about 1 per 12,400 x 12,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 51x57x41 blocks; picked in 31,678 cel... |
| Glass House New Canaan Villa | codex:cbd_044 | 0.0001430% | 0.0056 | about 1 per 13,400 x 13,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 79x22x45 blocks; picked in 31,862 cel... |
| Massive Dark Oak Medieval Mansion | codex:cbd_009 | 0.0001397% | 0.0055 | about 1 per 13,500 x 13,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 61x28x61 blocks; picked in 31,692 cel... |
| Grand Fantasy Survival Castle Base | codex:cbd_094 | 0.0001198% | 0.0047 | about 1 per 14,600 x 14,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 62x59x65 blocks; picked in 29,178 cel... |
| Ultimate Waterfall Cave Mansion Survival Base | codex:cbd_096 | 0.0001191% | 0.0047 | about 1 per 14,700 x 14,700 blocks | embedded: dry, 5 samples within 8 blocks; sunk 3 blocks; 67x62x69 blocks; picked in 26,843 cells, placed in 18,604 (failed attempts: terrain/habitat 8... |
| Edinburgh Castle Scotland | codex:cbd_036 | 0.0001166% | 0.0046 | about 1 per 14,800 x 14,800 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 75x78x61 blocks; picked in 29,459 cel... |
| Ultimate Modern Cliffside Treehouse Mansion Survival Base | codex:cbd_093 | 0.0001136% | 0.0044 | about 1 per 15,000 x 15,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 64x56x74 blocks; picked in 29,488 cel... |
| Ultimate Player Favorite Mine Entrance Lodge Survival Base | codex:cbd_051 | 0.0001122% | 0.0044 | about 1 per 15,100 x 15,100 blocks | embedded: dry, 5 samples within 8 blocks; sunk 3 blocks; 82x56x92 blocks; picked in 29,534 cells, placed in 17,531 (failed attempts: terrain/habitat 1... |
| Ultimate Fantasy Observatory Survival Base | codex:cbd_068 | 0.0001109% | 0.0043 | about 1 per 15,200 x 15,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 55x66x84 blocks; picked in 29,372 cel... |
| Grand Medieval Fantasy Survival Castle House | codex:cbd_039 | 0.0001104% | 0.0043 | about 1 per 15,200 x 15,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 66x64x66 blocks; picked in 29,161 cel... |
| Grand Fortified Medieval Stone Castle | codex:cbd_015 | 0.0001077% | 0.0042 | about 1 per 15,400 x 15,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 88x75x57 blocks; picked in 29,490 cel... |
| Ultimate Japanese Sakura Castle Survival Base | codex:cbd_100 | 0.0001063% | 0.0042 | about 1 per 15,500 x 15,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 67x72x75 blocks; picked in 29,389 cel... |
| Ultimate Player Favorite Giant Torch Survival Tower | codex:cbd_079 | 0.0001063% | 0.0042 | about 1 per 15,500 x 15,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 69x92x73 blocks; picked in 29,154 cel... |
| Ultimate Fantasy Crystal Lighthouse Survival Base | codex:cbd_095 | 0.0001040% | 0.0041 | about 1 per 15,700 x 15,700 blocks | shore: some but not all of the 5 samples below y62; origin at waterline; 63x67x66 blocks; picked in 31,814 cells, placed in 16,250 (failed attempts: t... |
| Ultimate Crescent Moon Observatory Castle Survival Base | codex:cbd_046 | 0.0001016% | 0.0040 | about 1 per 15,900 x 15,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 80x108x66 blocks; picked in 29,263 ce... |
| Ultimate Player Favorite Giant Golden Apple Survival House | codex:cbd_083 | 0.0001001% | 0.0039 | about 1 per 16,000 x 16,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 77x81x75 blocks; picked in 29,371 cel... |
| Verdant Gothic Cathedral | codex:cbd_003 | 0.00009243% | 0.0036 | about 1 per 16,600 x 16,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 54x124x114 blocks; picked in 31,758 c... |
| Gothic Style Cathedral Structure | codex:cbd_016 | 0.00009197% | 0.0036 | about 1 per 16,700 x 16,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 54x124x114 blocks; picked in 31,576 c... |
| Ultimate Giant Crafting Table Survival Base | codex:cbd_088 | 0.00009117% | 0.0036 | about 1 per 16,800 x 16,800 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 87x68x74 blocks; picked in 29,561 cel... |
| Ultimate All In One Survival House | codex:cbd_091 | 0.00008856% | 0.0035 | about 1 per 17,000 x 17,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 93x69x69 blocks; picked in 29,541 cel... |
| Grand Enchanted Library Survival Base | codex:cbd_066 | 0.00008488% | 0.0033 | about 1 per 17,400 x 17,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 80x49x84 blocks; picked in 29,538 cel... |
| Ultimate Cherry Blossom Survival Mansion | codex:cbd_042 | 0.00008471% | 0.0033 | about 1 per 17,400 x 17,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 80x64x89 blocks; picked in 29,761 cel... |
| Mistgate Sentinel Castle | codex:cbd_035 | 0.00008410% | 0.0033 | about 1 per 17,400 x 17,400 blocks | shore: some but not all of the 5 samples below y62; origin at waterline; 131x92x123 blocks; picked in 26,748 cells, placed in 13,140 (failed attempts:... |
| Ultimate Player Favorite Giant Grass Block House Survival Base | codex:cbd_080 | 0.00008364% | 0.0033 | about 1 per 17,500 x 17,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 81x75x81 blocks; picked in 29,809 cel... |
| Ultimate Medieval Survival Castle Mansion | codex:cbd_067 | 0.00008363% | 0.0033 | about 1 per 17,500 x 17,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 78x74x95 blocks; picked in 29,684 cel... |
| Ultimate Redstone Engineer Tower Survival Base | codex:cbd_061 | 0.00008332% | 0.0033 | about 1 per 17,500 x 17,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 78x84x81 blocks; picked in 27,087 cel... |
| Ultimate Super Smelter Workshop Survival Base | codex:cbd_097 | 0.00008330% | 0.0033 | about 1 per 17,500 x 17,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 89x75x71 blocks; picked in 26,960 cel... |
| Ultimate Nether Portal Survival Base | codex:cbd_090 | 0.00007959% | 0.0031 | about 1 per 17,900 x 17,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 83x75x85 blocks; picked in 29,365 cel... |
| Ultimate Player Favorite Giant Ender Chest Survival Base | codex:cbd_076 | 0.00007842% | 0.0031 | about 1 per 18,100 x 18,100 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 84x64x89 blocks; picked in 29,805 cel... |
| Adventure Time Tree House Build | codex:cbd_024 | 0.00007833% | 0.0031 | about 1 per 18,100 x 18,100 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 94x96x87 blocks; picked in 31,633 cel... |
| Modern Forest Mansion Survival Mega Base | codex:cbd_014 | 0.00007824% | 0.0031 | about 1 per 18,100 x 18,100 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 70x46x107 blocks; picked in 31,672 ce... |
| Ultimate Modern Airport Terminal Survival Base | codex:cbd_060 | 0.00007801% | 0.0030 | about 1 per 18,100 x 18,100 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 97x71x74 blocks; picked in 29,311 cel... |
| Ultimate Survival Starter Mega Base | codex:cbd_038 | 0.00007520% | 0.0029 | about 1 per 18,500 x 18,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 80x70x90 blocks; picked in 26,950 cel... |
| Grand Chinese Mansion | codex:cbd_011 | 0.00007330% | 0.0029 | about 1 per 18,700 x 18,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 41x38x151 blocks; picked in 29,102 ce... |
| Ultimate Frozen Ice Castle Survival Base | codex:cbd_062 | 0.00007098% | 0.0028 | about 1 per 19,000 x 19,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 91x79x91 blocks; picked in 29,177 cel... |
| Ultimate Modern Skyscraper Survival Tower | codex:cbd_070 | 0.00006970% | 0.0027 | about 1 per 19,200 x 19,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 72x104x106 blocks; picked in 26,910 c... |
| Ultimate Player Favorite Giant Minecart Rail Depot Survival Base | codex:cbd_047 | 0.00006850% | 0.0027 | about 1 per 19,300 x 19,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 94x75x82 blocks; picked in 27,095 cel... |
| Ultimate Giant Furnace Survival Base | codex:cbd_089 | 0.00006801% | 0.0027 | about 1 per 19,400 x 19,400 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 85x81x97 blocks; picked in 29,556 cel... |
| Ultimate Player Favorite Giant Sniffer Sanctuary Survival Base | codex:cbd_087 | 0.00006573% | 0.0026 | about 1 per 19,700 x 19,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 81x87x102 blocks; picked in 29,293 ce... |
| Ultimate Lunar Research Station Survival Base | codex:cbd_053 | 0.00006463% | 0.0025 | about 1 per 19,900 x 19,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 101x56x84 blocks; picked in 29,494 ce... |
| Ultimate Mega Storage Hall Survival Base | codex:cbd_031 | 0.00006453% | 0.0025 | about 1 per 19,900 x 19,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 87x64x102 blocks; picked in 29,448 ce... |
| Ultimate Player Favorite Giant TNT Survival House | codex:cbd_075 | 0.00006337% | 0.0025 | about 1 per 20,100 x 20,100 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 84x90x107 blocks; picked in 29,438 ce... |
| Victorian Queen Anne Mansion Villa | codex:cbd_018 | 0.00006275% | 0.0025 | about 1 per 20,200 x 20,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 96x64x96 blocks; picked in 29,563 cel... |
| Ultimate Player Favorite Beacon Crystal Tower Survival Base | codex:cbd_078 | 0.00006266% | 0.0024 | about 1 per 20,200 x 20,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 83x103x107 blocks; picked in 29,407 c... |
| Ultimate Player Favorite Diamond Crystal Mansion Survival Base | codex:cbd_069 | 0.00006251% | 0.0024 | about 1 per 20,200 x 20,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 96x83x96 blocks; picked in 29,421 cel... |
| Ultimate Cliffside Infinity Pool Mansion Survival Base | codex:cbd_045 | 0.00006237% | 0.0024 | about 1 per 20,300 x 20,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 105x59x91 blocks; picked in 29,734 ce... |
| Ultimate Magical Academy Survival Base | codex:cbd_056 | 0.00006069% | 0.0024 | about 1 per 20,500 x 20,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 92x57x96 blocks; picked in 26,806 cel... |
| Ultimate Player Favorite Giant Jukebox Music Hall Survival Base | codex:cbd_073 | 0.00005804% | 0.0023 | about 1 per 21,000 x 21,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 85x66x108 blocks; picked in 27,213 ce... |
| Ultimate Mediterranean Courtyard Villa Survival Base | codex:cbd_041 | 0.00005711% | 0.0022 | about 1 per 21,200 x 21,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 94x64x109 blocks; picked in 29,369 ce... |
| Ultimate Player Favorite Netherite Vault Survival Base | codex:cbd_077 | 0.00005665% | 0.0022 | about 1 per 21,300 x 21,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 89x69x108 blocks; picked in 26,975 ce... |
| Ultimate Player Favorite Iron Golem Guardian Survival Base | codex:cbd_082 | 0.00005661% | 0.0022 | about 1 per 21,300 x 21,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 110x107x85 blocks; picked in 27,250 c... |
| Ultimate Haunted Mansion Survival Base | codex:cbd_055 | 0.00005564% | 0.0022 | about 1 per 21,500 x 21,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 101x68x94 blocks; picked in 27,077 ce... |
| Ultimate Player Favorite Giant Bed Survival House | codex:cbd_084 | 0.00005244% | 0.0020 | about 1 per 22,100 x 22,100 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 101x75x96 blocks; picked in 26,752 ce... |
| Ultimate Player Favorite Totem Survival House | codex:cbd_086 | 0.00005033% | 0.0020 | about 1 per 22,600 x 22,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 107x98x101 blocks; picked in 29,490 c... |
| Ultimate Player Favorite Giant Warden Guardian Survival Base | codex:cbd_049 | 0.00004762% | 0.0019 | about 1 per 23,200 x 23,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 115x110x89 blocks; picked in 26,822 c... |
| Enderfall Portal Library Survival Base | codex:cbd_052 | 0.00004621% | 0.0018 | about 1 per 23,500 x 23,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 115x71x94 blocks; picked in 26,960 ce... |
| Ultimate Player Favorite Giant Shield Fortress Survival Base | codex:cbd_048 | 0.00004540% | 0.0018 | about 1 per 23,700 x 23,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 111x88x104 blocks; picked in 27,411 c... |
| Ultimate Woodland Mansion Survival Base | codex:cbd_040 | 0.00004500% | 0.0018 | about 1 per 23,900 x 23,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 101x80x108 blocks; picked in 26,701 c... |
| Ultimate Redstone Laboratory Survival Base | codex:cbd_057 | 0.00004374% | 0.0017 | about 1 per 24,200 x 24,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 104x72x111 blocks; picked in 27,002 c... |
| Ultimate Player Favorite Redstone Piston House Survival Base | codex:cbd_085 | 0.00004209% | 0.0016 | about 1 per 24,700 x 24,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 115x90x104 blocks; picked in 29,770 c... |
| Gropius House Lincoln Modernist Villa | codex:cbd_043 | 0.00004198% | 0.0016 | about 1 per 24,700 x 24,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 134x32x91 blocks; picked in 29,322 ce... |
| Ultimate Space Rocket Survival Base | codex:cbd_059 | 0.00003903% | 0.0015 | about 1 per 25,600 x 25,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 110x91x116 blocks; picked in 29,202 c... |
| Ultimate Player Favorite Enderman Portal House Survival Base | codex:cbd_072 | 0.00003869% | 0.0015 | about 1 per 25,700 x 25,700 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 114x113x106 blocks; picked in 27,175 ... |
| Ultimate Trial Chamber Survival Base | codex:cbd_032 | 0.00003800% | 0.0015 | about 1 per 26,000 x 26,000 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 103x72x115 blocks; picked in 26,805 c... |
| Neuschwanstein Fairy Castle | codex:cbd_037 | 0.00003469% | 0.0014 | about 1 per 27,200 x 27,200 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 112x91x120 blocks; picked in 26,864 c... |
| Ultimate Player Favorite Giant Creeper Castle House Survival Base | codex:cbd_074 | 0.00003149% | 0.0012 | about 1 per 28,500 x 28,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 113x92x118 blocks; picked in 27,004 c... |
| Luminara Skybridge Royal Castle | codex:cbd_033 | 0.00003064% | 0.0012 | about 1 per 28,900 x 28,900 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 134x86x101 blocks; picked in 27,170 c... |
| Titanic Ship Structure | codex:cbd_020 | 0.00002998% | 0.0012 | about 1 per 29,200 x 29,200 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 23x29x128 blocks; picked in 31,577 cells, placed in 4,684 (failed attempts: terrain/habitat 228,685... |
| Ultimate Player Favorite Giant Shulker Box Survival Base | codex:cbd_081 | 0.00002979% | 0.0012 | about 1 per 29,300 x 29,300 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 117x86x117 blocks; picked in 26,883 c... |
| Ultimate Pirate Ship Survival Base | codex:cbd_063 | 0.00002601% | 0.0010 | about 1 per 31,400 x 31,400 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 55x76x112 blocks; picked in 31,976 cells, placed in 4,064 (failed attempts: terrain/habitat 234,948... |
| Ultimate Nether Hub Portal Station Survival Base | codex:cbd_019 | 0.00002570% | 0.0010 | about 1 per 31,600 x 31,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 128x82x128 blocks; picked in 29,327 c... |
| Ultimate Modern Shopping Mall Survival Base | codex:cbd_017 | 0.00002378% | 0.0009 | about 1 per 32,800 x 32,800 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 128x72x128 blocks; picked in 27,032 c... |
| Ultimate Player Favorite Shipwreck Survival House | codex:cbd_054 | 0.00001912% | 0.0007 | about 1 per 36,600 x 36,600 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 67x63x124 blocks; picked in 31,955 cells, placed in 2,987 (failed attempts: terrain/habitat 239,939... |
| Thousand Sunny One Piece Ship Schematic | codex:cbd_030 | 0.00001721% | 0.0007 | about 1 per 38,600 x 38,600 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; 86x135x117 blocks; picked in 29,431 cells, placed in 2,689 (failed attempts: terrain/habitat 221,32... |
| Starfall Runekeep Castle | codex:cbd_034 | 0.00001721% | 0.0007 | about 1 per 38,600 x 38,600 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; 140x104x138 blocks; picked in 27,161 ... |
| Ultimate Ocean Monument Survival Base | codex:cbd_065 | 0.000008653% | 0.0003 | about 1 per 54,400 x 54,400 blocks | ocean: >= 4 of 5 samples below y62 and all <= y59; deep_ocean_basin: water column filled from y1; 89x65x104 blocks; picked in 26,784 cells, placed in ... |
| Resident Evil HIVE isolated annex | codex:cbd_101 | 0.000004288% | 0.0002 | about 1 per 77,300 x 77,300 blocks | embedded: dry, 5 samples within 8 blocks; sunk 3 blocks; 212x39x161 blocks; picked in 29,316 cells, placed in 670 (failed attempts: terrain/habitat 19... |
| Castlevania Castle Structure | codex:cbd_006 | 0.000001146% | 0.0000 | about 1 per 149,500 x 149,500 blocks | land: 5 samples (4 corners + centre) all >= y62 and within 5 blocks; origin = centre ground + 1 - surfaceAnchor; tall_castle_embed: may be lowered up ... |

### other (HorrorBiomes portal sanctuary, ChatGPT Codex) (1)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| Portal Sanctuary | sanct:portal | 0.001526% | 0.0596 | about 1 per 4,100 x 4,100 blocks | none (any terrain) |

### other (JasprApocalypse legacy ruin, ChatGPT Codex) (6)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| Ruined Town Lot | ruin:town | 0% | 0.0000 | never | - **0%: ruins are disabled live (JasprApocalypse does not place them)** |
| Roadside Motel | ruin:motel | 0% | 0.0000 | never | - **0%: ruins are disabled live (JasprApocalypse does not place them)** |
| Watchpost | ruin:watchpost | 0% | 0.0000 | never | - **0%: ruins are disabled live (JasprApocalypse does not place them)** |
| Roadside Shrine | ruin:shrine | 0% | 0.0000 | never | - **0%: ruins are disabled live (JasprApocalypse does not place them)** |
| Field Bunker | ruin:bunker | 0% | 0.0000 | never | - **0%: ruins are disabled live (JasprApocalypse does not place them)** |
| Salvage Yard | ruin:salvage | 0% | 0.0000 | never | - **0%: ruins are disabled live (JasprApocalypse does not place them)** |

### other (the Fold, ChatGPT Codex) (16)

| name | id | % of new chunks (anchor) | per 1,000 x 1,000 | spacing | restrictions / notes |
|---|---|---:|---:|---|---|
| The Fold room 00: Return Foyer | fold:0 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 01: Copy Office | fold:1 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 02: Lost Archive | fold:2 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 03: Drowned Bath | fold:3 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 04: Service Tunnel | fold:4 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 05: Waiting Room | fold:5 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 06: Pipe Gallery | fold:6 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 07: Night Classroom | fold:7 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 08: Crate Depot | fold:8 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 09: Green Laboratory | fold:9 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 10: Inverted Hall | fold:10 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 11: Empty Theatre | fold:11 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 12: Stair to Nowhere | fold:12 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 13: Black Chapel | fold:13 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 14: Last Hotel | fold:14 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
| The Fold room 15: No Sky Well | fold:15 | 0% | 0.0000 | never | - **0% of overworld chunks: generated only in the separate jaspr_backrooms world (already generated)** |
