# Dungeon Dimension: dying wakes you at spawn (JasprDungeon 6.0.3, 2026-10-05)

Owner, 2026-10-05: "The dungeon dimension should be reset and randomized after every exit and entry. If you die in the
dungeon dimension, it should put you at spawn with all your stuff, but it should reset your character progression levels"
(with a screenshot of the `/stats` sheet).

| Rule | Where | Since |
| --- | --- | --- |
| Every entry starts a brand-new run in its own new world (random seed, new rooms, new refuge theme); leaving by the gate, `/dungeon leave`, death or any other way ends your part in it, and the run world is deleted once everyone has left | `Sessions` | generation 6 (2026-10-04) |
| A death in a run keeps every item and your experience levels (keepInventory, no drops, no grave) | `Sessions.died` | generation 6 |
| A death in a run wakes you **at spawn** -- your bed if you have one, else the world spawn -- never back at the gate you came in by | `Sessions.respawn` | **6.0.3** |
| A death in a run resets your `/stats` sheet (every stat level and mastery rank), as every death does | JasprRPG `StatEffects.onDeath` (`stats.reset-on-death: true`) | JasprRPG 1.2.0 |

Log: `DUNGEON_DEATH_RESPAWN player=<name> died=true to=spawn|bed world=<world>`; the ready line ends `death=spawn`. The player
is told "You fell in the Dungeon Dimension and woke at spawn with everything you carried", and JasprRPG says "Your training
dies with you".

Tests (sandbox `generation6-death`, `C:\Users\AM\Documents\JasperCraft-Dungeon-Death`): the nine static suites
(`build.ps1`) and the real-server `tests/gen6-runtime.cjs`, which now loads the live JasprRPG, trains a stat inside a run,
dies there and checks the player wakes at the world spawn far from the gate with the same items and levels, the stat
sheet reset, and the respawn logged.
