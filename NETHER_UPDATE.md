# BetterNether + NetherEx (JasprNether 1.0.0) -- 2026-09-26

BetterNether 0.1.8.6 and NetherEx 2.2.5 ported to Paper 1.12.2 as one harmonised, harder Nether. The owner asked for
the two mods to "harmonize with one another and make the Nether more interesting but also more difficult", and for
the current Nether to be regenerated.

## Where things are
- Plugin: `server/custom-plugins/JasprNether/` (`build.sh` -> `build/JasprNether.jar`).
- Block emulation table: `resources/blocks.tsv`, the ONE place where mod blocks become vanilla 1.12 blocks (the
  browser client renders vanilla blocks only). Structure templates (`resources/structures/*.jnt`, converted by
  `tools/convert_templates.py`) keep the original mod block ids; everything resolves through the table. To use genuine
  custom blocks later, change this table only (or drop an override at `plugins/JasprNether/blocks.tsv`).
- Tests: `node --test tests/nether-lostcities.test.cjs`; in-game `/jnether selftest` (needs the self-test JVM flag,
  never set live).

## Behaviour
- **Biomes**: NetherEx regions (Hell 35%, Ruthless Sands 26%, Torrid Wasteland 21%, Fungi Forest 12%, Arctic Abyss 6.5%);
  BetterNether's 10 biomes live inside Hell regions and stack vertically. `/where` in the Nether names the biome and
  the structure with its origin ("BetterNether" / "NetherEx"). HorrorBiomes' Outer Realms stays out of the Nether.
- **Structures** (52): BetterNether's Nether City (19 building templates), altars, portal ruins, gardens, pillar,
  respawn points, blaze-spawner cave room, bone reefs, large mushrooms, stalagnates and wart caps; NetherEx's Ghast
  Queen Shrine and Pigtificate Village (with a Gold Golem); NetherEx's unused spoul shrooms and soul-sandstone arch as
  rare decoration. No valuable blocks.
- **Mobs** (16): Wight, Spinout, Salamander, Ember, Mogus, Spore, Spore Creeper, Coolmar Spider, Frost, Brute,
  Nethermite, Ghastling, Ghast Queen (boss, summoned at the urn with a Potion of Sorrow), Gold Golem, Pigtificate
  (amethyst trades), fireflies -- on vanilla bases with the mods' abilities.
- **Harder**: Nether monster cap x1.5, Nether hostiles +25% health and damage, 6% elite variants.
  `plugins/JasprNether/config.yml`.
- **Items** (71): the mods' materials, foods, tools, armour sets, potions and utilities, as tagged vanilla items with
  the mods' names and behaviour; the mods' recipes and brewing.
- **Not ported**: blue-fire portal, obsidian boat, soul glass, plant growth, splash/lingering potions, Pigtificate
  breeding. Mob looks are vanilla.

## Regenerating the Nether
Automatic and one-shot: on JasprNether's first start, before any world loads, the old Nether's region files are MOVED
(not deleted) to `plugins/JasprNether/nether-before-v1/` (log `NETHER_REGENERATED movedRegionFiles=<n>`; marker
`plugins/JasprNether/regenerated-v1.txt`). The Nether then generates afresh as players explore. Undo: stop the server
and move `nether-before-v1/region` back to `world_nether/DIM-1/region`. `regenerate-once: false` in the config skips
it. No player was saved in the Nether on 2026-09-26.
