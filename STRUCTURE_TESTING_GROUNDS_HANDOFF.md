# Structure Testing Grounds — handoff

Written 2026-09-22. Covers five things: where the files are, how JasperCraft generates
structures, how the Structure Testing Grounds world works, what separates the Claude Code
structures from the ChatGPT Codex ones inside it, and how `/where` answers in both worlds.

Client build in the grounds: `stg-9` (`classes.js?v=20260922-grounds13`).
World data version: `w3` (what decides whether the world reinstalls).
Server plugin: `JasprHorrorBiomes 3.19.0`.

---

## 1. Paths

### 1.1 On the Windows machine

Two projects. The game and its site live in one; the website that serves them lives in the
other.

```
C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\      the game project
├─ site\                          everything the browser downloads
│  ├─ client.html                 the game page; every <script> here carries a ?v= cache buster
│  ├─ classes.js                  the TeaVM-compiled Eaglercraft client, 14.6 MB, patched in place
│  ├─ assets.epk                  vanilla assets
│  ├─ jaspercraft-grounds-world.js   the packed Testing Grounds world (6.8 MB; a container, not JS)
│  ├─ jaspercraft-grounds-index.js   the 317-entry plot index /where and /tp read in that world
│  ├─ jaspr-client.js             the site's own client glue (session, name, SSO)
│  └─ jaspercraft-*.js/.css       voice, mobile controls, video, diagnostics
├─ server\                        the Paper 1.12.2 server
│  ├─ plugins\JasprHorrorBiomes.jar    the structure generator (this is the live one)
│  ├─ plugins\update\                  drop a jar here; Bukkit swaps it in at next startup
│  ├─ world\                           the live multiplayer save
│  ├─ cache\patched_1.12.2.jar         compile against this
│  ├─ usercache.json                   jasper_e_ = e152d29e-cfc9-3d95-89ee-1177d0b4f863
│  └─ ops.json
├─ .runtime\                      runtime state the gateway watches
│  ├─ game-server-stop.request         write this file and the supervisor restarts the server
│  ├─ paper-console.log                the server console
│  └─ game-gateway.log
├─ scripts\                       build-server-plugin.ps1, build-site.ps1, start-*.ps1, status.ps1
├─ client-mods\, client-reference\     notes and reference copies of client patches
└─ *_UPDATE.md, MODDING_NOTES.md, deployment-manifest.json
```

```
C:\Users\AM\Desktop\Curser Test\Jaspergers\           the website (jaspr.chat)
├─ services\gateway\jaspercraft-bridge.mjs    serves /jaspercraft/* and proxies the game port
└─ data\jaspercraft-integration.json          points siteDir at the game project's site\ folder
```

`jaspercraft-integration.json` is the join between the two projects:

```json
{ "siteDir": "C:\\Users\\AM\\Documents\\Eaglercraft-1.12.2-Tailscale\\site",
  "upstreamHost": "127.0.0.1", "upstreamPort": 25565 }
```

