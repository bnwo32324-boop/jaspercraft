# BetterNether + NetherEx (JasprNether 1.2.0) -- 2026-09-26, mega structures 2026-09-28, GLM builds 2026-09-29

> 2026-09-29 (1.2.0): the owner's 98 GLM structures spawn in the Nether with 13 new creatures, spawners, mob packs,
> trapped chests, Nether-themed loot, gear and trinkets, and the ten Nether Lords; three Lords must be conquered before
> the Urn of Sorrow answers. The Nether regenerates (epoch 3). See GLM_NETHER_UPDATE.md.

> 2026-09-29: the Nether can be beaten step by step with the Nether Guide's compass, checklist and map (reach the
> Spore Cathedral, take a Potion of Sorrow from the Font of Sorrow in its crypt, pour it into the Urn, slay the Ghast
> Queen), and the spore cloud no longer freezes the browser client. See GUIDE_KIT_UPDATE.md.

BetterNether 0.1.8.6 and NetherEx 2.2.5 ported to Paper 1.12.2 as one harmonised, harder Nether. The owner asked for
the two mods to "harmonize with one another and make the Nether more interesting but also more difficult", and for
the current Nether to be regenerated.

## Where things are
- Plugin: `server/custom-plugins/JasprNether/` (`build.sh` -> `build/JasprNether.jar`).
- Block emulation table: `resources/blocks.tsv`, the ONE place where mod blocks become vanilla 1.12 blocks (the
  browser client renders vanilla blocks only). Structure templates (`resources/structures/*.jnt`, converted by
  `tools/convert_templates.py`) keep the original mod block ids; everything resolves through the table. To use genuine
  custom blocks later, change this table only (or drop an override at `plugins/JasprNether/blocks.tsv`).
- Tests: `node --test tests/nether-lostcities.test.cjs tests/nether-mega.test.cjs` (the second compiles the plugin and
  draws all five mega structures offline, `tests/java/chat/jaspr/nether/MegaPreview.java`; given an output folder it
  also renders isometric PNGs); in-game `/jnether selftest` (needs the self-test JVM flag, never set live) now also
  generates the nearest site of each mega structure and inspects it.

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

## Mega structures (1.1.0, 2026-09-28)
The owner asked for "massive structures in the Nether that spawn naturally ... five new mega structures" that keep
the Nether's theme, progression and loot, for the Nether to be "more interesting and fun to explore", and for the
Nether to be regenerated afterwards. Code: `Mega.java` (planner, caverns), `MegaBazaar/Pyramid/Forge/Cathedral/
Citadel.java` (the designs), `Draw.java` (chunk-clipped drawing), `Garrisons.java`, `Wonders.java`.
- **Placement**: one site per 384 x 384-block cell, kept 112 blocks from the cell's edges and out of every Nether City's
  reach; the NetherEx region at the site decides which structure it is, so each stands in its own land. Each hollows a
  cavern about 190 blocks across (floor at y 34-38, dome up to y 120) with its own floor, lakes and dressing; lava in the
  surrounding rock is sealed so nothing pours in; the walls stay natural, so tunnels still lead in. Drawn chunk by
  chunk and deterministic; the mods' small structures and the wonders stay out of a site's reach.
