# GLM structures, Nether creatures and the Nether Lords (JasprNether 1.2.0) -- 2026-09-29

The owner asked to "implement everything from `C:\Users\AM\Desktop\GLM Nether structures` into the Nether's natural
structure generation", to make the builds "dangerous and filled with loot" with "only Nether mobs", many of them
custom, with "bosses that you have to conquer", "a lot of custom loot that has a Nether theme ... new trinkets", all
"integrated into the story, mission, and quest progression system of the Nether", and then to regenerate the Nether.
The builds are raw schematics: the loot, spawners, mob packs and bosses are designed here, in keeping with each build.

## The builds
- **Source**: the owner's folder (read only). It held 100 files when converted: 98 unique builds (N206 duplicates
  N018 and N224 duplicates N086, 99.6 % and 99.4 % identical). Files added later are converted with defaults (title
  from the file name, tier by size, theme by keywords).
- **Converter**: `server/custom-plugins/JasprNether/tools/convert_glm.py` (numpy) writes `resources/glm/KEY.glb`
  (gzip binary, 3.9 MB in all) and `resources/glm/builds.tsv`. Run `python convert_glm.py [KEY ...] [--render DIR]`.
  - Trims empty borders; the tallest builds are cut or scaled to the Nether (y 5-121): N144 Deathwing's Lair scaled
    1/2.42, N207 and N208 cut and scaled, N202 loses the mountain's base, N157/N194/N211/N072/N073 lose their tops,
    N057 its lower pole, N162 part of its end-stone base.
  - **Valuable and technical blocks become look-alikes** (gold block -> yellow concrete, iron -> white, diamond -> light
    blue, emerald -> lime, lapis -> blue, redstone block -> red concrete, beacon -> sea lantern, portal -> purple
    glass, command blocks -> terracotta / purpur / prismarine, hopper -> cauldron, anvil -> polished andesite,
    enchanting table and dragon egg -> obsidian, ender chest -> a loot chest, jukebox -> spruce planks, TNT -> red
    terracotta, beds -> carpet, shulker boxes -> concrete, iron doors and trapdoors -> wooden ones, special rails and
    plates -> plain ones). Redstone is neutralised (wire and comparators removed, levers off, lamps glowstone). The
    converter refuses to write a build that still holds a block of the forbidden list.
  - **Natural ground becomes the region's**: grass, dirt, stone, sand, snow, trees, plants and water are placeholders
    that take the materials of the NetherEx region the build stands in (Hell netherrack and wart-block trees, Ruthless
    Sands soul sand and bone "dead trees", Torrid fiery rock and magma canopies, Fungi hyphae with elder-mushroom trees
    and mushrooms, Arctic frostburn ice, packed-ice trees, snow and ichor water; elsewhere water is lava).
  - Signs keep harmless text; offensive, username and mechanism signs are blanked; graveyards and crypts (N143, N177,
    N220) get Nether epitaphs. Skulls are never wither skulls.
  - Each build gets: its chests tiered (vault, rich/common, scraps; extra chests where it had too few), spawner spots,
    garrison (mob pack) spots and, for the ten Lords, an arena (flying Lords: the most open air beside the lair).
    Buried builds without a way in get a ladder shaft with a nether-brick well head.
  - Each build carries its cavern: per column the ceiling it needs (headroom, more for flying Lords), a sealing ring
    and a margin where lava lakes may lie.

