# The world after the breach — deployed

Live at https://jaspr.chat/jaspercraft/. Verified 2026-09-07T23:09:18Z. Reload the browser page for the new biome names and climate presentation.

All **62 Minecraft 1.12.2 biome slots** now have explicit original horror replacements. The existing network IDs remain as compatibility carriers; there are no leftover vanilla biome names in the browser's biome registry. The Overworld uses a new deterministic terrain generator, not a post-generation name-only reskin. The Nether and End have regenerated native geometry and progression structures, with replacement biome identities and exposed-floor palettes. Nether regions follow nine repeating radial Inferno bands; these are not nine vertically stacked dimensions.

## What was preserved

The reset retained all 23 player NBT files, 23 statistics files, 23 advancement files, Ender Chests, XP, saved game modes, health/food/effects, inventories and item NBT. World UUIDs, score/map data, seeds, gamerules, account databases and authentication configuration were retained. Jaspr.chat account-linked browser settings were not touched.

All 90 protected files matched byte-for-byte after terrain removal and metadata migration, before startup. After startup every player/statistics/advancement file and the account database still matched; only the operational `AuthMe/authme.log` had grown. No player account was created to test production.

The old **terrain, placed builds, ordinary world containers, and world entities** were replaced in the Overworld, Nether and End. A player's first return relocates them to the new safe spawn instead of their obsolete coordinates; later reconnects retain their new location. Old beds disappear with the terrain. The new safe spawn and obsolete End dragon fight references are the only intentional level-metadata changes.

Recoverable old terrain and protected-state copies: `world-resets/horror-v1-2026-09-07T23-07-09-731Z/`. Its `reset-manifest.json` records exact targets and hashes. No world root or player directory was deleted. The always-on gateway was placed in maintenance for the graceful shutdown/reset, then restored. It reports running, SSO ready, always-on true, idle shutdown zero, no last failure.

## The 62 regions

The full machine-readable mapping, including each replaced slot, terrain relief, materials, vegetation, landmark family, atmosphere and water color, is [biomes.tsv](server/custom-plugins/JasprHorrorBiomes/resources/biomes.tsv). Names/layouts are original; no proprietary franchise textures, music, maps or models were downloaded.

### Silent Hill inspirations — 12

1. Ashveil Coast
2. Rustwater Ward
3. Gutterglass Schoolgrounds
4. Tenement Wound
5. Cinder Hospice
6. Drowned Borough
7. Whiteout Reservoir
8. Penitent Spillway
9. Palimpsest Vaultlands
10. Red Blossom Hollow
11. Bloomfall Tenements
12. Signalwreck Coast

