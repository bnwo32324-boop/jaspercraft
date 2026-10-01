# The colossal structures and the Endless Catacombs (JasprNether 1.3.0) -- 2026-10-01

The owner asked: "Add three new mega structures to the Nether. Each one should have a boss. One should be a giant
pyramid. One should be a giant fire fortress inspired by Avatar: The Last Airbender. One should be an ancient,
sprawling dungeon that goes on for miles. These are all megastructures that should be huge in scope. Add loot,
enemies, different dangers, puzzles, and traps." and then "don't forget about loot and mini-bosses".

## Where they stand, and what they leave alone
- **No regeneration, nothing moved.** `REGEN_EPOCH` stays 4. Every existing city, mega structure, GLM build and wonder
  keeps its place and its decision.
- **Only on new land.** At the first start of 1.3.0 the plugin records which Nether chunks already exist (the region
  files' tables, plus loaded chunks) in `plugins/JasprNether/data/world_nether/colossi-history.txt`. The new structures
  are never drawn into those chunks, so nothing already explored changes; they appear as players reach new land.
- **The colossi** (Great Pyramid, Caldera Citadel): one site per 1,152-block cell, the two kinds alternating like a
  chessboard, each in its own cavern 368 blocks across (floor y 32, dome up to y 120). A site is built only when none of
  its land is old and no recorded structure lies in its box (decided once, kept in the registry as `colossuscell`); a
  mega structure or GLM build planned there later gives way to it. The mods' small structures stay out of its reach.
- **The Catacombs** lie under everything (floor y 9, ceiling at most y 22; the colossi keep y 23 and up): one labyrinth
  3,073 blocks square (almost two miles a side) around its Heart, chosen once on new land 1,300-3,300 blocks from the
  origin and kept in `data/world_nether/catacombs.txt`. 64-block sectors holding old land are left solid, as are cells
  under a structure that reaches that deep; a GLM build that would cut into the Heart or a Warden's vault gives way.

## The Great Pyramid (Lord: the Sunless Pharaoh)
A sand cavern. An avenue of sphinxes leads to the north stair; beside it the Great Sphinx on its plaza (the **Sphinx
Sentinel** keeps the Jar of Duamutef), three queens' pyramids, corner obelisks, a mortuary court. The pyramid itself is
176 blocks wide and 84 high: the Ka hall; the **Labyrinth** (a 72-block maze of trapped corridors: arrows, floors over
pits, falling rubble, flame vents, gas) with fourteen burial rooms (sarcophagi, canopic niches, mummies; some chests
trapped); under its heart the **Scarab Pit** (the **Scarab Matriarch**, Jar of Qebehsenuef); the **Queen's Chamber**
(**Vizier Hekkat**, Jar of Imsety); the **Hall of Stars** (a lever puzzle; the signs tell the order; it opens a niche and
gives the Jar of Hapy); the **Grand Gallery** (arrow slits); the antechamber's **Canopic Seal** (opens for one who
carries all four jars); the **King's Chamber** (the **Sunless Pharaoh**); his treasury (opens when he falls); three
relieving chambers and, at the apex, the hidden **Sun Chamber**.

## The Caldera Citadel (Lord: the Ember Sovereign), after the Fire Nation
A volcano's caldera. A spiral road climbs the cone past guard towers with **fire cannons**; the rim wall has eight
towers and the North Rim Gate. Inside: lantern-lit streets, gardens with four shrines, noble houses (pagoda roofs, some
chests trapped), the **duelling ring** (the **Blazing Admiral**, Seal of the Admiral), the **Sun Temple** (a four-tier
pagoda: light its four braziers in the order the shrines tell and the sealed stair opens to the **High Fire Sage**,
Seal of the Sun), and the **palace** on its podium: the throne hall behind the **Throne Gate** (three seals), the wall
of fire, the **Ember Sovereign** on his dais, the royal treasury (opens when he falls), the royal quarters and the war
room. Under the city: the Lava Gate tunnel, the bridge over the lava lake to the **Boiling Keep** (six floors of cells;
the **Warden of the Boiling Keep**, Seal of the Warden) and, up its shaft, the Dragon Bone vault.

## The Endless Catacombs (Lord: the Hollow King)
A labyrinth on a 12-block grid with highways every 192 blocks; stairwells climb to ruined gatehouses on the surface
(about every 156 blocks; capped where a structure stands overhead). Rooms: crypts, store rooms, prisons, libraries,
shrines, gas-filled tombs, flame galleries, lava-channel halls, false floors (only the smooth stones bear weight), lever
puzzles (the sign tells the order; the prize is a **Hollow Reliquary**); pillared halls; trapped corridors. Deeper in,
the Old Halls (stone) turn to the Ember Halls (nether brick) and the Black Halls, and the loot and the dead grow
stronger. Four **Wardens' vaults** lie 480 blocks out on the axes, each with its champion and key: the Gaol (the
**Gaoler**), the Bone Harrow (the **Bone Harrower**), the Weeping Gallery (the **Weeping Shade**), the Rot Pits (the
**Rot Mother**). The **Heart**: its gates, the processional ring, the old kings' tombs, four inner gates (all four
keys), the **Hollow King's** throne hall and the treasury behind the throne (opens when he falls).

