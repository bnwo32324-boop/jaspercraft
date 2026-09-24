# JasprInvasions

Nightly invasions for JasperCraft, built after the design of the Forge mod *Hostile Worlds:
Invasions* by Corosus. This is an original Bukkit implementation of that mod's mechanics rather
than a code port, because a Forge mod cannot run on Paper at all, and the JasperCraft client is a
fixed Eaglercraft build that cannot take client-side mods. Everything below therefore runs
entirely server-side, using vanilla mobs dressed for the occasion.

## What an invasion is

Nothing starts on a schedule. An invasion comes only when an eligible player climbs into
bed — settling down for the night calls one down instead of letting them sleep, once per
night per player. Who it comes for, and what it sends, is personal.

Each player has their own difficulty, derived from their own time played on this server. On a
single invasion night a veteran can be fighting armoured, enchanted raiders at one base while a
newer player fights half a dozen bare zombies at another, and someone who joined this morning is
left alone entirely until they have played enough to be worth hunting. Each invaded player gets
their own siege, their own spawn budget and their own roster of invaders, so invasions happen at
several bases at once rather than converging on one.

The progression clock starts the first time a player is seen by the plugin, so nobody carries in a
head start from before it was installed.

## The invaders

Invaders spawn in a dark ring around their target, and the ring follows them. They arrive in waves
over the night, never exceeding the concurrency caps.

Three behaviours are what make this a siege rather than a mob spawn:

* **Omniscience.** An invader always knows where its target is, walls or no walls. It will not,
  however, steal aggression from another player who has waded in to help.
* **Miners** dig through whatever is between them and their target - forward through a wall, up
  through a ceiling, or down through a floor.
* **Soldiers** pillar upward to reach someone standing above them, and their ladders come down
  when the siege does.

Archers keep their distance. Occasional captains arrive with far more health and a name tag.
Daylight no longer burns invaders: whatever is still standing at sunrise keeps fighting.

## Tiers

Difficulty is a single number from 0 to 1 that drives mob composition, count, armour, enchantments,
weapons, movement speed, health, attack damage, knockback resistance and potion effects together.

| Tier | Roughly | What shows up |
| --- | --- | --- |
| Stirring | first eligible nights | A handful of bare zombies and a skeleton |
| Probing | early | Husks and spiders join, leather and wood, the first miner |
| Organised | middle | Strays and cave spiders, chainmail and stone, real digging crews |
| Armoured | late | Vindicators and witches, enchanted iron, captains |
| Overwhelming | endgame | Evokers and wither skeletons, enchanted diamond, speed and resistance |

## Base damage

Damage is meant to be frightening, not permanent. Every block an invader mines or explodes is
recorded and put back a few minutes later, and restoring never overwrites something a player has
rebuilt in the meantime, so repairing your own wall before the timer fires simply wins. Chests,
furnaces, spawners and anything else holding items are never destroyed. Obsidian and bedrock are
never breached, which makes a hardened room a real defence.

If the server goes down mid-siege, everything outstanding is restored on shutdown rather than left
as holes.

## Commands

| Command | Effect |
| --- | --- |
| `/invasion` | Call an invasion down on yourself at your own difficulty. |
| `/invasion <0.0-1.0>` | Call one at a forced difficulty, for testing a specific tier. |
| `/invasion status` | Your progression, what is running, and whether tonight is an invasion night. |
| `/invasion stop` | End every running invasion. |
| `/invasion reload` | Re-read `config.yml` without restarting. |

Commanded invasions ignore daylight scheduling and grant their invaders fire resistance as a
belt-and-braces measure. They do not count toward progression.

`/invasion` requires `jaspr.invasions.admin`, which defaults to op. On this server `JasprSsoBridge`
ops a player for as long as their Jaspr session is privileged, so verified admins have it and
nobody else does, and TestServerControl independently gates the command as admin-only.

## Differences from the original mod

* **No sacrificial block.** The original lets you offer items at a custom block with its own GUI to
  skip an invasion. Custom blocks and GUIs need a modded client, which this server cannot have, and
  the mechanic was dropped by choice rather than adapted.
* **No client-side difficulty readout.** The original sends difficulty to a client HUD. Here
  `/invasion status` reports the same information in chat.
* **Mob variety is vanilla.** The original can draw on mobs from other mods in a modpack. Every
  invader here is a vanilla 1.12.2 mob, buffed and equipped server-side.
* **Concurrency caps.** JasperCraft already runs a mob-heavy plugin set, so invasions cap both per
  player and server-wide and spend their budget in waves as earlier invaders die.

## Configuration

Everything is in `plugins/JasprInvasions/config.yml` and applies on `/invasion reload`. The tuning
worth knowing: `progression.min-days-played` is how long someone plays before invasions find them,
`progression.full-difficulty-days-played` is how long until they face the worst of it,
`spawning.count-multiplier` scales every invasion at once, and `base-damage.break-blocks: false`
keeps the siege while leaving terrain untouched. `players.yml` holds each player's progression.

## Building

```powershell
scripts\build-invasions-plugin.ps1     # compiles to candidate\invasions\JasprInvasions.jar
scripts\install-invasions.ps1          # installs it and restarts Paper
```
