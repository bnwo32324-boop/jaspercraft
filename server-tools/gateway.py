from __future__ import annotations

import asyncio
import contextlib
import ctypes
import hashlib
import json
import logging
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
from urllib.parse import urlsplit

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SERVER_ROOT = PROJECT_ROOT / "server"
RUNTIME_ROOT = PROJECT_ROOT / ".runtime"
PRIVATE_ROOT = PROJECT_ROOT / "private"
CONFIG_PATH = Path(__file__).resolve().parent / "gateway-config.json"
PID_PATH = RUNTIME_ROOT / "paper-server.pid"
PAPER_IDENTITY_PATH = RUNTIME_ROOT / "paper-server.process.json"
GATEWAY_PID_PATH = RUNTIME_ROOT / "game-gateway.pid"
GATEWAY_STOP_PATH = RUNTIME_ROOT / "game-gateway-stop.request"
SERVER_STOP_PATH = RUNTIME_ROOT / "game-server-stop.request"
SERVER_READY_PATH = RUNTIME_ROOT / "game-server-ready"
SSO_READY_PATH = RUNTIME_ROOT / "jaspr-sso-ready"
SSO_CONNECTIONS = set()
LAST_FAILURE = {"code": None, "at": None}
MAINTENANCE_PATH = RUNTIME_ROOT / "maintenance-mode"
OWNER_BOOTSTRAP_PATH = PRIVATE_ROOT / "owner-bootstrap.json"
OWNER_BOOTSTRAP_DONE_PATH = RUNTIME_ROOT / "owner-bootstrap.done"
LOG_PATH = RUNTIME_ROOT / "game-gateway.log"
PAPER_LOG_PATH = RUNTIME_ROOT / "paper-console.log"

