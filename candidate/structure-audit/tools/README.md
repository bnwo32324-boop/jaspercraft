# Structure audit tools

`SA` = `C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit`.
All tools are Python 3.13 + numpy + Pillow (+ opencv for 2-D labelling, optional). No scipy.
Run them from this folder (`SA\tools`). Everything follows `SA\CONVENTIONS.md` (ids, layout, JSD1).

| file | what it is |
|---|---|
| `jsd.py` | JSD1 reader/writer. `read_jsd(path) -> Dump` with `states` uint16 `[y, z, x]` (file index `(y*SZ+z)*SX+x`), `mask` uint8 or None, `header` dict. Helpers: `.ids`, `.data`, `.at(x,y,z)`, `.crop(x0,y0,z0,x1,y1,z1)` (half-open, shifts origin + tiles), `.iso()` (mask==1 only), `.nonair_bbox()`, `.trimmed()`, `write_jsd`, `new_dump`, `split_state/pack_state`. `python jsd.py f.jsd` prints header + top ids. |
| `mcids.py` | 1.12.2 block names 0-255, item names 256-453 + records, `item_id("minecraft:iron_ingot") -> 265`. |
| `stgw.py` | Structure Testing Grounds world reader + plot exporter (see below). |
| `textures.py` | Reads `site\assets.epk` and caches block textures in `tools\cache\textures.npz`. |
| `blockinfo.py` | numpy lookup tables per block id: passable, collision, opaque, light emission, climbable, stairs/slabs, valuables policy table, furnishing ... (shared by analyzer and renderer). |
| `blockmodels.py` | 3-D models (textured quads) for every 1.12 id 0-255 x data. |
| `render.py` | Textured isometric / plan / section renderer. |
| `analyze.py` | Automated metrics per dump. |
| `batch.py` | Multiprocessing render + analyse of a whole set. |
| `compare_appendix.py` | Confusion matrix of `analyze.py` auto-verdicts vs the handoff's Appendix A. |
| `selftest.py` | JSD round trip + a synthetic *natural* (masked) dump through render + analyze. |

## Quick start

```
python stgw.py info                          # container facts
python stgw.py export                        # -> SA\dumps\grounds-w3\*.jsd + _map.json   (~10 s)
python batch.py grounds-w3                   # render + analyse all 317 (14 workers, 3-5 min)
python batch.py grounds-w3 --no-render       # analysis only (~20 s)
python batch.py grounds-w3 --only reg__17,cat__rustwater     # file-name prefixes
python render.py ..\dumps\grounds-w3\reg__17__g.jsd          # one dump, all views
python render.py ..\dumps\grounds-w3\reg__17__g.jsd --views iso,plan
python render.py ..\dumps\grounds-w3\cat__canal_grid_metropolis__g.jsd --focus 97,5,4,8   # close-up of a suspect
python analyze.py ..\dumps\grounds-w3\reg__17__g.jsd
python compare_appendix.py grounds-w3
python selftest.py
```

## Testing Grounds export (`stgw.py`)

Container `site\jaspercraft-grounds-world.js` (STGW v3, big-endian; exact encoding taken from
`JasprGroundsUnpack` in `site\classes.js`): `"STGW"`, u32 version=3, u16 len + folder name, **u8** dimension,
u32 named-file count, each (u16 len + path, u32 size, bytes) = `level.dat`; u32 chunk count (18,867), each
(i32 cx, i32 cz, u32 size, gzip NBT `{Level:{xPos,zPos,Sections,TileEntities...}}`); no bytes left over.
Chunk sections: `Blocks` (index y*256+z*16+x), optional `Add`, `Data` nibbles (low nibble = even index).
World: superflat, ground top y=3, structures from y=4, chunks x 0..119, z 0..420.

Export per plot (index `site\jaspercraft-grounds-index.js`: `px,py,pz,sx,sy,sz` = structure footprint):
* crop = the footprint box; the plot border ring, corner posts and gate signs lie outside it.
* the plot interior outside the footprint (and everything above it up to y=255) is scanned: in 70 plots the only
  such blocks are a 1-block glass ring = the grounds' **glass tank**. The crop is widened to include it,
  `header.grounds.glassTank = true` and `header.grounds.footprintRel = [x0,z0,x1,z1]` gives the real footprint
  inside the dump. `render.py` and `analyze.py` crop the tank away (the tank is not part of the live structure).
* tiles: chests (items as `[numeric id, damage, count]`, 0 unknown item names), spawners (`mob` = Bukkit name,
  `mobId` raw), signs, other tile kinds. Totals match the handoff: 387 chest tiles (all stocked), 465 spawners;
  2,446 chest blocks (the 2,059 catalogue chests have no tile entity = empty by design).
* context `"grounds"`, variant `g`, no mask; `header.grounds` has number, author, group, family, tier, mode,
  plot, footprint, landing, `groundY` (world y 3).
