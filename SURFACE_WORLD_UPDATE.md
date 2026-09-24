# JasperCraft Surface World Update

Status: deployed, regenerated, and verified live on September 8, 2026.

## World generation

- The active terrain epoch is `surface-v4`.
- JasperCraft now has 234 distinct generated structure designs.
- Three out of every four expansion cells are reserved for biome-specific, tier-3-or-higher surface landmarks.
- Across the eight-seed discovery audit, 86.10% of generated sites were above-ground structures, averaging 5.98 surface sites per million blocks.
- The same audit measured a 322-block 95th-percentile distance and a 440-block maximum distance to the nearest surface structure.
- On the production seed, three surface structures were found within 300 blocks of spawn; the nearest was 258 blocks away.
- All 62 wasteland biome profiles remain active, but artificial litter and prop clutter is disabled in every biome. Natural terrain, rivers, trees, grass, flowers, and other native foliage remain enabled.

## Regeneration and preservation

- The Overworld, Nether, and End terrain were regenerated from seed `4425965048829651136`.
- Inventories, ender chests, player positions and state, advancements, statistics, accounts, authentication records, skins, survivor upgrades, settings, and durable loot claims were protected.
- All 215 protected files were verified byte-for-byte before startup.
- After startup, 204 remained byte-identical; the remaining files were verified as normal server metadata rewrites, mapping/order changes, append-only logs, or runtime timestamps only.
- All 24 player records, 24 statistics files, and 24 advancement files are present.
- Survival remains the default game mode in all three regenerated dimensions, and retained game rules were verified.
- The recoverable pre-regeneration terrain archive is `world-resets/surface-v4-2026-09-08T15-21-15-331Z`.

## Release verification

- Focused generation and preservation suite: 22 of 22 tests passed.
- Real Paper 1.12.2 smoke test: 104,541 assertions passed across all 234 designs.
- Horror biome plugin: version 3.2.0, SHA-256 `EE4D7CDF2BDDF421F6845F27B42BA06AD3539DC89BC7D19B1C0C4DC40B17DE24`.
- Public JasperCraft client: HTTP 200 at `https://jaspr.chat/jaspercraft/`.
- Game gateway: online, SSO ready, always-on enabled, idle shutdown disabled.
- Local and public Jaspr.chat health checks resolved to the same primary session.
- Machine-readable verification: `candidate/surface-v4-release-report.json`.

The Computer B failover controller was also corrected so a healthy, publicly verified primary retires an active standby connector. Public-health heartbeats now require the exact primary session ID, preventing stale standby traffic from intermittently serving JasperCraft errors.
