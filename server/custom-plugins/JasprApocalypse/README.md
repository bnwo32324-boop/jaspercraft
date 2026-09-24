# JasperCraft: The Last Broadcast

A server-authoritative zombie apocalypse for the existing Paper 1.12.2 / browser client. No Forge, client JavaScript gameplay patches, or new authentication system. This plugin requires AuthMe and the existing patched Paper API. The current candidate is plugin version 3.4.0.

## Survival

- The overworld runs on Hard. Existing inventories, accounts, player data, game modes, and worlds are retained; new players still default to survival. The Nether and End remain available for late-game progression.
- Six undead variants: Ash Shamblers, Feral Runners, Concrete Breachers, TNT Carriers, Hollow Revenants, and Grave Wardens. They survive daylight but do not glow. Every third night is a Blood Moon with increased supernatural variants.
- Pursuit acquires authenticated survival/adventure players within 48 blocks, even behind walls. Zombies navigate, jump, slowly dig toward trapped players, and can build up to six rubble steps toward elevated players. Pursuit stops beyond range; this is local siege behavior, not unlimited pathfinding across unloaded terrain.
- Wooden defenses take about four seconds of uninterrupted digging, stone about seven, metal about twelve; breachers and wardens dig faster. Obsidian, bedrock, beds, containers, and infrastructure blocks are excluded. Terrain can be destroyed around a base; this is intended apocalypse gameplay.
- TNT Carriers warn with a fuse before a localized blast. Default radius is 2.6 blocks, capped at 32 destroyed blocks per blast. Block changes and explosions honor cancellable Bukkit protection events. Explosions also damage nearby entities; they do not spawn chain-reaction vanilla TNT.
- Revenants slow victims; Grave Wardens can summon shamblers. Relics drop from dangerous supernatural enemies, not ordinary spawner zombies. New introductions receive a short grace period and a field guide; respawns receive 45 seconds of grace.

## Wall movement

Every player has the wall-jump ability from their first spawn. No item, armor, permission, authentication state, or special client install is required. While airborne and touching a wall, hold the normal sneak control (Shift on a keyboard; the client’s native sneak control on touch devices) to cling. The player is held against the wall briefly, then slides down slowly. Release sneak to launch away from the contacted wall with the researched default vertical boost of 0.55. The same wall cannot be immediately re-clung after a jump; the player must drop about one block or contact a different wall first.

The controller is server-authoritative and applies to all online players. It uses four thin collision probes and normal Bukkit movement events, so it works with the existing browser client and mobile clients without a gameplay patch. The server cannot see a client’s raw W-key state, so it uses the freshest horizontal movement intent available and falls back to the player’s facing direction; releasing sneak therefore launches where the player is trying to go instead of always kicking backward from the wall. Runtime counters are included in `/apocalypse status`, and startup emits a `WALL_JUMP_READY` diagnostic.

The research record and behavior comparison are in [WALL_JUMP_RESEARCH.md](../../../WALL_JUMP_RESEARCH.md).

## Public teleport requests

Every authenticated player can use consent-based teleportation; operator status is never consulted. `/tpa <player>` asks to teleport to that player, and the legacy `/tp <player>` spelling now sends the same request instead of moving anyone immediately. `/tpahere <player>` asks that player to teleport to the requester. The recipient chooses `/tpaccept` or `/tpdeny`, while the requester can use `/tpcancel`. Requests expire after 60 seconds by default and are discarded when either player disconnects.

Administrative vanilla teleportation remains available through the namespaced `/minecraft:tp` command. Request creation, acceptance, denial, cancellation, expiry, and failure are recorded as bounded `TPA_*` diagnostics without logging chat content.

## Lost ruins

Newly populated overworld chunks can contain clustered ruined districts and isolated remnants. Families are town houses/shops, motels, watchposts, occult chapels/standing stones, underground bunkers, and salvage yards/waterworks. Each has variants, rotated layouts, rubble, weathering, and sparse caches.

