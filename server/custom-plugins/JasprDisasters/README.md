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

* Every half second everyone on the ground within 24 blocks of the target is jolted sideways, harder at
  the peak, and the screen sways for the target.
* Rocks shake loose overhead (cave ceilings, overhangs, roofs) and come crashing down on whoever is
  under them. They burst where they land and leave no block behind. Under open sky nothing falls.
* Four fissures tear open across the ground a few blocks from the target, each racing across a dozen
  columns, up to five blocks deep, a quarter of them glowing with lava at the bottom. Fissures split
  only natural ground: buildings, roads, farms, protected blocks and obsidian are left alone.

## Tornado — `/tornado`

A funnel touches down 24 blocks from the target and churns across the land toward them for fifty seconds,
weaving as it goes and passing straight through when it reaches them. It is slower than a sprint, so it can
be outrun.

* Anything that gets close (players, mobs, animals, dropped items) is drawn in, spun round the funnel,
  lifted, and thrown clear once it is carried past half the funnel's height. The fall is the danger.
* Loose natural ground at its foot (grass, dirt, sand, gravel, leaves, small plants) is torn up and
  flung as debris, a few blocks a second and at most 60 per tornado. Builds, farms, protected blocks
  and obsidian are never torn up.
* It rains for the duration; the world's weather is handed back afterwards, as with the storm.
* Overworld skies only, and a scheduled tornado only picks a player near the surface.

## Blizzard — `/blizzard`

For ninety seconds snow drives in thick around the target.

* Anyone within 28 blocks who is out in the open and away from warmth gets colder: slow, then
  sluggish, then frostbitten and losing half a heart every two seconds. A cold meter shows on the
  action bar.
* Anything overhead keeps the cold off: a roof, an overhang, glass, a tree. So does the light of a
  torch, fire or lava close by. Out of the cold you warm back up.
* Snow piles up in drifts on open ground and roofs near the target, and still water freezes over
  (never right beside a player). When the blizzard passes, every snow layer and ice sheet it made
  thaws away again, so it leaves no permanent mark.
* Overworld skies only, and a scheduled blizzard only picks a player near the surface.

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
