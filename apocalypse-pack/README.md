# Jaspr Apocalypse equipment assets

The stable loot API, all 45 IDs, weapon statistics, full-set perks, supplies, recipes, and verification instructions are documented in [EQUIPMENT.md](EQUIPMENT.md).

The pack contains 47 native Minecraft 1.12 item-model JSON files: eleven distinct firearms, eight melee weapons, sixteen armor pieces, six item selectors and six vanilla fallbacks. All textures resolve against the bundled vanilla client archive. No generated bitmap images, modern CustomModelData, client JavaScript patches, or OptiFine extensions are required.

The original Last Light, Requiem and Gravebreaker geometry and transforms are unchanged. New firearms have individual suppressor, bullpup, cannon, scout, plasma, marksman, scattergun and flechette silhouettes. Exact unbreakable durability bands select custom models; adjacent bands and ordinarily damaged vanilla items retain the vanilla model. Armor item models are custom, while equipped armor still renders with the native diamond armor texture/silhouette.

Gun geometry uses −Z for the muzzle, +Y for the sights, and −Y for the grip. First-person transforms lift the muzzle six degrees. Third-person `[90, 0, 0]` compensates Minecraft's hand transform and living-model inversion. Both hands, inventory, ground and item-frame transforms are explicit. The builder verifies all 44 gun held orientations and all 9,886 durability/unbreakable cases. Manual browser-fixture aiming and device-specific clipping remain a separate validation step.

Use `ApocalypseItems.gear(String id)` or `ApocalypseItems.expedition(String id, int tier)` for rare loot. All guns start empty with a unique persisted serial and magazine. `catalogue()` and `catalogue(category)` expose immutable ID/name maps. Original crafting remains available alongside rare-loot firearms. AuthMe and survival/adventure restrictions apply in the configured world, Nether, End and `jaspr_backrooms`. The equipment predicate does not broaden siege or ruin generation.

Arsenal owns its original recipes and four new marked-material recipes, with exact tagged ingredient checks at preview and pickup. Normal single crafts require an empty cursor. Gun/melee/armor repair and anvil transformations are blocked. `ExpeditionEquipment` owns full-set attribute modifiers, supplies and melee behavior; its lifecycle is minimally hooked into `ApocalypsePlugin`. All lifecycle and factory calls run on the server thread.

From the project root:

```powershell
node --test tests/apocalypse-assets.test.cjs
node scripts/build-apocalypse-pack.cjs --check
node scripts/build-apocalypse-pack.cjs --output candidate/apocalypse/jaspr-apocalypse.zip
node scripts/merge-apocalypse-assets.cjs
```

The ZIP builder packages static models and `pack.mcmeta` deterministically. The EPK merge reads the current client archive and writes only `candidate/apocalypse/assets.epk` plus its report; it validates every texture/model dependency, preserves unrelated resources byte-for-byte, and is idempotent. It does not change live site assets, installed plugins, source/runtime config, or worlds. Candidate publication and the final combined smoke/UI gate belong to the coordinating task.
