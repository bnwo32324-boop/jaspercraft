# The Dungeon Dimension — JasperCraft dungeon plugin

Source of truth for `JasprDungeon` (Paper 1.12.2), the live JasperCraft plugin. Generation 4 renamed the dimension from The Penitent Below to **the Dungeon Dimension**; generation 5 (world `jaspr_dungeon5`, 2026-10-03) traps only some rooms, makes the seizing traps rare and stills them in a conquered room; **generation 6** (2026-10-04, base name `jaspr_dungeon6`) makes **every entry a fresh run** in its own new world. Design: [RUNS_DESIGN.md](RUNS_DESIGN.md). Deployment record: [DEPLOYED-2026-10-03.md](DEPLOYED-2026-10-03.md). Architecture and verification limits: [ADVENTURE_CHECKPOINT.md](ADVENTURE_CHECKPOINT.md). Generated content list: [build/CONTENT_CATALOG.md](build/CONTENT_CATALOG.md).

## Getting there

Build a Nether-portal-shaped **stone-brick frame**, **4 wide × 5 high**, with an empty **2 × 3** opening (corners optional, either orientation). Light a frame block with **flint and steel** and stand in the portal for a second. Every entry begins a **new run** (see below). You arrive in **The Last Candle**, a four-room refuge with no enemies; only the candle-lit arrival circle around its portal is free of danger. Its stone-brick portal takes you back to where you entered; each player's return point is saved. Arrival and return check for solid ground, headroom and nearby hazards, and refuse travel rather than carve blocks.

Walk into a barred doorway to cross into the next room. Enemies cannot follow. Rooms generate on demand in every direction, in sizes from 32 to 128 blocks, across 36 themes and 48 decoration patterns. Threat runs 0–5 and grows with room size and distance.

## Some rooms have traps

Owner, 2026-10-03: "traps should not be in every room, and the gravity well should be particularly rare, as well as other traps that are similar to it", and those "should be disabled if you conquer the room". About **one room in three** is trapped: **gauntlets always (two traps)**, three treasure rooms in five, two shrines in five, three ordinary rooms in ten and one boss chamber in four (one trap each); **the refuges never**. Each theme favours three dangers. The **seizing** ones take hold of you instead of striking a spot: **Gravity Well** (drags), **Shockwave** (throws), **Creeping Dark** (blinds) and **Frost Gusts** (freeze). They are rare: about one room in 70 has one, and a Gravity Well is rarest of all (about one room in 400). A gauntlet's second trap is never a seizing one. **When a room is absolved (all its enemies killed), its seizing trap falls still at once**; its other traps carry on at a slower pace. A room's title says "No traps" when it has none. Every danger is **telegraphed by particles and a sound**, is **marked in the stone of its room**, and **never reaches the arrival circle** around the return portal. Your room's dangers appear on screen when you enter it and in `/dungeon where`.

| Danger | Mark | What it does |
|---|---|---|
| Flame Vents | netherrack grates in the floor | the grates erupt in fire |
| Dart Slits | chiseled slits in the walls at eye level | darts fly across the room |
| Spike Runes | black rune channels across the floor | the runes flare, then spike whoever stands on them |
| Falling Masonry | cracked bricks in the ceiling | masonry shakes loose and falls |
| Poison Miasma | lime seep stones | poison clouds rise from the seeps |
| Frost Gusts | packed-ice drifts | freezing gusts slow and chill |
| Smiting Bolts | white glass storm lenses overhead | bolts strike down from the lenses |
| Shockwave | cyan tremor rings around the centre | the floor heaves outward and throws you |
| Phantom Blades | pale blade tracks in the floor | spectral blades sweep along the tracks |
| Potion Rain | purple glass drip panes overhead | harmful potions rain down |
| Gravity Well | blue sigils around lane crossings | a well opens and drags you in |
| Creeping Dark | black stains on floor and ceiling | darkness creeps out and saps you |
| Blast Spores | spore pods along the walls | the pods swell and burst |
| Ember Bolts | nether-brick sockets in the walls | the sockets spit burning bolts |

## Chests and rewards

**Every chest opens like a normal chest** once its room is clear, so you can pick and choose what to take. Each room's chest is filled once and never refills. In **treasure rooms and shrines** the guardians sleep until someone opens the chest; opening it wakes them. Gauntlets bring extra enemies on top of their two dangers, and boss rooms hold named bosses with telegraphed attack patterns. Loot follows threat: provisions and materials, then experience, diamonds, enchanted gear, dungeon baubles and armory weapons.

