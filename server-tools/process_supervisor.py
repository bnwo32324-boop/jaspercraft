from __future__ import annotations

import contextlib
import logging
import os
from pathlib import Path
import subprocess
import sys
import time

PROJECT_ROOT = Path(__file__).resolve().parent.parent
RUNTIME_ROOT = PROJECT_ROOT / ".runtime"
TARGETS = {
    "site": PROJECT_ROOT / "server-tools" / "site_server.py",
    "gateway": PROJECT_ROOT / "server-tools" / "gateway.py",
    "funnel": PROJECT_ROOT / "server-tools" / "funnel_watchdog.py",
}

def run(target_name: str) -> int:
    if target_name not in TARGETS:
        raise SystemExit("usage: process_supervisor.py site|gateway|funnel")
    RUNTIME_ROOT.mkdir(parents=True, exist_ok=True)
    stop_path = RUNTIME_ROOT / f"{target_name}-supervisor-stop.request"
    log_path = RUNTIME_ROOT / f"{target_name}-supervisor.log"
    logging.basicConfig(filename=log_path, level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    target = TARGETS[target_name]
    if not target.is_file():
        raise FileNotFoundError(target)
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    while not stop_path.exists():
        child_log_path = RUNTIME_ROOT / f"{target_name}-supervisor-child.log"
        with child_log_path.open("ab", buffering=0) as child_log:
            child = subprocess.Popen(
                [sys.executable, "-u", str(target)],
                cwd=PROJECT_ROOT,
                stdin=subprocess.DEVNULL,
                stdout=child_log,
                stderr=subprocess.STDOUT,
                creationflags=flags,
            )
            logging.info("Started %s child PID %s", target_name, child.pid)
            while child.poll() is None and not stop_path.exists():
                time.sleep(1.0)
            if stop_path.exists() and child.poll() is None:
                if target_name == "gateway":
                    (RUNTIME_ROOT / "game-gateway-stop.request").write_text("supervisor stop\n", encoding="utf-8")
                    for _ in range(120):
                        if child.poll() is not None:
                            break
                        time.sleep(1.0)
                else:
                    child.terminate()
                    with contextlib.suppress(subprocess.TimeoutExpired):
                        child.wait(timeout=15)
                if child.poll() is None:
                    child.kill()
                    child.wait(timeout=15)
            code = child.poll()
            if code is None:
                code = child.wait()
            logging.info("%s child exited with code %s", target_name, code)
        if not stop_path.exists():
            logging.warning("Restarting %s child in two seconds", target_name)
            time.sleep(2.0)
    logging.info("Supervisor stop marker present; %s supervisor exiting", target_name)
    return 0

if __name__ == "__main__":
    raise SystemExit(run(sys.argv[1] if len(sys.argv) > 1 else ""))
