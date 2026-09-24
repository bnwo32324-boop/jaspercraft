"""Self-test: JSD1 round trip + a synthetic NATURAL (masked) dump through render.py and analyze.py.

    python selftest.py [--keep]
Builds set 'selftest' from grounds dumps: each structure is embedded in synthetic terrain (stone, dirt,
grass surface) as 'buried' (surface above the roof, a shaft carved to the surface) and as 'surface'.
Outputs go to SA/dumps/selftest, SA/renders/selftest, SA/analysis/selftest (deleted unless --keep).
"""
import os
import shutil
import sys
import time

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from jsd import read_jsd, write_jsd, Dump  # noqa: E402
import render  # noqa: E402
import analyze  # noqa: E402

SA = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit"


def embed(src: Dump, mode: str, margin=8, below=6, cover=5):
    ids = src.states
    g = src.header.get("grounds") or {}
    if g.get("glassTank"):
        fx0, fz0, fx1, fz1 = g["footprintRel"]
        ids = ids[:, fz0:fz1, fx0:fx1]
    SY, SZ, SX = ids.shape
    H = below + SY + (cover + 3 if mode == "buried" else 6)
    st = np.zeros((H, SZ + 2 * margin, SX + 2 * margin), np.uint16)
    mk = np.zeros(st.shape, np.uint8)
    surf = below + (SY + cover if mode == "buried" else 0)        # y of the grass layer
    xs = np.arange(st.shape[2])[None, :]
    zs = np.arange(st.shape[1])[:, None]
    wobble = ((np.sin(xs / 5.0) + np.cos(zs / 7.0)) * 1.2).round().astype(int)
    for y in range(H):
        top = surf + wobble
        st[y][y < top - 3] = 1 << 4
        st[y][(y >= top - 3) & (y < top)] = 3 << 4
        st[y][y == top] = 2 << 4
    y0, z0, x0 = below, margin, margin
    region = (slice(y0, y0 + SY), slice(z0, z0 + SZ), slice(x0, x0 + SX))
    solid = ids != 0
    sub_st = st[region]
    sub_mk = mk[region]
    sub_st[solid] = ids[solid]
    sub_mk[solid] = 1
    # the builder carves the interior air of the footprint (like the real generators do)
    sub_st[~solid] = 0
    sub_mk[~solid] = 2
    if mode == "buried":
        # shaft from the roof to the surface
        cz, cx = z0 + SZ // 2, x0 + 1
        for y in range(y0 + SY, H):
            st[y, cz, cx] = 0
            mk[y, cz, cx] = 2
    offx, offz = (g["footprintRel"][0], g["footprintRel"][1]) if g.get("glassTank") else (0, 0)
    h = dict(src.header)
    h.update({"set": "selftest", "variant": "n" + mode[0], "context": "natural", "hasMask": True,
              "origin": [0, 0, 0], "site": {"x": x0, "z": z0, "floorY": y0, "sizeX": SX, "sizeZ": SZ,
                                            "height": SY, "mode": mode}, "grounds": None,
              "tiles": [dict(t, x=t["x"] - offx + x0, y=t["y"] + y0, z=t["z"] - offz + z0)
                        for t in src.header.get("tiles", [])],
              "notes": "synthetic natural embedding for selftest"})
    return Dump(h, st, mk)


def main(keep=False):
    ok = True
    src = read_jsd(os.path.join(SA, "dumps", "grounds-w3", "dun__10__g.jsd"))
    # 1. JSD round trip
    tmp = os.path.join(SA, "dumps", "selftest")
    os.makedirs(tmp, exist_ok=True)
    for mode in ("buried", "surface"):
        d = embed(src, mode)
        p = os.path.join(tmp, f"dun__10__n{mode[0]}.jsd")
        write_jsd(p, d)
        back = read_jsd(p)
        same = np.array_equal(back.states, d.states) and np.array_equal(back.mask, d.mask)
        print(f"roundtrip {mode}: {'OK' if same else 'FAIL'} size={back.size}")
        ok &= same
        iso = back.iso()
        print(f"  iso(): {int((iso.ids != 0).sum())} blocks (mask==1: {int((back.mask == 1).sum())})")
        ok &= int((iso.ids != 0).sum()) == int((back.mask == 1).sum())
        t = time.time()
        od, info = render.render_dump(back)
        print(f"  render: {len(info['files'])} files in {time.time() - t:.1f}s floors={info['floorLevels']} "
              f"-> {od}")
        ok &= any(f.startswith("ctx_iso_") for f in info["files"]) and any(f.startswith("str_iso_") for f in info["files"])
        a = analyze.analyze(back)
        print(f"  analyze: verdict={a['autoVerdict']['verdict']} floating={a['components']['floating']} "
              f"interiorAccessible={a['enclosed']['interiorAccessibleFraction']} reach={a['reach']}")
    if not keep:
        for sub in ("dumps", "renders", "analysis"):
            shutil.rmtree(os.path.join(SA, sub, "selftest"), ignore_errors=True)
    print("SELFTEST", "PASS" if ok else "FAIL")
    return ok


if __name__ == "__main__":
    main("--keep" in sys.argv)
