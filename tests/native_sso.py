"""Real Eagler/AuthMe test in a fresh candidate directory; never controls the live server.

Run: python -B tests/native_sso.py
Only test fixture credentials/tickets are generated; no production credential file is read.
"""
from __future__ import annotations
import asyncio
import contextlib
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import struct
import subprocess
import threading
import time
import uuid
import zipfile
import websockets

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / 'candidate' / ('native-sso-' + uuid.uuid4().hex)
SERVER = FIXTURE / 'server'
TICKETS = {}
LOCK = threading.Lock()
KEY = secrets.token_urlsafe(48)
PROBE_SOURCE = r'''
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import fr.xephi.authme.api.v3.AuthMeApi;
public final class SsoProbe extends JavaPlugin {
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (sender instanceof Player) return true;
    if (cmd.getName().equals("ssodisable")) {
      Bukkit.getPluginManager().disablePlugin(Bukkit.getPluginManager().getPlugin("TestServerControl"));
    } else for (Player p : Bukkit.getOnlinePlayers()) {
      getLogger().info("SSO_PROBE " + p.getName() + " authenticated=" + AuthMeApi.getInstance().isAuthenticated(p) + " op=" + p.isOp());
    }
    return true;
  }
}
'''

class Consume(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        status, result = 401, {}
        if self.path == '/api/jaspercraft/internal/consume' and self.headers.get('X-Jaspr-Craft-Key') == KEY:
            try:
                request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                with LOCK:
                    result = TICKETS.pop(request['ticket'], None)
                if result and result['expiresAt'] > time.time() * 1000:
                    status = 200
            except (KeyError, ValueError):
                pass
        body = json.dumps(result or {}).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *_):
        pass

def issue(name, privileged=False, lifetime=90):
    token = secrets.token_urlsafe(32)
    with LOCK:
        TICKETS[token] = {'userId': 'fixture-' + name, 'gameName': name, 'privileged': privileged,
                          'expiresAt': int((time.time() + lifetime) * 1000)}
    return token

def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]

