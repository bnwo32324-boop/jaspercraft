# JasprDisasters

Natural disasters for JasperCraft. Exactly one disaster runs at a time, server-wide, and it always
happens to exactly one player. That is the whole cost model: no matter how many people are online,
the server is only ever paying for one bounded event near one player.

Two disasters ship today. Each keeps its own independent timer and fires somewhere between 1 and 14
Minecraft days (a Minecraft day is 20 real minutes). If one is already running when the other comes
due, the second simply waits its turn, and a cooldown after each event stops them chaining.

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

## Commands

| Command | Effect |
| --- | --- |
| `/shower` | Call a meteor shower down on yourself. |
| `/lightning` | Call a thunder-hell storm down on yourself. |
| `/shower status` or `/lightning status` | What is running, and when each kind is next due. |
| `/shower stop` or `/lightning stop` | End whatever is running now. |
| `/shower reload` or `/lightning reload` | Re-read `config.yml` without restarting the server. |

Both commands require `jaspr.disasters.admin`, which defaults to op. On this server `JasprSsoBridge`
ops a player for as long as their Jaspr session is privileged, so verified admins have it and nobody
else does. TestServerControl's command policy independently gates both commands as admin-only.

## Configuration

Everything is in `plugins/JasprDisasters/config.yml`, including the day ranges, the bolt cadence,
the crater size, and whether either disaster is allowed to break blocks at all. Setting
`break-blocks: false` on either section keeps the spectacle and the damage to players while leaving
terrain intact. Changes take effect on `/shower reload`, so tuning never costs anyone a restart. `state.yml` holds the next scheduled time for each kind so a restart does not reset
the cycle.

## Building

```powershell
scripts\build-disasters-plugin.ps1     # compiles to candidate\disasters\JasprDisasters.jar
scripts\install-disasters.ps1          # installs it and restarts Paper
```
