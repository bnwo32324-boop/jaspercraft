"""Compare analyze.py auto-verdicts of a grounds set with the handoff's Appendix A automated verdicts.

    python compare_appendix.py [grounds-w3]
Reads SA/analysis/<set>/_summary.json (written by batch.py) and the handoff Appendix A
(C:\\Users\\AM\\Desktop\\JASPERCRAFT_HANDOFF.txt), prints a confusion matrix and the disagreements, and
writes SA/analysis/<set>/_appendixA_compare.json.
"""
import collections
import json
import os
import re
import sys

SA = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit"
HANDOFF = r"C:\Users\AM\Desktop\JASPERCRAFT_HANDOFF.txt"
LABELS = ["good", "passable", "defective", "severely incomplete"]


def appendix_verdicts():
    txt = open(HANDOFF, encoding="utf-8", errors="replace").read()
    app = txt[txt.index("APPENDIX A. ALL 317"):]
    out = {}
    for line in app.splitlines():
        m = re.match(r"#(\d+)\s+(.*?)\s+(good|passable|defective|severely incomplete)\s*$", line)
        if m:
            out[int(m.group(1))] = m.group(3)
    return out


def main(set_name="grounds-w3", verbose=True):
    verd = appendix_verdicts()
    s = json.load(open(os.path.join(SA, "analysis", set_name, "_summary.json"), encoding="utf-8"))
    mine = {r["number"]: r["summary"]["verdict"] for r in s["results"] if "summary" in r}
    cm = collections.Counter((verd[n], mine[n]) for n in verd if n in mine)
    exact = sum(cm[(l_, l_)] for l_ in LABELS)
    rank = {l_: i for i, l_ in enumerate(LABELS)}
    within1 = sum(v for (a, b), v in cm.items() if abs(rank[a] - rank[b]) <= 1)
    dis = []
    for r in s["results"]:
        n = r.get("number")
        if n in verd and "summary" in r and r["summary"]["verdict"] != verd[n]:
            dis.append({"number": n, "id": r["id"], "appendixA": verd[n], "auto": r["summary"]["verdict"],
                        "why": r["summary"]["why"]})
    res = {"appendixA": dict(collections.Counter(verd.values())), "auto": dict(collections.Counter(mine.values())),
           "matrix_rows_appendix_cols_auto": {a: {b: cm[(a, b)] for b in LABELS} for a in LABELS},
           "exact": exact, "withinOneClass": within1, "n": len(verd), "disagreements": dis}
    with open(os.path.join(SA, "analysis", set_name, "_appendixA_compare.json"), "w", encoding="utf-8") as f:
        json.dump(res, f, indent=1)
    if verbose:
        print("appendix A:", res["appendixA"])
        print("auto      :", res["auto"])
        print(" " * 22 + "".join(f"{l_[:10]:>12}" for l_ in LABELS))
        for a in LABELS:
            print(f"{a:22}" + "".join(f"{cm[(a, b)]:12d}" for b in LABELS))
        print(f"exact {exact}/{len(verd)}  within one class {within1}/{len(verd)}")
    return res


if __name__ == "__main__":
    main(*(sys.argv[1:2] or ["grounds-w3"]))
