"""Geometry + textures for Minecraft 1.12.2 block states (ids 0-255 with data values).

model(bid, data, ctx) -> Model(quads, flags)
  Quads are textured parallelograms in block-local coordinates (0..1 per axis):
      o (corner for uv (u0,v0)), e1 (towards u1), e2 (towards v1), normal n,
      tex (HxWx4 float32 RGBA 0..1), uv (u0,v0,u1,v1) in 0..16 texel units, flags.
  ctx carries neighbour-dependent information computed by render.py:
      bits 0-3   horizontal connections N,E,S,W (fences, walls, panes, bars)
      bits 4-9   same-type neighbour on face up,down,north,south,west,east (cull internal faces of glass/liquids)
      bits 10-17 extra data (door: other half's data; double plant upper: lower data)
      bit 18     liquid above (full-height liquid)
      bit 19     cut (top face darkened - cutaway surface)
      bit 20     terrain (desaturated, natural context)
      bits 21-22 dim level for sections (0 none .. 3 strongest)
"""
from __future__ import annotations

import math
from functools import lru_cache

import numpy as np

import textures

C_N, C_E, C_S, C_W = 1, 2, 4, 8
S_UP, S_DOWN, S_N, S_S, S_W, S_E = 16, 32, 64, 128, 256, 512
EXTRA_SHIFT = 10
LIQ_ABOVE = 1 << 18
CUT = 1 << 19
TERRAIN = 1 << 20
DIM_SHIFT = 21

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "big_oak"]
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple",
          "blue", "brown", "green", "red", "black"]
GRASS = (0.57, 0.74, 0.35)       # 0x91BD59 plains grass
FOLIAGE = (0.47, 0.67, 0.19)     # 0x77AB2F
SPRUCE = (0.38, 0.60, 0.38)      # 0x619961
BIRCH = (0.50, 0.65, 0.33)       # 0x80A755
LILY = (0.13, 0.50, 0.19)

# fallback colours (only used if a texture is missing)
_FALLBACK = (0.8, 0.0, 0.8, 1.0)


class Quad:
    __slots__ = ("o", "e1", "e2", "n", "tex", "uv", "two", "shade")

    def __init__(self, o, e1, e2, n, tex, uv, two=False, shade=None):
        self.o = np.asarray(o, float)
        self.e1 = np.asarray(e1, float)
        self.e2 = np.asarray(e2, float)
        self.n = np.asarray(n, float)
        self.tex = tex
        self.uv = uv
        self.two = two
        self.shade = shade


class Model:
    __slots__ = ("quads", "translucent", "emissive", "full", "name")

    def __init__(self, quads, translucent=False, emissive=0, full=False, name=""):
        self.quads = quads
        self.translucent = translucent
        self.emissive = emissive
        self.full = full
        self.name = name


# ------------------------------------------------------------------ textures
@lru_cache(maxsize=None)
def T(name, tint=None, alpha=None):
    a = textures.get(name)
    if a is None:
        a = np.zeros((16, 16, 4), np.uint8)
        a[..., 0], a[..., 2], a[..., 3] = 200, 200, 255
        a[::2, ::2, 1] = 200
    t = a.astype(np.float32) / 255.0
    if tint is not None:
        t = t.copy()
        t[..., :3] *= np.asarray(tint, np.float32)
    if alpha is not None:
        t = t.copy()
        t[..., 3] *= alpha
    t.setflags(write=False)
    return t


def solid_tex(rgb, a=1.0, noise=0.0, seed=0):
    t = np.zeros((16, 16, 4), np.float32)
    t[..., :3] = rgb
    t[..., 3] = a
    if noise:
        r = np.random.RandomState(seed).uniform(-noise, noise, (16, 16, 1)).astype(np.float32)
        t[..., :3] = np.clip(t[..., :3] + r, 0, 1)
    t.setflags(write=False)
    return t


@lru_cache(maxsize=None)
def grass_side():
    base = T("grass_side").copy()
    ov = T("grass_side_overlay", GRASS)
    a = ov[..., 3:4]
    base[..., :3] = base[..., :3] * (1 - a) + ov[..., :3] * a
    base.setflags(write=False)
    return base


def _resize(a, h=16, w=16):
    from PIL import Image
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA").resize((w, h), Image.NEAREST)
    return np.asarray(im, np.float32) / 255.0


@lru_cache(maxsize=None)
def chest_tex(kind="normal"):
    e = textures.entity("chest/" + kind)
    if e is None:
        col = {"normal": (0.55, 0.38, 0.16), "trapped": (0.55, 0.38, 0.16), "ender": (0.1, 0.15, 0.15)}[kind]
        top = solid_tex(col)
        return top, top, top
    e = e.astype(np.float32) / 255.0
    top = _resize(e[0:14, 14:28])
    side = _resize(np.concatenate([e[14:19, 14:28], e[33:43, 14:28]], axis=0))
    front = side.copy()
    latch = (0.75, 0.75, 0.72) if kind != "ender" else (0.55, 0.9, 0.85)
    front[5:9, 7:9, :3] = latch
    front[5:9, 7:9, 3] = 1.0
    front[5, 7:9, :3] = 0.25
    for t in (top, side, front):
        t.setflags(write=False)
    return top, side, front


@lru_cache(maxsize=None)
def redstone_tex(power):
    lvl = 0.35 + 0.65 * (power / 15.0)
    a = T("redstone_dust_dot").copy()
    for ln in ("redstone_dust_line0", "redstone_dust_line1"):
        b = T(ln)
        m = b[..., 3:4]
        a = a * (1 - m) + b * m
    a[..., 0] *= lvl
    a[..., 1] *= 0.08 * lvl
    a[..., 2] *= 0.05 * lvl
    a.setflags(write=False)
    return a


_ROT = {}


def rot90(tex, k=1):
    key = (id(tex), k % 4)
    hit = _ROT.get(key)
    if hit is not None and hit[0] is tex:
        return hit[1]
    r = np.ascontiguousarray(np.rot90(tex, k))
    r.setflags(write=False)
    _ROT[key] = (tex, r)
    return r


# ------------------------------------------------------------------ geometry helpers
FACES = ("up", "down", "north", "south", "west", "east")
NORMALS = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0),
           "east": (1, 0, 0)}