So `https://jaspr.chat/jaspercraft/<name>` reads `...\Eaglercraft-1.12.2-Tailscale\site\<name>`
straight off disk. There is no build step and no copy: writing a file into `site\` publishes it.

**Which build is running.** The Testing Grounds button on the main menu reads
`Structure Testing Grounds · stg-9`. That suffix is the client build. If it names an older
build than the one just deployed, the page is cached and nothing else about the session can
be trusted — reload it before diagnosing anything.

**The allowlist matters.** `jaspercraft-bridge.mjs` only serves names matching:

```
(index|client).html | classes.js | assets.epk | favicon.(png|ico)
jaspr-(client|profile|sso).(js|css) | jaspercraft-[a-z0-9-]+\.(js|css|png|jpg)
lang/<name>.(lang|json)
```

Anything else is a 404. That is why the packed world is named `jaspercraft-grounds-world.js`
even though it is a binary container — it is the only shape of filename the gateway will
serve, and it is streamed with `fs.createReadStream`, so the bytes come back exactly as
written. Files requested with a `?build=` or `?v=` parameter are served `immutable` for a
year, so **a changed asset must get a new `?v=` in `client.html` or browsers keep the old one.**

### 1.2 In the build workspace (this session's cloud container)

Nothing here is on the Windows machine. It is where the world is built and the client is
patched; only the finished files are written across.

```
/home/claude/build/          the server plugin
├─ src/chat/jaspr/biomes/*.java     47 sources; Where.java, Dungeons.java, Megaliths.java, …
├─ res/plugin.yml                   version + command declarations
└─ JasprHorrorBiomes.jar            the built jar

/home/claude/stg/            the Testing Grounds pipeline, in the order it runs
├─ registry.py → registry.json      the 317 structures: name, author, group, family, size, blurb
├─ cap/, ruincap/, ids/             the capture step: runs the real generators under mocks and
│                                   dumps each structure's blocks to dump/*.stg
├─ sizes.json                       every structure's measured bounding box
├─ layout.py → layout.json          the grid: one plot per structure, and its number
├─ nbt.py                           a minimal NBT writer
├─ eagler.py                        EaglercraftX's save format: chunk naming, gzip, reader
├─ build_world.py                   paints the plots into Anvil region files → world/
├─ verify_world.py                  reads the world back and checks it against the dumps
├─ index.py → stg-index.json        the client's plot index, incl. every /tp landing,
│                                   measured out of the world that was just built
├─ pack3.py                         world/ + index → the two jaspercraft-grounds-*.js files
├─ atlas.py → atlas-data.json       the payload behind the published atlas page
└─ client/
   ├─ grounds.js                    the client patch block, authored on its own
   └─ testgrounds.js                a Node stub of the browser that runs grounds.js and asserts

/home/claude/look/           the client working copies
├─ classes8.js, classes9.js         successive builds; classes9.js is what is deployed now
└─ client.html                      the deployed page
```

**Deploying.** `device_commit_files` writes a staged file to the Windows machine. In this
session its *first* write after a fresh copy silently landed stale four separate times, so
every deploy is: commit → commit again → stage the file back → compare md5 against the
source. The current deploy was verified that way; all four files match byte for byte.

**Restarting the game server.** Write `.runtime\game-server-stop.request`; the supervisor
restarts it, which takes 90–330 seconds. A new plugin jar goes in `server\plugins\update\`
and Bukkit swaps it in on that restart. Never create `.runtime\maintenance-mode`.

---

## 2. How structures are generated in JasperCraft

Everything in the live world is a **pure function of the world seed and the position**.
Nothing is stored in a structure file, nothing is rolled at runtime, and no structure is
"placed" in the sense of being recorded anywhere. Ask the same function the same question
and it gives the same answer forever — which is what makes `/where` possible at all
(section 5) and what made it possible to rebuild all 317 of them offline (section 3).

There are three separate builders, and they are independent of each other.

### 2.1 The set-piece register — 62 structures

`Landmarks`, `Megaliths`, `Anomalies`, `Relics`, `Metropolis`, `Wonders`, `Temples`,
`Breach`. Each is one Java file, each file holds several named structures, and each
structure sits on **its own lattice**.

A lattice is a spacing in chunks plus a salt. `Megaliths.java`, for instance:

```java
private static final int AM_CELL = 92, OUTPOST_CELL = 81, GARRISON_CELL = 124;
private static final int MALL_CELL = 98, SEWER_CELL = 106, AIRLINER_CELL = 86;
```

A chunk carries structure *k* when its chunk coordinates land on that structure's lattice:

```java
if (Math.floorMod(acx, cell) != Math.floorMod(Terrain.mix(t.seed + salt) >>> 3, cell)) continue;
if (Math.floorMod(acz, cell) != Math.floorMod(Terrain.mix(t.seed + salt + 17L) >>> 3, cell)) continue;
```

The cell sizes are all mutually prime-ish and distinct, so two set pieces effectively never
want the same chunk. From the anchor cell, a per-site seed is derived
(`Terrain.mix(seed + acx*6364136223846793005 + acz*1442695040888963407 + salt)`) and every
variable detail — heights, damage, which windows are broken — comes out of a `Random` seeded
with it. Buried structures pick a y from that seed under the surface; surface structures use
`surfaceAnchor`, which samples the four corners and **refuses the cell** if the ground varies
by more than 3 blocks, or sits below y64, or above y132. A refused cell simply has no
structure: that is why some lattice positions are empty.

Because a structure can span many chunks, each chunk scans a small neighbourhood of cells
(`span = (max(sizeX,sizeZ) >> 4) + 1`) and stamps only the part that falls inside itself.
Every chunk computes the same anchor, so the pieces line up.

### 2.2 The dungeon lattices — 15 rooms

`Dungeons.java`. Same machinery, but each room is laid **twice**, on two lattices with the
same spacing and different salts:

```java
ward(world, chunk, terrain, caves, 0x5741524400L);   // first lattice
...
ward(world, chunk, terrain, caves, 0x5741524401L);   // second, the doubling
```

The first pass is skipped on chunks that predate the retrofit; the second always runs. That
is how density was doubled without moving anything that already existed.

### 2.3 The expedition catalogue — 234 designs

`StructureCatalog` + `StructurePlanner` + `WorldgenExpansion`. A different model: the world
is cut into regions and at most one catalogue structure is planned per region.

```java
public static final int REGION = 1024;              // legacy regions
public static final int EXPANSION_REGION = 384;     // post-upgrade regions
public static final double RELATIVE_STRUCTURE_DENSITY = 0.10;   // 90% of candidate cells dropped
static final int MARGIN = 4, APPROACH = 96;
```

Each design is a grid of 12×12 rooms (`design.columns × 12` by `design.rows × 12` blocks)
with a family, a tier, a mode (`surface` / `buried` / `underwater`) and a set of markers —
`supply`, `vault`, `boss`, `mob`, `door` — which is where loot chests, spawners and the
Fold thresholds are attached later by `StructureLoot` and `StructureEncounters`.

`WorldgenExpansion` is the safety rail. It reads `jaspr-expansion-v3.boundary` from the world
folder — a bitmap of every chunk that existed before the upgrade — and **rejects any new
catalogue site that would touch one**. If the boundary file cannot be read, generation
refuses to run rather than build over old ground. Three quarters of expansion cells are
reserved for above-ground landmarks, so the surface has visible things on it.

### 2.4 Where it is all hooked up

- `HorrorGenerator.generateChunkData` — terrain, caves, openings, biomes, then
  `for (Site site : WorldgenExpansion.sites(w, cx, cz)) site.stamp(d, cx, cz);`
- `RuinSupplies` (the one `BlockPopulator`) → `Dungeons.populate(...)`, which in turn calls
  `Landmarks.populate`, `Megaliths.populate`, `Anomalies`, `Relics`, `Metropolis`, `Wonders`,
  `Temples`, `Breach`, and then the fifteen dungeon rooms.

The split is not arbitrary: a `ChunkGenerator` writes block ids only, so anything needing a
**tile entity** — a chest with contents, a mob spawner, a sign — has to run in the populator,
where real `Block` objects exist.

### 2.5 The sixth thing: apocalypse ruins

Six legacy ruin families (`Ruins$Builder`), disabled in live generation and recorded as
legacy in `deployment-manifest.json`. They are still in the Testing Grounds so they can be
looked at.

---

## 3. How the Structure Testing Grounds world works

A private single-player world containing all 317 structures on flat ground, one per plot.
It is **entirely client-side**: the game server is not involved, is not contacted, and does
not need to be running. Nobody else can see it, because it never leaves your browser.

### 3.1 Getting in

The row sits on the main menu directly under **Join JasperCraft Server**, and clicking it
joins the world — no world list, no intermediate screen.

It appears only after **two** separate confirmations, both answered by the host, not by
anything the page was handed:

| check | source | what it proves |
|---|---|---|
| verified owner | `GET /api/bootstrap?member_directory=0&message_limit=0` → `currentUser.isOwner` | the signed-in chat session is the canonical owner — the server sets `owner_verified` for that one identity only |
| owner game account | `GET /api/jaspercraft/me` → `gameName`, compared with `jasper_e_` **and** with the name the client was started with | the account playing is the owner's game account |

Both run at the main menu, not at server join — that was the original bug: verification only
happened on joining the server, so the row never existed at the point it was needed.

If the game account matches but the session is not the verified owner, the row is visible but
disabled and says why. If the game account does not match, there is no row at all. No other
account ever sees anything. `$rt_globals.JasprGroundsStatus()` reports the current answer.

### 3.2 Installing

On first entry the client downloads `jaspercraft-grounds-world.js` (6.8 MB) and writes it
into the browser's own world storage. The button label is the progress bar.

### 3.2a The save format — the thing that took four builds to find

**EaglercraftX does not use Anvil region files.** Its integrated server has its own
`EaglerChunkLoader`, and it stores **one gzipped NBT file per chunk**:

```
<world>/level<dimension>/<12 hex digits>.dat
```

The twelve digits are six for x and six for z, each offset by **1,900,000** so they are
never negative, and each written **least-significant digit first**. Chunk (0, 0) is
`0EDFC10EDFC1.dat`. The file's contents are gzip (`CompressedStreamTools`), and the root
tag is exactly `{Level: {...}}` — no `DataVersion`; `EaglerChunkLoader` reads only `Level`
and requires `Level.Sections`.

The relevant code, for anyone checking this again:

- `EaglerChunkLoader.chunkFileName` — the hex encoding
- `EaglerSaveHandler.getChunkLoader` — `new VFile2(worldDir, "level" + dimensionId)`
- `EaglerSaveHandler` — `worldDir = new VFile2(saveRoot, folderName)`, player data in
  `<worldDir>/playerdata`

The first four builds of this world shipped **Anvil `.mca` region files**, because that is
what a 1.12.2 world is on a real server — and a real Paper 1.12.2 server read them back
correctly, which is why they verified. The browser's server never looked at them. It read
`level.dat`, found no chunks, and generated plain superflat ground for every chunk in the
world. The result was a world that installed cleanly, reported itself healthy, teleported
correctly, and was completely empty: 317 structures on disk that the game never once read.

The container is **STGW v3**: magic `"STGW"`, version 3, the folder name, the dimension,
then the named files (`level.dat`) as path/length/bytes, then the chunks as
`x, z, length, bytes`. Bytes are stored **exactly as they must land on disk** — the chunk
files are already gzip, so compressing them again only meant 18,867 decompressions on
install for nothing. Chunks are sent as coordinates because the name is a pure function of
them; sending both would add about half a megabyte. 18,868 members, 7.8 MB.

They are written to `eaglercraft/worlds/StructureTestingGrounds/…` in IndexedDB database
`_net_lax1dude_eaglercraft_v1_8_internal_PlatformFilesystem_1_12_2_`, in batches of 400 per
transaction (four at a time would be nearly five thousand round trips), and the folder name
is appended to `worlds_list.txt`. Both of those exact names were the cause of three successive
crashes: the engine's save root is `eaglercraft/worlds/`, not `worlds/`, and the real database
name is the bare prefix — `eaglercraftXOpts.worldsDB` is only a discriminator that
`PlatformFilesystem` resolves to `""`. A world written anywhere else is invisible to both the
save loader and the integrated server, which then builds a `WorldInfo` out of a null
`WorldSettings` and crashes on `null.cG1`.

A stamp file `stg-build.txt` holds the **world data version** (`JasprGroundsWorld`, currently
`w3`), which is deliberately separate from the client build (`JasprGroundsBuild`). The client
changes far more often than the 18,867 chunk files do, and a client-only fix should not cost a
fresh 8 MB download and a full reinstall. Bump the world version only when the world data
actually changes; bump the client build every deploy. When the stamp does not match
`JasprGroundsWorld`, the client **deletes the entire world folder** — including any saved
player data — and reinstalls. That deletion is what makes a rebuild take effect: a world that
has been played once carries the player's last position inside its own `level.dat`, and
without the wipe a new spawn point is read and then immediately overridden by the old
position. That is exactly why the structures could not be found after the last rebuild.

After writing, the client reads the world back at the engine's own path and **counts the
chunk files** — not just the total files, which is what let a chunkless world pass as healthy
for three builds — and records the result in `window.eaglercraftXOpts.jasprGrounds` — so if it ever does crash, the crash report
prints whether the world was actually on disk and what its first bytes were.

### 3.3 The world itself

Superflat, `3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1` — bedrock at y0, dirt at
y1–2, grass at y3, so everything stands on y4. Creative, commands on, daylight cycle off at
noon, no weather, no mob spawning, `spawnRadius 0`.

**The grid.** One plot per structure. A plot is a fixed square — 80 blocks for a Claude Code
structure, 160 for a Codex one — with the structure centred inside it and a stained-clay
border with 4-tall corner posts marking the edge, so no two structures ever touch. Every
structure has at least 4 blocks of clear ground on every side; the layout script asserts it
(`sx <= cell - 8 and sz <= cell - 8`) and separately proves no two plots share a chunk.

Rows run east–west, sections run north–south:

```
z 0    ─┐
        │  Claude Code    9 rows, 80-block plots, 77 structures
z 720  ─┘
z 768      ►  THE LOBBY — spawn (960, 5, 768)
z 816  ─┐
        │  ChatGPT Codex  37 rows, 160-block plots, 240 structures
z 6736 ─┘
                     x runs 0 → 1920
```

**Spawn** is the lobby at **960, 4, 768**: a 25×17 quartz plaza in the corridor between the
two halves, on columns both sections cover. The last Claude row is 48 blocks north, the first
Codex row 48 south, and the nearest plots are roughly 70 blocks away — inside render distance
on landing. Two 42-block masts with beacons stand at the **ends** of the plaza, nine blocks
either side of the middle, so the lobby is findable from anywhere on the grid.

The masts are off-centre for a reason worth keeping. The first version put a single mast on
the plaza's centre — which is the world spawn. That is a 42-block solid quartz column through
the one column the player is placed in, so the game shoved the player up the outside of it and
the world looked empty, because you never got to stand on the plaza and look around at all.
`paint_lobby` now ends with an assertion that the spawn column is solid under foot and air at
head and shoulders; **nothing may ever be built on the spawn column.**

**Every structure has a number**, 1 to 317, running in map reading order: the Claude Code half
first, each row west to east, so #1 is the north-west corner and #317 is the last apocalypse
ruin in the far south. `layout.py` assigns it, the plot signs lead with it, `/where` reports
it, the atlas page shows and searches it, and `/tp <n>` goes to it. One number, four places,
generated from one source.

Underground structures are not underground here. Everything sits on the flat, whatever its
`mode` says, because the point is to be able to look at it. Anything containing water or lava
was given a glass tank so it holds together on dry flat ground.

Each plot has a 3-wide gate on its south side and two signs beside it: the first gives the
number, the name and the author, the second the group, kind, mode and tier — the same answer
`/where` gives, so you can audit without typing.

**Getting around.** `/tp <number>` lands you on top of that structure, at its middle:
`/tp 1`, `/tp 143`, `/tp 317`. `/tp lobby` (or `spawn`, or `home`) comes back. The landing is
not computed from the bounding box — `index.py` reads the finished world and takes the highest
block actually under the structure's middle column, so a courtyard, a stairwell or an open nave
puts you on its floor instead of dropping you down it. Thirty-four structures have nothing at
all over their middle (a rail loop, a quad, a ring); for those the search steps outward ring by
ring until it finds something to stand on. All 317 landings were checked to be solid under foot
and clear at head height.

Only the single-argument numeric form of `/tp` is claimed. `/tp 100 5 200`, `/tp <player>` and
every other use of `/tp` are passed through to the game untouched, and on the multiplayer server
nothing is intercepted at all.

**Verification of the deployed build.** `verify_deployed.py` unpacks the container that is
actually on the server, checks every chunk file is named the way `EaglerChunkLoader` names it
and holds the chunk that name encodes, reads the index that is actually on the server, and checks all 317
structures block-for-block against the generators' own output, every landing, every plot
boundary and every numbered sign. `verify_tp_endtoend.js` then extracts the patch block from
the `classes.js` that is actually on the server, runs it against that index, and emits all 317
teleports, which are checked against the unpacked world: each lands inside its own structure's
footprint, on a block belonging to the structure, with air at head height, and `/where` at that
spot names the same structure and number. Run both after every deploy.

**Verification of the build pipeline.** The world was proved correct three ways: block-for-block against the capture
dumps offline; by a real Paper 1.12.2 server reading back blocks, chest items, spawner types
and sign text; and by `/where` agreeing with the register at 28,107 sampled columns. The last
run: 18,867 chunks, 4,350 sampled blocks verified, 387 chests, 465 spawners, 0 missing,
638 signs.

### 3.4 Rebuilding it

```
cd /home/claude/stg
python3 layout.py         # the grid; numbers the plots; asserts no overlaps
python3 build_world.py    # paints world/; asserts the spawn column is clear
python3 verify_world.py   # reads it back against the dumps
python3 index.py          # → stg-index.json, landings measured from the built world
python3 pack3.py          # → jaspercraft-grounds-world.js + -index.js
python3 atlas.py          # → atlas-data.json for the published atlas page
```

`index.py` must run **after** `build_world.py` and **before** `pack3.py`: it reads the
finished region files to find each structure's roof. Skipping it ships an index describing
a world that no longer exists — which is exactly what happened once, leaving every Codex
plot 224 blocks out and `/where` naming the wrong structure.

Then bump `JasprGroundsBuild` in `client/grounds.js` (or the world will not reinstall),
re-insert the block into `classes.js` between `/* JASPR_GROUNDS_V1_BEGIN */` and
`/* JASPR_GROUNDS_V1_END */` **in binary mode, preserving CRLF**, bump the `?v=` in
`client.html`, run `node client/testgrounds.js`, and deploy all three files plus the world.

Editing `classes.js` in Python text mode once silently destroyed 43 KB of it by rewriting
line endings. Always binary.

---

## 4. Claude Code structures vs ChatGPT Codex structures

Authorship is not a guess. It comes from `deployment-manifest.json`, which was generated on
2026-09-11 — before this Claude Code session existed — and already records `structures=234`
and the ruins as legacy-disabled. So those 240 predate this work and are Codex's; the 62
register set pieces and the 15 dungeon rooms were written here.

| | **Claude Code** | **ChatGPT Codex** |
|---|---|---|
| count | 77 | 240 |
| plot size | 80 blocks | 160 blocks |
| rows | 9 | 37 |
| z range | 0 – 720 | 816 – 6736 |
| border colour | **blue** stained clay (`159:11`) | **orange** stained clay (`159:1`) |
| numbers | #1 – #77 | #78 – #317 |
| side of the lobby | north (−Z) | south (+Z) |
| rows grouped by | source file / set-piece group | catalogue family |
| `/where` tint | aqua | gold |

**Claude Code — 77, in 9 rows, one row per source file:**

| row | group | count |
|---|---|---|
| 0 | Landmarks | 12 |
| 1 | Megaliths | 11 |
| 2 | Anomalies | 6 |
| 3 | Relics | 8 |
| 4 | Metropolis | 6 |
| 5 | Wonders | 7 |
| 6 | Temples | 7 |
| 7 | Breach | 5 |
| 8 | Dungeons | 15 |

Each is hand-written architecture with its own loot table and its own threat. They are
individually named ("Spencer Manor", "The Impossible Stair", "Columbia") and each one is a
different shape — no two share a floor plan. In the live world each sits on its own lattice.

**ChatGPT Codex — 240, in 37 rows, one row per catalogue family**, largest families first:
metro (12), monastery (11), observatory (10), research (10), school (10), archive (9),
castle (9), cistern (9), house (9), underwater_city (9), auditorium (8), city (8), harbor (8),
mansion (8), backroom (7), catacomb (7), dungeon (7), telecom (7), bunker (6), foundry (6),
infirmary (6), plane (6), sewer (6), battle_tower (5), metropolis (5), mortuary (5),
signal (5), bathhouse (4), conservatory (4), excavation (4), prison (4), market (3),
barracks (2), hospital (2), reliquary (2), refuge (1), and a final row of the 6 legacy
apocalypse ruins.

Within a family the row is sorted by tier, so difficulty reads left to right. These are
**generated from a shared grammar** rather than hand-built: a grid of 12×12 rooms, a family
that decides the palette and fittings, a tier that decides scale and danger, and markers that
say where the caches, vaults, boss and encounters go. Two members of the same family are
recognisably siblings — which is the thing the grid is there to let you judge.

The larger plot is not decoration: catalogue designs run up to 10×10 rooms, so 160 blocks is
what they need. That is also why the Codex half is nine times deeper than the Claude half.

Practical difference when auditing: in the Claude half you are checking whether **this one
structure** is right. In the Codex half you are mostly checking whether a **family** is
coherent and whether the tiers actually escalate — which is why they are in family rows with
tier order along the row.

---

## 5. How `/where` works — in both worlds

`/where` (aliases `/biome`, `/survey`) answers: what am I standing in, and who built it.

### 5.1 On the live server

`Survey.java` runs the existing survey and then calls `Where.report(sender, world, terrain, x, y, z)`.

`Where.at(...)` asks the three builders **in order of how specific they are**, and returns the
first that claims the column:

1. **`Megaliths.located(t, x, y, z)`** — the 62-entry set-piece register. Returns the rank of
   the structure, then `Megaliths.originOf` gives its origin and floor, and the `C_GROUP` /
   `C_KIND` tables give the group and kind. Author: **Claude Code**.
2. **`Dungeons.locate(t, x, y, z)`** — walks the 15 room types across both lattices, recomputes
   each anchor exactly as the generator did, and checks the footprint and height. Returns
   `name \0 method \0 x \0 y \0 z`. Author: **Claude Code**.
3. **`WorldgenExpansion.sites(world, x >> 4, z >> 4)`** — the catalogue, with a 4-block skirt
   and a −40/+60 height window. Reports family, tier, mode, room grid, biome exclusivity, and
   counts the markers into caches / vaults / boss / encounters. Author: **ChatGPT Codex**.

If none claims it: *"open ground — nothing built on this column"*.

The key property: **these are the same functions that placed the structures.** `/where` does
not look anything up and does not search the world — it re-evaluates the placement function at
your position. So it cannot name a structure that is not there, cannot mistake one for
another, and works in chunks that have never been generated.

The catalogue lookup is wrapped in `try { … } catch (RuntimeException ignored) {}` — it reads
the expansion boundary file, and a survey is never worth failing over.

Output is four lines: name and author (aqua for Claude Code, gold for Codex), group and
detail, the one-line blurb, then origin, floor y, footprint, and how far into the structure
and how far above its floor you are.

### 5.2 In the Structure Testing Grounds

There is no plugin there — no server at all — so `/where` is implemented in the client.

`Cn9` (`EntityPlayerSP.sendChatMessage`) is patched: every outgoing chat line is passed through
`JasprGroundsCommand(b)` first. In the grounds, `/where` is rewritten into a
`/tellraw @s {...}` that the world prints locally; **anywhere else, including on the live
server, the line passes through completely untouched**, so the server's own `/where` still
runs and nothing about multiplayer chat changes.

The answer comes from `jaspercraft-grounds-index.js` — the 317-entry plot index generated
alongside the world by `index.py`, keyed by plot. Your position maps to a plot, the plot maps
to an entry, and the entry carries the number, name, author, group, family, tier, mode, kind,
blurb, footprint and teleport landing. Answers are capped at 250 characters to fit the chat
limit; the longest is 217. The answer leads with `#<number>`, so whatever you are looking at
tells you the command that brings you back to it.

