#!/usr/bin/env python3
"""Converts the owner's GLM Nether structures (MCEdit .schematic files, 1.12 block ids) into JasprNether builds:
resources/glm/<KEY>.glb (gzip, big-endian binary read by GlmBuild.java) and resources/glm/builds.tsv (the index the
planner reads). The owner's folder is only read.

What the conversion does to each build (owner, 2026-09-29: "they may contain valuable blocks ... replaced with
something with a similar color palette"; the builds are raw, so the loot, spawners, mob packs and bosses are ours):
  * extracts structure geometry BEFORE trimming and fitting it to the Nether (y 5..121);
  * replaces valuable and technical blocks with look-alikes (gold block -> yellow concrete, beacon -> sea lantern,
    portal -> purple glass, command blocks -> terracotta/purpur/prismarine, hoppers -> cauldrons ...) and neutralises
    redstone; no block of the plugin's forbidden list survives;
  * removes surrounding soil, rock mass, water, trees and landscape; retains thin stone architecture and the
    walls/floors of enclosed rooms. Reviewed sculpture exceptions preserve authored dragon geometry;
  * marks the air below the ground outside the build as "keep the world" (VOID), so buried parts sit in rock;
  * records every tile block (chests, signs, banners, skulls, pots) for placement after the block flush;
  * picks loot chests (tiered), spawner spots, mob-pack (garrison) spots and, for the ten Nether Lords, the arena;
  * computes the cavern each build is placed in (ceiling height per column, a sealing ring, lake-able margin).

Run:  python convert_glm.py [KEY ...] [--src DIR] [--render DIR] [--report]
Needs numpy (no scipy)."""
import collections
import glob
import gzip
import io
import json
import math
import os
import re
import struct
import sys
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)
from stair_alignment import correct_exported_stairs
from source_materials import original_materials
SRC = os.environ.get('GLM_DIR', r'C:\Users\AM\Desktop\GLM Nether structures')
OUT = os.path.normpath(os.path.join(HERE, '..', 'resources', 'glm'))
VERSION = 1
# Optional provenance-aware correction supplied by the parent audit. Called as
# hook(key, path, blocks, metadata) -> corrected metadata, BEFORE extraction or
# resampling. This module does not infer provenance or alter source stair facing.
SOURCE_METADATA_HOOK = None

