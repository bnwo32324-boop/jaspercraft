# Structure inventory (reconciled)

Generated 2026-09-22 16:56 by the inventory/identities/valuables step. Data: `inventory.json` (381 records). Valuable blocks: `valuables.md` / `valuables.json`.

## Counts

| category | id prefix | records | classification | generating live | author |
|---|---|---|---|---|---|
| register set pieces | `reg:0..61` | 62 | structure | 61 yes / 1 no (The Interceptor disabled) | Claude Code |
| dungeon rooms | `dun:0..14` | 15 | structure | all 15 | Claude Code |
| expedition catalogue | `cat:<design-id>` | 234 | structure | all 234 eligible (admission-gated) | ChatGPT Codex |
| legacy apocalypse ruins | `ruin:<family>` | 6 | structure | none (ruins.enabled: false) | ChatGPT Codex |
| portal sanctuary | `sanct:portal` | 1 | structure | yes (1 per 4096x4096 blocks) | ChatGPT Codex (inferred, BIOMES_UPDATE 2026-09-07) |
| the Fold rooms | `fold:0..15` | 16 | structure | yes (jaspr_backrooms, already generated) | ChatGPT Codex (inferred, STRUCTURES_UPDATE 2026-09-07/08) |
| biome-detail motifs | `detail:<MOTIF>` | 47 | terrain decoration | none (density 0 everywhere) | ChatGPT Codex (inferred, BIOME_DETAILS_UPDATE 2026-09-08) |

Audit scope (structures): 62 + 15 + 234 + 6 + 1 + 16 = **334**. The 47 detail motifs are terrain decoration and are disabled live: listed for completeness, out of audit scope.

Other checks: 36 catalogue families; catalogue markers {"supply": 1199, "medical": 234, "armory": 233, "relic": 206, "vault": 181, "boss": 182, "mob": 1037, "door": 13}; chests measured register+dungeons = 387; catalogue chest markers = 2053; spawners (register+dungeons) = 465.

## Key findings