def box(x0, y0, z0, x1, y1, z1, tex, skip=(), full_uv=False, two=False):
    """Axis-aligned box in texel units (0..16). tex: array | dict face->array (missing faces skipped).
    full_uv: map the whole texture onto every face regardless of the box extents."""
    q = []
    x0f, y0f, z0f, x1f, y1f, z1f = x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16
    dx, dy, dz = x1f - x0f, y1f - y0f, z1f - z0f
    geo = {
        "up": ((x0f, y1f, z0f), (dx, 0, 0), (0, 0, dz), (x0, z0, x1, z1)),
        "down": ((x0f, y0f, z1f), (dx, 0, 0), (0, 0, -dz), (x0, 16 - z1, x1, 16 - z0)),
        "north": ((x1f, y1f, z0f), (-dx, 0, 0), (0, -dy, 0), (16 - x1, 16 - y1, 16 - x0, 16 - y0)),
        "south": ((x0f, y1f, z1f), (dx, 0, 0), (0, -dy, 0), (x0, 16 - y1, x1, 16 - y0)),
        "west": ((x0f, y1f, z0f), (0, 0, dz), (0, -dy, 0), (z0, 16 - y1, z1, 16 - y0)),
        "east": ((x1f, y1f, z1f), (0, 0, -dz), (0, -dy, 0), (16 - z1, 16 - y1, 16 - z0, 16 - y0)),
    }
    for f in FACES:
        if f in skip:
            continue
        t = tex.get(f) if isinstance(tex, dict) else tex
        if t is None:
            continue
        o, e1, e2, uv = geo[f]
        if full_uv:
            uv = (0, 0, 16, 16)
        q.append(Quad(o, e1, e2, NORMALS[f], t, uv, two))
    return q


def cube(tex, skip=()):
    return box(0, 0, 0, 16, 16, 16, tex, skip)


def tsb(top, side, bottom=None):
    bottom = top if bottom is None else bottom
    return {"up": top, "down": bottom, "north": side, "south": side, "west": side, "east": side}


def axis_tex(top, side, axis):
    """Pillar-like block (logs, hay, quartz pillar...). axis 0=y 1=x 2=z 3=all side."""
    if axis == 0:
        return tsb(top, side)
    if axis == 3:
        return tsb(side, side)
    rs = rot90(side)
    if axis == 1:   # along x: ends east/west
        return {"up": rs, "down": rs, "north": rs, "south": rs, "west": top, "east": top}
    return {"up": side, "down": side, "north": top, "south": top, "west": rs, "east": rs}


def cross(tex, scale=1.0, height=1.0, tint_two=True):
    s = (1 - scale) / 2
    a, b = s, 1 - s
    h = height
    return [Quad((a, h, a), (b - a, 0, b - a), (0, -h, 0), (0.7071, 0, -0.7071), tex, (0, 16 - 16 * h, 16, 16), True, 0.92),
            Quad((a, h, b), (b - a, 0, a - b), (0, -h, 0), (0.7071, 0, 0.7071), tex, (0, 16 - 16 * h, 16, 16), True, 0.85)]


def flat(tex, y=0.03, rot=0, two=True):
    t = rot90(tex, rot) if rot else tex
    return [Quad((0, y, 0), (1, 0, 0), (0, 0, 1), (0, 1, 0), t, (0, 0, 16, 16), two)]


def wall_quad(tex, facing, inset=0.04, two=True):
    """Quad against the wall on the side OPPOSITE to 'facing' (ladders, vines...). facing in N,S,W,E."""
    if facing == "N":    # attached to the block south of it -> plane at z = 1 - inset
        return [Quad((1, 1, 1 - inset), (-1, 0, 0), (0, -1, 0), (0, 0, -1), tex, (0, 0, 16, 16), two)]
    if facing == "S":
        return [Quad((0, 1, inset), (1, 0, 0), (0, -1, 0), (0, 0, 1), tex, (0, 0, 16, 16), two)]
    if facing == "W":
        return [Quad((1 - inset, 1, 0), (0, 0, 1), (0, -1, 0), (-1, 0, 0), tex, (0, 0, 16, 16), two)]
    return [Quad((inset, 1, 1), (0, 0, -1), (0, -1, 0), (1, 0, 0), tex, (0, 0, 16, 16), two)]


def ceiling_quad(tex, inset=0.04):
    return [Quad((0, 1 - inset, 0), (1, 0, 0), (0, 0, 1), (0, -1, 0), tex, (0, 0, 16, 16), True)]


# box inside the cell against a side: side in 'N','S','W','E','U','D'
def attached_box(side, w0, w1, h0, h1, depth, tex, full_uv=False):
    """Small box touching face 'side' of the cell: spans w0..w1 horizontally (along the wall), h0..h1
    vertically, 'depth' texels out from the wall."""
    if side == "N":   # on the north wall (z=0)
        return box(w0, h0, 0, w1, h1, depth, tex, full_uv=full_uv)
    if side == "S":
        return box(16 - w1, h0, 16 - depth, 16 - w0, h1, 16, tex, full_uv=full_uv)
    if side == "W":
        return box(0, h0, w0, depth, h1, w1, tex, full_uv=full_uv)
    if side == "E":
        return box(16 - depth, h0, 16 - w1, 16, h1, 16 - w0, tex, full_uv=full_uv)
    if side == "D":
        return box(w0, 0, h0, w1, depth, h1, tex, full_uv=full_uv)
    return box(w0, 16 - depth, h0, w1, 16, h1, tex, full_uv=full_uv)


OPP = {"N": "S", "S": "N", "W": "E", "E": "W"}
# facing codes used by various blocks
F6 = {0: "D", 1: "U", 2: "N", 3: "S", 4: "W", 5: "E"}          # dispenser/piston/observer/end rod facing
HORIZ4_SWNE = {0: "S", 1: "W", 2: "N", 3: "E"}                  # beds, pumpkins, gates, doors(+1)
DOOR_FACING = {0: "E", 1: "S", 2: "W", 3: "N"}
ROT_CW = {"N": "E", "E": "S", "S": "W", "W": "N"}
ROT_CCW = {v: k for k, v in ROT_CW.items()}


# ------------------------------------------------------------------ block families
def stairs(tex, data):
    facing = {0: "E", 1: "W", 2: "S", 3: "N"}[data & 3]
    upside = bool(data & 4)
    q = []
    if not upside:
        q += box(0, 0, 0, 16, 8, 16, tex)
        hy0, hy1 = 8, 16
    else:
        q += box(0, 8, 0, 16, 16, 16, tex)
        hy0, hy1 = 0, 8
    if facing == "E":
        q += box(8, hy0, 0, 16, hy1, 16, tex)
    elif facing == "W":
        q += box(0, hy0, 0, 8, hy1, 16, tex)
    elif facing == "S":
        q += box(0, hy0, 8, 16, hy1, 16, tex)
    else:
        q += box(0, hy0, 0, 16, hy1, 8, tex)
    return q


def slab(tex, top):
    return box(0, 8, 0, 16, 16, 16, tex) if top else box(0, 0, 0, 16, 8, 16, tex)


def fence(tex, conn, post=(6, 10), bars=((6, 9), (12, 15)), bw=(7, 9)):
    p0, p1 = post
    q = box(p0, 0, p0, p1, 16, p1, tex)
    b0, b1 = bw
    for (y0, y1) in bars:
        if conn & C_N:
            q += box(b0, y0, 0, b1, y1, p0, tex)
        if conn & C_S:
            q += box(b0, y0, p1, b1, y1, 16, tex)
        if conn & C_W:
            q += box(0, y0, b0, p0, y1, b1, tex)
        if conn & C_E:
            q += box(p1, y0, b0, 16, y1, b1, tex)
    return q


