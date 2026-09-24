# Valuable blocks in structure generators

Generated 2026-09-22 16:56. Policy: CONVENTIONS.md "Block valuables policy". Data: `valuables.json` (155 placement records).

## Totals measured in the Testing Grounds (one instance per structure) + the Fold (from code)

| block | count | where |
|---|---|---|
| iron block (42) | 7259 | dungeon, register |
| gold block (41) | 4410 | register |
| redstone block (152) | 1275 | register |
| anvil (145) | 1017 | catalogue, register |
| coal block (173) | 802 | dungeon, fold, register |
| iron ore (15) | 26 | catalogue |
| lapis block (22) | 25 | register |
| brewing stand (117) | 16 | register |
| beacon (138) | 1 | register |
| enchanting table (116) | 1 | fold |

No diamond (57), emerald (133) blocks and no ores except iron ore (catalogue excavation rooms) are placed as blocks by any structure generator. The catalogue brush already remaps iron/gold/diamond blocks (see below). Ruins, portal sanctuaries and biome details place no valuables.

## Proposed replacements (policy)

- `IRON_IND` -> 43:8 smooth stone double slab: policy: iron block -> smooth stone double slab (industrial light-grey metal look)
- `IRON_CLEAN` -> 251:0 white concrete: policy: iron block -> white concrete (clean white-lab context)
- `GOLD` -> 251:4 yellow concrete: policy: gold block -> yellow concrete (same hue, bright gilding)
- `GOLD_DULL` -> 159:4 yellow terracotta: policy option: duller gold/brass reads better here (matches the catalogue Brush remap 41->159:4)
- `RED` -> 251:14 red concrete: policy: redstone block -> red concrete (decorative, powers nothing)
- `COAL` -> 251:15 black concrete: policy: coal block -> black concrete (black terracotta 159:15 is a duller alternative for ground scorch)
- `LAPIS` -> 251:11 blue concrete: policy: lapis block -> blue concrete
- `BEACON` -> 169:0 sea lantern: policy: beacon -> sea lantern (glassy light; the beacon has no pyramid and only acts as a light)
- `ORE_GRANITE` -> 1:1 granite: policy: ore used as decor -> host stone; the host of this ore face is the granite 1:1 prop it sits in
- `KEEP` -> - keep (report only): policy: functional workstation, judged case by case, not blanket-replaced

## Functional cases

- **Relics.java:506-507 (reg:37 Bombfall)**: redstone blocks with TNT directly on top in the bomb underside: powered TNT. Proposal: 251:14 red concrete (TNT becomes inert); verify whether the TNT already primes at population (BlockTNT.onPlace).
- **Dungeons.java:521-522 (dun:9 Trial Ground)**: iron block with a redstone torch on top: the torch is the power source; nothing is powered. Proposal: decorative -> 43:8.
- **Anomalies.java:116-123 (reg:10 Site-19 clockworks)**: gold walls with pistons/sticky pistons and a lever nearby; gold is not a power source and pistons do not need it. Proposal: decorative -> 159:4.
- **no redstone lamps / doors / rails are powered by any structure redstone block**: checked every builder that places 152 for lamps (123/124), pistons, rails, doors, dispensers, TNT, note blocks. Proposal: all other 152 -> 251:14.

## Workstations (report only, not blanket-replaced)

- **anvil 145** - register: Foundry (workshop), Strip top floor, Grand Meridian suite, Rapture tower top, Wonderland haunted house (1 each; Lodge cellar overwritten); catalogue: 1012 anvils (B/E/t rooms, lines 170/251/405). Judgement: functional, not a mineral exploit (anvils cannot be turned back into iron in 1.12); keep the single on-theme props; the catalogue repeats one anvil per bunker/barracks storey and two per engine hall (1012 total) - owner decision whether to thin them.
- **brewing stand 117** - Site-19 surgery (1), Hive labs (12), Vault 44 clinic (3). Judgement: on-theme lab/clinic fittings; keep.
- **enchanting table 116** - Fold room 13 Black Chapel altar (1, LiminalGenerator.java:141). Judgement: single thematic altar; keep unless the owner wants no free enchanting table.

## Per structure (measured) with the proposal

