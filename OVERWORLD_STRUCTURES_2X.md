# Overworld structures 2x (JasprHorrorBiomes 3.29.0)

Owner, 2026-10-04: "Make structures in the overworld 2x common ... Don't regenerate the overworld, but do retrofit
structures that would normally spawn if it was regenerated."

## What changed for players
- **New ground** (chunks generated from now on): about twice the HorrorBiomes structures. That means set pieces, lattice
  rooms, expedition (catalogue) sites and spawner rooms, each in its usual places and mix.
- **Ground that already exists** (the world is NOT regenerated): a background job adds the same new structures a
  regenerated world would have. It only builds where nobody has spent time:
  - every existing chunk under the new structure has an inhabited time of at most 1200 ticks (one minute of a player
    nearby);
  - no chunk is guarded by an imported or Muse+GLM_Maps build;
  - no player is within 112 blocks while it is built.
  Anything else is skipped for good. Old player-visited land, bases and spawn are never touched.
- **Unchanged:** every structure that already stands is placed and recognised exactly as before (loot, `/where`,
  encounters, the Fold door, lighting). The imported (GLM/Codex) and Muse+GLM_Maps packs keep their own rates.

## How it works (tier 2)
- The 3.28.0 source had an unshipped tier-2 layer (1.5x) limited to brand-new ground. 3.29.0 retunes it to a second
  full 1x. Its plan is a pure function of the seed, the plan a regenerated world would have. It yields to every older
  site, and nothing older ever asks about it.
- **Decided once (`Tier2`):** each tier-2 site gets a receipt in `plugins/JasprHorrorBiomes/tier2-<world uid>.receipts`,
  either `B key` (built) or `R key why` (refused). The receipt is appended and synced to disk before any block goes down.
  - A site wholly on new ground is decided at the first populate that reaches it.
  - A site touching ground that existed at the upgrade (the v2 boundary snapshot, `jaspr-rates-v2.boundary`) is decided
    by the retrofit.
- **The other packs:** before a tier-2 site is built, the JasprImportedWorldgen and JasprMuseMaps cells that could reach
  it are decided (frozen) in their own ledgers. A site that would meet one of their plans is refused, so they can never
  plan over a built tier-2 site later. If a pack is enabled but unreadable, the site is refused (`TIER2_PACK_UNREADABLE`).
- **Retrofit (`Tier2Retrofit`):**
  1. Probes every old chunk, plus a ring of one chunk round them, with the real populate builders in PROBE mode. No chunk
     is loaded, and nothing is built.
  2. Decides each tier-2 site found. Built sites go into every populated chunk under them, one chunk per step, with the
     same builders in ONLY mode: only that site, and every block write outside its box is dropped.
  3. Gives each old, populated chunk nobody has spent time in its extra spawner-room attempts.
  It spends at most 4 ms per tick. Receipts make every step happen once across restarts.
- **Recognition:** a tier-2 site is named (loot, `/where`, encounters) only once its receipt says built.
- **The old dungeon retrofit (v8) is retired.** It re-ran its builders over any region file it had not listed, so after
  a restart it would have rebuilt the standing rooms in newly explored regions and refilled their chests.

## Measured (rates probe, `tests/java/chat/jaspr/biomes/StructureRatesProbe.java`)
- Every 3.27 layer keeps its exact fingerprint: set pieces (primary, secondary), lattice rooms (A, B, C), catalogue
  tiers 0 and 1, and sanctuaries. `tests/structure-density.test.cjs` pins them to live 3.27.6.
- Seed 3127727864271777472, 1,500 x 1,500 chunks:
  - set pieces: 16,423 older, 16,401 tier 2 (each of the 62 kinds calibrated in rank order, most within 5%);
  - catalogue: 915 older, 920 tier 2 (grids v8, v10, v9, v11 and v12 at 55%). A second box gave 0.93x.
- Lattice rooms (1,000 x 1,000 chunks): 33,328 older, 33,068 tier 2 (each of the 14 rooms calibrated in order).
- Small box (300 x 300 chunks, the unit test): set pieces 1.05x, rooms 0.98x the older count.
- Spawner rooms: attempts 39 -> 78 per chunk. The 3.28.0 test world measured 40 -> 62 rooms for 39 -> 59 attempts.

## Rehearsal on a copy of the live overworld (2026-10-04)
`Documents/JasperCraft-Tier2-Test`: the live world and the importer, Muse and Lost Cities data, on port 25672, with
nobody online. The retrofit ran in about 3 minutes:
- probed 3,886 chunks (the 3,496 old ones and a ring of one);
- 151 tier-2 sites touch old ground: 44 built (266 chunk steps), 107 refused because the land is lived in;
- 890 untouched old chunks got their extra spawner-room attempts; 2,401 were refused (lived in, guarded or city);
- no probe touched a chunk, nothing failed, and there was no exception.
A chunk-by-chunk diff against the untouched copy (`regiondiff.py`) found 61 changed chunks. Every one was populated, and
the most time anyone had spent in any of them was 1,090 ticks. A second start found nothing left to decide. A bot
exploring new ground had 18 tier-2 sites decided as their chunks populated; one room yielded to an imported plan.

## Logs
- `RATES_V2_BOUNDARY_READY oldChunks=N`
- `TIER2_READY receipts=N`
- `TIER2_RETROFIT_START`, `TIER2_RETROFIT_BUILT key=… chunks=… ms=…`, `TIER2_RETROFIT_PROGRESS` (once a minute),
  `TIER2_RETROFIT_COMPLETE` (counts of built and refused, by reason)
- Failures: `TIER2_RETROFIT_*_FAILED`, `TIER2_PACK_UNREADABLE`, `TIER2_LEDGER_FAILED`, `TIER2_RECEIPT_FAILED`
- `DUNGEON_RETROFIT retired=v8`