def wall(tex, conn):
    q = box(4, 0, 4, 12, 16, 12, tex)
    if conn & C_N:
        q += box(5, 0, 0, 11, 14, 4, tex)
    if conn & C_S:
        q += box(5, 0, 12, 11, 14, 16, tex)
    if conn & C_W:
        q += box(0, 0, 5, 4, 14, 11, tex)
    if conn & C_E:
        q += box(12, 0, 5, 16, 14, 11, tex)
    return q


def pane(tex, edge, conn):
    if conn == 0:
        conn = C_N | C_S | C_E | C_W       # 1.12: an unconnected pane renders as a full cross
    t = {"up": edge, "down": edge, "north": tex, "south": tex, "west": tex, "east": tex}
    q = box(7, 0, 7, 9, 16, 9, t)
    if conn & C_N:
        q += box(7, 0, 0, 9, 16, 7, t, skip=("south",))
    if conn & C_S:
        q += box(7, 0, 9, 9, 16, 16, t, skip=("north",))
    if conn & C_W:
        q += box(0, 0, 7, 7, 16, 9, t, skip=("east",))
    if conn & C_E:
        q += box(9, 0, 7, 16, 16, 9, t, skip=("west",))
    return q


def gate(tex, data):
    facing = HORIZ4_SWNE[data & 3]
    opened = bool(data & 4)
    q = []
    if facing in ("S", "N"):       # gate spans x (a gate facing south blocks the z direction)
        q += box(0, 5, 7, 2, 16, 9, tex) + box(14, 5, 7, 16, 16, 9, tex)
        if not opened:
            q += box(2, 6, 7, 14, 9, 9, tex) + box(2, 12, 7, 14, 15, 9, tex) + box(6, 9, 7, 10, 12, 9, tex)
        else:
            dz = (9, 16) if facing == "S" else (0, 7)
            q += box(0, 6, dz[0], 2, 15, dz[1], tex) + box(14, 6, dz[0], 16, 15, dz[1], tex)
    else:
        q += box(7, 5, 0, 9, 16, 2, tex) + box(7, 5, 14, 9, 16, 16, tex)
        if not opened:
            q += box(7, 6, 2, 9, 9, 14, tex) + box(7, 12, 2, 9, 15, 14, tex) + box(7, 9, 6, 9, 12, 10, tex)
        else:
            dx = (9, 16) if facing == "E" else (0, 7)
            q += box(dx[0], 6, 0, dx[1], 15, 2, tex) + box(dx[0], 6, 14, dx[1], 15, 16, tex)
    return q


def door(bid, data, ctx):
    other = (ctx >> EXTRA_SHIFT) & 0xFF
    upper = bool(data & 8)
    lower_d = other if upper else data
    upper_d = data if upper else other
    facing = DOOR_FACING[lower_d & 3]
    opened = bool(lower_d & 4)
    hinge_right = bool(upper_d & 1)
    if opened:
        facing = ROT_CCW[facing] if hinge_right else ROT_CW[facing]
    wood = {64: "wood", 71: "iron", 193: "spruce", 194: "birch", 195: "jungle", 196: "acacia", 197: "dark_oak"}[bid]
    tx = T(f"door_{wood}_{'upper' if upper else 'lower'}")
    # panel on the side opposite to facing (1.12 BlockDoor AABBs)
    if facing == "E":
        return box(0, 0, 0, 3, 16, 16, tx, full_uv=True)
    if facing == "S":
        return box(0, 0, 0, 16, 16, 3, tx, full_uv=True)
    if facing == "W":
        return box(13, 0, 0, 16, 16, 16, tx, full_uv=True)
    return box(0, 0, 13, 16, 16, 16, tx, full_uv=True)


def trapdoor(tex, data):
    facing = {0: "N", 1: "S", 2: "W", 3: "E"}[data & 3]
    opened = bool(data & 4)
    top = bool(data & 8)
    if not opened:
        return box(0, 13, 0, 16, 16, 16, tex, full_uv=True) if top else box(0, 0, 0, 16, 3, 16, tex, full_uv=True)
    if facing == "N":
        return box(0, 0, 13, 16, 16, 16, tex, full_uv=True)
    if facing == "S":
        return box(0, 0, 0, 16, 16, 3, tex, full_uv=True)
    if facing == "W":
        return box(13, 0, 0, 16, 16, 16, tex, full_uv=True)
    return box(0, 0, 0, 3, 16, 16, tex, full_uv=True)


def torch(tex, data):
    if data == 5 or data == 0:
        return box(7, 0, 7, 9, 10, 9, tex)
    side = {1: "W", 2: "E", 3: "N", 4: "S"}.get(data, "W")   # wall it is attached to
    if side == "W":
        return box(1, 3, 7, 3, 13, 9, tex)
    if side == "E":
        return box(13, 3, 7, 15, 13, 9, tex)
    if side == "N":
        return box(7, 3, 1, 9, 13, 3, tex)
    return box(7, 3, 13, 9, 13, 15, tex)


def rail(tex, turned, shape):
    if shape in (6, 7, 8, 9):
        rot = {6: 0, 7: 1, 8: 2, 9: 3}[shape]
        return flat(turned, rot=rot)
    if shape == 0:
        return flat(tex)
    if shape == 1:
        return flat(tex, rot=1)
    # ascending: 2 east, 3 west, 4 north, 5 south -> slope quad
    if shape == 2:
        return [Quad((0, 0.05, 0), (1, 1, 0), (0, 0, 1), (-0.7, 0.7, 0), rot90(tex), (0, 0, 16, 16), True)]
    if shape == 3:
        return [Quad((0, 1.05, 0), (1, -1, 0), (0, 0, 1), (0.7, 0.7, 0), rot90(tex), (0, 0, 16, 16), True)]
    if shape == 4:
        return [Quad((0, 1.05, 0), (1, 0, 0), (0, -1, 1), (0, 0.7, -0.7), tex, (0, 0, 16, 16), True)]
    return [Quad((0, 0.05, 0), (1, 0, 0), (0, 1, 1), (0, 0.7, 0.7), tex, (0, 0, 16, 16), True)]


def liquid(tex, data, ctx, still_alpha):
    same = ctx
    lvl = data & 7
    if (ctx & LIQ_ABOVE) or (data & 8):
        h = 16
    else:
        h = 16 * (1 - (lvl + 1) / 9.0) if lvl else 14.2
    skip = []
    for bit, f in ((S_UP, "up"), (S_DOWN, "down"), (S_N, "north"), (S_S, "south"), (S_W, "west"), (S_E, "east")):
        if same & bit:
            skip.append(f)
    if h < 16 and "up" in skip:
        skip.remove("up")
    return box(0, 0, 0, 16, h, 16, tex, skip=tuple(skip))


