from __future__ import annotations

import json
from logging.handlers import RotatingFileHandler
import logging
import os
from pathlib import Path
import subprocess
import time
from urllib.error import URLError
from urllib.request import urlopen


PROJECT_ROOT = Path(__file__).resolve().parent.parent
RUNTIME_ROOT = PROJECT_ROOT / ".runtime"
TAILSCALE = Path(r"C:\Program Files\Tailscale\tailscale.exe")
PUBLIC_HOST = "jaspergers.tail3bd959.ts.net"
CHECK_INTERVAL_SECONDS = 15
ROUTES = {
    8443: {
        "proxy": "http://127.0.0.1:3308",
        "health": "http://127.0.0.1:3308/healthz",
        "name": "browser client",
    },
    10000: {
        "proxy": "http://127.0.0.1:3310",
        "health": "http://127.0.0.1:3310/status",
        "name": "multiplayer gateway",
    },
}


def configure_logging() -> logging.Logger:
    RUNTIME_ROOT.mkdir(parents=True, exist_ok=True)
    logger = logging.getLogger("funnel-watchdog")
    logger.setLevel(logging.INFO)
    handler = RotatingFileHandler(
        RUNTIME_ROOT / "funnel-watchdog.log",
        maxBytes=1_000_000,
        backupCount=3,
        encoding="utf-8",
    )
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    logger.addHandler(handler)
    return logger


def local_service_is_healthy(url: str) -> tuple[bool, str]:
    try:
        with urlopen(url, timeout=4) as response:
            response.read(1024)
            return 200 <= response.status < 300, f"HTTP {response.status}"
    except (OSError, URLError, TimeoutError) as exc:
        return False, f"{type(exc).__name__}: {exc}"


def run_tailscale(arguments: list[str]) -> subprocess.CompletedProcess[str]:
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    return subprocess.run(
        [str(TAILSCALE), *arguments],
        capture_output=True,
        text=True,
        timeout=20,
        check=False,
        creationflags=flags,
    )


def read_funnel_status() -> dict:
    result = run_tailscale(["funnel", "status", "--json"])
    if result.returncode != 0:
        detail = (result.stderr or result.stdout).strip()
        raise RuntimeError(f"tailscale funnel status failed ({result.returncode}): {detail}")
    return json.loads(result.stdout)


def route_is_correct(status: dict, port: int, expected_proxy: str) -> tuple[bool, str]:
    key = f"{PUBLIC_HOST}:{port}"
    web = status.get("Web", {}).get(key, {})
    proxy = web.get("Handlers", {}).get("/", {}).get("Proxy")
    allowed = status.get("AllowFunnel", {}).get(key) is True
    https_enabled = status.get("TCP", {}).get(str(port), {}).get("HTTPS") is True
    proxy_matches = isinstance(proxy, str) and proxy.rstrip("/") == expected_proxy.rstrip("/")
    if proxy_matches and allowed and https_enabled:
        return True, "configured"
    return False, f"proxy={proxy!r} allowed={allowed} https={https_enabled}"


def repair_route(port: int, proxy: str) -> tuple[bool, str]:
    result = run_tailscale(["funnel", "--bg", "--yes", f"--https={port}", proxy])
    detail = (result.stderr or result.stdout).strip()
    return result.returncode == 0, detail


def write_runtime_status(payload: dict) -> None:
    path = RUNTIME_ROOT / "funnel-watchdog-status.json"
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def check_once(logger: logging.Logger) -> None:
    report = {
        "checkedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "checkIntervalSeconds": CHECK_INTERVAL_SECONDS,
        "ownedPorts": sorted(ROUTES),
        "routes": {},
    }
    try:
        status = read_funnel_status()
        status_error = None
    except (OSError, subprocess.SubprocessError, ValueError, RuntimeError) as exc:
        status = {}
        status_error = str(exc)
        logger.error("Could not read Funnel status: %s", exc)

    repaired_any = False
    for port, route in ROUTES.items():
        healthy, health_detail = local_service_is_healthy(route["health"])
        correct, route_detail = route_is_correct(status, port, route["proxy"])
        route_report = {
            "name": route["name"],
            "localHealthy": healthy,
            "localHealthDetail": health_detail,
            "configured": correct,
            "configurationDetail": route_detail,
            "repairAttempted": False,
            "repairSucceeded": None,
        }
        report["routes"][str(port)] = route_report
        if correct:
            continue
        if not healthy:
            logger.warning(
                "Port %s route is missing or incorrect, but its local service is unhealthy (%s); repair deferred",
                port,
                health_detail,
            )
            continue
        route_report["repairAttempted"] = True
        logger.warning("Repairing Eaglercraft port %s route: %s", port, route_detail)
        try:
            succeeded, detail = repair_route(port, route["proxy"])
        except (OSError, subprocess.SubprocessError) as exc:
            succeeded, detail = False, str(exc)
        route_report["repairSucceeded"] = succeeded
        route_report["repairDetail"] = detail
        if succeeded:
            repaired_any = True
            logger.info("Repaired Eaglercraft port %s route", port)
        else:
            logger.error("Failed to repair Eaglercraft port %s route: %s", port, detail)

    if repaired_any:
        time.sleep(2)
        try:
            verified_status = read_funnel_status()
            for port, route in ROUTES.items():
                verified, detail = route_is_correct(verified_status, port, route["proxy"])
                report["routes"][str(port)]["verifiedAfterRepair"] = verified
                report["routes"][str(port)]["verificationDetail"] = detail
                if not verified:
                    logger.error("Port %s route did not verify after repair: %s", port, detail)
        except (OSError, subprocess.SubprocessError, ValueError, RuntimeError) as exc:
            report["verificationError"] = str(exc)
            logger.error("Could not verify Funnel status after repair: %s", exc)
    if status_error:
        report["statusError"] = status_error
    write_runtime_status(report)


def main() -> int:
    logger = configure_logging()
    if not TAILSCALE.is_file():
        logger.critical("Tailscale executable not found: %s", TAILSCALE)
        return 1
    logger.info(
        "Starting Eaglercraft Funnel watchdog; owns only ports %s and never resets Funnel globally",
        ", ".join(str(port) for port in sorted(ROUTES)),
    )
    while True:
        try:
            check_once(logger)
        except Exception:
            logger.exception("Unexpected watchdog check failure")
        time.sleep(CHECK_INTERVAL_SECONDS)


if __name__ == "__main__":
    raise SystemExit(main())