| id | name | measured valuables | proposal |
|---|---|---|---|
| `reg:57` | Raccoon Street | iron block [42:0] 50, coal block [173:0] 16 | 173->251:15, 42->43:8 |
| `reg:56` | The Brass Foundry | iron block [42:0] 171, redstone block [152:0] 14, anvil [145:0] 1 | 145->keep, 152->251:14, 42->43:8 |
| `reg:61` | The Lost Metro | iron block [42:0] 83 | 42->43:8 |
| `reg:53` | The Walking House | iron block [42:0] 233 | 42->43:8 |
| `reg:38` | AM | redstone block [152:0] 312 | 152->251:14 |
| `reg:35` | Delta Labs | iron block [42:0] 23 | 42->43:8 |
| `reg:42` | Flight 226 | iron block [42:0] 270, coal block [173:0] 91 | 173->251:15, 42->43:8 |
| `reg:40` | Phobos Anomaly | redstone block [152:0] 18, iron block [42:0] 12 | 152->251:14, 42->43:8 |
| `reg:4` | The Field | iron block [42:0] 1451, redstone block [152:0] 712 | 152->251:14, 42->43:8 |
| `reg:7` | The Garrison | iron block [42:0] 228 | 42->43:8 |
| `reg:39` | The Gate | redstone block [152:0] 38 | 152->251:14 |
| `reg:19` | The Hallowed Reach | gold block [41:0] 125 | 41->251:4 |
| `reg:46` | The Outpost | iron block [42:0] 27 | 42->43:8 |
| `reg:10` | Site-19 | gold block [41:0] 418, iron block [42:0] 68, brewing stand [117:0] 1 | 117->keep, 41->159:4, 42->43:8 |
| `reg:31` | The Hive | iron block [42:0] 107, redstone block [152:0] 51, brewing stand [117:0] 12 | 117->keep, 152->251:14, 42->251:0 |
| `reg:37` | Bombfall | iron block [42:0] 472, iron block [42:8] 213, coal block [173:0] 106, redstone block [152:0] 15 | 152->251:14, 173->251:15, 42->43:8 |
| `reg:8` | Ostrovets Station | iron block [42:0] 957, redstone block [152:0] 56, lapis block [22:0] 25 | 152->251:14, 22->251:11, 42->43:8 |
| `reg:32` | The Flatpack | iron block [42:0] 14 | 42->43:8 |
| `reg:50` | The Golden Arches | gold block [41:0] 19, iron block [42:0] 11 | 41->251:4, 42->43:8 |
| `reg:11` | The Strip | gold block [41:0] 80, iron block [42:0] 77, anvil [145:0] 1 | 145->keep, 41->251:4, 42->43:8 |
| `reg:3` | The Visitor | coal block [173:0] 155, beacon [138:0] 1 | 138->169:0, 173->251:15 |
| `reg:13` | Vault 44 | iron block [42:0] 89, brewing stand [117:0] 3 | 117->keep, 42->43:8 |
| `reg:2` | Old Town | iron block [42:0] 167, coal block [173:0] 116, gold block [41:0] 12 | 173->251:15, 41->251:4, 42->43:8 |
| `reg:48` | The Bell | iron block [42:0] 5 | 42->43:8 |
| `reg:43` | The Grand Meridian | anvil [145:0] 1 | 145->keep |
| `reg:9` | The Interchange | iron block [42:0] 114, coal block [173:0] 23 | 173->251:15, 42->43:8 |
| `reg:41` | The Spire | iron block [42:0] 24 | 42->43:8 |
| `reg:52` | The Sundowner | iron block [42:0] 5 | 42->43:8 |
| `reg:0` | Columbia | gold block [41:0] 1086, iron block [42:0] 30 | 41->251:4, 42->43:8 |
| `reg:1` | Rapture | gold block [41:0] 416, iron block [42:3] 246, anvil [145:0] 1 | 145->keep, 41->251:4, 42->43:8 |
| `reg:12` | The Burnt Chancel | coal block [173:0] 238 | 173->251:15 |
| `reg:18` | The Lagoon | iron block [42:0] 135 | 42->43:8 |
| `reg:15` | The Long Choir | gold block [41:0] 78, redstone block [152:0] 50 | 152->251:14, 41->251:4 |
| `reg:6` | The Mastaba | gold block [41:0] 99 | 41->251:4 |
| `reg:14` | Wonderland | iron block [42:0] 141, gold block [41:0] 67, anvil [145:0] 1 | 145->keep, 41->251:4, 42->43:8 |
| `reg:36` | Survival Town | iron block [42:0] 78, coal block [173:0] 8 | 173->251:15, 42->43:8 |
| `reg:28` | The Dust Reliquary | gold block [41:0] 951 | 41->159:4, 41->251:4 |
| `reg:25` | The Ember Ziggurat | gold block [41:0] 27 | 41->251:4 |
| `reg:30` | The Frostvault | gold block [41:0] 27 | 41->251:4 |
| `reg:27` | The Green Sanctum | gold block [41:0] 951 | 41->159:4, 41->251:4 |
| `reg:29` | The Oxide Sepulchre | iron block [42:0] 924, gold block [41:0] 27 | 41->251:4, 42->43:8 |
| `reg:26` | The Tidewell | gold block [41:0] 27 | 41->251:4 |
| `reg:20` | The Pit | iron block [42:0] 771 | 42->43:8 |
| `reg:24` | The Signal | iron block [42:0] 54, redstone block [152:0] 9 | 152->251:14, 42->43:8 |
| `reg:23` | The Viewing Room | iron block [42:0] 5 | 42->43:8 |
| `dun:13` | The Dead Highway | coal block [173:0] 8 | 173->251:15 |
| `dun:9` | The Trial Ground | iron block [42:0] 4 | 42->43:8 |
| `fold:11` | The Fold room 11: Empty Theatre | coal block [173:0] 6 | 173->251:15 |
| `fold:13` | The Fold room 13: Black Chapel | enchanting table [116:0] 1 | 116->keep |
| `fold:15` | The Fold room 15: No Sky Well | coal block [173:0] 35 | 173->251:15 |

Catalogue: 121 designs contain anvils (1012 total), 5 contain iron ore (26 blocks: cat:excavators_shoring_camp 3, cat:excavation_sunken_assay 9, cat:coalbreath_survey_mine 9, cat:greed_assay_catacombs 4, cat:quarry_pay_window 1).

## Every placement site (file:line, PC tree)

