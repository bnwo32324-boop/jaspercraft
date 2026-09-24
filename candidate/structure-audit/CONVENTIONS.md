# Structure audit — shared conventions

Every agent and script in this audit follows this file. `SA` below means
`C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit`.
`GAME` means `C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale`.

## Hard rules (from the owner and the project)

- Never touch the live server: do not write anything under `GAME\server\world*`, `GAME\server\plugins\`
  (except, at the very end, the orchestrator dropping a jar into `plugins\update\`), `GAME\.runtime\`,
  or `GAME\site\` unless your task explicitly says so. Never create `GAME\.runtime\maintenance-mode`.
  Never write `GAME\.runtime\game-server-stop.request` unless your task explicitly says so.
- Never read out, copy or log anything in `GAME\private\` or any `.env` / key / token file.
- Do not bind anything to port 25565 or 3200/3199/3210/3299/3308/3310 (live services). Test servers use
  ports 25590-25599 on 127.0.0.1 only.
- The canonical plugin source tree is `GAME\server\custom-plugins\JasprHorrorBiomes\` (src + resources).
  Until the source-recovery step reports success it is at 3.19.0 and MUST NOT be built for anything.
- Java: build with `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin\javac.exe --release 8
  -encoding UTF-8 -cp GAME\server\cache\patched_1.12.2.jar`. Java 21+ (for the Fernflower decompiler only):
  `C:\Program Files\Android\Android Studio\jbr\bin\java.exe`, decompiler jar
  `C:\Program Files\Android\Android Studio\plugins\java-decompiler\lib\java-decompiler.jar`
  (main class `org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler`, flags `-dgs=1 -rsy=1`).
- Python 3.13 with numpy, Pillow, opencv is available (`python`). Node 24 is available.
- Minecraft 1.12.2 numeric block ids + 4-bit data values are used everywhere (no flattening names).
- Never call BlockState.update() after filling a chest's live inventory (it erases the items).
- Anything that changes which structures get PLACED must not change which structures can be RECOGNISED
  (/where, loot identification, lighting all re-run placement functions).

## Structure ids (stable across the whole audit)

- `reg:<k>` — set-piece register entry k = index into `Megaliths.C_NAME` (0..61). Name from C_NAME.
- `dun:<i>` — dungeon room i = index into `Dungeons.D_NAME` (0..14).
- `cat:<design-id>` — catalogue design, id exactly as in `resources/structures/catalog-v1.tsv`.
- `ruin:<family>` — JasprApocalypse legacy ruin family (disabled live).
- Extra categories found by the inventory step get their own prefix (e.g. `sanct:`, `detail:`, `fold:`).
The Testing Grounds number (#1..#317) is recorded as metadata, not used as the id.
In file names replace `:` with `__` (e.g. `reg__17`).

## Directory layout

```
SA/tools/            Python: jsd.py (dump reader/writer), render.py, analyze.py, stgw.py, capture.py ...
SA/harness/          Java capture plugin source + build script
SA/testserver-template/   pristine test Paper server (copied per run into SA/runs/<run>/server)
SA/runs/<run-id>/    one capture run: server copy, jobs.json, logs
SA/dumps/<set>/<id>__<variant>.jsd       captured structures (format below)
SA/renders/<set>/<id>__<variant>/*.png    renders
SA/analysis/<set>/<id>__<variant>.json    automated metrics
SA/inventory.json    the reconciled inventory
SA/audit-state.json  the machine-readable checklist (one record per structure)
SA/AUDIT_LOG.md      the human-readable log: completed, unresolved, exact next items
```
`<set>` names a capture set, e.g. `grounds-w3` (unpacked from the deployed Testing Grounds world),
`base323` (current 3.23.0 generator, natural world), `r1`, `r2` ... (repair rounds).
`<variant>` names the instance, e.g. `a`, `b` (two natural sites), or `iso` (structure-only).

## JSD1 dump format (all little-endian)

```
0   4 bytes   "JSD1"
4   uint32    header length H
8   H bytes   UTF-8 JSON header
    SX*SY*SZ  uint16   block state = (id << 4) | data, index = (y*SZ + z)*SX + x
    [if header.hasMask]  SX*SY*SZ uint8  0 = not written by a structure builder (terrain / untouched)
                                         1 = structure builder wrote a non-air block here (last write)
                                         2 = structure builder wrote AIR here (carved space)
```
Header fields:
```json
{ "format": "JSD1", "id": "reg:17", "variant": "a", "set": "base323",
  "name": "The Interceptor", "context": "natural" | "iso" | "grounds",
  "origin": [wx, wy, wz],            // world coords of array cell (0,0,0)
  "size": [SX, SY, SZ],
  "site": {"x":..,"z":..,"floorY":..,"sizeX":..,"sizeZ":..,"height":..,"mode":"surface|buried|underwater"},
  "tiles": [ {"x":rx,"y":ry,"z":rz,"kind":"chest","items":[[id,data,count],...]},
             {"x":..,"y":..,"z":..,"kind":"spawner","mob":"ZOMBIE"},
             {"x":..,"y":..,"z":..,"kind":"sign","lines":["","","",""]} ],
  "hasMask": true, "seed": "3127727864271777472", "notes": "" }
```
Tile coordinates are relative to `origin`. A natural dump covers the structure's bounding box plus a
margin of 8 blocks horizontally and enough vertical range to include the surface above buried sites.

## Block valuables policy (owner request, 2026-09-22)

Structures must not be built from valuable mineral blocks. Structure *building/decorative* placements of
these blocks are replaced by ordinary blocks of a similar colour (chest LOOT is not affected):

| valuable (id) | replacement (id:data) | why |
|---|---|---|
| gold block 41 | yellow concrete 251:4 (or yellow terracotta 159:4 where a duller gold reads better) | same hue |
| iron block 42 | smooth stone double slab 43:8 (industrial) / white concrete 251:0 (clean) | light grey metal look |
| diamond block 57 | light blue concrete 251:3, or prismarine bricks 168:1 for ornamental use | cyan/teal |
| emerald block 133 | lime concrete 251:5 | bright green |
| lapis block 22 | blue concrete 251:11 | deep blue |
| redstone block 152 | red concrete 251:14 — BUT if it powers something (lamp, door, piston), replace the powered pair with a self-lit block (lit lamps -> glowstone 89 / sea lantern 169) | red |
| coal block 173 | black concrete 251:15 | black |
| ores used as building/decor: 14,15,16,21,56,73,74,129,153 | the host stone (1:0 stone, or netherrack 87 for 153) | ore specks are the value |
| beacon 138 | sea lantern 169 | glassy light |
Functional workstations (anvil, enchanting table, brewing stand, ender chest) are judged case by case and
reported; they are not blanket-replaced. Terrain ore generation (OreVeins etc.) is out of scope.

## Honesty rules for the audit

- "inspected" means a human-style look at actual rendered images of a freshly generated capture.
- Classification (good / passable / defective / severely incomplete) is separate from verification status
  (not inspected / inspected / repaired / verified-after-repair).
- Never mark something verified that you did not look at in renders of the generated result.
