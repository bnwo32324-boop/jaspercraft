# JasperCraft structure audit & repair — final report (2026-09-23)

Live: JasprHorrorBiomes **3.24.1** (jar md5 8a556eb77657b5a55c6b2001a5555434), Testing Grounds **w4 / stg-10**.
Checklist: `audit-state.json` (one record per structure). Log: `AUDIT_LOG.md`. Per-structure evidence:
`inspections/*.json` (before), `repairs/*.json` (after).

## Counts (reconcile to the inventory)

| | count |
|---|---|
| Inventory records | 381 = 334 in scope + 47 biome-detail motifs (density 0 everywhere, never generate) |
| In scope | 62 set pieces + 15 dungeon rooms + 234 catalogue designs + 6 ruins + 16 Fold rooms + 1 portal sanctuary = 334 |
| Visually inspected before repair | 143 (all set pieces, dungeons, Fold, sanctuary, ruins, 43 catalogue) |
| Visually inspected after the shared fixes (Sonnet) | 18 catalogue designs flagged by the analyzer |
| Automated only (analyzer, not looked at) | 173 catalogue designs (29 good, 144 passable by the analyzer after the shared fixes) |
| Baseline classes (visual, 143) | 115 defective, 2 severely incomplete, 25 passable, 1 good |
| Individually repaired and visually verified | 121 (100 set pieces/dungeons/Fold/sanctuary/ruins + 21 catalogue designs) → 90 good, 30 passable, 1 defective on one site |
| Changed by shared/grammar fixes | all 334 (valuables, physics, overlaps, grammar passes) |
| Replaced by new designs | 0 |
| Still unresolved / unverified | cinder_hospice_morgue site a not enterable (another generator's street plate); 173 catalogue designs not individually looked at; dun:14 not capturable (code + grounds only); ruins verified offline only (disabled live) |

Live-build check: all 638 test sites recaptured with 3.24.1 — 0 regressions against the verified versions.
Baseline → live totals over 638 sites: valuable blocks → 0; floating structure blocks 31,523 → 0 (analyzer);
unreachable chests/spawners 614 → 124; mean dark interior 41% → 22%. (The analyzer's empty-room count rose
because deeper footings make captures include cave air — a measuring artefact, checked by the engineers.)

## Shared root causes fixed

1. Valuable construction blocks (gold, iron, diamond, emerald, lapis, redstone, coal blocks, beacon, iron ore) →
   same-colour ordinary blocks (27,586 in the captured sites → 0). Loot tables unchanged.
2. Block physics after generation: unpowered lit lamps, unsupported torches, falling sand, draining water/lava
   (CaveSprings chunk-edge bug), fire, grass paths, stacked door halves.
3. Placement overlaps: dungeon rooms and catalogue sites stamped over set pieces → they now yield
   (admission only; recognition unchanged). Catalogue density 0.15 → 0.20 to keep the structure count.
4. Rapture register drift: /where now recognises real Raptures.
5. Shared helpers: surface sites clear their footprint (no hills inside rooms), buried floors packed over caves,
   ladder shafts reach real floors and daylight, floating spawners/chests set down, orphaned debris removed,
   buried dungeon rooms got entrances, footings/foundations reach ground.
6. Catalogue grammar: floating handrails/terraces, approach paths that met water/air/no entrance, dark interiors
   (one torch per storey), barren tower/bunker/backroom rooms, doorways vs terrace heights, piers.

## Limitations

- Only newly generated chunks get the repaired structures; already-explored chunks keep old copies.
- The Fold world is already generated; its room repairs apply only to a regenerated Fold.
- Ruins stay disabled live; their repaired source is in JasprApocalypse (not rebuilt/deployed).
- Structures straddling explored/unexplored ground may show a seam between old and new versions.
- Sea/lake catalogue designs are reached by swimming to a landing stair; some pillar supports over big caves
  look heavy.
