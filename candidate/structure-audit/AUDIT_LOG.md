# JasperCraft structure audit & repair — log

Started 2026-09-22. Conventions: `CONVENTIONS.md`. Owner criteria: `OWNER_CRITERIA.md`. Machine checklist:
`audit-state.json` (to be written from `inspections/*.json`). SA = this folder.

## CHECKPOINT 2026-09-22 ~18:15 CDT (paused for the owner's usage-limit reset)

Nothing is half-written. State on disk:

| item | state | where |
|---|---|---|
| Source drift | FIXED: canonical tree restored to 3.23.0, proven equivalent to the live jar | `recovery-report.md`, `recovery/verify.sh` (RESULT: CLEAN), backup of old tree in `backup-pc319/` |
| Capture hook | added (null in production; 8 lines in 5 files) | `harness/hook-patch/hook.diff` |
| Harness | working; matches live world cell-for-cell | `harness/`, `tools/capture.py`, `testserver-template/`, `jars/base323.jar` |
| Sites | 638 sites (reg 124, dun 28, cat 468, sanct 2, fold 16) | `sites.json` |
| Renderer/analyzer | working, real textures | `tools/` (README.md) |
| Inventory | 381 records, 334 in scope (47 biome-detail motifs out: density 0) | `inventory.json`, `inventory.md` |
| Valuables scan | 155 placement records; 13,757 valuable blocks in 51 structures | `valuables.json`, `valuables.md` |
| Grounds baseline | all 317 exported + rendered + analysed | `dumps|renders|analysis/grounds-w3/` |
| base323 capture | slot 0 (reg/dun/sanct/fold + 58 cat) and slot 1 (176 cat) were still running locally at checkpoint; they need no API usage and finish on their own | `runs/base323-0-1/`, `runs/base323-1-1/`, `dumps/base323/` |
| base323 renders | reg/dun/sanct/fold (170 dumps) DONE, 0 errors | `renders/base323/`, `analysis/base323/` |
| Inspection | NOT STARTED (a launch failed instantly on a bad args placeholder — 0 agents ran, nothing lost) | groups ready in `inspection-groups.json` (174 groups, 334 ids) |

## EXACT NEXT STEPS (resume here)

1. Confirm both capture runs finished: `runs/base323-0-1/results.jsonl` should have 286 lines and
   `runs/base323-1-1/results.jsonl` 352 lines (both with an "end" line), and `dumps/base323/` should hold
   1276 .jsd files. If a run died, re-run only the missing ids with `tools/capture.py --set base323 --ids ...`.
2. Render the catalogue captures:
   `cd tools && python batch.py base323 --only cat__ --skip-iso --workers 12`.
3. Launch inspection workflow A (non-catalogue, 84 groups) by re-invoking the saved script
   (`structure-inspection-wf_7534e9f8-d9d.js`) with args `{"set":"base323","groups":[...]}` where groups is
   the ACTUAL ARRAY of the non-`cat` entries of `inspection-groups.json` (the script cannot read files; the
   earlier launch passed a string placeholder and failed). Then workflow B with the 90 `cat` groups.
   Inspectors write `inspections/<stem>.json` as they finish, so a limit hit mid-run loses at most the
   in-flight groups; re-run only groups whose files are missing.
4. Aggregate inspections → `audit-state.json` + shared-defect clusters → shared fixes first
   (valuables palette; block physics: unpowered lit lamps / unsupported torches / falling sand; dungeon rooms
   overwriting set pieces and catalogue sites; Rapture register drift; catalogue staircase handrails and
   terrace strips), then per-source-file repairs in private tree copies with capture verification, then a
   full recapture + independent verification inspection, then deploy + Testing Grounds rebuild (w4).

## Plan (phases)

