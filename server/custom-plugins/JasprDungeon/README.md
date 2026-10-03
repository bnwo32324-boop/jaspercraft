# JasprDungeon — the Dungeon Dimension

An endless, connected, room-by-room dungeon with three stranger pocket realms behind rifts (The Ashen Fold → The
Drowned Choir → The Starless Maw). Rooms are gated: enemies stay in their room, and difficulty and loot scale with
depth. 36 themes, 48 decoration modules, 36 bosses (72 phases), 72 baubles, 24 equipment families and 24 dungeon
weapons (8 melee, 4 magic, 12 guns).

Generation 4 (2026-10-03, owner's request): renamed from The Penitent Below, with dangers in every room, more detail
in every room, and chests that open normally (world `jaspr_dungeon`). Generation 5 (2026-10-03, owner: "traps should
not be in every room, and the gravity well should be particularly rare, as well as other traps that are similar to
it", and those disabled in a conquered room) is a new world, `jaspr_dungeon5`, with the same room plan. The source of truth with every
suite is the sandbox `C:\Users\AM\Documents\JasperCraft-Dungeon-Sandbox-20261002` (branch `generation5`, commit
`f1b4d9f`): `README.md`, `build.ps1`, `tests/gen5-runtime.cjs`. This folder is the deployed source, and
`server/plugins/JasprDungeon.jar` is the tested build (no probe or audit classes).

## Playing

* **Portal:** a Nether-portal-shaped frame of **stone bricks** (4 wide × 5 high, 2 × 3 opening; corners optional), lit
  with **flint and steel**. Stand in it for a second to reach **The Last Candle**, the four-room refuge.
* **Safety:** only the arrival circle around the return portal is safe.
* **Traps:** about one room in three: gauntlets always (two traps), 3 in 5 treasure rooms, 2 in 5 shrines, 3 in 10
  ordinary rooms, 1 in 4 boss chambers (one trap each); the refuges never. Seizing traps (Gravity Well, Shockwave,
  Creeping Dark, Frost Gusts) are rare (about one room in 70; a Gravity Well about one in 400), never a gauntlet's
  second trap, and fall still the moment their room is absolved. 14 kinds:
  Flame Vents, Dart Slits, Spike Runes, Falling Masonry, Poison Miasma, Frost Gusts, Smiting Bolts, Shockwave,
  Phantom Blades, Potion Rain, Gravity Well, Creeping Dark, Blast Spores, Ember Bolts. Each is marked in the stone of
  its room and telegraphed by particles and a sound before it strikes.
* **Guardians:** treasure rooms and shrines have guardians that wake when the chest is opened. A room's title shows
  its traps, or "No traps".
* **Chests:** every chest opens as a normal chest once its room is clear, so players take what they want; it is never
  refilled. The refuge chest offers each player a Dungeon Reliquary and a lore book.
* **Commands:** `/dungeon` (help), `/dungeon leave` (always takes you back out), `/dungeon where` (room and dangers),
  `/dungeon baubles`; `/dungeon items` (Creative) and `/dungeon visit` (admin) are guarded by the plugin.
  TestServerControl lists `dungeon` among the public commands.

## Worlds and files

* `jaspr_dungeon5` is created on first portal use; each pocket realm (`jaspr_dungeon5_rift_*`) on first entry.
* Changing `world-name` starts a new dimension: the previous one's `dimension.yml`, `rooms/`, `rooms-realms/` and
  `rifts/` move intact to `plugins/JasprDungeon/archive/` (log `DUNGEON_GENERATION_ARCHIVED`). Gates, return points and
  the old world folder are left alone. Generation 3's `jaspr_penitent_below` and generation 4's `jaspr_dungeon` stay on
  disk, unloaded. JasprDaylight exempts every world starting `jaspr_dungeon`, so keep that prefix.
* Log lines: `DUNGEON_READY`, `DUNGEON_HAZARDS_ARMED room= types=`, `DUNGEON_ROOM_AMBUSH`, `DUNGEON_RELIQUARY_FILLED`,
  `DUNGEON_ROOM_CLEARED`; failures `DUNGEON_HAZARD_DISABLED` / `DUNGEON_*_FAILED`.

## Client

The browser client's Creative menu has 349 entries, appended to `JasprCreativeCatalog` inside a
`/*JASPR_DUNGEON_CAT_V3_BEGIN:...*/ ... /*JASPR_DUNGEON_CAT_V3_END*/` fence by the sandbox's
`client/scripts/build-creative-client.cjs` (data only). After another stage changes `classes.js`, cut the old fence out
and rerun the builder with `--source` set to that file and `--catalog` set to the live plugin's `creative-catalog.json`.

## Known limits

Weapons use vanilla carrier models with custom names, lore and server mechanics. Gravity wells and miasma reward
moving away; the other dangers also catch a player who stands still. Verified for generation 5 by the sandbox suites
and `tests/gen5-runtime.cjs`.