def same_skip(ctx):
    skip = []
    for bit, f in ((S_UP, "up"), (S_DOWN, "down"), (S_N, "north"), (S_S, "south"), (S_W, "west"), (S_E, "east")):
        if ctx & bit:
            skip.append(f)
    return tuple(skip)


def facing_cube(front, side, top, bottom, facing, back=None):
    back = side if back is None else back
    faces = {"up": top, "down": bottom, "north": side, "south": side, "west": side, "east": side}
    key = {"N": "north", "S": "south", "W": "west", "E": "east", "U": "up", "D": "down"}[facing]
    opp = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}[key]
    faces[key] = front
    faces[opp] = back
    return faces


# ------------------------------------------------------------------ main table
def _cube_model(tex, **kw):
    return Model(cube(tex), full=True, **kw)


SLAB_MAT = {0: ("stone_slab_top", "stone_slab_side", None), 1: ("sandstone_top", "sandstone_normal", "sandstone_bottom"),
            2: ("planks_oak", "planks_oak", None), 3: ("cobblestone", "cobblestone", None), 4: ("brick", "brick", None),
            5: ("stonebrick", "stonebrick", None), 6: ("nether_brick", "nether_brick", None),
            7: ("quartz_block_top", "quartz_block_side", "quartz_block_bottom")}
STAIR_MAT = {53: ("planks_oak",), 67: ("cobblestone",), 108: ("brick",), 109: ("stonebrick",),
             114: ("nether_brick",), 128: ("sandstone_top", "sandstone_normal", "sandstone_bottom"),
             134: ("planks_spruce",), 135: ("planks_birch",), 136: ("planks_jungle",),
             156: ("quartz_block_top", "quartz_block_side", "quartz_block_bottom"), 163: ("planks_acacia",),
             164: ("planks_big_oak",), 180: ("red_sandstone_top", "red_sandstone_normal", "red_sandstone_bottom"),
             203: ("purpur_block",)}
FENCE_MAT = {85: "planks_oak", 113: "nether_brick", 188: "planks_spruce", 189: "planks_birch", 190: "planks_jungle",
             191: "planks_big_oak", 192: "planks_acacia"}
GATE_MAT = {107: "planks_oak", 183: "planks_spruce", 184: "planks_birch", 185: "planks_jungle",
            186: "planks_big_oak", 187: "planks_acacia"}


def _mat(names):
    if len(names) == 1:
        return T(names[0])
    return tsb(T(names[0]), T(names[1]), T(names[2]) if names[2] else None)


def model(bid: int, data: int, ctx: int = 0) -> Model | None:
    m = _model(bid, data, ctx)
    return m


