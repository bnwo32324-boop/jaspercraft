"""Automated structure metrics for JSD1 dumps.

    python analyze.py DUMP.jsd [...] [--out ANALYSIS_ROOT]
-> SA/analysis/<set>/<id>__<variant>.json

Metrics (coordinates are dump-relative [x,y,z]; add header.origin for world coordinates; for grounds dumps
with a glass tank the tank ring is removed first and 'offset' says how coordinates shift):
  histogram, valuables (CONVENTIONS policy table) with positions, workstations,
  components (6-connected structure solids; grounded = touches the ground / terrain) and floating ones
  (tiny 1-4, fragment 5-63, mass >=64), ladder runs (top exit / ceiling / air, bottom floor),
  stairs with headroom < 2, doors with a blocked front/back, player reachability BFS from outside
  (2-high clearance, jump 1, stairs/slabs step, safe fall <= 3, ladders/vines, swimming, doors/trapdoors/gates
  pass), sealed air pockets and unreachable roofed regions (>= 8 cells), unreachable chests/spawners/beds/...,
  chests that cannot open (solid block above), spawners without spawn space, light (block light + sky light) ->
  dark fraction of reachable floor, rooms per floor with furnishing density and empty shells, ranked suspects.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
from collections import deque, Counter

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blockinfo as BI  # noqa: E402
import mcids  # noqa: E402
from jsd import Dump, read_jsd, id_to_filename  # noqa: E402

try:
    import cv2
except Exception:  # pragma: no cover
    cv2 = None

SA = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit"


# ============================================================================ labelling
def label3d(mask: np.ndarray):
    """6-connected components of a boolean [y,z,x] array without scipy (vectorised union-find).
    Returns (labels int32 array, -1 = background; n_components). Labels are 0..n-1."""
    shape = mask.shape
    flat = mask.ravel()
    idx = np.flatnonzero(flat)
    n = idx.size
    lab = np.full(flat.size, -1, np.int64)
    if n == 0:
        return lab.reshape(shape).astype(np.int32), 0
    comp = np.full(flat.size, -1, np.int64)
    comp[idx] = np.arange(n)
    comp3 = comp.reshape(shape)
    ea, eb = [], []
    for axis in range(3):
        sl_a = [slice(None)] * 3
        sl_b = [slice(None)] * 3
        sl_a[axis] = slice(0, shape[axis] - 1)
        sl_b[axis] = slice(1, shape[axis])
        a = comp3[tuple(sl_a)]
        b = comp3[tuple(sl_b)]
        m = (a >= 0) & (b >= 0)
        ea.append(a[m])
        eb.append(b[m])
    a = np.concatenate(ea)
    b = np.concatenate(eb)
    parent = np.arange(n, dtype=np.int64)
    while a.size:
        pa, pb = parent[a], parent[b]
        m = pa != pb
        if not m.any():
            break
        a, b, pa, pb = a[m], b[m], pa[m], pb[m]
        lo, hi = np.minimum(pa, pb), np.maximum(pa, pb)
        np.minimum.at(parent, hi, lo)
        while True:
            pp = parent[parent]
            if np.array_equal(pp, parent):
                break
            parent = pp
    roots, inv = np.unique(parent, return_inverse=True)
    lab[idx] = inv
    return lab.reshape(shape).astype(np.int32), int(roots.size)


def label2d(mask2: np.ndarray):
    if cv2 is not None:
        n, lab = cv2.connectedComponents(mask2.astype(np.uint8), connectivity=4)
        lab = lab.astype(np.int32) - 1
        return lab, n - 1
    l3, n = label3d(mask2[None])
    return l3[0], n


def _shift(a, axis, step, fill):
    out = np.full_like(a, fill)
    n = a.shape[axis]
    src = [slice(None)] * a.ndim
    dst = [slice(None)] * a.ndim
    if step > 0:
        src[axis] = slice(step, n)
        dst[axis] = slice(0, n - step)
    else:
        src[axis] = slice(0, n + step)
        dst[axis] = slice(-step, n)
    out[tuple(dst)] = a[tuple(src)]
    return out


def any_neighbor6(a):
    r = np.zeros_like(a, dtype=bool)
    for axis in range(3):
        for s in (1, -1):
            r |= _shift(a, axis, s, False)
    return r


# ============================================================================ volume
class Vol:
    """Padded analysis volume. Array coords [y,z,x]; pad offsets (px,py,pz) map back to dump coords."""

    def __init__(self, dump: Dump):
        h = dump.header
        self.h = h
        ids = dump.ids.astype(np.int16)
        data = dump.data.astype(np.int8)
        mask = dump.mask
        g = h.get("grounds") or {}
        self.offset = [0, 0, 0]
        self.notes = []
        if g.get("glassTank") and g.get("footprintRel"):
            fx0, fz0, fx1, fz1 = g["footprintRel"]
            ids, data = ids[:, fz0:fz1, fx0:fx1], data[:, fz0:fz1, fx0:fx1]
            if mask is not None:
                mask = mask[:, fz0:fz1, fx0:fx1]
            self.offset = [fx0, 0, fz0]
            self.notes.append("grounds glass tank ring removed before analysis; positions are relative to the "
                              f"dump origin (tank offset {fx0},{fz0} already added back)")
        self.context = h.get("context", "iso")
        self.natural = mask is not None
        SY, SZ, SX = ids.shape
        self.size = (SX, SY, SZ)
        if self.natural:
            # no side padding (terrain continues); 1 layer of stone below, 2 layers of air above
            self.px, self.pz, self.py = 0, 0, 1
            P = np.zeros((SY + 3, SZ, SX), np.int16)
            D = np.zeros_like(P, dtype=np.int8)
            M = np.zeros_like(P, dtype=np.uint8)
            P[0] = 1
            P[1:SY + 1] = ids
            D[1:SY + 1] = data
            M[1:SY + 1] = mask
            self.plinth = 0
            self.struct = (M == 1)
            self.carved = (M == 2)
            self.terrain = (M == 0) & (P != 0)
            self.terrain[0] = True
        else:
            pad = 3
            self.px, self.pz, self.py = pad, pad, 1
            P = np.zeros((SY + 3, SZ + 2 * pad, SX + 2 * pad), np.int16)
            D = np.zeros_like(P, dtype=np.int8)
            P[0] = 2   # flat ground (grass) under everything
            P[1:SY + 1, pad:pad + SZ, pad:pad + SX] = ids
            D[1:SY + 1, pad:pad + SZ, pad:pad + SX] = data
            self.struct = np.zeros(P.shape, bool)
            self.struct[1:SY + 1, pad:pad + SZ, pad:pad + SX] = ids != 0
            self.carved = np.zeros(P.shape, bool)
            self.terrain = np.zeros(P.shape, bool)
            self.terrain[0] = True
            # Foundation / plinth: surface pieces stand on solid bottom layers that are buried in the live world.
            # If the bottom layers are >=85% solid over the footprint and the footprint's outer ring is fully
            # solid, fill the outside with virtual terrain up to the plinth top (the flat grounds lack it).
            self.plinth = 0
            foot = self.struct.any(axis=0)
            if foot.any():
                colm = BI.COLLIDE[P]
                if cv2 is not None:
                    er = cv2.erode(foot.astype(np.uint8), np.ones((3, 3), np.uint8), borderValue=0).astype(bool)
                else:
                    er = foot & np.roll(foot, 1, 0) & np.roll(foot, -1, 0) & np.roll(foot, 1, 1) & np.roll(foot, -1, 1)
                ring = foot & ~er
                nf, nr = foot.sum(), max(1, ring.sum())
                hp = 0
                for y in range(1, SY + 1):
                    c = colm[y]
                    if (c & foot).sum() >= 0.85 * nf and (c & ring).sum() == nr:
                        hp += 1
                    else:
                        break
                if 2 <= hp < SY:
                    fill = ~foot
                    for y in range(1, hp + 1):
                        P[y][fill] = 2
                        self.terrain[y] |= fill
                    self.plinth = hp
                    self.notes.append(f"solid plinth of {hp} layers: virtual terrain added around the structure up to "
                                      f"y={hp - 1} (grounds lack the live terrain)")
        self.ids = P
        self.data = D
        self.shape = P.shape
        self.origin = h.get("origin", [0, 0, 0])
        # structure footprint (x,z) and roof
        self.foot = self.struct.any(axis=0)

    def rel(self, y, z, x):
        """padded array coords -> dump-relative [x, y, z] (in the original dump incl. tank offset)."""
        return [int(x) - self.px + self.offset[0], int(y) - self.py, int(z) - self.pz + self.offset[2]]

    def world(self, r):
        return [self.origin[0] + r[0], self.origin[1] + r[1], self.origin[2] + r[2]]


# ============================================================================ helpers
def slab_low(ids, data):
    """bottom slabs / stairs / low blocks the player walks onto without jumping."""
    low = BI.IS_LOWISH[ids].copy()
    top_slab = BI.IS_SLAB[ids] & ((data & 8) != 0)
    low &= ~top_slab
    return low


def comp_info(vol, lab, k, cells=None, max_samples=6):
    ys, zs, xs = np.nonzero(lab == k) if cells is None else cells
    n = len(ys)
    bb = [vol.rel(ys.min(), zs.min(), xs.min()), vol.rel(ys.max(), zs.max(), xs.max())]
    step = max(1, n // max_samples)
    samples = [vol.rel(ys[i], zs[i], xs[i]) for i in range(0, n, step)][:max_samples]
    ids = vol.ids[ys, zs, xs]
    comp = Counter(ids.tolist()).most_common(4)
    return {"size": int(n), "bbox": bb, "samples": samples,
            "blocks": [[int(i), mcids.block_name(int(i)), int(c)] for i, c in comp]}


def _pos(vol, y, z, x):
    r = vol.rel(y, z, x)
    return {"rel": r, "world": vol.world(r)}


# ============================================================================ analysis
def analyze(dump: Dump, keep_arrays: bool = False) -> dict:
    t0 = time.time()
    vol = Vol(dump)
    ids, data = vol.ids.astype(np.int64), vol.data.astype(np.int64)
    SYp, SZp, SXp = ids.shape
    DY, DZ = SXp * SZp, SXp
    struct = vol.struct
    out = {"id": dump.header.get("id"), "variant": dump.header.get("variant"), "set": dump.header.get("set"),
           "name": dump.header.get("name"), "context": vol.context, "size": list(vol.size),
           "origin": vol.origin, "notes": vol.notes}
    g = dump.header.get("grounds") or {}
    if g:
        out["grounds"] = {k: g.get(k) for k in ("number", "author", "group", "family", "tier", "kind", "mode",
                                                 "glassTank")}
    timing = {}

    # ---------------------------------------------------------------- histogram
    sid = ids[struct]
    sdt = data[struct]
    st = (sid << 4) | sdt
    u, c = np.unique(st, return_counts=True)
    order = np.argsort(-c)
    out["histogram"] = [[int(u[i] >> 4), int(u[i] & 15), mcids.block_name(int(u[i] >> 4)), int(c[i])] for i in order]
    solid = struct & BI.IS_SOLID[ids]
    out["counts"] = {"structureBlocks": int(struct.sum()), "solid": int(solid.sum()),
                     "liquid": int((struct & BI.IS_LIQUID[ids]).sum()),
                     "chests": int((struct & np.isin(ids, [54, 146])).sum()),
                     "spawners": int((struct & (ids == 52)).sum()),
                     "ladders": int((struct & (ids == 65)).sum()),
                     "doors": int((struct & BI.IS_DOOR[ids] & ((data & 8) == 0)).sum()),
                     "stairs": int((struct & BI.IS_STAIRS[ids]).sum()),
                     "lights": int((struct & (BI.LIGHT[ids] >= 8)).sum()),
                     "tiles": len(dump.header.get("tiles", []))}

    # ---------------------------------------------------------------- valuables
    vals = []
    for vid, vname in BI.VALUABLE.items():
        m = struct & (ids == vid)
        n = int(m.sum())
        if not n:
            continue
        ys, zs, xs = np.nonzero(m)
        pos = [vol.rel(ys[i], zs[i], xs[i]) for i in range(min(n, 400))]
        e = {"id": vid, "name": vname, "count": n, "replacement": BI.VALUABLE_REPLACEMENT[vid],
             "positions": pos, "positionsTruncated": n > 400}
        if vid == 152:
            cons = np.isin(ids, list(BI.REDSTONE_CONSUMERS))
            adj = any_neighbor6(cons) & m
            if adj.any():
                yy, zz, xx = np.nonzero(adj)
                e["powersSomething"] = [vol.rel(yy[i], zz[i], xx[i]) for i in range(min(len(yy), 100))]
        vals.append(e)
    out["valuables"] = {"total": int(sum(v["count"] for v in vals)), "byId": vals}
    ws = []
    for wid, wname in BI.WORKSTATIONS.items():
        m = struct & (ids == wid)
        if m.any():
            ys, zs, xs = np.nonzero(m)
            ws.append({"id": wid, "name": wname, "count": int(m.sum()),
                       "positions": [vol.rel(ys[i], zs[i], xs[i]) for i in range(min(len(ys), 50))]})
    out["workstations"] = ws
    timing["hist"] = time.time() - t0

    # ---------------------------------------------------------------- components
    t = time.time()
    # structure liquids connect (pools, fountains, sewers); sizes count solid blocks only
    conn = solid | (struct & BI.IS_LIQUID[ids])
    lab, ncomp = label3d(conn)
    ground_solid = vol.terrain & BI.IS_SOLID[ids]
    touch = any_neighbor6(ground_solid) & conn
    if not vol.natural:
        # grounds/iso: the structure's base layer counts as ground (buried pieces often start with a pool,
        # lava moat or shaft whose surrounding rock is missing on the flat grounds)
        occ_y = np.nonzero(conn.any(axis=(1, 2)))[0]
        if occ_y.size:
            touch[occ_y[0]] |= conn[occ_y[0]]
    grounded_lab = np.unique(lab[touch])
    grounded_lab = grounded_lab[grounded_lab >= 0]
    sizes = np.bincount(lab[solid & (lab >= 0)], minlength=ncomp) if ncomp else np.zeros(0, int)
    is_ground = np.zeros(ncomp, bool)
    is_ground[grounded_lab] = True
    floating = []
    fl_ids = np.nonzero(~is_ground & (sizes > 0))[0]
    fl_ids = fl_ids[np.argsort(-sizes[fl_ids])]
    tot_float = 0
    for k in fl_ids:
        tot_float += int(sizes[k])
    for k in fl_ids[:60]:
        info = comp_info(vol, lab, k, np.nonzero((lab == k) & solid))
        sz = info["size"]
        info["class"] = "tiny" if sz <= 4 else ("fragment" if sz < 64 else "mass")
        floating.append(info)
    cls = Counter("tiny" if sizes[k] <= 4 else ("fragment" if sizes[k] < 64 else "mass") for k in fl_ids)
    # all floating solid cells: summary by block and the full position list of tiny floaters (repair input)
    fl_mask = np.zeros(ncomp, bool)
    fl_mask[fl_ids] = True
    fcell = solid & (lab >= 0)
    fcell &= fl_mask[np.where(lab >= 0, lab, 0)]
    by_block = Counter(ids[fcell].tolist())
    tiny_mask = np.zeros(ncomp, bool)
    tiny_mask[[k for k in fl_ids if sizes[k] <= 4]] = True
    tcell = fcell & tiny_mask[np.where(lab >= 0, lab, 0)]
    tys, tzs, txs = np.nonzero(tcell)
    tiny_pos = [vol.rel(tys[i], tzs[i], txs[i]) + [int(ids[tys[i], tzs[i], txs[i]])] for i in range(min(len(tys), 2000))]
    out["components"] = {"count": int(ncomp), "grounded": int(is_ground.sum()), "floating": int(len(fl_ids)),
                         "floatingBlocks": int(tot_float), "floatingTiny": cls.get("tiny", 0),
                         "floatingFragments": cls.get("fragment", 0), "floatingMasses": cls.get("mass", 0),
                         "largestGrounded": int(sizes[is_ground].max()) if is_ground.any() else 0,
                         "floatingByBlock": {mcids.block_name(int(k)): int(v) for k, v in by_block.most_common()},
                         "floatingList": floating,
                         "tinyFloaterPositions": tiny_pos, "tinyFloaterPositionsNote": "[x, y, z, blockId] dump-relative"}
    timing["components"] = time.time() - t

    # ---------------------------------------------------------------- movement primitives
    pas = BI.PASSABLE[ids]
    col = BI.COLLIDE[ids]
    climb = BI.IS_CLIMB[ids]
    water = BI.IS_WATER[ids]
    tall = BI.IS_TALL[ids]
    low = slab_low(ids, data)
    # borders: never step outside the volume
    border = np.zeros(ids.shape, bool)
    border[:, 0, :] = border[:, -1, :] = True
    border[:, :, 0] = border[:, :, -1] = True
    border[-1] = True
    border[0] = True
    pas_b = pas & ~border
    P = bytearray(pas_b.ravel().astype(np.uint8).tobytes())
    C = bytearray(col.ravel().astype(np.uint8).tobytes())
    CL = bytearray(climb.ravel().astype(np.uint8).tobytes())
    W = bytearray(water.ravel().astype(np.uint8).tobytes())
    TL = bytearray(tall.ravel().astype(np.uint8).tobytes())
    LO = bytearray(low.ravel().astype(np.uint8).tobytes())

    # sky exposure: nothing with collision above
    colc = np.flip(np.cumsum(np.flip(col, 0), axis=0), 0)   # number of collide cells at >= y
    above_col = np.zeros(ids.shape, bool)
    above_col[:-1] = colc[1:] > 0
    sky = ~above_col

    # ---------------------------------------------------------------- start cells
    stand = pas_b & _shift(pas_b, 0, 1, False) & (_shift(col, 0, -1, False) | climb | water)
    starts = np.zeros(ids.shape, bool)
    if vol.natural:
        ring = np.zeros(ids.shape, bool)
        ring[:, 1:3, :] = ring[:, -3:-1, :] = True
        ring[:, :, 1:3] = ring[:, :, -3:-1] = True
        starts = stand & sky & ring
        if not starts.any():
            starts = stand & sky & ~vol.foot[None]
    else:
        ys0 = 1 + vol.plinth
        starts[ys0, 1:3, :] = starts[ys0, -3:-1, :] = True
        starts[ys0, :, 1:3] = starts[ys0, :, -3:-1] = True
        starts &= stand
    t = time.time()

    def fall_from(n):
        g = n
        k = 0
        while k < 96:
            g2 = g - DY
            if not P[g2]:
                break
            g = g2
            k += 1
            if CL[g] or W[g]:
                break
        return g, k

    dirs = (1, -1, DZ, -DZ)

    def bfs(start_mask, maxfall=3):
        seen = bytearray(ids.size)
        q = deque()
        for f in np.flatnonzero(start_mask.ravel()).tolist():
            seen[f] = 1
            q.append(f)
        while q:
            f = q.popleft()
            fu = f + DY
            onclimb = CL[f] or W[f]
            head2 = P[fu + DY]
            for d in dirs:
                n = f + d
                if P[n]:
                    if P[n + DY]:
                        if C[n - DY] or CL[n] or W[n]:
                            if not seen[n]:
                                seen[n] = 1
                                q.append(n)
                        else:
                            gcell, k = fall_from(n)
                            if (k <= maxfall or W[gcell] or CL[gcell]) and not seen[gcell] and P[gcell + DY]:
                                seen[gcell] = 1
                                q.append(gcell)
                        continue
                # step / jump up onto the block at n
                if C[n] and not TL[n]:
                    tcell = n + DY
                    if P[tcell] and P[tcell + DY] and (LO[n] or onclimb or head2):
                        if not seen[tcell]:
                            seen[tcell] = 1
                            q.append(tcell)
            if onclimb:
                u = fu
                if P[u] and P[u + DY] and (CL[u] or W[u] or C[u - DY]):
                    if not seen[u]:
                        seen[u] = 1
                        q.append(u)
                dn = f - DY
                if P[dn]:
                    if CL[dn] or W[dn] or C[dn - DY]:
                        if not seen[dn]:
                            seen[dn] = 1
                            q.append(dn)
                    else:
                        gcell, k = fall_from(dn)
                        if (k <= maxfall or W[gcell] or CL[gcell]) and not seen[gcell] and P[gcell + DY]:
                            seen[gcell] = 1
                            q.append(gcell)
        return np.frombuffer(bytes(seen), dtype=np.uint8).reshape(ids.shape).astype(bool)

    feet_ground = bfs(starts)
    open_starts = starts if vol.natural else (starts | (stand & sky))
    feet = bfs(open_starts) if open_starts.sum() > starts.sum() else feet_ground
    # lenient variant: falls up to 20 blocks (hurts, survivable from full health)
    feet_hurt = bfs(open_starts, maxfall=20)
    occ = feet | _shift(feet, 0, -1, False)
    # accessible airspace: passable cells straight above an occupied cell
    acc = occ.copy()
    for y in range(1, SYp):
        acc[y] |= acc[y - 1] & pas[y]
    acc_h = feet_hurt | _shift(feet_hurt, 0, -1, False)
    for y in range(1, SYp):
        acc_h[y] |= acc_h[y - 1] & pas[y]
    timing["reach"] = time.time() - t

    # ---------------------------------------------------------------- roofs / interior
    t = time.time()
    sc = np.flip(np.cumsum(np.flip(struct & col, 0), axis=0), 0)
    roofed = np.zeros(ids.shape, bool)
    roofed[:-1] = sc[1:] > 0
    inside_fp = vol.foot[None] & np.ones((SYp, 1, 1), bool)
    interior_air = pas & ~BI.IS_LIQUID[ids] & roofed & inside_fp
    if vol.natural:
        # only structure-owned air: carved by the builder or under a structure roof within its footprint
        interior_air &= (vol.carved | ~vol.terrain)
    # sealed pockets: passable components with no link to the volume boundary or the sky
    lab_air, nair = label3d(pas & ~BI.IS_LIQUID[ids] | water)
    outside_lab = np.unique(lab_air[(border | sky) & (lab_air >= 0)])
    sealed = []
    sealed_cells = np.zeros(ids.shape, bool)
    if nair:
        is_out = np.zeros(nair, bool)
        is_out[outside_lab[outside_lab >= 0]] = True
        asz = np.bincount(lab_air[lab_air >= 0], minlength=nair)
        cand = np.nonzero(~is_out & (asz >= 8))[0]
        for k in cand[np.argsort(-asz[cand])][:40]:
            cells = np.nonzero(lab_air == k)
            own = interior_air[cells].mean()
            if own < 0.5:
                continue
            info = comp_info(vol, lab_air, k, cells)
            sealed_cells[cells] = True
            # contents adjacent to the pocket
            adj = any_neighbor6(lab_air == k)
            cont = Counter(ids[adj & np.isin(ids, list(BI.INTERACT_KINDS))].tolist())
            info["adjacent"] = {BI.INTERACT_KINDS[int(i)]: int(c) for i, c in cont.items()}
            sealed.append(info)
    unreach_reg = []
    ur = interior_air & ~acc & ~sealed_cells
    lab_u, nu = label3d(ur)
    if nu:
        usz = np.bincount(lab_u[lab_u >= 0], minlength=nu)
        cand = np.nonzero(usz >= 8)[0]
        for k in cand[np.argsort(-usz[cand])][:40]:
            cells = np.nonzero(lab_u == k)
            info = comp_info(vol, lab_u, k, cells)
            reg = lab_u == k
            adj = any_neighbor6(reg)
            cont = Counter(ids[adj & np.isin(ids, list(BI.INTERACT_KINDS))].tolist())
            info["adjacent"] = {BI.INTERACT_KINDS[int(i)]: int(c) for i, c in cont.items()}
            # open = touches the accessible airspace (e.g. air above tall furniture / a gallery you can see);
            # enclosed = walled off from everything the player can reach
            info["openToReachable"] = bool((adj & acc & ~reg).any())
            info["standableCells"] = int((reg & stand).sum())
            unreach_reg.append(info)
    interior_total = int(interior_air.sum())
    out["enclosed"] = {"interiorAirCells": interior_total,
                       "interiorAccessibleCells": int((interior_air & acc).sum()),
                       "interiorAccessibleFraction": round(float((interior_air & acc).sum()) / max(1, interior_total), 3),
                       "interiorAccessibleWithFallDamageFraction":
                           round(float((interior_air & acc_h).sum()) / max(1, interior_total), 3),
                       "sealedPockets": sealed, "sealedCells": int(sealed_cells.sum()),
                       "unreachableRegions": unreach_reg,
                       "unreachableRegionCells": int(sum(r["size"] for r in unreach_reg)),
                       "unreachableEnclosedCells": int(sum(r["size"] for r in unreach_reg if not r["openToReachable"]))}
    timing["enclosed"] = time.time() - t

    # ---------------------------------------------------------------- interactables
    t = time.time()
    # 26-neighbourhood = dilate along all three axes successively
    o1 = occ.copy()
    for axis in range(3):
        o1 = o1 | _shift(o1, axis, 1, False) | _shift(o1, axis, -1, False)
    near = o1
    inter = []
    unreachable = Counter()
    for iid, kind in BI.INTERACT_KINDS.items():
        m = struct & (ids == iid)
        if iid == 26:
            m &= (data & 8) != 0   # one entry per bed (head)
        if not m.any():
            continue
        ys, zs, xs = np.nonzero(m)
        for i in range(len(ys)):
            y, z, x = ys[i], zs[i], xs[i]
            e = {"kind": kind, **_pos(vol, y, z, x), "reachable": bool(near[y, z, x])}
            if iid in (54, 146):
                above = ids[y + 1, z, x] if y + 1 < SYp else 0
                if BI.OPAQUE[above]:
                    e["lidBlocked"] = mcids.block_name(int(above))
            if iid == 52:
                y0, y1 = max(y - 1, 1), min(y + 2, SYp - 2)
                z0, z1 = max(z - 4, 0), min(z + 5, SZp)
                x0, x1 = max(x - 4, 0), min(x + 5, SXp)
                sp = pas[y0:y1, z0:z1, x0:x1] & pas[y0 + 1:y1 + 1, z0:z1, x0:x1] & col[y0 - 1:y1 - 1, z0:z1, x0:x1] \
                    & ~BI.IS_LIQUID[ids[y0:y1, z0:z1, x0:x1]]
                e["spawnSpaces"] = int(sp.sum())
                mob = None
                for tl in dump.header.get("tiles", []):
                    r = e["rel"]
                    if tl.get("kind") == "spawner" and [tl["x"], tl["y"], tl["z"]] == r:
                        mob = tl.get("mob")
                if mob:
                    e["mob"] = mob
            if not e["reachable"]:
                unreachable[kind] += 1
            inter.append(e)
    out["interactables"] = {"count": len(inter), "unreachable": dict(unreachable),
                            "items": inter[:600], "truncated": len(inter) > 600}
    timing["interact"] = time.time() - t

    # ---------------------------------------------------------------- ladders
    t = time.time()
    ladders = []
    lm = struct & (ids == 65)
    if lm.any():
        ys, zs, xs = np.nonzero(lm)
        seenl = set()
        order = np.lexsort((ys, zs, xs))
        for i in order:
            y, z, x = int(ys[i]), int(zs[i]), int(xs[i])
            if (y, z, x) in seenl:
                continue
            y1 = y
            while y1 + 1 < SYp and ids[y1 + 1, z, x] == 65:
                y1 += 1
            y0 = y
            while y0 - 1 >= 0 and ids[y0 - 1, z, x] == 65:
                y0 -= 1
            for yy in range(y0, y1 + 1):
                seenl.add((yy, z, x))
            probs = []

            def standable(yy, zz, xx):
                return (0 < yy < SYp - 1 and 0 <= zz < SZp and 0 <= xx < SXp and pas[yy, zz, xx]
                        and pas[yy + 1, zz, xx] and col[yy - 1, zz, xx])
            nbrs = ((0, 1), (0, -1), (1, 0), (-1, 0))
            top_exit = False
            tcell = y1 + 1
            for dz, dx in nbrs:
                if standable(y1, z + dz, x + dx) or standable(y1 + 1, z + dz, x + dx):
                    top_exit = True
            if tcell < SYp - 1 and pas[tcell, z, x] and pas[tcell + 1, z, x]:
                for dz, dx in nbrs:
                    if standable(tcell, z + dz, x + dx):
                        top_exit = True
            top = "exit"
            if not top_exit:
                top = "ceiling" if (tcell < SYp and not pas[tcell, z, x]) else "air"
                probs.append("top ends at a ceiling" if top == "ceiling" else "top ends in air (no floor to step onto)")
            bottom = "floor"
            if not col[y0 - 1, z, x]:
                side = any(standable(y0, z + dz, x + dx) for dz, dx in nbrs)
                if side:
                    bottom = "side-exit"
                elif y0 >= 2 and col[y0 - 2, z, x] and pas[y0 - 1, z, x]:
                    bottom = "jump"
                else:
                    gap = 0
                    yy = y0 - 1
                    while yy > 0 and not col[yy, z, x]:
                        gap += 1
                        yy -= 1
                    bottom = f"hangs {gap} above floor"
                    probs.append(f"bottom hangs {gap} blocks above the floor")
            fac = int(data[y1, z, x]) & 7
            back = {2: (0, 1, 0), 3: (0, -1, 0), 4: (0, 0, 1), 5: (0, 0, -1)}.get(fac)
            unattached = 0
            if back:
                for yy in range(y0, y1 + 1):
                    bz, bx = z + back[1], x + back[2]
                    if 0 <= bz < SZp and 0 <= bx < SXp and not BI.OPAQUE[ids[yy, bz, bx]] and not col[yy, bz, bx]:
                        unattached += 1
            if unattached:
                probs.append(f"{unattached} ladder block(s) with nothing behind them")
            ladders.append({"bottom": vol.rel(y0, z, x), "top": vol.rel(y1, z, x), "length": y1 - y0 + 1,
                            "topStatus": top, "bottomStatus": bottom, "reachable": bool(feet[y0:y1 + 1, z, x].any()),
                            "problems": probs})
    out["ladders"] = {"runs": len(ladders), "problemRuns": sum(1 for l_ in ladders if l_["problems"]),
                      "list": ladders[:200]}

    # ---------------------------------------------------------------- stairs
    sm = struct & BI.IS_STAIRS[ids] & ((data & 4) == 0)
    blocked = []
    stair_runs = 0
    if sm.any():
        ys, zs, xs = np.nonzero(sm)
        fvec = {0: (1, 0), 1: (-1, 0), 2: (0, 1), 3: (0, -1)}
        for i in range(len(ys)):
            y, z, x = int(ys[i]), int(zs[i]), int(xs[i])
            dx, dz = fvec[int(data[y, z, x]) & 3]
            fac = int(data[y, z, x]) & 3

            def same(yy, zz, xx):
                return (0 <= yy < SYp and 0 <= zz < SZp and 0 <= xx < SXp and BI.IS_STAIRS[ids[yy, zz, xx]]
                        and (int(data[yy, zz, xx]) & 7) == fac)
            nxt = same(y + 1, z + dz, x + dx)
            prv = same(y - 1, z - dz, x - dx)
            if not (nxt or prv):
                continue
            stair_runs += 1
            if y + 2 >= SYp:
                continue
            pz0, px0 = z - dz, x - dx
            used = bool(feet[y + 1, z, x]) or (0 <= pz0 < SZp and 0 <= px0 < SXp and
                                               bool(feet[y, pz0, px0] or feet[y + 1, pz0, px0]))
            head_ok = pas[y + 1, z, x] and pas[y + 2, z, x]
            if not head_ok:
                what = mcids.block_name(int(ids[y + 1, z, x] if not pas[y + 1, z, x] else ids[y + 2, z, x]))
                blocked.append({**_pos(vol, y, z, x), "reason": f"headroom < 2 above this step ({what})",
                                "reachable": used})
            elif not nxt:
                # top of the run: can the player step off?
                ty, tz, tx = y + 1, z + dz, x + dx
                ok = not (0 <= tz < SZp and 0 <= tx < SXp)

                def pz_(yy):
                    return True if yy >= SYp else bool(pas[yy, tz, tx])
                if not ok:
                    if pz_(ty) and pz_(ty + 1):
                        ok = True
                    elif ty < SYp and col[ty, tz, tx] and pz_(ty + 1) and pz_(ty + 2):
                        ok = True
                if not ok:
                    blocked.append({**_pos(vol, y, z, x), "reason": "top of stair run leads into a wall/ceiling",
                                    "reachable": used})
    out["stairs"] = {"inRuns": stair_runs, "blocked": len(blocked),
                     "blockedReachable": sum(1 for b_ in blocked if b_["reachable"]), "list": blocked[:200]}

    # ---------------------------------------------------------------- doors
    dm = struct & BI.IS_DOOR[ids] & ((data & 8) == 0)
    doors = []
    if dm.any():
        ys, zs, xs = np.nonzero(dm)
        for i in range(len(ys)):
            y, z, x = int(ys[i]), int(zs[i]), int(xs[i])
            fac = int(data[y, z, x]) & 3
            axis = "x" if fac in (0, 2) else "z"
            probs = []
            if y + 1 >= SYp or ids[y + 1, z, x] != ids[y, z, x]:
                if y > 0 and ids[y - 1, z, x] == ids[y, z, x] and (int(data[y - 1, z, x]) & 8) == 0:
                    probs.append("second LOWER door half stacked on a lower half (upper half, data|8, never placed)")
                else:
                    probs.append("upper half missing")
            elif (int(data[y + 1, z, x]) & 8) == 0:
                probs.append("door block above is another lower half (data|8 upper half missing)")
            for s, side in ((1, "+"), (-1, "-")):
                zz, xx = (z, x + s) if axis == "x" else (z + s, x)
                if not (0 <= zz < SZp and 0 <= xx < SXp):
                    continue
                if not (pas[y, zz, xx] and (y + 1 >= SYp or pas[y + 1, zz, xx])):
                    blk = ids[y, zz, xx] if not pas[y, zz, xx] else ids[y + 1, zz, xx]
                    probs.append(f"{side}{axis} side blocked by {mcids.block_name(int(blk))}")
            if probs:
                doors.append({**_pos(vol, y, z, x), "axis": axis, "problems": probs,
                              "reachable": bool(occ[y, z, x] or near[y, z, x])})
    out["doors"] = {"count": int(dm.sum()), "problem": len(doors), "list": doors[:200]}
    timing["ladders_stairs_doors"] = time.time() - t

    # ---------------------------------------------------------------- light
    t = time.time()
    op = BI.OPAQUE[ids]
    att = BI.ATTEN[ids].astype(np.int16)
    blk = BI.LIGHT[ids].astype(np.int16)
    emit = blk.copy()
    L = blk.copy()
    for _ in range(15):
        nb = np.zeros_like(L)
        for axis in range(3):
            for s in (1, -1):
                np.maximum(nb, _shift(L, axis, s, 0), out=nb)
        cand = np.where(op, 0, nb - 1 - att)
        newL = np.maximum(np.maximum(L, cand), emit)
        if np.array_equal(newL, L):
            break
        L = newL
    block_light = L
    # sky light: straight down from the top until the first opaque block, then spread
    skyl = np.zeros_like(L)
    opc = np.flip(np.cumsum(np.flip(op, 0), axis=0), 0)
    open_above = np.zeros(ids.shape, bool)
    open_above[:-1] = opc[1:] == 0
    open_above[-1] = True
    skyl[open_above & ~op] = 15
    skyl = np.where(water & open_above, 13, skyl)
    S = skyl.copy()
    for _ in range(15):
        nb = np.zeros_like(S)
        for axis in range(3):
            for s in (1, -1):
                np.maximum(nb, _shift(S, axis, s, 0), out=nb)
        cand = np.where(op, 0, nb - 1 - att)
        newS = np.maximum(S, cand)
        if np.array_equal(newS, S):
            break
        S = newS
    light = np.maximum(block_light, S)
    floor_cells = feet & inside_fp & struct.any(axis=0)[None]
    int_floor = floor_cells & roofed
    lf = light[floor_cells]
    li = light[int_floor]
    bi = block_light[int_floor]
    out["light"] = {"reachableFloorCells": int(floor_cells.sum()),
                    "darkFraction": round(float((lf < 8).mean()), 3) if lf.size else None,
                    "interiorFloorCells": int(int_floor.sum()),
                    "interiorDarkFraction": round(float((li < 8).mean()), 3) if li.size else None,
                    "interiorDarkAtNightFraction": round(float((bi < 8).mean()), 3) if bi.size else None,
                    "emitters": int((struct & (BI.LIGHT[ids] > 0)).sum())}
    timing["light"] = time.time() - t

    # ---------------------------------------------------------------- rooms & furnishing
    t = time.time()
    stand_all = pas & _shift(pas, 0, 1, False) & _shift(col, 0, -1, False) & ~BI.IS_DOOR[ids] & ~BI.IS_LIQUID[ids]
    stand_all &= roofed & inside_fp
    if vol.natural:
        stand_all &= ~vol.terrain
    furn = struct & BI.IS_FURNISH[ids]
    lights = struct & (BI.LIGHT[ids] >= 8)
    rooms = []
    shells = 0
    kern = np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]], np.uint8)
    for y in range(1, SYp - 2):
        lay = stand_all[y]
        if not vol.natural and y == vol.py and vol.plinth == 0:
            continue   # grounds/iso: y=0 stands on the flat ground (space under raised parts), not a room floor
        if lay.sum() < 6:
            continue
        lab2, n2 = label2d(lay)
        if n2 <= 0:
            continue
        a2 = np.bincount(lab2[lab2 >= 0], minlength=n2)
        f_lay = furn[y] | furn[y + 1] | (furn[y - 1] & ~BI.IS_SOLID[ids[y - 1]])
        l_lay = lights[y] | lights[y + 1] | lights[min(y + 2, SYp - 1)] | lights[min(y + 3, SYp - 1)] | lights[y - 1]
        for k in np.nonzero(a2 >= 6)[0]:
            reg = lab2 == k
            if cv2 is not None:
                dil = cv2.dilate(reg.astype(np.uint8), kern).astype(bool)
            else:
                dil = reg | np.roll(reg, 1, 0) | np.roll(reg, -1, 0) | np.roll(reg, 1, 1) | np.roll(reg, -1, 1)
            nf = int((f_lay & dil).sum())
            nl = int((l_lay & dil).sum())
            area = int(a2[k])
            zs2, xs2 = np.nonzero(reg)
            reach = float(feet[y][reg].mean())
            ml = float(light[y][reg].mean())
            shell = area >= 16 and nf == 0
            shells += shell
            rooms.append({"y": y - vol.py, "area": area, "furnishing": nf, "lights": nl,
                          "density": round(nf / area, 3), "reachableFraction": round(reach, 2),
                          "meanLight": round(ml, 1), "emptyShell": bool(shell),
                          "bbox": [vol.rel(y, zs2.min(), xs2.min()), vol.rel(y, zs2.max(), xs2.max())]})
    rooms.sort(key=lambda r: -r["area"])
    tot_area = sum(r["area"] for r in rooms)
    out["rooms"] = {"count": len(rooms), "emptyShells": int(shells), "totalArea": tot_area,
                    "furnishingPerCell": round(sum(r["furnishing"] for r in rooms) / max(1, tot_area), 3),
                    "list": rooms[:60]}
    timing["rooms"] = time.time() - t

    # ---------------------------------------------------------------- reach summary
    int_feet = stand_all  # roofed standable interior cells (computed for rooms)
    out["reach"] = {"plinthLayers": vol.plinth, "startMode": "surface ring (natural)" if vol.natural else
                    "ground ring at y=0 + every sky-exposed standable cell (roofs, terraces, shaft tops)",
                    "startCells": int(open_starts.sum()), "reachedFeetCells": int(feet.sum()),
                    "reachedStructureFeetCells": int((feet & inside_fp).sum()),
                    "interiorStandCells": int(int_feet.sum()),
                    "interiorReachedFraction": round(float((feet & int_feet).sum()) / max(1, int(int_feet.sum())), 3),
                    "interiorReachedFromGroundOnlyFraction":
                        round(float((feet_ground & int_feet).sum()) / max(1, int(int_feet.sum())), 3),
                    "interiorReachedWithFallDamageFraction":
                        round(float((feet_hurt & int_feet).sum()) / max(1, int(int_feet.sum())), 3)}

    # ---------------------------------------------------------------- suspects
    sus = []

    def add(score, kind, pos, reason):
        e = {"score": round(float(score), 1), "kind": kind, "reason": reason}
        if pos is not None:
            e["rel"] = pos
            e["world"] = vol.world(pos)
        sus.append(e)
    for f in floating:
        if f["class"] == "mass":
            add(90 + math.log10(f["size"]) * 3, "floating-mass", f["samples"][0],
                f"unsupported mass of {f['size']} blocks ({', '.join(b[1] for b in f['blocks'][:2])}) bbox {f['bbox']}")
        elif f["class"] == "fragment":
            add(60 + f["size"] / 10, "floating-fragment", f["samples"][0],
                f"floating fragment of {f['size']} blocks ({', '.join(b[1] for b in f['blocks'][:2])})")
    tiny = [f for f in floating if f["class"] == "tiny"]
    if tiny:
        byb = ", ".join(f"{k} {v}" for k, v in list(out["components"]["floatingByBlock"].items())[:5])
        add(28 + min(len(tiny), 20), "floating-tiny", tiny[0]["samples"][0],
            f"{out['components']['floatingTiny']} tiny floating piece(s) (1-4 blocks; floating blocks by type: {byb}), e.g. "
            + "; ".join(f"{t_['blocks'][0][1]}@{t_['samples'][0]}" for t_ in tiny[:4]))
    for e in inter:
        if e["kind"] in ("chest", "trapped chest") and not e["reachable"]:
            add(72, "chest-unreachable", e["rel"], "chest cannot be reached by walking/climbing from outside")
        if e.get("lidBlocked"):
            add(66, "chest-lid-blocked", e["rel"], f"chest cannot open: solid {e['lidBlocked']} directly above")
        if e["kind"] == "spawner":
            if e.get("spawnSpaces", 1) == 0:
                add(56, "spawner-no-space", e["rel"], f"spawner ({e.get('mob', '?')}) has no 2-high spawn space in range")
            elif not e["reachable"]:
                add(38, "spawner-unreachable", e["rel"], f"spawner ({e.get('mob', '?')}) is not next to any reachable cell")
        if e["kind"] in ("bed", "crafting table", "furnace", "anvil", "enchanting table", "brewing stand") and not e["reachable"]:
            add(35, e["kind"].replace(" ", "-") + "-unreachable", e["rel"], f"{e['kind']} unreachable")
    for s_ in sealed:
        extra = f", adjacent {s_['adjacent']}" if s_["adjacent"] else ""
        add(50 + (15 if s_["adjacent"] else 0) + min(15, s_["size"] / 40), "sealed-pocket", s_["samples"][0],
            f"sealed air pocket of {s_['size']} cells, bbox {s_['bbox']}{extra}")
    for r in unreach_reg:
        extra = f", adjacent {r['adjacent']}" if r["adjacent"] else ""
        if not r["openToReachable"]:
            add(46 + (12 if r["adjacent"] else 0) + min(18, r["size"] / 40), "unreachable-room", r["samples"][0],
                f"walled-off air region of {r['size']} cells ({r['standableCells']} standable) the player cannot "
                f"reach, bbox {r['bbox']}{extra}")
        elif r["standableCells"] >= 12 or r["adjacent"]:
            add(28 + (12 if r["adjacent"] else 0) + min(10, r["standableCells"] / 20), "unreachable-floor",
                r["samples"][0], f"open but unreachable area: {r['standableCells']} standable cells in {r['size']} "
                f"air cells (visible from reachable space, no way up/down), bbox {r['bbox']}{extra}")
    for l_ in ladders:
        for p in l_["problems"]:
            sc_ = 60 if "top" in p else (50 if "bottom" in p else 40)
            add(sc_, "ladder", l_["top"] if "top" in p else l_["bottom"], f"ladder run {l_['bottom']}..{l_['top']}: {p}")
    for b in blocked:
        if b["reachable"]:
            add(48, "stairs", b["rel"], b["reason"])
    for d_ in doors:
        add(54 if d_["reachable"] else 40, "door", d_["rel"], "door: " + "; ".join(d_["problems"]))
    lt = out["light"]
    if lt["interiorDarkFraction"] is not None and lt["interiorFloorCells"] >= 20 and lt["interiorDarkFraction"] > 0.5:
        add(30 + 10 * lt["interiorDarkFraction"], "dark", None,
            f"{int(lt['interiorDarkFraction'] * 100)}% of the reachable interior floor has light < 8")
    for r in rooms:
        if r["emptyShell"]:
            add(22 + min(10, r["area"] / 30), "empty-room", r["bbox"][0],
                f"room of {r['area']} floor cells at y={r['y']} with no furnishing, bbox {r['bbox']}")
    for v in vals:
        add(20 + min(15, v["count"] / 10), "valuable", v["positions"][0],
            f"{v['count']} x {v['name']} ({v['id']}) used as building/decor -> {v['replacement']}")
    if out["enclosed"]["interiorAirCells"] >= 50 and out["enclosed"]["interiorAccessibleFraction"] < 0.3:
        add(70, "inaccessible", None, f"only {int(out['enclosed']['interiorAccessibleFraction'] * 100)}% of the "
            f"interior air is accessible from outside without a fall > 3 "
            f"({int(out['enclosed']['interiorAccessibleWithFallDamageFraction'] * 100)}% accepting falls up to 20)")
    sus.sort(key=lambda s: -s["score"])
    out["suspects"] = sus[:150]
    out["suspectCounts"] = dict(Counter(s["kind"] for s in sus))
    out["autoVerdict"] = auto_verdict(out)
    timing["total"] = time.time() - t0
    out["timing"] = {k: round(v, 3) for k, v in timing.items()}
    if keep_arrays:
        out["_arrays"] = {"vol": vol, "feet": feet, "feet_ground": feet_ground, "starts": open_starts, "acc": acc,
                          "light": light, "stand": stand, "roofed": roofed, "lab": lab}
    return out


def auto_verdict(a):
    """Transparent rule-of-thumb classification (NOT a visual inspection)."""
    c = a["components"]
    solid = max(1, a["counts"]["solid"])
    fl_frac = c["floatingBlocks"] / solid
    enc = a["enclosed"]
    reasons = []
    unreach_chests = a["interactables"]["unreachable"].get("chest", 0) + a["interactables"]["unreachable"].get("trapped chest", 0)
    lid = sum(1 for e in a["interactables"]["items"] if e.get("lidBlocked"))
    acc = enc["interiorAccessibleFraction"] if enc["interiorAirCells"] >= 50 else 1.0
    acc_h = enc.get("interiorAccessibleWithFallDamageFraction", acc) if enc["interiorAirCells"] >= 50 else 1.0
    if fl_frac > 0.2 or acc_h < 0.15:
        reasons.append(f"floating {fl_frac:.0%} of solids / {c['floatingMasses']} masses / interior accessible "
                       f"{acc:.0%} ({acc_h:.0%} accepting fall damage)")
        return {"verdict": "severely incomplete", "reasons": reasons}
    bad = 0
    if c["floatingMasses"]:
        bad += 2 if (c["floatingMasses"] >= 3 or fl_frac > 0.05) else 1
        reasons.append(f"{c['floatingMasses']} floating mass(es), {fl_frac:.1%} of solids")
    if c["floatingFragments"] >= 3:
        bad += 1; reasons.append(f"{c['floatingFragments']} floating fragments")
    if unreach_chests or lid:
        bad += 1; reasons.append(f"{unreach_chests} unreachable chest(s), {lid} lid-blocked")
    if a["ladders"]["problemRuns"] >= 2:
        bad += 1; reasons.append(f"{a['ladders']['problemRuns']} ladder runs with problems")
    if a["doors"]["problem"] >= 2:
        bad += 1; reasons.append(f"{a['doors']['problem']} blocked doors")
    if acc < 0.5:
        bad += 1; reasons.append(f"interior accessible {acc:.0%} ({acc_h:.0%} accepting fall damage)")
    if enc["sealedCells"] >= 100:
        bad += 1; reasons.append(f"{enc['sealedCells']} sealed air cells")
    if bad >= 2:
        return {"verdict": "defective", "reasons": reasons}
    minor = 0
    if c["floatingTiny"] + c["floatingFragments"] > 4:
        minor += 1; reasons.append(f"{c['floatingTiny']} tiny floaters, {c['floatingFragments']} fragments")
    if a["stairs"].get("blockedReachable", 0) >= 2:
        minor += 1; reasons.append(f"{a['stairs']['blockedReachable']} blocked stairs in use")
    es, nr = a["rooms"]["emptyShells"], max(1, a["rooms"]["count"])
    if es >= 3 and es / nr > 0.25:
        minor += 1; reasons.append(f"{es} of {nr} rooms empty")
    lt = a["light"]["interiorDarkFraction"]
    if lt is not None and lt > 0.6:
        minor += 1; reasons.append(f"interior {lt:.0%} dark")
    if bad == 0 and minor == 0:
        return {"verdict": "good", "reasons": ["no automated problems"]}
    return {"verdict": "passable", "reasons": reasons}


def out_path(dump, root=None):
    root = root or os.path.join(SA, "analysis")
    h = dump.header
    d = os.path.join(root, h.get("set", "misc"))
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, id_to_filename(h.get("id", "x")) + "__" + str(h.get("variant", "x")) + ".json")


def analyze_file(path, root=None):
    d = read_jsd(path)
    a = analyze(d)
    p = out_path(d, root)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(a, f, indent=1)
    return p, a


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("dumps", nargs="+")
    ap.add_argument("--out", default=None)
    a = ap.parse_args(argv)
    for p in a.dumps:
        path, res = analyze_file(p, a.out)
        c = res["components"]
        print(f"{res['id']}: {res['autoVerdict']['verdict']} floating={c['floating']} ({c['floatingBlocks']} blk) "
              f"suspects={len(res['suspects'])} t={res['timing']['total']}s -> {path}")


if __name__ == "__main__":
    main()