| vid | file:line | structure(s) | block | purpose | usage | proposal | notes |
|---|---|---|---|---|---|---|---|
| V001 | Landmarks.java:77 | reg:53 | iron block (42) | iron-plated legs, iron feet and sagging plate underside | decorative | 43:8 smooth stone double slab |  |
| V002 | Landmarks.java:78 | reg:53 | iron block (42) | iron-plated legs, iron feet and sagging plate underside | decorative | 43:8 smooth stone double slab | conditional id expression: y % 3 == 0 ? 42 : 101 |
| V003 | Landmarks.java:80 | reg:53 | iron block (42) | iron-plated legs, iron feet and sagging plate underside | decorative | 43:8 smooth stone double slab |  |
| V004 | Landmarks.java:86 | reg:53 | iron block (42) | iron-plated legs, iron feet and sagging plate underside | decorative | 43:8 smooth stone double slab | conditional id expression: dx == 8 && dz == 7 ? 0 : r.nextInt(6) == 0 ? 101 : 42 |
| V005 | Landmarks.java:161 | reg:56 | iron block (42) | iron plate riveted over brick, outside pipework, the great cog body | decorative | 43:8 smooth stone double slab | conditional id expression: wall ? 42 : r.nextInt(7) == 0 ? 0 : 101 |
| V006 | Landmarks.java:169 | reg:56 | iron block (42) | iron plate riveted over brick, outside pipework, the great cog body | decorative | 43:8 smooth stone double slab | conditional id expression: pipe ? 42 : r.nextInt(5) == 0 ? 42 : 45 |
| V007 | Landmarks.java:179 | reg:56 | iron block (42) | iron plate riveted over brick, outside pipework, the great cog body | decorative | 43:8 smooth stone double slab | conditional id expression: dy % 2 == 0 ? 42 : 152 |
| V008 | Landmarks.java:179 | reg:56 | redstone block (152) | teeth of the great cog (alternate with iron) | decorative | 251:14 red concrete | conditional id expression: dy % 2 == 0 ? 42 : 152 |
| V009 | Landmarks.java:180 | reg:56 | iron block (42) | iron plate riveted over brick, outside pipework, the great cog body | decorative | 43:8 smooth stone double slab |  |
| V010 | Landmarks.java:182 | reg:56 | anvil (145) | workshop anvil | functional workstation | keep (report only) |  |
| V011 | Landmarks.java:482 | reg:57 | coal block (173) | burnt-out car chassis | decorative | 251:15 black concrete |  |
| V012 | Landmarks.java:485 | reg:57 | iron block (42) | burnt car bodies (glass at dy==2) | decorative | 43:8 smooth stone double slab | conditional id expression: dy == 2 ? 20 : 42 |
| V013 | Landmarks.java:735 | reg:61 | iron block (42) | train body (glass windows at dy==3) | decorative | 43:8 smooth stone double slab | conditional id expression: side ? (dy == 3 ? 20 : 42) : 42 |
| V014 | Megaliths.java:771 | reg:38 | redstone block (152) | "live redstone" nave inlays, machine-bank cores and the redstone face | decorative | 251:14 red concrete |  |
| V015 | Megaliths.java:772 | reg:38 | redstone block (152) | "live redstone" nave inlays, machine-bank cores and the redstone face | decorative | 251:14 red concrete |  |
| V016 | Megaliths.java:778 | reg:38 | redstone block (152) | "live redstone" nave inlays, machine-bank cores and the redstone face | decorative | 251:14 red concrete |  |
| V017 | Megaliths.java:779 | reg:38 | redstone block (152) | "live redstone" nave inlays, machine-bank cores and the redstone face | decorative | 251:14 red concrete |  |
| V018 | Megaliths.java:799 | reg:38 | redstone block (152) | "live redstone" nave inlays, machine-bank cores and the redstone face | decorative | 251:14 red concrete |  |
| V019 | Megaliths.java:882 | reg:46 | iron block (42) | armoury racks (under iron bars) and the truck on blocks | decorative | 43:8 smooth stone double slab |  |
| V020 | Megaliths.java:891 | reg:46 | iron block (42) | armoury racks (under iron bars) and the truck on blocks | decorative | 43:8 smooth stone double slab |  |
| V021 | Megaliths.java:948 | reg:7 | iron block (42) | hangar roof ribs, the silo missile ("the bird") body, strongroom racks | decorative | 43:8 smooth stone double slab |  |
| V022 | Megaliths.java:979 | reg:7 | iron block (42) | hangar roof ribs, the silo missile ("the bird") body, strongroom racks | decorative | 43:8 smooth stone double slab |  |
| V023 | Megaliths.java:1000 | reg:7 | iron block (42) | hangar roof ribs, the silo missile ("the bird") body, strongroom racks | decorative | 43:8 smooth stone double slab |  |
| V024 | Megaliths.java:1001 | reg:7 | iron block (42) | hangar roof ribs, the silo missile ("the bird") body, strongroom racks | decorative | 43:8 smooth stone double slab |  |
| V025 | Megaliths.java:1237 | reg:42 | iron block (42) | fuselage floor strip inside each barrel section | decorative | 43:8 smooth stone double slab |  |
| V026 | Megaliths.java:1249 | reg:42 | coal block (173) | scorch in the crash furrow (1 in 5 of netherrack) | decorative | 251:15 black concrete | conditional id expression: r.nextInt(5) == 0 ? 173 : 87 |
| V027 | Megaliths.java:1268 | reg:42 | iron block (42) | wing skins | decorative | 43:8 smooth stone double slab |  |
| V028 | Megaliths.java:1269 | reg:42 | iron block (42) | wing skins | decorative | 43:8 smooth stone double slab |  |
| V029 | Megaliths.java:1329 | reg:4 | redstone block (152) | "live" core of the obsidian spine | decorative | 251:14 red concrete | conditional id expression: d > 2.2 ? 49 : 152 |
| V030 | Megaliths.java:1344 | reg:4 | iron block (42) | pod scaffold spines (two rings) | decorative | 43:8 smooth stone double slab |  |
| V031 | Megaliths.java:1452 | reg:19 | gold block (41) | the gold altar block in the chancel ("a gold thing ... still giving off light") | decorative | 251:4 yellow concrete |  |
| V032 | Megaliths.java:1521 | reg:40 | redstone block (152) | computer-wall lights and the star-room floor centre | decorative | 251:14 red concrete |  |
| V033 | Megaliths.java:1532 | reg:40 | iron block (42) | consoles in the three keyed rooms | decorative | 43:8 smooth stone double slab |  |
| V034 | Megaliths.java:1545 | reg:40 | redstone block (152) | computer-wall lights and the star-room floor centre | decorative | 251:14 red concrete |  |
| V035 | Megaliths.java:1610 | reg:39 | redstone block (152) | pentagram lines cut into the courtyard floor | decorative | 251:14 red concrete |  |
| V036 | Megaliths.java:1663 | reg:35 | iron block (42) | pilasters/bay frames along the spine corridor | decorative | 43:8 smooth stone double slab |  |
| V037 | Megaliths.java:1664 | reg:35 | iron block (42) | pilasters/bay frames along the spine corridor | decorative | 43:8 smooth stone double slab |  |
| V038 | Anomalies.java:80 | reg:10 | iron block (42) | hall pilasters between observation windows; armoury racks | decorative | 43:8 smooth stone double slab |  |
| V039 | Anomalies.java:81 | reg:10 | iron block (42) | hall pilasters between observation windows; armoury racks | decorative | 43:8 smooth stone double slab |  |
| V040 | Anomalies.java:112 | reg:10 | brewing stand (117) | surgery cell instruments (brewing stand) | functional workstation | keep (report only) |  |
| V041 | Anomalies.java:117 | reg:10 | gold block (41) | brass clockworks cell dividing wall | decorative | 159:4 yellow terracotta |  |
| V042 | Anomalies.java:146 | reg:10 | iron block (42) | hall pilasters between observation windows; armoury racks | decorative | 43:8 smooth stone double slab |  |
| V043 | Anomalies.java:408 | reg:45 | anvil (145) | cellar belongings (anvils alternating with bookshelves) - OVERWRITTEN by the shaft shell, 0 survive | functional workstation | keep (report only) | overwritten later in the same builder: no block survives (Testing Grounds capture: 0) / conditional id expression: i % 2 == 0 ? 47 : 145 |
| V044 | Anomalies.java:417 | reg:45 | iron block (42) | lift-shaft bottom plate - OVERWRITTEN by the gallery floor, 0 survive | decorative | 251:0 white concrete | overwritten later in the same builder: no block survives (Testing Grounds capture: 0) |
| V045 | Anomalies.java:477 | reg:31 | iron block (42) | tram-tunnel ribs, white-corridor pilasters, column-room pillars, the steel seal of the last lab | decorative | 251:0 white concrete |  |
| V046 | Anomalies.java:490 | reg:31 | iron block (42) | tram-tunnel ribs, white-corridor pilasters, column-room pillars, the steel seal of the last lab | decorative | 251:0 white concrete |  |
| V047 | Anomalies.java:491 | reg:31 | iron block (42) | tram-tunnel ribs, white-corridor pilasters, column-room pillars, the steel seal of the last lab | decorative | 251:0 white concrete |  |
| V048 | Anomalies.java:497 | reg:31 | redstone block (152) | laser-hall emitters and the lit core of the column room ("Red Queen") | decorative | 251:14 red concrete |  |
| V049 | Anomalies.java:498 | reg:31 | redstone block (152) | laser-hall emitters and the lit core of the column room ("Red Queen") | decorative | 251:14 red concrete |  |
| V050 | Anomalies.java:510 | reg:31 | redstone block (152) | laser-hall emitters and the lit core of the column room ("Red Queen") | decorative | 251:14 red concrete |  |
| V051 | Anomalies.java:514 | reg:31 | iron block (42) | tram-tunnel ribs, white-corridor pilasters, column-room pillars, the steel seal of the last lab | decorative | 251:0 white concrete |  |
| V052 | Anomalies.java:536 | reg:31 | brewing stand (117) | lab benches (brewing stands) | functional workstation | keep (report only) |  |
| V053 | Anomalies.java:542 | reg:31 | iron block (42) | tram-tunnel ribs, white-corridor pilasters, column-room pillars, the steel seal of the last lab | decorative | 251:0 white concrete |  |
| V054 | Relics.java:85 | reg:8 | iron block (42) | eight 1x6 support legs round the pond under each of the two cooling towers | decorative | 43:8 smooth stone double slab |  |
| V055 | Relics.java:113 | reg:8 | iron block (42) | containment drum bands (every 5th course), the crane, three turbines | decorative | 43:8 smooth stone double slab | conditional id expression: dy % 5 == 0 ? 42 : 251 |
| V056 | Relics.java:130 | reg:8 | lapis block (22) | fuel rods in the core grid | decorative | 251:11 blue concrete | conditional id expression: rod ? 22 : 152 |
| V057 | Relics.java:130 | reg:8 | redstone block (152) | core channels between the rods | decorative | 251:14 red concrete | conditional id expression: rod ? 22 : 152 |
| V058 | Relics.java:135 | reg:8 | iron block (42) | containment drum bands (every 5th course), the crane, three turbines | decorative | 43:8 smooth stone double slab |  |
| V059 | Relics.java:148 | reg:8 | iron block (42) | containment drum bands (every 5th course), the crane, three turbines | decorative | 43:8 smooth stone double slab |  |
| V060 | Relics.java:342 | reg:3 | coal block (173) | scorch in the crash furrow (1 in 6) | decorative | 251:15 black concrete | conditional id expression: r.nextInt(6) == 0 ? 173 : (r.nextInt(3) == 0 ? 87 : 49) |
| V061 | Relics.java:411 | reg:3 | beacon (138) | light at the centre of the sealed sphere | decorative | 169:0 sea lantern |  |
| V062 | Relics.java:442 | reg:37 | coal block (173) | scorched crater floor (1 in 5 of stone) | decorative | 251:15 black concrete | conditional id expression: d > 14 ? 3 : (r.nextInt(5) == 0 ? 173 : 1) |
| V063 | Relics.java:451 | reg:37 | iron block (42) | bomb casing, aircraft-door gate, bomb inner shell | decorative | 43:8 smooth stone double slab | conditional id expression: dx > 4 ? 155 : 42 |
| V064 | Relics.java:488 | reg:37 | iron block (42) | bomb casing, aircraft-door gate, bomb inner shell | decorative | 43:8 smooth stone double slab |  |
| V065 | Relics.java:503 | reg:37 | iron block (42) | bomb casing, aircraft-door gate, bomb inner shell | decorative | 43:8 smooth stone double slab |  |
| V066 | Relics.java:506 | reg:37 | redstone block (152) | rows inside the bomb underside WITH TNT DIRECTLY ON TOP (powers the TNT) | functional (powers TNT) | 251:14 red concrete | FUNCTIONAL: each redstone-block row has TNT placed directly on it (Relics.java:507); the block powers the TNT. Replacing with red concrete makes the TNT inert (a player must ignite it). Note: because the TNT is placed AFTER the powered block, vanilla BlockTNT.onPlace probably primes it during population - verify in the capture harness; the trap may already have fired in live copies. |
| V067 | Relics.java:556 | reg:11 | iron block (42) | boulevard lamp posts and the tower core column | decorative | 43:8 smooth stone double slab |  |
| V068 | Relics.java:595 | reg:11 | iron block (42) | boulevard lamp posts and the tower core column | decorative | 43:8 smooth stone double slab |  |
| V069 | Relics.java:619 | reg:11 | gold block (41) | floor of the secret top floor | decorative | 251:4 yellow concrete |  |
| V070 | Relics.java:627 | reg:11 | anvil (145) | prop in the secret top floor | functional workstation | keep (report only) |  |
| V071 | Relics.java:702 | reg:13 | brewing stand (117) | clinic (brewing stands) | functional workstation | keep (report only) |  |
| V072 | Relics.java:711 | reg:13 | iron block (42) | plate-steel wall sealing the east wing | decorative | 43:8 smooth stone double slab |  |
| V073 | Relics.java:779 | reg:32 | iron block (42) | the two legs of the store sign | decorative | 43:8 smooth stone double slab |  |
| V074 | Relics.java:780 | reg:32 | iron block (42) | the two legs of the store sign | decorative | 43:8 smooth stone double slab |  |
| V075 | Relics.java:914 | reg:50 | iron block (42) | sign pole; walk-in freezer door | decorative | 43:8 smooth stone double slab |  |
| V076 | Relics.java:919 | reg:50 | gold block (41) | the two golden arcs | decorative | 251:4 yellow concrete |  |
| V077 | Relics.java:920 | reg:50 | gold block (41) | the two golden arcs | decorative | 251:4 yellow concrete |  |
| V078 | Relics.java:922 | reg:50 | gold block (41) | the two golden arcs | decorative | 251:4 yellow concrete |  |
| V079 | Relics.java:956 | reg:50 | iron block (42) | sign pole; walk-in freezer door | decorative | 43:8 smooth stone double slab |  |
| V080 | Metropolis.java:112 | reg:2 | coal block (173) | burnt cars | decorative | 251:15 black concrete |  |
| V081 | Metropolis.java:153 | reg:2 | iron block (42) | bank-vault box and round vault door | decorative | 43:8 smooth stone double slab |  |
| V082 | Metropolis.java:157 | reg:2 | iron block (42) | bank-vault box and round vault door | decorative | 43:8 smooth stone double slab |  |
| V083 | Metropolis.java:161 | reg:2 | gold block (41) | gold bullion stacks inside the vault | decorative | 251:4 yellow concrete |  |
| V084 | Metropolis.java:208 | reg:9 | iron block (42) | sign gantries; wreck bodies | decorative | 43:8 smooth stone double slab |  |
| V085 | Metropolis.java:209 | reg:9 | iron block (42) | sign gantries; wreck bodies | decorative | 43:8 smooth stone double slab |  |
| V086 | Metropolis.java:210 | reg:9 | iron block (42) | sign gantries; wreck bodies | decorative | 43:8 smooth stone double slab |  |
| V087 | Metropolis.java:231 | reg:9 | iron block (42) | sign gantries; wreck bodies | decorative | 43:8 smooth stone double slab | conditional id expression: r.nextInt(3) == 0 ? 173 : 42 |
| V088 | Metropolis.java:231 | reg:9 | coal block (173) | burnt wrecks (1 in 3) | decorative | 251:15 black concrete | conditional id expression: r.nextInt(3) == 0 ? 173 : 42 |
| V089 | Metropolis.java:323 | reg:41 | iron block (42) | four roof-canopy columns | decorative | 43:8 smooth stone double slab |  |
| V090 | Metropolis.java:431 | reg:43 | anvil (145) | prop in the suite behind the bar | functional workstation | keep (report only) |  |
| V091 | Metropolis.java:516 | reg:48 | iron block (42) | the bell in the arch | decorative | 43:8 smooth stone double slab |  |
| V092 | Metropolis.java:588 | reg:52 | iron block (42) | VACANCY sign pole | decorative | 43:8 smooth stone double slab |  |
| V093 | Wonders.java:66 | reg:12 | coal block (173) | soot-black nave floor of the burnt church (burnt=true only; the intact choir gets red carpet) | decorative | 251:15 black concrete | conditional id expression: burnt ? 173 : 171 |
| V094 | Wonders.java:139 | reg:15 | gold block (41) | gold altar plinth and the undercroft dais | decorative | 251:4 yellow concrete |  |
| V095 | Wonders.java:144 | reg:15 | redstone block (152) | "black sun" checker (obsidian/redstone) and the undercroft path | decorative | 251:14 red concrete | conditional id expression: ((dx + dy) & 1) == 0 ? 49 : 152 |
| V096 | Wonders.java:174 | reg:15 | redstone block (152) | "black sun" checker (obsidian/redstone) and the undercroft path | decorative | 251:14 red concrete |  |
| V097 | Wonders.java:181 | reg:15 | gold block (41) | gold altar plinth and the undercroft dais | decorative | 251:4 yellow concrete |  |
| V098 | Wonders.java:212 | reg:12 | coal block (173) | soot/char debris and the fallen tower | decorative | 251:15 black concrete |  |
| V099 | Wonders.java:231 | reg:12 | coal block (173) | soot/char debris and the fallen tower | decorative | 251:15 black concrete |  |
| V100 | Wonders.java:311 | reg:6 | gold block (41) | gold cap core, hall fittings and the foundation-hall altar | decorative | 251:4 yellow concrete |  |
| V101 | Wonders.java:347 | reg:6 | gold block (41) | gold cap core, hall fittings and the foundation-hall altar | decorative | 251:4 yellow concrete |  |
| V102 | Wonders.java:350 | reg:6 | gold block (41) | gold cap core, hall fittings and the foundation-hall altar | decorative | 251:4 yellow concrete |  |
| V103 | Wonders.java:385 | reg:1 | iron block (42) | plaza-dome and tube ribs (data 3) | decorative | 43:8 smooth stone double slab | conditional id expression: (dx + dz + dy) % 9 == 0 ? 0 : (((dx + dy) & 3) == 0 ? 42 : 95) |
| V104 | Wonders.java:396 | reg:1 | gold block (41) | deco gold bands, statue bases, tower-top floor | decorative | 251:4 yellow concrete |  |
| V105 | Wonders.java:412 | reg:1 | gold block (41) | deco gold bands, statue bases, tower-top floor | decorative | 251:4 yellow concrete | conditional id expression: band ? 41 : (win ? 95 : 155) |
| V106 | Wonders.java:431 | reg:1 | iron block (42) | plaza-dome and tube ribs (data 3) | decorative | 43:8 smooth stone double slab | conditional id expression: d > 1.7 ? ((k & 3) == 0 ? 42 : 95) : 0 |
| V107 | Wonders.java:441 | reg:1 | gold block (41) | deco gold bands, statue bases, tower-top floor | decorative | 251:4 yellow concrete |  |
| V108 | Wonders.java:444 | reg:1 | anvil (145) | prop in the sealed tower top | functional workstation | keep (report only) |  |
| V109 | Wonders.java:509 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete |  |
| V110 | Wonders.java:515 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete | conditional id expression: (dy & 1) == 0 ? 155 : 41 |
| V111 | Wonders.java:518 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete |  |
| V112 | Wonders.java:519 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete |  |
| V113 | Wonders.java:523 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete |  |
| V114 | Wonders.java:544 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete |  |
| V115 | Wonders.java:550 | reg:0 | iron block (42) | two mooring masts | decorative | 43:8 smooth stone double slab |  |
| V116 | Wonders.java:562 | reg:0 | gold block (41) | gilded drum, dome bands, lantern, statues, hall roof | decorative | 251:4 yellow concrete |  |
| V117 | Wonders.java:663 | reg:18 | iron block (42) | three pump machines in the pump house | decorative | 43:8 smooth stone double slab |  |
| V118 | Wonders.java:703 | reg:14 | iron block (42) | ferris-wheel rim and tower | decorative | 43:8 smooth stone double slab |  |
| V119 | Wonders.java:711 | reg:14 | iron block (42) | ferris-wheel rim and tower | decorative | 43:8 smooth stone double slab |  |
| V120 | Wonders.java:717 | reg:14 | gold block (41) | carousel centre pole and horse poles | decorative | 251:4 yellow concrete |  |
| V121 | Wonders.java:721 | reg:14 | gold block (41) | carousel centre pole and horse poles | decorative | 251:4 yellow concrete |  |
| V122 | Wonders.java:782 | reg:14 | anvil (145) | prop in the haunted-house back room | functional workstation | keep (report only) |  |
| V123 | Temples.java:247 | reg:25, reg:26, reg:27, reg:28, reg:29, reg:30 | gold block (41) | the golden idol (3x3x3) in the sanctum of all six temples | decorative | 251:4 yellow concrete |  |
| V124 | Temples.java:357 | reg:36 | iron block (42) | the car in the garage; camera-tower legs | decorative | 43:8 smooth stone double slab |  |
| V125 | Temples.java:390 | reg:36 | coal block (173) | bus wheels; burn patches in the grass-path scorch (1 in 3) | decorative | 251:15 black concrete |  |
| V126 | Temples.java:390 | reg:36 | coal block (173) | bus wheels; burn patches in the grass-path scorch (1 in 3) | decorative | 251:15 black concrete |  |
| V127 | Temples.java:409 | reg:36 | iron block (42) | the car in the garage; camera-tower legs | decorative | 43:8 smooth stone double slab |  |
| V128 | Temples.java:409 | reg:36 | iron block (42) | the car in the garage; camera-tower legs | decorative | 43:8 smooth stone double slab |  |
| V129 | Temples.java:410 | reg:36 | iron block (42) | the car in the garage; camera-tower legs | decorative | 43:8 smooth stone double slab |  |
| V130 | Temples.java:410 | reg:36 | iron block (42) | the car in the garage; camera-tower legs | decorative | 43:8 smooth stone double slab |  |
| V131 | Temples.java:430 | reg:36 | coal block (173) | bus wheels; burn patches in the grass-path scorch (1 in 3) | decorative | 251:15 black concrete | conditional id expression: r.nextInt(3) == 0 ? 173 : 208 |
| V132 | Breach.java:83 | reg:20 | iron block (42) | rim gantries and the catwalk to the island | decorative | 43:8 smooth stone double slab |  |
| V133 | Breach.java:98 | reg:20 | iron block (42) | rim gantries and the catwalk to the island | decorative | 43:8 smooth stone double slab |  |
| V134 | Breach.java:249 | reg:23 | iron block (42) | the stand the photograph sits on | decorative | 43:8 smooth stone double slab |  |
| V135 | Breach.java:316 | reg:24 | iron block (42) | four mast legs | decorative | 43:8 smooth stone double slab |  |
| V136 | Breach.java:317 | reg:24 | iron block (42) | four mast legs | decorative | 43:8 smooth stone double slab |  |
| V137 | Breach.java:318 | reg:24 | iron block (42) | four mast legs | decorative | 43:8 smooth stone double slab |  |
| V138 | Breach.java:319 | reg:24 | iron block (42) | four mast legs | decorative | 43:8 smooth stone double slab |  |
| V139 | Breach.java:355 | reg:24 | redstone block (152) | racks in the buried repeater room | decorative | 251:14 red concrete |  |
| V140 | Dungeons.java:521 | dun:9 | iron block (42) | the four "machines" (a redstone torch stands on each; the torch is itself the power source, nothing is powered) | decorative | 43:8 smooth stone double slab |  |
| V141 | Dungeons.java:726 | dun:13 | coal block (173) | burnt-out car shell | decorative | 251:15 black concrete |  |
| V142 | Landmarks.java:88 | reg:53 | iron block (42) | one slot of the deliberately mismatched upper-storey wall palette ("nothing matches anything") | decorative | 43:8 smooth stone double slab | replace the 42 entry in the array |
| V143 | Anomalies.java:116 | reg:10 | gold block (41) | shell of the brass "clockworks" cell (cell 5) | decorative | 159:4 yellow terracotta | pass 159,4 instead of 41,0 |
| V144 | Relics.java:475 | reg:37 | iron block (42) | scrap-shack skin on every 4th stilt shack (corrugated sheet metal) | decorative | 43:8 smooth stone double slab | replace 42 in the array (data 8 then yields 43:8 exactly) |
| V145 | Relics.java:659 | reg:13 | iron block (42) | rim and hub of the rolled-aside vault door disc (hazard-striped concrete between) | decorative | 43:8 smooth stone double slab | replace both 42s |
| V146 | Temples.java:109 | reg:27 | gold block (41) | trim of The Green Sanctum | decorative | 159:4 yellow terracotta | change p[4] to 159 (p[5]=4) |
| V147 | Temples.java:114 | reg:28 | gold block (41) | trim of The Dust Reliquary | decorative | 159:4 yellow terracotta | change p[4] to 159 (p[5]=4) |
| V148 | Temples.java:119 | reg:29 | iron block (42) | trim ("plate") of The Oxide Sepulchre | decorative | 43:8 smooth stone double slab | change p[4] to 43 (p[5]=8) |
| V149 | StructureArchitecture.java:359 | cat:coalbreath_survey_mine, cat:greed_assay_catacombs, cat:excavators_shoring_camp, cat:quarry_pay_window, cat:excavation_sunken_assay | iron ore (15) | decorative ore face inside the granite 1:1 prop at (8,1..2,2..4); one iron-ore block per e-room storey; NOT remapped by Brush.put | decorative | 1:1 granite |  |
| V150 | StructureArchitecture.java:170 | cat:cinder_hospice_wings, cat:penitent_spillway_prison, cat:palimpsest_archive, cat:signalwreck_cell_chain, cat:cordon_vehicle_school, cat:blackreed_stilt_exchange ... | anvil (145) | bunker workbench | functional workstation | keep (report only) |  |
| V151 | StructureArchitecture.java:251 | cat:ashveil_customs_cistern, cat:rustwater_last_train, cat:tenement_counterweight_prison, cat:drowned_borough_bathworks, cat:penitent_ebb_prison, cat:signalwreck_fog_observatory ... | anvil (145) | machinery props in engine halls | functional workstation | keep (report only) |  |
| V152 | StructureArchitecture.java:405 | cat:tenement_counterweight_prison, cat:whiteout_meridian_station, cat:cordon_quarantine_lab, cat:sanguine_field_autopsy, cat:viridian_decontamination_well, cat:coalbreath_survey_mine ... | anvil (145) | armourer's anvil on the guard rack | functional workstation | keep (report only) |  |
| V153 | LiminalGenerator.java:133 | fold:11 | coal block (173) | raised dark stage (6 blocks) | decorative | 251:15 black concrete | the Fold world (jaspr_backrooms) is already generated: a generator change only affects a regenerated Fold; existing blocks need an in-world edit if wanted |
| V154 | LiminalGenerator.java:147 | fold:15 | coal block (173) | black bottom of the 12-deep glazed void wells (35 blocks) | decorative | 251:15 black concrete | the Fold world (jaspr_backrooms) is already generated: a generator change only affects a regenerated Fold; existing blocks need an in-world edit if wanted |
| V155 | LiminalGenerator.java:141 | fold:13 | enchanting table (116) | the "broken altar" (1 block) | functional workstation | keep (report only) | the Fold world (jaspr_backrooms) is already generated: a generator change only affects a regenerated Fold; existing blocks need an in-world edit if wanted |

