"""JSD1 structure dump reader / writer (see ../CONVENTIONS.md).

Layout (little-endian):
    0   b"JSD1"
    4   uint32  header length H
    8   H bytes UTF-8 JSON header
        SX*SY*SZ uint16  state = (id << 4) | data, index = (y*SZ + z)*SX + x
        [if header.hasMask] SX*SY*SZ uint8 mask (0 terrain/untouched, 1 structure solid, 2 structure air)

In memory the arrays are numpy arrays of shape (SY, SZ, SX), i.e. indexed [y, z, x]
(the C-order reshape of the file index), so ``states[y, z, x]``.

Usage:
    from jsd import Dump, read_jsd, write_jsd
    d = read_jsd(path)            # Dump
    d.ids, d.data                 # uint16 / uint8 views, shape (SY,SZ,SX)
    d2 = d.crop(x0, y0, z0, x1, y1, z1)   # half-open, array coords
    d3 = d.iso()                  # mask==1 only (others -> air); tiles kept only on mask==1 cells
    write_jsd(path2, d3)
"""
from __future__ import annotations

import json
import struct
from dataclasses import dataclass, field
from typing import Optional

import numpy as np

MAGIC = b"JSD1"


def split_state(states: np.ndarray):
    """Return (ids uint16, data uint8) arrays from packed states."""
    s = np.asarray(states, dtype=np.uint16)
    return (s >> 4).astype(np.uint16), (s & 15).astype(np.uint8)


def pack_state(ids, data) -> np.ndarray:
    return ((np.asarray(ids, dtype=np.uint16) << 4) | (np.asarray(data, dtype=np.uint16) & 15)).astype(np.uint16)


def state(bid: int, bdata: int = 0) -> int:
    return ((int(bid) & 0xFFF) << 4) | (int(bdata) & 15)


@dataclass
class Dump:
    header: dict
    states: np.ndarray                 # uint16 (SY, SZ, SX)
    mask: Optional[np.ndarray] = None  # uint8 (SY, SZ, SX) or None
    path: Optional[str] = None

    # ------------------------------------------------------------------ basics
    @property
    def size(self):
        """(SX, SY, SZ) like the header."""
        sy, sz, sx = self.states.shape
        return sx, sy, sz

    @property
    def ids(self) -> np.ndarray:
        return (self.states >> 4).astype(np.uint16)

    @property
    def data(self) -> np.ndarray:
        return (self.states & 15).astype(np.uint8)

    @property
    def id(self) -> str:
        return self.header.get("id", "?")

    @property
    def variant(self) -> str:
        return self.header.get("variant", "?")

    @property
    def has_mask(self) -> bool:
        return self.mask is not None

    @property
    def tiles(self) -> list:
        return self.header.setdefault("tiles", [])

    def at(self, x, y, z):
        s = int(self.states[y, z, x])
        return s >> 4, s & 15

    def nonair_bbox(self, use_mask: bool = False):
        """Bounding box (x0,y0,z0,x1,y1,z1) half-open of non-air cells (mask==1 cells if use_mask)."""
        if use_mask and self.mask is not None:
            occ = self.mask == 1
        else:
            occ = (self.states >> 4) != 0
        if not occ.any():
            return None
        ys = np.where(occ.any(axis=(1, 2)))[0]
        zs = np.where(occ.any(axis=(0, 2)))[0]
        xs = np.where(occ.any(axis=(0, 1)))[0]
        return int(xs[0]), int(ys[0]), int(zs[0]), int(xs[-1]) + 1, int(ys[-1]) + 1, int(zs[-1]) + 1

    # --------------------------------------------------------------- transforms
    def crop(self, x0, y0, z0, x1, y1, z1) -> "Dump":
        """Crop to the half-open box [x0,x1) x [y0,y1) x [z0,z1) in array coordinates.
        Out-of-range parts are padded with air (mask 0). Origin and tiles are shifted."""
        sx, sy, sz = self.size
        nsx, nsy, nsz = x1 - x0, y1 - y0, z1 - z0
        st = np.zeros((nsy, nsz, nsx), dtype=np.uint16)
        mk = np.zeros((nsy, nsz, nsx), dtype=np.uint8) if self.mask is not None else None
        ax0, ay0, az0 = max(x0, 0), max(y0, 0), max(z0, 0)
        ax1, ay1, az1 = min(x1, sx), min(y1, sy), min(z1, sz)
        if ax1 > ax0 and ay1 > ay0 and az1 > az0:
            st[ay0 - y0:ay1 - y0, az0 - z0:az1 - z0, ax0 - x0:ax1 - x0] = self.states[ay0:ay1, az0:az1, ax0:ax1]
            if mk is not None:
                mk[ay0 - y0:ay1 - y0, az0 - z0:az1 - z0, ax0 - x0:ax1 - x0] = self.mask[ay0:ay1, az0:az1, ax0:ax1]
        h = json.loads(json.dumps(self.header))
        ox, oy, oz = h.get("origin", [0, 0, 0])
        h["origin"] = [ox + x0, oy + y0, oz + z0]
        h["size"] = [nsx, nsy, nsz]
        tiles = []
        for t in h.get("tiles", []):
            tx, ty, tz = t["x"] - x0, t["y"] - y0, t["z"] - z0
            if 0 <= tx < nsx and 0 <= ty < nsy and 0 <= tz < nsz:
                t = dict(t)
                t["x"], t["y"], t["z"] = tx, ty, tz
                tiles.append(t)
        h["tiles"] = tiles
        return Dump(h, st, mk)

    def iso(self) -> "Dump":
        """Structure-only copy: cells with mask==1 keep their state, everything else becomes air.
        Without a mask the dump is returned unchanged (copy)."""
        h = json.loads(json.dumps(self.header))
        if self.mask is None:
            return Dump(h, self.states.copy(), None)
        keep = self.mask == 1
        st = np.where(keep, self.states, 0).astype(np.uint16)
        h["tiles"] = [t for t in h.get("tiles", []) if keep[t["y"], t["z"], t["x"]]]
        h["context"] = "iso"
        h["hasMask"] = False
        h.setdefault("notes", "")
        h["notes"] = (h["notes"] + " " if h["notes"] else "") + "iso-extracted (mask==1)"
        return Dump(h, st, None)

    def trimmed(self, use_mask=False, margin=0) -> "Dump":
        bb = self.nonair_bbox(use_mask)
        if bb is None:
            return self
        x0, y0, z0, x1, y1, z1 = bb
        return self.crop(x0 - margin, y0 - margin, z0 - margin, x1 + margin, y1 + margin, z1 + margin)


