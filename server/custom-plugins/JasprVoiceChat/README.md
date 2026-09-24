# Always-on proximity voice chat

Voice for the JasperCraft browser client. There is no push-to-talk and nothing for a player to
switch on: the page opens the microphone when they join and transmits whenever it hears speech.
Listening is unconditional, so a nearby player is audible whether or not they ever touched a
setting. The only control is a mute button in the overlay.

This is not the Simple Voice Chat Forge mod and does not contain any of its code. A Forge jar
cannot run in this browser runtime — it needs `javax.sound.sampled`, UDP datagram sockets and
native Opus/RNNoise binaries, none of which exist in a browser. The architecture is the same
shape (client captures, server culls by distance, listeners place the sound in 3D), rebuilt on
browser and Bukkit primitives.

## How a packet travels

```
browser mic -> Opus (WebCodecs) -> wss://jaspr.chat/jaspercraft/voice
    -> Jaspr gateway (authenticates the session, signs the account name)
    -> loopback 24454 -> this plugin (decides who is close enough)
    -> each nearby listener -> Opus decode -> PannerNode at the speaker's relative position
```

The relay never inspects audio payloads; it forwards them verbatim and attaches geometry.

## Why the server decides who hears whom

Distance culling happens here, not in the browser. A client is never told that a distant player
is speaking, so hearing range cannot be extended by editing the overlay. The browser also never
asserts who it is: the Jaspr gateway has already authenticated the session and signs the account
name with the shared bridge key, and `EdgeAuth` rejects anything unsigned, expired, replayed
from a stale timestamp, or arriving from a non-loopback peer.

## Behaviour

- Hearing range is `max-distance` (48 blocks by default), linear falloff to silence at the edge.
- Sneaking drops the speaker to `whisper-distance` (12 blocks) and marks the frame as a whisper,
  which the overlay shows in italics.
- Different worlds never hear each other unless `cross-world` is enabled.
- Spectators and dead players do not transmit by default; they still hear.
- Positions are sampled on the main thread every tick and read lock-free by the network threads,
  because Bukkit state may not be touched off-thread.

## Commands

`/voice` shows status and range, `/voice who` lists connected players, `/voice off` drops this
tab's session. `voice` is in `PlayerCommandPolicy`'s public list, so ordinary players may run it;
every other non-authentication command stays owner-only exactly as before.

## Configuration

`config.yml` carries the loopback port, ranges, client cap and the per-client rate limits. The
port is loopback-only and is never exposed publicly; the gateway is the only way in.

## Build and verification

```powershell
node --check site\jaspercraft-voice.js
node --check site\jaspercraft-voice-worklet.js
.\scripts\build-voice-plugin.ps1     # writes candidate\voice-chat\ only
.\scripts\install-voice-chat.ps1     # backs up, installs, restarts Paper
```

The offline fixture starts the real relay against scripted player positions, with no Paper
server, and asserts the handshake, identity checks, proximity culling, whisper radius, listener
geometry and rate limiting:

```powershell
python server\custom-plugins\JasprVoiceChat\test\voice_relay_test.py
```

`window.JasprVoiceDiagnostics.status()` in the game frame reports the connection state, codec in
use, microphone state and who is currently audible. It exposes no account details, keys or
signatures.

## Limits

Mesh size is bounded by `max-clients` (24). Every listener within range gets their own copy of
each frame, so bandwidth grows with the number of people standing together, not with the server
population; at 24 kbps per speaker a crowded spot costs a few hundred kbps.

Browsers without WebCodecs Opus fall back to 16 kHz IMA ADPCM at 64 kbps. Packets stay
independent in both codecs, so a lost packet is a short gap and never corrupts what follows.
