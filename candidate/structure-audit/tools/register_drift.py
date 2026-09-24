"""Static check: do the 62 register builders anchor where the register table says they do?

Megaliths.located() (used by /where, StructureLoot.restockAuthored, Discovery, Containment and the capture
harness' discovery) finds a set piece with the register table C_CELL/C_SALT/C_SX/C_SZ/C_FIT/C_MODE. Each builder
anchors itself with its own literals: ground()/deep()/sunken() (Megaliths) or site() (Landmarks), or the
Temples Skin table. Any difference means located() predicts sites that are never built and does not
recognise the ones that are.

    python register_drift.py [--tree TREE] [--json out.json]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

TREE = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\server\custom-plugins\JasprHorrorBiomes"
FILES = ["Landmarks", "Megaliths", "Anomalies", "Relics", "Metropolis", "Wonders", "Temples", "Breach"]


def arr(src, name, conv):
    m = re.search(r"\b" + name + r"\s*=\s*\{(.*?)\};", src, re.S)
    body = re.sub(r"//[^\n]*", "", m.group(1))
    return [conv(v.strip()) for v in body.split(",") if v.strip()]


def num(v):
    v = v.strip().rstrip("L")
    return int(v, 16) if v.lower().startswith("0x") else int(v)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tree", default=TREE)
    ap.add_argument("--json")
    ap.add_argument("--emit-anchors", help="write {k: {kind, cell, salt, sx, sz, param, builder}} for the capture harness")
    a = ap.parse_args()
    d = os.path.join(a.tree, "src", "chat", "jaspr", "biomes")
    src = {f: open(os.path.join(d, f + ".java"), encoding="utf-8").read() for f in FILES}
    M = src["Megaliths"]
    C = {k: arr(M, k, num) for k in ("C_CELL", "C_SALT", "C_SX", "C_SZ", "C_FIT", "C_MODE")}
    names = arr(M, "C_NAME", lambda s: s.strip().strip('"'))
    ranks = {m.group(1): int(m.group(2)) for m in re.finditer(r"\bRANK_(\w+)\s*=\s*(\d+)", M)}
    calls = []
    for f, s in src.items():
        consts = {m.group(1): int(m.group(2)) for m in re.finditer(r"\b(\w+_CELL)\s*=\s*(\d+)", s)}
        for m in re.finditer(r"Site s = (ground|deep|sunken|site)\(t, c, ([^,]+), ([^,]+), (\d+), (\d+), (\d+), ([\w.]+)\);", s):
            kind, cell, salt, sx, sz, p, rank = m.groups()
            meth = s[:m.start()].rsplit("private static void ", 1)[-1].split("(")[0]
            if rank.startswith("k."):
                continue      # Temples skins: handled below
            cellv = consts.get(cell.strip()) if not cell.strip().isdigit() else int(cell)
            calls.append(dict(file=f, method=meth, kind=kind, cell=cellv, salt=num(salt), sx=int(sx), sz=int(sz),
                              param=int(p), rank=ranks[rank.split("RANK_")[1]]))
    T = src["Temples"]
    sk = re.search(r"Site s = ground\(t, c, k\.cell, k\.salt, (\d+), (\d+), (\d+), k\.rank\);", T)
    for m in re.finditer(r"new Skin\(Megaliths\.RANK_(\w+), (\d+), (0x[0-9A-Fa-f]+L)", T):
        calls.append(dict(file="Temples", method="skin:" + m.group(1).lower(), kind="ground", cell=int(m.group(2)),
                          salt=num(m.group(3)), sx=int(sk.group(1)), sz=int(sk.group(2)), param=int(sk.group(3)),
                          rank=ranks[m.group(1)]))
    rows, drift = [], 0
    seen = set()
    for c in sorted(calls, key=lambda c: c["rank"]):
        k = c["rank"]
        seen.add(k)
        want = dict(cell=C["C_CELL"][k], salt=C["C_SALT"][k], sx=C["C_SX"][k], sz=C["C_SZ"][k], fit=C["C_FIT"][k],
                    mode=C["C_MODE"][k])
        mode = {"ground": 0, "site": 0, "deep": 1, "sunken": 2}[c["kind"]]
        diffs = []
        for key, have in (("cell", c["cell"]), ("salt", c["salt"]), ("sx", c["sx"]), ("sz", c["sz"])):
            if have != want[key]:
                diffs.append(f"{key} builder={have} register={want[key]}")
        if mode != want["mode"]:
            diffs.append(f"mode builder={mode} register={want['mode']}")
        if c["kind"] != "sunken" and c["param"] != want["fit"]:
            diffs.append(f"{'height' if mode == 1 else 'slope'} builder={c['param']} register C_FIT={want['fit']}")
        drift += bool(diffs)
        rows.append(dict(k=k, name=names[k], builder=f"{c['file']}.{c['method']}", kind=c["kind"], diffs=diffs))
        print(f"reg:{k:<2} {names[k][:22]:<22} {c['file']}.{c['method']:<16} {c['kind']:<6} "
              + ("OK" if not diffs else "DRIFT: " + "; ".join(diffs)))
    missing = sorted(set(range(len(names))) - seen)
    if a.emit_anchors:
        anchors = {str(c["rank"]): {"kind": c["kind"], "cell": c["cell"], "salt": c["salt"], "sx": c["sx"], "sz": c["sz"],
                                    "param": c["param"], "builder": f"{c['file']}.{c['method']}"} for c in calls}
        with open(a.emit_anchors, "w", encoding="utf-8") as f:
            json.dump(anchors, f, indent=1)
    print(f"{len(rows)} builders parsed, {drift} drift from the register table, not parsed: {missing}")
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"rows": rows, "drift": drift, "unparsed": missing}, f, indent=1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