- Appendix A (317) maps 1:1 onto reg:0-61, dun:0-14, 234 cat:<id> and 6 ruin:<family> via the Testing Grounds index; number, name, group/family, tier, mode, size and plot origin agree for all 317 (0 mismatches). Appendix names are truncated to 36 characters; ids resolve them.
- Dungeons.D_NAME has 14 entries, not 15. The 15th Testing Grounds dungeon (#63 "Spawner Room") is the vanilla-style room from Dungeons.plain(); it is given the pseudo-id dun:14. /where cannot name it.
- Only the first 8 dungeon types (ward..checkpoint) are laid on two lattices (D_SALT_B != 0); sanatorium..highway use one lattice (handoff 8.4 says all are doubled).
- disabled-structures.txt is honoured only by the 11 builders in Megaliths.populate (AM, The Outpost, The Garrison, The Super Mall (+alias "The Store"), The Interceptor, Flight 226 (+alias "The Airliner"), The Field, The Hallowed Reach, Phobos Anomaly, The Gate, Delta Labs) and by catalogue designs (StructurePlanner, admit=true). The other 51 register entries (Landmarks, Anomalies, Relics, Metropolis, Wonders, Temples, Breach) and all 15 dungeon rooms are NOT gated: naming them in the file has no effect. Alias bug: "The Store" gates Megaliths.mall() (The Super Mall), not Anomalies.store3008 (reg:16 The Store).
- Currently disabled: reg:17 The Interceptor only. Legacy ruins disabled by JasprApocalypse config ruins.enabled: false. Biome-detail landmarks/litter disabled by density 0 in every biome-details.tsv row.
- Extras found beyond the 317: portal sanctuaries (HorrorGenerator.sanctuary, live), the Fold (16 rooms in jaspr_backrooms, LiminalGenerator, live world), biome-detail motifs (47 geometries, disabled), and the second Ruins SALVAGE layout (waterworks).
- Catalogue chest markers computed from the ported StructurePlanner.makeMarkers = 2053, equal to the chest blocks in the 234 Testing Grounds plots (handoff 8.7 "2,059" = 2053 + the 6 ruin caches). Register+dungeon chests measured 387 and spawners 465, matching the handoff.
- Source tree note: line numbers refer to the PC canonical tree AFTER the concurrent source-recovery step rewrote Megaliths/Dungeons/StructurePlanner/... at 2026-09-22 16:39-16:42 (sha256 in "sources"). Builder code of all register/dungeon structures is the same in the live 3.23.0 decompile (liveBuilderLine given).
- Lodge (reg:45) observation: the lift-shaft shell overwrites the cellar props (bookshelves/anvils) and appears to cap the cellar-to-shaft opening; Bombfall (reg:37): TNT placed on powered redstone blocks inside the bomb (possible detonation at population).

## Reconciliation with Appendix A (317 entries)

- Mapped: 317 / 317. Missing in code: none.
- Testing Grounds numbers: #1-62 register (grounds order is by group, not by register index), #63-77 dungeons, #78-311 catalogue (one row per family, sorted by tier), #312-317 ruins.
- Prior automated verdicts (not visual): {"defective": 39, "passable": 227, "good": 40, "severely incomplete": 11}.
- In code but not in Appendix A: sanct:portal, fold:0..15, detail:* (47), and Ruins SALVAGE variant 1 (waterworks, a different layout under ruin:salvage).

### Register (reg:k = Megaliths.C_NAME index)

| # | id | name | group | mode | builder (PC file:line) | declared XxYxZ | measured | verdict | live |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `reg:51` | Raccoon Precinct | Landmarks | surface | Landmarks.precinct @Landmarks.java:383 | 21x14x17 | 21x19x17 | defective | yes |
| 2 | `reg:57` | Raccoon Street | Landmarks | surface | Landmarks.street @Landmarks.java:444 | 25x10x13 | 25x16x13 | passable | yes |
| 3 | `reg:47` | Spencer Manor | Landmarks | surface | Landmarks.manor @Landmarks.java:322 | 21x14x17 | 21x20x17 | passable | yes |
| 4 | `reg:56` | The Brass Foundry | Landmarks | surface | Landmarks.foundry @Landmarks.java:140 | 19x20x15 | 19x21x15 | defective | yes |
| 5 | `reg:44` | The Impossible Stair | Landmarks | surface | Landmarks.stair @Landmarks.java:770 | 21x22x21 | 21x19x21 | defective | yes |
| 6 | `reg:59` | The Lost Blocks | Landmarks | surface | Landmarks.blocks @Landmarks.java:647 | 25x20x15 | 25x55x15 | good | yes |
| 7 | `reg:61` | The Lost Metro | Landmarks | surface | Landmarks.metro @Landmarks.java:706 | 23x12x13 | 23x18x13 | defective | yes |
| 8 | `reg:58` | The Lost Tower | Landmarks | surface | Landmarks.tower @Landmarks.java:587 | 15x26x15 | 16x51x16 | passable | yes |
| 9 | `reg:49` | The Neon Arcology | Landmarks | surface | Landmarks.arcology @Landmarks.java:202 | 15x30x15 | 15x38x15 | good | yes |
| 10 | `reg:60` | The Room | Landmarks | surface | Landmarks.theRoom @Landmarks.java:262 | 13x12x11 | 13x32x11 | good | yes |
| 11 | `reg:54` | The Village | Landmarks | surface | Landmarks.village @Landmarks.java:512 | 23x14x19 | 23x15x19 | passable | yes |
| 12 | `reg:53` | The Walking House | Landmarks | surface | Landmarks.house @Landmarks.java:67 | 17x18x15 | 13x26x11 | passable | yes |
| 13 | `reg:38` | AM | Megaliths | buried | Megaliths.amCore @Megaliths.java:756 | 33x26x29 | 33x45x29 | passable | yes |
| 14 | `reg:35` | Delta Labs | Megaliths | buried | Megaliths.doomThree @Megaliths.java:1652 | 39x11x29 | 39x67x29 | passable | yes |
| 15 | `reg:42` | Flight 226 | Megaliths | surface | Megaliths.airliner @Megaliths.java:1240 | 49x12x25 | 49x14x25 | severely incomplete | yes |
| 16 | `reg:40` | Phobos Anomaly | Megaliths | buried | Megaliths.doomOne @Megaliths.java:1502 | 37x12x31 | 37x68x31 | defective | yes |
| 17 | `reg:4` | The Field | Megaliths | buried | Megaliths.matrix @Megaliths.java:1311 | 45x64x45 | 45x77x45 | passable | yes |
| 18 | `reg:7` | The Garrison | Megaliths | surface | Megaliths.garrison @Megaliths.java:912 | 57x18x45 | 57x35x47 | defective | yes |
| 19 | `reg:39` | The Gate | Megaliths | buried | Megaliths.doomTwo @Megaliths.java:1574 | 35x16x33 | 35x56x33 | defective | yes |
| 20 | `reg:19` | The Hallowed Reach | Megaliths | surface | Megaliths.souls @Megaliths.java:1404 | 39x34x39 | 39x42x41 | defective | yes |
| 21 | `reg:17` | The Interceptor | Megaliths | buried | Megaliths.sewer @Megaliths.java:1148 | 61x16x61 | 61x45x61 | passable | **no (disabled)** |
| 22 | `reg:46` | The Outpost | Megaliths | surface | Megaliths.outpost @Megaliths.java:831 | 29x12x25 | 29x20x25 | defective | yes |
| 23 | `reg:33` | The Super Mall | Megaliths | surface | Megaliths.mall @Megaliths.java:1021 | 41x22x35 | 41x26x35 | passable | yes |
| 24 | `reg:10` | Site-19 | Anomalies | buried | Anomalies.site19 @Anomalies.java:67 | 53x20x45 | 53x44x45 | passable | yes |
| 25 | `reg:34` | The Corroded Place | Anomalies | buried | Anomalies.pocket @Anomalies.java:307 | 37x14x37 | 37x60x37 | defective | yes |
| 26 | `reg:31` | The Hive | Anomalies | buried | Anomalies.hive @Anomalies.java:466 | 43x16x35 | 43x42x35 | severely incomplete | yes |
| 27 | `reg:45` | The Lodge | Anomalies | surface | Anomalies.lodge @Anomalies.java:368 | 29x12x25 | 25x43x25 | defective | yes |
| 28 | `reg:55` | The Stairwell | Anomalies | buried | Anomalies.stairwell @Anomalies.java:247 | 15x56x15 | 15x69x15 | passable | yes |
| 29 | `reg:16` | The Store | Anomalies | buried | Anomalies.store3008 @Anomalies.java:170 | 45x14x45 | 45x53x45 | passable | yes |
| 30 | `reg:37` | Bombfall | Relics | surface | Relics.megaton @Relics.java:431 | 41x20x41 | 39x30x41 | severely incomplete | yes |
| 31 | `reg:8` | Ostrovets Station | Relics | surface | Relics.nuclear @Relics.java:91 | 57x34x49 | 57x48x49 | defective | yes |
| 32 | `reg:5` | The Buried City | Relics | buried | Relics.buriedCity @Relics.java:217 | 61x26x61 | 63x57x63 | defective | yes |
| 33 | `reg:32` | The Flatpack | Relics | surface | Relics.flatpack @Relics.java:758 | 41x18x37 | 41x22x37 | defective | yes |
| 34 | `reg:50` | The Golden Arches | Relics | surface | Relics.arches @Relics.java:870 | 27x14x23 | 27x21x23 | defective | yes |
| 35 | `reg:11` | The Strip | Relics | surface | Relics.strip @Relics.java:528 | 45x42x45 | 45x48x46 | defective | yes |
| 36 | `reg:3` | The Visitor | Relics | surface | Relics.alienShip @Relics.java:332 | 57x26x57 | 57x25x56 | defective | yes |
| 37 | `reg:13` | Vault 44 | Relics | buried | Relics.vault @Relics.java:647 | 45x18x37 | 45x48x38 | severely incomplete | yes |
| 38 | `reg:2` | Old Town | Metropolis | surface | Metropolis.oldTown @Metropolis.java:60 | 61x30x61 | 61x37x61 | defective | yes |
| 39 | `reg:48` | The Bell | Metropolis | surface | Metropolis.bell @Metropolis.java:450 | 25x12x21 | 25x19x21 | passable | yes |
| 40 | `reg:43` | The Grand Meridian | Metropolis | surface | Metropolis.hotel @Metropolis.java:358 | 33x34x25 | 33x45x25 | defective | yes |
| 41 | `reg:9` | The Interchange | Metropolis | surface | Metropolis.interchange @Metropolis.java:182 | 61x18x29 | 63x22x25 | passable | yes |
| 42 | `reg:41` | The Spire | Metropolis | surface | Metropolis.spire @Metropolis.java:273 | 21x62x21 | 21x76x21 | defective | yes |
| 43 | `reg:52` | The Sundowner | Metropolis | surface | Metropolis.motel @Metropolis.java:540 | 31x12x19 | 31x18x19 | defective | yes |
| 44 | `reg:0` | Columbia | Wonders | surface | Wonders.columbia @Wonders.java:463 | 51x40x51 | 48x48x48 | passable | yes |
| 45 | `reg:1` | Rapture | Wonders | underwater | Wonders.rapture @Wonders.java:374 | 55x24x55 | 41x23x41 | defective | yes |
| 46 | `reg:12` | The Burnt Chancel | Wonders | surface | Wonders.chancel @Wonders.java:202 | 43x34x35 | 43x31x35 | defective | yes |
| 47 | `reg:18` | The Lagoon | Wonders | surface | Wonders.lagoon @Wonders.java:590 | 45x22x41 | 48x35x41 | defective | yes |
| 48 | `reg:15` | The Long Choir | Wonders | surface | Wonders.choir @Wonders.java:120 | 43x34x35 | 43x49x36 | defective | yes |
| 49 | `reg:6` | The Mastaba | Wonders | surface | Wonders.mastaba @Wonders.java:278 | 49x30x49 | 49x45x49 | severely incomplete | yes |
| 50 | `reg:14` | Wonderland | Wonders | surface | Wonders.wonderland @Wonders.java:688 | 55x26x47 | 55x32x47 | passable | yes |
| 51 | `reg:36` | Survival Town | Temples | surface | Temples.survivalTown @Temples.java:289 | 45x16x37 | 45x28x37 | defective | yes |
| 52 | `reg:28` | The Dust Reliquary | Temples | surface | Temples.temple @Temples.java:136 | 33x22x45 | 33x41x45 | defective | yes |
| 53 | `reg:25` | The Ember Ziggurat | Temples | surface | Temples.temple @Temples.java:136 | 33x22x45 | 33x41x45 | defective | yes |
| 54 | `reg:30` | The Frostvault | Temples | surface | Temples.temple @Temples.java:136 | 33x22x45 | 33x41x45 | defective | yes |
| 55 | `reg:27` | The Green Sanctum | Temples | surface | Temples.temple @Temples.java:136 | 33x22x45 | 33x41x45 | defective | yes |
| 56 | `reg:29` | The Oxide Sepulchre | Temples | surface | Temples.temple @Temples.java:136 | 33x22x45 | 33x41x45 | defective | yes |
| 57 | `reg:26` | The Tidewell | Temples | surface | Temples.temple @Temples.java:136 | 33x22x45 | 33x41x45 | defective | yes |
| 58 | `reg:21` | The Kennels | Breach | buried | Breach.kennels @Breach.java:124 | 35x14x27 | 35x34x27 | severely incomplete | yes |
| 59 | `reg:20` | The Pit | Breach | buried | Breach.pit @Breach.java:58 | 37x20x37 | 39x44x39 | defective | yes |
| 60 | `reg:24` | The Signal | Breach | surface | Breach.signal @Breach.java:297 | 25x26x21 | 25x36x21 | defective | yes |
| 61 | `reg:23` | The Viewing Room | Breach | buried | Breach.viewing @Breach.java:237 | 31x16x27 | 31x48x27 | good | yes |
| 62 | `reg:22` | The Warren | Breach | buried | Breach.warren @Breach.java:179 | 41x12x41 | 41x54x41 | defective | yes |

### Dungeon rooms (dun:i = Dungeons.D_NAME index; dun:14 = plain spawner room)

| # | id | name | code name | mode | builder (Dungeons.java:line) | declared | measured | verdict |
|---|---|---|---|---|---|---|---|---|
| 63 | `dun:14` | Spawner Room | (plain vanilla dungeon) | buried | plain:138 | 7 or 9 (5-7 interior + walls)x5x7 or 9 | 9x5x7 | good |
| 64 | `dun:10` | The Cabin | Deadwood Cabin | surface | cabin:534 | 11x12x9 | 11x12x9 | passable |
| 65 | `dun:7` | The Checkpoint | Roadside Checkpoint | surface | checkpoint:396 | 13x9x9 | 13x9x9 | passable |
| 66 | `dun:13` | The Dead Highway | The Long Road | surface | highway:689 | 15x6x9 | 15x6x9 | good |
| 67 | `dun:11` | The Everburning Shrine | Kindled Shrine | surface | shrine:589 | 11x11x11 | 11x11x11 | severely incomplete |
| 68 | `dun:2` | The Flooded Cistern | Flooded Cistern | buried | cistern:246 | 13x8x13 | 13x8x13 | severely incomplete |
| 69 | `dun:1` | The Ossuary | Ossuary | buried | ossuary:218 | 11x7x11 | 11x7x11 | severely incomplete |
| 70 | `dun:12` | The Quarantine Blockhouse | Quarantine Block | surface | blockhouse:629 | 15x12x13 | 15x12x13 | defective |
| 71 | `dun:8` | The Sanatorium | Fogbound Sanatorium | surface | sanatorium:441 | 15x13x13 | 15x13x13 | defective |
| 72 | `dun:5` | The Spire | Signal Spire | surface | spire:327 | 7x20x7 | 7x20x7 | passable |
| 73 | `dun:9` | The Trial Ground | Trial Ground | surface | trial:489 | 13x8x13 | 13x8x13 | good |
| 74 | `dun:3` | The Vault | Reliquary Vault | buried | vault:274 | 11x6x11 | 11x6x11 | severely incomplete |
| 75 | `dun:0` | The Ward | Quarantine Ward | buried | ward:195 | 13x6x9 | 13x6x9 | severely incomplete |
| 76 | `dun:4` | The Warren | Sporecist Warren | buried | warren:301 | 15x6x11 | 15x6x11 | good |
| 77 | `dun:6` | The Wayside Chapel | Ashen Chapel | surface | chapel:359 | 11x12x15 | 11x12x15 | passable |

### Catalogue families (234 designs; per-design rows are in inventory.json)

| family | designs | tiers | modes | grounds #range | meaning | room codes (count) |
|---|---|---|---|---|---|---|
| metro | 12 | 1,2,3,4,5 | buried,surface,underwater | #78-#89 | abandoned metro / rail stations, platforms, concourses and freight loops | `R`197 `V`57 `J`19 `E`18 `Q`18 `p`17 `Z`11 `X`8 `I`8 `t`6 `Y`4 `i`3 `l`2 |
| monastery | 11 | 1,3,4,5 | surface | #90-#100 | monastic complexes: chapels, refectories/dormitories, cloisters, bell towers | `H`100 `a`50 `Y`44 `Q`35 `g`18 `Z`18 `k`14 `c`10 `h`9 `t`6 `v`5 `i`3 `O`2 `w`2 `l`2 |
| observatory | 10 | 1,2,3,4,5 | buried,surface | #101-#110 | survey stations and observatories (telescope domes, map rooms) | `v`60 `O`37 `L`22 `Z`20 `m`19 `Q`18 `t`13 `H`13 `Y`5 `p`4 `l`4 `V`4 `I`4 `J`3 `E`3 `a`3 `k`3 `P`3 `c`2 `X`2 |
| research | 10 | 2,3,4,5 | buried,surface,underwater | #111-#120 | research laboratories, containment and reactor sites | `L`99 `J`38 `E`29 `X`16 `I`15 `V`14 `t`9 `=`9 `P`5 `Z`5 `g`4 `c`4 `T`3 `w`2 `i`2 `p`2 `l`1 `m`1 `R`1 `H`1 |
| school | 10 | 2,3 | buried,surface | #121-#130 | schools and campuses (classrooms, halls, courtyards) | `s`159 `=`82 `S`60 `B`26 `o`24 `h`5 `K`4 |
| archive | 9 | 2,4,5 | buried,surface,underwater | #131-#139 | archives and libraries | `Z`104 `Q`23 `V`22 `l`22 `H`18 `X`16 `P`8 `E`7 `m`7 `I`7 `a`4 `Y`4 `w`3 `J`3 `t`1 `i`1 `c`1 `k`1 |
| castle | 9 | 3,4,5 | surface | #140-#148 | castles, keeps and battle towers | `=`123 `M`43 `K`42 `o`33 `h`29 `B`17 `T`12 |
| cistern | 9 | 1,3,4,5 | buried,surface,underwater | #149-#157 | cisterns, pump houses and waterworks | `I`101 `E`35 `V`14 `i`11 `J`9 `w`8 `c`8 `H`8 `L`4 `X`4 `Y`4 `R`2 `Z`2 `Q`2 `m`1 |
| house | 9 | 1,2 | surface | #158-#166 | houses, homesteads and hamlets | `h`122 `=`55 `o`10 `M`8 `2`4 |
| underwater_city | 9 | 3,4,5 | underwater | #167-#175 | submerged cities of sealed glass habitats joined by streets | `G`258 `=`73 `o`20 |
| auditorium | 8 | 1,2,3,4,5 | buried,surface | #176-#183 | theatres, auditoriums and broadcast halls | `l`40 `V`24 `J`15 `Z`13 `R`12 `p`11 `=`10 `P`9 `E`6 `X`5 `j`4 `g`4 `w`4 `h`2 `I`2 `H`2 `t`2 `c`2 `k`1 |
| city | 8 | 3,4,5 | surface | #184-#191 | city districts: multi-storey blocks round streets and courtyards | `=`126 `h`49 `2`29 `4`24 `3`17 `o`14 `B`11 `K`7 `s`4 `M`3 `6`3 `S`2 `8`2 |
| harbor | 8 | 1,2,3,4,5 | surface,underwater | #192-#199 | harbors, docks, customs houses and drydocks | `i`69 `I`35 `Q`11 `L`10 `E`9 `j`8 `X`7 `t`3 `c`3 `V`2 `Z`2 `m`1 `p`1 |
| mansion | 8 | 2,3,4 | surface | #200-#207 | mansions and manor houses | `M`110 `h`47 `o`37 `G`14 `3`14 `=`5 `B`5 `K`2 |
| backroom | 7 | 3,4 | buried,surface | #208-#214 | liminal 'backroom' complexes with Fold threshold gates (P rooms carry the door marker) | `P`118 `B`37 `h`23 `s`19 `o`9 `q`5 `S`4 `=`1 `A`1 |
| catacomb | 7 | 2,3,4,5 | buried,underwater | #215-#221 | catacombs, crypts and ossuaries | `Q`120 `H`22 `t`18 `c`9 `Z`7 `k`7 `I`5 `b`5 `E`2 `h`1 `i`1 `a`1 |
| dungeon | 7 | 4,5 | buried,surface | #222-#228 | vaulted dungeons and descending prisons/archives | `D`143 `q`66 `S`20 `B`11 `K`3 |
| telecom | 7 | 2,3 | surface | #229-#235 | telephone exchanges, relay masts and wire towns | `=`82 `B`45 `C`32 `h`8 `2`4 `3`2 |
| bunker | 6 | 2,3,4,5 | buried | #236-#241 | bunkers, shelters and containment vaults | `B`158 `G`13 `s`8 `=`5 `S`4 `o`2 |
| foundry | 6 | 2,4,5 | buried,surface | #242-#247 | foundries, forges and kiln works | `b`44 `t`34 `E`31 `Q`26 `=`9 `k`6 `Z`4 `Y`2 `H`2 `I`2 `X`2 `V`2 `c`2 `T`1 |
| infirmary | 6 | 1,3,4 | buried,surface | #248-#253 | field infirmaries and triage wards | `J`42 `X`17 `c`13 `a`10 `l`9 `Z`9 `t`6 `L`6 `w`6 `H`6 `V`5 `Q`5 `Y`4 `g`1 |
| plane | 6 | 2,3 | surface,underwater | #254-#259 | crashed aircraft (fuselage + wings) | `F`53 `W`50 `B`6 `s`3 `=`3 `C`1 |
| sewer | 6 | 3,4,5 | buried | #260-#265 | sewers and drain networks | `U`179 `B`13 `D`12 `q`11 |
| battle_tower | 5 | 4,5 | surface | #266-#270 | battle towers and fortified spires | `=`45 `q`17 `T`14 `B`11 `K`8 `A`6 `D`4 `o`1 |
| metropolis | 5 | 5 | surface | #271-#275 | large metropolis districts with towers, arches and avenues | `=`134 `h`50 `o`38 `4`31 `2`29 `3`29 `K`17 `6`13 `8`7 `s`5 `A`4 `M`1 `S`1 |
| mortuary | 5 | 1,2,3,4 | buried,surface | #276-#280 | mortuaries and embalming houses | `c`23 `Q`17 `J`11 `E`9 `h`5 `Z`4 `V`4 `g`4 `L`4 `Y`2 `t`2 `X`2 |
| signal | 5 | 1,2,3,4,5 | buried,surface | #281-#285 | signal stations and listening posts | `p`25 `=`12 `l`9 `E`6 `V`6 `Z`5 `L`5 `J`4 `T`4 `H`3 `X`3 `Q`2 `R`2 `t`2 `c`1 `h`1 |
| bathhouse | 4 | 1,2,3,4 | buried,surface,underwater | #286-#289 | bathhouses and boiler baths | `w`31 `I`15 `H`5 `Y`5 `E`3 `h`3 `i`3 `a`2 `J`2 `V`1 `k`1 |
| conservatory | 4 | 1,4 | buried,surface | #290-#293 | conservatories, seed houses and herbariums | `g`22 `L`14 `Z`6 `J`4 `Q`4 `h`3 `V`3 `a`2 `E`2 `Y`2 `c`2 `w`1 |
| excavation | 4 | 1,3,4,5 | buried,surface | #294-#297 | excavations, assay pits and mines (shored ore faces) | `e`25 `E`13 `Q`12 `Z`10 `m`9 `t`5 `b`5 `V`4 `k`2 `X`2 |
| prison | 4 | 1,4 | buried,surface | #298-#301 | prisons and cell blocks | `X`41 `E`17 `I`9 `V`5 `Z`3 `m`2 `e`1 `i`1 `t`1 |
| market | 3 | 1,2,3 | surface | #302-#304 | markets, caravanserais and salvage exchanges | `j`17 `t`10 `Y`4 `v`3 `m`3 `Z`3 `i`2 `a`2 |
| barracks | 2 | 2,4 | surface | #305-#306 | barracks and sally ports | `t`19 `Q`5 `X`3 `J`2 `E`2 `m`1 |
| hospital | 2 | 3,4 | surface | #307-#308 | hospitals and asylums | `s`30 `S`18 `=`10 `B`8 `o`6 |
| reliquary | 2 | 2,5 | surface | #309-#310 | reliquary spires and votive yards | `H`7 `a`6 `Q`6 `k`4 `Z`2 `h`1 `c`1 |
| refuge | 1 | 3 | buried | #311-#311 | civic refuges / underground assembly vaults | `V`13 `Y`8 `J`4 `l`3 `E`3 `Z`2 |

### Ruins

| # | id | name | builder (Ruins.java:line) | verdict | live |
|---|---|---|---|---|---|
| 312 | `ruin:town` | Ruined Town Lot | Ruins.town:510 | good | no |
| 313 | `ruin:motel` | Roadside Motel | Ruins.motel:546 | good | no |
| 314 | `ruin:watchpost` | Watchpost | Ruins.watchpost:573 | good | no |
| 315 | `ruin:shrine` | Roadside Shrine | Ruins.shrine:592 | good | no |
| 316 | `ruin:bunker` | Field Bunker | Ruins.bunker:636 | good | no |
| 317 | `ruin:salvage` | Salvage Yard | Ruins.salvage/waterworks:673 | good | no |

## Extras (not in the 317) and scope decisions

| generator | ids | classification | live | in audit scope? | reason |
|---|---|---|---|---|---|
| HorrorGenerator.sanctuary (HorrorGenerator.java) | `sanct:portal` | structure (a place players walk up to) | yes | **yes** | built by the chunk generator, player-facing; no valuables |
| LiminalGenerator (+LiminalWorld runtime) | `fold:0..15` | structure (16 enterable rooms) | yes (world exists) | **yes** | enterable rooms; valuables: 41 coal blocks + 1 enchanting table; changes only apply to a regenerated Fold |
| BiomeDetails + biome-details.tsv | `detail:*` (47 motifs) | terrain decoration (<=64-block props) | no (density 0) | no | disabled; palette whitelist already excludes ores/storage blocks |
| Dungeons.plain | `dun:14` | structure | yes | yes | counted in the 15 dungeon rooms |
| Ruins.waterworks | (variant of `ruin:salvage`) | structure | no | yes (with ruin:salvage) | second layout of the SALVAGE family, never captured |
| HorrorGenerator trees (tree/leaf) | - | terrain decoration | - | no | biome tree styles (dead, pine, red/blossom terracotta canopies, frost, cactus, scrub, ancient); out of scope |
| SurfaceOpenings | - | terrain | - | no | crevasse/cirque/caldera/cenote/collapse/crater landforms and ejecta rims; out of scope |
| Caves / CaveDecor / CaveSprings | - | terrain decoration | - | no | cave carving, cave-floor/ceiling decoration (mushrooms, cobweb, sea lantern/glowstone specks), springs; no valuable blocks; out of scope |
| OreVeins / DiamondRetrofit | - | terrain ore generation | - | no | places ores (14,15,16,21,56,73,129) by cave style; explicitly out of scope per CONVENTIONS |
| Floaters / WaterRepair | - | terrain repair | - | no | prunes floating terrain, repairs water; out of scope |
| OuterRealms | - | terrain decoration | - | no | Nether/End surface recolour + BiomeDetails.decorateNative (disabled, density 0); out of scope |
| Terrain landmark column (biomes.tsv) | - | terrain shaping | - | no | reactor/anomaly depressions, trench, mine terracing; no blocks built |
| Containment | - | not a builder | - | no | re-dresses mobs spawned in anomaly/boss sites; places no blocks |
| DungeonRetrofit | - | driver | - | no | re-runs Dungeons.build(retrofit=true) on old chunks (same builders) |
| JasprGraves Headstone | - | gameplay marker | - | no | mossy cobble wall + skull on player death; not world generation |
| JasprDisasters Impacts | - | event terrain damage | - | no | meteor craters (magma/netherrack/fire); not structures |
| JasprApocalypse SiegeTraversal / JasprInvasions | - | temporary mob blocks | - | no | zombie/invader cobblestone pillars (restored later); not structures |
| JasprApocalypse SentryTurret | - | player-built device | - | no | not world generation |

## Identity notes

Every register/dungeon record in inventory.json carries `identity` = {blurb, buildingType, references (+referenceBasis: code vs inferred), intendedDamage, keyFeatures, designerComment (verbatim header comment), sectionComments (verbatim in-builder comments), mainMaterialsMeasured (top block states of the Testing Grounds capture), featuresMeasured (chests, spawners, ladders, stairs, doors, glass, lights, water, lava, cobwebs), sourcePaletteLiteralIds, mobsInCode, lootTables}. Catalogue records carry familyMeaning, roomLegend and roomPalette per room code (effective palette after the Brush remap) and marker counts.

### Register quick reference (type / reference / main materials)

- **Raccoon Precinct** `reg:51` - columned civic building used as a police station. Ref: Resident Evil 2 (Raccoon Police Department) (code). Damage: windows boarded from the inside; fountain dry on one side. Materials: stone bricks 3646, quartz block 157, iron bars 116, chiseled quartz 70, quartz pillar 35, quartz slab 21.
- **Raccoon Street** `reg:57` - one wrecked city street block. Ref: Resident Evil 2/3 (Raccoon City street) (code). Damage: shop units gutted, walls broken to head height; two burnt-out cars (coal-block chassis, iron bodies); bus jammed across the far end; manhole left open. Materials: stone 1938, bricks 393, stone bricks 150, black concrete 123, glass pane 55, quartz block 50.
- **Spencer Manor** `reg:47` - colonial country mansion. Ref: Resident Evil (Spencer Mansion) (code). Damage: none intended. Materials: stone bricks 2499, dark oak planks 844, white stained terracotta 539, oak stairs 285, bricks 72, black stained terracotta 50.
- **The Brass Foundry** `reg:56` - steampunk brass foundry/workshop built round its chimney. Ref: generic foundry (inferred). Damage: never finished; brick blackened at the flue (nether brick); iron plate riveted over gaps. Materials: bricks 2323, iron bars 297, iron block 171, nether brick 48, magma block 14, redstone block 14.
- **The Impossible Stair** `reg:44` - rock-cut chamber of contradictory stairs. Ref: M. C. Escher, 'Relativity' (inferred). Damage: mossy/cracked stone bricks (age). Materials: stone bricks 1829, mossy stone bricks 285, cracked stone bricks 215, stone brick stairs 48, stone brick stairs 25, stone brick stairs 24.
- **The Lost Blocks** `reg:59` - three party-wall buildings on a street. Ref: generic ruined city block (inferred). Damage: random missing wall/glass blocks. Materials: gray concrete 4609, light gray concrete 1198, bricks 954, stone bricks 770, glass pane 97, black concrete 75.
- **The Lost Metro** `reg:61` - collapsed metro platform. Ref: generic abandoned metro (inferred). Damage: ceiling let go leaving a shaft of daylight; flooded at the low end. Materials: stone bricks 644, light gray stained terracotta 408, iron block 83, cracked stone bricks 59, rail 57, water 30.
- **The Lost Tower** `reg:58` - commercial tower with the top sheared off diagonally. Ref: generic ruined skyscraper (inferred). Damage: diagonal shear plane; slab floors holed (always at the core); curtain wall mostly gone; rebar out of broken edges; rubble skirt heaviest on the fall side. Materials: gray concrete 4937, glass 332, ladder 33, iron bars 26, cobblestone 6, chest 5.
- **The Neon Arcology** `reg:49` - neon cyberpunk megablock (arcology). Ref: cyberpunk arcology / megablock (inferred). Damage: top three storeys stripped back to the frame. Materials: gray concrete 2077, black concrete 2031, iron bars 173, cyan stained glass 120, magenta stained glass 120, sea lantern 72.
- **The Room** `reg:60` - one-bed flat that exists twice (a wrong copy far below). Ref: Silent Hill 4: The Room (inferred). Damage: the lower copy has 'every surface gone wrong' (red/black terracotta, cobwebs). Materials: stone bricks 858, white stained terracotta 383, brown stained terracotta 235, black stained terracotta 143, cracked stone bricks 143, spruce planks 143.
- **The Village** `reg:54` - hill hamlet around a green. Ref: Resident Evil 4 village (inferred). Damage: thatch blackened at the ridge. Materials: dirt 2622, grass 371, oak log 315, hay bale 290, white stained terracotta 96, gravel 61.
- **The Walking House** `reg:53` - house on four iron legs. Ref: Howl's Moving Castle / Baba Yaga's hut idiom (inferred). Damage: sagging plate underside; mismatched materials ('nothing matches anything'). Materials: iron block 233, planks 149, spruce planks 146, oak stairs 63, bricks 40, jungle planks 37.
- **AM** `reg:38` - cathedral-sized hateful supercomputer in a pit. Ref: 'I Have No Mouth, and I Must Scream' (AM) (code). Damage: cobwebs and remains of its prisoners. Materials: black concrete 4017, obsidian 1052, gray concrete 416, redstone block 312, iron bars 227, glowstone 42.
- **Delta Labs** `reg:35` - dark tech-base corridors with a portal ring. Ref: DOOM 3 (Delta Labs) (code). Damage: ceiling panels hanging off; one light in six working; floor around the ring 'gone wrong' (netherrack/fire). Materials: gray concrete 7769, black concrete 1135, light gray concrete 594, netherrack 126, obsidian 62, ladder 56.
- **Flight 226** `reg:42` - crashed airliner broken into three. Ref: generic airliner crash (inferred). Damage: furrow and burn (coal block in netherrack); tail on its side, nose face down with the flight deck crushed; baggage strewn. Materials: quartz block 952, netherrack 311, iron block 270, light gray concrete 107, coal block 91, light blue stained glass 42.
- **Phobos Anomaly** `reg:40` - tech base with nukage moat and a star-shaped trap room. Ref: DOOM (E1M8 'Phobos Anomaly') (code). Damage: none intended. Materials: gray concrete 2005, green concrete 1484, green stained terracotta 721, obsidian 319, lava (flowing) 319, red stained terracotta 67.
- **The Field** `reg:4` - vertical pod farm in a deep bored shaft. Ref: The Matrix (human pod fields) (code). Damage: none intended. Materials: black concrete 17596, gray concrete 2141, iron bars 1483, iron block 1451, obsidian 1428, quartz slab 960.
- **The Garrison** `reg:7` - large military base ('built to hold a province'). Ref: generic military airbase / missile silo (inferred). Damage: hangar doors left open; base abandoned; strongroom never opened. Materials: stone 16820, light gray concrete 2897, gray concrete 2429, quartz block 858, quartz slab 691, glass 647.
- **The Gate** `reg:39` - city block turning into hell. Ref: DOOM (gate to hell) (code). Damage: brick going to nether brick as it gets worse; heat coming up through cracks (lava/magma). Materials: bricks 2687, netherrack 1182, cobblestone 988, nether brick 676, brick slab 112, nether brick slab 112.
- **The Hallowed Reach** `reg:19` - cathedral on a plinth with a sunken ring behind the chancel. Ref: Dark Souls (fog gate, bonfire-like ring) (code). Damage: none intended. Materials: stone bricks 16997, mossy stone bricks 969, stone brick slab 551, chiseled stone bricks 522, quartz block 426, red carpet 234.
- **The Interceptor** `reg:17` - giant storm-sewer junction. Ref: interceptor sewer (engineering term) (code). Damage: standing water; growth (vines), grates, cobwebs. Materials: mossy stone bricks 1862, stone brick slab 599, water 589, iron bars 430, stone bricks 420, cracked stone bricks 400.
- **The Outpost** `reg:46` - abandoned forward operating base. Ref: generic military outpost (inferred). Damage: left in a hurry; armoury emptied from the inside. Materials: stone 5075, light gray stained terracotta 610, green stained terracotta 253, brown stained terracotta 198, stone bricks 172, light gray concrete 115.
- **The Super Mall** `reg:33` - three-storey shopping mall with the roof down the middle. Ref: zombie-film mall (Dawn of the Dead idiom) (inferred). Damage: roof down the middle; skylight on the ground floor; planters gone feral. Materials: stone bricks 10066, light gray stained terracotta 1776, white glazed terracotta 1281, stone brick slab 1240, light blue glazed terracotta 1144, pink glazed terracotta 1144.
- **Site-19** `reg:10` - containment wing with six occupied cells. Ref: SCP Foundation Site-19 (cells read as SCP-173, SCP-096, the acid tank of SCP-682, SCP-049's surgery, SCP-914's clockworks, SCP-999) (inferred). Damage: none intended. Materials: light gray concrete 39340, gold block 418, white concrete 407, obsidian 366, orange stained glass 366, gray concrete 231.
- **The Corroded Place** `reg:34` - rusted pocket dimension of identical rooms. Ref: SCP-106's pocket dimension (code). Damage: rusted through; pools of black stuff. Materials: brown stained terracotta 12658, hardened clay 492, light gray stained terracotta 400, black concrete 79, iron bars 74, ladder 45.
- **The Hive** `reg:31` - corporate underground laboratory sealed from inside. Ref: Resident Evil: the Hive (Umbrella lab; tram, laser corridor, Red Queen core) (inferred). Damage: sealed lab at the end ('the seal did not help'), cobwebs. Materials: light gray concrete 15574, white concrete 1403, black concrete 1265, gray concrete 502, quartz block 450, iron block 107.
- **The Lodge** `reg:45` - holiday cabin over a monster-cell facility. Ref: The Cabin in the Woods (inferred). Damage: none intended. Materials: gray concrete 2345, cobblestone 1432, white stained glass 923, light gray concrete 618, spruce planks 584, spruce log 210.
- **The Stairwell** `reg:55` - endless concrete stairwell. Ref: SCP-087 (inferred). Damage: lights fail as you descend; wall marks stop being writing. Materials: light gray concrete 3791, gray concrete 226, black concrete 184, cobweb 39, mob spawner 13, ladder 13.
- **The Store** `reg:16` - infinite furniture superstore. Ref: SCP-3008 ('a perfectly normal IKEA') (code). Damage: none intended. Materials: white concrete 2576, birch planks 2046, glowstone 1897, quartz slab 1641, planks 158, spruce planks 158.
- **Bombfall** `reg:37` - crater town built round an unexploded bomb. Ref: Fallout 3 (Megaton) (code). Damage: everything is scrap on stilts. Materials: oak fence 812, iron block 472, dirt 447, stone 385, brown stained terracotta 274, spruce planks 268.
- **Ostrovets Station** `reg:8` - nuclear power station. Ref: Chernobyl-type RBMK plant (Ostrovets = Belarusian NPP site) (inferred). Damage: core 'about eighty per cent' shut down; core-catcher shaft shows the core came through the floor. Materials: stone 17766, light gray concrete 5897, white concrete 2757, obsidian 1361, gray concrete 1096, iron block 957.
- **The Buried City** `reg:5` - buried downtown with rock where the sky was. Ref: generic buried city (inferred). Damage: top floors fallen in; some buildings 'came down'; growth and damp, standing water at the low end. Materials: stone 16009, andesite 3711, spruce planks 2384, mossy stone bricks 2273, cobblestone 1976, stone bricks 1418.
- **The Flatpack** `reg:32` - flat-pack furniture store. Ref: IKEA (code). Damage: half the roof on the floor. Materials: stone 10619, blue concrete 1560, light gray concrete 1442, birch planks 1034, quartz slab 819, iron bars 285.
- **The Golden Arches** `reg:50` - fast-food restaurant. Ref: McDonald's (golden arches) (code). Damage: none intended. Materials: stone 4347, light gray concrete 551, quartz slab 275, white concrete 167, bricks 136, red concrete 97.
- **The Strip** `reg:11` - walled casino strip. Ref: Fallout: New Vegas Strip (tower with a saucer ~ Lucky 38) (inferred). Damage: none: 'the power has not gone off'. Materials: stone 14175, gray concrete 1737, quartz slab 1544, light gray concrete 1505, black concrete 1338, red stained terracotta 855.
- **The Visitor** `reg:3` - crashed alien craft: seamless dark lens driven into the ground at the end of a burn furrow. Ref: generic crashed UFO (inferred). Damage: torn open on the furrow side (the way in); scorched furrow (coal block / netherrack / obsidian mix). Materials: dark prismarine 8347, purpur pillar 1815, obsidian 1275, prismarine bricks 1100, purpur block 975, green stained glass 515.
- **Vault 44** `reg:13` - sealed fallout-shelter vault in a hillside. Ref: Fallout vault (inferred). Damage: door rolled aside; east wing walled off in plate steel. Materials: light gray concrete 23771, white concrete 465, quartz slab 272, black concrete 246, iron block 89, yellow concrete 51.
- **Old Town** `reg:2` - post-blast downtown district (six blocks). Ref: generic post-apocalyptic downtown (inferred). Damage: mid-rise frames sheared off partway up, cladding off; cars burnt where they stopped; rubble drifts against kerbs; municipal building leaning ~4 degrees; flooded subway entrance. Materials: stone 24697, quartz slab 5833, gray concrete 4114, cracked stone bricks 1388, bricks 1266, quartz block 978.
- **The Bell** `reg:48` - fast-food restaurant with a bell tower. Ref: Taco Bell (code). Damage: none intended. Materials: stone 3675, light gray concrete 430, red stained terracotta 428, hardened clay 187, black concrete 103, purple concrete 66.
- **The Grand Meridian** `reg:43` - eight-floor grand hotel. Ref: generic grand hotel ('two hundred rooms and every door shut') (inferred). Damage: none intended. Materials: orange stained terracotta 12220, stone bricks 5775, light gray stained terracotta 1092, light blue stained glass 969, light gray concrete 570, water 390.
- **The Interchange** `reg:9` - elevated six-lane motorway interchange on piers. Ref: generic motorway interchange (inferred). Damage: middle span collapsed into the ditch with everything on it; wrecks (coal/iron). Materials: gray concrete 959, black concrete 641, light gray concrete 275, oak fence 161, iron block 114, quartz slab 36.
- **The Spire** `reg:41` - twenty-floor curtain-wall office tower. Ref: generic skyscraper ('something is waiting on the roof') (inferred). Damage: one floor in three taken over; gets worse higher up. Materials: gray concrete 16112, white concrete 1067, quartz slab 441, black concrete 238, iron bars 160, oak fence 80.
- **The Sundowner** `reg:52` - L-shaped roadside motel. Ref: generic roadside motel (inferred). Damage: room thirteen boarded from the inside. Materials: stone 3850, orange stained terracotta 542, light gray concrete 478, quartz block 243, quartz slab 235, light gray stained terracotta 80.
- **Columbia** `reg:0` - floating sky-city fragment: five white plinths over open air, joined by bridges. Ref: BioShock Infinite (Columbia) (inferred). Damage: none structural by design: pristine but abandoned (bunting still up, masts empty). Materials: stone 19382, sandstone 10076, quartz block 2716, gold block 1086, chiseled quartz 462, quartz pillar 188.
- **Rapture** `reg:1` - undersea art-deco city: towers on the sea bed joined by glass tubes around a plaza dome. Ref: BioShock (Rapture) (inferred). Damage: plaza dome half its glass gone (holes where (dx+dz+dy)%9==0); parts open to the water where the glass did not hold. Materials: quartz block 2247, light blue stained glass 1272, quartz slab 717, ladder 486, yellow stained glass 444, gold block 416.
- **The Burnt Chancel** `reg:12` - burnt-out church (same basilica as The Long Choir). Ref: paired with The Long Choir (same building, burnt) (code). Damage: roof gone, glass out; tower snapped off across the nave; soot black to the waist (coal-block floor, cobble); pews down. Materials: stone bricks 9367, cracked stone bricks 5074, mossy stone bricks 599, cobblestone 532, chiseled stone bricks 266, coal block 238.
- **The Lagoon** `reg:18` - water park. Ref: generic water park (inferred). Damage: wave pool full of standing green water. Materials: stone 10491, quartz block 1931, water 1025, lime stained glass 805, light gray concrete 613, blue stained glass 442.
- **The Long Choir** `reg:15` - intact cult church with its congregation. Ref: paired with The Burnt Chancel (same building, intact) (code). Damage: none intended. Materials: stone bricks 10492, black concrete 4540, obsidian 762, quartz slab 556, quartz block 522, red carpet 340.
- **The Mastaba** `reg:6` - modern stepped pyramid (49 across) with lit seams and a black glass cap. Ref: generic pyramid/mastaba ('clearly not old') (inferred). Damage: none intended. Materials: quartz block 13176, white concrete 4952, sandstone 2281, light gray concrete 1675, red sandstone 841, sea lantern 668.
- **Wonderland** `reg:14` - abandoned funfair. Ref: generic amusement park (inferred). Damage: coaster 'stops being a coaster about eighty feet up' (broken track). Materials: stone 18095, light gray concrete 2445, black concrete 811, spruce planks 632, quartz block 224, red concrete 212.
- **Survival Town** `reg:36` - 1950s nuclear-test mannequin town. Ref: Nevada test-site 'Survival Town' (Operation Doorstep mannequin towns) (code). Damage: the blast happened: burn beyond the wire; paint still yellow. Materials: stone 11252, grass 1253, spruce planks 1036, green stained terracotta 404, orange stained terracotta 404, light gray concrete 376.
- **The Dust Reliquary** `reg:28` - trap temple: one long run of jumps and a locked door (skin: dust reliquary (dust atmosphere)). Ref: Indiana Jones / Tomb Raider-style trap temple (inferred). Damage: intended hazards rather than damage: missing floor tiles, missing planks, loose ceiling. Materials: smooth sandstone 13359, magma block 1344, gold block 951, sandstone 225, sand 51, glowstone 29.
- **The Ember Ziggurat** `reg:25` - trap temple: one long run of jumps and a locked door (skin: ember (ash atmosphere)). Ref: Indiana Jones / Tomb Raider-style trap temple (inferred). Damage: intended hazards rather than damage: missing floor tiles, missing planks, loose ceiling. Materials: nether brick 13364, lava (flowing) 1344, obsidian 924, red nether brick 225, magma block 50, glowstone 29.
- **The Frostvault** `reg:30` - trap temple: one long run of jumps and a locked door (skin: frostvault (snow atmosphere)). Ref: Indiana Jones / Tomb Raider-style trap temple (inferred). Damage: intended hazards rather than damage: missing floor tiles, missing planks, loose ceiling. Materials: packed ice 13363, water 1344, quartz block 924, ice 225, snow block 50, sea lantern 29.
- **The Green Sanctum** `reg:27` - trap temple: one long run of jumps and a locked door (skin: green sanctum (spores atmosphere)). Ref: Indiana Jones / Tomb Raider-style trap temple (inferred). Damage: intended hazards rather than damage: missing floor tiles, missing planks, loose ceiling. Materials: mossy stone bricks 13360, magma block 1344, gold block 951, mossy cobblestone 225, vines 51, glowstone 29.
- **The Oxide Sepulchre** `reg:29` - trap temple: one long run of jumps and a locked door (skin: oxide sepulchre (rust atmosphere)). Ref: Indiana Jones / Tomb Raider-style trap temple (inferred). Damage: intended hazards rather than damage: missing floor tiles, missing planks, loose ceiling. Materials: hardened clay 13363, lava (flowing) 1344, iron block 924, orange stained terracotta 225, iron bars 50, sea lantern 29.
- **The Tidewell** `reg:26` - trap temple: one long run of jumps and a locked door (skin: tidewell (mist atmosphere)). Ref: Indiana Jones / Tomb Raider-style trap temple (inferred). Damage: intended hazards rather than damage: missing floor tiles, missing planks, loose ceiling. Materials: prismarine bricks 13360, water 1344, dark prismarine 924, mossy stone bricks 225, vines 51, sea lantern 29.
- **The Kennels** `reg:21` - kennel block containment. Ref: SCP-939 ('the noise of somebody you know') (inferred). Damage: cages mostly open. Materials: light gray concrete 10876, black concrete 666, gray concrete 217, iron bars 87, bone block 22, ladder 20.
- **The Pit** `reg:20` - lidded containment pit. Ref: SCP-682 (acid containment; 'they gave up on killing it') (inferred). Damage: catwalk to the island mostly gone. Materials: light gray concrete 5861, gray concrete 2856, obsidian 2149, iron block 771, iron bars 761, lava (flowing) 680.
- **The Signal** `reg:24` - hilltop radio mast with buried repeater. Ref: SCP-style radio anomaly (unspecified) (inferred). Damage: none intended. Materials: stone 3371, light gray concrete 840, iron bars 388, light gray stained terracotta 169, quartz slab 100, gray concrete 85.
- **The Viewing Room** `reg:23` - single observation cell. Ref: SCP-096 ('do not look at the photograph') (inferred). Damage: observation glass broken. Materials: white concrete 9491, light gray concrete 507, white stained glass 39, ladder 32, red stained terracotta 28, quartz block 18.
- **The Warren** `reg:22` - unlit tunnel-lattice containment. Ref: SCP-style containment (unspecified) (inferred). Damage: none intended. Materials: black concrete 14002, light gray concrete 1477, gray concrete 793, spruce planks 121, ladder 52, cobweb 23.

### Dungeon quick reference

- **Spawner Room** `dun:14` ((plain vanilla dungeon)) - vanilla-style spawner room. Damage: mossy cobblestone floor patches. Materials: cobblestone 123, mossy cobblestone 52, chest 2, mob spawner 1.
- **The Cabin** `dun:10` (Deadwood Cabin) - one-room log cabin with a cellar. Damage: stains that did not wash out. Materials: stone 370, oak log 173, oak stairs 99, planks 98, cobblestone 58.
- **The Checkpoint** `dun:7` (Roadside Checkpoint) - quarantine checkpoint. Damage: broken barricade. Materials: stone 351, light gray concrete 108, polished andesite 59, stone brick slab 45, gray concrete 27.
- **The Dead Highway** `dun:13` (The Long Road) - dead stretch of highway. Damage: broken tarmac, centre line mostly gone; burnt car shell (coal block) half on the verge; ash on the verge. Materials: stone 270, light gray concrete 49, black concrete 42, gravel 26, oak log 13.
- **The Everburning Shrine** `dun:11` (Kindled Shrine) - ruined chapel around an ever-burning fire. Damage: ruined: less of it standing the higher it goes. Materials: cobblestone 553, mossy cobblestone 25, white stained terracotta 21, cobblestone 12, netherrack 9.
- **The Flooded Cistern** `dun:2` (Flooded Cistern) - sunken reservoir. Damage: floor flooded. Materials: stone bricks 457, mossy stone bricks 193, water 106, stone brick slab 11, chest 3.
- **The Ossuary** `dun:1` (Ossuary) - underground ossuary. Damage: none. Materials: mossy stone bricks 190, stone bricks 83, bone block 80, glowstone 24, bone block 21.
- **The Quarantine Blockhouse** `dun:12` (Quarantine Block) - barred quarantine compound. Damage: chain fence sagging in places. Materials: stone 390, stone bricks 263, iron bars 184, light gray concrete 132, stone brick slab 72.
- **The Sanatorium** `dun:8` (Fogbound Sanatorium) - rusting sanatorium ward. Damage: tile stained orange/brown where damp got in; grated floors. Materials: cracked stone bricks 780, light gray stained terracotta 214, white stained terracotta 178, stone brick slab 152, brown stained terracotta 94.
- **The Spire** `dun:5` (Signal Spire) - surface watchtower. Damage: cracked stone bricks. Materials: stone bricks 444, cracked stone bricks 81, stone brick slab 49, ladder 15, chest 2.
- **The Trial Ground** `dun:9` (Trial Ground) - clearing with machines and hooks (slaughter-ground horror). Damage: none. Materials: dirt 435, planks 107, oak stairs 36, cobblestone 20, oak slab 8.
- **The Vault** `dun:3` (Reliquary Vault) - small underground strongroom. Damage: cracked stone bricks. Materials: stone bricks 296, cracked stone bricks 106, iron bars 45, mob spawner 4, glowstone 4.
- **The Ward** `dun:0` (Quarantine Ward) - underground cell ward. Damage: cracked stone bricks. Materials: stone bricks 324, cracked stone bricks 106, iron bars 36, mob spawner 4, glowstone 3.
- **The Warren** `dun:4` (Sporecist Warren) - overgrown fungal hollow. Damage: overgrowth, heavy webbing. Materials: cobblestone 193, mossy cobblestone 177, cobweb 34, brown mushroom block 7, glowstone 7.
- **The Wayside Chapel** `dun:6` (Ashen Chapel) - burnt-out wayside chapel. Damage: collapsed roof, a few ribs standing; broken windows. Materials: cobblestone 537, stone bricks 293, mossy cobblestone 67, cracked stone bricks 45, mossy stone bricks 44.

### Catalogue room palette (effective, after Brush remap)

- `2` 2-storey block: gray terracotta 159:7 walls, stone-brick floors; table, stair chair, bookshelves
- `3` 3-storey block: as 2 (3 storeys)
- `4` 4-storey block: as 2 (4 storeys)
- `6` 6-storey block: as 2 (6 storeys)
- `8` 8-storey block: as 2 (8 storeys)
- `=` street/bridge: open street/bridge: stone-brick deck with yellow terracotta 159:4 centre lines
- `A` arch: open arch: stone-brick columns and chiselled lintel
- `B` bunker: gray terracotta 159:7 walls, stone-brick floor, cyan terracotta 159:9 roof; furnace, ANVIL 145, polished-andesite (was iron) column, lever
- `C` cell mast: open cell mast: iron bars legs, polished-andesite (was iron) cross-arm, iron trapdoor
- `D` vaulted dungeon: stone bricks 98, vault ribs of stone-brick stairs; torch plinth
- `E` engine/pump hall: polished andesite 1:6 (was iron) walls; furnace boiler banks topped with andesite (was iron), ANVILS 145, cauldrons, iron-bar gantry; brick flue stacks, iron-trapdoor roof vents
- `F` aircraft fuselage: polished andesite 1:6 (was iron) fuselage walls/roof with glass window bands, glazed cockpit, swept tail; oak-stair seats, lever/button
- `G` sealed glass habitat: glass 20 walls, prismarine 168 floor; dark prismarine, sea lantern, cauldron
- `H` monastic chapel (2): stone bricks 98 (2 storeys); spruce-stair choir stalls, bookshelf lectern, dropper, chiselled stone-brick ribs with stairs; nether-brick pitched roof
- `I` enclosed cistern: prismarine 168 walls/floor; two lined water tanks, iron-bar pipes, dark prismarine, andesite (was iron) valve cabinet with lever; prismarine vaulted roof
- `J` infirmary: quartz 155 walls/floor; examination couches (quartz slabs), carpet, glass-pane screens, cauldron basin, red terracotta; andesite (was iron) roof plant
- `K` keep (4): stone bricks 98 (mossy/cracked) walls, stone-brick floor, crenellations; torch on a mossy/chiselled plinth, slab
- `L` research laboratory (2): quartz 155 walls/floor (2); quartz-glazed specimen columns (quartz, lime/light-blue glass, glass, iron trapdoor), polished-andesite (was iron) worktops, cauldron sink, lever; andesite (was iron) roof plant
- `M` mansion (2 storeys): oak planks 5:0 walls, spruce-plank floor 5:1, pitched roof 5:1 + oak stairs; bookshelves, furnace, crafting table
- `O` observatory (3): quartz 155 walls (3 storeys); bookshelves/quartz library, daylight sensor, crafting table; telescope (cobble wall, andesite (was iron), hopper, blue glass), lever; quartz dome with glass slot
- `P` backroom gate (Fold threshold door marker): gray terracotta 159:7 walls, quartz floor; yellow terracotta 159:4 columns, sea lantern; door marker = purpur 201 + end rods 198 (Fold threshold)
- `Q` ossuary: stone bricks 98 walls, bone-block 216 floor; bone-block ossuary niches with slabs, stone-brick-stair ribs; bone-block vaulted roof
- `R` rail platform: brick 45 walls; broken track (spruce planks 5:1 + rail 66), yellow terracotta platform edge, stone-brick stairs, fence + button, polished-andesite (was iron) end block
- `S` school hall (2): brick 45 walls, quartz 155 floor (2 storeys); desks, bookshelves, chalkboard
- `T` battle tower (7): stone bricks 98 walls (7 storeys), crenellations; torch plinth, slab
- `U` sewer: mossy cobblestone 48 walls, stone-brick floor; water channel, iron bars, cauldron
- `V` vaulted station concourse (2): quartz 155 walls/floor (2 storeys); quartz ticket counters with glass panes, lever, route-map terracotta 159:11/159:4, quartz-stair barrel ribs, quartz dome roof
- `W` wing: open wing: polished andesite 1:6 (was iron) skin, slab spar, engine pods with furnaces
- `X` barred cell block: stone bricks 98; four iron-bar holding bays with stone-slab ledges and cauldrons; crenellated parapet
- `Y` refectory/dormitory (2): oak planks 5 (2); refectory table (fence + plate, stair seats) and furnace below; bunks (spruce slabs), spruce logs, hay; spruce roof with spruce stairs
- `Z` archive (2): brick 45 walls (2); tall bookshelves, upper iron-bar manuscript cage, desk (fence + plate + stairs); brick parapet
- `a` open cloister: open cloister: stone bricks; herb beds (dirt + flowers), chiselled stone-brick columns, slab eaves
- `b` kiln: nether brick 112 walls/floor; cold kiln mouths (nether brick, furnace, iron bars), netherrack slag; nether-brick flues
- `c` mortuary: bone block 216 walls/floor; mortuary slabs (quartz + smooth slabs), cauldron sinks, stone-brick-stair ribs; bone-block roof
- `e` shored excavation: open excavation: stone bricks; spruce-log shoring, hoist (iron bars), granite 1:1 ore face with IRON ORE 15, crafting table
- `g` conservatory (2): glass 20 walls, prismarine floor (2); planting beds (dirt + flowers), glass panes, cauldron; pitched glass roof
- `h` pitched house: oak planks 5:0 walls with spruce-log 17:1 corners, spruce-plank floor 5:1, pitched spruce-plank roof with oak stairs; furnace, crafting table, table (fence + pressure plate), stair chairs
- `i` harbor workshop: polished andesite 1:6 (was iron) walls; timber hull (spruce slabs, spruce stairs), net racks (iron bars), spruce logs, mooring post (cobble wall); spruce-plank roof steps
- `j` market colonnade: open market colonnade (no walls), oak-plank floor; four stalls (spruce-plank counters, hay, fence posts), brown/red wool awnings at two heights
- `k` reliquary spire (3): stone bricks 98 (3); sealed relic cases (quartz pillar + stained glass + bone block / yellow terracotta (was gold) on the top floor), slabs; chiselled stone-brick spire with cobble-wall finial
- `l` auditorium: gray terracotta 159:7 walls, spruce-plank floor 5:1; spruce-stair raked seating, spruce-plank stage, red terracotta curtains; stepped stone-slab roof
- `m` map/survey room: oak-plank 5 walls, spruce-plank floor; inlaid mosaic (terracotta 159:11/159:5), drafting tables, daylight sensor, bookshelves; spruce-slab roof
- `o` courtyard: open courtyard: dirt + poppy
- `p` signal exchange (2): brick 45 walls (2); black terracotta relay cabinets, andesite (was iron) panels with levers and buttons, iron-bar cable trays, note blocks upstairs; andesite (was iron) roof plant
- `q` crypt: stone bricks 98, vault ribs; mossy plinth with torch
- `s` classroom: brick 45 walls, quartz 155 floor; desks (fence+plate), stair chairs, bookshelf column, black terracotta chalkboard
- `t` sally-port barracks (2): stone bricks 98 (2); iron-bar guard racks, spruce-slab bunks, hay, andesite (was iron) block with ANVIL 145; crenellated parapet
- `v` wind arcade: open wind arcade: stone bricks; slab benches, tall chiselled stone-brick arches with stair caps
- `w` bathhouse: prismarine 168 walls, quartz floor; plunge pools (water), quartz-stair rims, iron-bar shower rails, cauldron; prismarine vault
- all rooms: all rooms: cracked/mossy stone-brick 98:2 stepped piers under every occupied column, glass windows every 4 blocks on surface/non-buried walls, torch per storey, two-wide stone-brick-stair staircases with oak-fence handrails between storeys, stone-brick approach path with stair steps and fence rails, chest markers (54) on stone-brick pedestals; underwater roofs are glass; random scar gaps (air) in surface walls and roof corners = intended ruin damage

## Sources (sha256 at scan time)

- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Landmarks.java` 927e6cff4ae9912c (mtime 2026-09-21 19:23:14)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Megaliths.java` d73ce507aa32b8f4 (mtime 2026-09-22 16:40:26)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Anomalies.java` e1d6f8498230c692 (mtime 2026-09-21 19:23:14)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Relics.java` ab83ec0573427c8a (mtime 2026-09-21 19:23:14)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Metropolis.java` ea3f08dbb3bad762 (mtime 2026-09-21 19:23:13)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Wonders.java` 487a910f93c40b7a (mtime 2026-09-21 19:23:13)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Temples.java` a2a5340601492302 (mtime 2026-09-21 20:34:59)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Breach.java` 15126b0c83461fa9 (mtime 2026-09-21 20:34:59)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/Dungeons.java` 48211853d0df0449 (mtime 2026-09-22 16:40:26)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/StructureArchitecture.java` 4e061283603e19d5 (mtime 2026-09-12 14:36:25)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/StructurePlanner.java` ca95b1bd69527608 (mtime 2026-09-22 16:42:40)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/StructureCatalog.java` 85a25b6cb8ad5a21 (mtime 2026-09-08 02:17:53)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/WorldgenExpansion.java` a41d582f8930429f (mtime 2026-09-22 16:40:04)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/HorrorGenerator.java` b88c22aa02663923 (mtime 2026-09-18 20:48:53)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/LiminalGenerator.java` 508049d006affac3 (mtime 2026-09-12 14:36:25)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/BiomeDetails.java` 8bbe6108bed95891 (mtime 2026-09-08 09:52:43)
- `server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/DisabledStructures.java` 5fb950c2d9729ced (mtime 2026-09-22 16:50:41)
- `server/custom-plugins/JasprHorrorBiomes/resources/structures/catalog-v1.tsv` e190b3a58813d174 (mtime 2026-09-08 09:51:27)
- `server/custom-plugins/JasprHorrorBiomes/resources/biome-details.tsv` 0cca9d76b66b13b7 (mtime 2026-09-08 09:52:43)
- `server/custom-plugins/JasprApocalypse/src/chat/jaspr/apocalypse/Ruins.java` eedcfc77e715371b (mtime 2026-09-06 20:36:07)
- `site/jaspercraft-grounds-world.js` 7dd712341d1a29c4 (mtime 2026-09-22 11:45:10)
- `site/jaspercraft-grounds-index.js` 70987e0de688bbb9 (mtime 2026-09-22 11:45:10)
