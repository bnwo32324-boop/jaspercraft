"""End-to-end check of the voice relay: handshake, identity, proximity culling, geometry.

Compiles the plugin and the offline fixture into a temporary directory and drives the real relay
over a real WebSocket. No Paper server, no network exposure, nothing installed.

    python server/custom-plugins/JasprVoiceChat/test/voice_relay_test.py
"""
import base64, hashlib, hmac, os, pathlib, shutil, socket, struct, subprocess, sys, tempfile, time, secrets

PLUGIN_ROOT = pathlib.Path(__file__).resolve().parent.parent
PROJECT_ROOT = PLUGIN_ROOT.parent.parent.parent
PAPER_JAR = PROJECT_ROOT / "server" / "cache" / "patched_1.12.2.jar"
PORT = 24571
WORK = pathlib.Path(tempfile.mkdtemp(prefix="jaspr-voice-test-"))
KEY_PATH = str(WORK / "bridge.key")
KEY = secrets.token_hex(32)


def tool(name):
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(pathlib.Path(os.environ["JAVA_HOME"]) / "bin" / name)
    candidates.append(pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot") / "bin" / name)
    for candidate in candidates:
        for suffix in ("", ".exe"):
            if candidate.with_name(candidate.name + suffix).is_file():
                return str(candidate.with_name(candidate.name + suffix))
    found = shutil.which(name)
    if not found:
        sys.exit(f"{name} not found. Install a JDK or set JAVA_HOME.")
    return found


def compile_fixture():
    if not PAPER_JAR.is_file():
        sys.exit(f"Missing {PAPER_JAR}; this test compiles against the server jar.")
    classes = WORK / "classes"
    classes.mkdir()
    sources = sorted(str(p) for p in PLUGIN_ROOT.rglob("*.java"))
    subprocess.run([tool("javac"), "--release", "8", "-encoding", "UTF-8", "-nowarn",
                    "-cp", str(PAPER_JAR), "-d", str(classes)] + sources, check=True)
    return classes


CLASSES = compile_fixture()
S_WELCOME, S_AUDIO, S_ROSTER, S_NOTICE = 0x81, 0x82, 0x83, 0x85
C_HELLO, C_AUDIO = 0x01, 0x02

failures = []
def check(name, ok, detail=""):
    print(("  PASS  " if ok else "  FAIL  ") + name + (("  -> " + str(detail)) if detail else ""))
    if not ok: failures.append(name)

def sign(player, expires):
    return hmac.new(KEY.encode(), f"{player}\n{expires}".encode(), hashlib.sha256).hexdigest()

def handshake(player, expires=None, signature=None, omit_auth=False):
    sock = socket.create_connection(("127.0.0.1", PORT), timeout=5)
    key = base64.b64encode(os.urandom(16)).decode()
    expires = expires if expires is not None else str(int(time.time() * 1000) + 20000).zfill(13)
    lines = [
        "GET /jaspercraft/voice HTTP/1.1", f"Host: 127.0.0.1:{PORT}",
        "Upgrade: websocket", "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}", "Sec-WebSocket-Version: 13",
    ]
    if not omit_auth:
        lines += [f"X-Jaspr-Voice-Player: {player}", f"X-Jaspr-Voice-Expires: {expires}",
                  f"X-Jaspr-Voice-Auth: {signature if signature is not None else sign(player, expires)}"]
    sock.sendall(("\r\n".join(lines) + "\r\n\r\n").encode())
    data = b""
    while b"\r\n\r\n" not in data:
        chunk = sock.recv(4096)
        if not chunk: break
        data += chunk
    status = data.split(b"\r\n", 1)[0].decode(errors="replace")
    return sock, status

def send_frame(sock, payload):
    header = bytearray([0x82])
    mask = os.urandom(4)
    n = len(payload)
    if n < 126: header.append(0x80 | n)
    else: header.append(0x80 | 126); header += struct.pack(">H", n)
    header += mask
    header += bytes(b ^ mask[i & 3] for i, b in enumerate(payload))
    sock.sendall(bytes(header))

def read_frames(sock, seconds=0.6):
    sock.settimeout(seconds)
    buf, frames = b"", []
    deadline = time.time() + seconds
    while time.time() < deadline:
        try: chunk = sock.recv(65536)
        except socket.timeout: break
        if not chunk: break
        buf += chunk
        while len(buf) >= 2:
            length = buf[1] & 0x7F
            offset = 2
            if length == 126:
                if len(buf) < 4: break
                length = struct.unpack(">H", buf[2:4])[0]; offset = 4
            if len(buf) < offset + length: break
            frames.append(buf[offset:offset + length])
            buf = buf[offset + length:]
    return frames

def audio_packet(seq, payload=b"\x11\x22\x33\x44"):
    return bytes([C_AUDIO, seq >> 8, seq & 0xFF, 1, len(payload) >> 8, len(payload) & 0xFF]) + payload

open(KEY_PATH, "w").write(KEY)
proc = subprocess.Popen(
    [tool("java"), "-cp", os.pathsep.join([str(CLASSES), str(PAPER_JAR)]),
     "chat.jaspr.voice.VoiceTestHarness", str(PORT), KEY_PATH],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
for _ in range(100):
    line = proc.stdout.readline()
    if line.startswith("READY"): break
    if not line and proc.poll() is not None:
        print("harness died:", proc.stdout.read()); sys.exit(1)

try:
    print("\nIdentity")
    s, status = handshake("nobody", omit_auth=True)
    check("unsigned connection is refused", "403" in status, status); s.close()

    s, status = handshake("listener", signature="0" * 64)
    check("bad signature is refused", "403" in status, status); s.close()

    old = str(int(time.time() * 1000) - 5000).zfill(13)
    s, status = handshake("listener", expires=old)
    check("expired signature is refused", "403" in status, status); s.close()

    s, status = handshake("listener", expires=str(int(time.time()*1000) + 600000).zfill(13))
    check("far-future signature is refused", "403" in status, status); s.close()

    s, status = handshake("bad name!")
    check("malformed player name is refused", "403" in status, status); s.close()

    print("\nHandshake")
    listener, status = handshake("listener")
    check("signed connection upgrades", "101" in status, status)
    frames = read_frames(listener)
    welcome = [f for f in frames if f and f[0] == S_WELCOME]
    check("welcome frame received", len(welcome) == 1, [f[0] for f in frames])
    if welcome:
        _, version, self_id, max_d, whisper_d, frame_ms, _ = struct.unpack(">BBHHHBB", welcome[0][:10])
        check("welcome advertises config", version == 1 and max_d == 48 and whisper_d == 12 and frame_ms == 20,
              (version, max_d, whisper_d, frame_ms))

    speakers = {}
    for name in ("ahead", "east", "distant", "nether", "sneaker", "whisperer"):
        sock, st = handshake(name)
        assert "101" in st, (name, st)
        speakers[name] = sock
    read_frames(listener, 0.4)

    print("\nProximity culling")
    def relay(name, payload=b"\xaa\xbb\xcc\xdd"):
        send_frame(speakers[name], audio_packet(7, payload))
        time.sleep(0.25)
        return [f for f in read_frames(listener, 0.4) if f and f[0] == S_AUDIO]

    got = relay("ahead")
    check("nearby player is relayed", len(got) == 1, len(got))
    ahead_frame = got[0] if got else None

    check("far player is culled", len(relay("distant")) == 0)
    check("other world is culled", len(relay("nether")) == 0)
    check("distant sneaker is culled", len(relay("sneaker")) == 0)

    got = relay("whisperer")
    check("close sneaker is relayed", len(got) == 1, len(got))
    if got:
        check("sneaking is marked as whisper", (got[0][6] & 0x01) == 1, got[0][6])

    send_frame(listener, audio_packet(9))
    time.sleep(0.2)
    check("speaker never hears itself", len([f for f in read_frames(listener, 0.3) if f and f[0] == S_AUDIO]) == 0)

    print("\nListener geometry (listener at origin facing +Z)")
    if ahead_frame:
        right, up, forward = struct.unpack(">hhh", ahead_frame[7:13])
        distance = struct.unpack(">H", ahead_frame[13:15])[0] / 32.0
        check("player due south is straight ahead", abs(right / 32.0) < 0.01 and abs(forward / 32.0 - 10) < 0.01,
              (right / 32.0, forward / 32.0))
        check("distance is exact", abs(distance - 10) < 0.05, distance)
        check("payload relayed verbatim", ahead_frame[16:] == b"\xaa\xbb\xcc\xdd", ahead_frame[16:].hex())

    got = relay("east")
    if got:
        right, up, forward = struct.unpack(">hhh", got[0][7:13])
        check("player to the east is on the left", abs(right / 32.0 + 10) < 0.01 and abs(forward / 32.0) < 0.01,
              (right / 32.0, forward / 32.0))

    print("\nGuard rails")
    send_frame(speakers["ahead"], bytes([C_AUDIO, 0, 1, 1, 0xFF, 0xFF]) + b"x" * 8)
    time.sleep(0.2)
    check("lying about payload length is dropped",
          len([f for f in read_frames(listener, 0.3) if f and f[0] == S_AUDIO]) == 0)

    send_frame(speakers["ahead"], bytes([C_AUDIO, 0, 2, 99, 0, 4]) + b"abcd")
    time.sleep(0.2)
    check("unknown codec is dropped",
          len([f for f in read_frames(listener, 0.3) if f and f[0] == S_AUDIO]) == 0)

    flooded = 0
    for i in range(200):
        send_frame(speakers["ahead"], audio_packet(i & 0xFFFF))
    time.sleep(0.5)
    flooded = len([f for f in read_frames(listener, 0.8) if f and f[0] == S_AUDIO])
    check("flood is rate limited", 0 < flooded <= 95, flooded)
finally:
    proc.kill()
    shutil.rmtree(WORK, ignore_errors=True)

print("\n" + ("ALL CHECKS PASSED" if not failures else f"{len(failures)} FAILED: {failures}"))
sys.exit(1 if failures else 0)
