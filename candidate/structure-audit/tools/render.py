"""Textured isometric / plan / section renderer for JSD1 structure dumps.

    python render.py DUMP.jsd [DUMP2.jsd ...] [--out RENDERS_ROOT] [--views iso,under,cut,plan,sec,contact]

Writes SA/renders/<set>/<id>__<variant>/ :
    iso_NE/NW/SE/SW.png   exterior from each corner, 30 deg down (2:1 dimetric)
    iso_under.png         from below (SE corner, 30 deg up)
    cut_yNN.png           SE iso with everything above y=NN removed (one per detected floor, max 10)
    plan_yNN.png          top-down plan: floor NN-1, contents NN and NN+1, symbols, grid, legend
    sec_x.png / sec_z.png vertical sections through the middle (plane x=const / z=const)
    contact.png           triage sheet: 4 iso views + up to 4 plans
    zoom_NE|SW_rRcC.png   only when the whole-structure iso is < 16 px/block: the NE and SW views re-rendered at
                          2x scale and cut into <=1500 px tiles (overlap 80 px)
    views.json            what was rendered (floor levels, scale, timings)
    python render.py DUMP.jsd --focus x,y,z[,r]   close-up of one (dump-relative) position:
                          focus_x_y_z_SE/NW.png (cut above y+2, red marker) + focus_x_y_z_plan.png
Natural dumps (hasMask) get ctx_iso_* (terrain, cut away above/around buried sites, desaturated) and
str_iso_* (mask==1 only); cut/plan/sec use the ctx volume. Coordinates in all labels are dump-relative
(array) coordinates; world = origin + relative (origin printed in every title).
Textures come from site/assets.epk (see textures.py).
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blockinfo as BI  # noqa: E402
import blockmodels as BM  # noqa: E402
import textures  # noqa: E402
from jsd import Dump, read_jsd, id_to_filename  # noqa: E402

SA = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit"
MAXPX = 1600
BG = (206, 212, 218)
BG_DARK = (58, 62, 70)
ELEV_H = 0.6124          # vertical cube edge / diamond width for a 30 degree elevation


def _font(size, bold=False):
    for f in (("arialbd.ttf" if bold else "arial.ttf"), "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except Exception:
            pass
    return ImageFont.load_default()


F_TITLE = _font(15, True)
F_SMALL = _font(11)
F_TINY = _font(9)
F_SYM = _font(10, True)


# ============================================================================ views
class View:
    def __init__(self, name, ex, ey, ez, d, cull, shade_face=None):
        self.name = name
        self.ex, self.ey, self.ez = np.array(ex, float), np.array(ey, float), np.array(ez, float)
        self.d = np.array(d, float)
        self.cull = cull                  # list of (axis, step) neighbours toward the viewer
        self.sign = np.sign(self.d).astype(int)
        self.shade_face = shade_face or {}
        self.key = (name, tuple(ex), tuple(ey), tuple(ez))

    def proj(self, p):
        p = np.asarray(p, float)
        return p[..., 0:1] * self.ex + p[..., 1:2] * self.ey + p[..., 2:3] * self.ez


CORNERS = {"NE": (1, -1), "NW": (-1, -1), "SE": (1, 1), "SW": (-1, 1)}


def iso_view(corner, w, below=False):
    sx, sz = CORNERS[corner]
    a, b = w // 2, w // 4
    h = int(round(w * ELEV_H))
    yb = -1 if below else 1
    ex = (sz * a, yb * sx * b)
    ez = (-sx * a, yb * sz * b)
    ey = (0, -h)
    d = (sx, yb * (2.0 * b / h), sz)
    cull = [(2, sx), (1, yb), (0, sz)]          # array axes: 0=y 1=z 2=x ; (axis, step)
    cull = [(2, sx), (0, yb), (1, sz)]
    return View(("under_" if below else "iso_") + corner, ex, ey, ez, d, cull)


def top_view(k):
    return View("top", (k, 0), (0, 0), (0, k), (0, 1, 0), [(0, 1)], {"up": 1.0})


def section_view(axis, k):
    if axis == "x":      # plane x = const, seen from +x (east); +z to the left
        return View("secx", (0, 0), (0, -k), (-k, 0), (1, 0, 0), [(2, 1)], {"east": 0.95})
    return View("secz", (k, 0), (0, -k), (0, 0), (0, 0, 1), [(1, 1)], {"south": 0.95})


FACE_SHADE = {(0, 1, 0): 1.0, (0, -1, 0): 0.5, (0, 0, -1): 0.8, (0, 0, 1): 0.8, (-1, 0, 0): 0.64, (1, 0, 0): 0.64}
FACE_NAME = {(0, 1, 0): "up", (0, -1, 0): "down", (0, 0, -1): "north", (0, 0, 1): "south", (-1, 0, 0): "west",
             (1, 0, 0): "east"}


# ============================================================================ sprites
_SPRITES = {}


def _sprite_box(view):
    c = np.array([[x, y, z] for x in (0, 1) for y in (0, 1) for z in (0, 1)], float)
    p = view.proj(c)
    x0, y0 = np.floor(p.min(0)).astype(int)
    x1, y1 = np.ceil(p.max(0)).astype(int)
    return x0, y0, max(x1 - x0, 1), max(y1 - y0, 1)


def make_sprite(bid, data, ctx, view, ss=2):
    key = (bid, data, ctx, view.key)
    hit = _SPRITES.get(key)
    if hit is not None:
        return hit
    m = BM.model(bid, data, ctx)
    if m is None or not m.quads:
        _SPRITES[key] = None
        return None
    ox, oy, W, H = _sprite_box(view)
    Ws, Hs = W * ss, H * ss
    col = np.zeros((Hs, Ws, 3), np.float32)
    alp = np.zeros((Hs, Ws), np.float32)
    zb = np.full((Hs, Ws), -1e9, np.float32)
    d = view.d
    emissive = m.emissive
    cut = bool(ctx & BM.CUT)
    terrain = bool(ctx & BM.TERRAIN)
    dim = (ctx >> BM.DIM_SHIFT) & 3
    items = []
    for q in m.quads:
        nd = float(np.dot(q.n, d))
        if not q.two and nd <= 1e-6:
            continue
        P0 = (view.proj(q.o) - (ox, oy)) * ss
        U = view.proj(q.e1) * ss
        V = view.proj(q.e2) * ss
        det = U[0] * V[1] - U[1] * V[0]
        if abs(det) < 1e-6:
            continue
        depth0 = float(np.dot(q.o + 0.5 * q.e1 + 0.5 * q.e2, d))
        items.append((depth0, q, P0, U, V, det))
    if not items:
        _SPRITES[key] = None
        return None
    translucent = m.translucent
    items.sort(key=lambda t: t[0])
    for depth0, q, P0, U, V, det in items:
        pts = np.array([P0, P0 + U, P0 + V, P0 + U + V])
        ix0 = max(int(np.floor(pts[:, 0].min())), 0)
        ix1 = min(int(np.ceil(pts[:, 0].max())), Ws)
        iy0 = max(int(np.floor(pts[:, 1].min())), 0)
        iy1 = min(int(np.ceil(pts[:, 1].max())), Hs)
        if ix1 <= ix0 or iy1 <= iy0:
            continue
        px = np.arange(ix0, ix1, dtype=np.float32) + 0.5 - P0[0]
        py = np.arange(iy0, iy1, dtype=np.float32) + 0.5 - P0[1]
        rx, ry = np.meshgrid(px, py)
        a = (rx * V[1] - ry * V[0]) / det
        b = (U[0] * ry - U[1] * rx) / det
        inside = (a >= 0) & (a < 1) & (b >= 0) & (b < 1)
        if not inside.any():
            continue
        tex = q.tex
        th, tw = tex.shape[0], tex.shape[1]
        u0, v0, u1, v1 = q.uv
        uu = (u0 + a * (u1 - u0)) / 16.0 * tw
        vv = (v0 + b * (v1 - v0)) / 16.0 * th
        tx = np.clip(uu.astype(np.int32), 0, tw - 1)
        ty = np.clip(vv.astype(np.int32), 0, th - 1)
        texel = tex[ty, tx]
        rgb = texel[..., :3].copy()
        ta = texel[..., 3]
        nkey = tuple(int(round(x)) for x in q.n)
        fname = FACE_NAME.get(nkey)
        if q.shade is not None:
            shade = q.shade
        elif fname in view.shade_face:
            shade = view.shade_face[fname]
        else:
            shade = FACE_SHADE.get(nkey, 0.85)
        if emissive:
            shade = 1.0
            rgb = rgb * 0.82 + 0.18 * np.array([1.0, 0.95, 0.75], np.float32)
        rgb = rgb * shade
        if cut and fname == "up" and q.o[1] > 0.99:
            rgb = rgb * 0.45 + np.array([0.18, 0.05, 0.05], np.float32)
        if terrain:
            g = rgb.mean(axis=-1, keepdims=True)
            rgb = (rgb * 0.35 + g * 0.65) * 0.9 + 0.04
        if dim:
            f = (1.0, 0.62, 0.48, 0.36)[dim]
            g = rgb.mean(axis=-1, keepdims=True)
            rgb = (rgb * (1 - 0.3 * dim) + g * 0.3 * dim) * f + (1 - f) * 0.55
        depth = depth0 + (a - 0.5) * float(np.dot(q.e1, d)) + (b - 0.5) * float(np.dot(q.e2, d))
        sl = (slice(iy0, iy1), slice(ix0, ix1))
        if not translucent:
            msk = inside & (ta > 0.5) & (depth >= zb[sl] - 1e-5)
            c = col[sl]
            c[msk] = rgb[msk]
            al = alp[sl]
            al[msk] = 1.0
            z = zb[sl]
            z[msk] = depth[msk]
        else:
            msk = inside & (ta > 0.02)
            aa = np.where(msk, ta, 0)[..., None]
            c = col[sl]
            al = alp[sl]
            col[sl] = rgb * aa + c * (1 - aa)
            alp[sl] = aa[..., 0] + al * (1 - aa[..., 0])
    # downsample (premultiplied box filter)
    if ss > 1:
        pc = (col * alp[..., None]).reshape(H, ss, W, ss, 3).mean(axis=(1, 3))
        pa = alp.reshape(H, ss, W, ss).mean(axis=(1, 3))
        rgb = np.where(pa[..., None] > 1e-6, pc / np.maximum(pa[..., None], 1e-6), 0)
    else:
        rgb, pa = col, alp
    if pa.max() <= 0.01:
        _SPRITES[key] = None
        return None
    arr = np.dstack([np.clip(rgb, 0, 1) * 255, np.clip(pa, 0, 1) * 255]).astype(np.uint8)
    spr = (Image.fromarray(arr, "RGBA"), ox, oy)
    _SPRITES[key] = spr
    return spr


# ============================================================================ context (ctx) per cell
def _shift(a, axis, step, fill):
    """out[i] = a[i+step] along axis (neighbour value), fill outside."""
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


def compute_ctx(ids, data, cut_y=None, orig_ids=None, terrain=None):
    ctx = np.zeros(ids.shape, np.int64)
    full = BM.FULL_CUBE[ids]
    # connections (N=-z, E=+x, S=+z, W=-x)
    needs = np.isin(ids, list(BM.NEEDS_CONN))
    if needs.any():
        nb = {"N": _shift(ids, 1, -1, 0), "E": _shift(ids, 2, 1, 0), "S": _shift(ids, 1, 1, 0), "W": _shift(ids, 2, -1, 0)}
        nbfull = {k: BM.FULL_CUBE[v] for k, v in nb.items()}
        bits = {"N": BM.C_N, "E": BM.C_E, "S": BM.C_S, "W": BM.C_W}
        fence = np.isin(ids, list(BM.FENCE_MAT))
        pane = np.isin(ids, [101, 102, 160])
        wal = ids == 139
        for k, v in nb.items():
            isf = np.isin(v, list(BM.CONNECT_FENCE))
            isp = np.isin(v, list(BM.CONNECT_PANE))
            isw = np.isin(v, list(BM.CONNECT_WALL))
            nether = (v == 113)
            conn = (fence & ((isf & ((v == 113) == (ids == 113))) | nbfull[k])) | \
                   (pane & (isp | nbfull[k])) | (wal & (isw | nbfull[k]))
            _ = nether
            ctx |= np.where(conn, bits[k], 0)
    # same-type neighbours for glass-like and liquids
    same = np.isin(ids, list(BM.SAME_CULL))
    if same.any():
        fam = ids.astype(np.int64).copy()
        fam[(ids == 8) | (ids == 9)] = 8
        fam[(ids == 10) | (ids == 11)] = 10
        key = fam * 16 + np.where(np.isin(ids, [95]), data, 0)
        key = np.where(same, key, -1)
        for axis, step, bit in ((0, 1, BM.S_UP), (0, -1, BM.S_DOWN), (1, -1, BM.S_N), (1, 1, BM.S_S),
                                (2, -1, BM.S_W), (2, 1, BM.S_E)):
            nk = _shift(key, axis, step, -2)
            ctx |= np.where(same & (nk == key), bit, 0)
        liq = np.isin(ids, [8, 9, 10, 11])
        above = _shift(fam, 0, 1, -1)
        ctx |= np.where(liq & (above == fam), BM.LIQ_ABOVE, 0)
    # doors: other half's data
    door = np.isin(ids, list(BM.DOOR_IDS))
    if door.any():
        up_ids, up_d = _shift(ids, 0, 1, 0), _shift(data, 0, 1, 0)
        dn_ids, dn_d = _shift(ids, 0, -1, 0), _shift(data, 0, -1, 0)
        upper = (data & 8) != 0
        other = np.where(upper, np.where(dn_ids == ids, dn_d, 0), np.where(up_ids == ids, up_d, 0))
        ctx |= np.where(door, other.astype(np.int64) << BM.EXTRA_SHIFT, 0)
    dp = (ids == 175) & ((data & 8) != 0)
    if dp.any():
        dn_ids, dn_d = _shift(ids, 0, -1, 0), _shift(data, 0, -1, 0)
        ctx |= np.where(dp & (dn_ids == 175), dn_d.astype(np.int64) << BM.EXTRA_SHIFT, 0)
    if cut_y is not None and orig_ids is not None and 0 <= cut_y < ids.shape[0] - 1:
        lay = np.zeros(ids.shape, bool)
        lay[cut_y] = (orig_ids[cut_y + 1] != 0) & (ids[cut_y] != 0)
        ctx |= np.where(lay, BM.CUT, 0)
    if terrain is not None:
        ctx |= np.where(terrain, BM.TERRAIN, 0)
    _ = full
    return ctx


# ============================================================================ scene
def render_scene(ids, data, view, ctx=None, canvas=None, origin_px=None, bg=BG, extra_ctx=None, ss=2, cells=None):
    """Paint every visible non-air cell of (ids,data) [y,z,x] with painter's order into a new image.
    Returns (image, origin_px) where origin_px is the screen position of array point (0,0,0)."""
    SY, SZ, SX = ids.shape
    nonair = (ids != 0) & (ids != 36) & (ids != 217)
    if cells is not None:
        nonair &= cells
    full = BM.FULL_CUBE[ids] & nonair
    hidden = np.ones(ids.shape, bool)
    for axis, step in view.cull:
        hidden &= _shift(full, axis, step, False)
    vis = nonair & ~hidden
    if ctx is None:
        ctx = compute_ctx(ids, data)
    if extra_ctx is not None:
        ctx = ctx | extra_ctx
    if canvas is None:
        corners = np.array([[x, y, z] for x in (0, SX) for y in (0, SY) for z in (0, SZ)], float)
        p = view.proj(corners)
        pad = 12
        x0, y0 = p.min(0)
        x1, y1 = p.max(0)
        W, H = int(math.ceil(x1 - x0)) + 2 * pad, int(math.ceil(y1 - y0)) + 2 * pad
        canvas = Image.new("RGB", (max(W, 16), max(H, 16)), bg)
        origin_px = (pad - x0, pad - y0)
    ys, zs, xs = np.nonzero(vis)
    if len(ys) == 0:
        return canvas, origin_px
    sgn = view.sign
    key = xs * sgn[0] + ys * sgn[1] + zs * sgn[2]
    order = np.argsort(key, kind="stable")
    ys, zs, xs = ys[order], zs[order], xs[order]
    st = (ids[ys, zs, xs].astype(np.int64) << 4) | data[ys, zs, xs]
    cx = ctx[ys, zs, xs]
    comb = (st << 32) | cx
    uniq, inv = np.unique(comb, return_inverse=True)
    sprites = []
    for u in uniq:
        s = int(u >> 32)
        c = int(u & 0xFFFFFFFF)
        sprites.append(make_sprite(s >> 4, s & 15, c, view, ss))
    X = xs * view.ex[0] + ys * view.ey[0] + zs * view.ez[0] + origin_px[0]
    Y = xs * view.ex[1] + ys * view.ey[1] + zs * view.ez[1] + origin_px[1]
    X = np.round(X).astype(int)
    Y = np.round(Y).astype(int)
    paste = canvas.paste
    for i in range(len(xs)):
        spr = sprites[inv[i]]
        if spr is None:
            continue
        im, ox, oy = spr
        paste(im, (int(X[i]) + ox, int(Y[i]) + oy), im)
    return canvas, origin_px


# ============================================================================ helpers
def choose_w(SX, SY, SZ, maxpx=MAXPX - 40, wmax=64, wmin=8):
    w = wmax
    while w > wmin:
        W = (SX + SZ) * w / 2
        H = (SX + SZ) * w / 4 + SY * round(w * ELEV_H)
        if max(W, H + 40) <= maxpx:
            break
        w -= 4
    return w


def title_bar(img, text, sub=None, dark=False):
    W, H = img.size
    th = 40 if sub else 24
    probe = ImageDraw.Draw(img)
    tw = max(probe.textlength(text, font=F_TITLE), probe.textlength(sub or "", font=F_SMALL)) + 14
    W2 = int(max(W, min(tw, MAXPX)))
    out = Image.new("RGB", (W2, H + th), BG_DARK if dark else (40, 44, 52))
    if W2 > W:
        out.paste((206, 212, 218), (0, th, W2, H + th))
    out.paste(img, ((W2 - W) // 2, th))
    d = ImageDraw.Draw(out)
    d.text((6, 4), text, fill=(240, 240, 240), font=F_TITLE)
    if sub:
        d.text((6, 22), sub, fill=(190, 200, 210), font=F_SMALL)
    return out


def fit(img, maxpx=MAXPX):
    W, H = img.size
    s = min(1.0, maxpx / max(W, H))
    if s < 1.0:
        img = img.resize((max(1, int(W * s)), max(1, int(H * s))), Image.LANCZOS)
    return img


def draw_ground(img, view, origin_px, SX, SZ, y=0, color=(170, 186, 150), grid=(145, 160, 128), step=4):
    d = ImageDraw.Draw(img)

    def P(x, z):
        p = view.proj([x, y, z])
        return (p[0] + origin_px[0], p[1] + origin_px[1])
    d.polygon([P(0, 0), P(SX, 0), P(SX, SZ), P(0, SZ)], fill=color)
    for x in range(0, SX + 1, step):
        d.line([P(x, 0), P(x, SZ)], fill=grid, width=1)
    for z in range(0, SZ + 1, step):
        d.line([P(0, z), P(SX, z)], fill=grid, width=1)


def draw_compass(img, view, pos=(40, 40), r=22):
    d = ImageDraw.Draw(img)
    cx, cy = pos
    for lab, vec, colr in (("N", (0, 0, -1), (200, 40, 40)), ("E", (1, 0, 0), (40, 40, 40))):
        p = view.proj(vec)
        n = math.hypot(p[0], p[1]) or 1
        ex, ey = cx + p[0] / n * r, cy + p[1] / n * r
        d.line([(cx, cy), (ex, ey)], fill=colr, width=2)
        d.text((ex + (3 if p[0] >= 0 else -10), ey - 6), lab, fill=colr, font=F_SMALL)
    d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=(40, 40, 40))


# ============================================================================ volume preparation
class Scene:
    """Arrays + metadata used by all views of one dump."""

    def __init__(self, dump: Dump, mode="auto"):
        self.dump = dump
        h = dump.header
        self.h = h
        self.ids = dump.ids.astype(np.int64)
        self.data = dump.data.astype(np.int64)
        self.mask = dump.mask
        self.context = h.get("context", "iso")
        g = h.get("grounds") or {}
        self.notes = []
        self.origin = list(h.get("origin", [0, 0, 0]))
        self.off = (0, 0)
        if g.get("glassTank") and g.get("footprintRel"):
            fx0, fz0, fx1, fz1 = g["footprintRel"]
            self.off = (fx0, fz0)
            self.ids = self.ids[:, fz0:fz1, fx0:fx1].copy()
            self.data = self.data[:, fz0:fz1, fx0:fx1].copy()
            self.origin = [self.origin[0] + fx0, self.origin[1], self.origin[2] + fz0]
            self.notes.append(f"grounds glass tank omitted (cropped to footprint, offset {fx0},{fz0})")
        self.ground = self.context in ("grounds", "iso")    # flat ground under y=0
        self.terrain = None
        self.struct = None
        if self.mask is not None:
            self.struct = (self.mask == 1) | (self.mask == 2)
            self.terrain = (self.mask == 0) & (self.ids != 0)
            self.ground = False


def ctx_volume(sc: Scene, corner=None):
    """Natural context. Buried sites: terrain above the structure's roof (per column) is removed; outside the
    footprint terrain is cut down to the structure's base, except - when an iso corner is given - on the two
    far sides, where it is kept up to the structure's top as a rock backdrop (shows the contact with rock
    without hiding anything). Surface sites keep their terrain."""
    ids, data, mask = sc.ids.copy(), sc.data.copy(), sc.mask
    SY, SZ, SX = ids.shape
    sm = mask == 1
    if not sm.any():
        return ids, data, sc.terrain
    sa = (mask == 1) | (mask == 2)
    ys = np.arange(SY)[:, None, None]
    coltop = np.where(sa, ys, -1).max(axis=0)          # [z,x]
    ymin = int(np.nonzero(sm.any(axis=(1, 2)))[0][0])
    mode = ((sc.h.get("site") or {}).get("mode") or "")
    terr = sc.terrain.copy()
    # how buried is it: fraction of structure-top columns covered by terrain above
    cover = (terr & (ys > coltop[None])).any(axis=0) & (coltop >= 0)
    buried = mode in ("buried", "underwater") or cover.sum() > 0.3 * max(1, (coltop >= 0).sum())
    if buried:
        lim = np.where(coltop >= 0, coltop, ymin - 1)
        if corner is not None:
            sx, sz = CORNERS[corner]
            zz, xx = np.nonzero(coltop >= 0)
            bx0, bx1, bz0, bz1 = xx.min(), xx.max(), zz.min(), zz.max()
            ytop = int(np.nonzero(sm.any(axis=(1, 2)))[0][-1])
            X = np.arange(SX)[None, :]
            Z = np.arange(SZ)[:, None]
            far_x = (X < bx0) if sx > 0 else (X > bx1)
            far_z = (Z < bz0) if sz > 0 else (Z > bz1)
            back = (far_x | far_z) & (coltop < 0)
            lim = np.where(back, ytop, lim)
        above = ys > lim[None]
        remove = terr & above
        ids[remove] = 0
        data[remove] = 0
        # water above buried structures (underwater) would hide it too
        wat = np.isin(ids, [8, 9]) & (mask == 0) & above
        ids[wat] = 0
        data[wat] = 0
    return ids, data, (mask == 0) & (ids != 0)


def floor_levels(ids, ground=True, maxn=10, struct_roof=None):
    """Detect walkable floor levels: y with many 'roofed standable' cells (feet y, head y+1 passable,
    support at y-1). Returns sorted list of y."""
    SY, SZ, SX = ids.shape
    pas = BI.PASSABLE[ids]
    col = BI.COLLIDE[ids]
    sup = np.zeros_like(col)
    sup[1:] = col[:-1]
    if ground:
        sup[0] = True
    head = np.zeros_like(pas)
    head[:-1] = pas[1:]
    head[-1] = True
    stand = pas & head & (sup | BI.IS_CLIMB[ids])
    roofsrc = col if struct_roof is None else (col & struct_roof)
    # roofed: something with collision above (anywhere higher in the column)
    rev = np.flip(np.cumsum(np.flip(roofsrc, 0), axis=0), 0)   # count of collide at >= y
    roofed = np.zeros_like(col)
    roofed[:-2] = rev[2:] > 0
    cnt = (stand & roofed).sum(axis=(1, 2))
    area = max(1, int((ids != 0).any(axis=0).sum()))
    thr = max(6, 0.03 * area)
    # greedy cover: a plan at L shows symbols for feet levels L-1..L+1; pick the L covering most
    # not-yet-covered roofed standable cells until >= 92% are covered (max maxn levels)
    rem = cnt.astype(np.int64).copy()
    total = int(rem.sum())
    sel = []
    while len(sel) < maxn and total > 0:
        gain = np.array([rem[max(0, L - 1):L + 2].sum() + rem[L] for L in range(SY)])
        L = int(np.argmax(gain))
        if gain[L] - rem[L] < thr or rem.sum() <= 0.08 * total:
            break
        sel.append(L)
        rem[max(0, L - 1):L + 2] = 0
    sel = sorted(sel)
    if not sel:
        tot = (stand).sum(axis=(1, 2))
        sel = [int(np.argmax(tot))] if tot.max() > 0 else [min(1, SY - 1)]
    return [int(y) for y in sel], cnt


# ============================================================================ plan view
LEGEND = [("wall (blocked at y, y+1)", "wall"), ("1-high gap (low headroom)", "low"),
          ("no floor within 4 below", "void"),
          ("ladder", "ladder"), ("door", "door"), ("trapdoor/gate", "trap"), ("chest", "chest"),
          ("spawner", "spawner"), ("stairs (arrow = up)", "stairs"), ("light source", "light"),
          ("bed / workstation", "func"), ("valuable block", "valuable"), ("water / lava", "liquid")]


def _sym(d, kind, x, y, k, extra=None):
    c = k / 2
    cx, cy = x + c, y + c
    r = max(3, k * 0.36)
    if kind == "ladder":
        d.line([(cx - r * 0.6, cy - r), (cx - r * 0.6, cy + r)], fill=(120, 72, 20), width=2)
        d.line([(cx + r * 0.6, cy - r), (cx + r * 0.6, cy + r)], fill=(120, 72, 20), width=2)
        for t in (-0.5, 0, 0.5):
            d.line([(cx - r * 0.6, cy + t * r), (cx + r * 0.6, cy + t * r)], fill=(230, 180, 90), width=1)
    elif kind == "door":
        d.rectangle([x + 1, y + 1, x + k - 2, y + k - 2], outline=(220, 30, 30), width=2)
        if extra:
            ax = extra
            if ax == "x":
                d.line([(x + 2, cy), (x + k - 3, cy)], fill=(220, 30, 30), width=2)
            else:
                d.line([(cx, y + 2), (cx, y + k - 3)], fill=(220, 30, 30), width=2)
    elif kind == "trap":
        d.rectangle([x + 2, y + 2, x + k - 3, y + k - 3], outline=(230, 140, 20), width=1)
    elif kind == "chest":
        d.rectangle([cx - r, cy - r, cx + r, cy + r], fill=(150, 90, 30), outline=(40, 20, 0))
        if k >= 10:
            d.text((cx - 3, cy - 6), "C", fill=(255, 240, 200), font=F_SYM)
    elif kind == "spawner":
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(210, 20, 20), outline=(60, 0, 0))
        if k >= 10:
            d.text((cx - 3, cy - 6), "S", fill=(255, 255, 255), font=F_SYM)
    elif kind == "stairs":
        dx, dz = extra
        L = r
        d.line([(cx - dx * L, cy - dz * L), (cx + dx * L, cy + dz * L)], fill=(20, 20, 20), width=2)
        hx, hy = cx + dx * L, cy + dz * L
        px, py = -dz, dx
        d.polygon([(hx + dx * 2, hy + dz * 2), (hx - dx * 3 + px * 3, hy - dz * 3 + py * 3),
                   (hx - dx * 3 - px * 3, hy - dz * 3 - py * 3)], fill=(20, 20, 20))
    elif kind == "light":
        rr = max(2, r * 0.55)
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 225, 40) if (extra or 15) >= 8 else (255, 150, 40),
                  outline=(120, 90, 0))
    elif kind == "func":
        d.rectangle([cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7], fill=(150, 80, 200), outline=(50, 0, 80))
        if k >= 12 and extra:
            d.text((cx - 3, cy - 6), extra, fill=(255, 255, 255), font=F_SYM)
    elif kind == "valuable":
        d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=(255, 215, 0), outline=(120, 80, 0))
    elif kind == "wall":
        d.rectangle([x, y, x + k - 1, y + k - 1], fill=(60, 60, 66))
    elif kind == "low":
        d.rectangle([x, y, x + k - 1, y + k - 1], fill=(240, 150, 60))
    elif kind == "void":
        d.rectangle([x, y, x + k - 1, y + k - 1], fill=(255, 215, 215))
        d.line([(x + 2, y + 2), (x + k - 3, y + k - 3)], fill=(200, 40, 40))
        d.line([(x + k - 3, y + 2), (x + 2, y + k - 3)], fill=(200, 40, 40))
    elif kind == "liquid":
        d.rectangle([x, y, x + k - 1, y + k - 1], fill=(60, 110, 230))


FUNC_LETTER = {26: "B", 58: "T", 61: "F", 62: "F", 145: "A", 116: "E", 117: "P", 130: "X", 84: "J", 118: "U",
               154: "H", 23: "D", 158: "D"}


def render_plan(sc: Scene, ids, data, L, title, sub):
    SY, SZ, SX = ids.shape
    k = int(max(6, min(40, math.floor(min((MAXPX - 60) / SX, (MAXPX - 190) / SZ)))))
    lo, hi = max(L - 4, 0), min(L + 2, SY)
    cells = np.zeros(ids.shape, bool)
    cells[lo:hi] = True
    view = top_view(k)
    ctx = compute_ctx(ids, data)
    for yy in range(lo, max(L - 1, 0)):          # floors 1-3 blocks lower than L-1 are drawn dimmed
        ctx[yy] |= min(3, (L - 1) - yy) << BM.DIM_SHIFT
    bgc = (112, 132, 92) if (L == 0 and sc.ground) else (24, 26, 44)   # L=0 on the grounds: the floor is grass
    img, org = render_scene(ids, data, view, ctx=ctx, cells=cells, bg=bgc, ss=1 if k >= 16 else 2)
    # lift dark materials (obsidian, black concrete...) so floors stay legible
    arr = np.asarray(img, dtype=np.float32) / 255.0
    img = Image.fromarray((np.power(arr, 0.72) * 255).astype(np.uint8), "RGB")
    # classification per column
    def lay(y):
        if 0 <= y < SY:
            return ids[y], data[y]
        z = np.zeros((SZ, SX), np.int64)
        return z, z
    f, fd = lay(L - 1)
    a, ad = lay(L)
    b, bd = lay(L + 1)
    c2, _ = lay(L + 2)
    pa, pb = BI.PASSABLE[a], BI.PASSABLE[b]
    wall = ~pa & ~pb
    low = pa & ~pb
    floor_ok = BI.COLLIDE[f] | BI.IS_CLIMB[a] | BI.IS_LIQUID[a] | BI.IS_LIQUID[f] | ((L - 1) < 0 and sc.ground) \
        | BI.IS_CLIMB[f]
    for yy in range(max(L - 4, 0), max(L - 1, 0)):   # a lower floor within 3 blocks is not "void"
        floor_ok = floor_ok | BI.COLLIDE[ids[yy]] | BI.IS_LIQUID[ids[yy]]
    if L - 1 < 0 and sc.ground:
        floor_ok = np.ones_like(floor_ok)
    # only inside the structure (a roof within 8 blocks above); open air around it stays background
    roof8 = np.zeros((SZ, SX), bool)
    for yy in range(L + 2, min(L + 10, SY)):
        roof8 |= BI.COLLIDE[ids[yy]]
    void = pa & pb & ~floor_ok & (a == 0) & roof8
    ox, oy = int(round(org[0])), int(round(org[1]))
    ov = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(ov)
    for z in range(SZ):
        for x in range(SX):
            px, py = ox + x * k, oy + z * k
            if wall[z, x]:
                od.rectangle([px, py, px + k - 1, py + k - 1], fill=(0, 0, 0, 135))
            elif low[z, x]:
                for t in range(-k, k, max(3, k // 3)):
                    od.line([(px + max(t, 0), py + max(-t, 0)), (px + min(k - 1, k - 1 + t), py + min(k - 1, k - 1 - t))],
                            fill=(255, 140, 0, 200), width=1)
            elif void[z, x]:
                od.rectangle([px, py, px + k - 1, py + k - 1], fill=(255, 215, 215, 225))
                od.line([(px + 2, py + 2), (px + k - 3, py + k - 3)], fill=(200, 40, 40, 255))
                od.line([(px + k - 3, py + 2), (px + 2, py + k - 3)], fill=(200, 40, 40, 255))
    img = Image.alpha_composite(img.convert("RGBA"), ov)
    d = ImageDraw.Draw(img)
    # wall outlines
    for z in range(SZ):
        for x in range(SX):
            if not wall[z, x]:
                continue
            px, py = ox + x * k, oy + z * k
            if x + 1 >= SX or not wall[z, x + 1]:
                d.line([(px + k - 1, py), (px + k - 1, py + k - 1)], fill=(10, 10, 10), width=1)
            if x - 1 < 0 or not wall[z, x - 1]:
                d.line([(px, py), (px, py + k - 1)], fill=(10, 10, 10), width=1)
            if z + 1 >= SZ or not wall[z + 1, x]:
                d.line([(px, py + k - 1), (px + k - 1, py + k - 1)], fill=(10, 10, 10), width=1)
            if z - 1 < 0 or not wall[z - 1, x]:
                d.line([(px, py), (px + k - 1, py)], fill=(10, 10, 10), width=1)
    # symbols
    counts = {}
    layers = [(L - 1, f, fd), (L, a, ad), (L + 1, b, bd), (L + 2, c2, None)]
    for z in range(SZ):
        for x in range(SX):
            px, py = ox + x * k, oy + z * k
            av, bv, fv = int(a[z, x]), int(b[z, x]), int(f[z, x])
            if av in (8, 9, 10, 11):
                d.rectangle([px + 1, py + 1, px + k - 2, py + k - 2], outline=(60, 110, 230) if av < 10 else (240, 110, 20))
            if av == 65 or bv == 65:
                _sym(d, "ladder", px, py, k); counts["ladder"] = counts.get("ladder", 0) + 1
            elif av == 106 or bv == 106:
                d.text((px + k / 2 - 3, py + k / 2 - 6), "v", fill=(40, 160, 40), font=F_SYM)
            if av in BI.DOORS and (int(ad[z, x]) & 8) == 0:
                fac = BM.DOOR_FACING[int(ad[z, x]) & 3]
                _sym(d, "door", px, py, k, "z" if fac in ("E", "W") else "x")
                counts["door"] = counts.get("door", 0) + 1
            elif av in BI.TRAPDOORS or av in BI.GATES or fv in BI.TRAPDOORS:
                _sym(d, "trap", px, py, k)
            if av in (54, 146) or fv in (54, 146):
                _sym(d, "chest", px, py, k); counts["chest"] = counts.get("chest", 0) + 1
            if 52 in (av, bv, fv):
                _sym(d, "spawner", px, py, k); counts["spawner"] = counts.get("spawner", 0) + 1
            if av in BI.STAIRS and not (int(ad[z, x]) & 4):
                dd = int(ad[z, x]) & 3
                vec = {0: (1, 0), 1: (-1, 0), 2: (0, 1), 3: (0, -1)}[dd]
                _sym(d, "stairs", px, py, k, vec)
            lv = max(int(BI.LIGHT[fv]), int(BI.LIGHT[av]), int(BI.LIGHT[bv]), int(BI.LIGHT[int(c2[z, x])]))
            if lv >= 5 and not (av in (10, 11) or fv in (10, 11)):
                _sym(d, "light", px, py, k, lv); counts["light"] = counts.get("light", 0) + 1
            for v in (av, fv):
                if v in FUNC_LETTER and v not in (23, 158, 154):
                    _sym(d, "func", px, py, k, FUNC_LETTER[v])
                    break
            if BI.IS_VALUABLE[av] or BI.IS_VALUABLE[bv] or BI.IS_VALUABLE[fv]:
                _sym(d, "valuable", px + k * 0.3, py - k * 0.3, int(k * 0.55))
    # grid + labels
    step = 4 if k >= 9 else 8
    gl = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(gl)
    for x in range(0, SX + 1, step):
        gd.line([(ox + x * k, oy), (ox + x * k, oy + SZ * k)], fill=(255, 255, 255, 150 if x % 16 == 0 else 70), width=1)
    for z in range(0, SZ + 1, step):
        gd.line([(ox, oy + z * k), (ox + SX * k, oy + z * k)], fill=(255, 255, 255, 150 if z % 16 == 0 else 70), width=1)
    img = Image.alpha_composite(img, gl).convert("RGB")
    # frame with margins for labels and legend
    left, top, bottom = 34, 18, 64
    W, H = img.size
    out = Image.new("RGB", (max(W + left + 8, 700), H + top + bottom), (236, 238, 242))
    out.paste(img, (left, top))
    d = ImageDraw.Draw(out)
    for x in range(0, SX + 1, step):
        d.text((left + ox + x * k - 4, 3), str(x), fill=(30, 30, 30), font=F_TINY)
    for z in range(0, SZ + 1, step):
        d.text((4, top + oy + z * k - 5), str(z), fill=(30, 30, 30), font=F_TINY)
    # legend
    lx, ly = 6, top + H + 6
    for lab, kind in LEGEND:
        box = Image.new("RGB", (14, 14), (200, 200, 200))
        bd_ = ImageDraw.Draw(box)
        if kind in ("wall", "low", "void", "liquid"):
            _sym(bd_, kind, 0, 0, 14)
        elif kind == "stairs":
            _sym(bd_, kind, 0, 0, 14, (0, -1))
        elif kind == "door":
            _sym(bd_, kind, 0, 0, 14, "x")
        else:
            _sym(bd_, kind, 0, 0, 14, "B" if kind == "func" else None)
        tw = int(d.textlength(lab, font=F_SMALL)) + 26
        if lx + tw > out.size[0] - 4:
            lx, ly = 6, ly + 18
        out.paste(box, (lx, ly))
        d.text((lx + 17, ly + 1), lab, fill=(20, 20, 20), font=F_SMALL)
        lx += tw
    d.text((6, ly + 19), "north up, east right; x across, z down (dump-relative). Floor = y-1 (greyed: floor 1-3 "
           "blocks lower), contents = y and y+1.",
           fill=(70, 70, 70), font=F_TINY)
    out = title_bar(out, title, sub)
    return fit(out), counts


# ============================================================================ section view
def render_section(sc: Scene, ids, data, axis, title, sub, depth=4):
    SY, SZ, SX = ids.shape
    n_h = SZ if axis == "x" else SX
    k = int(max(4, min(40, math.floor(min((MAXPX - 60) / n_h, (MAXPX - 80) / SY)))))
    view = section_view(axis, k)
    mid = (SX // 2) if axis == "x" else (SZ // 2)
    cells = np.zeros(ids.shape, bool)
    extra = np.zeros(ids.shape, np.int64)
    for i in range(depth):
        c = mid - i
        if c < 0:
            break
        if axis == "x":
            cells[:, :, c] = True
            extra[:, :, c] = min(i, 3) << BM.DIM_SHIFT
        else:
            cells[:, c, :] = True
            extra[:, c, :] = min(i, 3) << BM.DIM_SHIFT
    ctx = compute_ctx(ids, data) | extra
    img, org = render_scene(ids, data, view, ctx=ctx, cells=cells, bg=(214, 222, 230), ss=1 if k >= 16 else 2)
    d = ImageDraw.Draw(img)
    ox, oy = org
    # ground line
    if sc.ground:
        d.line([(ox - n_h * k if axis == "x" else ox, oy), (ox if axis == "x" else ox + n_h * k, oy)],
               fill=(90, 120, 60), width=3)
    left, top = 34, 16
    W, H = img.size
    out = Image.new("RGB", (W + left + 6, H + top + 20), (236, 238, 242))
    out.paste(img, (left, top))
    d = ImageDraw.Draw(out)
    stp = 4 if k >= 8 else 8
    for y in range(0, SY + 1, stp):
        py = top + oy - y * k
        d.text((4, py - 5), str(y), fill=(30, 30, 30), font=F_TINY)
        d.line([(left - 4, py), (left, py)], fill=(30, 30, 30))
    for hcoord in range(0, n_h + 1, stp):
        px = left + ((ox - hcoord * k) if axis == "x" else (ox + hcoord * k))
        d.text((px - 4, top + H + 3), str(hcoord), fill=(30, 30, 30), font=F_TINY)
    out = title_bar(out, title, sub + f"   plane {axis}={mid}, 3 layers behind dimmed; horizontal axis = "
                    + ("z (north at right)" if axis == "x" else "x (east at right)"))
    return fit(out)


# ============================================================================ top-level
def out_dir_for(dump: Dump, root=None):
    root = root or os.path.join(SA, "renders")
    return os.path.join(root, dump.header.get("set", "misc"), id_to_filename(dump.header.get("id", "x")) + "__" +
                        str(dump.header.get("variant", "x")))


def render_iso(ids, data, view, sc, title, sub, terrain=None, ground=None, cut_y=None, orig_ids=None, raw=False):
    SY, SZ, SX = ids.shape
    ctx = compute_ctx(ids, data, cut_y=cut_y, orig_ids=orig_ids, terrain=terrain)
    corners = np.array([[x, y, z] for x in (0, SX) for y in (0, SY) for z in (0, SZ)], float)
    p = view.proj(corners)
    pad = 14
    x0, y0 = p.min(0)
    x1, y1 = p.max(0)
    W, H = int(math.ceil(x1 - x0)) + 2 * pad, int(math.ceil(y1 - y0)) + 2 * pad
    canvas = Image.new("RGB", (W, H), BG)
    org = (pad - x0, pad - y0)
    if ground if ground is not None else sc.ground:
        if not view.name.startswith("under"):
            draw_ground(canvas, view, org, SX, SZ)
    img, _ = render_scene(ids, data, view, ctx=ctx, canvas=canvas, origin_px=org)
    if ground if ground is not None else sc.ground:
        if view.name.startswith("under"):
            # outline of the ground plane seen from below
            d = ImageDraw.Draw(img)
            P = lambda x, z: tuple(view.proj([x, 0, z]) + org)  # noqa: E731
            d.line([P(0, 0), P(SX, 0), P(SX, SZ), P(0, SZ), P(0, 0)], fill=(90, 120, 60), width=1)
    draw_compass(img, view, (34, img.size[1] - 34))
    if raw:
        return img
    return fit(title_bar(img, title, sub))


def tiles_of(img, tile=1500, overlap=80):
    W, H = img.size
    nx = max(1, math.ceil((W - overlap) / (tile - overlap)))
    ny = max(1, math.ceil((H - overlap) / (tile - overlap)))
    tw = min(W, math.ceil((W + (nx - 1) * overlap) / nx))
    th = min(H, math.ceil((H + (ny - 1) * overlap) / ny))
    out = []
    for r in range(ny):
        for c in range(nx):
            x0 = min(c * (tw - overlap), W - tw)
            y0 = min(r * (th - overlap), H - th)
            out.append((r, c, img.crop((x0, y0, x0 + tw, y0 + th))))
    return out, nx, ny


def render_focus(path_or_dump, x, y, z, r=10, out_root=None):
    """Close-up of dump-relative position (x,y,z): iso SE + NW of the surrounding (2r+1)^2 x (2r+1) box and a
    plan at feet level y. Coordinates as printed by analyze.py (dump-relative)."""
    dump = read_jsd(path_or_dump) if isinstance(path_or_dump, str) else path_or_dump
    sc = Scene(dump)
    od = out_dir_for(dump, out_root)
    os.makedirs(od, exist_ok=True)
    ids, data = sc.ids, sc.data
    if sc.mask is not None:
        ids, data, _ = ctx_volume(sc)
    ax, az = x - sc.off[0], z - sc.off[1]
    SY, SZ, SX = ids.shape
    x0, x1 = max(ax - r, 0), min(ax + r + 1, SX)
    z0, z1 = max(az - r, 0), min(az + r + 1, SZ)
    y0, y1 = max(y - r, 0), min(y + r + 1, SY)
    sub_i, sub_d = ids[y0:y1, z0:z1, x0:x1], data[y0:y1, z0:z1, x0:x1]
    h = dump.header
    head = f"{h.get('id')} {h.get('name', '')}  focus ({x},{y},{z}) r={r}"
    files = []
    w = choose_w(x1 - x0, y1 - y0, z1 - z0, wmax=48)
    for c in ("SE", "NW"):
        v = iso_view(c, w)
        sub2 = sub_i.copy()
        # cut everything above y+2 so the spot is visible from above
        sub2[min(y + 3, y1) - y0:] = 0
        img = render_iso(sub2, sub_d, v, sc, head + f"  iso {c}",
                         f"box x{x0 + sc.off[0]}..{x1 - 1 + sc.off[0]} y{y0}..{y1 - 1} z{z0 + sc.off[1]}..{z1 - 1 + sc.off[1]}"
                         f" (dump-relative), cut above y={y + 2}; red post marks the spot", ground=False, raw=True)
        # marker: red vertical line through the target column
        vv = iso_view(c, w)
        corners = np.array([[a, b, cc] for a in (0, x1 - x0) for b in (0, y1 - y0) for cc in (0, z1 - z0)], float)
        pp = vv.proj(corners)
        org = (14 - pp.min(0)[0], 14 - pp.min(0)[1])
        top = vv.proj([ax - x0 + 0.5, (y1 - y0) + 1, az - z0 + 0.5]) + org
        bot = vv.proj([ax - x0 + 0.5, y - y0 + 0.5, az - z0 + 0.5]) + org
        dr = ImageDraw.Draw(img)
        dr.line([tuple(top), tuple(bot)], fill=(230, 20, 20), width=3)
        dr.ellipse([bot[0] - 5, bot[1] - 5, bot[0] + 5, bot[1] + 5], outline=(230, 20, 20), width=2)
        img = fit(title_bar(img, head + f"  iso {c}", f"cut above y={y + 2}; red line marks the column, circle the cell"))
        fn = f"focus_{x}_{y}_{z}_{c}.png"
        img.save(os.path.join(od, fn))
        files.append(fn)
    L = y - y0
    if 0 <= L < sub_i.shape[0]:
        img, _ = render_plan(sc, sub_i, sub_d, L, head + f"  plan y={y}", f"floor y{y - 1}, contents y{y}-{y + 1}; "
                             f"grid labels are relative to the focus box (x0={x0 + sc.off[0]}, z0={z0 + sc.off[1]})")
        fn = f"focus_{x}_{y}_{z}_plan.png"
        img.save(os.path.join(od, fn))
        files.append(fn)
    return od, files


def render_dump(path_or_dump, out_root=None, views=("iso", "zoom", "under", "cut", "plan", "sec", "contact"),
                verbose=False):
    t0 = time.time()
    dump = read_jsd(path_or_dump) if isinstance(path_or_dump, str) else path_or_dump
    sc = Scene(dump)
    h = dump.header
    od = out_dir_for(dump, out_root)
    os.makedirs(od, exist_ok=True)
    SY, SZ, SX = sc.ids.shape
    sid = h.get("id", "?")
    name = h.get("name", "")
    g = h.get("grounds") or {}
    num = f"#{g['number']} " if g.get("number") else ""
    org = sc.origin
    base_sub = f"{h.get('set')}/{h.get('variant')} ctx={h.get('context')} size {SX}x{SY}x{SZ} origin {org}" + \
               (f" mode={(h.get('site') or {}).get('mode')}" if h.get("site") else "") + \
               ("; " + "; ".join(sc.notes) if sc.notes else "")
    head = f"{num}{sid}  {name}"
    written = []
    timing = {}
    info = {"id": sid, "variant": h.get("variant"), "set": h.get("set"), "size": [SX, SY, SZ], "origin": org,
            "textures": textures.SOURCE}

    natural = sc.mask is not None
    if natural:
        cids, cdata, cterr = ctx_volume(sc)
        iso_d = dump.iso()
        sids, sdata = iso_d.ids.astype(np.int64), iso_d.data.astype(np.int64)
        # trim structure-only volume to its bbox
        bb = iso_d.nonair_bbox()
        if bb:
            x0, y0, z0, x1, y1, z1 = bb
            sids, sdata = sids[y0:y1, z0:z1, x0:x1], sdata[y0:y1, z0:z1, x0:x1]
        pids, pdata, pterr = cids, cdata, cterr
        roof = sc.mask == 1
    else:
        pids, pdata, pterr = sc.ids, sc.data, None
        roof = None
        # trim empty space for iso (keep y from 0 so the ground plane stays)
    levels, cnt = floor_levels(pids, ground=sc.ground, struct_roof=roof)
    info["floorLevels"] = levels
    PSY, PSZ, PSX = pids.shape
    w = choose_w(PSX, PSY, PSZ)
    info["isoPxPerBlock"] = w

    def save(img, fn):
        p = os.path.join(od, fn)
        img.save(p, optimize=False, compress_level=6)
        written.append(fn)
        return img

    iso_imgs = {}
    t = time.time()
    if "iso" in views:
        for c in ("NE", "NW", "SE", "SW"):
            v = iso_view(c, w)
            if natural:
                vids, vdata, vterr = ctx_volume(sc, c)
                img = render_iso(vids, vdata, v, sc, f"{head}  -  ctx iso {c}",
                                 base_sub + " (terrain desaturated; buried: cut above roof, rock kept on far sides)",
                                 terrain=vterr, ground=False)
                iso_imgs[c] = save(img, f"ctx_iso_{c}.png")
                SSY, SSZ, SSX = sids.shape
                v2 = iso_view(c, choose_w(SSX, SSY, SSZ))
                save(render_iso(sids, sdata, v2, sc, f"{head}  -  structure-only iso {c}", base_sub, ground=False),
                     f"str_iso_{c}.png")
            else:
                iso_imgs[c] = save(render_iso(pids, pdata, v, sc, f"{head}  -  iso {c} (from the {c})", base_sub),
                                   f"iso_{c}.png")
    if "iso" in views and w < 16 and "zoom" in views:
        wz = min(24, ((2 * w) // 4) * 4)
        for c in ("NE", "SW"):
            v = iso_view(c, wz)
            if natural:
                vids, vdata, vterr = ctx_volume(sc, c)
                big = render_iso(vids, vdata, v, sc, "", "", terrain=vterr, ground=False, raw=True)
            else:
                big = render_iso(pids, pdata, v, sc, "", "", raw=True)
            tl, nx, ny = tiles_of(big)
            for r_, c_, im in tl:
                save(title_bar(im, f"{head}  -  zoom iso {c} tile r{r_}c{c_} of {ny}x{nx}",
                               base_sub + f"  ({wz}px/block; tiles overlap 80px, read left-to-right, top-to-bottom)"),
                     f"zoom_{c}_r{r_}c{c_}.png")
        info["zoomPxPerBlock"] = wz
    timing["iso"] = round(time.time() - t, 2)
    t = time.time()
    if "under" in views:
        if natural:
            SSY, SSZ, SSX = sids.shape
            v = iso_view("SE", choose_w(SSX, SSY, SSZ), below=True)
            save(render_iso(sids, sdata, v, sc, f"{head}  -  from below (SE), structure only", base_sub, ground=False),
                 "iso_under.png")
        else:
            v = iso_view("SE", w, below=True)
            save(render_iso(pids, pdata, v, sc, f"{head}  -  from below (SE)", base_sub), "iso_under.png")
    timing["under"] = round(time.time() - t, 2)
    t = time.time()
    if "cut" in views:
        v = iso_view("SE", w)
        for L in levels:
            cids2 = pids.copy()
            cdata2 = pdata.copy()
            cids2[L + 1:] = 0
            cdata2[L + 1:] = 0
            terr = None if pterr is None else (pterr & (cids2 != 0))
            save(render_iso(cids2, cdata2, v, sc, f"{head}  -  cutaway y<={L} (SE)",
                            base_sub + f"  cut above y={L} (world {org[1] + L}); dark tops = cut surfaces",
                            terrain=terr, cut_y=L, orig_ids=pids), f"cut_y{L:02d}.png")
    timing["cut"] = round(time.time() - t, 2)
    t = time.time()
    plans = []
    if "plan" in views:
        pc = {}
        for L in levels:
            img, counts = render_plan(sc, pids, pdata, L, f"{head}  -  plan y={L} (world {org[1] + L})",
                                      base_sub + f"  floor y{L - 1}, contents y{L}-{L + 1}")
            plans.append((L, save(img, f"plan_y{L:02d}.png")))
            pc[L] = counts
        info["planSymbols"] = pc
    timing["plan"] = round(time.time() - t, 2)
    t = time.time()
    if "sec" in views:
        for ax in ("x", "z"):
            save(render_section(sc, pids, pdata, ax, f"{head}  -  section {ax}", base_sub), f"sec_{ax}.png")
    timing["sec"] = round(time.time() - t, 2)
    t = time.time()
    if "contact" in views and iso_imgs:
        pick = plans
        if len(plans) > 4:
            idx = sorted({0, len(plans) - 1, len(plans) // 3, (2 * len(plans)) // 3})
            pick = [plans[i] for i in idx][:4]
        tiles = [(f"iso {c}", iso_imgs[c]) for c in ("NE", "NW", "SE", "SW") if c in iso_imgs] + \
                [(f"plan y={L}", im) for L, im in pick]
        cw, ch = 400, 330
        cols = 4
        rows = (len(tiles) + cols - 1) // cols
        sheet = Image.new("RGB", (cw * cols, ch * rows + 26), (30, 32, 38))
        d = ImageDraw.Draw(sheet)
        d.text((6, 5), f"{head}   {base_sub}   floors {levels}", fill=(240, 240, 240), font=F_SMALL)
        for i, (lab, im) in enumerate(tiles):
            im2 = im.copy()
            im2.thumbnail((cw - 6, ch - 20), Image.LANCZOS)
            x, y = (i % cols) * cw, 26 + (i // cols) * ch
            sheet.paste(im2, (x + (cw - im2.size[0]) // 2, y + 16))
            d.text((x + 4, y + 2), lab, fill=(220, 220, 120), font=F_SMALL)
        save(sheet, "contact.png")
    timing["contact"] = round(time.time() - t, 2)
    timing["total"] = round(time.time() - t0, 2)
    info["files"] = written
    info["timing"] = timing
    with open(os.path.join(od, "views.json"), "w", encoding="utf-8") as f:
        json.dump(info, f, indent=1)
    if verbose:
        print(f"{sid}: {len(written)} files in {timing['total']}s -> {od}")
    return od, info


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("dumps", nargs="+")
    ap.add_argument("--out", default=None, help="renders root (default SA/renders)")
    ap.add_argument("--views", default="iso,zoom,under,cut,plan,sec,contact")
    ap.add_argument("--focus", default="", help="x,y,z[,r] dump-relative: write focus_* close-ups only")
    a = ap.parse_args(argv)
    for p in a.dumps:
        if a.focus:
            f = [int(v) for v in a.focus.split(",")]
            od, files = render_focus(p, f[0], f[1], f[2], f[3] if len(f) > 3 else 10, a.out)
            print(od, files)
        else:
            render_dump(p, a.out, tuple(a.views.split(",")), verbose=True)


if __name__ == "__main__":
    main()