* ids: `reg:k` = index in `Megaliths.C_NAME`; `dun:i` = index in `Dungeons.D_NAME` - **D_NAME has only 14
  names; the grounds' "Spawner Room" (`Dungeons.plain`) is exported as `dun:14`** (assumption, noted in
  `_map.json`); `cat:<tsv id>` (all 234 match `catalog-v1.tsv` by id, name, family and tier); `ruin:<family>`
  (`JasprApocalypse Ruins.Family`: town, motel, watchpost, shrine, bunker, salvage).
* `_map.json`: number -> id, name, author, kind, group/family, tier, mode, footprint, exported origin/size,
  file, glassTank, tile counts.

## Renderer (`render.py`)

Textures: real 1.12 textures from `site\assets.epk` (EPK v2: `EAGPKG$$`, version/name/comment/time/count,
compression byte `G` = gzip, stream of `HEAD` / `FILE` (u8 name len, name, u32 len incl. 4-byte crc, data,
`:>`) / `END$`, trailer `:::YEE:>`). 478 block textures + chest/bed/sign entity sheets; animated textures use
frame 0; grass/foliage/lily use fixed plains tints. Models cover ids 0-255 with data: wool/terracotta/
concrete/powder/glass/pane colours, plank/log/leaf/sapling species and log axes, stone/sandstone/red sandstone/
quartz/prismarine/stonebrick variants, slabs top/bottom and double slabs, stairs (facing + upside-down; straight
shape only), fences/nether fences/walls/panes/iron bars with neighbour connections (unconnected pane = cross like
1.12), carpets/pressure plates/rails (incl. curves and slopes)/snow layers/redstone/repeaters/daylight
detectors/trapdoors as plates, ladders/vines/wall signs/buttons/levers/wall torches/banners as wall-attached
pieces by facing, doors as 3/16 panels (open/hinge from both halves), torches/flowers/crops/webs/saplings/
tall plants as crossed sprites, liquids (level heights, internal faces culled) and glass translucent,
light sources emissive (unshaded + warm lift). Unknown ids render as a magenta checker.
Not modelled: stair corner shapes, double-chest merging, sign text, banner patterns/bed colours (tile data),
entities (paintings, item frames, armour stands are not in dumps).

Projection: 2:1 dimetric (30 deg down), integer pixel lattice, painter's order `sx*x + sy*y + sz*z` (exact for
grid cells), blocks hidden behind full opaque cubes are culled. Scale = largest multiple of 4 px/block
(<= 64) that keeps the image <= 1600 px.