def _model(bid, data, ctx):  # noqa: C901  (a big table by nature)
    if bid == 0 or bid == 36 or bid == 217:
        return None
    if bid == 1:
        n = ["stone", "stone_granite", "stone_granite_smooth", "stone_diorite", "stone_diorite_smooth",
             "stone_andesite", "stone_andesite_smooth"][data if data < 7 else 0]
        return _cube_model(T(n))
    if bid == 2:
        return _cube_model(tsb(T("grass_top", GRASS), grass_side(), T("dirt")))
    if bid == 3:
        if data == 2:
            return _cube_model(tsb(T("dirt_podzol_top"), T("dirt_podzol_side"), T("dirt")))
        return _cube_model(T("coarse_dirt" if data == 1 else "dirt"))
    if bid == 4:
        return _cube_model(T("cobblestone"))
    if bid in (5, 125):
        return _cube_model(T("planks_" + WOODS[(data & 7) % 6]))
    if bid == 6:
        n = ["oak", "spruce", "birch", "jungle", "acacia", "roofed_oak"][(data & 7) % 6]
        return Model(cross(T("sapling_" + n), 0.9))
    if bid == 7:
        return _cube_model(T("bedrock"))
    if bid in (8, 9):
        return Model(liquid(T("water_still", None, 0.72), data, ctx, True), translucent=True)
    if bid in (10, 11):
        return Model(liquid(T("lava_still"), data, ctx, False), emissive=15)
    if bid == 12:
        return _cube_model(T("red_sand" if data == 1 else "sand"))
    if bid == 13:
        return _cube_model(T("gravel"))
    if bid in (14, 15, 16, 21, 56, 73, 129, 153):
        n = {14: "gold_ore", 15: "iron_ore", 16: "coal_ore", 21: "lapis_ore", 56: "diamond_ore", 73: "redstone_ore",
             129: "emerald_ore", 153: "quartz_ore"}[bid]
        return _cube_model(T(n))
    if bid == 74:
        return Model(cube(T("redstone_ore")), full=True, emissive=9)
    if bid in (17, 162):
        sp = ["oak", "spruce", "birch", "jungle"][data & 3] if bid == 17 else ["acacia", "big_oak", "acacia", "big_oak"][data & 3]
        return _cube_model(axis_tex(T(f"log_{sp}_top"), T(f"log_{sp}"), (data >> 2) & 3))
    if bid in (18, 161):
        if bid == 18:
            sp = ["oak", "spruce", "birch", "jungle"][data & 3]
            tint = {"oak": FOLIAGE, "spruce": SPRUCE, "birch": BIRCH, "jungle": (0.28, 0.71, 0.09)}[sp]
        else:
            sp = ["acacia", "big_oak", "acacia", "big_oak"][data & 3]
            tint = FOLIAGE
        return Model(cube(T("leaves_" + sp, tint)))
    if bid == 19:
        return _cube_model(T("sponge_wet" if data == 1 else "sponge"))
    if bid == 20:
        return Model(cube(T("glass"), skip=same_skip(ctx)), translucent=True)
    if bid == 22:
        return _cube_model(T("lapis_block"))
    if bid in (23, 158):
        f = F6.get(data & 7, "N")
        pre = "dispenser" if bid == 23 else "dropper"
        if f in ("U", "D"):
            fr = T(pre + "_front_vertical")
            faces = facing_cube(fr, T("furnace_top"), T("furnace_top"), T("furnace_top"), f)
        else:
            faces = facing_cube(T(pre + "_front_horizontal"), T("furnace_side"), T("furnace_top"), T("furnace_top"), f)
        return _cube_model(faces)
    if bid == 24:
        side = ["sandstone_normal", "sandstone_carved", "sandstone_smooth"][data if data < 3 else 0]
        return _cube_model(tsb(T("sandstone_top"), T(side), T("sandstone_bottom")))
    if bid == 25:
        return _cube_model(T("noteblock"))
    if bid == 26:
        head = bool(data & 8)
        top = T("wool_colored_white") if head else T("wool_colored_red")
        faces = tsb(top, T("wool_colored_red"), T("planks_oak"))
        return Model(box(0, 3, 0, 16, 9, 16, faces) + box(0, 0, 0, 3, 3, 3, T("planks_oak")) +
                     box(13, 0, 13, 16, 3, 16, T("planks_oak")) + box(13, 0, 0, 16, 3, 3, T("planks_oak")) +
                     box(0, 0, 13, 3, 3, 16, T("planks_oak")))
    if bid in (27, 28, 157):
        pre = {27: "rail_golden", 28: "rail_detector", 157: "rail_activator"}[bid]
        tex = T(pre + ("_powered" if data & 8 else ""))
        return Model(rail(tex, tex, data & 7))
    if bid == 66:
        return Model(rail(T("rail_normal"), T("rail_normal_turned"), data if data < 10 else 0))
    if bid in (29, 33):
        f = F6.get(data & 7, "U")
        ext = bool(data & 8)
        front = T("piston_inner") if ext else T("piston_top_sticky" if bid == 29 else "piston_top_normal")
        side = T("piston_side")
        if f in ("U", "D"):
            faces = facing_cube(front, side, side, side, f, back=T("piston_bottom"))
        else:
            rs = rot90(side)
            faces = facing_cube(front, rs, rs, rs, f, back=T("piston_bottom"))
        return _cube_model(faces)
    if bid == 34:
        f = F6.get(data & 7, "U")
        head = T("piston_top_sticky" if data & 8 else "piston_top_normal")
        side = T("piston_side")
        plate = {"U": (0, 12, 0, 16, 16, 16), "D": (0, 0, 0, 16, 4, 16), "N": (0, 0, 0, 16, 16, 4),
                 "S": (0, 0, 12, 16, 16, 16), "W": (0, 0, 0, 4, 16, 16), "E": (12, 0, 0, 16, 16, 16)}[f]
        arm = {"U": (6, 0, 6, 10, 12, 10), "D": (6, 4, 6, 10, 16, 10), "N": (6, 6, 4, 10, 10, 16),
               "S": (6, 6, 0, 10, 10, 12), "W": (4, 6, 6, 16, 10, 10), "E": (0, 6, 6, 12, 10, 10)}[f]
        return Model(box(*plate, tsb(head, side)) + box(*arm, side))
    if bid == 30:
        return Model(cross(T("web")))
    if bid == 31:
        if data == 0:
            return Model(cross(T("deadbush"), 0.9))
        return Model(cross(T("fern" if data == 2 else "tallgrass", GRASS), 0.9))
    if bid == 32:
        return Model(cross(T("deadbush"), 0.9))
    if bid == 35:
        return _cube_model(T("wool_colored_" + COLORS[data]))
    if bid == 37:
        return Model(cross(T("flower_dandelion"), 0.8))
    if bid == 38:
        n = ["flower_rose", "flower_blue_orchid", "flower_allium", "flower_houstonia", "flower_tulip_red",
             "flower_tulip_orange", "flower_tulip_white", "flower_tulip_pink", "flower_oxeye_daisy"][data if data < 9 else 0]
        return Model(cross(T(n), 0.8))
    if bid in (39, 40):
        return Model(cross(T("mushroom_brown" if bid == 39 else "mushroom_red"), 0.7, 0.6), emissive=1 if bid == 39 else 0)
    if bid == 41:
        return _cube_model(T("gold_block"))
    if bid == 42:
        return _cube_model(T("iron_block"))
    if bid == 43:
        if data == 8:
            return _cube_model(T("stone_slab_top"))
        if data == 9:
            return _cube_model(T("sandstone_top"))
        if data == 15:
            return _cube_model(T("quartz_block_top"))
        return _cube_model(_mat(SLAB_MAT[data & 7]))
    if bid == 44:
        return Model(slab(_mat(SLAB_MAT[data & 7]), data & 8))
    if bid == 45:
        return _cube_model(T("brick"))
    if bid == 46:
        return _cube_model(tsb(T("tnt_top"), T("tnt_side"), T("tnt_bottom")))
    if bid == 47:
        return _cube_model(tsb(T("planks_oak"), T("bookshelf")))
    if bid == 48:
        return _cube_model(T("cobblestone_mossy"))
    if bid == 49:
        return _cube_model(T("obsidian"))
    if bid == 50:
        return Model(torch(T("torch_on"), data), emissive=14)
    if bid == 51:
        return Model(cross(T("fire_layer_0")), emissive=15, translucent=False)
    if bid == 52:
        return Model(cube(T("mob_spawner")) + box(4, 4, 4, 12, 12, 12, solid_tex((0.25, 0.08, 0.08))), full=False)
    if bid in STAIR_MAT:
        return Model(stairs(_mat(STAIR_MAT[bid]), data))
    if bid in (54, 146, 130):
        kind = {54: "normal", 146: "trapped", 130: "ender"}[bid]
        top, side, front = chest_tex(kind)
        f = {2: "N", 3: "S", 4: "W", 5: "E"}.get(data, "S")
        faces = facing_cube(front, side, top, side, f)
        return Model(box(1, 0, 1, 15, 14, 15, faces, full_uv=True), emissive=7 if bid == 130 else 0)
    if bid == 55:
        return Model(flat(redstone_tex(data & 15), 0.02))
    if bid == 57:
        return _cube_model(T("diamond_block"))
    if bid == 58:
        return _cube_model({"up": T("crafting_table_top"), "down": T("planks_oak"), "north": T("crafting_table_front"),
                            "south": T("crafting_table_side"), "west": T("crafting_table_front"),
                            "east": T("crafting_table_side")})
    if bid == 59:
        return Model(cross(T(f"wheat_stage_{data & 7}"), 0.9))
    if bid == 60:
        return Model(box(0, 0, 0, 16, 15, 16, tsb(T("farmland_wet" if data else "farmland_dry"), T("dirt"), T("dirt"))))
    if bid in (61, 62):
        f = {2: "N", 3: "S", 4: "W", 5: "E"}.get(data, "S")
        fr = T("furnace_front_on" if bid == 62 else "furnace_front_off")
        return Model(cube(facing_cube(fr, T("furnace_side"), T("furnace_top"), T("furnace_top"), f)), full=True,
                     emissive=13 if bid == 62 else 0)
    if bid == 63:
        r = data & 15
        pl = T("planks_oak")
        q = box(7.3, 0, 7.3, 8.7, 9, 8.7, T("log_oak"))
        if r in (0, 1, 2, 14, 15, 6, 7, 8, 9, 10):
            q += box(0, 8, 7.3, 16, 16, 8.7, pl)
        else:
            q += box(7.3, 8, 0, 8.7, 16, 16, pl)
        return Model(q)
    if bid in (64, 71, 193, 194, 195, 196, 197):
        return Model(door(bid, data, ctx))
    if bid == 65:
        f = {2: "N", 3: "S", 4: "W", 5: "E"}.get(data, "N")
        return Model(wall_quad(T("ladder"), f, 0.05))
    if bid == 68:
        pl = T("planks_oak")
        f = {2: "N", 3: "S", 4: "W", 5: "E"}.get(data, "N")
        wallside = OPP[f]
        return Model(attached_box(wallside, 0, 16, 4.5, 12.5, 1.5, pl))
    if bid == 69:
        d = data & 7
        cob = T("cobblestone")
        stick = T("lever")
        side = {1: "W", 2: "E", 3: "N", 4: "S"}.get(d)
        if side:
            q = attached_box(side, 5, 11, 4, 12, 3, cob) + attached_box(side, 7, 9, 7, 9, 9, stick)
        elif d in (5, 6):
            q = box(5, 0, 4, 11, 3, 12, cob) + box(7, 3, 7, 9, 10, 9, stick)
        else:
            q = box(5, 13, 4, 11, 16, 12, cob) + box(7, 6, 7, 9, 13, 9, stick)
        return Model(q)
    if bid in (70, 72, 147, 148):
        t = T({70: "stone", 72: "planks_oak", 147: "gold_block", 148: "iron_block"}[bid])
        return Model(box(1, 0, 1, 15, 1, 15, t))
    if bid in (75, 76):
        return Model(torch(T("redstone_torch_on" if bid == 76 else "redstone_torch_off"), data),
                     emissive=7 if bid == 76 else 0)
    if bid in (77, 143):
        t = T("stone" if bid == 77 else "planks_oak")
        d = data & 7
        side = {0: "U", 1: "W", 2: "E", 3: "N", 4: "S", 5: "D"}.get(d, "D")
        if side in ("U", "D"):
            return Model(attached_box(side, 5, 11, 6, 10, 2, t))
        return Model(attached_box(side, 5, 11, 6, 10, 2, t))
    if bid == 78:
        h = ((data & 7) + 1) * 2
        return Model(box(0, 0, 0, 16, h, 16, T("snow")))
    if bid == 79:
        return Model(cube(T("ice", None, 0.75), skip=same_skip(ctx)), translucent=True)
    if bid == 80:
        return _cube_model(T("snow"))
    if bid == 81:
        return Model(box(1, 0, 1, 15, 16, 15, tsb(T("cactus_top"), T("cactus_side"), T("cactus_bottom"))))
    if bid == 82:
        return _cube_model(T("clay"))
    if bid == 83:
        return Model(cross(T("reeds", GRASS)))
    if bid == 84:
        return _cube_model(tsb(T("jukebox_top"), T("jukebox_side")))
    if bid in FENCE_MAT:
        return Model(fence(T(FENCE_MAT[bid]), ctx & 15))
    if bid in (86, 91):
        f = HORIZ4_SWNE[data & 3]
        fr = T("pumpkin_face_on" if bid == 91 else "pumpkin_face_off")
        return Model(cube(facing_cube(fr, T("pumpkin_side"), T("pumpkin_top"), T("pumpkin_top"), f)), full=True,
                     emissive=15 if bid == 91 else 0)
    if bid == 87:
        return _cube_model(T("netherrack"))
    if bid == 88:
        return _cube_model(T("soul_sand"))
    if bid == 89:
        return Model(cube(T("glowstone")), full=True, emissive=15)
    if bid == 90:
        t = T("portal", None, 0.75)
        q = box(0, 0, 6, 16, 16, 10, t) if (data & 3) != 2 else box(6, 0, 0, 10, 16, 16, t)
        return Model(q, translucent=True, emissive=11)
    if bid == 92:
        return Model(box(1, 0, 1, 15, 8, 15, tsb(T("cake_top"), T("cake_side"), T("cake_bottom"))))
    if bid in (93, 94, 149, 150):
        n = {93: "repeater_off", 94: "repeater_on", 149: "comparator_off", 150: "comparator_on"}[bid]
        return Model(box(0, 0, 0, 16, 2, 16, tsb(T(n), T("stone_slab_top"))), emissive=9 if bid in (94, 150) else 0)
    if bid == 95:
        return Model(cube(T("glass_" + COLORS[data], None, 1.0), skip=same_skip(ctx)), translucent=True)
    if bid in (96, 167):
        return Model(trapdoor(T("trapdoor" if bid == 96 else "iron_trapdoor"), data))
    if bid == 97:
        n = ["stone", "cobblestone", "stonebrick", "stonebrick_mossy", "stonebrick_cracked", "stonebrick_carved"][data if data < 6 else 0]
        return _cube_model(T(n))
    if bid == 98:
        n = ["stonebrick", "stonebrick_mossy", "stonebrick_cracked", "stonebrick_carved"][data & 3]
        return _cube_model(T(n))
    if bid in (99, 100):
        skin = T("mushroom_block_skin_brown" if bid == 99 else "mushroom_block_skin_red")
        inside, stem = T("mushroom_block_inside"), T("mushroom_block_skin_stem")
        if data == 0:
            return _cube_model(inside)
        if data in (10, 15):
            return _cube_model(tsb(inside if data == 10 else stem, stem))
        return _cube_model(tsb(skin, skin, inside if data != 14 else skin))
    if bid == 101:
        return Model(pane(T("iron_bars"), T("iron_bars"), ctx & 15))
    if bid == 102:
        return Model(pane(T("glass"), T("glass_pane_top"), ctx & 15), translucent=True)
    if bid == 160:
        c = COLORS[data]
        return Model(pane(T("glass_" + c), T("glass_pane_top_" + c), ctx & 15), translucent=True)
    if bid == 103:
        return _cube_model(tsb(T("melon_top"), T("melon_side")))
    if bid in (104, 105):
        age = data & 7
        tint = (0.3 + age * 0.08, 0.75 - age * 0.05, 0.1)
        return Model(cross(T("pumpkin_stem_disconnected" if bid == 104 else "melon_stem_disconnected", tint), 0.8,
                           (age + 1) * 2 / 16))
    if bid == 106:
        t = T("vine", FOLIAGE)
        q = []
        if data & 1:
            q += wall_quad(t, "N")        # vine on the south side of the cell
        if data & 2:
            q += wall_quad(t, "E")        # on the west side
        if data & 4:
            q += wall_quad(t, "S")        # on the north side
        if data & 8:
            q += wall_quad(t, "W")        # on the east side
        if data == 0:
            q += ceiling_quad(t)
        return Model(q)
    if bid in GATE_MAT:
        return Model(gate(T(GATE_MAT[bid]), data))
    if bid == 110:
        return _cube_model(tsb(T("mycelium_top"), T("mycelium_side"), T("dirt")))
    if bid == 111:
        return Model(flat(T("waterlily", LILY), 0.02))
    if bid == 112:
        return _cube_model(T("nether_brick"))
    if bid == 115:
        st = 0 if data == 0 else (1 if data < 3 else 2)
        return Model(cross(T(f"nether_wart_stage_{st}"), 0.9, 0.6))
    if bid == 116:
        return Model(box(0, 0, 0, 16, 12, 16, tsb(T("enchanting_table_top"), T("enchanting_table_side"),
                                                    T("enchanting_table_bottom"))))
    if bid == 117:
        return Model(box(7, 0, 7, 9, 14, 9, T("brewing_stand")) + box(1, 0, 1, 15, 2, 15, T("brewing_stand_base")),
                     emissive=1)
    if bid == 118:
        side = T("cauldron_side")
        q = box(0, 3, 0, 16, 16, 2, side) + box(0, 3, 14, 16, 16, 16, side) + box(0, 3, 2, 2, 16, 14, side) + \
            box(14, 3, 2, 16, 16, 14, side) + box(2, 3, 2, 14, 4, 14, T("cauldron_inner")) + \
            box(0, 0, 0, 4, 3, 4, side) + box(12, 0, 12, 16, 3, 16, side)
        if data & 3:
            q += box(2, 4, 2, 14, 6 + 3 * (data & 3), 14, T("water_still", None, 0.8))
        return Model(q)
    if bid == 119:
        return Model(box(0, 0, 0, 16, 12, 16, solid_tex((0.02, 0.02, 0.06), 1, 0.06, 7)), emissive=15)
    if bid == 120:
        q = box(0, 0, 0, 16, 13, 16, tsb(T("endframe_top"), T("endframe_side"), T("end_stone")))
        if data & 4:
            q += box(4, 13, 4, 12, 16, 12, T("endframe_eye"))
        return Model(q)
    if bid == 121:
        return _cube_model(T("end_stone"))
    if bid == 122:
        return Model(box(1, 0, 1, 15, 16, 15, T("dragon_egg")), emissive=1)
    if bid == 123:
        return _cube_model(T("redstone_lamp_off"))
    if bid == 124:
        return Model(cube(T("redstone_lamp_on")), full=True, emissive=15)
    if bid == 126:
        return Model(slab(T("planks_" + WOODS[(data & 7) % 6]), data & 8))
    if bid == 127:
        st = min((data >> 2) & 3, 2)
        side = {0: "S", 1: "W", 2: "N", 3: "E"}[data & 3]
        return Model(attached_box(side, 5, 11, 4, 11, 7, T(f"cocoa_stage_{st}"), full_uv=True))
    if bid == 131:
        side = {0: "S", 1: "W", 2: "N", 3: "E"}[data & 3]
        return Model(attached_box(side, 6, 10, 2, 10, 2, T("planks_oak")) + attached_box(side, 7, 9, 5, 7, 6, T("trip_wire_source")))
    if bid == 132:
        return Model(flat(T("trip_wire"), 0.1))
    if bid == 133:
        return _cube_model(T("emerald_block"))
    if bid in (137, 210, 211):
        pre = {137: "command_block", 210: "repeating_command_block", 211: "chain_command_block"}[bid]
        f = F6.get(data & 7, "U")
        return _cube_model(facing_cube(T(pre + "_front"), T(pre + "_side"), T(pre + "_side"), T(pre + "_side"), f,
                                       back=T(pre + "_back")))
    if bid == 138:
        q = box(2, 0, 2, 14, 3, 14, T("obsidian")) + box(3, 3, 3, 13, 14, 13, T("beacon")) + cube(T("glass"))
        return Model(q, translucent=False, emissive=15)
    if bid == 139:
        return Model(wall(T("cobblestone_mossy" if data == 1 else "cobblestone"), ctx & 15))
    if bid == 140:
        return Model(box(5, 0, 5, 11, 6, 11, T("flower_pot")))
    if bid in (141, 142):
        st = [0, 0, 1, 1, 2, 2, 2, 3][data & 7]
        return Model(cross(T(("carrots" if bid == 141 else "potatoes") + f"_stage_{st}"), 0.9))
    if bid == 144:
        bone = solid_tex((0.82, 0.8, 0.72), 1, 0.05, 3)
        d = data & 7
        if d in (0, 1):
            return Model(box(4, 0, 4, 12, 8, 12, bone))
        side = {2: "S", 3: "N", 4: "E", 5: "W"}.get(d, "S")
        return Model(attached_box(side, 4, 12, 4, 12, 8, bone))
    if bid == 145:
        dmg = min((data >> 2) & 3, 2)
        base = T("anvil_base")
        top = T(f"anvil_top_damaged_{dmg}")
        along_x = (data & 1) == 1
        if along_x:
            q = box(2, 0, 2, 14, 4, 14, base) + box(4, 4, 3, 12, 5, 13, base) + box(6, 5, 4, 10, 10, 12, base) + \
                box(0, 10, 3, 16, 16, 13, tsb(top, base))
        else:
            q = box(2, 0, 2, 14, 4, 14, base) + box(3, 4, 4, 13, 5, 12, base) + box(4, 5, 6, 12, 10, 10, base) + \
                box(3, 10, 0, 13, 16, 16, tsb(rot90(top), base))
        return Model(q)
    if bid in (151, 178):
        top = T("daylight_detector_inverted_top" if bid == 178 else "daylight_detector_top")
        return Model(box(0, 0, 0, 16, 6, 16, tsb(top, T("daylight_detector_side"))))
    if bid == 152:
        return _cube_model(T("redstone_block"))
    if bid == 154:
        out = T("hopper_outside")
        q = box(0, 10, 0, 16, 16, 16, {"up": T("hopper_top"), "down": out, "north": out, "south": out, "west": out,
                                          "east": out}) + box(4, 4, 4, 12, 10, 12, out) + box(6, 0, 6, 10, 4, 10, out)
        return Model(q)
    if bid == 155:
        if data == 1:
            return _cube_model(tsb(T("quartz_block_chiseled_top"), T("quartz_block_chiseled")))
        if data in (2, 3, 4):
            return _cube_model(axis_tex(T("quartz_block_lines_top"), T("quartz_block_lines"), {2: 0, 3: 1, 4: 2}[data]))
        return _cube_model(tsb(T("quartz_block_top"), T("quartz_block_side"), T("quartz_block_bottom")))
    if bid == 159:
        return _cube_model(T("hardened_clay_stained_" + COLORS[data]))
    if bid == 165:
        return Model(cube(T("slime", None, 0.8), skip=same_skip(ctx)), translucent=True)
    if bid == 166:
        return Model(cube(solid_tex((0.9, 0.2, 0.2), 0.18)), translucent=True)
    if bid == 168:
        n = ["prismarine_rough", "prismarine_bricks", "prismarine_dark"][data if data < 3 else 0]
        return _cube_model(T(n))
    if bid == 169:
        return Model(cube(T("sea_lantern")), full=True, emissive=15)
    if bid == 170:
        return _cube_model(axis_tex(T("hay_block_top"), T("hay_block_side"), (data >> 2) & 3))
    if bid == 171:
        return Model(box(0, 0, 0, 16, 1, 16, T("wool_colored_" + COLORS[data])))
    if bid == 172:
        return _cube_model(T("hardened_clay"))
    if bid == 173:
        return _cube_model(T("coal_block"))
    if bid == 174:
        return _cube_model(T("ice_packed"))
    if bid == 175:
        upper = bool(data & 8)
        kind = ((ctx >> EXTRA_SHIFT) & 7) if upper else (data & 7)
        n = ["sunflower", "syringa", "grass", "fern", "rose", "paeonia"][kind if kind < 6 else 2]
        tint = GRASS if n in ("grass", "fern") else None
        t = T(f"double_plant_{n}_{'top' if upper else 'bottom'}", tint)
        return Model(cross(t, 0.9))
    if bid in (176, 177):
        wool = T("wool_colored_white")
        if bid == 176:
            return Model(box(7.3, 0, 7.3, 8.7, 16, 8.7, T("planks_oak")) + box(1, 2, 7, 15, 16, 8, wool))
        f = {2: "N", 3: "S", 4: "W", 5: "E"}.get(data, "N")
        return Model(attached_box(OPP[f], 1, 15, 0, 14, 1, wool))
    if bid == 179:
        side = ["red_sandstone_normal", "red_sandstone_carved", "red_sandstone_smooth"][data if data < 3 else 0]
        return _cube_model(tsb(T("red_sandstone_top"), T(side), T("red_sandstone_bottom")))
    if bid == 181:
        if data & 8:
            return _cube_model(T("red_sandstone_top"))
        return _cube_model(tsb(T("red_sandstone_top"), T("red_sandstone_normal"), T("red_sandstone_bottom")))
    if bid == 182:
        return Model(slab(tsb(T("red_sandstone_top"), T("red_sandstone_normal"), T("red_sandstone_bottom")), data & 8))
    if bid == 198:
        f = F6.get(data & 7, "U")
        t = solid_tex((0.93, 0.9, 0.86), 1, 0.03, 5)
        if f in ("U", "D"):
            q = box(7, 0, 7, 9, 16, 9, t)
        elif f in ("N", "S"):
            q = box(7, 7, 0, 9, 9, 16, t)
        else:
            q = box(0, 7, 7, 16, 9, 9, t)
        return Model(q, emissive=14)
    if bid == 199:
        return Model(box(3, 3, 3, 13, 13, 13, T("chorus_plant")))
    if bid == 200:
        return _cube_model(T("chorus_flower_dead" if data == 5 else "chorus_flower"))
    if bid in (201, 204):
        return _cube_model(T("purpur_block"))
    if bid == 202:
        return _cube_model(axis_tex(T("purpur_pillar_top"), T("purpur_pillar"), (data >> 2) & 3))
    if bid == 205:
        return Model(slab(T("purpur_block"), data & 8))
    if bid == 206:
        return _cube_model(T("end_bricks"))
    if bid == 207:
        return Model(cross(T(f"beetroots_stage_{data & 3}"), 0.9))
    if bid == 208:
        return Model(box(0, 0, 0, 16, 15, 16, tsb(T("grass_path_top"), T("grass_path_side"), T("dirt"))))
    if bid == 209:
        return Model(cube(solid_tex((0.03, 0.02, 0.06), 1, 0.08, 11)), full=True, emissive=15)
    if bid == 212:
        return Model(cube(T(f"frosted_ice_{data & 3}", None, 0.8), skip=same_skip(ctx)), translucent=True)
    if bid == 213:
        return Model(cube(T("magma")), full=True, emissive=3)
    if bid == 214:
        return _cube_model(T("nether_wart_block"))
    if bid == 215:
        return _cube_model(T("red_nether_brick"))
    if bid == 216:
        return _cube_model(axis_tex(T("bone_block_top"), T("bone_block_side"), (data >> 2) & 3))
    if bid == 218:
        f = F6.get(data & 7, "U")
        side = T("observer_side")
        if f in ("U", "D"):
            faces = facing_cube(T("observer_front"), side, side, side, f, back=T("observer_back"))
        else:
            faces = facing_cube(T("observer_front"), side, T("observer_top"), T("observer_top"), f, back=T("observer_back"))
        return _cube_model(faces)
    if 219 <= bid <= 234:
        return _cube_model(T("shulker_top_" + COLORS[bid - 219]))
    if 235 <= bid <= 250:
        return _cube_model(rot90(T("glazed_terracotta_" + COLORS[bid - 235]), data & 3) if data & 3 else
                           T("glazed_terracotta_" + COLORS[bid - 235]))
    if bid == 251:
        return _cube_model(T("concrete_" + COLORS[data]))
    if bid == 252:
        return _cube_model(T("concrete_powder_" + COLORS[data]))
    if bid == 255:
        return _cube_model(T("structure_block"))
    # unknown id: magenta/black checker so it cannot be missed
    t = np.zeros((16, 16, 4), np.float32)
    t[..., 3] = 1
    t[::2, ::2, 0] = t[::2, ::2, 2] = 1
    t[1::2, 1::2, 0] = t[1::2, 1::2, 2] = 1
    t.setflags(write=False)
    return Model(cube(t), full=True, name=f"unknown{bid}")


