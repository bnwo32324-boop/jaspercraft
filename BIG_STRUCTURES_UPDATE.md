# Big structures and variety (2026-10-04/05)

Owner, 2026-10-04: "Add 10 new structures to the Nether. I want them each to be unique, and there should be all kinds of
Nether mobs in them, and they should each have a boss. I want them to be huge structures. All this update is just about
big, big, big structures. In the Drown Hollow dimension, make 20 new big structures there as well. They all should be
unique and have bosses, and they should include all types of mobs and custom mobs."

Owner, 2026-10-05: "There's also a problem in the Nether with a lack of variety. I feel like this is a problem in the
Overworld too. I keep seeing the same structures over and over again, and I don't encounter the big structures or unique
ones a lot in general. Try to fix this."

| Plugin | Version | World |
| --- | --- | --- |
| JasprNether | 1.5.0 | Nether regenerated (REGEN_EPOCH 7; the old one moved to `plugins/JasprNether/nether-before-v7/`) |
| JasprRuins | 1.4.0 | Drownhollow regenerated (EPOCH 6; the old world renamed `jaspr_ruins-retired-epoch5-*`) |
| JasprHorrorBiomes | 3.30.0 | Overworld NOT regenerated: new ground only, nothing built is moved |

## Nether: ten new colossi

Each hollows its own cavern (up to ~350 blocks across), has its own Lord (a boss with its own powers, relic, sigil and
hoard; every one counts towards the Urn of Sorrow), a vault that opens when its Lord falls, traps, 16+ chests, spawners,
signs, and garrisons holding every hostile creature of the Nether (41 kinds: vanilla, NetherEx, the GLM creatures and the
colossi's dwellers; checked per structure by `ColossusPreview`).

| Colossus | Lord | Shape |
| --- | --- | --- |
| The Maw of the Abyss | Abyssal Gatekeeper (giant) | a demon-faced monolith with horn towers; the Gullet leads to the Abyssal Pit |
| The Ashen Spire | Spire Archon (blaze) | a twisting tower to the roof with satellite towers; the Archon's Crown on top |
| The Leviathan's Bones | Marrow Wyrm (dragon) | a titan serpent's skeleton: skull hall, spine walk, rib vaults, shanty town |
| The Infernal Colosseum | Undying Gladiator | a 220-block amphitheatre with hypogeum, beast pens and the Emperor's Box |
| The Hanging Citadel | Chained Titan (golem) | a fortress hung from the roof by four chains over a lava lake |
| The Sporefather's Hive | Sporefather | a city of giant mushrooms joined by fungal bridges round a puffball hive |
| The Rime Bastion | Rime Lich | a five-pointed ice star fort with a domed keep and a frozen lavafall |
| The Palace of the Burning Throne | Burning King | a terraced acropolis; a 44-block statue of the King behind his throne |
| The Amethyst Sanctum | Amethyst Oracle (enderman) | a colossal crystal geode with spires and floating crystal islands |
| The Coil of the World Serpent | Serpent Queen (spider) | a stone serpent coiled three times round a magma pillar; her lair in its head |

Placement: the colossi stand on an 896-block grid of fourteen slots (the Great Pyramid and the Caldera Citadel two each,
each new kind one; any four cells in a row or three in a column are all different kinds): 1.8x as many colossi as before.
The GLM builds and mega structures now plan their spots around the colossi instead of giving way after planning; on three
seeds they keep 99% (GLM) and 75% (megas) of their sites (a 640-block grid would have kept 80% and 42%).

## Drownhollow: twenty great structures

One candidate per 256-block cell (about 7 in 10 cells get one), each up to ~110 blocks across, with its own keeper (a boss
with three powers and its own horror to call), 12+ chests, spawners, signs and garrisons holding all ten horrors and all
seventeen overworld monsters (never rising inside a gate's sanctuary; a garrison creeper never blasts the stone). Kinds
follow a 20-slot lattice; four stand in the shallows or on shores.

The Necropolis of the Ghoul-Kings, the Drowned Cathedral, the Fallen Sleeper, the Tower of Silent Stars, the Shoggoth
Vats, the Dreadnought, the Hive of the Mi-Go, the Angles of Tindalos, the Temple of the Black Goat, the Beacon of R'lyeh,
the Viaduct of the Drowned Kings, the Library of Celaeno, the Terraces of the Drowned Queen, Y'ha-nthlei Bastion of the
Deep, the Gate of the Silver Key, the Monastery of Leng, the Vault of the Elder Sign, the Weeping Cistern, the Orrery of
Aeons, Golgotha the Skull Keep.

The ruins and the field keep clear of them; the field's lesser ruins weigh about twice as much so the density the owner
asked for on 2026-10-04 holds (`RuinsPreview` against epoch 4: structures 2.02x, dungeons 2.38x, catacomb rooms 2.23x).

## Variety

Live data (`plugins/JasprNether/data/world_nether/placed.txt` since the epoch-6 regeneration): BetterNether's one cave
room was 22 of its 47 small structures, two wonder kinds were 44 of 45 wonders, the Catacombs used one guard pack, and
only 3 megas, 2 colossi and 7 GLM builds had been reached. In the overworld the catalogue -- its 234 big, unique designs --
was banned within 3,072 blocks of spawn, beyond everything explored, so nobody had met one; tier 2 (3.29.0) doubled the
same per-biome set pieces and rooms one for one.

- **Nether:** wonders at half the 3x chance (1.5x the original), any kind in any region (its own weighted first), never
  the same kind in a neighbouring chunk; the cave room in a third of the spots it took; Catacomb packs from five to seven
  per depth; ten more colossus kinds, 1.8x as many colossi; colossi and great GLM builds on the guide map.
- **Overworld (3.30.0, new ground only):** catalogue sites may stand from 512 blocks out where none of their ground existed
  when 3.30.0 first started (`jaspr-rates-v3.boundary`, fail closed): within 2.5 km of spawn the plan goes from 6 to 77
  sites on fresh ground; the third tier-2 catalogue grid at full density; tier 2 keeps only 40% of its not yet decided
  extra copies of the commonest surface kinds (The Signal, eight landmarks, five surface rooms). Everything built keeps
  its place and recognition (older layers' fingerprints unmoved, `tests/structure-density.test.cjs`).

## Tests and tools

`tests/nether-colossi.test.cjs`, `tests/nether-mega.test.cjs`, `tests/ruins.test.cjs` (with `GreatPreview`),
`tests/overworld-variety.test.cjs`, `tests/structure-density.test.cjs`. `ColossusPreview -Donly=<kind ids> <png dir>` and
`GreatPreview -Donly=<KINDS> <png dir>` render any structure offline.
