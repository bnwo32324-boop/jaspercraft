# Ul'Nhaar, the drowned city (JasprRuins, epoch 2)

**TL;DR:** the ruins dimension is regenerated as a dense cyclopean city of the Choir of the Drowned Star. There are no
trees or plains, just a little grass and ivy. Endless night, Lovecraftian horrors everywhere, five Warden bosses, and a
final boss, the Dreamer's Herald, behind the Great Door. Everyone who enters gets a guide book and a compass. Beating
the Herald pays out a hoard of unique items. The world loads only while someone is in it.

## Getting there
- Build a nether-portal-shaped frame of **mossy cobblestone** (at least 4×5) and light it with flint and steel.
- Stand in the portal for 3 s.

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

**Relics.** These are found only in Ul'Nhaar: on horrors (1.5%), in cult chests, from Wardens and from the Herald.
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

## The world
- **Ruin field:** every 24-block cell that no city, site, Lost City or the Door claims holds one monument:
  - leaning giant pillars (up to about 80 blocks tall), obelisks, pillar gates, cyclopean walls, stairs to nowhere;
  - sunken plazas, arches, idols of the Dreamer, cult altars, spire clusters, colonnades, fallen cyclopean blocks;
  - two or three lesser remnants in its corners;
  - cracked paving strewn with rubble, bones and skulls. Pillars rise from the water too.
- **Earlier content, kept:**
  - the ten original wilderness ruins;
  - old cities, now with dead gardens of petrified trees and cult shrines instead of meadows;
  - the weathered Lost Cities, with leaves, flowers and their vine pass removed.
- **Lore:** ten lore books in cult chests and on slain horrors, and carved chant signs on the monuments.
- **Offline check** (`tests/ruins.test.cjs`, 384×384 sample):
  - no trees, leaves, flowers or mushrooms;
  - grass on 2.4% of columns;
  - at least 64% of columns clearly built on (plain gravel and cobble paving not counted);
  - 0.5 ms per chunk.

## Server resources
- **Unused, it stays on disk.** The world loads when someone enters (portal, `/ruins tp`, or logging in where they
  left off): 50–220 ms.
- **Empty, it unloads.** 60 s after its last player leaves it is saved and fully unloaded (0 chunks, 0 entities, about
  20 ms). Its horror, dread and boss tasks then return immediately. The Lost Cities detach and re-attach cleanly.
- **Browser:** all visuals are vanilla blocks and mobs, so there is nothing extra to download. Relics are vanilla items
  with names, so they display fine to players who have never visited.
- **Regeneration:** this update (epoch 2) renames the old world folder to `jaspr_ruins-retired-epoch1-<time>` (nothing
  is deleted) and forgets its portals. Players who logged out inside it wake at the overworld spawn.

## Owner tools
`/ruins [status|tp|back|where|find <city|lostcity|kind>|door [open|close]|guide|lore|seal [type]|trinket [type]|boss <type>|horror <kind>|dread|unload]`

## Logs
`RUINS_READY`, `RUINS_WORLD_LOADED`, `RUINS_WORLD_UNLOADED`, `RUINS_REGENERATED`, `RUINS_WELCOME`, `RUINS_BOSS_WOKE`,
`RUINS_BOSS_SLEPT`, `RUINS_WARDEN_SLAIN`, `RUINS_HERALD_SLAIN`, `RUINS_DOOR_SEAL`, `RUINS_DOOR_OPENED`,
`RUINS_DOOR_SEALED`, `RUINS_PORTAL_*`, `RUINS_TRAVEL`, `RUINS_METRICS` (on shutdown). The Lost Cities log
`LOST_CITIES_DETACHED`.
