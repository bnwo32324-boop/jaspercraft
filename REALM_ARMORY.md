# Realm armouries: a set stronger than diamond for every dimension (2026-10-02)

Owner, 2026-10-02: "introduce some armor sets that are stronger than diamond. There should be an overworld armor set that
you can make just out of emeralds ... every dimension should have an armor set that's unique to that dimension and that is
stronger than diamond. Add this, and also add this to the looting system of each dimension including the overworld", and
"The armor should have accompanying tools and weapons as well, and they should also work with the upgrade system, the
armaments upgrade system."

## The six sets (JasprGear 5.0.0)
Every set has nine pieces: helmet, chestplate, leggings, boots, sword, axe, pickaxe, shovel, hoe. Each piece has its
own icon, and the armour has its own look when worn.

| Set | Realm | Forged from | Full armour set | Weapons (sword, axe) | Tools |
|---|---|---|---|---|---|
| **Emerald** | Overworld | emerald blocks only, in the vanilla shapes | Prosperity: +2 hearts, +2 luck | Bounty: +30% XP from kills, 12% chance of an emerald | Prospector: 20% chance of an extra ore drop |
| **Blazeforged** | Nether | diamond piece + 4 blaze rods + 4 magma cream | Fireproof: no fire, lava or magma damage | Searing: sets foes alight, +20% vs burning foes | Smelting: iron and gold ore drop ingots, sand drops glass |
| **Abyssal** | Drownhollow | diamond piece + 8 Abyssal Pearls | Deepbreath: never drown, the Dread cannot sicken you | Undertow: slows foes, +25% vs foes in water | Tidal: digging underwater refills your air |
| **Titan** | Atlas | diamond piece + 8 Titan Shards | Colossus: +50% knockback resistance, half fall damage | Sunder: +25% vs armoured foes (10+ armour), hits stagger | Titan's Strength: whole ore veins and trees; 3x3 digging and tilling while sneaking |
| **Liminal** | Backrooms | diamond piece + 8 Liminal Fragments | Lucid: immune to blindness and nausea, +10% speed | Disorient: 20% chance to blind, +25% vs foes in the dark | Pathfinder: mining in the dark places a torch from your pack |
| **Void** | End | diamond piece + 4 shulker shells + 4 popped chorus fruit | Ender Step: no levitation, no ender-pearl damage, pulled back from the void once every 5 min | Voidstrike: 15% chance of a +60% hit | Void Pull: drops and XP go straight to you |

- **Stronger than diamond:** armour 3/9/7/3 (diamond 3/8/6/3), toughness 3 a piece (diamond 2), +5% knockback resistance
  a piece. A full set is 22 armour and 12 toughness (diamond 20 and 8). Sword 8 damage (diamond 7), axe 10 (9), pickaxe 6,
  shovel 6.5, hoe 2. **No piece ever breaks.**
- **At home:** in its own realm a set is stronger. Its weapons hit 15% harder, and each armour piece blocks 3% of every
  hit (12% for all four).
- **Forging** (crafting table): the diamond piece goes in the middle with the realm's goods around it. It can be worn,
  enchanted, renamed or an armament, and the forged piece keeps its enchantments and its armament level and abilities.
  Realm materials and armoury pieces are refused in every other recipe (nothing is used up by mistake). Enchanting table
  and anvil books work as usual.
- **Realm materials:** Abyssal Pearls, Titan Shards and Liminal Fragments are glinting items found only in their realm.
  The Nether and the End forge with their own vanilla goods. `/gear sets` lists the sets; `/gear sets <set>` gives the
  powers and every recipe. Everything is also in the Creative catalogue and the recipe book ("Realm Armoury").

## Loot in every dimension
- **Overworld (Emerald):**
  - structure chests (HorrorBiomes, imported worldgen, Muse maps): 0.4-2.5% by difficulty;
  - vanilla loot chests (dungeons, mineshafts, temples, strongholds, villages, mansions) on first opening: 2-6%;
  - hostile mobs: 0.05%.