- **The five, in progression order**:
  - *The Golden Bazaar* (Hell): the Pigtificate capital on a walled mesa in a lava moat; four bridges and gatehouses,
    market stalls and round huts with Pigtificate traders (amethyst), a golden-domed palace with a **Respawner Statue**
    (glowstone dust sets your spawn) and a treasury guarded by Gold Golems, an amethyst mine, wart farms. **Peaceful**:
    no natural hostiles on the mesa, moat and bridges, no natural ghasts in its cavern.
  - *The Soul Pyramid* (Ruthless Sands): a nine-tier soul-sandstone ziggurat in soul-sand dunes: grand stairs to an
    obsidian obelisk, portals with Soul Wardens, a gallery, a ring corridor with burial chambers, the Hall of Kings (a
    ladder shaft drops into it from the summit), ghast fossils, thorn fields. Wither skeletons and Spinouts.
  - *The Cinder Forge* (Torrid Wasteland): a basalt foundry hall with three chimneys into the roof, a great crucible
    pouring into casting channels, blaze furnaces (2 blaze spawners), a slag pit (magma cube spawner), storerooms, a lava
    aqueduct on arches and the master smith's strongroom. Salamanders, Embers, magma cubes.
  - *The Spore Cathedral* (Fungi Forest): a colossal elder mushroom: spiral stair up the glass-slotted stem, the nave
    under the glowing-spotted cap, chapels under smaller caps, a crypt Reliquary, and on the crown the Weeping Balcony
    with an **Urn of Sorrow** (the Ghast Queen summon, open sky above). Spore Creepers, Mogus, a Ghastling.
  - *The Frozen Citadel* (Arctic Abyss): an octagonal ice curtain wall with eight spired towers, an ichor moat and
    drawbridge, and a four-storey keep: barracks, rime library, the **Rime Throne** between blue-fire braziers, a roof
    under a great ice spire. Wights (a Wight Lord), Coolmar Spiders, a Brute, Frost.
- **Loot** (`Loot.java`, `jaspr:mega/<name>` and `<name>_vault`): Nether goods only, graded by the structure's step;
  each vault always holds its prize: Bazaar amethyst, Pyramid a Wither Bone armour piece, Forge an Orange Salamander
  Hide armour piece, Cathedral a Potion of Sorrow (for the Queen), Citadel a Frosted Wither Bone (the richest vault).
  No valuable blocks are ever placed (checked offline for every block written and in-game by the self-test).
- **Garrisons**: guard points rouse a pack (2-4, some led by an elite) when a player comes within 18 blocks, then rest
  eight minutes; at most 10 garrison mobs near a point; the tracked-mob cap still applies; garrison wither skeletons
  drop no skulls. Log `NETHER_GARRISON_ROUSED at=x,y,z pack=... spawned=n`.
- **Wonders** (between the mega structures, at most one per chunk): lost expedition camps (their journal gives the
  distance and direction of the nearest of each mega structure), amethyst geodes, hanging cages, ghast fossils, soul
  graveyards with a buried chest, basalt groves, fairy rings, frozen obelisks with an offering chest.
- **Commands**: `/where` names mega structures and wonders ("JasperCraft"); admins: `/jnether goto <golden_bazaar|
  soul_pyramid|cinder_forge|spore_cathedral|frozen_citadel|mega|wonder> [player]` (the plan knows every site, so this
  also works for land not generated yet); `/jnether status` shows garrisons, peace kept, journals, rescues.
- **Logs**: `NETHER_MEGA_PLANNED kind= at= built=`, `NETHER_MEGA_HISTORY complete=`, `NETHER_READY ... mega=5 wonders=8`,
  `NETHER_GEN_STATS phaseMs[... mega= wonder= ...]`, `NETHER_RESCUED cause=join moved=dx,dy,dz` (no player names).
- A site is decided once, by the first chunk that reaches it, and recorded (`megacell` lines in the registry). In the
  regenerated Nether every site is built (marker `data/world_nether/mega-complete.txt`); in a Nether with older land a
  site is built only where its centre is new land.

## Regenerating the Nether
Automatic and one-shot per epoch (`NetherPlugin.REGEN_EPOCH`): before any world loads, the old Nether's region files
are MOVED (not deleted) to `plugins/JasprNether/nether-before-v<epoch>/` together with this plugin's records of that
terrain (log `NETHER_REGENERATED epoch=<n> movedRegionFiles=<n>`; marker `plugins/JasprNether/regenerated-v<epoch>.txt`).
The Nether then generates afresh as players explore. Undo: stop the server and move `nether-before-v<epoch>/region`
back to `world_nether/DIM-1/region`. `regenerate-once: false` in the config skips it. Epoch 1 (2026-09-26) was the
port; epoch 2 (2026-09-28) regenerates it for the mega structures and wonders. No player was saved in the Nether on
either date; a player who logs in inside rock or lava in the Nether is moved to the nearest safe floor.
