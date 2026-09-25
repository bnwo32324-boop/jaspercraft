# JasperCraft — HANDOFF (updated 2026-09-24 ~00:20 CDT)

GAME = C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale, SA = GAME\candidate\structure-audit.
Owner prefs: concise; low tokens; Sonnet for inspection, Opus for engineering; don't ask questions; never
backtrack; stop and hand off when either usage bar reaches 98%. Never create GAME\.runtime\maintenance-mode.

## CLOUD CHECKPOINT 2026-09-24 ~22:00 CDT (branch claude/awesome-cannon-fidcbt, PR #4) - resume here
PR #4 holds, all DONE and tested in the cloud (jars committed in server/plugins): Gear loot pass (JasprGear 3.1.0,
HB 3.27.0, importer 1.2.1), Backpacks (JasprGear 3.2.0, 5 tiers, in the Creative gear column), EasierCrafting rebuild
(client only, classes.js?v=20260924-gear6). Merging PR #4 as it stands ships those and is safe.

OPEN: owner request "All structures universally should spawn 1.5x whatever their current spawn rate is."
Source committed, jars NOT installed in server/plugins (deliberately: needs the steps below first).
- JasprHorrorBiomes 3.28.0 (server/custom-plugins/JasprHorrorBiomes, build: scripts/build-horror-biomes.sh):
  additive tier-2 layer behind new boundary jaspr-rates-v2 (only chunks generated after the upgrade): tertiary
  set-piece lattice (Megaliths C_CELL3/C_SALT3, per-kind calibrated), room lattice D (Dungeons D_CELL_D/D_SALT_D),
  vanilla spawner-room attempts 39->59, catalogue grids v8/v10/v9 (StructurePlanner tier2*, salt +50000),
  tier-2 sanctuaries (StructureRates.sanctuary2). Old layers never consult tier 2 -> no existing site moves.
  Verified: tests/java/chat/jaspr/biomes/StructureRatesProbe.java (seed 3127727864271777472, chunks 400..3400):
  old-layer fingerprints identical to 3.27.0; set pieces 65,610->98,477, rooms 301,092->452,370, sanctuaries
  207->314, catalogue 3,559->5,306 (1.49x); all tier-2 sites recognised (0 ERROR). Fresh test world, 1600 chunks:
  spawner rooms 40->62, 8.9->9.4 ms/chunk, RATES_V2_BOUNDARY_READY, no SEVERE.
- JasprImportedWorldgen 1.3.0 (patch sources server/custom-plugins/JasprImportedWorldgen/patch, build:
  scripts/patch-imported-worldgen.sh = 1.2.0 jar + patched classes, needs the HB 3.28 candidate jar first):
  grid 3 (26x26-chunk cells, jaspr-imported-v3.boundary, receipts cells3-<uid>), yields to grids 1/2; ClaimGuard
  treats every HB tier-2 site as a claim (reflection, SEVERE IMPORTED_GENERATION_REFUSED if missing). Refuses
  HB < 3.28 -> DEPLOY BOTH TOGETHER. Probe (fresh ground): 13,113 -> 19,991 sites (1.525x); small designs median
  1.59x; big designs 369 -> 318 (1.5x unreachable). Cloud counts used a catalogue rebuilt from census.csv
  (waterline/anchors guessed) and synthetic piece files, so they are approximate. Upgrade test on one world:
  old receipts/boundaries byte-identical, /where finds grid 1/2/3 sites, 0 SEVERE.
NEXT (main agent on the PC):
 1. Fix known gap: HB tier-2 admission does not know imported receipts decided before 1.3.0 but not yet built
    (~20% of those could get a tier-2 site inside their footprint, later overwritten by the import). Make HB
    tier-2 (StructureRates.permits2 path) also yield to plan boxes in plugins/JasprImportedWorldgen/cells-* and
    cells2-*, keeping old layers untouched; re-run StructureRatesProbe (old hashes must stay identical).
 2. Port the five patch classes (CellPlanner, CellLedger, Admission, ClaimGuard, ImportedWorldgenPlugin; keep
    GearLoot picks -> return n > 0) into C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923,
    rebuild 1.3.0 against HB 3.28 with the real catalogue, recheck counts with SA\work\r-gear probe\run_imp.sh.
 3. Test server with all live plugins (READY: HORROR_BIOMES_READY 3.28.0, RATES_V2_BOUNDARY_READY,
    IMPORTED_ASSETS_READY 1.3.0 grids=48+41+26, IMPORTED_BOUNDARY_READY ... protectedChunksV3=), then commit both
    jars to server/plugins in ONE commit and deploy together.
 Scratch evidence from the cloud session is gone with its container; everything needed is in git.
 Also still unanswered by the owner (offered earlier): Containment boss re-farm/glow, lowering mob drops,
 a key to open backpacks.

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
   - Buried set pieces' risers are not traced (their whole footprint counts as riser zone for big imported designs).

## PENDING OWNER REQUEST (2026-09-24 evening): full world regeneration
- Owner: "Regenerate the entire world; keep ALL my player data without exception (level, XP, every item with full NBT,
  backpacks AND their contents, gear slots); that's the only thing to preserve; everything else regenerate. Quick world backup first."
- Tool: scripts/reset-horror-terrain.cjs (copies+verifies protected files, moves terrain to world-resets/ = backup). Needs a new
  epoch (add 'rare-v8'; current is rare-v7) and requires .runtime/maintenance-mode + stopped server (owner rule normally forbids
  maintenance-mode: get explicit OK, keep it brief, remove after).
- Owner's player data = world/playerdata/<uuid>.dat (+stats, advancements), plugins/JasprGear/players/<uuid>.* (gear slots,
  vitals, mutation), backpack storage (check JasprGear 3.2.0: item NBT vs plugin file), Apocalypse life checkpoints/ranks, AuthMe
  account (keep or owner can't log in). Verify owner inventory + backpack contents after restart.
- OPEN QUESTIONS asked of owner: other players' data too? (tool keeps all by default); regenerate the Fold (jaspr_backrooms)
  and reset other plugin data (waypoints, graves, turrets, loot journals) - "everything else" suggests yes, but not accounts.
- Deferred to after the weekly usage reset (Sat 2026-09-26 09:00).
- OWNER ANSWERS (2026-09-24): regenerate the Fold (jaspr_backrooms) too; ALL players keep their player data (not just the owner);
  maintenance-mode shutdown for the reset is approved. Keep accounts/logins. Everything else (terrain, structures, loot journals,
  waypoints, graves, turrets, boundaries) regenerates. Note the tool deliberately protects jaspr_backrooms and all plugin data:
  it must be changed for the Fold and for plugin world-state, while still keeping per-player plugin data (JasprGear players/,
  backpacks, Apocalypse ranks/life checkpoints, AuthMe, skins/SSO).

## DONE 2026-09-25 13:59 (HB 3.27.2): blank enchanted books in structure chests
- Owner: enchanted books may appear in loot, but never empty. Cause found: JasprHorrorBiomes Dungeons.roll() case 15 returns
  `new ItemStack(Material.ENCHANTED_BOOK)` with no stored enchantment (ExpeditionLoot.book() is fine). Fix without moving the
  chest's Random r (it may be a builder stream): pick the stored enchantment from a separate source. Also check the importer
  (JasprImportedWorldgen) and vanilla-table chests. HB source is 3.28.0 with undeployed drift -> patch classes into the live jar.
- JasprFeral 1.0.0 is live (see AUDIT_LOG 2026-09-25 13:16).