`/tp <number>` is handled in the same place, by the same index — see §3.3.

So the two implementations differ completely in mechanism — re-evaluated placement functions
on the server, a static plot index in the grounds — but agree on the answer, which was checked
by having `/where` name all 317 plots and comparing every one against the register.

The two signs at each plot's south gate carry the same answer in the world itself.

---

## 6. Things that will bite

- **`device_commit_files` writes stale on the first call after a fresh copy.** Seen four
  times. Commit twice, then stage back and compare hashes.
- **Never edit `classes.js` in text mode.** Read and write binary, preserve CRLF.
- **A changed asset needs a new `?v=`** in `client.html`, or the year-long immutable cache
  serves the old one. This applies to files the *client* fetches too: the world container and
  the index are downloaded by `grounds.js`, and the gateway serves them `max-age=3600`, so
  both are requested as `?build=<JasprGroundsBuild>`. Without that, a browser could answer
  a request for the new world with the copy it fetched for the previous build — installing
  the old world under the new build's stamp, which then looks installed and is wrong.
- **Nothing may go into a chat packet that the server will not accept.** `processChatMessage`
  rejects any character below space, DEL, or the section sign (167) and *closes the
  connection* — "Illegal characters in chat". The section sign is how Minecraft writes colour,
  so a coloured `/tellraw` built the obvious way is a disconnect. `JasprGroundsAscii` rewrites
  every non-ASCII character in the finished JSON as a `\uXXXX` escape, which travels as plain
  ASCII and is decoded back by the server's own JSON parser, and `JasprGroundsChatLegal` is
  the last gate every outgoing command passes before it is returned. Escaping costs six
  characters per character, so chat text uses ASCII separators (`-`, not `·`) to stay inside
  the 256-character packet limit.