The catalogue associates these with the numbered games/remake, Origins, Homecoming, Shattered Memories, Downpour, Book of Memories, f, The Short Message, Townfall, and handheld/arcade branches. Creative references include [Konami's f setting](https://www.konami.com/games/us/en/topics/2814/), [Book of Memories](https://www.konami.com/games/eu/es/products/shbom/) and [Townfall announcement](https://www.konami.com/games/corporate/ja/news/topics/20260213s/).

### S.T.A.L.K.E.R. inspirations — 8

13. Abandoned Cordon
14. Blackreed Marsh
15. Sunken Convoy
16. Dead Array
17. Sanguine Exclusion Forest
18. Gravity Scar
19. Oxide Railgrave
20. Glass Reactor

The original trilogy, Legends of the Zone and Heart of Chornobyl supply the exclusion-zone reference family. [Official S.T.A.L.K.E.R. 2 setting](https://www.stalker2.com/).

### Fallout inspirations — 13

21. Bleached Expanse
22. Bitter Orchard
23. Capitol Ossuary
24. Neon Dust Basin
25. Viridian Blastlands
26. Coalbreath Ridges
27. Shelterfall Terraces
28. Iron Convoy Barrens
29. Brotherhood Slagfields
30. Vaultroot Gardens
31. Brine Lantern Mire
32. Gilded Hush
33. Carousel Wastes

The catalogue includes Fallout 1–4, New Vegas, 76, Tactics, Brotherhood of Steel, Shelter and Shelter Online, plus expansion influences. References: [official Fallout catalogue](https://fallout.bethesda.net/) and [Fallout 4's additional regions](https://fallout.bethesda.net/en/games/fallout-4/your-journey-continues).

### Dark Souls inspirations — 10

34. Hollow Crown Borough
35. Rootbound Sanctuary
36. Leechwater Depths
37. Cinderlake Shelf
38. Forgotten Monarch Shore
39. Blackgutter Chasm
40. Frostcrown Ramparts
41. Ringfall Cinders
42. Cathedral of the Last Ember
43. Drowned Oath Fen

References span all three games, Remastered/Scholar editions and their expansions. [Official Dark Souls Trilogy](https://en.bandainamcoent.eu/dark-souls/dark-souls-trilogy).

### Evil Dead inspirations — 10

44. Cabin of the Listening Pines
45. Bloodroot Timberland
46. Boomtown After Midnight
47. Deadward Asylum
48. Bastion of the Unburied
49. Possessed Orchard
50. Endless Night Boulevard
51. Mirrorward Thicket
52. Kandarian Hunting Grounds
53. Grinning Hollow

The catalogue draws on the 1984 game, Hail to the King, A Fistful of Boomstick, Regeneration, Army of Darkness: Defense, mobile/VR entries, the 2022 game and RetroRealms. Reference families: [game history collection](https://bookofthedead.ws/website/collectables_main_games.html) and [Evil Dead: The Game](https://www.evildeadthegame.com/en/).

### Dante's Inferno — all 9 circles

54. I — Limbo: Mourning Fields
55. II — Lust: The Unceasing Gale
56. III — Gluttony: Carrion Slough
57. IV — Greed: The Burdened Quarry
58. V — Wrath: Styx Blackwater
59. VI — Heresy: Sepulcher of Embers
60. VII — Violence: Thornblood Reach
61. VIII — Fraud: Malebolge Trenches
62. IX — Treachery: Cocytus

Cocytus is an ice region, not another lava field, following the ninth-circle setting. [Digital Dante, Inferno 32](https://digitaldante.columbia.edu/dante/divine-comedy/inferno/inferno-32/inferno-32-longfellow/).

## Gameplay and limits

- Warped regional boundaries and blended elevation; scar craters, terraces, channels and trenches; distinct vegetation and block palettes. Features stay inside generated chunks; ruin foundations extend into the actual ground and reject steep/waterlogged sites.
- Ruined districts, cabins, keeps, shrines, radio arrays, reactor remnants, rail/checkpoints, vault cellars and other themed landmark variants. These coexist with the prior Apocalypse plugin's ruins. Landmark families share construction primitives; this is not 62 completely separate structure generators.
- Ore distribution, farm supplies, ordinary saplings and food in sparse ruin caches preserve survival progression. No free late-game guns or relic bypasses.
- Native Nether fortresses and End dragon/cities remain. Custom Overworld portal sanctuaries occur on a 4096-block grid and require twelve Eyes of Ender. Use `/wasteland portal` for directions; vanilla stronghold location mechanics are not the route to these custom sanctuaries.
- `/wasteland` identifies the region and inspiration. `/wasteland atlas [1–8]` lists the regions. Region titles and occasional quiet local sound accents do not change saved audio settings or impose blindness. No extra environmental damage/radiation system is claimed by this update.
- Lighting uses native light values: local chunk skylight propagation fixes black canopy patches before initial delivery; later boundary reconciliation is bounded to 32 columns/tick with a 2 ms soft budget and no neighbor chunk loads. There is additional generation/lighting work, not a zero-overhead guarantee.
- Existing guns, gore, combat, account login, browser settings synchronization and mobile launch fixes remain intact. Physical mobile hardware was not tested in this release.

## Verification / exact release

- Final real-Paper fixture: `candidate/biomes-smoke-fe496a5b-1035-415a-bfce-e6e23f6e3388/`.
- 62 reachable replacements, 3 available dimensions, deterministic block/biome output, terrain continuity, bedrock/ores, twelve End portal frames, real chunk save and positive canopy skylight checks: **2,563,242 assertions passed**.
- Raw chunk-generation average in that isolated fixture: 0.925 ms/chunk. This excludes full server/client/network cost and is not a production benchmark.
- In-browser visual checks covered forest, icy and desert examples. The visual check directly led to the canopy-lighting fix and an automated regression assertion.
- Real disposable terrain/metadata reset rehearsal preserved all fixture player/account files. Production preservation was then checked before and after restart as described above.
- All **35 Node regression tests** passed after publication, covering biome patch/reset, gore, weapon resources/orientation, account settings, login, direct entry, mobile startup and Survival/game-mode persistence. Site verification passed.
- Public loader chain and full client bytes were fetched and verified. Client SHA256: `CFF2AB19333224B092D90B89BDD6FC74ABF23D974A2CCFEE2EE21BA0B5252B0D` (14,381,226 bytes).
- Plugin SHA256: `74F23D5E4178235B53B7F8544DD0E53BF9DB939A81B4E3822BA913658BEE27C4`.
- EPK, Apocalypse plugin and authentication plugin hashes remain unchanged from the gore release. No test/probe plugin was installed in production. All isolated fixtures were stopped.

Build: `scripts/build-horror-biomes.ps1`, then `node scripts/biome-client-patch.cjs`. Test: `node --test tests/horror-biomes.test.cjs` and `node tests/biomes-smoke.cjs`. The fixture stays open for visual checks; finish it with `stop`.

The reset script is dry-run by default and refuses `--apply` unless the live gateway is in maintenance and Paper is stopped. Review the exact paths first. A future terrain generation must use a fresh relocation-generation marker rather than reusing `relocated-world-v1.yml` blindly. Client stages are hash-pinned and reversible: biome presentation reverses before native gore hooks, and rebuilding gore preserves an existing biome stage.