# ------------------------------------------------------------------ classification helpers for render.py
FULL_CUBE = np.zeros(4096, dtype=bool)          # opaque full cubes: hide neighbours' faces
for _b in range(256):
    try:
        _m = _model(_b, 0, 0)
    except Exception:
        _m = None
    if _m is not None and _m.full and not _m.translucent:
        FULL_CUBE[_b] = True
for _b in (18, 161, 52, 20, 95, 79, 165, 166, 212):
    FULL_CUBE[_b] = False

CONNECT_FENCE = set(FENCE_MAT) | set(GATE_MAT)
CONNECT_PANE = {101, 102, 160, 20, 95}
CONNECT_WALL = {139} | set(GATE_MAT)
SAME_CULL = {8, 9, 10, 11, 20, 95, 79, 165, 212}
DOOR_IDS = {64, 71, 193, 194, 195, 196, 197}
NEEDS_CONN = set(FENCE_MAT) | {101, 102, 160, 139}


def texture_mean(bid, data):
    """Mean RGB (0..1) of a block's top-ish appearance (for colour-only fallbacks / legends)."""
    m = _model(bid, data, 0)
    if m is None or not m.quads:
        return (0, 0, 0)
    t = m.quads[0].tex
    a = t[..., 3:4]
    s = a.sum()
    if s <= 0:
        return tuple(float(x) for x in t[..., :3].mean(axis=(0, 1)))
    return tuple(float(x) for x in (t[..., :3] * a).sum(axis=(0, 1)) / s)
