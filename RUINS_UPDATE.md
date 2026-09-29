# Drownhollow, the drowned city (JasprRuins, epoch 3)

> 2026-09-29: the Drownhollow Guide at every mossy gate hands out a compass, a checklist and a map that lead through
> the three Seals, the Great Door and the Herald (they replace the Pilgrim's Primer and the Drowned Star Compass). See
> GUIDE_KIT_UPDATE.md.

**TL;DR:** the ruins dimension is a dense cyclopean city of the Choir of the Drowned Star. There are no trees or
plains, just a little grass and ivy. Endless night, Lovecraftian horrors everywhere, five Warden bosses, and a final
boss, the Dreamer's Herald, behind the Great Door. Everyone who enters gets a guide book and a compass. Beating the
Herald pays out a hoard of unique items. The world loads only while someone is in it.

**2026-09-29 (owner): safe gates, the danger in the dungeons** ("make the spawn point ... virtually safe, and as you
venture out, it gets more and more dangerous. Most of the dangers should come from dungeons and not from random spawns
... Cap the spawn rate even more"). `Danger.java` decides where anything hostile may appear (JasprRuins 1.1.0):
- **Sanctuary:** within 48 blocks of any lit gate nothing hostile spawns (naturally, from a cage, a summoner,
  reinforcements or a jockey), strays that wander in fade away in smoke, no horror targets or hurts a player there,
  the Dread ebbs as in light, no masonry falls and no chest ambush springs.
- **The ramp:** danger grows from 0 at the sanctuary's edge to full at 448 blocks from the nearest gate.
- **Open ground** (outside the old cities' walls, the ruin sites and the Great Door, with nothing overhead): no horror
  rises within about 150 blocks of a gate; beyond that at most one per 96 blocks, two past about 310 blocks. The Dread
  only whispers there (it stops at 54: no nausea, blindness or shadows).
- **Structures** (old cities, ruin sites, the Great Door, and anything roofed or underground: houses, crypts, vaults,
  the catacombs) hold the danger, at a quarter of its strength by the gates rising to full far out: spawner cages (at
  most 2-5 horrors near a cage), natural spawns (at most 1-6 within 24 blocks), chest ambushes and falling masonry.
  Under a roof or underground the Dread is full (sickness, blindness, shadows); in a ruin's open air it stops at a
  whisper by the gates and grows to full far out. Elders appear only away from the gates (up to 6 % far out).
- **Fewer spawns:** monster cap 38 -> 20, natural spawning tried once a second instead of every tick; all spawn
  decisions run in Paper's pre-spawn event, before a creature exists.
- Half damage and half health stay as below. `/ruins danger` shows the zone and numbers where you stand
  (`RUINS_DANGER`); `RUINS_METRICS` and `/ruins status` count refusals, banishments and calmed targets.

**2026-09-28 (owner): renamed and eased.** The city was called Ul'Nhaar; it is now **Drownhollow** (easier to say).
Relics, Seals and compasses marked with the old name (Ul'Nhaar) still work. It is also half as hard, with half the spawns:
- everything hostile deals **half damage** to players (horrors, Wardens, the Herald, falling masonry, Dread, wither);
- horrors, Elders and the six bosses have **half their health**;
- **spawns halved:** monster cap 75 -> 38, spawner cages fire at half rate, Tomb Crawlers come 1-2 (not 2-3) and
  Nightgaunts singly, bosses summon half as many helpers;
- **hazards halved:** Elders 6% (not 12%), Dread rises half as fast and its damage is halved, shadows half as often,
  falling masonry half as often, chest ambushes 15% with 1-2 horrors (not 30% with 2-4), bite effects half as long.
The numbers below are the design before this change; `RuinsPlugin.EASE` (0.5) scales them.

**Epoch 3 (regenerated again) adds:**
- **Catacombs under everything:** vaulted rooms joined by corridors with dart traps, reached through gates in the field.
- **Six greater ruins:** the Bastion of the Choir (fortress), the Labyrinth of Angles, the Ossuary Temple, the Temple
  of the Deep, the Star-Watcher's Spire and the Great Idol of Ythaqqua.
- **Denser land:** more sites and five new field monuments (shrine temples, catacomb gates, faceless watchers,
  obelisk groves, gibbets).
- **More dangers:** Elder horrors, chest ambushes, shadows that answer great fear, falling masonry, and a higher
  monster cap.
- **An eerie sky:** a sick green sky, a blood-red moon and stars, and a mist that turns slowly from corpse-green to
  blood-red and back. Pale spores, distant wails and silent lightning are sent by the server.

## Getting there
- Build a nether-portal-shaped frame of **mossy cobblestone** (at least 4×5) and light it with flint and steel.
- Stand in the portal for 3 s. Gates to Drownhollow glow **green**; obsidian Nether portals stay purple (client
  `20260927-portal1`).

**Gates are linked in pairs:**
- Going back through the gate you arrived by returns you to the gate you left from, even when two gates share one Ruins
  gate.
- Otherwise a gate goes to its partner (its first live link, kept in `portals.yml`). An unlinked gate links to the
  nearest mossy gate within 48 blocks of the same x/z, or builds one.
- You arrive beside the gate, stepping down up to 3 blocks to find floor, facing away from it. You arrive inside only
  if both sides are walled in, and then the gate will not fire again until you step out (no ping-pong).
- A vanilla Nether trip that lands inside a mossy gate is moved out in front of it (`RUINS_PORTAL_STRAY_ARRIVAL`).
- **Fixed on 2026-09-27:** gates now detect your body touching the portal, as vanilla does, not only your feet block. Before this, stopping at the edge of a green gate (swirl already on screen) left your feet outside it: the gate never fired and vanilla took you to the Nether after 4 s. Vanilla travel is cancelled for anyone touching a mossy gate (`RUINS_PORTAL_VANILLA_BLOCKED`), and a lit mossy portal missing from the registry is adopted (`RUINS_PORTAL_ADOPTED`).

**2026-09-27:** trips from jasper_e_'s green gate went to the Nether when stopping at the gate's edge (fixed, see below). The obsidian Nether portal three blocks away is unrelated.

## How to beat it (the Pilgrim's Primer)

Everyone who enters is handed the **Pilgrim's Primer** and a **Drowned Star Compass**. They are given again if lost.

1. **Survive.** Standing in darkness without a light raises your **Dread**: whispers, then nausea, then
   weakness and slowness, then blindness and damage. Holding a torch (or standing in light) clears it. Beds do not work.
2. **Follow the compass.** It points to the nearest Warden whose Seal you lack. With three Seals it points to the
   Great Door.
3. **Slay three of the five Wardens.** Each drops its **Seal**:

   | Warden | Mob | Arena | Seal |
   |---|---|---|---|
   | Hierophant | evoker | Sanctum of the Drowned Star | Tides |
   | Pillar Warden | wither skeleton | Circle of the Watchers | Stone |
   | Brood Mother | spider | Pit of Offerings | Hunger |
   | Spawn of the Deep | giant slime | Spawning Pool | the Deep |
   | Faceless Priest | illusioner | Chapel of the Faceless | Silence |

4. **Open the Great Door.** Its coordinates are written in the guide. Right-click the Door with three **different** Seals.
5. **Slay the Dreamer's Herald**, a giant with 1600 HP in three phases.
   - Its attacks: tentacle rifts (evoker fangs), a stomp, a blinding and withering gaze, and calling the Deep.
   - Near death: sky-fall lightning and levitation.
   - Everyone within 64 blocks receives the hoard.
   - The Door reseals 3 minutes later and can be reopened after 45 minutes.

## Rewards and trinkets

**The Herald's hoard:**
- Crown of the Drowned Star: diamond helmet, Protection IV. Grants water breathing and Dread immunity.
- Herald's Cleaver: Sharpness V, Looting III, Fire Aspect II.
- Wings of the Nightgaunt: elytra with Mending.
- Dreamer's Heart: a nether star.
- Idol of the Dreamer and the Faceless Mask.
- 2 totems, 3 enchanted golden apples, 12 diamonds, the book *After the Waking*, and 3000 XP.

**Wardens:**
- Each drops its Seal, a random relic, a lore book, diamonds and golden apples, plus 600 XP.
- The Hierophant also drops a totem.
- Each respawns 30 minutes after it is killed.

**Relics.** These are found only in Drownhollow: on horrors (1.5%, Elders 8%), in cult and catacomb chests, from Wardens
and from the Herald.
They work in any world:

| Relic | Effect |
|---|---|
| Choir Wardstone | Dread rises at half speed and never sickens you |
| Pearl of the Drowned | water breathing |
| Tentacle Charm | off hand: regeneration below half health |
| Star-Metal Shard | off hand: Strength |
| Nightgaunt Pinion | no fall damage |
| Ghoul's Tooth | off hand: heals you by 20% of the damage you deal |
| Mi-Go Brain Cylinder | right-click to blink 8 blocks (12 s recharge) |
| Faceless Mask | worn: horrors ignore you until you strike one |
| Idol of the Dreamer | Resistance and Dread immunity |

## Horrors

It is always night here, and every natural or spawner monster becomes a horror. No animals spawn.

| Horror | Mob | Traits |
|---|---|---|
| Deep One | green-scaled zombie | fast in water, slows you |
| Ghoul | husk | hunger and weakness |
| Shoggoth | slime | regenerates, splits |
| Nightgaunt | vex | flies through walls, lifts you |
| Mi-Go | enderman | always hunting, blinds you |
| Hound of Tindalos | angry wolf | teleports out of corners behind you, wither bite |
| Star-Spawn Thrall | wither skeleton | wither |
| Cult Zealot | vindicator | heavy hits |
| Cult Adept | evoker | fangs and vexes |
| Tomb Crawler | cave spider | comes in swarms |

All horrors hit harder and have more health than vanilla mobs. They keep full speed during overworld daytime:
JasprDaylight skips mobs tagged `jaspr_daylight_exempt`.

**Other dangers (epoch 3):**
- **Elders:** 12% of horrors rise as an Elder, wreathed in faint purple motes. They have 1.8× health, 1.4× damage,
  armour and a little more speed, and give double XP. The motes are particles, not a glowing effect.
- **Ambushes:** the first time a chest in Drownhollow is opened, there is a 30% chance that 2–4 horrors rise around it.
- **Shadows:** at Dread 90 or higher, a Nightgaunt may appear behind you (at most every 20 s).
- **Falling masonry:** about once a minute, anyone out in the ruins may hear stone crack, then be hit by blocks
  falling from above a second later. The blocks never land, so they leave no mess.
- **Dart traps:** pressure plates in catacomb corridors and treasure rooms fire arrows from hidden floor dispensers.
- **Spawn cap:** 75 monsters, half of epoch 3's first 150 after playtesting ("a little too aggressive").

## The world
- **Ruin field:** every 24-block cell that no city, site, Lost City or the Door claims holds one monument:
  - leaning giant pillars (up to about 80 blocks tall), obelisks, pillar gates, cyclopean walls, stairs to nowhere;
  - sunken plazas, arches, idols of the Dreamer, cult altars, spire clusters, colonnades, fallen cyclopean blocks;
  - shrine temples, catacomb gates, faceless watchers, obelisk groves and rows of gibbets (new in epoch 3);
  - two or three lesser remnants in its corners;
  - cracked paving strewn with rubble, bones and skulls. Pillars rise from the water too.
- **Greater ruins (sites, one per 80-block grid square at 85%):**
  - **Bastion of the Choir:** walls, four towers and a two-floor keep with spawners, prison cells, a dart trap and
    offerings.
  - **Labyrinth of Angles:** a 9×9 maze of 4-block corridors. Its dead ends hold spawners; its heart holds a relic
    chest.
  - **Ossuary Temple:** walls and pillars of bone, a frieze of skulls, an altar and its keeper.
  - **Temple of the Deep:** a prismarine temple in its own drowned court, flooded to the knees inside.
  - **Star-Watcher's Spire:** a banded tower crowned with an armillary ring, with an offering at the top.
  - **Great Idol of Ythaqqua:** the Dreamer, three times the size of the wayside idols, on a stepped dais.
- **Catacombs:** a node sits under nearly every chunk, at the district's depth (24 below the lowest ground around).
  - Nodes are crossings or vaulted rooms: crypts, treasure rooms, flooded rooms, lava rooms, shrines, bone pits and
    prisons.
  - Corridors join neighbours in the same 128-block district.
  - Catacomb gates in the field (a stone porch, a ladder shaft, a passage) lead down. A shaft that lands inside a room
    comes down its ladder pillar.
  - Chests, spawners, signs and trap dispensers are never overwritten by later building: the first tile placed on a
    spot keeps it.
- **Earlier content, kept:**
  - the ten original wilderness ruins;
  - old cities, now with dead gardens of petrified trees and cult shrines instead of meadows;
  - the weathered Lost Cities, with leaves, flowers and their vine pass removed.
- **Lore:** ten lore books in cult chests and on slain horrors, and carved chant signs on the monuments.
- **Offline check** (`tests/ruins.test.cjs`, 384×384 sample):
  - no trees, leaves, flowers or mushrooms;
  - grass on 2.4% of columns;
  - at least 64% of columns clearly built on (plain gravel and cobble paving not counted);
  - all 21 site kinds appear;
  - 505 catacomb rooms in 25×25 chunks, 93% of them hollow at depth; 11 gates in the field; 300 dart traps, each with
    its plate;
  - every chest, spawner, sign and dispenser the populator recomputes matches the generated block;
  - 0.46 ms per chunk.

## The sky
- **Server side** (`Sky.java`):
  - Players in Drownhollow receive a hidden scoreboard objective `jrs` ("JRS v1 eerie"); it is removed when they leave.
  - Every 2 s, each player there gets pale spores (`TOWN_AURA`, `SUSPENDED_DEPTH`) and, now and then, a distant
    wail.
  - Now and then, silent lightning flashes somewhere out in the ruins.
- **Client side** (`scripts/build-sky-client.cjs`, five fenced `/*JASPR_SKY_V1*/` hooks in `site/classes.js`,
  client `20260927-sky1`): while the objective is present:
  - the sky dome is sick green;
  - the moon, sun and stars are blood red;
  - fog and the backdrop blend into a mist that turns from corpse-green to blood-red and back (about 40 s);
  - below render distance 4 no sky dome is drawn, so the whole backdrop is that mist.

  Everywhere else the sky is untouched. Nothing new is downloaded; the hooks ship inside the normal client.
- **Diagnostics:** `JasprSkyDiagnostics.status()` in the browser returns `{on, failure, mist}`.

## Server resources
- **Unused, it stays on disk.** The world loads when someone enters (portal, `/ruins tp`, or logging in where they
  left off): 50–220 ms.
- **Empty, it unloads.** 60 s after its last player leaves it is saved and fully unloaded (0 chunks, 0 entities, about
  20 ms). Its horror, dread and boss tasks then return immediately. The Lost Cities detach and re-attach cleanly.
- **Browser:** all visuals are vanilla blocks and mobs, so there is nothing extra to download. Relics are vanilla items
  with names, so they display fine to players who have never visited. The sky is a few kilobytes inside the client.
- **Regeneration:** this update (epoch 3) renames the old world folder to `jaspr_ruins-retired-epoch2-<time>` (nothing
  is deleted) and forgets its portals. Players who logged out inside it wake at the overworld spawn.
- **Epoch 3 fixture** (lean headless browser, seed 305441741): about 3 minutes in survival rose 10 horrors, including
  4 Elders. It also produced 2 chest ambushes, 2 shadows, 1 masonry fall and 6 traps populated, with no generation
  failures.

## Portal colours (client)
`scripts/build-portal-client.cjs` installs four fenced `/*JASPR_PORTAL_V1*/` hooks in `site/classes.js` and
changes `site/assets.epk`:
- `portal.png` is replaced by a neutral grey animation (`server/custom-plugins/JasprRuins/pack/portal-neutral.png`),
  and the two portal models get `tintindex 0`.
- The block colour hook walks down each portal column to its frame block: mossy cobblestone gives green, anything else
  gives the vanilla purple multiplier (a least-squares fit of the original animation).
- Portal particles and the in-portal screen overlay follow the same colour.
- Future gate kinds add a frame-block → colour entry to `FRAMES`.
- **Diagnostics:** `JasprPortalDiagnostics.status()` returns `{coloured, purple, failure}`.

## Owner tools
`/ruins [status|tp|back|where|find <city|lostcity|kind>|door [open|close]|guide|lore|seal [type]|trinket [type]|boss <type>|horror <kind>|dread|danger|unload]`

## Logs
`RUINS_READY`, `RUINS_WORLD_LOADED`, `RUINS_WORLD_UNLOADED`, `RUINS_REGENERATED`, `RUINS_WELCOME`, `RUINS_BOSS_WOKE`,
`RUINS_BOSS_SLEPT`, `RUINS_WARDEN_SLAIN`, `RUINS_HERALD_SLAIN`, `RUINS_DOOR_SEAL`, `RUINS_DOOR_OPENED`,
`RUINS_DOOR_SEALED`, `RUINS_PORTAL_*`, `RUINS_TRAVEL`, and `RUINS_METRICS` on shutdown. In epoch 3,
`RUINS_METRICS` also counts `elders`, `ambushes`, `shadows`, `crumbles`, `traps`, `skyFlashes`, `portalsLinked` and
`strayArrivals`; since 2026-09-29 also `sanctuaryRefused`, `wildAllowed/Refused`, `structureAllowed/Refused`,
`cageAllowed/Refused`, `banished` and `calmed`. `RUINS_WORLD_LOADED` gives `monsterCap`, `spawnTicks`, `sanctuary`,
`fullDanger` and `gates`; `/ruins danger` logs `RUINS_DANGER`. `RUINS_TRAVEL` gives the route: `return`, `link`, `nearest` or `built`. The Lost
Cities log `LOST_CITIES_DETACHED`.