The refuge chest offers a free **Dungeon Reliquary** and a lore book. Right-click the reliquary to equip two different baubles; it works in every world and its contents travel with it. Baubles, armory weapons and guns have no recipes and are found only in this dimension. Creative players can search `dungeon` in the Creative menu or use `/dungeon items [page]`. Weapon details: [ARMORY_NOTES.md](ARMORY_NOTES.md).

## Every entry is a new run

Owner, 2026-10-04: "every time I go in, it should be like a new run", and a new session "should not contain any of the stale rooms that the previous session had". Every entry through an overworld gate starts a **run** in its own new world, `jaspr_dungeon6_s<n>`, with a fresh random seed, so its rooms (themes, patterns, sizes, kinds, traps and loot) are completely different from any other run; a new run never even repeats the refuge theme of the run you just had. Its rifts are its own worlds too (`jaspr_dungeon6_s<n>_rift_<realm>`), made when first crossed. The run number `n` is never used twice.

- **Friends share a run**: whoever steps through the **same gate** within **30 seconds** (`session-join-seconds`) of a run's start joins it.
- **Leaving ends your part for good**: the return gate, `/dungeon leave` or **dying** sends you home, and you never rejoin that run; entering again starts a new one. A death in a run keeps your inventory and levels (run worlds use `keepInventory`), because the run world is deleted afterwards.
- **Disconnecting inside** keeps you in the run: log back in while it lives and you are back where you were.
- A run with nobody inside **closes** after **5 minutes** (`session-grace-seconds`), or as soon as its join window is over when everyone has left it: its worlds are unloaded and, ten seconds later, deleted with its room and rift journals.
- **Nothing survives a restart**: leftover run worlds and journals are deleted at start, and a returning player whose run is gone is sent home to the gate they entered by.
- At most **16** runs (`max-sessions`) exist at once; beyond that the gate politely refuses until one closes.

## Rifts

A violet crack in the refuge leads to **The Ashen Fold**, then **The Drowned Choir**, then **The Starless Maw**: three further worlds of the same run, each harder and richer than the last. A pale crack returns you to the room you came from; `/dungeon leave` always returns you to the parent realm. Some cleared boss rooms and shrines reveal more cracks. See [RIFTS_NOTES.md](RIFTS_NOTES.md).

## Persistence

Generation 6 keeps no shared world: `world-name` (`jaspr_dungeon6`, it must start with `jaspr_dungeon`) is the base name of every run world. When the configured `world-name` differs from the saved dimension, the old dimension's identity and room journals move intact to `plugins/JasprDungeon/archive/` and a new dimension starts (generation 5 is archived this way; its world `jaspr_dungeon5` stays untouched on disk). Items from earlier generations keep working, and each player's way home (`returns/<uuid>.yml`, which also carries the run marker) survives everything.

Within a run, room plans come from the run's seed and do not depend on chunk order; cleared rooms, defeated enemies and chest fills last as long as the run (journals under `plugins/JasprDungeon/sessions/<run>/`). After 30 seconds without players a room's enemies sleep; only survivors return. Survival players cannot break, place or blow up the dungeon. The run counter is `plugins/JasprDungeon/sessions.yml`.

## Commands

- `/dungeon`: how to reach the dimension.
- `/dungeon where`: run number, realm, room, theme pattern, threat and dangers.
- `/dungeon baubles`: open your reliquary.
- `/dungeon leave`: return to the parent realm, or from the run's own world to your entry portal (ending your run).
- `/dungeon items [page]`: Creative-only item catalogue.
- `/dungeon status`: base world name, live runs, generation, dangers and encounter status.
- `/dungeon visit <roomX> <roomZ>`: inspection travel for Creative administrators with `jaspr.dungeon.admin`, inside their own run (from outside, a new run begins for them).

## Build

`powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1` builds `build/JasprDungeon.jar` and runs the deterministic layout, hazard, geometry, encounter, loot, armory, rift, session and catalogue tests. `node tests/gen6-runtime.cjs` checks runs on an isolated 127.0.0.1:25671 Paper server with protocol clients; `npm run test:server` runs the older integration tests. Never deploy `DungeonProbe.jar`.
