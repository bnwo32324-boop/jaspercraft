from __future__ import annotations

import functools
import http.server
import json
import logging
import os
from pathlib import Path
import socketserver

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SITE_ROOT = PROJECT_ROOT / "site"
RUNTIME_ROOT = PROJECT_ROOT / ".runtime"
PID_PATH = RUNTIME_ROOT / "http-server.pid"
LOG_PATH = RUNTIME_ROOT / "http-server.log"
HOST = "127.0.0.1"
PORT = 3308

RUNTIME_ROOT.mkdir(parents=True, exist_ok=True)
logging.basicConfig(filename=LOG_PATH, level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

class SiteHandler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript; charset=utf-8",
        ".map": "application/json; charset=utf-8",
        ".epk": "application/octet-stream",
    }

    def do_GET(self) -> None:
        if self.path.split("?", 1)[0] == "/healthz":
            body = json.dumps({"site": "online", "client": "Eaglercraft 1.12.2"}).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def end_headers(self) -> None:
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Permissions-Policy", "camera=(), geolocation=(), payment=()")
        if self.path.split("?", 1)[0] in ("/", "/index.html"):
            self.send_header("Cache-Control", "no-store")
        else:
            self.send_header("Cache-Control", "no-cache")
        super().end_headers()

    def log_message(self, format_string: str, *args: object) -> None:
        logging.info("%s - %s", self.address_string(), format_string % args)

class ThreadingSiteServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True

def main() -> None:
    for required in ("index.html", "classes.js", "assets.epk"):
        if not (SITE_ROOT / required).is_file():
            raise FileNotFoundError(f"Missing required site file: {SITE_ROOT / required}")
    handler = functools.partial(SiteHandler, directory=str(SITE_ROOT))
    with ThreadingSiteServer((HOST, PORT), handler) as server:
        PID_PATH.write_text(str(os.getpid()), encoding="utf-8")
        logging.info("Serving Eaglercraft 1.12.2 from %s on %s:%s", SITE_ROOT, HOST, PORT)
        try:
            server.serve_forever(poll_interval=0.5)
        finally:
            PID_PATH.unlink(missing_ok=True)

if __name__ == "__main__":
    main()