# ---------------------------------------------------------------------- file io
def read_jsd(path: str) -> Dump:
    with open(path, "rb") as f:
        buf = f.read()
    if buf[:4] != MAGIC:
        raise ValueError(f"{path}: not a JSD1 file (magic {buf[:4]!r})")
    hlen = struct.unpack_from("<I", buf, 4)[0]
    header = json.loads(buf[8:8 + hlen].decode("utf-8"))
    sx, sy, sz = header["size"]
    n = sx * sy * sz
    off = 8 + hlen
    states = np.frombuffer(buf, dtype="<u2", count=n, offset=off).reshape(sy, sz, sx).astype(np.uint16)
    off += 2 * n
    mask = None
    if header.get("hasMask"):
        mask = np.frombuffer(buf, dtype=np.uint8, count=n, offset=off).reshape(sy, sz, sx).copy()
        off += n
    if off != len(buf):
        raise ValueError(f"{path}: {len(buf) - off} trailing bytes")
    return Dump(header, states, mask, path)


def write_jsd(path: str, dump: Dump) -> None:
    h = dict(dump.header)
    sx, sy, sz = dump.size
    h["format"] = "JSD1"
    h["size"] = [sx, sy, sz]
    h["hasMask"] = dump.mask is not None
    hb = json.dumps(h, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    with open(path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack("<I", len(hb)))
        f.write(hb)
        f.write(np.ascontiguousarray(dump.states, dtype="<u2").tobytes())
        if dump.mask is not None:
            f.write(np.ascontiguousarray(dump.mask, dtype=np.uint8).tobytes())


def new_dump(states: np.ndarray, mask=None, **header) -> Dump:
    sy, sz, sx = states.shape
    h = {"format": "JSD1", "id": "?", "variant": "?", "set": "?", "name": "", "context": "iso",
         "origin": [0, 0, 0], "size": [sx, sy, sz], "site": None, "tiles": [], "hasMask": mask is not None,
         "seed": "", "notes": ""}
    h.update(header)
    return Dump(h, np.asarray(states, dtype=np.uint16), None if mask is None else np.asarray(mask, dtype=np.uint8))


def id_to_filename(sid: str) -> str:
    return sid.replace(":", "__")


def filename_to_id(stem: str) -> str:
    return stem.replace("__", ":", 1)


def split_stem(stem: str):
    """'reg__17__a' -> ('reg:17', 'a'); 'cat__foo__g' -> ('cat:foo', 'g')."""
    base, _, variant = stem.rpartition("__")
    return filename_to_id(base), variant


if __name__ == "__main__":
    import sys
    for p in sys.argv[1:]:
        d = read_jsd(p)
        h = {k: v for k, v in d.header.items() if k != "tiles"}
        print(p, json.dumps(h)[:400], "tiles=", len(d.tiles))
        ids = d.ids
        u, c = np.unique(ids, return_counts=True)
        order = np.argsort(-c)
        print("  top ids:", [(int(u[i]), int(c[i])) for i in order[:12]])
