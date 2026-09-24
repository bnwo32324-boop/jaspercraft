# JasperCraft — The Lost Expeditions

## Release intent

Replace both the 16%-per-chunk biome ruin shells and the older Apocalypse district generator. The new system uses authored multi-room layouts, biome-specific eligibility, regional spacing, terrain-derived terraces and chunk-clipped generation. Existing 62-biome terrain, native gore, account authentication, settings synchronization and survival progression are retained.

This records the original v2 release, installed on September 7, 2026 (Central time). Its initial public-routing blocker was resolved, and the strict public gate passed at 2026-09-08T06:31:35.861Z. See [the v3 biome-detail expansion](BIOME_DETAILS_UPDATE.md) for the subsequent catalogue, frequency and regeneration changes.

## Finding and looting structures

- There are 104 authored layouts across all 62 replacement biomes, including an exclusive structure for each biome. Cities, castles, towers, schools, aircraft wrecks, bunkers, sewers, mansions, houses, telecom ruins and submerged settlements use different connected room graphs.
- Regional selection replaces per-chunk ruin spam. Candidate anchors are at least 768 blocks apart; biome and terrain checks can make the actual distance greater. Difficulty ranges from tier I to V.
- `/wasteland structures` explains difficulty, cache types and dimensional thresholds. `/wasteland atlas` retains all 62 biome entries.
- Tier I is refuge/scavenging; II supplies and basic combat; III dangerous expeditions; IV elite; V apex. Guardians seal the final vault in tier III–V sites.
- Supplies, medical rooms, armories, relic caches and guardian vaults use different weighted loot tables. Books, food, fuel, farming supplies, ammunition, materials, equipment and trophies are distributed across the layout, not one universal chest.
- Tier IV/V armories can contain firearms. Tier IV/V guardian vaults guarantee a firearm, one exoskeleton component and marked ammunition. Tier III rewards include special melee weapons and crafting materials. New equipment is not given automatically on login.
- Caches are shared and finite: looting, emptying, reconnecting, chunk unloading and server restarts never refill them. Hopper extraction cannot claim an unopened cache or bypass a guardian seal.
- Guns start empty; main-hand right-click fires and sneak + right-click reloads from marked Forged Cartridges. Equipment uses validated item tags, not its display name.
- Exoskeleton bonuses require matching equipped sets. Each set has trade-offs; removing it removes only its own modifiers.

## The Fold

Rare strange thresholds enter `jaspr_backrooms`, a separate persistent server world using the client-compatible NORMAL protocol environment. Its doors connect rooms through a non-spatial graph, across different elevations. This is not a new client protocol dimension ID.

Return points are written atomically before travel. Right-click the foyer light or use `/wasteland return` to escape; missing or obstructed return coordinates fall back to a safe normal-world spawn. The protected foyer is the only protected building area. World travel does not clear inventory, experience or game mode. Room caches use the same durable claim policy.

## Operational safeguards

- Structure generation writes only the chunk being generated; it does not load neighboring chunks. Regional plans use bounded caching.
- Encounter entities and projectiles have explicit caps and loaded-chunk checks. Native living entities feed the existing dismemberment renderer and lighting. No emissive/glowing mobs are introduced.
- Cache claims are forced to an append-only checksum journal before their inventory side effect. A torn/corrupt journal fails closed and logs an operator error; it is never silently reset. A process loss in the small claim-to-inventory window can forfeit that cache rather than duplicate it.
- Guardian claims, phases, deaths and summon budgets are durable. An unloaded entity is not treated as dead. Only confirmed defeated guardians unlock sealed vaults.
- Diagnostic messages use `STRUCTURE_*`, `LOOT_*`, `ENCOUNTER_*` and `LIMINAL_*` prefixes in the server log. `/wasteland status` is operator-only and reports bounded runtime counters.
- Any terrain regeneration must stop Paper first, preserve player/account records, preserve the seed and world identity, and use a fresh relocation generation marker. Terrain-linked loot/encounter journals must stay paired with their corresponding terrain.

