"""Render + analyse a whole dump set in parallel.

    python batch.py grounds-w3                      # all dumps in SA/dumps/grounds-w3
    python batch.py grounds-w3 --only reg__17,cat__foo   # stems (prefix match)
    python batch.py grounds-w3 --no-render / --no-analyze --workers 12

Outputs: SA/renders/<set>/<stem>/..., SA/analysis/<set>/<stem>.json,
         SA/analysis/<set>/_summary.json (one line of key metrics per dump + timings),
         SA/renders/<set>/_batch.json (timings, failures).
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import time
import traceback
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

SA = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit"


def work(args):
    path, do_render, do_analyze = args
    import analyze
    import render
    from jsd import read_jsd
    res = {"file": os.path.basename(path)}
    t0 = time.time()
    try:
        d = read_jsd(path)
        res["id"] = d.header.get("id")
        res["size"] = d.header.get("size")
        g = d.header.get("grounds") or {}
        if g:
            res["number"] = g.get("number")
        if do_render:
            t = time.time()
            od, info = render.render_dump(d)
            res["render_s"] = round(time.time() - t, 2)
            res["files"] = len(info["files"])
            res["floors"] = info.get("floorLevels")
        if do_analyze:
            t = time.time()
            a = analyze.analyze(d)
            p = analyze.out_path(d)
            with open(p, "w", encoding="utf-8") as f:
                json.dump(a, f, indent=1)
            res["analyze_s"] = round(time.time() - t, 2)
            c = a["components"]
            res["summary"] = {
                "verdict": a["autoVerdict"]["verdict"], "why": a["autoVerdict"]["reasons"],
                "solid": a["counts"]["solid"], "floating": c["floating"], "floatingBlocks": c["floatingBlocks"],
                "masses": c["floatingMasses"], "fragments": c["floatingFragments"], "tiny": c["floatingTiny"],
                "interiorAccessible": a["enclosed"]["interiorAccessibleFraction"],
                "sealedCells": a["enclosed"]["sealedCells"],
                "unreachableEnclosedCells": a["enclosed"].get("unreachableEnclosedCells", 0),
                "chests": a["counts"]["chests"], "unreachable": a["interactables"]["unreachable"],
                "ladderProblems": a["ladders"]["problemRuns"], "stairsBlocked": a["stairs"]["blocked"],
                "doorProblems": a["doors"]["problem"], "darkInterior": a["light"]["interiorDarkFraction"],
                "rooms": a["rooms"]["count"], "emptyShells": a["rooms"]["emptyShells"],
                "valuables": a["valuables"]["total"],
                "valuableIds": {v["name"]: v["count"] for v in a["valuables"]["byId"]},
                "plinth": a["reach"].get("plinthLayers", 0),
                "topSuspects": [f"{s['kind']}: {s['reason'][:120]}" for s in a["suspects"][:5]],
            }
    except Exception as e:  # keep going, report
        res["error"] = f"{type(e).__name__}: {e}"
        res["trace"] = traceback.format_exc()[-1500:]
    res["total_s"] = round(time.time() - t0, 2)
    return res


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("set")
    ap.add_argument("--only", default="")
    ap.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 4) - 2))
    ap.add_argument("--no-render", action="store_true")
    ap.add_argument("--no-analyze", action="store_true")
    ap.add_argument("--skip-iso", action="store_true",
                    help="skip <stem>iso.jsd structure-only dumps (natural dumps already render str_ views)")
    a = ap.parse_args(argv)
    files = sorted(glob.glob(os.path.join(SA, "dumps", a.set, "*.jsd")))
    if a.skip_iso:
        files = [f for f in files if not os.path.basename(f)[:-4].endswith("iso")]
    if a.only:
        pre = [s.strip() for s in a.only.split(",") if s.strip()]
        files = [f for f in files if any(os.path.basename(f).startswith(p) for p in pre)]
    # biggest first for better load balance
    files.sort(key=lambda f: -os.path.getsize(f))
    t0 = time.time()
    jobs = [(f, not a.no_render, not a.no_analyze) for f in files]
    results = []
    with Pool(a.workers) as pool:
        for i, r in enumerate(pool.imap_unordered(work, jobs)):
            results.append(r)
            if "error" in r:
                print(f"[{i + 1}/{len(jobs)}] {r['file']} ERROR {r['error']}", flush=True)
            elif (i + 1) % 25 == 0 or i + 1 == len(jobs):
                print(f"[{i + 1}/{len(jobs)}] {time.time() - t0:.0f}s", flush=True)
    wall = time.time() - t0
    results.sort(key=lambda r: (r.get("number") or 0, r["file"]))
    meta = {"set": a.set, "dumps": len(files), "workers": a.workers, "wall_s": round(wall, 1),
            "render_s_sum": round(sum(r.get("render_s", 0) for r in results), 1),
            "analyze_s_sum": round(sum(r.get("analyze_s", 0) for r in results), 1),
            "render_s_max": max((r.get("render_s", 0) for r in results), default=0),
            "analyze_s_max": max((r.get("analyze_s", 0) for r in results), default=0),
            "errors": [r for r in results if "error" in r]}
    if not a.no_render:
        os.makedirs(os.path.join(SA, "renders", a.set), exist_ok=True)
        with open(os.path.join(SA, "renders", a.set, "_batch.json"), "w", encoding="utf-8") as f:
            json.dump({**meta, "results": [{k: v for k, v in r.items() if k != "summary"} for r in results]}, f, indent=1)
    if not a.no_analyze:
        os.makedirs(os.path.join(SA, "analysis", a.set), exist_ok=True)
        with open(os.path.join(SA, "analysis", a.set, "_summary.json"), "w", encoding="utf-8") as f:
            json.dump({**meta, "results": results}, f, indent=1)
    print(json.dumps({k: v for k, v in meta.items() if k != "errors"}), "errors:", len(meta["errors"]))


if __name__ == "__main__":
    main()