def setup(bridge_port, game_port):
    SERVER.mkdir(parents=True)
    (FIXTURE / 'private').mkdir()
    (FIXTURE / '.runtime').mkdir()
    (FIXTURE / 'private' / 'jaspr-bridge.key').write_text(KEY, encoding='ascii')
    (SERVER / 'cache').mkdir()
    (SERVER / 'plugins').mkdir()
    for name in ['paper-1.12.2.jar', 'cache/mojang_1.12.2.jar', 'cache/patched_1.12.2.jar', 'plugins/EaglerXServer.jar', 'plugins/AuthMe.jar']:
        shutil.copyfile(ROOT / 'server' / name, SERVER / name)
    shutil.copyfile(ROOT / 'candidate/jaspr-sso/TestServerControl.jar', SERVER / 'plugins/TestServerControl.jar')
    (SERVER / 'plugins/TestServerControl').mkdir()
    (SERVER / 'plugins/TestServerControl/config.yml').write_text(f'idle-shutdown-seconds: 0\njaspr-bridge-port: {bridge_port}\n')
    (SERVER / 'eula.txt').write_text('eula=true\n')
    (SERVER / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={game_port}
online-mode=false
enable-rcon=false
enable-query=false
level-name=sso-fixture
level-type=FLAT
generate-structures=false
max-players=8
view-distance=2
spawn-protection=0
allow-nether=false
max-tick-time=-1
network-compression-threshold=-1
''')
    (SERVER / 'bukkit.yml').write_text('settings:\n  allow-end: false\n  update-folder: update\nspawn-limits:\n  monsters: 0\n  animals: 0\n  water-animals: 0\n  ambient: 0\n')
    (SERVER / 'plugins/EaglercraftXServer').mkdir()
    for name in ['settings.yml', 'listener.yml']:
        content = (ROOT / 'server/plugins/EaglercraftXServer' / name).read_text()
        content = content.replace('download_latest_certs: true', 'download_latest_certs: false').replace('enable_update_checker: true', 'enable_update_checker: false')
        content = content.replace('require_tls: true', 'require_tls: false')
        (SERVER / 'plugins/EaglercraftXServer' / name).write_text(content)
    (SERVER / 'plugins/AuthMe').mkdir()
    (SERVER / 'plugins/AuthMe/config.yml').write_text('''DataSource:
  backend: SQLITE
settings:
  sessions:
    enabled: false
  restrictions:
    ForceSingleSession: true
    kickNonRegistered: false
    timeout: 30
  registration:
    enabled: true
    force: true
    forceKickAfterRegister: false
    forceLoginAfterRegister: false
''')
    probe = FIXTURE / 'probe'
    probe.mkdir()
    (probe / 'SsoProbe.java').write_text(PROBE_SOURCE)
    cp = str(ROOT / 'server/cache/patched_1.12.2.jar') + os.pathsep + str(ROOT / 'server/plugins/AuthMe.jar')
    subprocess.run(['javac', '-cp', cp, '-d', str(probe), str(probe / 'SsoProbe.java')], check=True, capture_output=True)
    with zipfile.ZipFile(SERVER / 'plugins/SsoProbe.jar', 'w') as jar:
        jar.write(probe / 'SsoProbe.class', 'SsoProbe.class')
        jar.writestr('plugin.yml', 'name: SsoProbe\nversion: 1\nmain: SsoProbe\ndepend: [AuthMe, TestServerControl]\ncommands:\n  ssoprobe: {}\n  ssodisable: {}\n')

def command(process, text):
    process.stdin.write((text + '\n').encode())
    process.stdin.flush()

def logtext():
    return (FIXTURE / 'paper.log').read_text(encoding='utf-8', errors='replace')

async def until(predicate, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        await asyncio.sleep(.2)
    raise AssertionError('Isolated fixture condition timed out')

def short(value):
    value = value.encode('ascii')
    return bytes([len(value)]) + value

async def login(port, name, token):
    target = f'ws://127.0.0.1:{port}/jaspercraft/socket' + (('?ticket=' + token) if token else '')
    ws = await websockets.connect(target, origin='https://jaspr.chat', open_timeout=5, close_timeout=2, max_size=None)
    try:
        # Protocol-4 hello: legacy marker, handshake versions, Minecraft protocols, brand/version, no password auth.
        await ws.send(b'\x01\x02' + struct.pack('>HHHH', 1, 4, 1, 340) + short('JasprSSOTest') + short('1') + b'\x00' + short(name))
        hello = await asyncio.wait_for(ws.recv(), 8)
        if not isinstance(hello, bytes) or hello[0] != 2:
            raise AssertionError('Hello rejected, packet type=' + str(hello[0] if isinstance(hello, bytes) else 'text'))
        await ws.send(b'\x04' + short(name) + short('default') + b'\x00\x00\x00')
        allowed = await asyncio.wait_for(ws.recv(), 8)
        if not isinstance(allowed, bytes) or allowed[0] != 5:
            raise AssertionError('Login rejected, packet type=' + str(allowed[0] if isinstance(allowed, bytes) else 'text'))
        name_length = allowed[1]
        expected_uuid = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3)
        if allowed[2:2+name_length].decode() != name or uuid.UUID(bytes=allowed[2+name_length:18+name_length]) != expected_uuid:
            raise AssertionError('Native server profile name/UUID mismatch')
        await ws.send(b'\x07\x01' + short('skin_v1') + b'\x00\x05\x01\x00\x00\x00\x00')
        await ws.send(b'\x08')
        finished = await asyncio.wait_for(ws.recv(), 8)
        if not isinstance(finished, bytes) or finished[0] != 9:
            raise AssertionError('Finish rejected, packet type=' + str(finished[0] if isinstance(finished, bytes) else 'text'))
        async def drain():
            # Consume world frames so the fixture's inbound queue cannot hide a server close frame.
            try:
                async for _ in ws:
                    pass
            except websockets.exceptions.ConnectionClosed:
                pass
        ws.sso_drain_task = asyncio.create_task(drain())
        return ws
    except BaseException:
        await ws.close()
        raise

async def denied_login(port, name, token):
    try:
        ws = await login(port, name, token)
    except (AssertionError, websockets.exceptions.ConnectionClosed, websockets.exceptions.InvalidHandshake):
        return
    await ws.close()
    raise AssertionError('Invalid login was admitted')

def varint(value):
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    return bytes(result) + bytes([value])

async def denied_java(port):
    reader, writer = await asyncio.open_connection('127.0.0.1', port)
    try:
        address = b'localhost'
        handshake = b'\x00' + varint(340) + varint(len(address)) + address + struct.pack('>H', port) + b'\x02'
        start = b'\x00' + varint(len(b'SsoJava')) + b'SsoJava'
        writer.write(varint(len(handshake)) + handshake + varint(len(start)) + start)
        await writer.drain()
        # Paper may send login-success before the post-login guard rejects.
        # Require a disconnect before ANY play-state admission/world packet.
        state = 'login'
        for _ in range(5):
            length, shift = 0, 0
            while True:
                value = (await asyncio.wait_for(reader.readexactly(1), 5))[0]
                length |= (value & 127) << shift
                if value < 128:
                    break
                shift += 7
                if shift > 28:
                    raise AssertionError('Invalid Java packet length')
            if length < 1 or length > 65536:
                raise AssertionError('Unexpected Java packet size')
            packet = await asyncio.wait_for(reader.readexactly(length), 5)
            packet_id = packet[0]
            if (state == 'login' and packet_id == 0) or (state == 'play' and packet_id == 0x1a):
                return
            if state == 'login' and packet_id == 2:
                state = 'play'
                continue
            raise AssertionError('Unticketed Java connection received unexpected admission packet')
        raise AssertionError('Unticketed Java connection did not disconnect')
    finally:
        writer.close()
        await writer.wait_closed()

async def probe(process, name, privileged):
    for _ in range(35):
        before = len(logtext())
        command(process, 'ssoprobe')
        await asyncio.sleep(.2)
        if f'SSO_PROBE {name} authenticated=true op={str(privileged).lower()}' in logtext()[before:]:
            return
    raise AssertionError('AuthMe/privilege probe did not confirm ' + name)

async def main():
    bridge = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Consume)
    threading.Thread(target=bridge.serve_forever, daemon=True).start()
    port = free_port()
    setup(bridge.server_port, port)
    print('Isolated fixture:', FIXTURE, flush=True)
    with (FIXTURE / 'paper.log').open('wb') as output:
        process = subprocess.Popen(['java', '-Xms256M', '-Xmx768M', '-Dfile.encoding=UTF-8', '-jar', 'paper-1.12.2.jar', 'nogui'],
            cwd=SERVER, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        sockets = []
        try:
            await until(lambda: (FIXTURE / '.runtime/jaspr-sso-ready').exists() or process.poll() is not None, 150)
            if process.poll() is not None:
                raise AssertionError('Isolated Paper exited before readiness')
            print('PASS: isolated Paper loaded candidate, Eagler and AuthMe; readiness marker published.', flush=True)
            await denied_login(port, 'SsoFriend', None)
            print('PASS: native no-ticket handshake rejected.', flush=True)
            await denied_java(port)
            print('PASS: native Java connection without an Eagler ticket rejected.', flush=True)
            token = issue('SsoFriend')
            friend = await login(port, 'SsoFriend', token); sockets.append(friend)
            await probe(process, 'SsoFriend', False)
            print('PASS: native signed friend force-authenticated with op=false.', flush=True)
            await denied_login(port, 'SsoFriend', token)
            await probe(process, 'SsoFriend', False)
            print('PASS: replay rejected without stealing the original connection.', flush=True)
            mismatch = issue('SsoFriend2')
            await denied_login(port, 'SsoOther', mismatch)
            await denied_login(port, 'SsoFriend2', mismatch)
            print('PASS: mismatch burns ticket; reuse rejected.', flush=True)
            owner = await login(port, 'jasper', issue('jasper', True)); sockets.append(owner)
            await probe(process, 'jasper', True)
            print('PASS: canonical owner force-authenticated with op=true.', flush=True)
            await denied_login(port, 'jasper', issue('jasper', False))
            print('PASS: unprivileged owner-name impersonation rejected.', flush=True)
            await friend.close(); sockets.remove(friend)
            await asyncio.sleep(1)
            command(process, 'op SsoFriend')
            await asyncio.sleep(.3)
            friend = await login(port, 'SsoFriend', issue('SsoFriend')); sockets.append(friend)
            await probe(process, 'SsoFriend', False)
            print('PASS: preexisting operator status cleared on verified nonprivileged rejoin.', flush=True)
            await denied_login(port, 'SsoExpired', issue('SsoExpired', lifetime=-1))
            print('PASS: expired native ticket rejected.', flush=True)
            bridge.shutdown(); bridge.server_close()
            await denied_login(port, 'SsoOutage', issue('SsoOutage'))
            print('PASS: bridge outage rejects native admission.', flush=True)
            command(process, 'ssodisable')
            await until(lambda: not (FIXTURE / '.runtime/jaspr-sso-ready').exists(), 5)
            await asyncio.wait_for(owner.wait_closed(), 5)
            print('PASS: plugin disable removes readiness and disconnects signed sessions.', flush=True)
        finally:
            for ws in sockets:
                with contextlib.suppress(Exception): await ws.close()
            if process.poll() is None:
                command(process, 'stop')
                try:
                    await asyncio.wait_for(asyncio.to_thread(process.wait), 30)
                except asyncio.TimeoutError:
                    process.terminate()
                    await asyncio.to_thread(process.wait)
            bridge.shutdown(); bridge.server_close()
            # Fixture key and ticket state are disposable; never production data.
            (FIXTURE / 'private/jaspr-bridge.key').unlink(missing_ok=True)
            TICKETS.clear()
            print('Isolated Paper stopped; live services were not controlled.', flush=True)

if __name__ == '__main__':
    try:
        asyncio.run(main())
    except Exception as error:
        # Never include websocket URLs, header values, or arbitrary third-party exception messages.
        if isinstance(error, AssertionError): print('FAIL:', str(error), flush=True)
        else: print('FAIL:', type(error).__name__, '(inspect the local isolated fixture)', flush=True)
        raise SystemExit(1)