# ------------------------------------------------------------------------------------------------------------------
# Minimal NBT reader (gzip or raw).
def _nbt(f, t):
    if t == 1: return struct.unpack('>b', f.read(1))[0]
    if t == 2: return struct.unpack('>h', f.read(2))[0]
    if t == 3: return struct.unpack('>i', f.read(4))[0]
    if t == 4: return struct.unpack('>q', f.read(8))[0]
    if t == 5: return struct.unpack('>f', f.read(4))[0]
    if t == 6: return struct.unpack('>d', f.read(8))[0]
    if t == 7:
        n = struct.unpack('>i', f.read(4))[0]
        return f.read(n)
    if t == 8:
        n = struct.unpack('>H', f.read(2))[0]
        return f.read(n).decode('utf-8', 'replace')
    if t == 9:
        et = f.read(1)[0]
        n = struct.unpack('>i', f.read(4))[0]
        return [_nbt(f, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = f.read(1)
            if not tt or tt[0] == 0: return d
            name = _nbt(f, 8)
            d[name] = _nbt(f, tt[0])
    if t == 11:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == 12:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError('nbt tag %d' % t)

def load_nbt(path):
    raw = open(path, 'rb').read()
    try: data = gzip.decompress(raw)
    except OSError:
        try: data = zlib.decompress(raw)
        except zlib.error: data = raw
    f = io.BytesIO(data)
    t = f.read(1)[0]
    _nbt(f, 8)
    return _nbt(f, t)

def unpack_meta(data, n):
    """One metadata value per block: these files pack two blocks per byte, low nibble first (plain bytes otherwise)."""
    d = np.frombuffer(data, dtype=np.uint8)
    if len(d) >= n: return (d[:n] & 15).astype(np.int32)
    out = np.empty(n, dtype=np.int32)
    out[0::2] = d[:(n + 1) // 2] & 15
    out[1::2] = (d[:n // 2] >> 4) & 15
    return out

# ------------------------------------------------------------------------------------------------------------------
# The builds. tier: common (176-block grid), great (448), lord (640, one of the ten Nether Lords' strongholds).
# theme picks the mob roster and loot flavour; affinity the NetherEx region the build prefers ('' = any).
# Options: cut ('top'|'bottom'), cut_top/cut_bottom (layers, before scaling), scale, ground (local y of the floor),
# lift (air layers under a floating build), maxh, keep ({'sandstone','stone'}), buried (needs a way in from above),
# signs ('epitaph' fills blank signs with graveyard lines), skip (duplicate of another file).
B = {}
def build(key, title, tier, theme, affinity='', **kw):
    B[key] = dict(title=title, tier=tier, theme=theme, affinity=affinity, **kw)

H_, S_, T_, F_, A_ = 'HELL', 'RUTHLESS_SANDS', 'TORRID_WASTELAND', 'FUNGI_FOREST', 'ARCTIC_ABYSS'
build('N001', 'Bastion of Cinders', 'common', 'bastion', H_)
build('N002', 'The Crimson Ziggurat', 'great', 'temple')
build('N003', 'The Ruined Gatehub', 'great', 'hub')
build('N004', 'Arena of the Ashen Wither', 'lord', 'fortress', lord='ashen_wither')
build('N005', 'Magma Villa', 'common', 'volcanic', T_)
build('N009', 'Pigman Gold Pit', 'common', 'bastion', H_)
build('N010', 'Wither Killing Floor', 'common', 'fortress', S_)
build('N011', 'The Hollow Spires', 'great', 'void')
build('N014', 'The Grand Rotunda', 'great', 'hub', maxh=118)
build('N015', 'Blackstone Exchange', 'common', 'bastion', H_)
build('N016', 'Duskfang Keep', 'great', 'castle')
build('N018', 'Castle of the Dread Sorcerer', 'lord', 'arcane', lord='dread_sorcerer')
build('N020', 'Cindervent Works', 'great', 'volcanic', T_)
build('N023', 'Crossroads Bunker', 'common', 'hub')
build('N024', 'The Obsidian Drum', 'common', 'hub')
build('N055', 'The Four Gates', 'common', 'hub')
build('N057', 'Gilded Grinder Spire', 'common', 'bastion', H_, cut='bottom', maxh=48)
build('N060', 'Tidewrack Ring', 'common', 'hub')
build('N062', 'Iron Concourse', 'common', 'hub')
build('N063', 'The Blackstone Silo', 'common', 'hub')
build('N064', 'Ringed Citadel', 'common', 'hub')
build('N067', "Wayfarer's Rest", 'common', 'hub')
build('N069', 'Pigman Counting-House', 'common', 'bastion', H_)
build('N070', 'Pigman War-Barge', 'common', 'bastion', H_)
build('N072', 'Magma Rookery', 'common', 'volcanic', T_)
build('N073', 'Magma Cube Kiln', 'common', 'volcanic', T_)
build('N079', 'Skull Ossuary', 'common', 'crypt', S_)
build('N080', 'Blackrose Hatchery', 'common', 'crypt', S_)
build('N085', 'Basalt Foundry', 'common', 'volcanic', T_)
build('N086', 'Grimwood Hold', 'common', 'castle')
build('N087', 'Cursed Reliquary', 'common', 'temple')
build('N088', 'Engine of Malice', 'common', 'temple')
build('N090', 'Sanctum of the Cursed King', 'lord', 'temple', lord='cursed_king')
build('N092', 'The Brimstone Labyrinth', 'great', 'dungeon', buried=True)
build('N093', 'The Lesser Labyrinth', 'common', 'dungeon', buried=True)
build('N094', 'The Sulphur Sewers', 'great', 'dungeon', buried=True)
build('N096', 'Lavaflow Ruin', 'common', 'volcanic', T_)
build('N098', 'Domed Manse', 'common', 'castle')
build('N102', "Pyromancer's Spire", 'common', 'arcane')
build('N105', "Smuggler's Stash", 'common', 'vault', H_)
build('N107', 'Bone Mill', 'common', 'fortress', S_)
build('N108', 'The Great Bone Crater', 'great', 'fortress', S_, maxh=118)
build('N109', 'The Pale Box', 'common', 'void')
build('N113', 'Crimson Treehouse', 'common', 'ruin', F_)
build('N114', 'Fungus Grove Tower', 'common', 'farm', F_)
build('N116', 'Wart Garden', 'common', 'farm')
build('N124', 'Ancient Storehall', 'common', 'vault')
build('N129', 'Charred Lodge', 'common', 'ruin')
build('N132', 'Portal Keep', 'common', 'castle')
build('N137', 'Gold Crucible', 'common', 'bastion', H_)
build('N143', 'Brimstone Gaol', 'common', 'dungeon', signs='epitaph')
build('N144', "Deathwing's Lair", 'lord', 'volcanic', lord='deathwing', scale=2.42, keep={'stone'})
build('N145', 'Gallows Manor', 'common', 'castle')
build('N146', 'The Drifting Dungeon', 'great', 'dungeon', lift=10)
build('N151', 'Emberwall Castle', 'common', 'castle')
build('N152', 'Ashfall Fort', 'great', 'castle', signs='epitaph')
build('N153', 'Citadel of Nine Towers', 'great', 'castle')
build('N154', 'Monster Tower', 'common', 'arcane')
build('N157', 'Ossuary of the Bone Colossus', 'lord', 'crypt', lord='bone_colossus')
build('N159', 'Goldmoat Castle', 'great', 'castle')
build('N160', 'Moatstone Castle', 'common', 'castle')
build('N161', 'The Sealed Vault', 'common', 'void', buried=True)
build('N162', 'Spire of the Voidborn', 'lord', 'void', lord='voidborn', cut='bottom')
build('N163', 'Dune Fortress', 'common', 'temple', S_, keep={'sandstone'})
build('N164', 'The Undercroft', 'great', 'dungeon', buried=True)
build('N167', 'Sewertown', 'great', 'dungeon', ground=50, buried=True)
build('N169', 'The Buried Hamlet', 'common', 'dungeon', ground=49, buried=True)
build('N171', 'Pyramid of Ash', 'great', 'temple', S_, keep={'sandstone'})
build('N172', 'Frostpeak Castle', 'great', 'castle', A_)
build('N173', "The Wizard's Keep", 'great', 'arcane')
build('N174', 'Nether Castle', 'common', 'fortress', H_)
build('N176', 'Deep Storage', 'common', 'vault')
build('N177', 'Daggerfall Crypts', 'common', 'crypt', signs='epitaph')
build('N181', 'Castle of Spires', 'common', 'castle')
build('N188', 'Verdigris Tower', 'common', 'arcane')
build('N189', 'Cathedral of Cinders', 'common', 'cathedral')
build('N193', 'Blackspire Abbey', 'common', 'cathedral')
build('N194', 'The Grand Cathedral', 'great', 'cathedral', maxh=122)
build('N195', 'Hollowcrag Valley', 'great', 'castle')
build('N200', "The Pit Lord's Arena", 'lord', 'fortress', lord='pit_lord')
build('N201', "Heaven's Breach", 'common', 'temple')
build('N202', "Ignareth's Mountain", 'lord', 'volcanic', lord='ignareth', cut='bottom')
build('N203', 'The Haunted Mansion', 'common', 'crypt')
build('N204', 'Fortress of Ten Towers', 'common', 'castle')
build('N205', 'Crimson Valley', 'lord', 'volcanic', lord='crimson_tyrant')
build('N206', '', 'common', 'arcane', skip='duplicate of N018')
build('N207', 'Castle of the Blood Count', 'lord', 'castle', lord='blood_count', cut_top=20, scale=1.67)
build('N208', 'The Sunless Cathedral', 'great', 'cathedral', cut_top=50, scale=1.7)
build('N211', 'The Quartz Palace', 'great', 'castle')
build('N212', 'Cinderbrook Castle', 'common', 'castle')
build('N213', 'The Ember Pagoda', 'common', 'temple')
build('N214', 'Castle of Ten Banners', 'common', 'castle')
build('N215', 'Cliffside Keep', 'common', 'castle')
build('N216', 'The Great Labyrinth', 'great', 'dungeon', buried=True)
build('N218', 'Labyrinth Castle', 'great', 'dungeon')
build('N219', 'The Buried Pyramid', 'great', 'temple', buried=True)
build('N220', 'The Ashen Graveyard', 'common', 'crypt', signs='epitaph')
build('N221', 'The Evil Church', 'great', 'cathedral')
build('N222', 'The Hidden Mansion', 'great', 'crypt', buried=True)
build('N224', '', 'common', 'castle', skip='duplicate of N086')

THEMES = {'bastion', 'fortress', 'castle', 'crypt', 'arcane', 'volcanic', 'void', 'temple', 'dungeon', 'hub', 'farm',
          'vault', 'cathedral', 'ruin'}
LORDS = ['deathwing', 'ignareth', 'pit_lord', 'ashen_wither', 'cursed_king', 'dread_sorcerer', 'voidborn',
         'bone_colossus', 'crimson_tyrant', 'blood_count']

def default_config(key, fname):
    """A build dropped into the folder after this table was written: title from the file name, tier by size."""
    words = re.sub(r'^N\d+_', '', fname).replace('_', ' ').strip().title()
    theme = 'castle'
    for kw, th in (('castle', 'castle'), ('keep', 'castle'), ('fort', 'fortress'), ('temple', 'temple'), ('pyramid', 'temple'),
                   ('church', 'cathedral'), ('cathedral', 'cathedral'), ('crypt', 'crypt'), ('grave', 'crypt'), ('tower', 'arcane'),
                   ('dungeon', 'dungeon'), ('labyrinth', 'dungeon'), ('maze', 'dungeon'), ('farm', 'farm'), ('hub', 'hub'),
                   ('storage', 'vault'), ('house', 'ruin'), ('arena', 'fortress'), ('lava', 'volcanic'), ('magma', 'volcanic')):
        if kw in fname: theme = th; break
    return dict(title=words, tier='auto', theme=theme, affinity='')

# ------------------------------------------------------------------------------------------------------------------
# Block tables (1.12 numeric ids).
FORBIDDEN = {41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 120, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154, 27, 28, 147,
             148, 71, 167, 122, 209, 217}
# roles of a palette entry (GlmBuild.java has the same numbers)
LIT, AIR, VOID, SURFACE, SOIL, ROCK, SAND, CANOPY, TRUNK, PLANT, LIQUID, SNOW, PATH, TILE = range(14)
ROLE_NAMES = ['lit', 'air', 'void', 'surface', 'soil', 'rock', 'sand', 'canopy', 'trunk', 'plant', 'liquid', 'snow', 'path', 'tile']

PLANTS = {6, 31, 32, 37, 38, 39, 40, 81, 83, 175}
CROPS = {59, 141, 142, 207}
DROP = {55, 93, 94, 149, 150, 151, 178, 166, 217, 255, 104, 105, 127, 106, 111, 132}   # become air
TESR = {54, 146, 63, 68, 176, 177, 144, 140}     # tile blocks that need their tile entity (placed after the flush)
PASSABLE = {0, 6, 27, 28, 30, 31, 32, 37, 38, 39, 40, 50, 51, 55, 59, 63, 64, 65, 66, 68, 69, 70, 71, 72, 75, 76, 77, 78, 83,
            90, 93, 94, 104, 105, 106, 111, 115, 119, 131, 132, 141, 142, 143, 144, 147, 148, 149, 150, 157, 171, 175, 176,
            177, 193, 194, 195, 196, 197, 207, 209, 8, 9, 10, 11, 140}
FLAMMABLE = {5, 17, 162, 18, 161, 35, 47, 53, 134, 135, 136, 163, 164, 85, 188, 189, 190, 191, 192, 107, 183, 184, 185, 186,
             187, 125, 126, 171, 170, 46, 31, 32, 37, 38, 175, 106, 173}
DEFINITE = {2, 3, 12, 13, 60, 82, 110, 78, 80, 88}
ORES = {14, 15, 16, 21, 56, 73, 74, 129}

def is_raw_stone(b, m):
    return (b == 1) & ((m == 0) | (m == 1) | (m == 3) | (m == 5))

VALUABLE = {  # id -> (id, meta) look-alike (meta None = keep the original meta)
    41: (251, 4), 42: (251, 0), 57: (251, 3), 133: (251, 5), 22: (251, 11), 152: (251, 14), 138: (169, 0), 46: (159, 14),
    90: (95, 10), 119: (251, 15), 209: (251, 15), 120: (206, 0), 137: (159, 1), 210: (201, 0), 211: (168, 0), 116: (49, 0),
    145: (1, 6), 84: (5, 1), 154: (118, 0), 122: (49, 0), 218: (1, 5), 124: (89, 0), 19: (24, 2), 79: (174, 0), 212: (174, 0),
    199: (113, 0), 200: (214, 0), 60: (88, 0), 62: (61, None), 71: (194, None), 167: (96, None), 147: (72, 0), 148: (70, 0),
    75: (50, None), 76: (50, None), 130: (54, None),
}
EGG = {0: (1, 0), 1: (4, 0), 2: (98, 0), 3: (98, 1), 4: (98, 2), 5: (98, 3)}

# ------------------------------------------------------------------------------------------------------------------
# Rotation (a quarter turn clockwise seen from above: east -> south -> west -> north), for every oriented block.
F4 = {2: 5, 5: 3, 3: 4, 4: 2}                     # facing 2-5 (N S W E)
TORCH = {1: 3, 3: 2, 2: 4, 4: 1}                   # torch/button 1 east 2 west 3 south 4 north
STAIR = {0: 2, 2: 1, 1: 3, 3: 0}                   # stair 0 east 1 west 2 south 3 north
TRAP = {0: 3, 3: 1, 1: 2, 2: 0}                    # trapdoor 0 north 1 south 2 west 3 east
RAIL = {0: 1, 1: 0, 2: 5, 5: 3, 3: 4, 4: 2, 6: 7, 7: 8, 8: 9, 9: 6}
SHROOM = [0, 3, 6, 9, 2, 5, 8, 1, 4, 7, 10, 11, 12, 13, 14, 15]
STAIRS_IDS = {53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180, 203}

def rot_cw(i, m):
    if i in STAIRS_IDS: return i, (m & 4) | STAIR[m & 3]
    if i in (64, 71, 193, 194, 195, 196, 197): return (i, m) if m >= 8 else (i, (m & 4) | ((m + 1) & 3))
    if i in (107, 183, 184, 185, 186, 187, 86, 91) or 235 <= i <= 250: return i, (m & 12) | ((m + 1) & 3)
    if i in (54, 146, 61, 62, 65, 68, 177, 130): return (i, F4[m]) if m in F4 else (i, m)
    if i == 144: return (i, F4[m]) if m in F4 else (i, m)
    if i in (23, 158, 29, 33, 34, 198, 218): return (i, (m & 8) | F4[m & 7]) if (m & 7) in F4 else (i, m)
    if i in (50, 75, 76): return (i, TORCH[m]) if m in TORCH else (i, m)
    if i in (77, 143): return (i, (m & 8) | TORCH[m & 7]) if (m & 7) in TORCH else (i, m)
    if i == 69:
        k = m & 7
        k = TORCH.get(k, {5: 6, 6: 5, 0: 7, 7: 0}.get(k, k))
        return i, (m & 8) | k
    if i in (96, 167): return i, (m & 12) | TRAP[m & 3]
    if i in (63, 176): return i, (m + 4) & 15
    if i in (17, 162, 216, 170, 202):
        axis = m & 12
        axis = 8 if axis == 4 else 4 if axis == 8 else axis
        return i, (m & 3) | axis
    if i == 155: return i, 4 if m == 3 else 3 if m == 4 else m
    if i in (99, 100): return i, SHROOM[m]
    if i in (66,): return i, RAIL.get(m, m)
    if i in (27, 28, 157): return i, (m & 8) | RAIL.get(m & 7, m & 7)
    if i == 131: return i, (m & 12) | ((m + 1) & 3)
    if i == 106: return i, ((m << 1) | (m >> 3)) & 15
    return i, m

def rotations(i, m):
    out = [(i, m)]
    for _ in range(3): out.append(rot_cw(*out[-1]))
    return out

# ------------------------------------------------------------------------------------------------------------------
# numpy helpers (no scipy)
def shift(a, dy, dz, dx, fill=0):
    out = np.full_like(a, fill)
    H, L, W = a.shape
    ys, ye, zs, ze, xs, xe = max(0, dy), min(H, H + dy), max(0, dz), min(L, L + dz), max(0, dx), min(W, W + dx)
    if ys < ye and zs < ze and xs < xe:
        out[ys:ye, zs:ze, xs:xe] = a[ys - dy:ye - dy, zs - dz:ze - dz, xs - dx:xe - dx]
    return out

def shift2(a, dz, dx, fill=0):
    out = np.full_like(a, fill)
    L, W = a.shape
    zs, ze, xs, xe = max(0, dz), min(L, L + dz), max(0, dx), min(W, W + dx)
    if zs < ze and xs < xe: out[zs:ze, xs:xe] = a[zs - dz:ze - dz, xs - dx:xe - dx]
    return out

def dilate2(mask, r):
    out = mask.copy()
    for dz in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dz * dz <= r * r and (dx or dz): out |= shift2(mask, dz, dx, False)
    return out

def dist2(mask, rmax):
    """Chamfer-ish distance (in blocks, capped at rmax+1) from each column to the nearest True column."""
    d = np.where(mask, 0.0, rmax + 1.0)
    for dz in range(-rmax, rmax + 1):
        for dx in range(-rmax, rmax + 1):
            r = math.hypot(dx, dz)
            if r > rmax or (dx == 0 and dz == 0): continue
            s = shift2(mask, dz, dx, False)
            d = np.where(s & (d > r), r, d)
    return d

def flood(seed, allowed):
    """6-connected flood of `seed` through `allowed` (iterative dilation)."""
    cur = seed & allowed
    n = int(cur.sum())
    while True:
        nxt = cur.copy()
        nxt[1:] |= cur[:-1]; nxt[:-1] |= cur[1:]
        nxt[:, 1:] |= cur[:, :-1]; nxt[:, :-1] |= cur[:, 1:]
        nxt[:, :, 1:] |= cur[:, :, :-1]; nxt[:, :, :-1] |= cur[:, :, 1:]
        nxt &= allowed
        m = int(nxt.sum())
        if m == n: return nxt
        cur, n = nxt, m

def components(mask):
    """6-connected components of a sparse boolean volume: list of index arrays (y, z, x)."""
    pts = np.argwhere(mask)
    index = {tuple(p): k for k, p in enumerate(pts)}
    seen = np.zeros(len(pts), dtype=bool)
    out = []
    for k in range(len(pts)):
        if seen[k]: continue
        stack, comp = [k], []
        seen[k] = True
        while stack:
            j = stack.pop()
            comp.append(j)
            y, z, x = pts[j]
            for nb in ((y + 1, z, x), (y - 1, z, x), (y, z + 1, x), (y, z - 1, x), (y, z, x + 1), (y, z, x - 1)):
                q = index.get(nb)
                if q is not None and not seen[q]:
                    seen[q] = True
                    stack.append(q)
        out.append(pts[comp])
    return out

# ------------------------------------------------------------------------------------------------------------------
# Signs
EPITAPHS = [
    ['Here lies', 'a pigman who', 'trusted a', 'stranger.'],
    ['R.I.P.', 'He brought', 'a wooden sword', 'to the Nether.'],
    ['Here rests', 'a miner who', 'dug straight', 'down.'],
    ['She saw the', 'ghast. It saw', 'her first.', ''],
    ['Beloved', 'explorer.', 'Forgot the', 'fire potion.'],
    ['Here lies', 'Ser Cinder,', 'burned by his', 'own torch.'],
    ['Gone to face', 'the Ashen', 'Wither.', 'Never back.'],
    ['He said the', 'lava looked', 'shallow.', ''],
    ['Rest in', 'ashes, friend.', 'The Nether', 'keeps its own.'],
    ['Here lies', 'a thief who', 'opened the', 'trapped chest.'],
    ['Slain by', 'a Nether Lord.', 'Avenge me,', 'traveller.'],
    ['Do not', 'dig here.', '', 'Really.'],
]
SIGN_BLOCK = ['chuj', 'kurwa', 'kys', 'zabij', 'homosek', 'chimek', 'tutel', 'tutl', 'xardus', 'uwu', 'azrael', 'alabama',
              'nava', 'drop in', 'lever', 'button', 'stand on', 'your items', 'your gear', 'gear up', 'teleport', 'remove sign',
              'i was here', 'secret door', 'shoot the', ' mode', 'batery', 'bloques', 'dispenser', 'reset exit', 'kto budowal',
              'nazwa', 'grubej', 'gapisz', 'hmhn', 'unwanted limbs', 'pull-aparts', 'corpse', 'torture', 'tickle', 'shame',
              'skunks', 'clothing', 'toilets', 'drowned rat', 'jane doe', 'john doe', 'thumbscrew', 'x-ray', 'bachelor',
              'swallowing', 'luna empire', 'united kingdom', 'united states', 'guillotine', 'sorry bird']

def sign_plain(s):
    try: j = json.loads(s)
    except Exception: return s or ''
    def walk(o):
        if isinstance(o, str): return o
        if isinstance(o, list): return ''.join(walk(x) for x in o)
        if isinstance(o, dict): return str(o.get('text', '')) + ''.join(walk(x) for x in o.get('extra', []))
        return ''
    return walk(j)

def sign_lines(tile):
    lines = [sign_plain(tile.get('Text%d' % i, '')) for i in range(1, 5)]
    lines = [re.sub(r'[^\x20-\x7e]', '', l).strip()[:15] for l in lines]
    joined = ' '.join(lines).lower()
    alnum = re.sub(r'[^a-z0-9]', '', joined)
    if len(alnum) < 3 or re.fullmatch(r'(.)\1*', alnum) or any(w in ' ' + joined for w in SIGN_BLOCK):
        return None
    return lines

# ------------------------------------------------------------------------------------------------------------------
def load_build(path):
    r = load_nbt(path)
    W, H, L = r['Width'], r['Height'], r['Length']
    b = np.frombuffer(r['Blocks'], dtype=np.uint8).reshape(H, L, W).astype(np.int32)
    add = r.get('AddBlocks')
    if add:
        a = unpack_meta(add, W * H * L).reshape(H, L, W)
        b = b | (a << 8)
    m = unpack_meta(r['Data'], W * H * L).reshape(H, L, W)
    tiles = {}
    for t in r.get('TileEntities', []):
        try: tiles[(int(t['y']), int(t['z']), int(t['x']))] = t
        except (KeyError, ValueError): pass
    return b, m, tiles

def crop(b, m, tiles, y0, y1, z0, z1, x0, x1):
    b = b[y0:y1, z0:z1, x0:x1].copy(); m = m[y0:y1, z0:z1, x0:x1].copy()
    t2 = {}
    for (y, z, x), t in tiles.items():
        if y0 <= y < y1 and z0 <= z < z1 and x0 <= x < x1: t2[(y - y0, z - z0, x - x0)] = t
    return b, m, t2

def trim(b, m, tiles):
    solid = b != 0
    ys = np.nonzero(solid.any(axis=(1, 2)))[0]; zs = np.nonzero(solid.any(axis=(0, 2)))[0]; xs = np.nonzero(solid.any(axis=(0, 1)))[0]
    return crop(b, m, tiles, ys[0], ys[-1] + 1, zs[0], zs[-1] + 1, xs[0], xs[-1] + 1)

def downscale(b, m, tiles, f):
    """Nearest-sample reduction by factor f: 2x2x2 samples per target cell, the commonest solid sample wins when at
    least a quarter of them are solid. Tile blocks survive where their block lands on the same kind of block."""
    H, L, W = b.shape
    nh, nl, nw = max(1, int(H / f)), max(1, int(L / f)), max(1, int(W / f))
    comb = (b << 4) | m
    S = []
    for oy in (0.25, 0.75):
        ys = np.minimum(((np.arange(nh) + oy) * f).astype(int), H - 1)
        for oz in (0.25, 0.75):
            zs = np.minimum(((np.arange(nl) + oz) * f).astype(int), L - 1)
            for ox in (0.25, 0.75):
                xs = np.minimum(((np.arange(nw) + ox) * f).astype(int), W - 1)
                S.append(comb[np.ix_(ys, zs, xs)])
    S = np.stack(S)
    solid = S != 0
    best = np.zeros((nh, nl, nw), dtype=np.int32); bestc = np.zeros((nh, nl, nw), dtype=np.int32)
    for i in range(8):
        c = ((S == S[i][None]) & solid).sum(0) * solid[i]
        upd = c > bestc
        best[upd] = S[i][upd]; bestc[upd] = c[upd]
    out = np.where(solid.sum(0) >= 2, best, 0)
    t2 = {}
    for (y, z, x), t in tiles.items():
        ty, tz, tx = int(y / f), int(z / f), int(x / f)
        if ty < nh and tz < nl and tx < nw and (out[ty, tz, tx] >> 4) == b[y, z, x]: t2[(ty, tz, tx)] = t
    return out >> 4, out & 15, t2

def detect_ground(b, m, keep):
    H = b.shape[0]
    definite = np.isin(b, list(DEFINITE))
    natural = definite | is_raw_stone(b, m) | np.isin(b, [7, 121, 87] + list(ORES) + [153])
    if 'sandstone' not in keep: natural |= (b == 24) & (m == 0)
    ys = np.arange(H).reshape(H, 1, 1)
    top_def = np.where(definite, ys, -1).max(axis=0)
    cols = top_def >= 0
    footprint = (b != 0).any(axis=0)
    if cols.sum() > 0.2 * max(1, footprint.sum()):
        return int(np.median(top_def[cols])), natural, definite
    cover = [(natural[y] & footprint).sum() / max(1, footprint.sum()) for y in range(H)]
    good = [y for y in range(H) if cover[y] >= 0.6]
    return (max(good) if good else 0), natural, definite


def grow6(mask, radius=1):
    """Bounded six-neighbour dilation, without wraparound or a scipy dependency."""
    out = mask.copy()
    for _ in range(radius):
        nxt = out.copy()
        nxt[1:] |= out[:-1]; nxt[:-1] |= out[1:]
        nxt[:, 1:] |= out[:, :-1]; nxt[:, :-1] |= out[:, 1:]
        nxt[:, :, 1:] |= out[:, :, :-1]; nxt[:, :, :-1] |= out[:, :, 1:]
        out = nxt
    return out


def enclosed_air(b):
    """Air with a floor, a roof and opposing walls on both horizontal axes.

    This directional test includes door-connected rooms; an exterior flood alone
    would misclassify every room behind an open doorway as landscape. It is used
    only to retain a two-block boundary, never to retain the solid mountain core.
    """
    open_cell = (b == 0) | np.isin(b, list(PASSABLE - {8, 9, 10, 11, 51}))
    solid = ~open_cell
    room = open_cell.copy()
    for axis in range(3):
        ahead = np.logical_or.accumulate(solid, axis=axis)
        behind = np.flip(np.logical_or.accumulate(np.flip(solid, axis=axis), axis=axis), axis=axis)
        room &= ahead & behind
    return room


def interior_rooms(b):
    """Exclude the broad exterior air, while closing small doors/tunnel mouths.

    Directional enclosure by itself mistakes valleys between separate hills (or
    air beneath a dragon) for rooms. Flood the exterior's two-block-eroded air
    first; narrow doorways close, preserving rooms behind them. Then restore the
    exterior's two-block boundary. Only the residual enclosed air is a room.
    """
    air = (b == 0) | np.isin(b, list(PASSABLE - {8, 9, 10, 11, 51}))
    wide = air.copy()
    for _ in range(2):
        narrow = wide.copy()
        for step in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
            narrow &= shift(wide, *step, True)
        wide = narrow
    seed = np.zeros_like(air)
    seed[0] = True; seed[-1] = True
    seed[:,0] = True; seed[:,-1] = True; seed[:,:,0] = True; seed[:,:,-1] = True
    exterior = grow6(flood(seed, wide), 2) & air
    rooms = enclosed_air(b) & ~exterior
    return grow6(rooms) & air & ~exterior


def thin_material(mask, radius=3):
    """Material bounded on opposite sides within radius blocks (wall/slab geometry).

    A hill's exposed surface has air on only one side and does not qualify.
    A thin wall qualifies on its normal axis even when very long or tall.
    """
    out = np.zeros_like(mask)
    for axis in range(3):
        left = np.zeros_like(mask); right = np.zeros_like(mask)
        for distance in range(1, radius + 1):
            step = [0, 0, 0]; step[axis] = distance
            left |= shift(~mask, *step, True)
            step[axis] = -distance
            right |= shift(~mask, *step, True)
        out |= left & right
    return out & mask


def prune_reviewed_components(key, keep, b, markers):
    """Reviewed scene-specific debris cleanup, never a universal largest-only rule.

    N205's detached dead-tree islands have real wooden stairs/fences, so a
    material-count witness is insufficient. Keep the fortress/roads and actual
    bone arches or masonry watchtowers. The small N172/N173 stone flecks are
    terrain; source tile anchors and N172's detached glass crown are retained.
    """
    parts = components(keep)
    if not parts:
        return keep, 0
    primary = max(parts, key=len)
    out = np.zeros_like(keep)
    out[tuple(primary.T)] = True
    for pts in parts:
        ids = b[tuple(pts.T)]
        retain = bool(markers[tuple(pts.T)].any())
        if key == 'N205':
            bone = int((ids == 216).sum())
            masonry = int(np.isin(ids, [98,109,139]).sum())
            retain |= len(pts) >= 96 and (bone >= 24 or masonry >= 12 and int((ids == 99).sum()) >= 24)
        else:
            retain |= int(np.isin(ids, [20,98,102,155,156,206]).sum()) >= 12
        if retain:
            out[tuple(pts.T)] = True
    return out, int((keep & ~out).sum())


def extract_structure(key, b, m, tiles, source_masks=None):
    """Remove imported terrain, keeping only justified structure boundaries.

    Natural stone is not categorically deleted: room skins, thin connected
    architecture and its one-block backing survive literally. Dirt/grass never
    survive. N144's reviewed stone dragon and authored round pedestal are a
    sculpture; N202's floating dragon is separated from its continuous mountain.
    N205's cobble/grey mixed palette is terrain even though those ids can be
    architectural in other builds.
    """
    source_shape = list(b.shape)
    source_nonair = int((b != 0).sum())
    source_tiles = len(tiles)
    source_stairs = int(np.isin(b, list(STAIRS_IDS)).sum())
    soil = np.isin(b, [2, 3, 12, 13, 82, 110, 78, 80, 208])
    natural = is_raw_stone(b, m) | np.isin(b, [7, 87, 121, 153] + list(ORES))
    # Sandstone is ordinarily architecture. Raw sand/dirt beneath it are not.
    # The enormous mixed rock spires in N205 need an explicit palette rule.
    if key == 'N205':
        natural |= np.isin(b, [4, 112, 173, 242, 249, 250, 252])
        natural |= (b == 1) & (m == 6)
        natural |= (b == 35) & np.isin(m, [7, 8])
        natural |= (b == 251) & np.isin(m, [7, 8, 15])
        natural |= (b == 159) & np.isin(m, [6, 8, 9, 14])
        natural |= (b == 43) & (m == 7)
    if key in ('N216', 'N218'):
        natural |= (b == 24) & (m == 0)  # bulk sandstone fill around the buried maze
    if key == 'N202':
        natural |= b == 173  # legacy export maps scattered ancient debris to coal blocks
        natural |= (b == 1) & (m == 6)  # polished dark rock within the mountain blend
    landscape = natural | soil
    leaves = np.isin(b, [18, 161])
    if key == 'N202': leaves |= b == 214  # exported crimson-tree canopy, not temple walls
    logs = np.isin(b, [17, 162])
    if source_masks is not None:
        # Exact original-state provenance catches modern foliage exported as
        # wool. Logs are candidates, not a categorical deletion of timber walls.
        for name, target in [('soil',soil),('leaves',leaves),('logs',logs)]:
            if name in source_masks:
                mask = np.asarray(source_masks[name],dtype=bool)
                if mask.shape != b.shape:
                    raise ValueError('%s: source %s mask dimensions differ' % (key,name))
                target |= mask
        landscape = natural | soil
    custom_crown = np.zeros_like(leaves)
    if key in ('N018','N202'):
        # These reviewed crowns mix wool, stained glass and panes in the
        # ORIGINAL palette, not leaves.
        # Group diagonal/tiny crown fragments within one voxel, then require a
        # substantial actual crown touching a log/stem. Small interior
        # upholstery remains. N202's dragon is never in this lower-height rule.
        crown = np.isin(b,[35,95,160]) & (m == (6 if key == 'N018' else 14))
        if key == 'N202':
            crown &= np.arange(b.shape[0]).reshape(-1,1,1) < 100
        near_log = grow6(logs,3)
        for pts in components(grow6(crown)):
            where = tuple(pts.T)
            actual = pts[crown[where]]
            horizontal_crown = len(actual) and np.ptp(actual[:,1]) >= 3 and np.ptp(actual[:,2]) >= 3
            tree_shape = near_log[where].any() or key == 'N202' and horizontal_crown
            if len(actual) >= (32 if key == 'N018' else 24) and tree_shape:
                custom_crown[where] |= crown[where]
        leaves |= custom_crown
    decorations = np.isin(b, list(PLANTS) + [106, 111])
    water = np.isin(b, [8, 9])
    lava = np.isin(b, [10, 11])
    # Trees may form a single forest component; the old <=64-log heuristic
    # incorrectly retained that whole forest. Keep logs used by actual framing.
    tree = flood(grow6(leaves) & logs, logs) if leaves.any() else np.zeros_like(logs)
    anchor = (b != 0) & ~landscape & ~leaves & ~logs & ~decorations & ~water & ~lava & ~np.isin(b, list(DROP))
    structural_log = logs & (~tree | grow6(anchor))
    if key == 'N113': structural_log = logs  # reviewed treehouse support frame
    if key == 'N202': structural_log = logs & ~tree  # reviewed exterior garden trunks, not temple/dragon timber
    structural_log &= ~landscape
    anchor |= structural_log
    room_geometry = np.where(leaves | decorations | (tree & ~structural_log) | soil | water, 0, b)
    rooms = enclosed_air(room_geometry) if key == 'N144' else interior_rooms(room_geometry)
    # Removing dirt/trees for visibility must not INVENT rooms in their former
    # solid cells. Only air/passable cells present in the original can be a room.
    source_open = (b == 0) | np.isin(b, list(PASSABLE - {8, 9, 10, 11, 51}))
    rooms &= source_open & ~leaves & ~decorations & ~soil & ~water
    if key == 'N202':
        # Its mineral/unknown-block voids inside the mountain are not rooms.
        # The actual furnished temple starts at raw y50. Require a temple roof
        # within 20 cells above air below the disconnected dragon.
        ys = np.arange(b.shape[0]).reshape(-1,1,1)
        roof_block = anchor & (ys < 100) & np.isin(b, [5,43,44,45,98,125,126,155,156,206])
        roof = np.zeros_like(rooms)
        run = np.zeros(b.shape[1:], dtype=np.int32)
        for y in range(b.shape[0]-1,-1,-1):
            roof[y] = run > 0
            run = np.where(roof_block[y],20,np.maximum(run-1,0))
        rooms &= roof | (ys >= 100)
    if key in ('N195', 'N202', 'N205'):
        furnished = np.isin(b, list(TESR) + [52,130,5,47,53,58,61,64,65,109,117,118,126,134,135,136,155,156,193,194,195,196,197])
        rooms = flood(grow6(furnished, 3) & rooms, rooms)
    room_boundary = natural & grow6(rooms, 2)
    near_arch = natural & grow6(anchor)
    thin = thin_material(natural)
    connected_thin = flood((room_boundary | near_arch) & thin, thin) if thin.any() else thin
    if key in ('N202', 'N205', 'N216', 'N218') or soil.sum() > 1000:
        # Thin ground ledges can join a wall. Their connection alone is not
        # sufficient evidence to import a landscape skin across the whole map.
        connected_thin &= grow6(anchor, 3) | room_boundary
    retained_natural = room_boundary | near_arch | connected_thin
    exception = np.zeros_like(natural)
    reasons = []
    if key == 'N132':
        reasons.append('N132: parent-reviewed timber/clay platform retained as authored architecture; exact-source foliage/soil still excluded')
    if key == 'N004':
        reasons.append('N004: compiled cutaways verify hollow basement/arena beneath the house; raw stone walls/floors retained')
    if key == 'N113':
        reasons.append('N113: treehouse timber support frame is integral architecture; foliage removed, frame retained')
    if key == 'N144':
        exception = natural & ~np.isin(b, [7] + list(ORES) + [153])
        reasons.append('N144: reviewed authored stone dragon, columns and round pedestal, not a landscape mountain')
    if key == 'N202':
        ys = np.arange(b.shape[0]).reshape(-1, 1, 1)
        # Exact raw layer slices show the mountain ends at y92 and the
        # disconnected dragon begins above y112 (mostly red nether brick).
        # Preserve its small stone/rack accents, never the lower mountain rim.
        exception = natural & (ys >= 100)
        reasons.append('N202: raw slices verify mountain below y93, disconnected dragon above y112; natural accents kept only above y100')
    if key == 'N218':
        reasons.append('N218: cutaways show solid sandstone fill below/around authored maze; retain only room walls/floors and thin connected architecture, as N216')
    retained_natural |= exception
    if key == 'N202':
        retained_natural = room_boundary | exception
    # Netherrack under intentional fire is an explicit architectural fuel cell.
    fuel = (b == 87) & shift(b == 51, -1, 0, 0, False)
    retained_natural |= fuel
    # Lava beside structure/within its rooms is an authored hazard, not a lake
    # copied with the mountain. N144's dragon veins are part of the sculpture.
    keep_lava = lava & (grow6(anchor | retained_natural) & ~grow6(soil))
    if key == 'N144': keep_lava = lava
    semantic_region = None
    semantic_seeds = None
    if key == 'N205':
        # Architectural witnesses, not the square obsidian landscape boundary,
        # determine the arena's footprint. Isolated rock flecks and decorative
        # mountain stairs do not become standalone fortifications.
        core = np.isin(b, list(TESR) + list(STAIRS_IDS) + [5,23,25,26,47,52,58,61,64,65,85,96,98,101,102,107,117,118,125,126,139,155,193,194,195,196,197,216]) & ~natural
        weight = core.sum(axis=0)
        clusters = components(dilate2(weight > 0, 3)[None])
        accepted = np.zeros(b.shape[1:],dtype=bool)
        for comp in clusters:
            cz,cx = comp[:,1],comp[:,2]
            if int(weight[cz,cx].sum()) >= 24 and int((weight[cz,cx] > 0).sum()) >= 8:
                accepted[cz,cx] = True
        semantic_region = dilate2(accepted, 4)
        semantic_seeds = core & semantic_region[None]
        # Only lava immediately framed by an actual accepted structure is an
        # authored hazard. Never retain channels along removed hill contours.
        keep_lava = lava & grow6(semantic_seeds) & semantic_region[None]
        reasons.append('N205: accepted furnished/masonry/bone-arch clusters (>=24 witness blocks and >=8 columns); four-block boundary; no landscape lava channels')
    keep = anchor | retained_natural | keep_lava | fuel
    # Always preserve chest/spawner/sign/etc anchors; their palette substitutions
    # and standard tile/loot handling still happen after extraction.
    markers = np.isin(b, list(TESR) + [52, 130])
    keep |= markers
    keep &= ~soil & ~leaves & ~decorations & ~water
    if key == 'N202':
        # Raw y0..47 is exclusively the mountain/caves (first chest and wood
        # furnishing are at y50); a two-cell floor boundary remains at y48.
        keep &= (np.arange(b.shape[0]).reshape(-1,1,1) >= 48)
    if key == 'N205':
        # Reject isolated flecks of the mixed rock palette left suspended where
        # its spires were removed. Retain components attached to arena masonry,
        # furnishings, bone arches or the deliberate obsidian hazard boundary.
        keep &= semantic_region[None] | markers
        keep = flood(keep & (semantic_seeds | markers), keep)
    pruned_components = 0
    if key in ('N205', 'N172', 'N173'):
        keep, pruned_components = prune_reviewed_components(key,keep,b,markers)
        if key == 'N205':
            reasons.append('N205: detached dead-tree/ground-chip components removed; primary connected fortress/roads plus reviewed bone arches and masonry watchtowers retained')
        else:
            reasons.append('%s: disconnected terrain rock flecks removed; source tile anchors and deliberate glass/masonry ornaments preserved' % key)
    removed = (b != 0) & ~keep
    justified = room_boundary | near_arch | connected_thin | exception | fuel
    unexplained = int((keep & natural & ~justified).sum())
    if unexplained:
        raise ValueError('%s: unexplained terrain retained: %d' % (key,unexplained))
    core = natural.copy()
    for _ in range(3):
        nxt = core.copy()
        for step in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
            nxt &= shift(core,*step,False)
        core = nxt
    unjustified_core = int((keep & core & ~(room_boundary | near_arch | exception | fuel)).sum())
    if unjustified_core:
        raise ValueError('%s: external terrain core retained: %d' % (key,unjustified_core))
    info = dict(source_dimensions=[source_shape[2], source_shape[0], source_shape[1]],
                source_nonair=source_nonair, source_tiles=source_tiles,
                source_dirt_grass=int(np.isin(b, [2, 3]).sum()),
                removed_nonair=int(removed.sum()), removed_natural=int((removed & natural).sum()),
                natural_candidates=int(natural.sum()), retained_natural=int((keep & natural).sum()),
                room_air=int(rooms.sum()), room_boundary_natural=int((keep & room_boundary).sum()),
                architecture_backing_natural=int((keep & near_arch).sum()),
                connected_thin_natural=int((keep & connected_thin).sum()),
                sculpture_natural=int(exception.sum()), fire_fuel=int(fuel.sum()),
                removed_trees=int((removed & (leaves | tree)).sum()), removed_water=int((removed & water).sum()),
                removed_custom_tree_crowns=int((removed & custom_crown).sum()),
                removed_lava=int((removed & lava).sum()), retained_lava=int(keep_lava.sum()),
                source_chests=int(np.isin(b, [54, 146, 130]).sum()), source_spawners=int((b == 52).sum()),
                source_stairs=source_stairs, retained_stairs=int((keep & np.isin(b,list(STAIRS_IDS))).sum()),
                unexplained_natural=unexplained,unjustified_mass_core=unjustified_core,
                source_mass_core=int(core.sum()),retained_mass_core=int((core & keep).sum()),
                disconnected_debris_removed=pruned_components,
                exceptions=reasons)
    if source_masks:
        info['original_material_counts'] = {name:int(mask.sum()) for name,mask in source_masks.items()}
        info['retained_original_material_counts'] = {name:int((keep & mask).sum()) for name,mask in source_masks.items()}
    if semantic_region is not None:
        info['semantic_region_columns'] = int(semantic_region.sum())
        info['semantic_witness_blocks'] = int(semantic_seeds.sum())
    b = b.copy(); m = m.copy()
    b[removed] = 0; m[removed] = 0
    tiles = {p:t for p,t in tiles.items() if all(0 <= v < s for v,s in zip(p,b.shape)) and b[p] != 0}
    info['retained_tiles'] = len(tiles)
    info['retained_chests'] = int(np.isin(b, [54, 146, 130]).sum())
    info['retained_spawners'] = int((b == 52).sum())
    info['retained_nonair'] = int((b != 0).sum())
    info['retained_dirt_grass'] = int(np.isin(b, [2, 3]).sum())
    # Ground is inferred from authored floors, not the removed grass hill.
    occupied = b != 0
    bounds = []
    for axis in range(3):
        indexes = np.nonzero(occupied.any(axis=tuple(i for i in range(3) if i != axis)))[0]
        if not len(indexes): raise ValueError('%s has no extracted structure' % key)
        bounds.append((int(indexes[0]), int(indexes[-1]) + 1))
    info['crop_bounds_yzx'] = bounds
    b,m,tiles = crop(b,m,tiles,*[v for pair in bounds for v in pair])
    return b,m,tiles,info

# ------------------------------------------------------------------------------------------------------------------
class Converted:
    pass

def convert(key, path, cfg, log, metadata_hook=None):
    b, m, tiles = load_build(path)
    m, stair_info = correct_exported_stairs(path, b, m)
    source_masks = original_materials(path,b.shape)
    hook = metadata_hook or SOURCE_METADATA_HOOK
    if hook is not None:
        m = np.asarray(hook(key, path, b, m), dtype=np.int32)
        if m.shape != b.shape or np.any((m < 0) | (m > 15)):
            raise ValueError('%s: source metadata hook returned invalid metadata' % key)
    b, m, tiles, crop_info = extract_structure(key, b, m, tiles, source_masks=source_masks)
    crop_info['metadata_hook'] = getattr(hook, '__name__', None)
    crop_info['stair_metadata'] = stair_info
    crop_info['material_provenance'] = dict(verified_source=bool(source_masks),
                                           counts={name:int(mask.sum()) for name,mask in source_masks.items()})
    notes = ['extracted %d landscape cells' % crop_info['removed_nonair']] + crop_info['exceptions']
    notes.append('stairs: verified=%s repaired=%d/%d' % (stair_info['verified_export'], stair_info['repaired'],
                                                       stair_info.get('stair_count', 0)))
    maxh = cfg.get('maxh', 117) - cfg.get('lift', 0)
    scale = max(float(cfg.get('scale', 1)), b.shape[0] / maxh)
    crop_info['fit_scale'] = scale
    crop_info['extracted_dimensions'] = [b.shape[2], b.shape[0], b.shape[1]]
    if scale > 1:
        b, m, tiles = downscale(b, m, tiles, scale)
        notes.append('scaled complete structure 1/%.4f (no height cuts)' % scale)
        b, m, tiles = trim(b, m, tiles)
    H, L, W = b.shape
    crop_info['fitted_dimensions'] = [W, H, L]
    crop_info['fitted_source_chests'] = int(np.isin(b, [54,146,130]).sum())
    crop_info['fitted_source_spawners'] = int((b == 52).sum())
    crop_info['fitted_tile_entities'] = len(tiles)
    crop_info['fitted_stairs'] = int(np.isin(b,list(STAIRS_IDS)).sum())
    # Enclosed rooms are placed under the native cavern floor, retaining their
    # literal stone boundary; removed external mass is supplied by native world.
    room = enclosed_air(b)
    room_levels = np.nonzero(room.any(axis=(1, 2)))[0]
    ground = min(H - 1, int(room_levels[-1]) + 1) if cfg.get('buried') and len(room_levels) else 0
    crop_info['recalculated_ground'] = ground
    if cfg.get('lift'): ground = -cfg['lift']
    ys = np.arange(H).reshape(H, 1, 1)

    role = np.zeros(b.shape, dtype=np.uint8)
    val = (b << 4) | m

    # ---- literal substitutions (valuable / technical / fragile) -----------------------------------------------------
    for src, (dst, dm) in VALUABLE.items():
        sel = b == src
        if sel.any():
            val[sel] = (dst << 4) | (m[sel] if dm is None else dm)
    sel = b == 97
    if sel.any():
        for k, (dst, dm) in EGG.items(): val[sel & (m == k)] = (dst << 4) | dm
    sel = (b >= 219) & (b <= 234)
    val[sel] = (251 << 4) | (b[sel] - 219)
    sel = np.isin(b, [27, 28, 157])
    val[sel] = (66 << 4) | (m[sel] & 7)
    sel = b == 69
    val[sel] = (69 << 4) | (m[sel] & 7)          # levers off
    sel = np.isin(b, list(CROPS))
    val[sel] = (115 << 4) | np.minimum(3, m[sel] * 3 // 7)
    sel = b == 7                                   # bedrock in a build -> black concrete (in the ground it is rock)
    val[sel] = (251 << 4) | 15
    beds = b == 26
    if beds.any():
        for (y, z, x) in zip(*np.nonzero(beds)):
            t = tiles.get((y, z, x)) or {}
            col = t.get('color', 14)
            val[y, z, x] = (171 << 4) | (int(col) & 15)
    # fire only burns on netherrack
    fire = b == 51
    if fire.any():
        below = shift(b, 1, 0, 0, 0)
        role[fire & (below != 87)] = AIR
    role[np.isin(b, list(DROP))] = AIR
    role[b == 0] = AIR
    # plants: only on the ground; tall plants' upper halves go
    plants = np.isin(b, list(PLANTS))
    upper = (b == 175) & (m >= 8)
    role[plants] = PLANT
    role[upper] = AIR

    # Extraction has already removed terrain. Retained stone/netherrack is
    # literal architecture, not SURFACE/SOIL/ROCK/SAND or vegetation placeholders.
    # Ores needed in room skins keep geometry with ordinary stone look-alikes.
    val[np.isin(b, list(ORES) + [153])] = 1 << 4
    lava = np.isin(b, [10, 11])
    val[lava] = (11 << 4) | m[lava]                # stationary: keeps its level, never ticked
    # plant role only on ground, otherwise air
    below_role = shift(role, 1, 0, 0, 0)
    role[plants & ~np.isin(below_role, [SURFACE, SOIL, SAND])] = AIR
    role[upper] = AIR

    # ---- tiles -------------------------------------------------------------------------------------------------------
    tesr = np.isin(b, list(TESR))
    role[tesr] = TILE
    tile_list = []
    epitaph = 0
    for (y, z, x) in zip(*np.nonzero(tesr)):
        bid = int(b[y, z, x]); t = tiles.get((int(y), int(z), int(x))) or {}
        e = dict(y=int(y), z=int(z), x=int(x), block=bid)
        if bid in (54, 146):
            e['kind'] = 'chest'; e['trapped'] = bid == 146
        elif bid in (63, 68):
            e['kind'] = 'sign'
            lines = sign_lines(t)
            if cfg.get('signs') == 'epitaph':
                lines = EPITAPHS[epitaph % len(EPITAPHS)]; epitaph += 1
            e['lines'] = lines or ['', '', '', '']
        elif bid in (176, 177):
            e['kind'] = 'banner'
            e['base'] = int(t.get('Base', 0)) & 15
            e['patterns'] = [(int(p.get('Color', 0)) & 15, str(p.get('Pattern', ''))[:6]) for p in t.get('Patterns', [])][:6]
        elif bid == 144:
            e['kind'] = 'skull'
            st = int(t.get('SkullType', 0))
            e['skull'] = {1: 0, 3: 2, 5: 0}.get(st, st if st in (0, 2, 4) else 0)
            e['rot'] = int(t.get('Rot', 0)) & 15
        elif bid == 140:
            e['kind'] = 'pot'
            e['item'] = str(t.get('Item', 'minecraft:air'))
        tile_list.append(e)
    # ender chests became chests: they are vault candidates
    for (y, z, x) in zip(*np.nonzero(b == 130)):
        role[y, z, x] = TILE
        tile_list.append(dict(y=int(y), z=int(z), x=int(x), block=54, kind='chest', trapped=False, ender=True))
    # spawners of the original builds are replaced by ours (markers)
    old_spawners = list(zip(*np.nonzero(b == 52)))
    role[b == 52] = AIR

    # ---- VOID: below the ground, the air outside the build keeps the world; above the cavern, everything -------------
    air = role == AIR
    below = ys <= ground if ground >= 0 else np.zeros(b.shape, dtype=bool)
    exterior = np.zeros(b.shape, dtype=bool)
    if ground >= 0:
        seed = np.zeros(b.shape, dtype=bool)
        seed[:, 0, :] = True; seed[:, -1, :] = True; seed[:, :, 0] = True; seed[:, :, -1] = True; seed[0] = True
        # Room air must be carved even if a doorway connects it to the exterior.
        exterior = flood(seed & air & below & ~room, air & below & ~room)
        role[exterior] = VOID

    # ---- a way in for buried builds ------------------------------------------------------------------------------------
    shaft = None
    if cfg.get('buried') and ground >= 0:
        shaft = dig_entrance(b, role, val, ground, notes)

    # ---- the cavern ------------------------------------------------------------------------------------------------------
    built = (role != AIR) & (role != VOID)
    footprint = built.any(axis=0)
    topmap = np.where(built, ys, -1).max(axis=0)
    headroom = 3 + (16 if cfg.get('lord') in FLYING else 0)      # a flying Lord needs air above its lair
    need = np.where(footprint, np.maximum(topmap - ground, 0) + headroom, 0).astype(np.float64)
    maxneed = float(need.max()) if footprint.any() else 4.0
    M = int(min(26, max(10, math.ceil(maxneed / 4.5) + 6)))
    slope = max(1.6, (maxneed - 6) / max(1, M - 2))
    Lc, Wc = L + 2 * M, W + 2 * M
    fp = np.zeros((Lc, Wc), dtype=bool); fp[M:M + L, M:M + W] = footprint
    nd = np.zeros((Lc, Wc)); nd[M:M + L, M:M + W] = need
    # clearance around tall parts, then a slope down to the cavern wall
    height = nd.copy()
    R = M
    for dz in range(-R, R + 1):
        for dx in range(-R, R + 1):
            r = math.hypot(dx, dz)
            if r > R or (dx == 0 and dz == 0): continue
            drop = 0.0 if r <= 3 else (r - 3) * slope
            height = np.maximum(height, shift2(nd, dz, dx, 0.0) - drop)
    dfp = dist2(fp, M)
    dome = 6.0 + (10.0 if cfg.get('buried') else 0.0)
    height = np.maximum(height, np.where(dfp <= M, dome * np.sqrt(np.clip(1 - (dfp / (M + 0.5)) ** 2, 0, 1)), 0))
    cave = (dfp <= M - 1) & (height >= 3)
    height = np.where(cave, np.clip(np.round(height), 3, 200), 0).astype(np.int32)
    ring = dilate2(cave, 3) & ~cave
    ringh = height.copy()
    for dz in range(-3, 4):
        for dx in range(-3, 4):
            ringh = np.maximum(ringh, shift2(height, dz, dx, 0))
    kind = np.zeros((Lc, Wc), dtype=np.uint8)
    kind[cave] = 1
    kind[cave & (dfp >= 3)] = 3                    # lake-able margin
    kind[ring] = 2
    ceil = np.where(cave, height, np.where(ring, ringh, 0)).astype(np.int32)
    low = np.full((Lc, Wc), 255, dtype=np.int32)
    solidcol = built.any(axis=0)
    lowmap = np.where(built, ys, H + 1).min(axis=0)
    lowc = np.full((Lc, Wc), 255, dtype=np.int32); lowc[M:M + L, M:M + W] = np.where(solidcol, lowmap, 255)
    # encase one block around the buried part too
    low = lowc.copy()
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            low = np.minimum(low, shift2(lowc, dz, dx, 255))
    # above the cavern ceiling the build keeps the world (nothing of it should be there, but be safe)
    capl = np.full((L, W), 0, dtype=np.int32)
    capl[:, :] = ceil[M:M + L, M:M + W] + ground
    above = ys > capl[None]
    role[above & (role == AIR)] = VOID

    c = Converted()
    c.key, c.cfg, c.b, c.m, c.role, c.val, c.tiles, c.ground, c.notes = key, cfg, b, m, role, val, tile_list, ground, notes
    c.M, c.kind, c.ceil, c.low, c.footprint, c.shaft, c.old_spawners = M, kind, ceil, low, footprint, shaft, old_spawners
    c.maxneed = maxneed
    c.crop_info = crop_info
    place_markers(c)
    return c

def dig_entrance(b, role, val, ground, notes):
    """A ladder shaft from the ground surface down to the build's largest sealed room, if nothing leads in."""
    H, L, W = b.shape
    openc = (role == AIR) | (role == PLANT) | ((role == LIT) & np.isin(b, list(PASSABLE)))
    inner = openc.copy(); inner[ground + 1:] = False
    # rooms already reached from the surface: open cells of the top layer flooding down
    seed = np.zeros_like(inner); seed[ground] = inner[ground]
    reach = flood(seed, inner)
    rooms = inner & ~reach
    if reach.sum() > 200 or not rooms.any():
        notes.append('entrance: open (%d cells reachable)' % int(reach.sum()))
        return None
    # the column nearest the centre whose topmost room cell is highest
    tops = np.where(rooms, np.arange(H).reshape(H, 1, 1), -1).max(axis=0)
    cz, cx = L / 2, W / 2
    best, bs = None, None
    zz, xx = np.nonzero(tops >= 0)
    for z, x in zip(zz, xx):
        if z < 2 or x < 2 or z > L - 3 or x > W - 3: continue
        s = (ground - tops[z, x]) * 3 + math.hypot(z - cz, x - cx) * 0.25
        if bs is None or s < bs: bs, best = s, (int(z), int(x))
    if best is None: return None
    z, x = best
    y0 = int(tops[z, x]) + 1
    for y in range(y0, ground + 1):
        role[y, z, x] = AIR
        # a ladder on the north wall of the shaft, a rock wall behind it
        if role[y, z - 1, x] in (AIR, VOID, PLANT):
            role[y, z - 1, x] = LIT; val[y, z - 1, x] = 112 << 4
    for y in range(y0, ground + 1):
        role[y, z, x] = LIT; val[y, z, x] = (65 << 4) | 3
    # a nether-brick well head around the hole, open to the south
    for dz in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if (dz == 0 and dx == 0) or 0 > z + dz or z + dz >= L or 0 > x + dx or x + dx >= W: continue
            role[ground, z + dz, x + dx] = LIT; val[ground, z + dz, x + dx] = 112 << 4
            if ground + 1 < H and dz != 1 and role[ground + 1, z + dz, x + dx] in (AIR, VOID):
                role[ground + 1, z + dz, x + dx] = LIT; val[ground + 1, z + dz, x + dx] = 113 << 4
    notes.append('entrance: ladder shaft at %d,%d down %d' % (x, z, ground - y0 + 1))
    return (x, y0, z)

# ------------------------------------------------------------------------------------------------------------------
def place_markers(c):
    """Loot chests, spawner spots, garrison spots and the lord's arena, spread out, deterministic per build."""
    rng = np.random.default_rng(int(c.key[1:]) * 7919 + 13)
    role, val, b = c.role, c.val, c.b
    H, L, W = role.shape
    ids = val >> 4
    passable = np.isin(ids, list(PASSABLE))
    openc = (role == AIR) | (role == PLANT) | ((role == LIT) & passable)
    solidc = ~openc & (role != VOID) & ~((role == LIT) & np.isin(ids, [8, 9, 10, 11])) & (role != LIQUID) & (role != TILE)
    walk = openc & shift(openc, -1, 0, 0, False) & shift(solidc, 1, 0, 0, False)
    # sheltered: something solid within 20 blocks above
    roof = np.zeros(role.shape, dtype=bool)
    acc = np.zeros(role.shape[1:], dtype=np.int32)
    for y in range(H - 1, -1, -1):
        roof[y] = acc > 0
        acc = np.where(solidc[y], 20, np.maximum(acc - 1, 0))
    shelter = walk & roof
    lava_near = np.zeros(role.shape, dtype=bool)
    lq = np.isin(ids, [10, 11]) | (role == LIQUID)
    for dy, dz, dx in ((0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1), (-1, 0, 0), (1, 0, 0)):
        lava_near |= shift(lq, dy, dz, dx, False)
    walk &= ~lava_near; shelter &= ~lava_near
    area = int(c.footprint.sum())
    tier = c.cfg['tier']
    k = {'common': (350, 2, 10, 500, 2, 8, 600, 3, 10), 'great': (450, 6, 24, 700, 5, 18, 800, 8, 22),
         'lord': (500, 4, 16, 800, 4, 12, 900, 6, 14)}[tier]
    n_sp = int(np.clip(round(area / k[0]), k[1], k[2]))
    n_ga = int(np.clip(round(area / k[3]), k[4], k[5]))
    n_ch = int(np.clip(round(area / k[6]), k[7], k[8]))
    n_vault = {'common': 1, 'great': 3, 'lord': 2}[tier]

    def pick(mask, n, spacing, avoid=()):
        pts = np.argwhere(mask)
        if len(pts) == 0 or n <= 0: return []
        if len(pts) > 40000: pts = pts[rng.choice(len(pts), 40000, replace=False)]
        rng.shuffle(pts)
        out = []
        for p in pts:
            ok = True
            for q in list(out) + list(avoid):
                if (p[0] - q[0]) ** 2 * 4 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2 < spacing * spacing: ok = False; break
            if ok:
                out.append(p)
                if len(out) >= n: break
        return out

    # chests: the build's own first, then spots against a wall under a roof
    chests = [t for t in c.tiles if t['kind'] == 'chest']
    for t in chests: t['table'] = ''
    order = list(range(len(chests)))
    rng.shuffle(order)
    # deepest (most sheltered) chests and ender chests become vaults
    def depthscore(t):
        y, z, x = t['y'], t['z'], t['x']
        above = int(solidc[y + 1:, z, x].sum()) if y + 1 < H else 0
        return above + (50 if t.get('ender') else 0) + rng.random()
    ranked = sorted(chests, key=depthscore, reverse=True)
    vaults = []
    for t in ranked:
        if len(vaults) >= n_vault: break
        if all((t['x'] - v['x']) ** 2 + (t['z'] - v['z']) ** 2 > 144 for v in vaults): vaults.append(t)
    for t in vaults: t['table'] = 'vault'
    rest = [chests[i] for i in order if not chests[i]['table']]
    primary = []
    for t in rest:
        if len(primary) >= n_ch: break
        if all((t['x'] - p['x']) ** 2 + (t['z'] - p['z']) ** 2 + 4 * (t['y'] - p['y']) ** 2 > 64 for p in primary): primary.append(t)
    for t in primary: t['table'] = 'rich' if tier != 'common' else 'common'
    scraps = [t for t in rest if not t['table']][:24]
    for t in scraps: t['table'] = 'scraps'
    have = [(t['y'], t['z'], t['x']) for t in chests if t['table']]
    # extra chests where the build has too few
    missing = max(0, n_ch + n_vault - len([t for t in chests if t['table'] in ('rich', 'common', 'vault')]))
    wall = np.zeros(role.shape, dtype=bool)
    facing = np.zeros(role.shape, dtype=np.int32)
    for dz, dx, f in ((-1, 0, 3), (1, 0, 2), (0, -1, 5), (0, 1, 4)):
        w = shift(solidc, 0, -dz, -dx, False) & shelter & ~wall
        facing[w] = f
        wall |= w
    added = pick(wall & (role != TILE), missing, 9, have)
    if len(added) < missing:
        # a small build: stand the chests on its floor, or on the cavern floor inside its box
        added += pick(walk & (role != TILE), missing - len(added), 5, have + added)
    if len(added) < missing and c.ground >= 0 and c.ground + 1 < H:
        floor = np.zeros(role.shape, dtype=bool)
        floor[c.ground + 1] = np.isin(role[c.ground + 1], [AIR, VOID]) & np.isin(c.kind[c.M:c.M+L,c.M:c.M+W], [1,3])
        added += pick(floor, missing - len(added), 5, have + added)
    for i, p in enumerate(added):
        y, z, x = map(int, p)
        f = int(facing[y, z, x]) or 3
        role[y, z, x] = TILE; val[y, z, x] = (54 << 4) | f
        if y > 0 and role[y - 1, z, x] in (AIR, VOID):     # a chest on the cavern floor needs its floor
            role[y - 1, z, x] = LIT; val[y - 1, z, x] = 112 << 4
        t = dict(y=y, z=z, x=x, block=54, kind='chest', trapped=False, table=('vault' if len(vaults) + i < n_vault else ('rich' if tier != 'common' else 'common')), added=True)
        c.tiles.append(t)
        have.append((y, z, x))
    # spawners replace the floor under a sheltered walkable cell (or where the build had one)
    spots = [np.array(p) for p in c.old_spawners]
    floor_ok = shift(shelter, -1, 0, 0, False) & solidc & (role != TILE)
    sp = pick(floor_ok, n_sp - len(spots), 11, have + spots)
    if len(sp) + len(spots) < n_sp:
        sp += pick(shift(walk, -1, 0, 0, False) & solidc & (role != TILE), n_sp - len(sp) - len(spots), 9, have + spots + sp)
    if len(sp) + len(spots) < n_sp and c.ground >= 0:
        # the ground layer itself, beside the build
        floor = np.zeros(role.shape, dtype=bool)
        floor[c.ground] = (role[c.ground] != TILE) & (role[c.ground] != VOID) & (role[c.ground] != AIR)
        if c.ground + 1 < H: floor[c.ground] &= np.isin(role[c.ground + 1], [AIR, VOID])
        sp += pick(floor, n_sp - len(sp) - len(spots), 6, have + spots + sp)
    c.spawners = [(int(p[2]), int(p[0]), int(p[1])) for p in list(spots) + sp]
    for (x, y, z) in c.spawners:
        if 0 <= x < W and 0 <= y < H and 0 <= z < L: role[y, z, x] = TILE; val[y, z, x] = 52 << 4
    # garrisons anywhere one can stand (inside first); tiny builds use the cavern floor inside their box
    # Chests/spawners changed occupancy since walk was first computed. Mob
    # packs and arenas must use the final roles, not stand inside a new tile.
    openc = (role == AIR) | ((role == LIT) & np.isin(val >> 4, list(PASSABLE)))
    solidc = ~openc & (role != VOID) & ~np.isin(val >> 4, [8,9,10,11])
    walk = openc & shift(openc, -1, 0, 0, False) & shift(solidc, 1, 0, 0, False) & ~lava_near
    shelter = walk & roof
    ga = pick(shelter, (n_ga + 1) // 2, 16, have)
    ga += pick(walk, n_ga // 2, 16, have + ga)
    if len(ga) < n_ga:
        ga += pick(walk & ~shelter, n_ga - len(ga), 12, have + ga)
    if len(ga) < n_ga and c.ground >= 0 and c.ground + 2 < H:
        floor = np.zeros(role.shape, dtype=bool)
        floor[c.ground + 1] = np.isin(role[c.ground + 1], [AIR, VOID]) & np.isin(role[c.ground + 2], [AIR, VOID]) & np.isin(c.kind[c.M:c.M+L,c.M:c.M+W], [1,3])
        ga += pick(floor, n_ga - len(ga), 10, have + ga)
    c.garrisons = [(int(p[2]), int(p[0]), int(p[1])) for p in ga][:n_ga]
    # still too few (a tiny build filling its box): the cavern floor around it, three blocks out
    ring = [(W // 2, c.ground + 1, -3), (W // 2, c.ground + 1, L + 2), (-3, c.ground + 1, L // 2), (W + 2, c.ground + 1, L // 2)]
    for p in ring:
        if len(c.garrisons) >= n_ga or c.ground < 0: break
        if c.kind[p[2]+c.M,p[0]+c.M] not in (1,3) or c.ceil[p[2]+c.M,p[0]+c.M] < 3: continue
        c.garrisons.append(p)
    for p in ring[::-1]:
        if len(c.spawners) >= n_sp or c.ground < 0: break
        if c.kind[p[2]+c.M,p[0]+c.M] not in (1,3) or c.ceil[p[2]+c.M,p[0]+c.M] < 3: continue
        if (p[0], p[1] - 1, p[2]) not in c.spawners: c.spawners.append((p[0], p[1] - 1, p[2]))
    # the lord's arena: the most open walkable place (flying lords: above the build's middle)
    c.arena = None
    lord = c.cfg.get('lord')
    if lord:
        c.arena = find_arena(c, walk, openc, lord)
    c.counts = dict(spawners=len(c.spawners), garrisons=len(c.garrisons), chests=len(chests) + len(added),
                    loot=len([t for t in c.tiles if t.get('kind') == 'chest' and t.get('table')]))

FLYING = {'deathwing', 'ignareth', 'ashen_wither'}
TALL = {'pit_lord': 13, 'bone_colossus': 4, 'crimson_tyrant': 8}

def find_arena(c, walk, openc, lord):
    """Where the Lord rises. Flying Lords: the most open pocket of air in the cavern (farthest from any block), a little
    above the middle of the build. The others: the widest walkable floor with room for their height, drawn towards the
    build's middle and its lower floors (a Lord holds the great hall, not a rooftop)."""
    H, L, W = walk.shape
    if lord in FLYING:
        air = (c.role == AIR)
        # distance to the nearest non-air cell, in blocks (6-neighbour chamfer, capped at 12)
        d = np.where(air, 12, 0).astype(np.int32)
        for _ in range(12):
            m = d.copy()
            for dy, dz, dx in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                m = np.minimum(m, shift(d, dy, dz, dx, 0) + 1)
            m = np.where(air, m, 0)
            if (m == d).all(): break
            d = m
        ys = np.arange(H).reshape(H, 1, 1); zs = np.arange(L).reshape(1, L, 1); xs = np.arange(W).reshape(1, 1, W)
        mid = max(c.ground, 0) + (H - max(c.ground, 0)) * 0.55
        score = d * 4.0 - 0.25 * np.abs(ys - mid) - 0.08 * np.hypot(zs - L / 2, xs - W / 2)
        score = np.where(air & (d >= 5), score, -1e9)
        i = int(np.argmax(score))
        if score.flat[i] <= -1e8: return (W // 2, H - 1, L // 2)
        y, z, x = np.unravel_index(i, score.shape)
        return (int(x), int(y), int(z))
    need = TALL.get(lord, 4)
    clear = np.zeros(walk.shape, dtype=np.int32)
    run = np.zeros(walk.shape[1:], dtype=np.int32)
    for y in range(H - 1, -1, -1):
        run = np.where(openc[y], run + 1, 0)
        clear[y] = run
    best, bs = None, None
    cand = walk & (clear >= need)
    zz, xx = np.meshgrid(np.arange(L), np.arange(W), indexing='ij')
    centre = np.hypot(zz - L / 2, xx - W / 2)
    for y in np.unique(np.nonzero(cand)[0]):
        layer = cand[y].astype(np.int32)
        ii = np.pad(layer, ((1, 0), (1, 0))).cumsum(0).cumsum(1)
        r = 6
        z0 = np.clip(np.arange(L) - r, 0, L); z1 = np.clip(np.arange(L) + r + 1, 0, L)
        x0 = np.clip(np.arange(W) - r, 0, W); x1 = np.clip(np.arange(W) + r + 1, 0, W)
        area = ii[z1][:, x1] - ii[z0][:, x1] - ii[z1][:, x0] + ii[z0][:, x0]
        s = np.where(layer > 0, area - 0.5 * centre - 0.6 * max(0, y - max(c.ground, 0)), -1e9)
        i = int(np.argmax(s))
        if s.flat[i] > -1e8 and (bs is None or s.flat[i] > bs): bs, best = float(s.flat[i]), (int(i % W), int(y), int(i // W))
    if best is None:
        return (W // 2, max(c.ground + 1, 0), L // 2)
    return best

# ------------------------------------------------------------------------------------------------------------------
def encode(c):
    """GlmBuild format v1 (big-endian): see GlmBuild.read."""
    role, val = c.role, c.val
    H, L, W = role.shape
    # palette of (role, value); VOID and AIR are fixed entries 0 and 1
    keyarr = (role.astype(np.int64) << 20) | np.where(np.isin(role, [AIR, VOID]), 0, val).astype(np.int64)
    uniq, inv = np.unique(keyarr, return_inverse=True)
    pal = [(int(u >> 20), int(u & 0xFFFFF)) for u in uniq]
    order = sorted(range(len(pal)), key=lambda i: (pal[i][0] != VOID, pal[i][0] != AIR, i))
    remap = np.empty(len(pal), dtype=np.int32)
    for new, old in enumerate(order): remap[old] = new
    pal = [pal[i] for i in order]
    vol = remap[inv].reshape(role.shape)
    out = io.BytesIO()
    w = out.write
    def u8(v): w(struct.pack('>B', v))
    def u16(v): w(struct.pack('>H', v))
    def i16(v): w(struct.pack('>h', v))
    def utf(s):
        s = s.encode('utf-8')[:2000]; w(struct.pack('>H', len(s))); w(s)
    w(b'JGLB'); u8(VERSION)
    u16(W); u16(H); u16(L); i16(c.ground); u8(c.M)
    u16(len(pal))
    for r, v in pal:
        u8(r)
        if r in (LIT, TILE):
            for (i, m) in rotations(v >> 4, v & 15): u16((i << 4) | m)
        elif r == TRUNK:
            for k in range(4): u16(v if k % 2 == 0 else ({4: 8, 8: 4}.get(v & 12, v & 12) | (v & 3)))
        else:
            for k in range(4): u16(v)
    wide = len(pal) > 256
    u8(1 if wide else 0)
    w((vol.astype('>u2') if wide else vol.astype(np.uint8)).tobytes())
    Lc, Wc = c.kind.shape
    w(c.kind.astype(np.uint8).tobytes())
    w(np.clip(c.ceil, 0, 250).astype(np.uint8).tobytes())
    w(np.clip(c.low, 0, 255).astype(np.uint8).tobytes())
    tiles = [t for t in c.tiles if role[t['y'], t['z'], t['x']] == TILE]
    u16(len(tiles))
    for t in tiles:
        kind = t['kind']
        u8({'chest': 1, 'sign': 2, 'banner': 3, 'skull': 4, 'pot': 5}[kind])
        u16(t['x']); u16(t['y']); u16(t['z'])
        u16(int(vol[t['y'], t['z'], t['x']]))
        if kind == 'chest':
            u8(1 if t.get('trapped') else 0); utf(t.get('table', ''))
        elif kind == 'sign':
            for l in t['lines']: utf(l)
        elif kind == 'banner':
            u8(t['base']); u8(len(t['patterns']))
            for col, pat in t['patterns']: u8(col); utf(pat)
        elif kind == 'skull':
            u8(t['skull']); u8(t['rot'])
        elif kind == 'pot':
            utf(t['item'])
    markers = [('spawner', p, str(i)) for i, p in enumerate(c.spawners)] + [('garrison', p, str(i)) for i, p in enumerate(c.garrisons)]
    if c.arena: markers.append(('arena', c.arena, c.cfg['lord']))
    u16(len(markers))
    for kind, (x, y, z), arg in markers:
        i16(x); i16(y); i16(z); utf(kind); utf(arg)
    return gzip.compress(out.getvalue(), 9, mtime=0), pal, vol

# ------------------------------------------------------------------------------------------------------------------
# Review renders (roles in a sample region palette)
def colour_table():
    import importlib.util
    DYE = [0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447, 0x8E8E86, 0x158991, 0x792AAC, 0x35399D,
           0x724728, 0x546D1B, 0xA12722, 0x141519]
    CLAY = [0xD1B1A1, 0xA15325, 0x95576C, 0x706C8A, 0xBA8523, 0x677534, 0xA14E4E, 0x392A23, 0x876A61, 0x565B5B, 0x764656,
            0x4A3B5B, 0x4D3323, 0x4B522A, 0x8E3C2E, 0x251610]
    base = {1: 0x7D7D7D, 4: 0x7A7A7A, 5: 0xA2834F, 17: 0x6B5433, 20: 0xC0E0F0, 24: 0xDBD3A0, 43: 0x9C9C9C, 44: 0x9C9C9C, 45: 0x965A4B,
            47: 0x6B5433, 48: 0x5A6C4A, 49: 0x14121E, 50: 0xFFD35A, 53: 0xA2834F, 54: 0x9E6E2E, 58: 0x7B5A33, 61: 0x6E6E6E, 64: 0xA2834F,
            65: 0x8C6A39, 66: 0x8C8C6E, 67: 0x7A7A7A, 69: 0x6E6E6E, 70: 0x7D7D7D, 72: 0xA2834F, 77: 0x7D7D7D, 85: 0x9A7A4B, 86: 0xE38A1D,
            87: 0x6F3634, 88: 0x51402F, 89: 0xF9D49C, 91: 0xE38A1D, 92: 0xE8D8C8, 96: 0x7E5D2D, 97: 0x7D7D7D, 98: 0x7A7A7A,
            99: 0x8D6A4D, 100: 0xB02A28, 101: 0x5E5E5E, 102: 0xC0E0F0, 103: 0x8F9224, 107: 0x6E5838, 108: 0x965A4B, 109: 0x7A7A7A,
            110: 0x6F6369, 112: 0x2C1519, 113: 0x2C1519, 114: 0x2C1519, 115: 0x8A1818, 117: 0x7A6A5A, 118: 0x3A3A3A, 121: 0xDDDFA5,
            123: 0x5F3A1E, 125: 0xA2834F, 126: 0xA2834F, 128: 0xDBD3A0, 131: 0x7D7D7D, 134: 0x5A4228, 135: 0xC8B77A, 136: 0x9A6E4B,
            139: 0x7A7A7A, 140: 0x7C4536, 143: 0xA2834F, 144: 0xCCCCCC, 146: 0x9E6E2E, 153: 0x7D4E4A, 155: 0xECE6DF, 156: 0xECE6DF,
            158: 0x6E6E6E, 162: 0x3D2813, 163: 0xA85A32, 164: 0x3D2813, 165: 0x6FC15A, 168: 0x63A597, 169: 0xAECBC0, 170: 0xA68A0C,
            172: 0x985E43, 173: 0x111111, 174: 0x8DB4FA, 179: 0xA8551F, 180: 0xA8551F, 181: 0xA8551F, 182: 0xA8551F, 183: 0x5A4228,
            184: 0xC8B77A, 185: 0x9A6E4B, 186: 0x3D2813, 187: 0xA85A32, 188: 0x5A4228, 189: 0xC8B77A, 190: 0x9A6E4B, 191: 0x3D2813,
            192: 0xA85A32, 193: 0x5A4228, 194: 0xC8B77A, 195: 0x9A6E4B, 196: 0xA85A32, 197: 0x3D2813, 198: 0xF0F0F0, 201: 0xA97EA9,
            202: 0xA97EA9, 203: 0xA97EA9, 204: 0xA97EA9, 205: 0xA97EA9, 206: 0xE2E7AB, 213: 0x8E3F1F, 214: 0x730303, 215: 0x450709,
            216: 0xE1DDC9, 10: 0xE3781E, 11: 0xE3781E, 8: 0x3F76E4, 9: 0x3F76E4, 30: 0xDDDDDD, 29: 0x8C8C6E, 33: 0x8C8C6E, 34: 0x9A7A4B,
            23: 0x6E6E6E, 25: 0x6B4433, 26: 0xA12722, 51: 0xFF9A1E, 52: 0x243646, 63: 0xA2834F, 68: 0xA2834F, 76: 0xAA0000, 78: 0xF0FBFB,
            80: 0xF0FBFB, 81: 0x0D6A18, 83: 0x94C065, 106: 0x3E7A2A, 111: 0x3E7A2A, 176: 0xEEEEEE, 177: 0xEEEEEE, 199: 0x5E3C5E,
            200: 0x5E3C5E, 18: 0x3E7A2A, 161: 0x3E7A2A, 2: 0x6A9B3F, 3: 0x86603F, 12: 0xDBD3A0, 13: 0x857F7E, 7: 0x333333, 82: 0xA0A6B3}
    lut = np.zeros((4096 * 16, 3), dtype=np.uint8)
    for i in range(4096):
        for m in range(16):
            if i in (35, 95, 160, 171, 251, 252): cc = DYE[m]
            elif i == 159: cc = CLAY[m]
            elif 235 <= i <= 250: cc = DYE[i - 235]
            elif i in (43, 44): cc = 0x2C1519 if m % 8 == 6 else 0xECE6DF if m % 8 == 7 else 0xDBD3A0 if m % 8 == 1 else 0x9C9C9C
            elif i == 1 and m > 0: cc = [0x7D7D7D, 0x9A6B5E, 0xA67F74, 0xBCBCBC, 0xC2C2C2, 0x858585, 0x8A8A8A][m % 7]
            else: cc = base.get(i, 0xFF00FF)
            lut[(i << 4) | m] = [(cc >> 16) & 255, (cc >> 8) & 255, cc & 255]
    return lut

ROLE_COLOUR = {SURFACE: 0x6F3634, SOIL: 0x5A2A28, ROCK: 0x4E2422, SAND: 0x51402F, CANOPY: 0x730303, TRUNK: 0xE1DDC9,
               PLANT: 0x8A1818, LIQUID: 0xE3781E, SNOW: 0xE1DDC9, PATH: 0x51402F, TILE: 0xB8860B}

def render(c, path, lut):
    from PIL import Image
    role, val = c.role, c.val
    H, L, W = role.shape
    solid = (role != AIR) & (role != VOID)
    col = np.zeros(role.shape + (3,), dtype=np.uint8)
    lit = solid & ((role == LIT) | (role == TILE))
    col[lit] = lut[val[lit] & 0xFFFF]
    for r, cc in ROLE_COLOUR.items():
        sel = solid & (role == r) if r != TILE else np.zeros_like(solid)
        col[sel] = [(cc >> 16) & 255, (cc >> 8) & 255, cc & 255]
    S = max(1, min(4, 1100 // (W + L)))
    img_w, img_h = (W + L) * S + 4, (W + L) * S // 2 + H * S + 4
    img = np.zeros((img_h, img_w, 3), dtype=np.uint8); img[:] = (24, 14, 16)
    ys, zs, xs = np.nonzero(solid)
    def exposed(dy, dz, dx):
        yy, zz, xx = ys + dy, zs + dz, xs + dx
        inside = (yy >= 0) & (yy < H) & (zz >= 0) & (zz < L) & (xx >= 0) & (xx < W)
        o = np.ones(len(ys), dtype=bool)
        o[inside] = ~solid[yy[inside], zz[inside], xx[inside]]
        return o
    top, east, south = exposed(1, 0, 0), exposed(0, 0, 1), exposed(0, 1, 0)
    keep = top | east | south
    ys, zs, xs, top, east, south = ys[keep], zs[keep], xs[keep], top[keep], east[keep], south[keep]
    order = np.lexsort((ys, xs + zs))
    ys, zs, xs, top, east, south = ys[order], zs[order], xs[order], top[order], east[order], south[order]
    cc = col[ys, zs, xs].astype(np.float32)
    px = (xs - zs) * S + L * S + 2
    py = (xs + zs) * S // 2 - ys * S + H * S + 2
    for k in range(len(ys)):
        x0, y0, cv = px[k], py[k], cc[k]
        if top[k]: img[max(0, y0 - S):y0, x0 - S:x0 + S] = cv
        if south[k]: img[y0:y0 + S, x0 - S:x0] = cv * 0.62
        if east[k]: img[y0:y0 + S, x0:x0 + S] = cv * 0.8
    for (x, y, z) in c.spawners:
        px0, py0 = (x - z) * S + L * S + 2, (x + z) * S // 2 - y * S + H * S + 2
        img[max(0, py0 - 3 * S):py0 + S, max(0, px0 - S):px0 + S] = (80, 200, 255)
    for (x, y, z) in c.garrisons:
        px0, py0 = (x - z) * S + L * S + 2, (x + z) * S // 2 - y * S + H * S + 2
        img[max(0, py0 - 3 * S):py0 + S, max(0, px0 - S):px0 + S] = (255, 60, 60)
    if c.arena:
        x, y, z = c.arena
        px0, py0 = (x - z) * S + L * S + 2, (x + z) * S // 2 - y * S + H * S + 2
        img[max(0, py0 - 6 * S):py0 + 2 * S, max(0, px0 - 2 * S):px0 + 2 * S] = (255, 255, 0)
    Image.fromarray(img).save(path)

# ------------------------------------------------------------------------------------------------------------------
def main(argv):
    global SRC
    keys, render_dir, report = [], None, False
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == '--src': SRC = argv[i + 1]; i += 2; continue
        if a == '--render': render_dir = argv[i + 1]; i += 2; continue
        if a == '--report': report = True; i += 1; continue
        keys.append(a); i += 1
    files = sorted(glob.glob(os.path.join(SRC, '*.schematic')))
    if not files: raise SystemExit('no schematics in %s' % SRC)
    os.makedirs(OUT, exist_ok=True)
    lut = colour_table() if render_dir else None
    if render_dir: os.makedirs(render_dir, exist_ok=True)
    rows = []
    index_path = os.path.join(OUT, 'builds.tsv')
    old = {}
    if os.path.exists(index_path) and keys:
        for line in open(index_path, encoding='utf-8'):
            if line.startswith('#') or not line.strip(): continue
            old[line.split('\t')[0]] = line.rstrip('\n')
    for f in files:
        fname = os.path.splitext(os.path.basename(f))[0]
        key = fname.split('_')[0]
        cfg = B.get(key) or default_config(key, fname)
        if cfg.get('skip'):
            print('%s skipped: %s' % (key, cfg['skip']))
            continue
        if keys and key not in keys:
            if key in old: rows.append(old[key])
            continue
        c = convert(key, f, cfg, None)
        tier = cfg['tier']
        H, L, W = c.role.shape
        if tier == 'auto': tier = cfg['tier'] = 'great' if max(L, W) > 110 else 'common'
        data, pal, vol = encode(c)
        with open(os.path.join(OUT, key + '.glb'), 'wb') as fh: fh.write(data)
        # forbidden check on everything that is placed literally
        lits = [(r, v) for r, v in pal if r in (LIT, TILE)]
        bad = sorted({v >> 4 for r, v in lits if (v >> 4) in FORBIDDEN})
        if bad: raise SystemExit('%s: forbidden blocks survived: %s' % (key, bad))
        span = H - max(c.ground, 0)
        rows.append('\t'.join(str(x) for x in (key, cfg['title'], tier, cfg['theme'], cfg.get('affinity', '') or '-', cfg.get('lord') or '-',
                                                 W, H, L, c.ground, c.M, int(c.ceil.max()), c.kind.shape[1], c.kind.shape[0],
                                                 c.counts['loot'], c.counts['spawners'], c.counts['garrisons'], len(data))))
        print('%s %-30s %-6s %-9s %3dx%3dx%3d ground=%4d M=%2d ceil=%3d pal=%4d loot=%3d/%4d sp=%2d ga=%2d tiles=%4d %6.1fkB %s' % (
            key, cfg['title'][:30], tier, cfg['theme'], W, H, L, c.ground, c.M, int(c.ceil.max()), len(pal), c.counts['loot'],
            c.counts['chests'], c.counts['spawners'], c.counts['garrisons'], len([t for t in c.tiles if c.role[t['y'], t['z'], t['x']] == TILE]),
            len(data) / 1024, '; '.join(c.notes)))
        if render_dir: render(c, os.path.join(render_dir, key + '.png'), lut)
    rows.sort()
    with open(index_path, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('# GLM Nether builds (tools/convert_glm.py). key\ttitle\ttier\ttheme\taffinity\tlord\tsx\tsy\tsz\tground\tmargin\tmaxceil\tcavw\tcavd\tloot\tspawners\tgarrisons\tbytes\n')
        for r in rows: fh.write(r + '\n')
    print('wrote %d builds to %s' % (len(rows), OUT))

if __name__ == '__main__':
    main(sys.argv[1:])
