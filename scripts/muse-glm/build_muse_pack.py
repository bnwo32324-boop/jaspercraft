"""Build the Muse+GLM_Maps structure pack for JasprMuseMaps (Paper 1.12.2).

Input : server/custom-plugins/JasprMuseMaps/maps/Muse+GLM_Maps/*.schem (Sponge v2, modern block states;
        the owner's collection, kept byte-for-byte in its own folder so it stays identifiable).
Output: server/custom-plugins/JasprMuseMaps/pack/maps/<id>.se45.gz  (one whole-design SE45 file each)
        server/custom-plugins/JasprMuseMaps/pack/maps/catalog.json   (placement, spots, dangers, loot)
        (pack/ becomes JasprMuseMapsPack.jar, an asset-only plugin, so code rebuilds never re-ship 16 MB)
        server/custom-plugins/JasprMuseMaps/MAPS.md                       (human-readable register)

Run:   python -B scripts/muse-glm/build_muse_pack.py [--check]
Conversion reuses the audited Threefold tools (lib/: legacy_mapping, source_reader, convert_assets), plus the
substitutions below for the few post-1.12 names they refuse. No valuable blocks survive (owner rule), spawners,
TNT, portals and command blocks become inert (the plugin adds its own dangers), and all source NBT is dropped.
Deterministic: same inputs -> byte-identical outputs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "lib"))
sys.dont_write_bytecode = True
import numpy as np  # noqa: E402
from legacy_mapping import convert_state, sanitize_legacy, LEGACY_NAMES, FORBIDDEN_IDS  # noqa: E402
from source_reader import load_structure  # noqa: E402
from convert_assets import select_air, encode_se45, decode_se45, FLOOD_PASSABLE, ARCHITECTURAL  # noqa: E402
import muse_design  # noqa: E402  (themes, dangers, loot, bosses)

GAME = HERE.parents[1]
PLUGIN = GAME / "server" / "custom-plugins" / "JasprMuseMaps"
SOURCE = PLUGIN / "maps" / "Muse+GLM_Maps"
OUT = PLUGIN / "pack" / "maps"
COLLECTION = "Muse+GLM_Maps"

# Post-1.12 or non-vanilla names the audited mapper refuses (fail-closed); explicit, reviewed substitutions.
EXTRA = {
    "wildflowers": (38, 3), "closed_eyeblossom": (38, 0), "open_eyeblossom": (38, 8), "pitcher_crop": (31, 1),
    "oak_propagule": (6, 0), "mangrove_propagule": (6, 0), "short_dry_grass": (32, 0), "tall_dry_grass": (32, 0),
    "creaking_heart": (17, 0), "resin_clump": (0, 0), "dried_ghast": (0, 0), "__reserved__": (0, 0),
    "sprout": (31, 1), "leaf_litter": (0, 0), "firefly_bush": (31, 1), "bush": (31, 1), "cactus_flower": (38, 0),
}
# Owner rule: no valuable blocks in structures. Ores become their host stone.
VALUABLE = {41: (159, 4), 42: (1, 6), 57: (159, 9), 133: (159, 13), 22: (159, 11), 152: (159, 14),
            14: (1, 0), 15: (1, 0), 16: (1, 0), 21: (1, 0), 56: (1, 0), 73: (1, 0), 74: (1, 0), 129: (1, 0),
            153: (87, 0), 138: (169, 0)}
TORCHES = {50, 75, 76}
SPAWNER_NAMES = {"spawner", "trial_spawner", "vault"}
LIQUID = {8, 9, 10, 11}
AIRLIKE = {0}


def convert(name):
    """(id, meta, isSpawner) for one palette entry; never silently maps a real block to air unless listed."""
    if name is None:
        return 0, 0, False
    base = str(name).split("[")[0]
    short = base.split(":")[-1]
    if short in SPAWNER_NAMES:
        return 101, 0, True
    m = re.fullmatch(r"(?:minecraft:)?(?:legacy_block_|unknown_)(\d+)", base)
    if m:
        legacy = int(m.group(1))
        if legacy == 52:
            return 101, 0, True
        meta = 5 if legacy in TORCHES else 0
        b, d, _ = sanitize_legacy(legacy, meta)
        return b, d, False
    if short in EXTRA and (base.startswith("minecraft:") or ":" not in base or base.startswith("biomesoplenty:")):
        b, d = EXTRA[short]
        return b, d, False
    b, d, _ = convert_state(str(name))
    if b == 101 and short in SPAWNER_NAMES:
        return b, d, True
    return b, d, False


def load(path):
    blocks, names, _, _, _ = load_structure(path)
    lut = np.zeros((len(names), 2), dtype=np.uint16)
    spawner = np.zeros(len(names), dtype=bool)
    true_air = np.zeros(len(names), dtype=bool)
    for i, n in enumerate(names):
        b, d, s = convert(n)
        lut[i] = b, d
        spawner[i] = s
        base = str(n).split("[")[0].split(":")[-1] if n is not None else "air"
        true_air[i] = base in ("air", "cave_air", "void_air") or (b == 0 and base in EXTRA)
    ids = lut[blocks, 0]
    meta = lut[blocks, 1].astype(np.uint8)
    for bid, (nb, nd) in VALUABLE.items():
        mask = ids == bid
        ids[mask] = nb
        meta[mask] = nd
    return ids, meta, true_air[blocks], spawner[blocks]


def crop(ids, meta, air, spw, top_limit):
    """Cut empty outer layers so dimensions are real; keep at most top_limit layers."""
    occ = ids != 0
    ys = np.flatnonzero(occ.any(axis=(0, 2)))
    xs = np.flatnonzero(occ.any(axis=(1, 2)))
    zs = np.flatnonzero(occ.any(axis=(0, 1)))
    y1 = min(ys[-1] + 1, ys[0] + top_limit)
    sl = (slice(xs[0], xs[-1] + 1), slice(ys[0], y1), slice(zs[0], zs[-1] + 1))
    return ids[sl], meta[sl], air[sl], spw[sl]


GROUND_IDS = {1, 2, 3, 4, 12, 13, 24, 80, 82, 110, 208, 159, 98}


def layer_profile(ids):
    fp = ids.shape[0] * ids.shape[2]
    occ = ids != 0
    cov = occ.sum(axis=(0, 2)) / fp
    ground = np.isin(ids, list(GROUND_IDS)).sum(axis=(0, 2)) / fp
    water = np.isin(ids, [8, 9]).sum(axis=(0, 2)) / fp
    return cov, ground, water


def placement(stem, ids):
    """habitat, surfaceAnchor (layers set below grade), waterline (first dry layer for water designs)."""
    cov, ground, water = layer_profile(ids)
    h = ids.shape[1]
    name = stem.lower()
    over = muse_design.HABITAT.get(stem)
    if over:
        habitat = over.get("habitat", "land")
        return habitat, int(over.get("anchor", 1)), over.get("waterline")
    if water[:4].max() >= 0.3 or "ship" in name or "sunny" in name:
        wl = 0
        while wl < h and water[wl] >= 0.2:
            wl += 1
        if wl == 0:  # a dry hull: sit three layers of it below the sea surface
            wl = 3
        return "ocean_surface", 0, int(wl)
    lid = h >= 5 and cov[-3:].min() >= 0.85
    if lid:
        top = h - 1
        return "underground_lid", h - 1 - top, None  # anchor unused for lids; the top layer is the ground
    deep = bool(re.search(r"underground|hideout|bunker|basement|mine_entrance|dwelling", name))
    cap = 12 if deep else 6
    anchor = 0
    while anchor < min(cap, h - 1) and (ground[anchor] >= 0.35 or (anchor <= 1 and cov[anchor] >= 0.9)):
        anchor += 1
    return "land", max(1, anchor), None


def spots(ids, keep_air, rng, limit):
    """Walkable 1x2 standing cells: solid below, passable at feet and head. Interior (kept air) first."""
    x, y, z = ids.shape
    solid = np.isin(ids, list(ARCHITECTURAL))
    passable = np.isin(ids, list(FLOOD_PASSABLE)) & ~np.isin(ids, list(LIQUID))
    stand = np.zeros_like(solid)
    stand[:, 1:-1, :] = solid[:, :-2, :] & passable[:, 1:-1, :] & passable[:, 2:, :]
    interior = stand & keep_air
    exterior = stand & ~keep_air
    out = []
    for mask, kind in ((interior, "in"), (exterior, "out")):
        cand = np.argwhere(mask)
        if not len(cand):
            continue
        want = limit - len(out) if kind == "out" else max(limit * 3 // 4, 1)
        if want <= 0:
            break
        # farthest-point sampling, deterministic start
        picks = [int(rng.integers(len(cand)))]
        dist = np.full(len(cand), np.inf)
        while len(picks) < min(want, len(cand)):
            p = cand[picks[-1]]
            dist = np.minimum(dist, ((cand - p) ** 2).sum(axis=1))
            picks.append(int(dist.argmax()))
        for i in picks:
            out.append([int(v) for v in cand[i]] + [1 if kind == "in" else 0])
    return out


def open_volume(ids, sx, sy, sz):
    passable = np.isin(ids, list(FLOOD_PASSABLE)) & ~np.isin(ids, list(LIQUID))
    x0, x1 = max(0, sx - 5), min(ids.shape[0], sx + 6)
    z0, z1 = max(0, sz - 5), min(ids.shape[2], sz + 6)
    y0, y1 = sy, min(ids.shape[1], sy + 4)
    return int(passable[x0:x1, y0:y1, z0:z1].sum())


def pretty(stem):
    words = stem.split("_")[1:]
    small = {"of", "and", "with", "the", "a", "at", "for", "in", "s"}
    out = []
    for i, w in enumerate(words):
        if w == "s" and out:
            out[-1] += "'s"
            continue
        out.append(w if (w in small and i) else w[:1].upper() + w[1:])
    return " ".join(out)


def build(check=False):
    files = sorted(SOURCE.glob("*.schem"))
    if len(files) != 139:
        raise SystemExit(f"expected 139 Muse+GLM_Maps schematics, found {len(files)}")
    OUT.mkdir(parents=True, exist_ok=True)
    catalog = []
    used_ids = set()
    for f in files:
        stem = f.stem
        ids, meta, air, spw = load(f)
        top_limit = muse_design.HABITAT.get(stem, {}).get("maxHeight", 255)
        ids, meta, air, spw = crop(ids, meta, air, spw, top_limit)
        if np.any(np.isin(ids, list(FORBIDDEN_IDS) + list(VALUABLE))):
            raise AssertionError(f"{stem}: forbidden or valuable block survived")
        keep_air, _ = select_air(ids, air)
        payload = encode_se45(ids, meta, keep_air)
        d_ids, d_meta, d_present = decode_se45(payload)
        if not (np.array_equal(d_ids, ids) and np.array_equal(d_meta, meta)):
            raise AssertionError(f"{stem}: SE45 roundtrip changed blocks")
        sx, sy, sz = (int(v) for v in ids.shape)
        habitat, anchor, waterline = placement(stem, ids)
        rng = np.random.default_rng(int(hashlib.sha256(stem.encode()).hexdigest()[:12], 16))
        nblocks = int((ids != 0).sum())
        limit = int(min(96, 12 + nblocks // 1500))
        sp = spots(ids, keep_air, rng, limit)
        chests = [[int(a), int(b), int(c), int(meta[a, b, c])] for a, b, c in np.argwhere(np.isin(ids, [54, 146]))][:24]
        spawners = [[int(a), int(b), int(c)] for a, b, c in np.argwhere(spw & (ids == 101))][:8]
        tier = 1 if nblocks < 2000 else 2 if nblocks < 6000 else 3 if nblocks < 15000 else 4 if nblocks < 40000 else 5
        sid = "muse:" + stem.lower()
        if sid in used_ids:
            raise AssertionError("duplicate id " + sid)
        used_ids.add(sid)
        arena = None
        if stem in muse_design.BOSSES:
            best = None
            for s in sp:
                v = open_volume(ids, s[0], s[1], s[2]) + (200 if s[3] else 0)
                if best is None or v > best[0]:
                    best = (v, s)
            arena = best[1][:3] if best else [sx // 2, anchor + 1, sz // 2]
        entry = {
            "id": sid, "file": stem + ".se45.gz", "source": f.name, "name": pretty(stem), "collection": COLLECTION,
            "dimensions": [sx, sy, sz], "habitat": habitat, "surfaceAnchor": anchor, "waterline": waterline,
            "tier": tier, "weight": 1.0, "blocks": nblocks, "sha256": hashlib.sha256(payload).hexdigest(),
            "sourceSha256": hashlib.sha256(f.read_bytes()).hexdigest(), "spots": sp, "chests": chests,
            "spawners": spawners, "bossArena": arena,
        }
        entry.update(muse_design.design(stem, entry))
        catalog.append(entry)
        target = OUT / entry["file"]
        if check:
            if not target.exists() or target.read_bytes() != payload:
                raise SystemExit(f"--check: {target.name} is stale")
        else:
            target.write_bytes(payload)
        print(f"{sid:18s} {habitat:15s} a={anchor:<2d} wl={waterline!s:4s} t={tier} {sx}x{sy}x{sz} spots={len(sp)} "
              f"chests={len(chests)} {entry['theme']:9s} {entry['name'][:40]}", flush=True)
    muse_design.validate(catalog)
    text = json.dumps({"schema": 1, "collection": COLLECTION, "count": len(catalog), "sites": catalog},
                      indent=1, sort_keys=True, ensure_ascii=False) + "\n"
    cat_path = OUT / "catalog.json"
    md = muse_design.register(catalog)
    if check:
        if cat_path.read_text(encoding="utf-8") != text or (PLUGIN / "MAPS.md").read_text(encoding="utf-8") != md:
            raise SystemExit("--check: catalog.json or MAPS.md is stale")
        print("CHECK_PASS", len(catalog))
        return
    cat_path.write_text(text, encoding="utf-8", newline="\n")
    (PLUGIN / "MAPS.md").write_text(md, encoding="utf-8", newline="\n")
    print("WROTE", len(catalog), "designs;", sum(p.stat().st_size for p in OUT.glob("*.se45.gz")), "bytes")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    build(ap.parse_args().check)