Temple trim (palette p[4]) is used at: Temples.java:152, Temples.java:156, Temples.java:166, Temples.java:168, Temples.java:169, Temples.java:171, Temples.java:182, Temples.java:187, Temples.java:190, Temples.java:198, Temples.java:228, Temples.java:242, Temples.java:246. Changing the Skin arrays (Temples.java:109, 114, 119) covers all of them.

## Already compliant: catalogue brush remap

- server/custom-plugins/JasprHorrorBiomes/src/chat/jaspr/biomes/StructureArchitecture.java Brush.put (lines 570-579; live decompile StructureArchitecture.java:1041-1055): every catalogue block write remaps iron 42 -> polished andesite 1:6, gold 41 -> yellow terracotta 159:4, diamond 57 -> cyan terracotta 159:9. Literal sites covered: StructureArchitecture.java:71, StructureArchitecture.java:171, StructureArchitecture.java:223, StructureArchitecture.java:243, StructureArchitecture.java:250, StructureArchitecture.java:263, StructureArchitecture.java:305, StructureArchitecture.java:381, StructureArchitecture.java:404, StructureArchitecture.java:422, StructureArchitecture.java:427, StructureArchitecture.java:463, StructureArchitecture.java:492, StructureArchitecture.java:517, StructureArchitecture.java:524, StructureArchitecture.java:527. Testing Grounds: 0 iron/gold/diamond blocks in all 234 catalogue plots. no change needed (already compliant); iron ore 15 and anvils 145 are NOT covered by the remap (listed above).

