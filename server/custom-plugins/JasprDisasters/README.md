# JasprDisasters

Natural disasters for JasperCraft. Exactly one disaster runs at a time, server-wide, and it always
happens to exactly one player. That is the whole cost model: no matter how many people are online,
the server is only ever paying for one bounded event near one player.

Five disasters ship today: the meteor shower, the thunder-hell storm, and since 1.3.0 the earthquake,
the tornado and the blizzard. They share one timer: after each disaster the next one is due somewhere
between 2 and 13 Minecraft days later (a Minecraft day is 20 real minutes; 8 days on average), and its
kind is drawn at random from the enabled kinds, never the same kind twice in a row. A cooldown after
each event stops them chaining.

That is twice as rare as before 1.3.0, when each of the two kinds kept its own 1-14 day timer, which
added up to a disaster every 4 days or so (tests/java/chat/jaspr/disasters/DisasterScheduleTest measures
both). Because the timer is shared, adding kinds adds variety, not frequency.

## Where is safe? Only an obsidian bunker

Since 1.4.0 the three newest disasters (earthquake, tornado, blizzard) reach you anywhere: deep in a mine,
up on a sky platform, inside your base. The one place they cannot reach is an **obsidian bunker**:

* obsidian under you, over your head, and on all four sides at both feet and head height, each within 12
  blocks of you (so a room up to about 23 blocks across counts);
* an iron door or iron trapdoor is fine as the way in; a wooden door, glass or a plank wall is a gap;
* what is inside does not matter: chests, beds, furnaces, torches, signs, carpets are all fine.

Sealed in, no quake jolt, tornado wind or blizzard cold reaches you, no rock falls from an obsidian
ceiling, and these disasters never break obsidian or the iron door in it. Bedrock and barrier blocks count
as obsidian. The meteor shower and the thunder-hell storm work as before (shelter from them is up to you).

Builds: in the worlds listed in `build-damage-worlds` (the overworld by default) the quake cracks floors and
shakes blocks out of roofs and ceilings, and the tornado tears through roofs, walls and trees, built blocks
dropping as items for the wind to scatter. Everywhere else the two only split and tear up natural ground, so
the realms' own structures are kept. Obsidian, utility blocks and portals are never touched anywhere (see
below).

## Meteor shower — `/shower`

Twenty rocks fall out of the sky around the target over about half a minute, anywhere inside a 28
block radius. Each one detonates on impact with a creeper-grade blast, scattering magma blocks and
fire around the crater. A meteor that
never reports a landing, because it despawned or its chunk unloaded, still detonates on a timeout so
the shower can never hang.

## Thunder-hell storm — `/lightning`

The world gets real weather: cloud cover and rain everywhere, because Minecraft weather is
world-wide and that is exactly the clouds-for-miles effect the storm wants. Everything violent stays
local to the target.

* A bolt lands roughly every two and a half seconds, anywhere inside a 40 block radius that follows
  the target as they run.
* Every fourth ordinary bolt is a **hunter bolt**: it strikes where the target stood a second ago.
  Standing still during the storm is what gets you hit. Keeping moving is what saves you.
* Every fifth bolt is a **hell bolt**: three flashes down the same channel, a TNT-grade blast, and a
  crater scooped out of the ground and left burning on a netherrack floor, ringed with magma.
  Anything inside the blast radius is set alight and withered; a hell bolt landing close to the
  target blinds and sickens them.
* Ordinary bolts leave scorch fires, the horizon flashes with sheet lightning, ash and embers fall
  out of the cloud deck, and the target glows for the duration so everyone can see who the storm is
  hunting.
* The world's previous weather is captured on the way in and handed back on the way out, including
  when the plugin is disabled mid-storm, so a storm never leaves the world permanently raining.
  Running `/weather clear` mid-storm does not call it off.

## Earthquake — `/quake`

The ground shakes for about forty seconds, building to a peak halfway through and dying away.

* Every half second everyone standing on something within 24 blocks of the target, at any height (in a
  mine, in a base, on a sky platform), is jolted sideways, harder at the peak, and the screen sways for
  the target. Nobody in an obsidian bunker feels it.
* Rocks shake loose overhead (cave ceilings, overhangs, roofs) and come crashing down on whoever is
  under them. They burst where they land. Under open sky nothing falls, and nothing ever falls from
  obsidian. In `build-damage-worlds` a plain ceiling block breaks out itself and leaves a hole.
* Four fissures tear open across the floor at the target's level a few blocks away, each racing across a
  dozen columns, up to five blocks deep; a quarter of them glow with lava at the bottom where they run
  through natural ground. In `build-damage-worlds` they split whatever the floor is made of (cave floor,
  base floor, sky platform); elsewhere only natural ground. Obsidian and protected blocks are never split.

## Tornado — `/tornado`

A funnel touches down 24 blocks from the target and churns across the land toward them for fifty seconds,
weaving as it goes and passing straight through when it reaches them. It is slower than a sprint, so it can
be outrun.

* Anything that gets close (players, mobs, animals, dropped items) is drawn in, spun round the funnel,
  lifted, and thrown clear once it is carried past half the funnel's height. The fall is the danger.