## Siege awareness and vertical pursuit

The update also includes an original server-side awareness system inspired by [Corosus's Zombie Awareness](https://coros.us/mods/zombieawareness). It does not require Forge or a client mod download. The game client and its account-linked settings code are unchanged.

- Sight considers distance, occlusion, ambient light, sneaking and invisibility. Noise and wound scents leave temporary locations to investigate. Gunshots use the same memory; Whisper's suppressed shots have an 8-block sound radius, compared with 40 for ordinary firearms.
- Ordinary siege zombies can build a cobblestone pillar up to 16 blocks to reach an elevated target. They jump first and place only after their body clears the new block. Wall Crawlers can climb physical walls up to 48 blocks and cross clear roof edges. Their traversal attempts are time-limited, and each zombie's pillar material budget persists across chunk reloads.
- Pursuit requires an eligible signed-in Survival/Adventure player. Signals expire, and pursuit is abandoned when the owner escapes, disconnects, changes world or becomes ineligible. Newcomer grace remains in effect.
- Movement, sight checks and block edits share bounded work budgets. Managed distant hunters are selectively activated; the global monster activation radius is not increased. The system does not generate chunks to chase someone.
- Block changes respect `mobGriefing`, cancellation events, collision and occupied-space checks. Pillars have finite material and height limits; wall climbing cannot pass through ceilings. Metrics include pillars, wall steps and active stimuli.

`/apocalypse status` reports siege counters to operators. Existing weapons, armor and materials are documented in [the equipment guide](apocalypse-pack/EQUIPMENT.md).

## Verification and deployment

- Final packaged plugins pass the combined Paper fixture: 31,445 assertions, 35 naturally selected sites and 104 designs. The earlier real-browser threshold test entered and left the Fold while preserving the player's inventory, experience and mode, including a vanilla-cancelled empty-hand click.
- Equipment runtime: 1,023 assertions passed. Existing apocalypse compatibility and shared-budget runtime: 64,050 assertions passed. Dedicated siege physics/awareness: 179 checks passed with zero runtime chunk loads. Guardian recovery passed 97 runtime assertions across restarts, including low-view-distance missing-boss recovery.
- The final 38 client/auth/settings/gore/biome/asset/loot regressions passed. Structural architecture validation separately checked all 104 rendered layouts and their biome mappings.
- Terrain in the Overworld, Nether and End was replaced using the guarded reset. Old terrain remains recoverable in `world-resets/structures-v2-2026-09-08T01-15-29-106Z`; no player or world root was deleted. All 24 player saves, 24 stats files and 24 advancement files remain byte-identical after restart. Of 94 protected files, 93 are byte-identical and AuthMe's log has only appended startup diagnostics. Seeds, default Survival and all game rules were preserved in all three worlds.
- Both plugins report version 2.0.0; the repetitive legacy ruin generator is disabled. The public-facing local gateway reports running, SSO-ready, always-on, zero idle shutdown and no game-server failure. The client runtime and account-profile code remain unchanged; only the equipment archive and cache identifiers changed.
- Verification report: `candidate/structures-release-report.json`. The initial 01:19Z check was blocked by the public route serving a standby without JasperCraft. This was resolved separately: the replacement 06:31Z report has `localVerified`, `publicAssetsVerified` and `verified` all true, with the public client and exact equipment archive served by the primary site. This historical routing failure is no longer an outstanding deployment task.
- An existing EaglerXServer/SkinsRestorer `ForceAliveListener` compatibility warning also appears in pre-update logs and is not introduced by these plugins. It does not prevent game readiness.

The original release gate is complete. Future regeneration must use an explicitly new terrain/loot/encounter epoch; never discard the Fold's shared loot claims. The v3 expansion implements that guarded migration.

Limits: zombie steering handles local obstacles, not arbitrary mazes; detection requires loaded chunks and eventual escape is possible. The runtime tests use controlled players, not a full multiplayer load test. Worn exoskeletons retain the native diamond-armor rendering limitation documented in the equipment guide.
