# JasprDungeon — The Penitent Below

A separate dungeon dimension: an endless, connected, room-by-room dungeon with three stranger pocket realms behind
rifts (The Ashen Fold → The Drowned Choir → The Starless Maw). Rooms are gated: enemies stay in their room, difficulty
and loot scale with depth. 36 themes, 48 decoration modules, 36 bosses (72 phases), 72 baubles, 24 equipment
families and 24 dungeon weapons (8 melee, 4 magic, 12 guns).

Live on JasperCraft since 2026-10-03, deployed from the isolated sandbox
`C:\Users\AM\Documents\JasperCraft-Dungeon-Sandbox-20261002` (commit `e541f33`, generation 3). That sandbox holds the
full design notes (`ADVENTURE_CHECKPOINT.md`, `README.md`), the build (`build.ps1`) and every test suite and its
evidence. This folder is the deployed source; `server/plugins/JasprDungeon.jar` is the tested binary
(SHA-256 `c858b9ae858c6cefdf1559950fd466a6466bf66c0b925707bb6b2260667d0553`, without probe or audit classes).

## Playing

* Build a Nether-portal-shaped frame of **stone bricks** (4 wide × 5 high, 2 × 3 opening; corners optional) and light
  it with **flint and steel**. Stand in it for a second to reach **The Last Candle**, a safe four-room refuge.
* `/dungeon` explains the portal; `/dungeon leave` always takes you back out (from a rift: to the parent realm).
  `/dungeon where`, `/dungeon baubles`; `/dungeon items` (Creative only) and `/dungeon visit` (admin) are guarded by
  the plugin itself. TestServerControl lists `dungeon` among the public commands.
* Creative: search `dungeon` or `penitent`. Survival players only get these items as dungeon rewards.

## Worlds and files

* `jaspr_penitent_below` is created at start-up; each pocket realm's world is created the first time someone enters
  it. Progress, claims and journals are world-qualified files in the plugin folder.
* `plugins/JasprDungeon/config.yml` (installed with the deploy, same values as the shipped default).

## Client

The browser client's Creative menu gets 349 entries, appended to the native `JasprCreativeCatalog` array inside a
`/*JASPR_DUNGEON_CAT_V3_BEGIN:...*/ ... /*JASPR_DUNGEON_CAT_V3_END*/` fence by the sandbox's
`client/scripts/build-creative-client.cjs --source <live site/classes.js>` (data only, guarded by hashes of the native
hooks, strips back byte for byte). The Mo' Bends and melee builders carry the fence through unchanged.

## Known limits

Weapons use vanilla carrier models with custom names, lore and server mechanics (no dedicated meshes yet); one long
inventory tooltip clips at the bottom left. Tested in the sandbox with real protocol clients, a browser client and
JasprGear; not soak-tested against the full production plugin set.
