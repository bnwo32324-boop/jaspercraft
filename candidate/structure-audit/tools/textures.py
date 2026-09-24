"""Block textures from the deployed Eaglercraft asset pack (site/assets.epk).

EPK v2 container (reverse-engineered from the bytes):
    "EAGPKG$$"  u8 n + "ver2.0"   u8 n + file name   u16 n + comment   i64 timestamp   i32 file count
    u8 compression ('G' gzip, 'Z' zlib, '0' none) then the compressed stream, then ":::YEE:>".
    Stream: "HEAD" u8 n + "file-type" u32 n + "epk/resources" '>'
            repeated "FILE" u8 n + path, u32 L, u32 crc32, (L-5) bytes of data, ':' '>'
            "END$"
The first extraction writes tools/cache/textures.npz (16x16 RGBA first frames of every block texture plus a
few entity textures); later imports load that.
"""
from __future__ import annotations

import io
import os
import struct
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
GAME = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale"
EPK = os.path.join(GAME, "site", "assets.epk")
CACHE = os.path.join(HERE, "cache", "textures.npz")

_TEX = None
SOURCE = "unloaded"


def read_epk(path=EPK):
    b = open(path, "rb").read()
    if b[:8] != b"EAGPKG$$":
        raise ValueError("not an EPK")
    p = 8
    n = b[p]; p += 1
    ver = b[p:p + n].decode(); p += n
    n = b[p]; p += 1
    fname = b[p:p + n].decode(); p += n
    n = struct.unpack_from(">H", b, p)[0]; p += 2
    p += n  # comment
    p += 8  # timestamp
    count = struct.unpack_from(">i", b, p)[0]; p += 4
    comp = chr(b[p]); p += 1
    end = b.rfind(b":::YEE:>")
    body = b[p:end]
    if comp == "G":
        raw = zlib.decompressobj(31).decompress(body)
    elif comp == "Z":
        raw = zlib.decompress(body)
    else:
        raw = body
    q = 0
    if raw[:4] != b"HEAD":
        raise ValueError("EPK stream has no HEAD")
    q = 4
    n = raw[q]; q += 1 + n
    n = struct.unpack_from(">I", raw, q)[0]; q += 4 + n
    if raw[q:q + 1] != b">":
        raise ValueError("bad HEAD terminator")
    q += 1
    files = {}
    while True:
        tag = raw[q:q + 4]; q += 4
        if tag == b"END$":
            break
        if tag != b"FILE":
            raise ValueError(f"unexpected tag {tag!r} at {q}")
        n = raw[q]; q += 1
        name = raw[q:q + n].decode("utf-8"); q += n
        ln = struct.unpack_from(">I", raw, q)[0]; q += 4
        crc = struct.unpack_from(">I", raw, q)[0]
        data = raw[q + 4:q + ln - 1]
        if raw[q + ln - 1:q + ln + 1] != b":>":
            raise ValueError(f"bad FILE terminator for {name}")
        if crc and (zlib.crc32(data) & 0xFFFFFFFF) != crc:
            raise ValueError(f"crc mismatch for {name}")
        files[name] = data
        q += ln + 1
    return {"version": ver, "name": fname, "count": count, "compression": comp, "files": files}


def _png(data):
    from PIL import Image
    return np.array(Image.open(io.BytesIO(data)).convert("RGBA"), dtype=np.uint8)


def extract(path=EPK, cache=CACHE):
    epk = read_epk(path)
    out = {}
    pre = "assets/minecraft/textures/blocks/"
    for name, data in epk["files"].items():
        if name.startswith(pre) and name.endswith(".png"):
            a = _png(data)
            w = a.shape[1]
            out["b/" + name[len(pre):-4]] = a[:w, :w] if a.shape[0] > w else a
    for ent in ("entity/chest/normal", "entity/chest/trapped", "entity/chest/ender", "entity/bed/red",
                "entity/sign", "entity/banner_base"):
        key = "assets/minecraft/textures/" + ent + ".png"
        if key in epk["files"]:
            out["e/" + ent.split("/", 1)[1]] = _png(epk["files"][key])
    os.makedirs(os.path.dirname(cache), exist_ok=True)
    np.savez_compressed(cache, **{k.replace("/", "|"): v for k, v in out.items()})
    return out


def load():
    global _TEX, SOURCE
    if _TEX is not None:
        return _TEX
    try:
        if os.path.exists(CACHE) and os.path.getmtime(CACHE) >= os.path.getmtime(EPK):
            z = np.load(CACHE)
            _TEX = {k.replace("|", "/"): z[k] for k in z.files}
            SOURCE = "assets.epk (cached)"
            return _TEX
    except Exception:
        pass
    try:
        _TEX = extract()
        SOURCE = "assets.epk"
    except Exception as e:  # fall back to colour table
        _TEX = {}
        SOURCE = f"colour fallback ({e})"
    return _TEX


def get(name: str):
    """16x16 RGBA uint8 block texture by 1.12 file name (without .png), or None."""
    t = load()
    return t.get("b/" + name)


def entity(name: str):
    return load().get("e/" + name)


if __name__ == "__main__":
    t = extract()
    print(len(t), "textures ->", CACHE)