- **Nether (Blazeforged):** JasprNether's chests (1.5-7.5% by depth: fortress rooms, mega structures, GLM strongholds,
  colossi, vaults) and vanilla fortress chests (5%).
- **Drownhollow (Abyssal):** its loot chests on first opening (pieces 3-5%, pearls 18%).
- **Atlas (Titan):** the Dominion's stores, forges, armouries and fort hoards, the orc stashes and the ruins (never a
  free city's own larders). Pieces 1.5-7.5%, shards 12-32%.
- **Backrooms (Liminal):** every Levels chest, more the deeper it is (pieces 1.5-7.5%, fragments 12-32%).
- **End (Void):** End city chests (12%).
- **Creatures slain in each realm drop that realm's set:**
  - pieces: ordinary hostiles 0.12%, elites 1.5%, bosses 20% (Lords, Wardens, Atlas lords, Backrooms keepers, the
    Wither and the rest);
  - realm materials (Drownhollow, Atlas and the Backrooms): 5% from ordinary hostiles, 35% (1-2) from elites, 3-6 from
    bosses;
  - **the Ender Dragon always leaves a Void piece**;
  - spawner-bred creatures never drop.
- Chests only roll when they are first generated or first opened, so this applies to newly explored ground. Areas that
  are already explored still give realm gear through creature drops.
- All chances are in `plugins/JasprGear/config.yml` (`armory.drops`).

## The Armaments upgrade system (JasprRPG 1.3.0)
- **Always enhanced:** realm pieces become armaments when crafted, picked up, taken from the Creative menu or found in a
  chest (on opening). Their rarity is never below Uncommon. The armament's lines are written under the piece's own lore,
  and neither plugin overwrites the other's.
- **Realm tools level too:** pickaxes, shovels and hoes level by digging (1), ores (4), harvesting grown crops (2) and
  tilling (1). Blocks you placed never pay.
- **Tool abilities:**
  - Excavation: digs a matching neighbour too;
  - Prospecting: extra ore drops;
  - Experienced: +30% XP per level;
  - Replanting: harvested crops plant themselves again;
  - Treasure Hunter: rare gold, emerald and diamond finds.
- A tool's rarity adds "+ore yield".
- `armaments.tools` is `realm` by default; `all` extends this to every tool, `off` turns it off.

## Technical
- **Identity:** an unbreakable diamond item with NBT `JasprArmory:{set, piece}` and damage = model number (100-110, the
  odd value above each is the vanilla fallback). Armour also carries `JasprArmorySkin: 1..6`, which the browser client
  reads to draw the worn textures (client stage `scripts/build-armory-client.cjs`, one hook in renderArmorLayer, fenced
  and reversible; Mo' Bends untouched).
- **Assets:** `scripts/build-armory-pack.cjs` draws 54 icons and 12 worn layers from the archive's own diamond textures,
  writes the 57 models, and extends the nine diamond items' overrides. Run it last (after the gear pack and the
  apocalypse merge); it is idempotent.
- **Code:**
  - realm chests call `GearApi.rollRealmLoot(Random, world, tier)` from `ArmoryLoot` in JasprNether 1.3.1, JasprAtlas
    1.0.2 and JasprBackrooms 1.0.2 (softdepend JasprGear);
  - Drownhollow and vanilla chests are handled by JasprGear on first opening.
- **Logs:** `GEAR_READY ... armorySets=6 armoryPieces=54 armoryRecipes=54`, `ARMORY_FORGE`, `ARMORY_DROP`, `ARMORY_CHEST`,
  `ARMORY_RESCUE`, `ARMORY_GIVE`, `ARMORY_METRICS`; JasprRPG `armamentTools=realm` and the tool metrics in `RPG_METRICS`.
- **Admin:** `/gear armory <player|*> <set|all|abyssal_pearl|titan_shard|liminal_fragment> [piece|all]`.
- **Tests (offline):** `tests/realm-armory.test.cjs`, `tests/realm-armory-armaments.test.cjs`, `tests/armory-pack.test.cjs`,
  `tests/armory-client.test.cjs`.