RUNTIME_ROOT.mkdir(parents=True, exist_ok=True)
PRIVATE_ROOT.mkdir(parents=True, exist_ok=True)
logging.basicConfig(filename=LOG_PATH, level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def log_event(event: str, **details) -> None:
    """Write bounded structured diagnostics without request targets or credentials."""
    safe = {"event": re.sub(r"[^a-zA-Z0-9_.-]", "", event)[:80]}
    for key, value in list(details.items())[:24]:
        clean_key = re.sub(r"[^a-zA-Z0-9_.-]", "", str(key))[:80]
        if not clean_key or re.search(r"ticket|token|cookie|authorization|password|secret", clean_key, re.I):
            continue
        if value is None or isinstance(value, (bool, int, float)):
            safe[clean_key] = value
        else:
            safe[clean_key] = re.sub(r"[\x00-\x1f\x7f]", " ", str(value))[:240]
    logging.info("%s", json.dumps(safe, separators=(",", ":"), sort_keys=True))


def record_failure(connection_id: str, code: str) -> None:
    LAST_FAILURE["code"] = code
    LAST_FAILURE["at"] = int(time.time() * 1000)
    log_event("jaspercraft.socket.rejected", connectionId=connection_id, code=code, activeConnections=len(SSO_CONNECTIONS))


def sso_ready() -> bool:
    try:
        if SSO_READY_PATH.stat().st_size > 32:
            return False
        value = SSO_READY_PATH.read_text(encoding="ascii").strip()
        if not re.fullmatch(r"\d{13}", value):
            return False
        age = int(time.time() * 1000) - int(value)
        return 0 <= age <= 5000
    except (OSError, ValueError, UnicodeError):
        return False


def process_is_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False
    kernel32 = ctypes.windll.kernel32
    handle = kernel32.OpenProcess(0x1000, False, pid)
    if not handle:
        return False
    try:
        exit_code = ctypes.c_ulong()
        return bool(kernel32.GetExitCodeProcess(handle, ctypes.byref(exit_code))) and exit_code.value == 259
    finally:
        kernel32.CloseHandle(handle)


def paper_process_is_alive(pid: int, java_path: Path) -> bool:
    """Do not confuse a recycled PID with the Paper server after a restart."""
    if not process_is_alive(pid):
        return False
    if os.name != "nt":
        return True

    class FileTime(ctypes.Structure):
        _fields_ = [("low", ctypes.c_uint32), ("high", ctypes.c_uint32)]

    kernel32 = ctypes.windll.kernel32
    handle = kernel32.OpenProcess(0x1000, False, pid)
    if not handle:
        return False
    try:
        executable = ctypes.create_unicode_buffer(32768)
        length = ctypes.c_ulong(len(executable))
        if not kernel32.QueryFullProcessImageNameW(handle, 0, executable, ctypes.byref(length)):
            return False
        if os.path.normcase(executable.value) != os.path.normcase(str(java_path)):
            return False

        if not PAPER_IDENTITY_PATH.exists():
            return True  # Paper started by an older gateway, before identity files existed.
        creation = FileTime()
        exit_time = FileTime()
        kernel_time = FileTime()
        user_time = FileTime()
        if not kernel32.GetProcessTimes(
            handle, ctypes.byref(creation), ctypes.byref(exit_time),
            ctypes.byref(kernel_time), ctypes.byref(user_time),
        ):
            return False
        recorded = json.loads(PAPER_IDENTITY_PATH.read_text(encoding="utf-8"))
        created = (creation.high << 32) | creation.low
        return recorded.get("pid") == pid and recorded.get("creationTime") == created
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        return False
    finally:
        kernel32.CloseHandle(handle)


def write_paper_identity(pid: int) -> None:
    if os.name != "nt":
        return

    class FileTime(ctypes.Structure):
        _fields_ = [("low", ctypes.c_uint32), ("high", ctypes.c_uint32)]

    kernel32 = ctypes.windll.kernel32
    handle = kernel32.OpenProcess(0x1000, False, pid)
    if not handle:
        raise OSError(f"Could not inspect Paper process {pid}")
    try:
        creation = FileTime()
        exit_time = FileTime()
        kernel_time = FileTime()
        user_time = FileTime()
        if not kernel32.GetProcessTimes(
            handle, ctypes.byref(creation), ctypes.byref(exit_time),
            ctypes.byref(kernel_time), ctypes.byref(user_time),
        ):
            raise OSError(f"Could not read Paper process creation time for {pid}")
        created = (creation.high << 32) | creation.low
        pending_identity = PAPER_IDENTITY_PATH.with_suffix(".tmp")
        pending_identity.write_text(
            json.dumps({"pid": pid, "creationTime": created}), encoding="utf-8"
        )
        pending_identity.replace(PAPER_IDENTITY_PATH)
    finally:
        kernel32.CloseHandle(handle)


class ServerManager:
    def __init__(self, config: dict) -> None:
        self.config = config
        self.lock = asyncio.Lock()
        self.process: subprocess.Popen | None = None
        self.paper_log = None
        self.last_start_attempt = 0.0
        self.bootstrap_in_progress = False

    async def backend_available(self) -> bool:
        try:
            _, writer = await asyncio.wait_for(
                asyncio.open_connection(self.config["backendHost"], int(self.config["backendPort"])),
                timeout=1.0,
            )
            writer.close()
            with contextlib.suppress(Exception):
                await writer.wait_closed()
            return True
        except (OSError, asyncio.TimeoutError):
            return False

    def recorded_process_alive(self) -> bool:
        if self.process is not None and self.process.poll() is None:
            return True
        if not PID_PATH.exists():
            return False
        try:
            pid = int(PID_PATH.read_text(encoding="utf-8").strip())
        except (OSError, ValueError):
            return False
        return paper_process_is_alive(pid, Path(self.config["javaPath"]))

    async def state(self) -> str:
        if await self.backend_available() and SERVER_READY_PATH.is_file():
            return "running"
        if self.recorded_process_alive():
            return "starting"
        if MAINTENANCE_PATH.is_file():
            return "maintenance"
        return "stopped"

    async def start_if_needed(self) -> str:
        async with self.lock:
            current = await self.state()
            if current != "stopped":
                return current
            now = time.monotonic()
            if now - self.last_start_attempt < 5.0:
                return "stopped"
            self.last_start_attempt = now
            java_path = Path(self.config["javaPath"])
            # Same installed Paper build, with its per-player movement radius
            # bug corrected. The stock Paperclip and cached JAR remain intact.
            paper_path = SERVER_ROOT / "jaspr-paper-clientbudget.jar"
            if not java_path.is_file():
                raise FileNotFoundError(f"Java runtime not found: {java_path}")
            if not paper_path.is_file():
                raise FileNotFoundError(f"Paper server not found: {paper_path}")
            for marker in (SERVER_STOP_PATH, SERVER_READY_PATH, PAPER_IDENTITY_PATH):
                with contextlib.suppress(FileNotFoundError):
                    marker.unlink()
            command = [
                str(java_path),
                f"-Xms{self.config['minimumMemory']}",
                f"-Xmx{self.config['maximumMemory']}",
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=200",
                "-Dfile.encoding=UTF-8",
                # Let slow clients recover; genuine keepalive IDs remain checked.
                "-Dpaper.playerconnection.keepalive=90",
                "-Dcom.mojang.eula.agree=true",
                "-jar",
                str(paper_path),
                "nogui",
            ]
            self.paper_log = PAPER_LOG_PATH.open("ab", buffering=0)
            flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
            self.process = subprocess.Popen(
                command,
                cwd=SERVER_ROOT,
                stdin=subprocess.PIPE,
                stdout=self.paper_log,
                stderr=subprocess.STDOUT,
                creationflags=flags,
            )
            PID_PATH.write_text(str(self.process.pid), encoding="utf-8")
            write_paper_identity(self.process.pid)
            logging.info("Started always-on Paper server with PID %s", self.process.pid)
            return "starting"

    async def ensure_ready(self) -> bool:
        state = await self.start_if_needed()
        if state == "maintenance":
            return False
        deadline = time.monotonic() + int(self.config["startupTimeoutSeconds"])
        while time.monotonic() < deadline:
            if await self.backend_available() and SERVER_READY_PATH.is_file():
                return True
            if not self.recorded_process_alive():
                logging.error("Paper exited before opening the backend port")
                return False
            await asyncio.sleep(0.5)
        logging.error("Paper did not become ready before the startup timeout")
        return False

    def send_console_command(self, command: str) -> bool:
        if self.process is None or self.process.poll() is not None or self.process.stdin is None:
            return False
        try:
            self.process.stdin.write((command + "\n").encode("utf-8"))
            self.process.stdin.flush()
            return True
        except (BrokenPipeError, OSError):
            logging.exception("Could not send a Paper console command")
            return False

    async def maybe_bootstrap_owner(self) -> None:
        if self.bootstrap_in_progress or OWNER_BOOTSTRAP_DONE_PATH.exists() or not OWNER_BOOTSTRAP_PATH.exists():
            return
        if self.process is None or self.process.poll() is not None or self.process.stdin is None:
            return
        self.bootstrap_in_progress = True
        try:
            payload = json.loads(OWNER_BOOTSTRAP_PATH.read_text(encoding="utf-8"))
            username = str(payload.get("username", ""))
            password = str(payload.get("password", ""))
            if re.fullmatch(r"[A-Za-z0-9_]{3,16}", username) is None:
                raise ValueError("Owner username is invalid")
            if re.fullmatch(r"[!-~]{12,30}", password) is None:
                raise ValueError("Owner password is invalid")
            await asyncio.sleep(2.0)
            if not self.send_console_command(f"authme register {username} {password}"):
                return
            if not self.send_console_command(f"op {username}"):
                return
            await asyncio.sleep(3.0)
            OWNER_BOOTSTRAP_DONE_PATH.write_text(
                json.dumps({"username": username, "appliedUnixTime": int(time.time())}), encoding="utf-8"
            )
            OWNER_BOOTSTRAP_PATH.unlink(missing_ok=True)
            logging.info("Bootstrapped password-protected owner account %s", username)
        except Exception:
            logging.exception("Owner bootstrap failed")
        finally:
            self.bootstrap_in_progress = False

    async def request_graceful_stop(self) -> bool:
        if not self.recorded_process_alive():
            return True
        SERVER_STOP_PATH.write_text("stop\n", encoding="utf-8")
        for _ in range(90):
            self.reap()
            if not self.recorded_process_alive():
                return True
            await asyncio.sleep(1.0)
        logging.error("Paper did not stop within 90 seconds")
        return False

    def reap(self) -> None:
        if self.process is not None and self.process.poll() is not None:
            logging.info("Paper exited with code %s", self.process.returncode)
            if self.process.stdin is not None:
                with contextlib.suppress(Exception):
                    self.process.stdin.close()
            self.process = None
            if self.paper_log is not None:
                self.paper_log.close()
                self.paper_log = None
            for marker in (PID_PATH, PAPER_IDENTITY_PATH, SERVER_READY_PATH):
                with contextlib.suppress(FileNotFoundError):
                    marker.unlink()
        elif PID_PATH.exists():
            try:
                pid = int(PID_PATH.read_text(encoding="utf-8").strip())
            except (OSError, ValueError):
                pid = 0
            if not paper_process_is_alive(pid, Path(self.config["javaPath"])):
                for marker in (PID_PATH, PAPER_IDENTITY_PATH, SERVER_READY_PATH):
                    with contextlib.suppress(FileNotFoundError):
                        marker.unlink()


async def write_json_response(writer: asyncio.StreamWriter, status: int, payload: dict) -> None:
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    reason = {200: "OK", 202: "Accepted", 503: "Service Unavailable"}.get(status, "Error")
    headers = [
        f"HTTP/1.1 {status} {reason}",
        "Content-Type: application/json",
        f"Content-Length: {len(body)}",
        "Access-Control-Allow-Origin: *",
        "Cache-Control: no-store",
        "Connection: close",
        "",
        "",
    ]
    writer.write("\r\n".join(headers).encode("ascii") + body)
    await writer.drain()
    writer.close()
    with contextlib.suppress(Exception):
        await writer.wait_closed()


async def copy_stream(reader: asyncio.StreamReader, writer: asyncio.StreamWriter, counters: dict, key: str) -> None:
    while True:
        data = await reader.read(65536)
        if not data:
            return
        counters[key] += len(data)
        writer.write(data)
        await writer.drain()


async def relay_streams(client_reader, client_writer, backend_reader, backend_writer) -> dict:
    counters = {"clientToBackendBytes": 0, "backendToClientBytes": 0}
    first = asyncio.create_task(copy_stream(client_reader, backend_writer, counters, "clientToBackendBytes"))
    second = asyncio.create_task(copy_stream(backend_reader, client_writer, counters, "backendToClientBytes"))
    done, pending = await asyncio.wait((first, second), return_when=asyncio.FIRST_COMPLETED)
    first_closed = "client" if first in done else "backend"
    for task in pending:
        task.cancel()
    results = await asyncio.gather(*done, *pending, return_exceptions=True)
    failures = [type(result).__name__ for result in results if isinstance(result, BaseException)
                and not isinstance(result, asyncio.CancelledError)]
    backend_writer.close()
    client_writer.close()
    with contextlib.suppress(Exception):
        await backend_writer.wait_closed()
    with contextlib.suppress(Exception):
        await client_writer.wait_closed()
    counters["firstClosed"] = first_closed
    counters["relayError"] = failures[0] if failures else None
    return counters


async def handle_connection(manager: ServerManager, client_reader, client_writer) -> None:
    connection_id = secrets.token_hex(6)
    opened_at = time.monotonic()
    try:
        header = await asyncio.wait_for(client_reader.readuntil(b"\r\n\r\n"), timeout=10.0)
    except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError):
        record_failure(connection_id, "incomplete-upgrade")
        client_writer.close()
        return
    try:
        line = header.split(b"\r\n", 1)[0].decode("ascii", "replace")
        method, raw_target, _ = line.split(" ", 2)
        path = urlsplit(raw_target).path
    except ValueError:
        record_failure(connection_id, "invalid-request-line")
        await write_json_response(client_writer, 503, {"gateway": "online", "server": "invalid-request"})
        return
    if path in ("/health", "/status") or method == "OPTIONS":
        if method == "OPTIONS":
            await write_json_response(client_writer, 200, {"gateway": "online"})
            return
        current = await manager.state()
        if path == "/health" and current == "stopped":
            current = await manager.start_if_needed()
        status = 200 if path == "/status" or current == "running" else 202 if current == "starting" else 503
        await write_json_response(client_writer, status, {
            "gateway": "online",
            "server": current,
            "backendPort": int(manager.config["backendPort"]),
            "alwaysOn": bool(manager.config.get("alwaysOn", False)),
            "idleShutdownSeconds": int(manager.config.get("idleShutdownSeconds", 0)),
            "adminProtected": OWNER_BOOTSTRAP_DONE_PATH.exists() or OWNER_BOOTSTRAP_PATH.exists(),
            "ssoReady": sso_ready(),
            "activeConnections": len(SSO_CONNECTIONS),
            "lastFailureCode": LAST_FAILURE["code"],
            "lastFailureAt": LAST_FAILURE["at"],
        })
        return
    # Apply to both the legacy public Funnel and Jaspr's same-origin relay.
    # Never log raw_target: it contains the single-use login capability.
    ticket_match = re.fullmatch(r"/jaspercraft/socket\?ticket=([A-Za-z0-9_-]{43})", raw_target)
    if method != "GET" or not ticket_match:
        record_failure(connection_id, "invalid-socket-path")
        await write_json_response(client_writer, 503, {"gateway": "online", "server": "use-jaspr-chat"})
        return
    ticket_fingerprint = hashlib.sha256(ticket_match.group(1).encode("ascii")).hexdigest()[:12]
    log_event("jaspercraft.socket.requested", connectionId=connection_id,
              capabilityFingerprint=ticket_fingerprint, activeConnections=len(SSO_CONNECTIONS))
    if not sso_ready():
        record_failure(connection_id, "sso-not-ready-before-start")
        await write_json_response(client_writer, 503, {"gateway": "online", "server": "sign-in-not-ready"})
        return
    if not await manager.ensure_ready():
        record_failure(connection_id, "paper-unavailable")
        await write_json_response(client_writer, 503, {"gateway": "online", "server": "unavailable"})
        return
    if not sso_ready():
        record_failure(connection_id, "sso-not-ready-after-start")
        await write_json_response(client_writer, 503, {"gateway": "online", "server": "sign-in-not-ready"})
        return
    try:
        backend_reader, backend_writer = await asyncio.open_connection(
            manager.config["backendHost"], int(manager.config["backendPort"])
        )
    except OSError:
        record_failure(connection_id, "backend-connect-failed")
        await write_json_response(client_writer, 503, {"gateway": "online", "server": "backend-unavailable"})
        return
    backend_writer.write(header)
    await backend_writer.drain()
    SSO_CONNECTIONS.add(client_writer)
    log_event("jaspercraft.socket.backend_connected", connectionId=connection_id,
              capabilityFingerprint=ticket_fingerprint, activeConnections=len(SSO_CONNECTIONS))
    try:
        relay = await relay_streams(client_reader, client_writer, backend_reader, backend_writer)
        log_event("jaspercraft.socket.closed", connectionId=connection_id,
                  durationMs=round((time.monotonic() - opened_at) * 1000),
                  activeConnections=max(0, len(SSO_CONNECTIONS) - 1), **relay)
    finally:
        SSO_CONNECTIONS.discard(client_writer)