Sites reject water, steep slopes, trees, overhangs, caves beneath foundations, existing tile entities, nearby players, or intervening player edits. Foundations extend into the ground. Construction never crosses its own chunk or loads more terrain. Existing explored chunks are **not** retrofitted or regenerated.

`ruins-ledger-v1.bin` reserves each built chunk durably before editing, preventing duplicate caches after restarts. Never delete this file while retaining its world. Interrupted builds are not replayed. A full/corrupt journal disables new generation with a log warning instead of risking duplicated loot. Generation defaults to 100 edits per tick with at most 32 queued plans; all Bukkit work stays on the main thread, journal I/O on a single bounded worker.

## Late-game arsenal

| Weapon | Damage before armor | Magazine | Specialty |
| --- | --- | --- | --- |
| Last Light rifle | 32 | 18 | Accurate sustained fire |
| Requiem shotgun | 9 pellets, 10 each | 6 | Heavy close-range damage |
| Gravebreaker railgun | 100 | 3 | Pierces up to three enemies |

All 35 firearms are craftable from vanilla materials. Recipes scale from iron-block pistol frames to heavier receivers, precision optics, feed mechanisms, redstone controls and rare conductors; no custom item is a gun ingredient. Firearms can also be recovered from expedition loot. The firearms, melee weapons, exoskeleton sets and field supplies are documented in [the equipment contract](../../../apocalypse-pack/EQUIPMENT.md). Right-click to fire; sneak-right-click to reload. Exact grids appear in the crafting-table recipe browser. All guns begin empty, retain NBT magazines, and consume plain vanilla iron nuggets as ammunition; marked Military Salvage is protected from consumption. No gun is issued free on joining. The stable loot entry point is `ApocalypseItems.gear(String id)`.

Shooting respects collision shapes, solid walls, armor, PvP policy, and damage-event cancellation. Reloads cancel on incompatible inventory changes. Crafting checks tagged ingredients again server-side; gun shift-crafting is disabled to prevent serial/round duplication. Ordinary diamond hoes retain their normal appearance and behavior.

The 47 JSON item-model entries can be merged into the candidate browser `assets.epk`, so players need not accept a separate download. Existing vanilla textures are reused. Models are bounded to 32 cuboids, with muzzle facing -Z and sights above the grip (+Y); both first-person hands have explicit transforms. The original gun models are preserved. Armor has distinct item models and full-set perks; worn armor retains the native diamond silhouette. Optional standalone ZIP generation is also available.

## Build and verify

From the game project root:

```powershell
./scripts/build-apocalypse.ps1
node scripts/build-apocalypse-pack.cjs --check
node scripts/merge-apocalypse-assets.cjs
node tests/apocalypse-smoke.cjs --paper --java-home="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
node --test tests/sso-browser.test.cjs
```

Build outputs go into `candidate/apocalypse`, never directly into the running server. The smoke harness starts its own random-loopback-port Paper instance with a disposable world and no production auth/world data. Detailed results remain under `candidate/apocalypse-smoke-*`.

Install only after tests pass: gracefully stop Paper through the existing maintenance scripts, copy the candidate JAR to `server/plugins/JasprApocalypse.jar`, copy the candidate EPK to `site/assets.epk`, bump the asset/loader cache keys and recorded hashes, then use `scripts/start-game-server.ps1`. Do not use `/reload`. The existing always-on gateway/supervisor handles restarts.

Diagnostics: `APOCALYPSE_READY`, minute-level `APOCALYPSE_METRICS`, and bounded `APOCALYPSE_BREACH type=tnt` events in Paper logs. Console/authorized admin `apocalypse status` shows tracked undead, queued ruins, breach totals, and blasts. No per-frame HTTP telemetry, credentials, or chat content is logged by this plugin.

## Performance limits

There is additional gameplay work, not a claim of zero overhead. Defaults cap tracked siege zombies at 80, local ambient encounters at 14 per player, digging at six blocks per half-second pass, ruin writes at 100 per tick, and gun ray candidates at 256. AI and rays do not load distant chunks. Limits can be tuned in `plugins/JasprApocalypse/config.yml` after observing live metrics.
