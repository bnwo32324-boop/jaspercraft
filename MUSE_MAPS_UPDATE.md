# Muse+GLM_Maps (JasprMuseMaps 1.0.0, JasprMuseMapsPack 1.0.0) -- 2026-09-26

The owner's 139 schematics from `Desktop\Muse+GLM_Maps` as their own structure pack, separate from every other pack.

## Where things are
- Originals (byte-for-byte): `server/custom-plugins/JasprMuseMaps/maps/Muse+GLM_Maps/*.schem`.
- Converter: `python -B scripts/muse-glm/build_muse_pack.py [--check]` (reuses the audited Threefold tools in
  `scripts/muse-glm/lib/`; design rules in `lib/muse_design.py`; per-design spawn calibration in `calibration.json`).
  Output: `server/custom-plugins/JasprMuseMaps/pack/maps/` (+ `MAPS.md`, the human-readable register).
- Plugins: `bash scripts/build-muse-maps.sh` -> `server/plugins/JasprMuseMaps.jar` (code) and
  `server/plugins/JasprMuseMapsPack.jar` (11 MB of block data, only rebuilt when the maps change).
- Tests: `node --test tests/muse-maps.test.cjs`.

## Behaviour
- **Placement**: own 32-chunk grid; one design per cell chosen by calibrated weight so every design lands about equally
  often; about one site per 980 x 980 blocks (the same spacing as the HorrorBiomes catalogue -- an average rate).
  Admitted clear of HorrorBiomes structures (ClaimGuard, expedition catalogue), JasprImportedWorldgen sites and the
  256-block spawn core. The three largest designs (Diamond Casino, Mega Base, Legislative Palace; ~200 blocks wide)
  almost never find room between existing structures and are therefore very rare.
- **Existing land (retrofit)**: chunks that existed when the plugin first started get sites too, but only where players
  have spent under a minute (chunk inhabited time), never within 256 blocks of spawn, and only while nobody is near.
  All-or-nothing per site. `retrofit`/`retrofit-max-inhabited-ticks` in `plugins/JasprMuseMaps/config.yml`.
- **/where** inside a site: `Structure <name> (Muse+GLM_Maps)`, its tier, dangers, special loot and boss.
- **Dangers**: every site has a garrison with Overworld, Nether and End mobs, its own named custom mob (two abilities,
  never a zombie), and two environmental hazards; the combination is unique per site. Garrisons return 20 minutes after
  being cleared (`rearm-minutes`). No block damage from any of it; no glowing effects.
- **Loot**: theme loot in the design's chests, So Many Enchantments books, supplies, weak sidearms, Survivor Gear; the
  vault chest holds the site's unique special item (named, vanilla + SME enchantments).
- **Ten bosses** (boss bar, telegraphed moves, three phases, arena tether, full reset if everyone leaves); the vault is
  sealed until the boss falls. Drops: the boss's signature weapon, two Essences, two SME books, diamonds (+ a Survivor
  Gear trinket via the jaspr_boss tag).
- **Weapons**: 16 new JasprApocalypse melee weapons (10 boss relics, 6 bench-crafted) with perks in `Weapons.java`.
- **Essences** (character upgrades): Vitality, Might, Celerity, Bulwark, Resolve, Fortune; 5 ranks each, permanent,
  `/essences` shows ranks. Boss drops, rare vault loot, Creative menu.

## Logs
`MUSE_MAPS_READY`, `MUSE_BOUNDARY_READY`, `MUSE_TILE`, `MUSE_RETROFIT_DONE/ABANDONED`, `MUSE_ENCOUNTER`,
`MUSE_BOSS_SPAWN/DEFEATED`, `MUSE_ESSENCE`, `MUSE_TILE_FAILED` (generation stops itself after 25 failures).
Test-server-only tools (`-Djaspr.muse.fixture=true`): `/musemaps census|calibrate|why|locate|place`.
