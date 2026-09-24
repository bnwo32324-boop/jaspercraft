# JasperCraft — HANDOFF (updated 2026-09-24 ~00:20 CDT)

GAME = C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale, SA = GAME\candidate\structure-audit.
Owner prefs: concise; low tokens; Sonnet for inspection, Opus for engineering; don't ask questions; never
backtrack; stop and hand off when either usage bar reaches 98%. Never create GAME\.runtime\maintenance-mode.

## DONE and LIVE
- Structure audit complete: JasprHorrorBiomes repairs (SA\FINAL_REPORT.md, SA\audit-state.json).
- Testing Grounds w4 / stg-10 installed in GAME\site.
- 1.5x spawn rates for ALL structures: JasprHorrorBiomes **3.25.0** (md5 f15f70bb7f61ed92cc9cffadcc63e215) and
  JasprImportedWorldgen **1.1.0** (md5 93bbbf7fa6ada24c049405bda7ad4783), live since 23:24. Additive (no old
  site moved), new sites only in new ground (boundary jaspr-rates-v1). Imported structures no longer collide
  with HorrorBiomes structures. Catalogue planner crash fixed. Census of rates: SA\census\CENSUS.md
  (BEFORE the 1.5x). Sources: GAME\server\custom-plugins\JasprHorrorBiomes and
  C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923 (importer).

## IN PROGRESS / NEXT
1. Survivor Gear (trinkets, mod-parity, apocalyptic theme) — background agent building a new plugin
   GAME\server\custom-plugins\JasprGear + TestServerControl allowlist + client patches (site\classes.js,
   client.html, assets.epk). Spec: real Baubles-style inventory slots (amulet, 2 rings, belt, head, body,
   charm), 15 trinkets with the mod's abilities re-themed (real mechanics, not potion effects), own 16x16
   pixel art, effects/mana-equivalent/HUD (phase 2), 9 races as mutations (phase 3); power rank 1-5 per
   item and public API chat.jaspr.gear.GearApi.rollLoot(Random, tier) / create(id) / isGear(item).
   Documentation: GAME\GEAR_UPDATE.md (written per phase).
2. DONE 2026-09-24 00:16 (live, one restart): Survivor Gear in structure chests + big imported designs =
   JasprHorrorBiomes 3.26.0 (md5 ef932c86...) + JasprImportedWorldgen 1.2.0 (md5 e71c78bf...); details, rates and
   evidence in AUDIT_LOG.md (entry 2026-09-24 00:16); work in SA\work\r-gear (probe\run_imp.sh = importer rates probe,
   gearprobe = in-server statistical fill plugin, verify\cap_gear.py + analyze_gear.py = capture checks).
   Notes: catalogue caches are filled on first open, so never-opened caches in old chunks (and pre-3.21 restocks) can
   now hold a trinket too; claimed chests are untouched. Megaliths.loot() has no caller (wired at tier 1 anyway).
3. NEXT (open):
   - glm:B61 (199x212, solid footprint) still never places: no collision-free site exists at 1.5x HB density
     (0 of 5,430 terrain-fit positions in 40 scanned cells). Only fixable by letting buried-room shafts yield to it.
   - The secondary-grid multipliers for the 23 big designs were calibrated on counts of 3-30 per 96k box (noisy,
     1.1-2.3x of 1.0.0); recalibrate with probe\calib3.py if an exact 1.5x matters.
   - Survivor Gear Phase 2 LIVE 2026-09-24 11:50 (JasprGear 2.0.0; see AUDIT_LOG). Phase 3 (mutations, Creative gear column, worn models, EasierCrafting recipes; JasprGear 3.0.0, versions gear3) is in the claude/gear-phase3 PR: live only after merge + Deploy JasperCraft.bat.
   - Gear loot pass (JasprGear 3.1.0 + JasprHorrorBiomes 3.27.0 + JasprImportedWorldgen 1.2.1, versions gear4; stacked on Phase 3): bosses always drop a trinket, every chest of every generator rolls 0.4-4% by tier, trinket recipes need a Nether Star + diamond/emerald blocks. See GEAR_UPDATE.md "Loot and bosses". The committed importer jar is a one-class patch of 1.2.0: apply the same change to the PC importer source (chat.jaspr.imported.GearLoot.picks -> `return n > 0;`, see server/custom-plugins/JasprImportedWorldgen/patch/) before its next rebuild, or that rebuild silently reverts it.
   - Backpacks (JasprGear 3.2.0, versions gear5, same PR branch claude/awesome-cannon-fidcbt): five tiers (18-54 slots, leather recipes rising in cost), right-click to open, contents in plugins/JasprGear/backpacks/<uuid>.pack; a 3-5% backpack band in GearApi.rollLoot (every structure chest) plus vanilla loot-table containers. See GEAR_UPDATE.md "Backpacks".
   - EasierCrafting panel rebuilt on a full server recipe export (client only, classes.js?v=20260924-gear6): all vanilla + plugin recipes, grid-size aware, shift-crafting, blueprint-safe clicks. See EASIERCRAFTING_UPDATE.md; re-export with scripts/export-recipes.sh whenever a plugin's recipes change.
   - Structures 1.5x again (owner 2026-09-24, same PR branch): JasprHorrorBiomes 3.28.0 + JasprImportedWorldgen 1.3.0, DEPLOY BOTH TOGETHER (the importer's ClaimGuard must know HB tier-2 sites). HB: new boundary jaspr-rates-v2 (chunks generated after the upgrade), tier-2 layer = tertiary set-piece lattice, room lattice D, vanilla spawner-room attempts 39->59, catalogue grids v8/v10/v9 (salt +50000), tier-2 sanctuaries. Older layers never consult tier 2, so no old site moves (per-layer fingerprints identical to 3.27.0: tests/java/chat/jaspr/biomes/StructureRatesProbe.java, seed 3127727864271777472, 48k box). Counts old->new: set pieces 65,610->98,477, rooms 301,092->452,370, sanctuaries 207->314, catalogue 3,559->5,306 (1.49x); test server 1600 fresh chunks: spawner rooms 40->62, 8.9->9.4 ms/chunk, no errors. Importer: grid 3 (jaspr-imported-v3.boundary); the committed jar is built from patch/ sources over 1.2.1 - port patch/ into the PC importer source before its next rebuild.
   - Buried set pieces' risers are not traced (their whole footprint counts as riser zone for big imported designs).
