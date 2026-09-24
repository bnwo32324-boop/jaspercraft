# JasperCraft consent teleport and sparse-structure update

> Historical `sparse-v5` release note. The later `rare-v6` migration keeps the 10% global admission rule, adds a 3,072-block structure-free spawn radius, and intentionally changes the world seed. See `RARE_WORLD_RESEED_UPDATE.md`.

## Teleportation

All signed-in players, whether operator or not, use the same consent-based flow:

- `/tpa <player>` or `/tp <player>` asks to teleport to that player.
- `/tpahere <player>` asks that player to teleport to you.
- `/tpaccept` accepts the single pending incoming request.
- `/tpdeny` denies it.
- `/tpcancel` cancels the request you sent.

Requests are single-use, expire after 60 seconds, and are removed when either party disconnects. A request never checks operator status. Administrators who intentionally need Minecraft's unrestricted command can use `/minecraft:tp`.

## Structure frequency and world epoch

Both generated-structure tiers now admit 10% of the candidate cells they admitted before. This makes structures 90% rarer in expectation while retaining the full 234-design catalog, biome-specific selection, surface/underground/underwater mix, bosses, and journaled loot rules. The deterministic admission gate means surviving sites keep their established design and layout.

The regenerated terrain epoch is `sparse-v5`. The reset moves the old Overworld, Nether, and End region data plus vanilla structure indexes into a dated recovery archive. Terrain-linked ruin and placed-turret registries travel with the discarded Overworld so they cannot become ghost objects on new blocks. It does not reset or rewrite player inventories, ender chests, statistics, advancements, account/authentication data, skins, survivor ranks/checkpoints, settings, loot claims, or the Fold/backrooms world and return records. The exact `surface-v4` predecessor is required, preventing this migration from being applied twice.