0. Infrastructure — DONE.
1. Baseline capture/render — in progress (see checkpoint).
2. Inspection — every structure looked at in its renders; classified; defects + root cause recorded.
3. Shared fixes first, then per-structure repairs grouped by source file.
4. Verification — recapture from modified source, re-render, re-inspect every repaired structure (both sites).
5. Deploy — plugin jar via `plugins\update\` + restart; rebuild the Testing Grounds world (w4).

## Findings so far (not yet fixed)

- Source drift confirmed and fixed (see recovery-report.md). The live jar was built with JDK 21.
- Live 3.23.0 quirks reproduced exactly and flagged (recovery-report.md): TerrainLighting dangling else;
  Dungeons.restock passes chunk coords to caves.region(); only Megaliths pieces + catalogue can be disabled;
  Where.java uses sites() not identify(); /wasteland status lacks spawnersDriven.
- Harness findings: Rapture register drift (builder cell 121 / 45x45 vs table 136 / 55x55 → /where cannot
  recognise real Raptures); dungeon rooms overwrite register set pieces (e.g. The Hive@b loses 2535 cells to
  Dungeons.sanatorium; overlaps at 15 register sites, 1 dungeon site, 32 catalogue sites); blocks lost after
  building (torches pop off, lit lamps turn off, sand falls, fire burns out, grass path → dirt); 16 register
  entries build ±1..5 blocks off their table footprint; The Spire dun:5@a missing in the live world (older ground).
- Valuables: iron block 7,259; gold block 4,410; redstone block 1,275; coal block 802; lapis 25; beacon 1;
  iron ore 26 (catalogue excavation rooms). Bombfall's redstone blocks sit under TNT (functional).
  Catalogue grammar already swaps iron/gold/diamond for plain blocks.
- Grounds-only analysis flags (to confirm in natural captures): #9 Neon Arcology has no entrance and ladders
  attached to nothing; #10 The Room upper flat floats in the grounds (natural capture shows it is a surface flat
  with a buried twin — context matters); floating fence handrails (e.g. 416 in #271 Canal Grid Metropolis);
  stacked lower iron-door halves in dun:8 / dun:12; AM's entrance ladder ends 16 blocks above the floor.
- 18:09 slot 0 capture finished (runs/base323-0-1); slot 1 still running.
- 18:12 RESUMED. Inspection workflow A (84 non-catalogue groups) running: run id wf_4adf24b6-0e0,
  script structure-inspection-wf_7534e9f8-d9d.js, args groups = runs/groupsA.json (line 2). Finished
  inspectors leave inspections/<stem>.json. If interrupted: resume with resumeFromRunId (cached agents replay)
  or re-run only groups whose inspection files are missing. Catalogue (workflow B, 90 groups) waits for
  slot 1 capture + `batch.py base323 --only cat__ --skip-iso`.
- 18:11 CHECKPOINT (usage 99%): inspections written so far: 0 files in inspections/ (workflow A
  wf_4adf24b6-0e0 may be cut off by the usage limit). Slot 1 capture: 226 result lines, finished=0.
  RESUME: (1) if slot 1 not finished, re-run missing cat ids into set base323; (2) render cat (batch.py base323
  --only cat__ --skip-iso); (3) resume workflow A with resumeFromRunId wf_4adf24b6-0e0 (same script + same args
  from runs/groupsA.json line 2) or re-run only groups lacking inspections/<stem>.json; (4) workflow B (cat groups).
- 18:55 RESUMED after the usage reset. The first workflow A (wf_4adf24b6-0e0) died on the limit with 0
  inspections written (all 84 agents errored; nothing to recover). Slot 1 capture finished: 638/638 sites OK,
  1276 dumps. Catalogue render running (runs/render-base323-cat.out). Workflow A RELAUNCHED with concurrency 8:
  run id wf_eb146b9d-9a5 (same script, now with a bounded worker pool; args = runs/groupsA.json line 2 plus
  "concurrency":8). If it is cut off, re-run only the groups whose inspections/<stem>.json files are missing.
- 19:10 Owner said "go": shared fixes started in private trees (SA/work/{blocks,placement,grammar}/tree,
  merged into SA/work/shared/tree) — workflow wf_78c6c651-fc9. Canonical tree untouched until inspections end.
  Inspection B (90 catalogue groups, concurrency 4) running: wf_cda97e77-ba1. Inspection A: wf_eb146b9d-9a5.
  Owner themes file copied to SA/STRUCTURE_INSPIRATIONS.txt and referenced from OWNER_CRITERIA.md.
- 23:55 RESUMED after the second usage limit. Survivors: 16 full inspections (reg:0-15, all "defective",
  high confidence) in inspections/. Shared-fix engineers died mid-task; partial private trees kept
  (work/blocks 12 files differ, work/placement 3, work/grammar 1) — relaunched to CONTINUE from them:
  wf_03208e7e-692. Remaining 318 structures: LEAN inspection (contact sheets a+b, sec_x, analyzer text,
  focus renders for suspects only; catalogue one family per inspector): wf_875b7851-2c4, args runs/groupsR2.json.
  Reason: full-depth inspection cost ~200-400k tokens per structure (usage limits hit twice).
- 2026-09-23 (after 3rd limit) RESUMED. Done so far: 65 inspections (reg:0-61, dun:0-2). Shared fixes:
  "blocks" DONE+verified (valuables 27,586 -> 0 on 236 captured sites; post-placement block losses 1,045 -> 5),
  "placement" DONE+verified (no overlaps with set pieces; Rapture recognised; side effect: ~24.5% of catalogue
  sites yield to set pieces in fresh ground — owner decision pending: keep, or raise catalogue density
  0.15 -> ~0.20 to compensate, which only ADDS cells). Grammar + merge resumed: wf_03208e7e-692 (resume).
  Remaining 269 inspections (47 groups): wf_5155ef9b-128, args runs/groupsR3.json.
- Owner chose COMPENSATE: after the merge, set StructurePlanner.RELATIVE_STRUCTURE_DENSITY 0.15 -> 0.20 in work/shared/tree (only adds cells; identify() unaffected).
- 2026-09-23 ~10:00 RESUMED (4th limit). Inspections 141/334 done (reg all, dun all, sanct, fold all,
  ruins all, cat: archive/auditorium/backroom/barracks/bathhouse/battle_tower/bunker). Grammar fix DONE.
  Merge (+ density 0.20) resumed: wf_03208e7e-692. Remaining 193 catalogue inspections (29 families):
  wf_f3b08b88-c3b, args runs/groupsR4.json. Next after both: aggregate -> audit-state.json, per-file repairs.
- 2026-09-23 10:25 Session restarted (live server restarted 10:09 by its gateway; no damage). Merge tree
  work/shared/tree complete (13 files, density 0.20) -> jars/shared.jar (md5 eb57109147483f7949320c7e43c185a8).
  sites-shared.json rediscovered (638, all found). Full capture set shared2 running on slots 5/6, auto-render queued.
  audit-state.json written (143 visual inspections: 115 defective, 2 severely incomplete, 25 passable, 1 good).
  DECISION (owner asked to save tokens): remaining 191 catalogue designs get ONE visual inspection on shared2
  (after shared grammar fixes) instead of a separate baseline pass; their baseline class = analyzer autoVerdict.
  Repairs workflow wf_163763d5-62d: stage 1 r-helpers (shared register/dungeon helpers) + r-extras (Fold,
  sanctuary, ruins); stage 2 per-file engineers r-landmarks/megaliths/anomalies/relics/metropolis/wonders/
  temples/breach/dungeons (private trees from work/r-helpers/tree; outputs repairs/<stem>.json).
- 11:00 Owner: more parallelism, Sonnet for inspections, inspect less. Repairs relaunched wider:
  wf_e20b8fe2-3d2 (r-helpers + r-extras + 7 independent file engineers at once; r-megaliths/r-dungeons start
  when r-helpers finishes). shared2 capture 638/638 OK; ANALYSIS ONLY for shared2 (full render cancelled).
  Catalogue triage (runs/cat-triage.json): of the 191 not-yet-inspected designs, after shared fixes the analyzer
  passes 173 (29 good, 144 passable = automated only, NOT visually inspected) and flags 18 -> Sonnet lean
  inspection on contact sheets: wf_2d2886ef-895.
- 11:05 18 flagged catalogue designs inspected (Sonnet, shared2): 13 defective, 5 passable. Catalogue repair engineer launched: wf_66c8ef0e-dc3 (r-catalogue, slot 8, grammar-level).
- 09-23 14:51 RESUMED after 5th limit. Done+verified: r-extras (Fold 16 + sanctuary + 6 ruins -> good; ruins diff separate: work/r-extras/apocalypse-changes.diff), r-breach (5 -> good). Resumed repairs wf_e20b8fe2-3d2 (helpers, 6 file engineers, then megaliths/dungeons) and catalogue wf_66c8ef0e-dc3; all continue from their private trees.
- 2026-09-23 ~16:00 Repair engineers ALL DONE (wf_e20b8fe2-3d2): 100 structures repaired + verified by their
  engineers (repairs/*.json): before 78 defective / 2 severely incomplete / 19 passable / 1 good ->
  after 88 good / 12 passable. Engineers' files are disjoint; merged into work/merge1 (= shared tree + all
  engineer files; catalogue engineer wf_66c8ef0e-dc3 still running) -> jars/merge1.jar (md5 ac5fb6a2...).
  Integration capture set merge1 (reg/dun/sanct/fold) running to catch regressions from combining the
  helper ground-clearing with engineers who worked without it (known risk: reg:6, 7b, 11, 14, 18).
- 2026-09-23 20:15 FINALIZING (no handoff; HANDOFF.md superseded). merge1 integration check: no real
  regressions (only Temples' wall-set dart-trap dispensers now counted "unreachable" because excavate
  cleared a tree outside the wall — analyzer artefact, by design). Sonnet contact-sheet verification of the
  46 set pieces repaired before the helper fix: wf_8eca4854-106. Catalogue engineer resumed: wf_66c8ef0e-dc3.
  Ruins: repaired Ruins.java copied into server/custom-plugins/JasprApocalypse (original unchanged since
  09-06; backup SA/backup-Ruins.java.orig); plugin NOT rebuilt/deployed (ruins disabled live; another agent
  edits JasprApocalypse gun recipes). DO NOT touch JasprImportedWorldgen or JasperCraft-Threefold-Structures-
  20260923 (another agent's work); tell owner when the live deploy is done.
- 09-23 20:19 Sonnet check of merged set pieces: 45/51 ok; 6 small floating pieces (reg:26b,32b,45a,51b,53ab,61a) -> fix-up engineer in work/merge1 (wf_0126d6f2-e6e), recapture set merge2.
- RULE: when 5-hour or weekly usage reaches 98% -> stop and write HANDOFF.md (check get_usage after each background job).
- 09-23 20:44 merge fix-ups DONE (work/merge1-fixups.md): footing/underpin reach ground, excavate removes orphaned tree pieces; 4 of 6 findings were capture-edge artefacts. jars/merge1.jar md5 b34429c2...; set merge2 = final reg/dun capture. Usage 30%/85%.
- 09-23 20:46 Grounds pipeline READY (grounds/rebuild.py, verify.py; 5 s each; all checks pass on test). Plan: trim oversize catalogue approach paths to fit 160-block plots (grounds only). Usage 30%/86%.
- 09-23 20:47 grounds/rebuild.py gained --oversize crop (keeps densest window = building; trims approach-path tail) + verify.py honours sourceCrop. Test out-test: 316/317 painted, PASS. Waiting on catalogue engineer, then final cat merge/capture, deploy, grounds.
- 2026-09-23 21:30 DEPLOYED stage 1 LIVE: JasprHorrorBiomes 3.24.0 (jar md5 462c5899a492447beba33fbd881529df;
  canonical tree = work/merge1 + version bump; previous canonical backed up to SA/backup-canonical-323hook;
  previous live jar in .runtime/plugin-backup-*-JasprHorrorBiomes/). Restart via game-server-stop.request,
  Done in 3.7 s, all READY lines present, relativeStructureDensity=20%, no errors. Contains: shared fixes +
  100 set-piece/dungeon/Fold/sanctuary repairs + merge fix-ups + first catalogue grammar pass.
  Stage 2 pending: catalogue grammar engineer (wf_66c8ef0e-dc3) -> second deploy; Testing Grounds w4.
- 2026-09-23 21:37 DEPLOYED stage 2 LIVE: JasprHorrorBiomes 3.24.1 (jar md5 8a556eb77657b5a55c6b2001a5555434) =
  3.24.0 + catalogue grammar pass (StructureArchitecture.java from work/r-catalogue). Clean restart.
- 21:40 TESTING GROUNDS w4 / stg-10 INSTALLED in site/ (world, index, classes.js, client.html; backups in
  SA/backup-site-*). Built from merge2 (reg/dun/Fold/sanct) + r-catalogue (cat) + r-extras-ruins, --oversize crop;
  verify PASS (317 plots, 316 painted, dun:14 kept; landings 317; spawn column clear). Public URLs serve new bytes.
  Confirmation capture set "final" (all 638 sites, jar 3.24.1) running -> compare, then final report.
- 2026-09-23 22:10 AUDIT COMPLETE. Live-build confirmation capture 'final' (638 sites, 3.24.1): 0 regressions vs verified sets. audit-state.json finalized; FINAL_REPORT.md written.
- 2026-09-23 23:24 DEPLOYED 1.5x STRUCTURE RATES (owner request) with ONE restart: JasprHorrorBiomes 3.25.0 (jar md5
  f15f70bb7f61ed92cc9cffadcc63e215; backup .runtime/plugin-backup-20260923-232229-JasprHorrorBiomes) + JasprImportedWorldgen
  1.1.0 (md5 93bbbf7fa6ada24c049405bda7ad4783; backup .runtime/plugin-backup-20260923-232229-JasprImportedWorldgen; source folder
  backed up to SA/backup-imported-worldgen-20260923-223902). Work in SA/work/r-rates (tree, imported, probe, cap.py).
  ADDITIVE: every 3.24.1 site keeps anchor/seed/blocks and recognition. HB: catalogue density 0.20->0.46 as tiers (tier 0 = old
  rules verbatim; tier 1 yields to everything older); per-structure secondary set-piece lattice (Megaliths.C_CELL2, own salt,
  yields to built primaries/higher secondaries/older catalogue+rooms/sanctuaries, no chunk shared with a same-kind primary);
  room lattice C (D_CELL_C/D_SALT_C); vanilla rooms 26->39 attempts; +50% sanctuaries (seeded share of square centres);
  jaspr-rates-v1.boundary keeps every new site out of the 6,074 pre-3.25 chunks (retrofit lays none); legacy region 229,242
  "Approach cannot reach surface" no longer throws (scan: 958,441 legacy regions to 500k blocks, 0 throws).
  Evidence (rates probe = plugin's own pure functions, old vs new jar): sites-shared.json 638/638 identical fingerprints;
  96k x 96k blocks outside r3072: 989,268 old sites 0 missing/0 changed, 491,422 added all recognised; ratios set pieces 1.491
  (62 ids 1.43-1.63), rooms 1.498 (1.48-1.54), catalogue 1.472, sanctuaries 1.561. Importer: census fix (plan-view clear of set
  pieces+excavation, rooms incl. buried shafts, new HB sites) + secondary 41-chunk grid with calibrated design weights: 8,753 ->
  12,906 plans (1.474x; Codex 1.456, GLM 1.486), plan-view conflicts with HB 3,030 (35%) -> 0. Captures rates-new (15 new sites,
  all built, 6 contact sheets inspected: complete, no clipping) and rates-imp (10 rooms 3 blocks from importer stamps: 0 HB
  blocks overwritten). Live: HORROR_BIOMES_READY 3.25.0, RATES_BOUNDARY_READY protectedChunks=6074, IMPORTED_BOUNDARY_READY
  grids=48+41, STRUCTURES_READY relativeStructureDensity=46%, no exceptions. Note: live autosave stalled 23:16:39-23:21:56
  (before this deploy; recovered by itself).
- 2026-09-24 00:16 DEPLOYED Survivor Gear structure loot + big imported designs with ONE restart: JasprHorrorBiomes 3.26.0 (md5
  ef932c8610984f668ce261f8b6662352) + JasprImportedWorldgen 1.2.0 (md5 e71c78bf4e79f206b367b5c4e8c7be64). Work: SA/work/r-gear (hb, imported,
  probe, gearprobe, verify). Backups: .runtime/plugin-backup-20260924-001507-{JasprHorrorBiomes,JasprImportedWorldgen}, importer source
  SA/backup-imported-worldgen-20260924-001507, canonical HB 3.25.0 tree SA/backup-canonical-325-20260924-001507. Canonical trees updated
  (custom-plugins/JasprHorrorBiomes; Threefold imported/ + build.sh); rebuilds are class-identical to the tested jars.
  LOOT: GearLoot (reflection, JasprGear's loader, cached, never throws; GEAR_LOOT_LINKED / GEAR_LOOT_UNAVAILABLE) -> GearApi.rollLoot at most
  once per chest: catalogue I-V -> 1-5 (last draw of the chest's own r), set-piece graded depth 0-4 -> 1-5 and troves 5 (from a serialized
  copy of the builder's Random keyed to the chest, so r is never moved), dungeon rooms 0 / rich 1 (the chest's keyed `luck` stream), restocked
  pre-3.21 chests (set piece depth+1, room 0); importer: ~2+tier of a placed design's chests picked per chest (seed+position stream), tier =
  design tier 1-5 (IMPORTED_GEAR_PLACED). In-server statistical fills (20k/row, live JasprGear jar): catalogue 4.9/8.0/11.9/17.7/25.0%,
  set pieces 5.0/7.7/12.1/18.0/24.5%, trove 25.2%, rooms 3.0% (rich 5.2%); highest rank = 2,2,3,4,5 by tier (rank 5 only at tier 5);
  max 1 trinket per chest; imported 0.14/0.30/0.59/1.06/1.73 trinkets per placed design (tier 1-5). Natural captures gear-a: set pieces 51 of
  395 chests, rooms 3 of 82, 9 big imported designs 5 trinkets. gear-a vs gear-c: 71 dumps (2,447 chests, gear included) identical across runs;
  gear-a vs gear-b (no JasprGear): 79 dumps identical block states and chest contents minus gear, builder-stream digest identical, 0 exceptions.
  BIG DESIGNS: primary grid keeps the 1.1.0 rule (all 6,178 probe plans identical). Secondary grid: designs >= 12,000 blocks^2 (23) are
  admitted by Occupancy (4x4 cells of their real records) against HB envelopes (set piece footprint+3 from floor-10 up, surface room +6
  clearing, buried room box + ENTRY shaft column to the sky, catalogue reserved box +16, sanctuaries as before, all +2) and search their whole
  cell; big-design multipliers recalibrated (probe/calib3 + damped pass); new jaspr-imported-v2.boundary (6,074 chunks at 1.2.0 start) keeps new
  secondary sites off every existing chunk. Rates (96k box, x of 1.0.0): cbd_101 1.67, B07 1.43, B15 1.86, cbd_034 1.43, B76 1.50, cbd_017 1.71,
  cbd_019 2.27, cbd_081 2.00, cbd_033 1.20 (all were 0 in 1.1.0); B61 still 0 (no collision-free site: 0 of 5,430 terrain-fit positions in 40
  fully scanned cells); small designs median 1.52x (1.1.0: 1.49x). Overwrites: 9 big designs stamped on a test server at the predicted boxes;
  21 HB sites meeting them (18 rooms incl. buried rooms under them, 3 set pieces): hookMismatch only the per-room-type torch/bed baseline that
  the 'final' captures show without any importer -> 0 HB blocks overwritten. Live: HORROR_BIOMES_READY version=3.26.0, IMPORTED_ASSETS_READY
  version=1.2.0, IMPORTED_BOUNDARY_READY protectedChunks=5865 protectedChunksV2=6074, GEAR_READY, STRUCTURES_READY; Done in 3.2 s; no new errors.
- 2026-09-24 owner: drop the 98% handoff rule; work until the usage limit.
- 09-24 09:43 PAUSED by owner. Gear Phase 2 and B61/recalibration jobs stopped before any edits (canonical sources unchanged since the 3.26.0/1.2.0 deploy; plugins/update empty). Live: HorrorBiomes 3.26.0, importer 1.2.0, JasprGear phase 1.
- 09-24 10:53 Cloud prep: GAME is now a git repo (allowlist .gitignore, .gitattributes '* -text', CLAUDE.md); server/.git renamed to server/.git-eaglercraft-template (template clone, reversible). 1 commit, 601 files, secrets/worlds/player data excluded (scanned). Waiting for owner to create a private GitHub repo.
- 2026-09-24 11:50 DEPLOYED Survivor Gear Phase 2 (cloud session PR branch claude/gear-phase2, commit 6d750db) with ONE restart, 0 players online:
  JasprGear 2.0.0 rebuilt locally from the reviewed source with JDK 17 (md5 67d17915b29c5b159636535d25aa6684; class bytes differ from the
  cloud JDK 21 jar only by compiler codegen) + site classes.js (md5 8798ec86...), assets.epk (md5 f224e540...), client.html, jaspr-client.js
  (versions 20260924-gear2); client builders re-run locally reproduce both byte-for-byte. Local test server (all live Jaspr* plugins minus
  VoiceChat/WorldReset, port 25597): GEAR_SELFTEST PASS checks=612 failures=0. Live: GEAR_READY items=15 consumables=5 statuses=5, Done 2.77 s,
  no new errors (EaglerXServer ForceAliveListener + AuthMe GeoLite warnings pre-existing), 3310 status running, 3200 health 200, site serves
  new versions. Backups: .runtime/plugin-backup-20260924-114952-JasprGear, .runtime/site-backup-20260924-114952-gear2. Not yet play-tested
  by a real Eaglercraft client on the live server.
- 2026-09-24 13:25 DEPLOYED JasprApocalypse 3.6.1 (owner request): in CREATIVE, left-clicking a waypoint in the waypoint menu teleports
  there (also sets it tracked); survival unchanged (left = track). Lore says "Left: teleport" in Creative. Built as a PATCH of the live
  3.6.0 jar (only WaypointMenu*.class + plugin.yml replaced) because the JasprApocalypse source has undeployed drift (Ruins restoreRails,
  SiegeRules) - a full rebuild would ship that too. Test server load clean; live APOCALYPSE_READY 3.6.1, 0 players, one restart.
  Backup .runtime/plugin-backup-20260924-132510-JasprApocalypse.
- 2026-09-24 PR (cloud, not deployed) Gear loot pass: JasprGear 3.1.0 (GearApi.CHANCE 0.4/0.6/1/1.5/2.5/4% by tier 0-5, a prefix of the
  old 3-25% band so seeds that still roll a trinket roll the same one; boss drops; Nether Star + diamond/emerald block recipes),
  JasprHorrorBiomes 3.27.0 (jaspr_boss scoreboard tag on Containment + encounter bosses; ExpeditionLoot.fold now rolls gear at tier 3;
  nothing placed or recognised changes), JasprImportedWorldgen 1.2.1 (1.2.0 jar + recompiled GearLoot: every chest rolls, was ~2+tier per
  design; PC source still needs the same change). Test server with all live Jaspr jars: GEAR_SELFTEST PASS 994, probe PASS 100,807,
  gear bots 28+22+3 pass.
- 2026-09-24 PR (cloud, not deployed) Backpacks: JasprGear 3.2.0 (5 tiers, 18/27/36/45/54 slots; recipes from 6 leather + 2 string up
  to diamond blocks + shulker shells; GearApi.BACKPACK_CHANCE 3.0-5.0% by tier above the supply band, tier shares 50/25/14/8/3%;
  vanilla loot-table containers roll GearApi once at tier 1; contents on disk by uuid). No structure/placement change. Test server:
  GEAR_SELFTEST PASS 1090, gear-backpack-bot 13/13 + restart 1/1, phase2/phase3/boss bots and browser client pass.
- 2026-09-24 PR (cloud, not deployed) Structures 1.5x (all generators): JasprHorrorBiomes 3.28.0 (tier-2 layer behind boundary
  jaspr-rates-v2: tertiary set pieces, room lattice D, vanilla spawner rooms 39->59 attempts, catalogue grids v8/v10/v9, tier-2
  sanctuaries; old layers' fingerprints identical to 3.27.0; 48k box old->new: set pieces 1.50x, rooms 1.50x, sanctuaries 1.52x,
  catalogue 1.49x; fresh 1600-chunk test world: spawner rooms 40->62, +6% ms/chunk, RATES_V2_BOUNDARY_READY, no SEVERE) +
  JasprImportedWorldgen 1.3.0 (grid 3 behind jaspr-imported-v3.boundary, aware of HB tier 2). Deploy the two together.
- 2026-09-24 17:21 FULL WORLD REGENERATED (owner request): new seed -6436856966336135554 (all 3 worlds), Fold regenerated. Kept: world/playerdata, stats, advancements (66 files, hash-verified), all per-player plugin data (JasprGear gear/backpacks/vitals, Apocalypse survivors/stats, AuthMe). Moved to world-resets/full-regen-2026-09-24T22-20-18-653Z: terrain, world data, boundaries, jaspr_backrooms, HB caves-v11 relocation/encounters/loot journal/discoveries, imported-protection-v1, importer cells receipts, turrets, waypoints, graves. Players get relocated once on join (relocated-caves-v11.yml reset). Live healthy, 0 import tile failures.
- 2026-09-24 18:46 JasprApocalypse 3.6.2 LIVE: SiegeTraversal.halt() re-sent zero velocity to stalled siege zombies every tick (entity velocity packet 20/s each; measured ~300 msgs/s to the client, owner felt ~1 s mouse lag). halt now only acts when horizontal speed > 1e-3. Patched into the live jar (only SiegeTraversal.class + plugin.yml). Backup .runtime/plugin-backup-*-JasprApocalypse (18:46).
- 2026-09-24 20:52 JasprHorrorBiomes 3.27.1 LIVE (owner: halve all passive mob spawns): SpawnBalance keeps 17.5% of wild non-sheep animals (was 35%), 50% of sheep (was 100%), 50% of squid/bats (was 100%); NATURAL + CHUNK_GEN only. Patched into the live 3.27.0 jar (SpawnBalance.class + plugin.yml) because source is already 3.28.0 (undeployed). Backup .runtime/plugin-backup-20260924-205221-JasprHorrorBiomes.
- 2026-09-25 08:12-08:16 JasprGraves LIVE, two owner-reported fixes, one restart each, 0 players online:
  1.1.0: break a gravestone with any tool (or empty hand in Creative), not only a pickaxe -- removed the pickaxe()
  gate in onBreak (owner request).
  1.2.0: LADDER added to Headstone.soft() -- findSpot() treated a ladder block as solid, so a death on a ladder
  walked the grave down to the first true-air gap, sometimes many blocks below the death point (owner report:
  died on a ladder at world -126,28,-3, grave landed at -126,13,-3, 15 blocks down -- confirmed no data was
  lost, just placed low; GRAVE_RAISED had logged stacks=21 xp=3439). Now the grave settles right at the ladder.
  Both patched into the live 1.0.0 jar; no source drift to worry about (JasprGraves had none pending).
  Backups .runtime/plugin-backup-20260925-081253-JasprGraves, .runtime/plugin-backup-20260925-081618-JasprGraves.
- 2026-09-25 11:01 JasprGear 3.2.1 LIVE (owner request): the adrenaline HUD bar only shows while an adrenaline-spending item is worn (Capacitor Belt, Phase Headset, Scrap Magnet), the mutation's R ability costs adrenaline, or a status effect is running (HUD clients get no other status notice). Server-side: GearVitals.hudWanted() feeds the hud packet's existing 'on' flag; no client change. Self-test PASS 1094 checks (4 new). Backup .runtime/plugin-backup-20260925-110128-JasprGear.
- 2026-09-25 11:12 Blight Filter LIVE (owner request): JasprGear 3.3.0 + JasprBlight 1.1.0 + client (classes.js/assets.epk/client.html/
  jaspr-client.js, versions 20260925-filter1), one restart, 0 players. New trinket BLIGHT_FILTER (any slot, rank 1, model 35, own 16x16
  art): immunity to blight water. Cheap recipe SIS/CBC/SPS (string, iron ingot, charcoal, glass bottle, paper; no Nether Star, no
  blocks). Craft-only (GearItem.loot=false, GearItem.LOOT pool) so structure loot, boss loot and mob drops are unchanged -- the
  Phase 1 loot-parity self-test passes. Mechanism: JasprGear sets player metadata "jaspr_blight_immune" while worn (cleared on
  unequip/quit/disable); JasprBlight skips players carrying it; JasprGear also cancels poison damage while the wearer touches water
  (belt and braces). Recipe panel: recipe-table.json re-exported from a test server with all live plugins (diff: +1 recipe, 0 removed),
  sync-recipe-table -> build-recipe-book-client --upgrade -> build-gear-client; Creative catalogue gains gear_blight_filter.
  Self-test PASS 1129. Backups .runtime/plugin-backup-20260925-111152-filter, .runtime/site-backup-20260925-111152-filter.
- 2026-09-25 11:22 Recipe panel scroll wheel fixed (site only, no restart; classes.js?v=20260925-wheel1): the client's canvas wheel handler calls stopPropagation(), so the recipe book's bubbling document 'wheel' listener never fired. Now a capture-phase window listener (passive, never blocks the game), only for a panel drawn in the last 500 ms. Backup .runtime/site-backup-20260925-112201-wheel.
- 2026-09-25 11:31 Melee recipe overhaul LIVE (owner request; guns untouched, another agent owns them): JasprApocalypse 3.6.3 =
  live 3.6.2 jar + new Blueprints.class only (source still has undeployed Ruins/SiegeRules drift). All 24 melee weapons out-hit a
  diamond sword; cost now scales with power = sqrt(DPS ratio x per-hit ratio) vs the diamond sword, four bands ~5-6.5 / 9-12 /
  12.5-16 / 18.5-20 diamond-equivalents (diamond sword = 2). Logical shapes: short blades and swords one stick, axes/picks/hammers/
  scythe two sticks, polearms two diagonal sticks, baton and whip leather grips; thematic parts (blaze rods on fire weapons, redstone
  battery + gold contacts on the shock baton, bone on the ossuary/grave weapons). tests/gun-recipe-blueprints.test.cjs passes (no
  collisions, no vanilla shadowing); recipe-table.json re-exported (24 changed, 0 removed); client classes.js?v=20260925-melee1.
  Backups .runtime/plugin-backup-20260925-113124-JasprApocalypse, .runtime/site-backup-20260925-113124-melee.
- 2026-09-25 12:44 Weapon/armor model flicker fixed (site only, no restart; assets.epk + jaspr-client.js build 20260925-flicker1).
  Cause: z-fighting -- 60 of the apocalypse item models had same-facing coplanar overlapping faces (e.g. Tempest top plate vs its
  side rails, steel vs coal). New apocalypse-pack/zfight.cjs pushes the smaller (detail) element out by 0.05 model units per
  conflict until none remain; applied to the generated models (arsenal-expansion.cjs models()) and once to the static ones (sentry
  turret eye edited by hand: its file has a custom layout). build-apocalypse-pack.cjs now fails on any z-fighting pair.
  arsenal-legacy-contract.json: 19 model sha256 pins updated deliberately. apocalypse-assets tests 7/7 pass (orientation checks
  included); EPK merge changed item models only. Backup .runtime/site-backup-20260925-124423-flicker.
- 2026-09-25 13:16 NEW PLUGIN JasprFeral 1.0.0 LIVE (owner: "make all passive mobs hostile, each with a unique fighting style";
  before this nothing passive was hostile). 16 types / 14 styles: cow stampede-charge, mooshroom spore bursts (nausea), pig ram +
  eats 2 food, sheep headbutt stagger, chicken fast pecks + rallies the flock, rabbit leap, horse/donkey/mule bite / rear-kick /
  buck, bat nip-and-flutter, squid ink (blind) in water, parrot dive-bomb, polar bear maul + 4 s bleed, ocelot pin, llama spit
  (+nausea), wolf pack (rallies wild wolves). Friendly: villagers, tamed/owned, leashed, name-tagged, ridden, babies; nothing in
  Peaceful/Creative/Spectator. Wolves/polar bears/ocelots/llamas use their vanilla attack AI via setTarget; the rest are steered by
  their own NMS pathfinder (repath every 4 ticks) and strike with real mob damage (armour/difficulty apply). Panic and avoid-player
  goals are removed from an animal when it turns. Bounded: 1 s sweep per player (14 m, line of sight), max 160 hunters, velocity only
  on a discrete move (never per tick). FERAL_READY / FERAL_METRICS (every 5 min) / /feral (op: turned + hits per style).
  Test: mineflayer bot on a test server with every live plugin -- every style turned and landed hits; named + baby cow never
  attacked. Test-harness notes: vanilla /tp is shadowed by Apocalypse TPA (use minecraft:tp); /summon failed on the test server
  (spawnEntity works). One restart, 0 players.
- 2026-09-25 13:59 JasprHorrorBiomes 3.27.2 LIVE: no more blank enchanted books (owner). Dungeons.roll() case 15 returned a bare ENCHANTED_BOOK; now LootBooks.random() (one non-curse enchantment at a random valid level, own RNG so the chest Random is not advanced). LootBooks also enchants any blank book when a container is first opened (position-seeded; catches already-rolled chests and imported designs; LOOT_BOOKS_FIXED log). Hosted from SpawnBalance.start() so HorrorPlugin (drifted source) is untouched. Patched into live 3.27.1 from commit 48487dc's Dungeons (bytecode-identical to live) + SpawnBalance + LootBooks; same edits in the 3.28.0 source. Bot test: 3 blank books in a chest -> Sweeping Edge I, Flame I, Luck of the Sea III. Backup .runtime/plugin-backup-20260925-135903-JasprHorrorBiomes.
- 2026-09-25 14:28 WORLD DIFFICULTY -> EASY (owner). Found while debugging JasprFeral (hunting=0 live): the main world had been
  PEACEFUL since 2026-09-17 via JasprApocalypse config `difficulty: peaceful` (it overrides server.properties). Peaceful also zeroes
  all creature damage to players, so feral animals, sieges and monsters could never hurt anyone. Owner: "anything above peaceful
  should make the mobs hostile; default world setting easy". Live plugins/JasprApocalypse/config.yml difficulty: easy, server.properties
  difficulty=1, plugin default (resources/config.yml) easy. One restart, 0 players; level.dat Difficulty reads 1 after autosave.
  Backup .runtime/config-backup-20260925-142754-difficulty. (Correction: an earlier answer to the owner said "Hard" from
  server.properties; the effective setting was Peaceful.)