Output `SA\renders\<set>\<id>__<variant>\`:

| file | content |
|---|---|
| `iso_NE/NW/SE/SW.png` | whole exterior from each corner; grounds: ground plane with a 4-block grid, compass (N red, E) |
| `iso_under.png` | from below (SE corner, 30 deg up) - undersides, floating pieces |
| `cut_yNN.png` | SE iso with everything above y=NN removed; tops of cut blocks are dark red ("cut surface") |
| `plan_yNN.png` | top-down plan per floor: floor = y NN-1 (floors 1-3 blocks lower drawn greyed), contents NN and NN+1; walls darkened + outlined, 1-high gaps hatched orange, "no floor within 4 below" pink with red X, symbols for ladders, doors, trapdoors/gates, chests (C), spawners (S), stairs (arrow = up), light sources (yellow >= 8, orange < 8), beds/workstations (letter), valuable blocks (gold diamond), water/lava; grid every 4 (16 bold) with dump-relative labels; legend |
| `sec_x.png`, `sec_z.png` | vertical sections: plane x = SX//2 seen from east (z axis, north at right) / plane z = SZ//2 seen from south; 3 layers behind dimmed |
| `contact.png` | 4 iso views + up to 4 plans for triage |
| `zoom_NE|SW_rRcC.png` | only if the iso scale is < 16 px/block: 2x-scale tiles of the NE and SW views |
| `focus_x_y_z_*.png` | on demand (`--focus`): close-up iso SE/NW (cut above y+2, red marker) + plan of one position |
| `views.json` | floor levels, px/block, files, timings |

Floor levels: greedy cover of "roofed standable" cells (feet passable, head passable, support below, a block
with collision somewhere above), max 10, each plan covers feet levels NN-1..NN+1.

Natural dumps (`hasMask`): `ctx_iso_*` (terrain desaturated; buried sites: terrain above the structure's roof
removed per column, terrain outside the footprint cut to the structure's base except on the two far sides of
each view, where it is kept up to the structure's top as a rock backdrop showing the contact) and `str_iso_*`
(mask==1 only, trimmed); `iso_under` = structure only; cut/plan/sec from the ctx volume.
Tested with synthetic masked dumps (`selftest.py`); no real natural capture existed yet.

Coordinates in all labels are dump-relative array coordinates; world = `header.origin` + relative. For grounds
dumps with a tank the render volume is the footprint; titles print the adjusted origin.

## Analyzer (`analyze.py`) -> `SA\analysis\<set>\<id>__<variant>.json`

All positions are dump-relative `[x, y, z]` (tank offset added back) plus `world`.

* `histogram` `[id, data, name, count]`, `counts`.
* `valuables`: every block of the CONVENTIONS policy table (41, 42, 57, 133, 22, 152, 173, 138, ores 14, 15, 16,
  21, 56, 73, 74, 129, 153) with count, replacement, positions (<= 400/id); redstone blocks next to a consumer
  are listed in `powersSomething`. `workstations` (anvil, enchanting table, brewing stand, ender chest).
* `components`: 6-connected components of structure solids; structure liquids connect (pools, moats);
  grounded = touching terrain/ground, and on grounds/iso dumps also touching the structure's lowest occupied
  layer. Floating ones classified tiny (1-4), fragment (5-63), mass (>= 64) with size, bbox, samples, block mix;
  `floatingByBlock`, `tinyFloaterPositions` (`[x,y,z,id]`, repair input).
* `ladders`: runs with top status (exit / ceiling / air), bottom (floor / side-exit / jump / hangs N), unattached
  ladder blocks, reachable.
* `stairs`: stairs in runs (>= 2 same-facing steps); headroom < 2 above a step or a run top leading into a
  wall/ceiling; `blockedReachable` counts only stairs the player actually uses.
* `doors`: lower halves whose front/back cell (y, y+1) is blocked, missing upper halves, and stacked lower halves.
* Reachability BFS (feet cells): 2-high clearance, walk, jump 1 (needs head room; not onto fences/walls/gates),
  step onto stairs/bottom slabs/low blocks without jumping, fall <= 3 (any height into water or onto a ladder),
  climb ladders/vines, swim, pass doors/trapdoors/fence gates, lava impassable. Starts: natural = sky-exposed
  standable cells in the outer 2-block ring; grounds/iso = the ground ring around the footprint **plus every
  sky-exposed standable cell** (roofs, terraces, shaft tops - on the flat grounds the live terrain around
  buried pieces is missing). Plinth heuristic: when the bottom layers are >= 85 % solid with a fully solid
  outer ring, virtual terrain is added around the structure up to the plinth top (`reach.plinthLayers`).
  Also reported: reach from the ground ring only, and a lenient BFS accepting falls up to 20.
* `enclosed`: interior air (passable, roofed by the structure, inside the footprint); accessible fraction
  (safe and with fall damage); sealed pockets (>= 8 cells, no link to the boundary or sky); unreachable regions
  (>= 8 cells) split into walled-off (`unreachable-room`) and open-but-unreachable (`unreachable-floor`).
* `interactables`: chests, trapped chests, spawners, beds, crafting tables, furnaces, anvils, enchanting tables,
  brewing stands, ender chests, cauldrons, jukeboxes, hoppers, dispensers, droppers: reachable (a 26-neighbour
  is a reachable feet/head cell), `lidBlocked` (opaque block above a chest), spawner `spawnSpaces` (2-high air on
  a solid floor within the vanilla spawn box) and mob.
* `light`: block light (emitters, -1 per step, water/ice -3, opaque stops) + sky light (open columns 15, spreading
  -1): dark fraction (< 8) of reachable floor, of reachable interior floor, and interior at night (block light only).
* `rooms`: per floor, 4-connected roofed standable cells (doors separate rooms; grounds y=0 skipped); area,
  furnishing count (functional + decorative non-structural blocks at feet/head level, incl. adjacent cells),
  lights, density, reachable fraction, mean light, `emptyShell` (area >= 16, no furnishing).
* `suspects`: ranked list with score, kind, rel/world position and one-line reason.
* `autoVerdict`: transparent rule of thumb (NOT a visual inspection): severely incomplete if > 20 % of solids
  float or < 15 % of the interior is accessible even accepting falls; defective if >= 2 of {floating mass
  (counts double when >= 3 masses or > 5 %), >= 3 fragments, unreachable/lid-blocked chests, >= 2 ladder problem
  runs, >= 2 door problems, interior < 50 % accessible, >= 100 sealed cells}; passable if any minor issue
  (> 4 tiny floaters/fragments, >= 2 used blocked stairs, >= 3 empty rooms and > 25 % of rooms, interior > 60 %
  dark); else good.

## Validation on grounds-w3 (2026-09-22)

* 317/317 exported, rendered and analysed, 0 errors; 5,196 PNGs (~1.0 GB), analysis 14 MB, dumps 97 MB.
* Single process: 63x57x63 (reg:5) all views 3.3 s, 45x77x45 (reg:4) 2.6 s, largest 132x56x141 10.7 s
  (12.7 s with zoom tiles); analysis 0.3 s / 2.4 s. Batch (14 workers, render + analyse): 3-5 min wall.
* Auto-verdict vs Appendix A (automated, older metric set): exact 258/317, within one class 310/317; see
  `SA\analysis\grounds-w3\_appendixA_compare.json`.

## Known limitations

* Grounds context: buried/underwater pieces stand on flat ground without their rock, so overhangs float and
  entrances can end in mid-air; the base-layer grounding, liquid connectivity, plinth heuristic and sky-exposed
  starts compensate only partly. Natural captures (masked) are the reference.
* Movement model is block-granular (no half-block heights, no sprint-jumps, no pillaring; trapdoors counted as
  both passable and standable, iron doors as openable).
* Floor detection is heuristic; very terraced designs may need `--focus` or several plans.
