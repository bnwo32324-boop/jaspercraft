# Sentry Turret Update

Craftable automated defense for the live multiplayer server. No world reset. Multiplayer only; no single-player content.

## Contents

- 1 new custom entry: `sentry_turret` / Sentry Turret (Creative catalogue `block` category, Building Blocks tab, searchable by turret/sentry/defense/dispenser). Catalogue grows 87 → 88.
- The item is a marked iron pickaxe (model band 100) with an original 13-cuboid sentry model: dome, forward barrel, targeting eye, optics pods, sight rail and beacon, using only verified vanilla textures. Ordinary pickaxes keep the vanilla look through the selector fallback.
- The placed body is a vanilla dispenser topped by a tracking head: a small invisible marker armor stand wearing a six-times-effective turret model. Because the 1.12.2 display transform is capped at 4x, the remaining 1.5x is baked into the cuboids; a calibrated -3.5px asset base offset and 1.1-block marker anchor put the model directly on the dispenser top while keeping the marker in open air. Its negative-Z barrel uses the target-forward head transform, and yaw is calculated from the same body muzzle origin used by the shot so the turret faces its locked target. Marker stands have no hitbox, so clicks reach the body and attacks ignore them; stray/duplicated stands and legacy heads are rebuilt automatically. Ceiling mounts and placements without the model's seven-block clearance are refused with a message.
- Server tick targets hostile mobs (zombies, skeletons, slimes, ghasts) by default within 12/24/36 blocks with block-step line of sight.
- Right-click the body opens settings: enabled, target hostiles, target passives (animals/villagers), target players (PvP worlds, authenticated survivors only), range, fire rate (slow 16 dmg / normal 12 / fast 8), priority (nearest/weakest/strongest), owner and kill readout. Placement and settings work in Creative and survival.
- Sneak + right-click, mining, or blasting collects it back as a fresh marked single; no duplication path. Creative collection/breaking litters no items.
- Shaped recipe available in both Survival and Creative: 4 plain iron ingots around 1 plain iron block, one ordinary click, server-validated like firearms. No ammunition needed.
- Turrets persist in `plugins/JasprApocalypse/turrets.yml` across restarts, never load chunks, cap at 6 per player / 60 total (`sentry` config section).
- Item lore uses short wrapped lines so tooltips fit the screen.

## Integrity and security

Current render correction: the tracking head uses the compact marker stand with a six-times-effective model (4x display transform plus baked 1.5x geometry) and calibrated visual mount offset. The model base is flush with the dispenser top, the barrel uses the target-forward transform, and existing compact/full-size heads are migrated on the next turret tick.

- Browser catalogue entry is the same inert request template as every custom item; Paper issues the canonical marked dispenser only to authenticated Creative players.
- Recipe preview/pickup revalidates exact plain-vanilla ingredients; tagged substitutes rejected; batched clicks denied.
- All damage uses cancellable Bukkit damage events with armor; shots are short-range siege noise like gunfire.
- Placement, pickup, break, and blast paths are logged (`SENTRY_*`); status via `apocalypse status` turret metrics.

## Verification

- `node --test tests/creative-catalogue.test.cjs` (5/5, incl. Blocks tab, turret/sentry search, NBT probe count 88).
- `scripts/build-apocalypse.ps1` candidate jar + Paper smoke suites before deploy.
- Deploy: graceful stop (maintenance mode), install candidate jar + rebuilt client `classes.js`, bump cache key, restart, confirm `SENTRY_READY` and `APOCALYPSE_READY` in logs.

Live address: <https://jaspr.chat/jaspercraft/>
