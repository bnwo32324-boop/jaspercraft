# JasperCraft rare-world reseed

## Live result

The `rare-v6` terrain epoch replaces seed `4425965048829651136` with seed `4184677908398476141` in the Overworld, Nether, and End. The worlds were regenerated from the new seed; the separate Fold/backrooms world was not regenerated.

Custom structures now have two independent discovery constraints:

- Both the original 1,024-block grid and the expanded 384-block grid retain exactly 10% of their former candidate probability. This is a 90% reduction globally.
- No custom structure footprint may enter a 3,072-block radius around world spawn. This prevents a lucky seed from putting expedition content in the opening survival area.

The retained distant structures still use all 234 biome-specific designs and their existing loot, encounter, boss, surface, buried, and underwater rules.

## Preservation and recovery

The reset archive is `world-resets/rare-v6-2026-09-12T18-48-43-848Z`. Before terrain was moved, every protected file was copied to that archive and hash-verified. The reset changed only regenerated terrain, three world seeds, the safe Overworld spawn, obsolete End terrain references, the terrain epoch marker, the expansion boundary, and terrain-linked turret/vanilla structure indexes.

Inventories, Ender Chests, XP, saved game modes, health, statistics, advancements, accounts, authentication data, account-linked settings, skins, survivor ranks/checkpoints, loot claims, and Fold state were preserved. Returning players receive the existing one-time safe relocation for the new terrain epoch.

## Verification

- Candidate admission sample: 4,000,000 cells per grid.
- Original grid observed retention: 9.9703%.
- Expanded grid observed retention: 9.9772%.
- New seed nearest custom-structure footprint: 3,072.9 blocks from spawn.
- Isolated Paper structure test: 101,190 assertions, 234 designs.
- Metadata and preservation suite: 86 passing tests, including deliberate tampering of inventories, modes, XP, seeds, gamerules, unknown NBT, recovery copies, and terrain targets.
- Live release verifier: 269 protected files verified; 34 player NBT files, 34 statistics files, and 34 advancement files retained; public and local sessions matched.