## Bosses
- **Three Lords** (count towards the Urn of Sorrow: any three of the thirteen Lords): the Sunless Pharaoh (sandstorms,
  withering curse, blinks, scarab swarms, an eclipse that blinds and raises mummies), the Ember Sovereign (fire whip,
  flame dash, rings of fire, falling fire in his last phase, and **lightning he gathers for two seconds**: get out of his
  sight or be struck), the Hollow King (drains life, dread, lost souls, a soul nova). Each always leaves its relic, two
  rolls of its structure's treasure, shards, amethyst and gold, and its sigil to everyone who conquered it.
- **Ten champions (mini-bosses)**: Sphinx Sentinel (slam, sandstorm, a gaze that makes you reel), Vizier Hekkat (witch:
  potions, hex, asps, blinks away), Scarab Matriarch (brood, webs, burrows), High Fire Sage (blaze: volleys, flame nova),
  Blazing Admiral (fire whip, dash, ring of fire), Warden of the Boiling Keep (chains, slam, scalding steam), Gaoler
  (chains, slam, crypt guards), Bone Harrower (bone storms), Weeping Shade (illusioner: blindness, mirror images, wails,
  lost souls), Rot Mother (a great slime: crushing landings, rot). Each hands its **key** to everyone who fought it
  (keys are never used up) and drops a share of its structure's treasure. No Lord's credit.
- These bosses **wake only for a player within reach who can see their place** (never behind a wall), keep to their
  hall, rest 15 minutes after falling, and harm no block.

## Creatures (Dwellers.java)
Mummy (husk: binds and starves), Asp (silverfish: venom), Scarab (endermite: swarms), Tomb Guardian (gilded skeleton),
Ember Legionnaire (jabs fire), Flame Adept (hurls fire, bursts up close), Royal Guard (lunges), Crypt Guard (mail-clad
skeleton: chills), Deep Crawler (spider: pounces, poison and cramp), Lost Soul (a burning spirit). They never change a
block: asps hide in no stone, no door is broken, no vanilla zombie is called, their fire sets nothing alight. They drop
the Nether materials of the GLM gear (shards, embers, essences, charred bone, brimstone) and gold.

## Dangers, puzzles and traps (Ordeals.java)
Arrows from wall slits, floors that fall away over pits (they close again, never over someone in the pit), falling
rubble, flame vents on a rhythm (smoke warns), poison gas, fireball cannons (they need line of sight), floors of tiles
where only the smooth stones bear weight; lever and brazier puzzles (wrong: the room strikes back; right: the way opens
and the prize is given, once in ten minutes per player); key seals and boss seals. Opened seals close after a while
(never on a player) and on shutdown. Seals, niches and puzzle parts cannot be broken or blown up, and nothing can be
placed against a seal.

## Loot
Fourteen new tables (`jaspr:colossus/*`, `jaspr:depths/*`), graded from the dead's leavings to the treasuries. Every
treasury and Warden's vault holds a piece of its structure's armour. New items: the keys (four Canopic Jars, three
Seals, four Wardens' Keys), the Hollow Reliquary, the Khopesh (poisons), the Dao of the Fire Nation (burns, quickens),
the Bone Reaver (weakens), the Scarab Amulet (off hand: no poison), the Phoenix Feather (carried: rise in flame when
near death, once in five minutes), three armour sets (Pharaoh's: no hunger or blindness; Ember Guard: no fire;
Deepwarden: no wither or poison), the relics (Crook of the Sunless Pharaoh: blinds and slows; Sovereign's Flame:
right-click lashes a whip of fire; Hollow Crown: drinks life, no wither) and three sigils.

## Tests and tools
- `node --test tests/nether-colossi.test.cjs` compiles the plugin with `tests/java/chat/jaspr/nether/ColossusPreview.java`:
  both colossi and three Catacombs windows drawn chunk by chunk on synthetic terrain (deterministic, no forbidden or
  valuable block, every boss's place, stocked, cheap, no lava left touching the halls) and the whole labyrinth's map
  walked from the Heart (96 % connected, the four vaults reached); then the wiring and the safety rules.
  `ColossusPreview <outDir> blocks.tsv` also renders PNGs.
- In-game (test servers only, `-Djaspr.nether.selftest=true`): generates the nearest pyramid and citadel and the
  Heart, checks chests, bosses' places, garrisons, signs, every seal's and puzzle's blocks against the runtime's layout
  and a treasury opening and closing; then raises and slays all 23 bosses.
- Admin: `/jnether goto great_pyramid|caldera_citadel|catacombs|heart|warden|<boss id>|ordeal:<id>` (ordeal ids:
  `pyramid_canopic`, `pyramid_stars`, `pyramid_treasury`, `citadel_throne`, `citadel_rite`, `citadel_treasury`,
  `depths_heart`, `depths_treasury`, `depths_levers`, `arrows`, `flames`, `gas`, `cannon`, `collapse`, `rubble`, `path`),
  `/jnether lord <id>`, `/jnether give <item>`; `/jnether status` shows the ordeals' and treasures' counters and the
  Catacombs' Heart; `/where` names the structures and the Catacombs' halls.
- Logs: `NETHER_COLOSSI_HISTORY`, `NETHER_CATACOMBS`, `NETHER_COLOSSUS_PLANNED`, `NETHER_LORD_RISEN ... champion=`,
  `NETHER_CHAMPION_DEFEATED`, `NETHER_SEAL_OPENED`, `NETHER_PUZZLE_SOLVED/FAILED`, `NETHER_AMBUSH`,
  `NETHER_RELIQUARY_OPENED`, `NETHER_PHOENIX_ROSE`, `NETHER_ORDEAL_FAILED`; generation cost in `NETHER_GEN_STATS`
  (`colossus=` and `depths=` phases).
