# The Backrooms (JasprBackrooms)

**TL;DR:** a new dimension of seven liminal spaces, one after another, each harder than the last. You beat it by
reaching the furthest room and killing the Lifeguard.
- **Who can get in:** only players who have beaten a dimension. That means Atlas, Drownhollow, the Nether, or the
  Ender Dragon (a kill from before this update counts, and so does anyone nearby when the dragon dies).
- **The gate:** a vanilla **yellow glazed terracotta** frame, shaped like a nether portal (inside 2-21 wide, 3-21 tall,
  corners optional). A conqueror lights it with flint and steel, in the overworld only. The surface glows yellow in
  the browser client.
- **The rules:** the terrain cannot be broken. You can only break blocks you placed yourself, chests and cobwebs.
- **The start:** the Threshold, a peaceful hub. No monsters appear within 64 blocks of it. The Backrooms Guide hands out
  a compass, a checklist and a map, and 2 Almond Water every 10 minutes.
- **Owner tools:** `/backrooms status`, `tp [1-7]`, `peek <1-7> [0..1]`, `arena <n>`, `boss <n>`, `mob <kind>`,
  `item <id|all|level<n>>`, `where`, `back`, `reset`, `selftest`, and `as <player> <sub>`. Players use `/goals`.

## The seven levels
Each level is a long zone running east. Every level is laid out as:
- a **landing**, a bright, safe room;
- a body that grows more dangerous the further you go;
- a **boss arena**;
- a glowing **exit** that opens once the boss is dead.

Reaching a level opens its door in the Threshold, so you never have to repeat a level. Walking back into the
Threshold's gate takes you home, to the gate you came through.

| # | Space | What it is like | Monsters | Boss |
|---|---|---|---|---|
| 1 | The Yellow Rooms | Yellow wallpaper, damp carpet, humming panels. The lights fail deeper in. | Wretch, Hound, Smiler | The Smiler, in the unlit Dark Room |
| 2 | The Warehouse | Racks of crates, hanging work lamps, partition walls, wet patches | Hounds, Crate Mimic, Scavenger | The Foreman, in the Loading Bay |
| 3 | The Maintenance Tunnels | Brick pipes, lava pockets, magma vents. **Heat** builds up; water cools you. | Steam Wraith, Burner, Pipe Crawler, Scalded Worker | The Stoker, in the Boiler Room |
| 4 | The Electrical Corridors | A maze of grey corridors, machinery and transformer rooms. **Live floor** tiles shock you, and transformer cores arc. | Spark, Surge, Lineman, Grid Crawler | The Live Wire, in the Substation |
| 5 | The Abandoned Office | Cubicles, meeting rooms and dead floors. The lights **flicker**, and something stands behind you. | Partygoer, Clerk, Paper Shredder, Recruiter | The Manager, in the Boardroom |
| 6 | The Endless City | A normal-looking street at dusk. Citizens turn into **Mimics** close up. Rooftop Stalkers. | Citizen, Rooftop Stalker, Watcher, Stray Hound, Faceless | The Stranger, on City Hall plaza |
| 7 | The Poolrooms | White tiles, flooded rooms, deep lit pools, and an **undertow** | Pool Guardian, Drowned Swimmer, Lost Lifeguard, Chlorine Wisp | The Lifeguard (a giant), then the Deep, in the Deep End |

**Difficulty** rises across each level and from level to level: 0% at the Threshold, 100% in the Deep End.
- Monster health rises from x0.7 to x1.8, damage from x0.55 to x1.55.
- Monsters come in bigger groups, and more of them are elites.
- Bosses get stronger for every extra fighter.

**Loot** gets better the further in you go:
- Every level has chests to find.
- Each boss drops a hoard the first time you beat it.
- Every level has its own **armour set** with a set bonus, plus a weapon, a tool and two trinkets:
  1. Wanderer: stealth. Rebar, Utility Knife, Almond Water Canteen, Flickering Bulb.
  2. Warehouse Worker: no knockback. Crowbar, Pallet Hook, Forklift Key, Packing Tape.
  3. Boiler Suit: fire resistance. Pipe Wrench, Valve Key, Pressure Gauge, Coolant Vial.
  4. Lineman: shock-proof. Arc Baton, Wire Cutters, Surge Protector, Capacitor.
  5. Executive: haste and stealth. Letter Opener, Staple Gun, Employee ID Badge, Cold Coffee.
  6. Night Watch: sees through Mimics, and arrows do half damage. Stop Sign, Jackhammer, Dead Man's Watch (cheats
     death), Subway Token.
  7. Lifeguard: at home in the water. Tidebreaker, Pool Skimmer, Rubber Duck, Lifeguard's Whistle.

**Winning:** killing the Lifeguard gives you the title "BACKROOMS CONQUERED", a server-wide broadcast, the Backrooms
Conqueror's Crown and **The Exit Sign** trinket (speed, resistance, and regeneration at low health). It also counts as
a beaten dimension. The exit behind the Deep End takes you home.

## Lighting (2026-09-30)
- The Threshold and Level 1 have light hidden under the carpet, so they are bright like the real Backrooms. It fails
  deeper into Level 1.
- Every landing has floor lamps.
- The Warehouse has work lamps on chains over its aisles.
- The Office has denser ceiling panels.
- The Poolrooms have a light in every tile square.
- The layout test works out the game's block light and fails any level that turns dark where it should be bright.
- **Why it looked dark before:** with Spigot's default `random-light-updates: false`, the server sent a new chunk
  before working out its light, and the client kept it dark until the chunk was sent again. For the Backrooms world
  only (`jaspr_levels`), the plugin now makes the server wait for the light first, as vanilla does (`BACKROOMS_LIGHT
  mode=wait` in the log). No server config file changed.
  - The trade-off: chunks at the very edge of the view distance appear one ring later.

## Safety and compatibility
- The world `jaspr_levels` loads on demand and unloads a minute after the last player leaves. It never has weather,
  daylight cycle, fire spread, mob griefing or vanilla spawning.
- JasprDisasters and JasprInvasions are kept out of `jaspr_levels`: meteors would carve the terrain, and invaders dig
  through walls with block changes that no protection can see.
  - Both live configs now list their worlds explicitly (backups: `config.yml.before-backrooms`). **A new world must be
    added to both lists** to get disasters and invasions.
  - As a backstop, JasprBackrooms removes any invader that appears in its world (`invadersTurnedAway` in
    `/backrooms status`).
- Blight poison and Daylight slowness do not apply in the Backrooms.
- Log events: `BACKROOMS_READY`, `BACKROOMS_WORLD_LOADED`, `BACKROOMS_LIGHT`, `BACKROOMS_LEVEL_REACHED`,
  `BACKROOMS_BOSS_WOKE` / `SLAIN`, `BACKROOMS_CONQUERED` and `BACKROOMS_SELFTEST`. No IPs or secrets are logged.

## Tests
- `tests/backrooms.test.cjs` covers:
  - layout: every level walkable from entry to arena, exit and boss, no water that can flow, and lit;
  - the logic;
  - the shipped jar;
  - the owner's rules in the source;
  - the yellow gate tint.
- `tests/guide-kit.test.cjs` and `tests/portal.test.cjs`.
- In-game self-test: `/backrooms selftest` (137 checks).
- Loopback runs with a bot:
  - full flow: gate, entry, levels, exits, bosses, victory;
  - gameplay: every boss fights, the director spawns, hazards, TNT is blocked, a Citizen turns into a Mimic, trinkets.
- Headless browser screenshots of every level.
