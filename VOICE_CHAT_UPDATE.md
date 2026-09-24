# Proximity voice chat

JasperCraft now has always-on proximity voice. Players hear whoever is near them and are heard
when they speak. There is no key to hold and no toggle to find: the page asks for the microphone
once the relay answers, then transmits on speech and plays back continuously. A player who denies
the microphone, or has none, still hears everyone.

## What a player sees

A small pill on the right edge above the Discord button. It reads `Voice on`, turns green and
says `Talking` while transmitting, and lists nearby speakers with a level bar while they talk.
Clicking it mutes the microphone; `K` does the same without leaving the game. Sneaking narrows
the speaker's range to 12 blocks and shows their name in italics to anyone close enough.

The overlay stays completely hidden until voice has connected at least once, so nothing appears
until the relay is actually running, and nobody is prompted for a microphone before then.

## Shape of the change

| Piece | Location |
| --- | --- |
| Voice relay and proximity culling | `server/custom-plugins/JasprVoiceChat/` |
| Public route and signed identity hand-off | `Jaspergers/services/gateway/jaspercraft-voice.mjs` |
| Route registration | `Jaspergers/services/gateway/server.mjs` |
| Browser overlay, codecs, spatial playback | `site/jaspercraft-voice.js`, `.css`, `-worklet.js` |
| Microphone permission for the game frame | `site/jaspr-sso.js` |
| Script and stylesheet tags | `site/client.html` |
| `/voice` allowed for ordinary players | `server/custom-plugins/TestServerControl/` |

The uploaded Simple Voice Chat Forge jar was used as a reference for behaviour only. None of its
code is here, and none of it could be: it depends on `javax.sound.sampled`, UDP sockets and
native Opus binaries that do not exist in a browser. `MODDING_NOTES.md` already rules out
dropping Forge jars into this deployment, and that still holds.

## Why it is built this way

Gameplay logic lives in a Paper plugin, per `MODDING_NOTES.md`; the browser only captures, plays
and draws. **`classes.js` was not patched.** The engine's WebSocket is not reachable from page
scripts, and it did not need to be: the server already knows every position, so it sends each
listener the speaker's position relative to their own facing and the overlay just places the
sound. That keeps the 14 MB client and its SHA-256 pin untouched.

Audio uses its own WebSocket rather than the game connection, so chunk-loading bursts cannot
stall speech behind them.

## Security

The browser never says who it is. The Jaspr gateway authenticates the session it already holds,
signs the account name with the existing bridge key, and the relay refuses anything unsigned,
expired or not from loopback — the same trust model as `/jaspercraft/socket`.

Distance culling is server-side. A client is never sent audio from someone out of range, so
hearing range cannot be widened by editing the overlay. The relay caps frame size, clients and
packet rate, and treats every payload as opaque bytes.

`PlayerCommandPolicy` gained one public command, `voice`. Its `PUBLIC_TELEPORT` decision was
renamed `PUBLIC_COMMAND` to match what the list now means. Every other non-authentication
command remains owner-only, and `jasper` is still the only operator.

## Separate fix: players stuck on "Loading your character..."

Found while testing voice, and **not caused by it** — the same failure appears in
`paper-console.log` from before this work, and it reproduces with the voice overlay removed
entirely.

`TestServerControl` rewrites `.runtime/jaspr-sso-ready` once a second from Paper's main thread.
The gateway's `jaspercraftReady()` only accepted a marker younger than 5 seconds, so four
consecutive failed writes closed public admission: every new game socket was refused and the
host page sat on "Loading your character..." forever. Those writes fail regularly — the console
log carries **934** `readiness marker refresh was delayed` warnings, which is exactly the
transient Windows file-sharing collision the publisher's own comment anticipates.

The window is now 15 seconds (`services/gateway/jaspercraft-bridge.mjs`). The marker still
refreshes every second, so this tolerates fourteen consecutive misses instead of four, and it
still fails closed promptly when the game server is genuinely gone.

A second, rarer symptom remains: roughly 7 of 132 joins die at the AuthMe step with the client
showing "Handshake timed out". That correlates with main-thread pressure — `tickP95Ms` reaches
73 ms and `high_ping` warnings fire at 153 ms while JasprApocalypse is running 200+ mobs. A
retry gets in. Reducing that mob load is the real cure; it is a performance issue, not a voice
or authentication one.

## Editing the site files afterwards

`site/*.js` and `site/*.css` are served with `Cache-Control: immutable` and sit behind
Cloudflare, so a changed file at the **same** `?build=` value keeps serving the old bytes from
the edge. Bump the `build` value in `site/client.html` whenever you edit
`jaspercraft-voice.js` or `jaspercraft-voice.css`. `client.html` itself is `no-store` and
updates immediately.

## Verification

- 21 relay checks (handshake, identity, culling, whisper, geometry, guard rails, rate limiting)
- 9 gateway checks (upgrade, signed hand-off, payload fidelity, connection cap)
- 13 codec checks (framing, round-trip SNR, silence, clipping, Opus header)
- Live browser: worklets register, Opus round-trips at ~92 bytes per 20 ms frame, spatial
  playback produces the expected RMS
- The rebuilt `TestServerControl.jar` was diffed against the installed jar: identical file set

```powershell
python server\custom-plugins\JasprVoiceChat\test\voice_relay_test.py
node server\custom-plugins\JasprVoiceChat\test\codec_test.mjs
```

## Rollback

`scripts\install-voice-chat.ps1` backs up the replaced jars to
`.runtime\plugin-backup-<timestamp>-voice\` first. To undo: copy those two jars back into
`server\plugins\`, restore `services\gateway\server.mjs` and `jaspercraft-bridge.mjs` from their
`.pre-voice` copies, remove the two voice tags from `site\client.html`, restart Paper and the
Jaspr.chat host. Originals of every edited site file are in `.runtime\voice-backup\`.
