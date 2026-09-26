# The Lost Cities (JasprLostCities 1.0.0) -- 2026-09-26

The Lost Cities 1.12-2.0.22 ported onto the HorrorBiomes overworld. The owner asked for "a lost cities biome where it's
only one big city", spawning naturally anywhere and frequently.

## Where things are
- Plugin: `server/custom-plugins/JasprLostCities/` (`HB_JAR=<HorrorBiomes jar> bash build.sh` ->
  `build/JasprLostCities.jar`). The mod's city data (173 parts, 25 buildings, 10 multibuildings, 36 palettes, 6 city
  styles) ships unchanged in `resources/lostcities/`.
- Tests: `node --test tests/nether-lostcities.test.cjs`.

## Behaviour
- **Where**: a 64x64-chunk region grid; about 55% of cells hold one city region (a blob of radius 12-24 chunks). Inside
  a region everything is one continuous city (the mod's "onlycities" profile); the ring around it is flattened toward
  the city as the mod does. About one city per 2 km^2, ~15% of new overworld land. Desert style in desert/mesa.
- **What**: the mod's generator with the mod's seeds -- buildings, 2x2 buildings, cellars, streets, parks, fountains,
  stairs, bridges, highways and rails between cities, subway stations, rail dungeons, explosion damage, ruins and
  rubble, spawners, loot chests. City level follows HorrorBiomes terrain (the mod's four level thresholds).
- **New land only**: chunks that existed when the plugin first started (`world/jaspr-cities-v1.boundary`) are never
  written; nor are HorrorBiomes sanctuaries (3-chunk halo); nor the footprints of structures other packs had already
  planned (`world/jaspr-cities-v1.committed`, taken once at first start). Cities blend into all of these.
- **Everyone else keeps out**: HorrorBiomes 3.27.6 (set pieces, rooms, catalogue sites, dungeons), JasprImportedWorldgen
  1.2.2 and JasprMuseMaps 1.0.1 ask `chat.jaspr.lostcities.CityApi.reserved(...)` before placing anything.
  City chunks get the HorrorBiomes repair guard like imported chunks.
- **Safety**: the mod's `!` valuables palette becomes ordinary blocks of the same hue; command blocks (the mod's
  hard-air marker) are never placed; chisel blocks become concrete. No torches (the mod's default).
- `/where` in a city: "Lost City: <name> (Lost Cities)" and the district; a title on entering a city.
  `/lostcities status|features|density|nearest` (operators).