## Placement (GlmSites.java)
Planning order: Nether Cities, **Lords' strongholds** (640-block cells, every cell, the Lord chosen so neighbouring
cells differ), **great builds** (448-block cells, 65 %), mega structures (384-block cells, as before but clear of the
Lords and the great builds), **common builds** (176-block cells, 50 %). Builds with a region affinity (bastions in Hell,
volcanic builds in the Torrid Wasteland, fungus builds in the Fungi Forest, desert builds in the Ruthless Sands, the
snowy castle in the Arctic) prefer it. Every build hollows its own cavern, seals the lava around it, lays the region's
floor, encases its buried part in rock, and is drawn chunk by chunk (deterministic; offline check draws all 98 at an
average under 2 ms per chunk). The mods' small structures and the wonders keep out; `/where` names the build.
Fire never destroys a build (burning and spreading fire are stopped inside the builds' boxes; fireballs still burn).

## Danger
- **13 Nether creatures** (`Fiends.java`): Infernal Knight (charging, burning wither skeleton), Ashbone Archer (burning
  arrows, volleys), Hellhound (burning, leaping pack wolves), Cinder Imp (fireball-throwing imp), Soul Wraith (passes
  walls, withers), Magma Hulk (ground slam, bursts into embers), Pyre Warden (flame nova), Shade (blinks behind you,
  slows), Brimstone Spider (poison and fire), Pigman Berserker (golden axe, enrages), Dread Rider (mounted charger),
  Charred Ghoul (hunger and weakness), Cinder Witch (brews, calls imps). Elite forms for each.
- **Spawners** (`GlmLife.java`): inert spawner blocks showing their creature, woken by the plugin while a player is
  within 16 blocks (one or two every 10-25 s, at most four near it); breaking one ends it. Each build's theme decides
  its creatures (a fifth come from the region's own NetherEx mobs).
- **Garrisons**: packs of two or three (often led by an elite) roused when a player comes within 18 blocks.
- **Trapped chests** spring an ambush of the build's guards, once.
- **The ten Nether Lords** (`Lords.java`), each in its stronghold, rising when a player reaches the arena, three
  phases with minions at 2/3 and 1/3, resting 15 minutes after they fall, never damaging blocks:
  Deathwing and Ignareth the Magma Wyrm (ender dragons flown by the plugin: fire volleys, blasts, dives, burning rain),
  the Pit Lord (giant: blows, slams, fireballs), the Ashen Wither, the Cursed King (blinks, curses, soul wraiths), the
  Dread Sorcerer (evoker: fangs, vexes, fire volleys, blinks away), the Voidborn (pulls, strikes from behind, lifts),
  the Bone Colossus (bone storms, slams), the Crimson Tyrant (giant magma cube, crushing landings, rings of fire), the
  Blood Count (drinks the life he takes, blood bats, mist form).

## Loot and items (Items.java, Loot.java, Relics.java, Crafting.java)
- Tables `glm:<scraps|common|rich|vault>:<theme>`; every stronghold chest is worth opening, vaults always hold a prize.
- Materials from the creatures: Hellforged Shard/Ingot, Ashbone, Hellhound Fang, Imp Horn, Soul Essence, Molten Core,
  Pyre Ember, Void Shard, Brimstone, Charred Bone, Hex Ember.
- Gear: Hellforged and Soulweave armour (full-set bonuses: four more hearts; no wither), Infernal Blade, Soulreaper
  Scythe, Magma Maul, Ember Bow, Obsidian Aegis. Trinkets: Brimstone Idol, Heart of Cinders, Hellhound Collar, Ember
  Heart, Wither Ward, Magma Band, Ghastly Pendant. All craftable from the materials; none feeds a vanilla recipe.
- Lords' relics (half the time in a hoard, unbreakable): Deathwing's Talon, Wyrmfire Bow, Pit Lord's Cleaver, Ashen
  Crown, Cursed Katana, Dread Staff (hurls fireballs), Voidstep Boots, Colossus Maul, Tyrant's Heart, Bloodfang Dagger.
  Every conqueror gets the Lord's Sigil (a music-disc trophy).

## The quest
The Nether Guide's checklist now opens with **Conquer three Nether Lords** (any three of the ten); the compass and the
map point to the nearest unconquered Lord's arena (red marks on the map, white crosses once conquered). The Urn of
Sorrow refuses the Potion of Sorrow until the player has conquered three Lords. Then as before: the Spore Cathedral,
the Potion of Sorrow, the Urn, the Ghast Queen.

## Regeneration
`REGEN_EPOCH` 3: on the next start the Nether's region files move (never deleted) to
`plugins/JasprNether/nether-before-v3/`; the new Nether is generated with everything above.

## Tests and tools
- `node --test tests/nether-glm.test.cjs` compiles the plugin with `tests/java/chat/jaspr/nether/GlmPreview.java`:
  planning over 12,000 blocks (98/98 builds and 10/10 Lords appear, no overlaps, no mega collisions, neighbouring
  lords differ, floors fit) and every build drawn chunk by chunk (deterministic, no forbidden block, stocked, cheap).
- In-game (test servers only, `-Djaspr.nether.selftest=true`): the self-test generates the nearest build of each tier
  and raises and slays all ten Lords. Admin: `/jnether glm [trap]`, `/jnether goto <glm|lord|lordid|N153>`,
  `/jnether lord <id>`, `/jnether lords`, `/jnether conquer <id|all|none> [player]`.
- Logs: `NETHER_GLM_PLANNED`, `NETHER_LORD_RISEN/PHASE/DEFEATED/CREDIT/RESET`, `NETHER_GLM_AMBUSH`, `NETHER_URN_REFUSED`;
  `/jnether status` shows build loads, tiles, loot, spawner wake-ups, ambushes, fire stopped and relic use.

## 2026-10-01: Sulphur Sewers removed, Nether reset (1.2.2)

The owner removed **N094, The Sulphur Sewers**, identified by their screenshot, and requested Nether regeneration.
`glm/disabled-natural.txt` disables this build in the planner. Its catalogue entry remains available for recognising
saved structures. The pool order is retained: other build choices and the ten Nether Lords are preserved. There are
**97 active GLM builds**, with 98 entries retained in the recognition catalogue.

`REGEN_EPOCH` is now **4**. On the next start, the old Nether region files, the JasprNether terrain records and rime
records, and the Nether's Fortress/village metadata are archived in `plugins/JasprNether/nether-before-v4/`.
The `regenerated-v4.txt` marker prevents another reset on later starts. The Nether seed and UUID, players' inventory
and quest progress, and other dimensions are preserved. N094 is absent from the new terrain.

Verified in isolation: the existing full GLM drawing/planning/wiring suite passes; a 12,000-block planning survey
finds 97/97 allowed builds, 10/10 Lords, no disabled placements, no overlaps, no mega collisions and no invalid floors.
A real Paper baseline/reset/second-restart test passes 17 checks, including byte-preserved backup files and persistence.
Evidence and deployment record: `C:\Users\AM\Documents\JasperCraft-NetherRemoval-20261001\`.

## 2026-10-01: audited crops, stair corrections, doubled density and fresh Nether (1.2.4)

All **98 unique GLM resources** were audited and updated; **97 remain enabled**, with N094 still disabled.
Imported hills, foliage, dirt/grass, water and bulk terrain were removed while preserving authored architecture,
room skins and basements. Original litematic states verified 357,307 source stairs and corrected 251,807 facing/half
metadata values before fitting. Native Paper stair rotation checks cover all four rotations. Existing downsampling
can still lose or duplicate thin geometry; this is not a claim of perfect appearance for every resampled stair flight.

GLM candidate density is now **2x in every tier**. Collision-adjusted sample counts rose about 2.05–2.11x. Existing
layouts are recognised with their old assets and decisions; freshly regenerated terrain uses the cleaned builds.
Creative-mode players can use `/tpd 1` (Overworld), `2` (Nether), `3` (End), `4` (Atlas), `5` (Drownhollow), and
`6` (Backrooms), regardless of their source dimension. `/tpd` lists additional loaded dimensions with stable IDs.

The owner explicitly requested another regeneration after the audit. **Epoch 5** archives the current Nether's
regions, JasprNether terrain records/rime and native structure metadata under `plugins/JasprNether/nether-before-v5/`.
The `regenerated-v5.txt` marker prevents repeated regeneration. The seed, world UUID, player inventories and other
dimensions are preserved. Regeneration replaces Nether constructions; the old terrain is recoverable from the backup.

Evidence: `C:\Users\AM\Documents\JasperCraft-NetherAudit-20261001\` (98-build audit, stairs, density and actual
dimension travel) and `C:\Users\AM\Documents\JasperCraft-NetherCommit-Regen-20261001\` (scoped Git commits,
epoch-5 baseline/reset/restart fixture, live verification and recovery checkpoint).