- **The grounds commands never fall through.** `/where` and `/tp <number>` always answer,
  even when the answer is "index not loaded". Letting them fall through to the game is what
  produced `Entity '1' cannot be found`: the real fault stayed hidden behind a vanilla error.
- **Bump `JasprGroundsBuild`** whenever the world changes, or the client keeps the installed
  copy and the change is invisible.
- **Run `index.py` between `build_world.py` and `pack3.py`.** The index is not hand-maintained
  and must describe the world that was actually just written.
- **Nothing on the spawn column.** `paint_lobby` asserts it; do not weaken that assertion.
- **Never write Anvil region files for this world.** They are valid 1.12.2 and a real server
  reads them; the browser's server does not. See §3.2a.
- **A passing verifier must prove the game can read the world, not just that the bytes are
  correct.** Every check in this project passed while the world was invisible to the engine,
  because they all read the files the same way the build wrote them. The install-time check
  now counts chunk files in the engine's own directory, and `verify_deployed.py` decodes each
  chunk file name back to coordinates and confirms the chunk inside matches.
- **`jasper` is the only allowed operator username**, AuthMe protects it with a password, and
  `TestServerControl` blocks non-authentication commands from every other player. The in-game
  name is `jasper_e_`.
- **Do not create `.runtime\maintenance-mode`.**
