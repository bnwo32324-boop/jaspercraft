# JasperCraft — Wasteland Details v3

## Content

All 62 replacement biomes now have an explicit detail recipe: three characteristic landmarks, a distinct material palette, and biome-specific litter. There are 47 landmark geometries and 12 litter styles, including broken moorings, culverts, signal wrecks, grave plots, arches, votive roots, ice shards and fossil ribs. Trees also vary in height, branching, roots and canopy shape. The existing biome identities, atmosphere, progression resources and terrain seed are retained.

The catalogue grows from 104 to **230 authored structures**: 62 new exclusive biome dungeons and 64 additional shared sites. The expansion contains 26 new room motifs and 111 distinct room-graph silhouettes. Additions include freight loops, observatories, catacombs, foundries, bathhouses, archives, conservatories, mortuaries, research stations and monasteries. Of the 126 additions, 63 are buried, 11 underwater and 52 surface designs. Existing boss, finite-cache and tiered equipment systems apply to the new sites.

## Frequency and placement

The original regional plans remain stable. A supplemental 384-block grid adds smaller and medium discoveries, with candidate anchors at least 352 blocks apart. Biome ownership, water depth, terrain, reserved spawn/portal areas and spacing from existing major sites can reject a candidate. Actual travel distances therefore vary; this is not a promise of a structure every 384 blocks.

A fixed 150,994,944-block sample contains 81 original sites plus 902 new sites, 465 of them buried: approximately 12.14 times the former total site density. All 62 biomes have exclusive and shared additions. The new system does not restore the old identical-per-chunk ruin generator.

Details are generation-only, grounded, rotated and collision-checked. Each chunk attempts 3–4 small landmarks and six litter clusters, subject to a hard 224-block detail budget; native Nether/End detail is capped at 96 blocks. Walkways, spawn, portal sanctuaries, native fortresses, End cities and dragon infrastructure are excluded. No decorative entities, extra tick loop, ore giveaways or new glowing mobs are added.

## Regeneration and persistence

The guarded `--details-v3` reset regenerates the Overworld, Nether and End while preserving their seeds and UUIDs. Player saves, inventory, Ender Chests, experience, game modes, statistics, advancements, accounts and configuration are protected. Players receive a one-time safe-spawn relocation when returning to the changed terrain. The separate Fold world is not regenerated.

Old loot and boss journals are retained. New Overworld caches use the `v2:details-v3` claim namespace and new encounters use `structure-encounters-details-v3.bin`; existing `fold-v1` claims retain their original namespace. Entering the Fold records the terrain epoch, so a return from a pre-reset trip cannot teleport into obsolete terrain. An immutable chunk boundary also protects old chunks if this expansion is deployed without a reset in another installation.

The reset first creates an exact preservation manifest and recovery archive. An offline verification gate checks every protected byte and permits only the specified spawn/obsolete-dragon metadata changes. After startup, the public gate checks the running plugin, fresh generation boundary, preservation evidence, always-on gateway, matching public/primary site session and exact public client assets. It explicitly distinguishes locked live journals and normal world-session metadata from byte-identical files.

## Verification

- Detail geometry: 4,702,725 checks across all 62 biomes, 1,504 shape variants, nine Nether and five End profiles.
- All 230 structures: 77,098 architecture/access checks and 144,568 expansion checks; malformed and reordered catalogues rejected.
- Density, terrain eligibility, region cache, immutable old-chunk boundary and unchanged legacy selection: 21,726 checks; 622 old site identities retain the same fingerprint.
- Real Paper biome sweep: 2,563,254 assertions; 62 deterministic biomes; 2.775 ms average raw chunk generation on this fixture, not a multiplayer load benchmark.
- Real Paper structures/loot: 38,972 assertions over 47 naturally selected sites. Old claims cannot consume fresh-world loot; old Fold claims cannot refill; restarting cannot duplicate rewards.
- Guardian restart fixture: 101 checks across two server boots, plus 22 journal-contract checks.
- Fold travel/recovery: 1,452 slice assertions and a real browser threshold round-trip preserving inventory, experience and game mode.
- Combined geometry/reset regression run: 16 tests passed. Separate client/auth/settings/gore/loot regressions: 30 tests passed.
- Offline preservation gate: 81 tests passed, including tampered inventories, modes, seeds, unknown NBT fields, Fold claims, missing terrain moves and incomplete recovery archives.

## Deployment record

Live and publicly verified at **2026-09-08T07:42:34.334Z**. The Overworld, Nether and End were regenerated using `world-resets/details-v3-2026-09-08T07-41-41-147Z`; their old terrain remains recoverable there. No player directory or world root was deleted. The Fold and its existing loot claims were retained.

All 103 protected files passed byte-for-byte verification with Paper stopped. After startup, all 24 player saves, 24 stats files and 24 advancement files remained byte-identical. Of the 103 protected files, 100 were still byte-identical; AuthMe's log only appended, the Fold's normal session lock changed, and the live-locked loot journal was covered by its exact offline verification. All three seeds, game rules and default Survival modes were preserved.

The production plugin matches the tested SHA-256 `fc4af0dcac209bda8943e30e4d86141fef268073d2120334caa30216ea666c4c`. The fresh world reports zero protected old chunks, 62 detail profiles and 230 structures. The gateway reports running, SSO-ready, always-on, zero idle shutdown and no failure. Both public and local site checks identify the unchanged primary session; the public game client and exact equipment archive pass verification. The website and public tunnel were not restarted.

Definitive release evidence: [live verification report](candidate/details-v3-release-report.json), [deployment manifest](deployment-manifest.json), and the reset archive's `reset-manifest.json` / `offline-verification.json`.