## Loot tables containing valuable blocks (items in chests) - NO CHANGE

| file:line | table | item | weight | count |
|---|---|---|---|---|
| Anomalies.java:52 | SITE19_LOOT | ender chest | 4 | 1-1 |
| Anomalies.java:52 | SITE19_LOOT | enchanting table | 3 | 1-1 |
| Anomalies.java:461 | HIVE_LOOT | redstone block | 8 | 1-3 |
| Anomalies.java:463 | HIVE_LOOT | iron block | 8 | 1-3 |
| Breach.java:56 | PIT_HOARD | diamond block | 1 | 2-2 |
| Breach.java:122 | KENNELS_HOARD | diamond block | 1 | 1-2 |
| Breach.java:177 | WARREN_HOARD | diamond block | 1 | 1-2 |
| Breach.java:177 | WARREN_HOARD | beacon | 1 | 1-1 |
| Breach.java:235 | VIEWING_HOARD | diamond block | 1 | 1-2 |
| Breach.java:295 | SIGNAL_HOARD | diamond block | 1 | 1-1 |
| Breach.java:295 | SIGNAL_HOARD | beacon | 1 | 1-1 |
| Dungeons.java:967 | Dungeons.roll | LAPIS_ORE | - | : new ItemStack(Material.LAPIS_ORE, 1 + r.nextInt(3)); |
| Dungeons.java:970 | Dungeons.roll | IRON_BLOCK | - | default: return new ItemStack(Material.IRON_BLOCK); |
| Landmarks.java:137 | FOUNDRY_LOOT | anvil | 4 | 1-1 |
| Landmarks.java:137 | FOUNDRY_LOOT | redstone block | 5 | 1-2 |
| Landmarks.java:199 | ARCOLOGY_LOOT | redstone block | 6 | 1-3 |
| Landmarks.java:702 | METRO_LOOT | redstone block | 7 | 1-3 |
| Megaliths.java:611 | DEEP | gold block | 0 | 1-2 |
| Megaliths.java:611 | DEEP | diamond block | 0 | 1-1 |
| Megaliths.java:611 | DEEP | emerald block | 0 | 1-1 |
| Megaliths.java:751 | AM_LOOT | redstone block | 9 | 1-3 |
| Megaliths.java:753 | AM_LOOT | anvil | 4 | 1-1 |
| Megaliths.java:908 | GARRISON_LOOT | iron block | 9 | 2-5 |
| Megaliths.java:1306 | MATRIX_LOOT | redstone block | 8 | 1-3 |
| Megaliths.java:1308 | MATRIX_LOOT | beacon | 2 | 1-1 |
| Megaliths.java:1399 | SOULS_LOOT | gold block | 6 | 1-2 |
| Metropolis.java:57 | OLDTOWN_TROVE | diamond block | 1 | 1-2 |
| Metropolis.java:57 | OLDTOWN_TROVE | ender chest | 1 | 1-1 |
| Metropolis.java:179 | HIGHWAY_TROVE | iron block | 1 | 4-8 |
| Metropolis.java:179 | HIGHWAY_TROVE | diamond block | 1 | 1-1 |
| Metropolis.java:270 | SPIRE_TROVE | beacon | 1 | 1-1 |
| Metropolis.java:355 | HOTEL_TROVE | gold block | 1 | 2-4 |
| Metropolis.java:447 | TACO_TROVE | gold block | 1 | 2-3 |
| Metropolis.java:537 | MOTEL_TROVE | diamond block | 1 | 1-1 |
| Relics.java:59 | NUKE_LOOT | lapis block | 8 | 1-3 |
| Relics.java:60 | NUKE_LOOT | iron block | 8 | 1-3 |
| Relics.java:64 | NUKE_TROVE | diamond block | 1 | 1-2 |
| Relics.java:214 | CITY_TROVE | emerald block | 1 | 1-2 |
| Relics.java:214 | CITY_TROVE | ender chest | 1 | 1-1 |
| Relics.java:428 | MEGATON_TROVE | diamond block | 1 | 1-1 |
| Relics.java:525 | STRIP_TROVE | gold block | 1 | 3-6 |
| Relics.java:639 | VAULT_LOOT | iron block | 9 | 2-6 |
| Relics.java:864 | MCD_LOOT | gold block | 5 | 1-1 |
| Relics.java:867 | MCD_TROVE | gold block | 1 | 2-3 |
| Temples.java:56 | EMBER_LOOT | gold block | 6 | 1-2 |
| Temples.java:59 | EMBER_HOARD | gold block | 1 | 4-8 |
| Temples.java:59 | EMBER_HOARD | diamond block | 1 | 1-1 |
| Temples.java:66 | TIDE_HOARD | emerald block | 1 | 2-2 |
| Temples.java:66 | TIDE_HOARD | diamond block | 1 | 1-1 |
| Temples.java:73 | SANCTUM_HOARD | emerald block | 1 | 3-3 |
| Temples.java:73 | SANCTUM_HOARD | gold block | 1 | 2-4 |
| Temples.java:77 | RELIQUARY_LOOT | gold block | 7 | 1-2 |
| Temples.java:80 | RELIQUARY_HOARD | gold block | 1 | 5-9 |
| Temples.java:80 | RELIQUARY_HOARD | diamond block | 1 | 1-1 |
| Temples.java:87 | OXIDE_HOARD | iron block | 1 | 6-10 |
| Temples.java:87 | OXIDE_HOARD | diamond block | 1 | 1-1 |
| Temples.java:94 | FROST_HOARD | diamond block | 1 | 2-2 |
| Temples.java:94 | FROST_HOARD | emerald block | 1 | 1-2 |
| Temples.java:277 | TOWN_HOARD | diamond block | 1 | 1-2 |
| Wonders.java:117 | CHOIR_TROVE | enchanting table | 1 | 1-1 |
| Wonders.java:199 | CHANCEL_TROVE | emerald block | 1 | 1-2 |
| Wonders.java:270 | PYRAMID_LOOT | quartz ore | 8 | 2-6 |
| Wonders.java:271 | PYRAMID_LOOT | gold block | 6 | 1-2 |
| Wonders.java:275 | PYRAMID_TROVE | gold block | 1 | 4-8 |
| Wonders.java:275 | PYRAMID_TROVE | emerald block | 1 | 2-2 |
| Wonders.java:366 | RAPTURE_LOOT | gold block | 7 | 1-2 |
| Wonders.java:371 | RAPTURE_TROVE | diamond block | 1 | 1-2 |
| Wonders.java:456 | COLUMBIA_LOOT | gold block | 7 | 1-2 |
| Wonders.java:460 | COLUMBIA_TROVE | gold block | 1 | 4-8 |
| Wonders.java:587 | LAGOON_TROVE | diamond block | 1 | 1-1 |
| Wonders.java:685 | PARK_TROVE | emerald block | 1 | 2-2 |

## Notes

- Replacing blocks in generator code changes only NEWLY generated chunks; existing copies in the live world (and the already-generated Fold) keep their blocks.
- Recognition is unaffected: Megaliths.located / Dungeons.locate / StructurePlanner.identify are pure placement functions and StructureLoot, Where, TerrainLighting, SpawnerDrive, StructureEncounters, Containment do not test for any of the replaced block ids (grep-checked), so the CONVENTIONS rule "changing placement must not change recognition" holds.
- Per-structure proposals listing two targets for one block (e.g. The Dust Reliquary 41->159:4 and 41->251:4) mean different placements: temple trim -> 159:4, the golden idol -> 251:4.
- The Lodge (reg:45) anvils/iron and the shaft-bottom plate are overwritten later in the same builder (0 in the capture); listed for completeness.