async def control_monitor(manager: ServerManager, stop_event: asyncio.Event) -> None:
    previous_sso_ready = None
    while not stop_event.is_set():
        manager.reap()
        current_sso_ready = sso_ready()
        if current_sso_ready != previous_sso_ready:
            log_event("jaspercraft.sso.readiness", ready=current_sso_ready,
                      activeConnections=len(SSO_CONNECTIONS))
            previous_sso_ready = current_sso_ready
        if not current_sso_ready:
            if SSO_CONNECTIONS:
                log_event("jaspercraft.socket.watchdog_close", count=len(SSO_CONNECTIONS))
            for connection in tuple(SSO_CONNECTIONS):
                connection.close()
        if GATEWAY_STOP_PATH.exists():
            GATEWAY_STOP_PATH.unlink(missing_ok=True)
            await manager.request_graceful_stop()
            stop_event.set()
            return
        try:
            current = await manager.state()
            if bool(manager.config.get("alwaysOn", False)) and current == "stopped":
                current = await manager.start_if_needed()
            if current == "running":
                await manager.maybe_bootstrap_owner()
        except Exception:
            logging.exception("Always-on monitor iteration failed")
        await asyncio.sleep(1.0)


async def main() -> None:
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    manager = ServerManager(config)
    stop_event = asyncio.Event()
    GATEWAY_PID_PATH.write_text(str(os.getpid()), encoding="utf-8")
    GATEWAY_STOP_PATH.unlink(missing_ok=True)
    server = await asyncio.start_server(
        lambda reader, writer: handle_connection(manager, reader, writer),
        config["listenHost"],
        int(config["listenPort"]),
        limit=131072,
    )
    log_event("jaspercraft.gateway.started", host=config["listenHost"], port=config["listenPort"], pid=os.getpid())
    if bool(config.get("alwaysOn", False)) and not MAINTENANCE_PATH.exists():
        await manager.start_if_needed()
    monitor = asyncio.create_task(control_monitor(manager, stop_event))
    try:
        await stop_event.wait()
    finally:
        server.close()
        await server.wait_closed()
        monitor.cancel()
        await asyncio.gather(monitor, return_exceptions=True)
        GATEWAY_PID_PATH.unlink(missing_ok=True)
        log_event("jaspercraft.gateway.stopped", pid=os.getpid())


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception:
        logging.exception("Gateway terminated unexpectedly")
        raise