* Players are reached at any height in the funnel's column. Under a roof or underground the wind cannot
  lift you out, so it drags you about and batters you with debris instead: a heart a second while the
  funnel is on top of you. Only an obsidian bunker keeps it out.
* Blocks under the funnel are torn up and flung as debris, four a second and at most 120 per tornado: in
  `build-damage-worlds` whatever is on top (roofs, walls, trees, the ground; built blocks drop as items),
  elsewhere only loose natural ground (grass, dirt, sand, gravel, leaves, small plants). Protected blocks
  and obsidian are never torn up.
* It rains for the duration; the world's weather is handed back afterwards, as with the storm.
* Overworld skies only; a scheduled tornado can pick anyone there, deep in a mine or up in the sky.

## Blizzard — `/blizzard`

For ninety seconds snow drives in thick around the target.

* Everyone within 28 blocks gets colder, wherever they are: slow, then sluggish, then frostbitten and
  losing half a heart every two seconds. Out in the open frostbite sets in after 12 seconds. A cold meter
  shows on the action bar.
* Anything overhead (a roof, an overhang, the rock above a mine, glass, a tree) or the light of a torch,
  fire or lava close by only slows the cold, to 40%; both together, to 20%. Only an obsidian bunker keeps
  it out entirely and lets you warm back up.
* Snow piles up in drifts on open ground and roofs near the target, and still water freezes over
  (never right beside a player). When the blizzard passes, every snow layer and ice sheet it made
  thaws away again, so it leaves no permanent mark.
* Overworld skies only; a scheduled blizzard can pick anyone there, deep in a mine or up in the sky.

## What disasters never harm

Other building blocks can be blown open, split or burned. These are the exceptions, for every disaster:

* **Obsidian**, always and everywhere (ObsidianProtection keeps it safe from every explosion, mob and fire,
  disaster or not).
* **Utility blocks**: chests, trapped and ender chests, shulker boxes, furnaces, crafting tables, anvils,
  enchanting tables, bookshelves, beds, brewing stands, cauldrons, hoppers, droppers, dispensers (a sentry
  turret's body), jukeboxes, note blocks, beacons, spawners, heads, command and structure blocks.
* **The block under any of those**, so nothing is left hanging and no anvil drops and chips.
* **Portals and their frames**: every portal block and everything within 2 blocks of it, whatever the frame is
  made of. That covers the Nether's obsidian and the realm gates' mossy cobblestone, sandstone and the rest.
* **Armor stands** (sentry turrets' stands among them), **item frames, paintings, minecarts and boats**: never
  moved by a quake or tornado and never harmed by a disaster's blast, bolt or fire.

Meteor and hell-bolt explosions go through one wrapper (`Impacts.explode`); while one runs, a listener at
the lowest priority takes every off-limits block out of the explosion before any other plugin (the turrets,
the realm gates) sees it. Disaster fire is never lit beside anything protected, fissure lava is never left
within three blocks of it, and for three minutes after a disaster nothing protected burns in that world.
`tests/java/chat/jaspr/disasters/DisasterSafetyTest` runs every disaster at full strength through a world packed
with these blocks and two portals, and checks every single block change against these rules. It also seals a
player in an obsidian bunker (untouched by all three new disasters), puts one on a sky platform, one down a cave
and one in a wooden hut (all reached), and checks the hut keeps every plank where builds are not wrecked.

## Commands

| Command | Effect |
| --- | --- |
| `/shower` | Call a meteor shower down on yourself. |
| `/lightning` | Call a thunder-hell storm down on yourself. |
| `/quake` | Call an earthquake down on yourself. |
| `/tornado` | Call a tornado down on yourself. |
| `/blizzard` | Call a blizzard down on yourself. |
| `/<any of them> status` | What is running, and when the next disaster is due. |
| `/<any of them> stop` | End whatever is running now. |
| `/<any of them> reload` | Re-read `config.yml` without restarting the server. |

Every command requires `jaspr.disasters.admin`, which defaults to op. On this server `JasprSsoBridge`
ops a player for as long as their Jaspr session is privileged, so verified admins have it and nobody
else does. TestServerControl's command policy independently gates both commands as admin-only.

## Configuration

Everything is in `plugins/JasprDisasters/config.yml`, including the shared `schedule` window, the bolt cadence,
the crater size, and whether either disaster is allowed to break blocks at all. Setting
`break-blocks: false` on any section keeps the spectacle and the damage to players while leaving
terrain intact. Changes take effect on `/shower reload`, so tuning never costs anyone a restart. `state.yml` holds the next scheduled time and the last kind, so a restart does not reset
the cycle.

## Building

```powershell
scripts\build-disasters-plugin.ps1     # compiles to candidate\disasters\JasprDisasters.jar
scripts\install-disasters.ps1          # installs it and restarts Paper
```

1.3.0 is built entirely from source. The three classes the 1.2.1 build had kept from an older jar
(`DisasterPlugin$1`, `DisasterPlugin$Kind`, `ThunderHellStorm`) differed from a source build only in
constant-pool order and in `ThunderHellStorm.trailingSpot` calling `World.equals` rather than
`Object.equals` (the same method at run time), checked with `javap -c`; `DisasterPlugin` and its kinds
are new in 1.3.0 anyway.
