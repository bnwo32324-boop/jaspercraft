# The Ancient Ruins (JasprRuins 1.0.0)

A new dimension (world `jaspr_ruins`) of moss-grown land and ruined cities, reached through portals built from
**mossy cobblestone**.

## Getting there

- **Build the frame:** use mossy cobblestone, in the same shape as a nether portal.
  - The inside must be 2–21 wide and 3–21 tall, so the smallest frame is 4 wide by 5 tall. Corners are optional.
- **Light it:** use flint and steel or a fire charge.
- **Cross over:** stand in the portal for 3 seconds (instantly in creative).
  - You arrive at the same x/z in the other world, in front of the nearest mossy portal there.
  - If no portal is nearby, one is built on open, level ground.
- **Closing a portal:** break or blow up a frame block.
- **Vanilla effects are off:** mossy portals never lead to the Nether, never spawn pigmen, and are not torn down for
  lacking obsidian.
- **First-join hint:** every player is told once, 10 seconds after they join, how to build the frame.

## What is there

- **Lost Cities, as ruins.** The Lost Cities plugin builds its cities in this world too. A weathering hook ages them
  before they are written:
  - Upper floors collapse into a jagged skyline, with rubble below.
  - Cobblestone turns mossy; stone bricks become mossy or cracked.
  - Modern materials (quartz, concrete, metal blocks) turn to old masonry.
  - Glass shatters, and furnishings and wool rot away.
  - Streets and courtyards grow over with grass, vines and cobwebs.
  - Entering one shows "The Ruins of <name>".
- **Original old cities.** In a 320-block grid, 45% of cells hold a walled square city 97–161 blocks across, on ground
  levelled into the land:
  - A gate in each side, towers at the corners and along the walls.
  - A street grid with a fountain and obelisk plaza, and a grand temple north of the plaza.
  - Lots filled with: ruined houses (some with a guarded vault beneath), courtyard wells, overgrown gardens, rubble
    heaps, tower houses, shrines, sunken cisterns and meadows.
  - Entering one shows "The Old City of <name>".
- **Wilderness ruins.** In a 96-block grid, 55% of cells hold one of ten original designs:
  - temple, colonnade, ziggurat (with a guarded chamber and a summit shrine), watchtower, aqueduct, amphitheater;
  - stone circle (something is buried under the altar), crypt (a stair down to a vault), gatehouse;
  - a fallen colossus (legs on the plinth, head in the grass).
- **Land.** The terrain uses the same heights as the overworld, so the Lost Cities fit it exactly. It is dressed as
  lush forest, dark forest (dark oak, huge mushrooms), jungle, meadow, marsh with lily pads, and rocky highlands with
  mossy boulders.
  - The biome slots were chosen from the ones the browser client styles green and snow-free.
  - Ores are at vanilla amounts. Chests use vanilla loot tables; the ruins contain no valuable blocks.
- **Placement rules:** original ruins stay off the Lost Cities' land and out of water, so the two kinds never overlap.

## Owner tools

`/ruins [status|tp|back|where|find <city|lostcity|temple|colonnade|ziggurat|watchtower|aqueduct|amphitheater|stones|crypt|gatehouse|colossus>]`
(op). `find` waits for the Lost Cities to finish generating the spot before landing you on it.

## How it works

- `RuinsGenerator` builds the terrain, then stamps the ruins into each chunk.
  - Every ruin is a pure function of the seed, so structures that cross chunk borders come out whole.
  - `RuinsPopulator` finds the chests and spawners by redrawing the plan, then adds trees, ores and lily pads.
- `JasprLostCities` gained `CityApi.registerWorld(name, titleFormat, PrimerHook)`.
  - This is how it builds in `jaspr_ruins` with `Weathering` as the hook.
  - The overworld behaves exactly as before.
- The world takes the overworld seed salted with "Ruins". An existing ruins world keeps its own saved seed, even after
  an overworld reset.
- Spawn chunks are not kept in memory.

## Logs

`RUINS_READY`, `RUINS_PORTAL_LIT|BUILT|CLOSED`, `RUINS_TRAVEL player= from= to= builtPortal= ms=`,
`RUINS_PORTAL_NO_ROOM`, `RUINS_CHUNK_FAILED`, `RUINS_METRICS` (on shutdown).

## Build and test

```
bash server/custom-plugins/JasprLostCities/build.sh
bash server/custom-plugins/JasprRuins/build.sh
node --test tests/ruins.test.cjs
```

The offline check (`tests/java/chat/jaspr/ruins/RuinsPreview.java`) renders top-down previews of an old city and every
site type. In-game checks use the lean fixture:

- Environment: `TANK_PREVIEW_PLUGINS` with the three jars, `TANK_PREVIEW_LEVEL=fixture` (keeps the world-bound plugins
  off the flat test world), `TANK_PREVIEW_SEED=305441741`.
- Steps: build a mossy frame, light it with flint and steel, then use `/ruins find ...`.

Fixture results:

- Generation: 429 ruins chunks with 0 failures, about 0.5 ms each offline.
- Lost Cities in the ruins: 190 ms per chunk on one low-priority core.
