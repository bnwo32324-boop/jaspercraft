"""Structure-audit capture driver: runs a throwaway Paper 1.12.2 test server with the JasprHorrorBiomes jar
under test + the capture harness, and captures structures exactly as the real generator builds them.

    python capture.py --discover --jar JARS/base323.jar --sites SA/sites.json [--slot N]
    python capture.py --jar JARS/base323.jar --sites SA/sites.json --set base323 [--ids reg:1,cat:foo@a,dun:*] [--slot N]

Every run: copies SA/testserver-template to SA/runs/<set>-<slot>-<n>/server (fresh world), installs the jar
under test as plugins/JasprHorrorBiomes.jar and SA/harness/build/JasprCaptureHarness.jar, writes the job file,
runs Java 17 (-Xmx2G, headless, port 25590+slot on 127.0.0.1) with a timeout, streams the console to
runs/<run>/console.log, and writes dumps to SA/dumps/<set>/<id>__<variant>.jsd (+ ...iso.jsd).
Results: runs/<run>/results.jsonl (one line per site) and runs/<run>/run.json (summary).
A per-slot lock (runs/slot-<N>.lock, an OS file lock) serialises concurrent callers on the same slot.

--ids tokens: exact id (all variants), id@variant, or a prefix ending in * (e.g. cat:*). Default: all sites.
Safety: never touches the live server; no internet (Paperclip runs from the copied cache; JVM proxies point at a
dead local port so any download attempt fails, and the run is aborted if the console mentions downloading).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import msvcrt
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time

import psutil

SA = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\candidate\structure-audit"
TEMPLATE = os.path.join(SA, "testserver-template")
RUNS = os.path.join(SA, "runs")
DUMPS = os.path.join(SA, "dumps")
HARNESS_JAR = os.path.join(SA, "harness", "build", "JasprCaptureHarness.jar")
JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot\bin\java.exe"
TREE = r"C:\Users\AM\Documents\Eaglercraft-1.12.2-Tailscale\server\custom-plugins\JasprHorrorBiomes"
SEED = "3127727864271777472"
FORBIDDEN_PORTS = {25565, 3200, 3199, 3210, 3299, 3308, 3310}
DOWNLOAD_PAT = re.compile(r"(?i)\bdownloading\b|paperclip.*download|could not download")


def md5(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


class SlotLock:
    """OS-level lock on runs/slot-<n>.lock; released automatically if this process dies."""

    def __init__(self, slot):
        self.path = os.path.join(RUNS, f"slot-{slot}.lock")
        self.f = None

    def __enter__(self):
        os.makedirs(RUNS, exist_ok=True)
        self.f = open(self.path, "a+b")
        waited = 0
        while True:
            try:
                self.f.seek(0)
                msvcrt.locking(self.f.fileno(), msvcrt.LK_NBLCK, 1)
                break
            except OSError:
                if waited % 30 == 0:
                    print(f"[capture] slot busy ({self.path}), waiting...", flush=True)
                time.sleep(1)
                waited += 1
        self.f.seek(0)
        self.f.truncate()
        self.f.write(f"pid={os.getpid()} since={time.strftime('%Y-%m-%d %H:%M:%S')}\n".encode())
        self.f.flush()
        return self

    def __exit__(self, *exc):
        try:
            self.f.seek(0)
            msvcrt.locking(self.f.fileno(), msvcrt.LK_UNLCK, 1)
        except OSError:
            pass
        self.f.close()


def port_free(port):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        s.bind(("127.0.0.1", port))
        return True
    except OSError:
        return False
    finally:
        s.close()


def select_sites(all_sites, ids):
    if not ids:
        return list(all_sites)
    out, seen = [], set()
    for tok in [t.strip() for t in ids.split(",") if t.strip()]:
        hit = False
        for s in all_sites:
            key = (s["id"], s["variant"])
            if tok.endswith("*"):
                ok = s["id"].startswith(tok[:-1])
            elif "@" in tok:
                ok = f"{s['id']}@{s['variant']}" == tok
            else:
                ok = s["id"] == tok
            if ok:
                hit = True
                if key not in seen:
                    seen.add(key)
                    out.append(s)
        if not hit:
            raise SystemExit(f"--ids: no site matches {tok!r}")
    return out


def next_run_dir(name, slot):
    os.makedirs(RUNS, exist_ok=True)
    n = 1
    pat = re.compile(re.escape(f"{name}-{slot}-") + r"(\d+)$")
    for d in os.listdir(RUNS):
        m = pat.match(d)
        if m:
            n = max(n, int(m.group(1)) + 1)
    return os.path.join(RUNS, f"{name}-{slot}-{n}")


def prepare_server(run_dir, jar, slot):
    server = os.path.join(run_dir, "server")
    shutil.copytree(TEMPLATE, server)
    # the template's boundary file is read-only; the copy stays read-only too (the plugin never rewrites it)
    props = os.path.join(server, "server.properties")
    with open(props, encoding="utf-8") as f:
        text = f.read()
    port = 25590 + slot
    text = re.sub(r"(?m)^server-port=.*$", f"server-port={port}", text)
    with open(props, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    plugins = os.path.join(server, "plugins")
    shutil.copy2(jar, os.path.join(plugins, "JasprHorrorBiomes.jar"))
    shutil.copy2(HARNESS_JAR, os.path.join(plugins, "JasprCaptureHarness.jar"))
    os.makedirs(os.path.join(plugins, "JasprCaptureHarness"), exist_ok=True)
    return server, port


def run_server(server, run_dir, timeout, job_path_note=""):
    log_path = os.path.join(run_dir, "console.log")
    cmd = [JAVA, "-Xmx2G", "-Xms1G", "-XX:+UseG1GC",
           "-Dcom.mojang.eula.agree=true", "-DIReallyKnowWhatIAmDoingISwear=true",
           "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=9", "-Dhttps.proxyHost=127.0.0.1", "-Dhttps.proxyPort=9",
           "-Djava.net.preferIPv4Stack=true", "-Dfile.encoding=UTF-8",
           "-Dterminal.jline=false", "-Dterminal.ansi=false", "-Djline.terminal=jline.UnsupportedTerminal",
           "-jar", "paper-1.12.2.jar", "nogui"]
    t0 = time.time()
    peak = {"rss": 0, "private": 0}
    state = {"download": None}
    with open(log_path, "w", encoding="utf-8", errors="replace") as log:
        log.write(f"# {' '.join(cmd)}\n# cwd {server}\n")
        log.flush()
        proc = subprocess.Popen(cmd, cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)

        def pump():
            for raw in iter(proc.stdout.readline, b""):
                line = raw.decode("utf-8", "replace")
                log.write(line)
                log.flush()
                if state["download"] is None and DOWNLOAD_PAT.search(line):
                    state["download"] = line.strip()
                if "CAPTURE_SITE " in line or "CAPTURE_DISCOVER done" in line or "CAPTURE_END" in line \
                        or "SEVERE" in line or "Exception" in line:
                    print("   " + line.rstrip()[:300], flush=True)

        th = threading.Thread(target=pump, daemon=True)
        th.start()
        ps = psutil.Process(proc.pid)
        reason = None
        while proc.poll() is None:
            try:
                mi = ps.memory_info()
                peak["rss"] = max(peak["rss"], mi.rss)
                peak["private"] = max(peak["private"], getattr(mi, "private", 0))
            except psutil.Error:
                pass
            if state["download"]:
                reason = "download attempt: " + state["download"]
                break
            if time.time() - t0 > timeout:
                reason = f"timeout after {timeout:.0f}s"
                break
            time.sleep(0.5)
        if reason:
            print(f"[capture] KILLING test server: {reason}", flush=True)
            try:
                for c in ps.children(recursive=True):
                    c.kill()
                ps.kill()
            except psutil.Error:
                pass
        try:
            proc.stdin.close()
        except OSError:
            pass
        proc.wait(timeout=60)
        th.join(timeout=10)
    return {"exitCode": proc.returncode, "seconds": round(time.time() - t0, 1), "killed": reason,
            "peakRssMB": peak["rss"] >> 20, "peakPrivateMB": peak["private"] >> 20, "log": log_path}


def cleanup(server, keep):
    if keep:
        return
    for rel in ("cache", "paper-1.12.2.jar", "lib", "world", "world_nether", "world_the_end", "jaspr_backrooms",
                "plugins/JasprHorrorBiomes.jar", "plugins/JasprCaptureHarness.jar"):
        p = os.path.join(server, rel)
        if os.path.isdir(p):
            shutil.rmtree(p, ignore_errors=True, onerror=None)
            if os.path.exists(p):  # read-only boundary file
                for root, _, files in os.walk(p):
                    for fn in files:
                        os.chmod(os.path.join(root, fn), 0o666)
                shutil.rmtree(p, ignore_errors=True)
        elif os.path.isfile(p):
            os.remove(p)


def read_results(path):
    out = []
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    out.append(json.loads(line))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--jar", required=True, help="JasprHorrorBiomes jar under test (must contain CaptureHook)")
    ap.add_argument("--sites", default=os.path.join(SA, "sites.json"))
    ap.add_argument("--ids", default="", help="comma list: id | id@variant | prefix*")
    ap.add_argument("--set", default="", help="capture set name (dumps/<set>/)")
    ap.add_argument("--slot", type=int, default=0, choices=range(10))
    ap.add_argument("--discover", action="store_true", help="(re)generate --sites instead of capturing")
    ap.add_argument("--tree", default=TREE,
                    help="--discover: source tree the jar was built from (the register builders' own anchor calls are "
                         "parsed from it to cross-check Megaliths.located())")
    ap.add_argument("--max-radius", type=int, default=100000)
    ap.add_argument("--per-site", type=int, default=2)
    ap.add_argument("--margin", type=int, default=8)
    ap.add_argument("--timeout", type=float, default=0, help="seconds (default: estimated from the job)")
    ap.add_argument("--shim", default="real", choices=["real", "stub", "off"],
                    help="JasprApocalypse stand-in for rareWeapon (real = live-faithful items)")
    ap.add_argument("--load-budget-ms", type=int, default=200)
    ap.add_argument("--settle-max-ticks", type=int, default=1200)
    ap.add_argument("--keep", action="store_true", help="keep the run's server copy (world, jars)")
    ap.add_argument("--dry-run", action="store_true", help="prepare the run dir and job, do not start Java")
    a = ap.parse_args()

    jar = os.path.abspath(a.jar)
    if not os.path.isfile(jar):
        raise SystemExit(f"no such jar {jar}")
    if not os.path.isfile(HARNESS_JAR):
        raise SystemExit(f"harness not built: {HARNESS_JAR} (run harness/build.sh)")
    import zipfile
    with zipfile.ZipFile(jar) as z:
        if "chat/jaspr/biomes/CaptureHook.class" not in z.namelist():
            raise SystemExit(f"{jar} has no CaptureHook: the capture would have an empty mask")
    port = 25590 + a.slot
    if port in FORBIDDEN_PORTS:
        raise SystemExit("forbidden port")

    if a.discover:
        name, sites = "discover", []
    else:
        if not a.set:
            raise SystemExit("--set is required for a capture")
        with open(a.sites, encoding="utf-8") as f:
            sites = select_sites(json.load(f)["sites"], a.ids)
        name = a.set

    with SlotLock(a.slot):
        if not port_free(port):
            raise SystemExit(f"port {port} is in use (a test server still running on slot {a.slot}?)")
        run_dir = next_run_dir(name, a.slot)
        os.makedirs(run_dir)
        server, port = prepare_server(run_dir, jar, a.slot)
        results_path = os.path.join(run_dir, "results.jsonl")
        out_dir = os.path.join(DUMPS, a.set) if not a.discover else ""
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        job = {"mode": "discover" if a.discover else "capture", "set": a.set or "discover", "seed": SEED,
               "run": os.path.basename(run_dir), "outDir": out_dir, "resultsFile": results_path,
               "jar": {"name": os.path.basename(jar), "md5": md5(jar), "path": jar},
               "shim": a.shim, "startDelayTicks": 60, "loadBudgetMs": a.load_budget_ms,
               "settleMinTicks": 5, "settleMaxTicks": a.settle_max_ticks, "shutdown": True}
        if a.discover:
            job["sitesOut"] = os.path.abspath(a.sites)
            job["discover"] = {"margin": a.margin, "perSite": a.per_site, "maxRadius": a.max_radius}
            anchors = os.path.join(run_dir, "builder-anchors.json")
            r = subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "register_drift.py"),
                                "--tree", a.tree, "--emit-anchors", anchors], capture_output=True, text=True)
            print("[capture] register_drift: " + (r.stdout.strip().splitlines() or ["?"])[-1], flush=True)
            if r.returncode == 0 and os.path.isfile(anchors):
                with open(anchors, encoding="utf-8") as f:
                    job["discover"]["builderAnchors"] = json.load(f)
            else:
                print("[capture] WARNING: builder anchors unavailable; register sites mirror located() only\n" + r.stderr,
                      flush=True)
            timeout = a.timeout or 1800
        else:
            job["sites"] = sites
            chunks = sum(s["chunks"]["loadCount"] for s in sites)
            timeout = a.timeout or (240 + 0.35 * chunks + 15 * len(sites))
        with open(os.path.join(server, "plugins", "JasprCaptureHarness", "job.json"), "w", encoding="utf-8") as f:
            json.dump(job, f, indent=1)
        print(f"[capture] run {run_dir} slot={a.slot} port={port} mode={job['mode']} sites={len(sites)} "
              f"timeout={timeout:.0f}s jar={os.path.basename(jar)} md5={job['jar']['md5']}", flush=True)
        if a.dry_run:
            return 0
        info = run_server(server, run_dir, timeout)
        results = read_results(results_path)
        end = next((r for r in results if r.get("type") == "end"), None)
        site_lines = [r for r in results if r.get("type") == "site"]
        summary = {"run": run_dir, "args": vars(a), "jar": job["jar"], "port": port, "process": info,
                   "sitesRequested": len(sites), "sitesReported": len(site_lines),
                   "sitesOk": sum(1 for r in site_lines if r.get("ok")), "end": end}
        with open(os.path.join(run_dir, "run.json"), "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=1)
        cleanup(server, a.keep)
        for r in site_lines:
            d = r.get("dump", {})
            print(f"  {r['id']}@{r['variant']:<2} ok={r.get('ok')} mask={d.get('maskSolid', '-')}/{d.get('maskAir', '-')} "
                  f"tiles={d.get('tiles', {})} gen={r.get('chunksGenerated')} t={r.get('timing', {}).get('totalMs')}ms"
                  + (f" ERROR={r['error']}" if r.get("error") else ""), flush=True)
        print(f"[capture] exit={info['exitCode']} {info['seconds']}s peakRSS={info['peakRssMB']}MB killed={info['killed']} "
              f"end={json.dumps(end) if end else None}", flush=True)
        ok = info["killed"] is None and end is not None and end.get("ok")
        if not a.discover:
            ok = ok and summary["sitesReported"] == len(sites)
        return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
