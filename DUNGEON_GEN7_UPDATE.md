# Dungeon Dimension generation 7 (2026-10-08, JasprDungeon 7.0.0)

Owner, 2026-10-08: "in the dungeon dimension, the mobs should be more spread out. They're all clustered in the center of giant rooms
when they could be more spread out in the rooms. The bosses should be harder, and there should be more variety. Do some polishing in
general of the dungeon dimension." Generation 7 (requested 2026-10-05: three Isaac-style floors, real bosses, crowds, perils, secrets,
gunners, twice the content) had been built in the dungeon sandbox but never shipped; it is finished, polished and released with this.

## What players get

**Crowds that fill the room and hold it.** Generation 6 put every mob within ten blocks of the room's centre. Now a room's mobs stand on
spawn stations spread over the whole floor (the first ones as far apart as the lanes allow), and the count grows with the room: 3 in a
small room, up to 26-33 in a 128 x 128 hall. They **hold their posts** until a player comes within 10 blocks, is seen within 18 (Floor II: 22,
Floor III: 26), strikes one, or a neighbour already in the fight calls them (12 blocks). So a great hall is a series of fights across the
room instead of one crowd running at the door. A mob that strays is walked back to its post. Boss rooms, the floor guardians' arenas and
woken treasure/shrine guardians still fight at once. Measured on the real server: in a 96 x 128 hall, 25 of 26 mobs stayed at their posts
spread over 126 blocks while the player stood inside the door; a blow at a mob 126 blocks away drew it into the fight.

**Three floors (The House of Mercy, The Underworks, The Abyssal Citadel).** Every run starts on Floor I. Each floor's Descent (Floor
III: the Throne) is a whole 128 x 128 arena, sealed until 5 / 6 / 7 rooms of that floor are absolved; its Floor Guardian falls, a well
opens, and the run goes down. Floors II and III are new worlds with their own layouts and themes (102 themes in all, 66 new), harder
(health x1.8 / x3.0, damage x1.4 / x1.9, more mobs). The Throne's fall wins the run (the Laurel, a chronicle, a gate home).

**Bosses that fight like bosses, and harder ones.** Every boss has a kit from 21 abilities (summons every 12-20 s, charge, leap slam,
volleys, beams, meteor rain, blink, vortex, fangs, lightning, snares, clones, shields, fire rings, roars ...), phases at two thirds and one
third, and an enrage. Floor guardians have their own multi-phase kits (Floor I 1,800 health, Floor II 4,212, the Throne 10,800). Room
bosses are tougher than before: **+35-39% health and +20-25% damage** on top of the kits. A mutant boss is never weaker than the boss it
stands in for (a floor guardian that is a mutant keeps its thousands of health). Bosses and escorts are **drawn as big as they are**: a
boss in a great hall is up to 5x its vanilla size, with the hitbox to match (the client draws the server's own size table, so a boss no
longer looks small while hitting from far away).

**More variety.** 20 new species (Flagellant, Candle Wisp, Gravebound Knight, Magma Lurker, Tunnel Gunner, Deep Miner, Rust Golem, Abyssal
Gunslinger, Void Wraith, Doom Herald ...), gunners from Floor II on (laser sight, then a shot walls stop), physical perils (lava pools,
falling stalactites, collapsing floors, geysers), secrets (alcoves, mimic reliquaries, a gilded thief, a lost peddler on Floors II/III, and
rare Easter-egg rooms), 112 new relics and 3 guardian trophies, each with its own icon.

**Mutants.** The Mutant Creatures port (JasprMutants) is not finished, so the dungeon uses each mutant's vanilla stand-in (a mutant
zombie is a zombie ...); the stand-ins are now sized like any other boss or escort of that kind (they were plain-size with a mutant's
hitbox).

## Fixes found while finishing it
- The secrets planner could promise a peddler's camp, an Easter egg or a loose stone in a room with no free bay for it (Floor III's lava-sea
  platforms): a room now promises only what it can hold.
- A floor guardian's second relic is drawn at the finale's own threat and given whatever the loot multiplier.
- Floor II/III guardians that were mutants spawned with a few hundred health instead of thousands.

## Under the hood
- Sandbox: `C:\Users\AM\Documents\JasperCraft-Dungeon-Gen7`, branch `generation7`. Source copied to `server/custom-plugins/JasprDungeon`,
  jar `server/plugins/JasprDungeon.jar` (7.0.0). The live config's `world-name` moves from `jaspr_dungeon6` to `jaspr_dungeon7` (generation 6's
  journals are archived; run worlds were never kept anyway).
- New: `Watch.java` (who joins the fight, pure), `Encounters` applies it (`DUNGEON_WATCH_METRICS noticed= called= struck= returns=` at shutdown,
  ready line `watch=stations`).
- Client (`scripts/assemble-dungeon7-client.cjs`, from the live files): the Creative catalogue's Dungeon block rebuilt from the plugin's own
  export (349 -> 926 entries), the Big Mobs stage (`client-mods/big-mobs-teavm.js`, `scripts/build-big-mobs-client.cjs`: channel jaspr:scale ->
  scaled model and client hitbox), and the trinket pack with the new icons. Cache key `20261008-dungeon7`.

## Tests
- 21 static suites in the sandbox (`build.ps1`), new `WatchAuditTest` (in 24 great halls only 40 of 732 mobs are drawn in at the door).
- `tests/gen7-runtime.cjs`: one real Paper with a protocol client through all three floors, every guardian, its kit, summons and phases,
  the Throne and victory, a death run, the in-server loot, Creative and armory audits, and the great-hall crowd (147 checks).
- `tests/big-mobs-client.test.cjs` (module and stage) and `tests/big-mobs-browser.cjs` (the real client: a zombie told 3x is drawn 3x with a
  1.8 x 5.85 hitbox, and its own size comes back).
