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
   - Survivor Gear Phase 2 LIVE 2026-09-24 11:50 (JasprGear 2.0.0; see AUDIT_LOG). Next gear work: Phase 3 (mutations, Creative gear column, worn models).
   - Buried set pieces' risers are not traced (their whole footprint counts as riser zone for big imported designs).
