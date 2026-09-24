(function () {
  "use strict";

  // Always-on proximity voice for the JasperCraft browser client.
  //
  // There is no push-to-talk and no switch to find: once the page has a microphone the overlay
  // transmits whenever it hears speech, and it always plays back whoever is nearby. Range and
  // audibility are decided by the server, so this file never learns about distant speakers.

  var PROTOCOL_VERSION = 3;
  var SAMPLE_RATE = 48000;
  var FRAME_SAMPLES = 960;          // 20 ms
  var FRAME_MICROS = 20000;
  var CODEC_OPUS = 1;
  var CODEC_ADPCM = 2;
  var ADPCM_RATE = 16000;
  var ADPCM_SAMPLES = 320;          // 20 ms at 16 kHz
  var PREROLL_FRAMES = 4;           // replayed on speech onset so word beginnings survive
  var RELEASE_MS = 350;
  var MUTE_KEY = "Backquote";       // mute toggle. Talking itself never needs a key.

  var C_HELLO = 0x01, C_AUDIO = 0x02, C_STATE = 0x03, C_PING = 0x04;
  var S_WELCOME = 0x81, S_AUDIO = 0x82, S_ROSTER = 0x83, S_PONG = 0x84, S_NOTICE = 0x85, S_ENV = 0x86;
  var AUDIO_HEADER = 16;
  var ENV_SPEAKER_UNDERWATER = 0x01, ENV_LISTENER_UNDERWATER = 0x02, ENV_SPEAKER_ENCLOSED = 0x04;
  // Nyquist is the real ceiling; this is simply "filter wide open".
  var OPEN_AIR_HZ = 20000;
  var STATE_MIC_MUTED = 0x01, STATE_DEAFENED = 0x02;
  var POSITION_SCALE = 32;

  var state = {
    started: false,
    mutedPeers: Object.create(null),
    bus: null,
    socket: null,
    reconnectDelay: 1000,
    reconnectTimer: null,
    selfId: 0,
    maxDistance: 48,
    audio: null,
    audioReady: null,
    workletReady: false,
    micStream: null,
    captureNode: null,
    encoder: null,
    codec: 0,
    sequence: 0,
    timestamp: 0,
    speaking: false,
    micRequested: false,
    everConnected: false,
    lastVoiceAt: 0,
    noiseFloor: 0.004,
    micLevel: 0,
    muted: false,
    micStatus: "pending",
    preroll: [],
    peers: Object.create(null),
    roster: [],
    notice: "",
    noticeUntil: 0
  };

  // ---------------------------------------------------------------- utilities

  function log(message, detail) {
    if (window.console && console.debug) console.debug("[voice] " + message, detail === undefined ? "" : detail);
  }

  function clamp(value, low, high) { return value < low ? low : value > high ? high : value; }

  // ---------------------------------------------------------------- IMA ADPCM
  // Fallback codec for browsers without WebCodecs Opus. Each packet is self-contained so a lost
  // packet cannot corrupt the ones that follow.

  var IMA_INDEX = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8];
  var IMA_STEP = [
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66,
    73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408,
    449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
    2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630,
    9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
  ];

  function adpcmEncode(samples) {
    var out = new Uint8Array(4 + (samples.length >> 1));
    // Every packet is decoded independently, so it carries its own starting state. Seeding the
    // predictor and step size from this frame avoids the loud ramp-in a cold start would cause.
    var predictor = samples.length ? samples[0] : 0;
    var deltaSum = 0;
    for (var d = 1; d < samples.length; d++) {
      var difference0 = samples[d] - samples[d - 1];
      deltaSum += difference0 < 0 ? -difference0 : difference0;
    }
    var meanDelta = samples.length > 1 ? deltaSum / (samples.length - 1) : 0;
    var index = 0;
    while (index < 88 && IMA_STEP[index] < meanDelta) index++;
    index = clamp(index, 0, 88);
    out[0] = predictor & 0xff;
    out[1] = (predictor >> 8) & 0xff;
    out[2] = index;
    out[3] = 0;
    var offset = 4, nibbleHigh = false, current = 0;
    for (var i = 0; i < samples.length; i++) {
      var step = IMA_STEP[index];
      var delta = samples[i] - predictor;
      var code = 0;
      if (delta < 0) { code = 8; delta = -delta; }
      var difference = step >> 3;
      if (delta >= step) { code |= 4; delta -= step; difference += step; }
      if (delta >= (step >> 1)) { code |= 2; delta -= step >> 1; difference += step >> 1; }
      if (delta >= (step >> 2)) { code |= 1; difference += step >> 2; }
      predictor += (code & 8) ? -difference : difference;
      predictor = clamp(predictor, -32768, 32767);
      index = clamp(index + IMA_INDEX[code], 0, 88);
      if (nibbleHigh) { out[offset++] = current | (code << 4); nibbleHigh = false; }
      else { current = code; nibbleHigh = true; }
    }
    if (nibbleHigh) out[offset++] = current;
    return out;
  }

  function adpcmDecode(bytes) {
    var count = (bytes.length - 4) * 2;
    var samples = new Int16Array(count);
    var predictor = bytes[0] | (bytes[1] << 8);
    if (predictor > 32767) predictor -= 65536;
    var index = clamp(bytes[2], 0, 88);
    var produced = 0;
    for (var i = 4; i < bytes.length; i++) {
      var pair = [bytes[i] & 0x0f, (bytes[i] >> 4) & 0x0f];
      for (var n = 0; n < 2; n++) {
        var code = pair[n];
        var step = IMA_STEP[index];
        var difference = step >> 3;
        if (code & 4) difference += step;
        if (code & 2) difference += step >> 1;
        if (code & 1) difference += step >> 2;
        predictor += (code & 8) ? -difference : difference;
        predictor = clamp(predictor, -32768, 32767);
        index = clamp(index + IMA_INDEX[code], 0, 88);
        if (produced < count) samples[produced++] = predictor;
      }
    }
    return samples;
  }

  function downsampleToInt16(pcm) {
    var ratio = SAMPLE_RATE / ADPCM_RATE;
    var out = new Int16Array(ADPCM_SAMPLES);
    for (var i = 0; i < ADPCM_SAMPLES; i++) {
      var start = Math.floor(i * ratio);
      var sum = 0, taken = 0;
      for (var s = start; s < start + ratio && s < pcm.length; s++) { sum += pcm[s]; taken++; }
      var value = taken ? sum / taken : 0;
      out[i] = clamp(Math.round(value * 32767), -32768, 32767);
    }
    return out;
  }

  function upsampleToFloat(samples) {
    var ratio = SAMPLE_RATE / ADPCM_RATE;
    var out = new Float32Array(Math.round(samples.length * ratio));
    for (var i = 0; i < out.length; i++) {
      var position = i / ratio;
      var low = Math.floor(position);
      var high = low + 1 < samples.length ? low + 1 : low;
      var fraction = position - low;
      out[i] = ((samples[low] * (1 - fraction)) + (samples[high] * fraction)) / 32768;
    }
    return out;
  }

  // ---------------------------------------------------------------- WebCodecs

  var OPUS_DESCRIPTION = (function () {
    var head = new Uint8Array(19);
    var magic = "OpusHead";
    for (var i = 0; i < 8; i++) head[i] = magic.charCodeAt(i);
    head[8] = 1;                       // version
    head[9] = 1;                       // channels
    head[10] = 0x00; head[11] = 0x0f;  // pre-skip 3840, little endian
    head[12] = 0x80; head[13] = 0xbb;  // 48000 Hz, little endian
    head[14] = 0x00; head[15] = 0x00;
    head[16] = 0x00; head[17] = 0x00;  // output gain
    head[18] = 0x00;                   // channel mapping family
    return head;
  })();

  function opusSupported() {
    return typeof window.AudioEncoder === "function" && typeof window.AudioDecoder === "function"
      && typeof window.AudioData === "function" && typeof window.EncodedAudioChunk === "function";
  }

  // ---------------------------------------------------------------- overlay

  var ui = {};

  function buildOverlay() {
    var root = document.createElement("div");
    root.id = "jaspr-voice";
    root.className = "jv-idle";

    var pill = document.createElement("button");
    pill.type = "button";
    pill.className = "jv-pill";
    pill.title = "Proximity voice chat";

    var icon = document.createElement("span");
    icon.className = "jv-icon";
    icon.innerHTML = micGlyph();

    var meter = document.createElement("span");
    meter.className = "jv-meter";
    var meterFill = document.createElement("span");
    meterFill.className = "jv-meter-fill";
    meter.appendChild(meterFill);

    var label = document.createElement("span");
    label.className = "jv-label";
    label.textContent = "Voice";

    pill.appendChild(icon);
    pill.appendChild(label);
    pill.appendChild(meter);
    pill.addEventListener("click", function (event) {
      event.preventDefault();
      event.stopPropagation();
      toggleMute();
    });

    var list = document.createElement("div");
    list.className = "jv-list";

    root.appendChild(pill);
    root.appendChild(list);
    document.body.appendChild(root);

    ui.root = root;
    ui.pill = pill;
    ui.icon = icon;
    ui.label = label;
    ui.meterFill = meterFill;
    ui.list = list;
  }

  function micGlyph() {
    return '<svg viewBox="0 0 16 16" aria-hidden="true">'
      + '<path d="M8 1.5a2 2 0 0 0-2 2v4a2 2 0 1 0 4 0v-4a2 2 0 0 0-2-2z"/>'
      + '<path d="M4 7.2v.3a4 4 0 0 0 8 0v-.3h1.2v.3a5.2 5.2 0 0 1-4.5 5.15V15H7.3v-2.35A5.2 5.2 0 0 1 2.8 7.5v-.3H4z"/>'
      + "</svg>";
  }

  function mutedGlyph() {
    return '<svg viewBox="0 0 16 16" aria-hidden="true">'
      + '<path d="M8 1.5a2 2 0 0 0-2 2v4a2 2 0 0 0 .1.6l3.8-3.8V3.5a2 2 0 0 0-2-2z"/>'
      + '<path d="M4 7.2v.3a4 4 0 0 0 5.9 3.5l.9.9A5.2 5.2 0 0 1 8.7 12.6V15H7.3v-2.35A5.2 5.2 0 0 1 2.8 7.5v-.3H4z"/>'
      + '<path d="M2.4 2.1 13.9 13.6l-.9.9L1.5 3z"/>'
      + "</svg>";
  }

  function toggleMute() {
    state.muted = !state.muted;
    sendState();
    renderOverlay();
  }

  function setNotice(text, ms) {
    state.notice = text;
    state.noticeUntil = Date.now() + (ms || 5000);
    renderOverlay();
  }

  function renderOverlay() {
    if (!ui.root) return;
    var now = Date.now();
    var connected = state.socket && state.socket.readyState === 1;
    // Stay out of the way until voice has actually worked once. A player should never be shown a
    // permanent "Connecting..." for a backend this deployment has not switched on yet.
    if (!state.everConnected) {
      ui.root.style.display = "none";
      return;
    }
    ui.root.style.display = "";
    var labelText = "Voice";
    var cls = "";

    if (state.notice && now < state.noticeUntil) {
      labelText = state.notice;
      cls = "jv-warn";
    } else if (!connected) {
      labelText = "Reconnecting…";
      cls = "jv-warn";
    } else if (state.micStatus === "denied") {
      labelText = "Mic blocked";
      cls = "jv-warn";
    } else if (state.micStatus === "absent") {
      labelText = "Listening only";
      cls = "jv-warn";
    } else if (state.muted) {
      labelText = "Muted - click to talk";
      cls = "jv-muted";
    } else if (state.speaking) {
      labelText = "Talking";
      cls = "jv-live";
    } else {
      labelText = "Voice on";
    }

    ui.label.textContent = labelText;
    ui.icon.innerHTML = state.muted ? mutedGlyph() : micGlyph();
    ui.pill.title = state.muted
      ? "Your microphone is off - click, or press the ` key"
      : "Mute yourself (` key)";
    ui.pill.className = "jv-pill " + cls;
    ui.meterFill.style.transform = "scaleX(" + clamp(state.micLevel * 7, 0, 1).toFixed(3) + ")";

    var audible = [];
    for (var id in state.peers) {
      var peer = state.peers[id];
      if (peer && now - peer.lastAudioAt < 600) audible.push(peer);
    }
    audible.sort(function (a, b) { return a.distance - b.distance; });

    ui.list.innerHTML = "";
    for (var i = 0; i < audible.length && i < 6; i++) {
      var row = document.createElement("div");
      row.className = "jv-row" + (audible[i].whisper ? " jv-whisper" : "");
      var name = document.createElement("span");
      name.className = "jv-name";
      name.textContent = audible[i].name;
      var bar = document.createElement("span");
      bar.className = "jv-bar";
      var fill = document.createElement("span");
      fill.style.transform = "scaleX(" + clamp(audible[i].level * 6, 0.04, 1).toFixed(3) + ")";
      bar.appendChild(fill);

      var silenced = !!state.mutedPeers[audible[i].id];
      var mic = document.createElement("button");
      mic.type = "button";
      mic.className = "jv-mic" + (silenced ? " jv-mic-off" : "");
      mic.innerHTML = silenced ? mutedGlyph() : micGlyph();
      mic.title = (silenced ? "Unmute " : "Mute ") + audible[i].name;
      mic.setAttribute("aria-label", mic.title);
      mic.addEventListener("click", (function (peerId) {
        return function (event) {
          event.preventDefault();
          event.stopPropagation();
          togglePeerMute(peerId);
        };
      })(audible[i].id));

      row.appendChild(mic);
      row.appendChild(name);
      row.appendChild(bar);
      if (silenced) row.className += " jv-row-muted";
      ui.list.appendChild(row);
    }
    ui.root.className = audible.length ? "jv-active" : "jv-idle";
  }

  // ---------------------------------------------------------------- playback

  function peerFor(id) {
    var peer = state.peers[id];
    if (peer) return peer;
    peer = {
      id: id,
      name: "Player",
      level: 0,
      distance: 0,
      whisper: false,
      lastAudioAt: 0,
      node: null,
      gain: null,
      panner: null,
      filter: null,
      caveSend: null,
      env: null,
      decoder: null,
      timestamp: 0
    };
    state.peers[id] = peer;
    return peer;
  }

  /**
   * One small set of shared effects.
   *
   * This used to be two convolution reverbs. They sounded right and cost far too much: on a
   * machine already running the game in the same tab, the audio thread could not keep up and
   * voices broke into artefacts. A short damped feedback delay gives the suggestion of a space
   * for a tiny fraction of the work, which is the correct trade here.
   */
  function ensureEffectBus() {
    if (state.bus || !state.audio) return state.bus;
    var context = state.audio;
    try {
      var master = context.createGain();
      var submerged = context.createBiquadFilter();
      submerged.type = "lowpass";
      submerged.frequency.value = OPEN_AIR_HZ;
      submerged.Q.value = 0.8;
      master.connect(submerged);
      submerged.connect(context.destination);

      var delay = context.createDelay(0.5);
      delay.delayTime.value = 0.115;
      var damp = context.createBiquadFilter();
      damp.type = "lowpass";
      damp.frequency.value = 1200;
      var feedback = context.createGain();
      feedback.gain.value = 0.26;
      delay.connect(damp);
      damp.connect(feedback);
      feedback.connect(delay);
      damp.connect(master);

      state.bus = { master: master, submerged: submerged, cave: delay };
    } catch (error) {
      log("effects unavailable, running dry", error);
      state.bus = null;
    }
    return state.bus;
  }

  function ensurePeerNodes(peer) {
    if (peer.node || !state.audio || !state.workletReady) return peer.node;
    try {
      peer.node = new AudioWorkletNode(state.audio, "jaspr-playback", {
        numberOfInputs: 0,
        numberOfOutputs: 1,
        outputChannelCount: [1],
        processorOptions: { targetSamples: FRAME_SAMPLES * 2, maxSamples: FRAME_SAMPLES * 12 }
      });
      peer.node.port.onmessage = function (event) {
        if (event.data && event.data.type === "level") peer.level = event.data.level;
      };
      peer.panner = state.audio.createPanner();
      peer.panner.panningModel = "HRTF";
      peer.panner.distanceModel = "linear";
      peer.panner.refDistance = 1;
      peer.panner.maxDistance = state.maxDistance;
      peer.panner.rolloffFactor = 1;
      peer.gain = state.audio.createGain();
      peer.gain.gain.value = 1;

      // A single filter carries every muffling effect there is: walls, and being underwater.
      peer.filter = state.audio.createBiquadFilter();
      peer.filter.type = "lowpass";
      peer.filter.frequency.value = OPEN_AIR_HZ;
      peer.filter.Q.value = 0.6;

      peer.node.connect(peer.gain);
      peer.gain.connect(peer.filter);
      peer.filter.connect(peer.panner);

      var bus = ensureEffectBus();
      if (bus) {
        peer.panner.connect(bus.master);
        peer.caveSend = state.audio.createGain();
        peer.caveSend.gain.value = 0;
        peer.panner.connect(peer.caveSend);
        peer.caveSend.connect(bus.cave);
      } else {
        peer.panner.connect(state.audio.destination);
      }
      applyPeerGain(peer);
    } catch (error) {
      log("playback node failed", error);
      peer.node = null;
    }
    return peer.node;
  }

  function positionPeer(peer, right, up, forward) {
    if (!peer.panner) return;
    // WebAudio listeners face -Z with +Y up, so forward maps onto negative Z.
    var x = right, y = up, z = -forward;
    var when = state.audio.currentTime;
    if (peer.panner.positionX) {
      peer.panner.positionX.setTargetAtTime(x, when, 0.02);
      peer.panner.positionY.setTargetAtTime(y, when, 0.02);
      peer.panner.positionZ.setTargetAtTime(z, when, 0.02);
    } else if (peer.panner.setPosition) {
      peer.panner.setPosition(x, y, z);
    }
  }

  /** Muting someone is purely local: the server keeps relaying, this client stops listening. */
  function applyPeerGain(peer) {
    if (!peer.gain) return;
    peer.gain.gain.value = state.mutedPeers[peer.id] ? 0 : 1;
  }

  function togglePeerMute(id) {
    if (state.mutedPeers[id]) delete state.mutedPeers[id];
    else state.mutedPeers[id] = true;
    var peer = state.peers[id];
    if (peer) applyPeerGain(peer);
    renderOverlay();
  }

  /**
   * Applies what the server measured around this speaker. Values glide rather than jump, because
   * a cutoff snapping between two values is audible as a click.
   */
  function applyPeerEnvironment(peer) {
    if (!peer.filter || !state.audio) return;
    var when = state.audio.currentTime;
    var glide = 0.15;
    var env = peer.env;
    var cutoff = OPEN_AIR_HZ;
    var cave = 0;

    if (env) {
      if (env.occluded > 0.02) cutoff = Math.min(cutoff, OPEN_AIR_HZ * Math.pow(0.06, env.occluded));
      if ((env.flags & ENV_SPEAKER_UNDERWATER) !== 0) cutoff = Math.min(cutoff, 380);
      cave = env.cave;
    }

    peer.filter.frequency.setTargetAtTime(clamp(cutoff, 260, OPEN_AIR_HZ), when, glide);
    // Deliberately slight. A cave should read as a hint of space, not as an effect.
    if (peer.caveSend) peer.caveSend.gain.setTargetAtTime(clamp(cave, 0, 1) * 0.20, when, glide);
  }

  function pushPcm(peer, pcm) {
    if (!ensurePeerNodes(peer)) return;
    peer.node.port.postMessage({ type: "pcm", pcm: pcm }, [pcm.buffer]);
  }

  function decodeForPeer(peer, codec, payload) {
    if (codec === CODEC_ADPCM) {
      pushPcm(peer, upsampleToFloat(adpcmDecode(payload)));
      return;
    }
    if (codec !== CODEC_OPUS || !opusSupported()) return;
    if (!peer.decoder) {
      try {
        peer.decoder = new AudioDecoder({
          output: function (audioData) {
            try {
              var frames = audioData.numberOfFrames;
              var pcm = new Float32Array(frames);
              audioData.copyTo(pcm, { planeIndex: 0, format: "f32-planar" });
              pushPcm(peer, pcm);
            } catch (error) {
              log("decode copy failed", error);
            } finally {
              audioData.close();
            }
          },
          error: function (error) {
            log("decoder error", error);
            try { peer.decoder.close(); } catch (ignored) { /* already closed */ }
            peer.decoder = null;
          }
        });
        peer.decoder.configure({
          codec: "opus",
          sampleRate: SAMPLE_RATE,
          numberOfChannels: 1,
          description: OPUS_DESCRIPTION
        });
      } catch (error) {
        log("decoder unavailable", error);
        peer.decoder = null;
        return;
      }
    }
    try {
      peer.decoder.decode(new EncodedAudioChunk({
        type: "key",
        timestamp: peer.timestamp,
        duration: FRAME_MICROS,
        data: payload
      }));
      peer.timestamp += FRAME_MICROS;
    } catch (error) {
      log("decode failed", error);
    }
  }

  function dropPeer(id) {
    var peer = state.peers[id];
    if (!peer) return;
    try { if (peer.decoder) peer.decoder.close(); } catch (ignored) { /* already closed */ }
    try { if (peer.node) peer.node.disconnect(); } catch (ignored) { /* already detached */ }
    try { if (peer.gain) peer.gain.disconnect(); } catch (ignored) { /* already detached */ }
    try { if (peer.panner) peer.panner.disconnect(); } catch (ignored) { /* already detached */ }
    try { if (peer.filter) peer.filter.disconnect(); } catch (ignored) { /* already detached */ }
    try { if (peer.caveSend) peer.caveSend.disconnect(); } catch (ignored) { /* already detached */ }
    delete state.peers[id];
  }

  // ---------------------------------------------------------------- transport

  function voiceUrl() {
    var protocol = location.protocol === "https:" ? "wss:" : "ws:";
    return protocol + "//" + location.host + "/jaspercraft/voice";
  }

  function connect() {
    if (state.socket || state.reconnectTimer) return;
    var socket;
    try {
      socket = new WebSocket(voiceUrl());
    } catch (error) {
      scheduleReconnect();
      return;
    }
    socket.binaryType = "arraybuffer";
    state.socket = socket;

    socket.onopen = function () {
      state.reconnectDelay = 1000;
      state.everConnected = true;
      // Only ask for the microphone once voice is actually reachable, so a player never sees a
      // permission prompt for a feature that cannot work yet.
      if (!state.micRequested) {
        state.micRequested = true;
        startMicrophone();
      }
      var hello = new Uint8Array(3);
      hello[0] = C_HELLO;
      hello[1] = PROTOCOL_VERSION;
      hello[2] = (opusSupported() ? 1 : 0) | 2;
      socket.send(hello);
      sendState();
      renderOverlay();
      log("connected");
    };

    socket.onmessage = function (event) {
      if (typeof event.data === "string") return;
      handleFrame(new Uint8Array(event.data));
    };

    socket.onclose = function () {
      state.socket = null;
      for (var id in state.peers) dropPeer(id);
      renderOverlay();
      scheduleReconnect();
    };

    socket.onerror = function () { try { socket.close(); } catch (ignored) { /* closing */ } };
  }

  function scheduleReconnect() {
    if (state.reconnectTimer) return;
    var delay = state.reconnectDelay;
    state.reconnectDelay = Math.min(state.reconnectDelay * 2, 15000);
    state.reconnectTimer = setTimeout(function () {
      state.reconnectTimer = null;
      connect();
    }, delay);
  }

  function handleFrame(frame) {
    if (!frame.length) return;
    var view = new DataView(frame.buffer, frame.byteOffset, frame.byteLength);
    switch (frame[0]) {
      case S_WELCOME:
        if (frame.length < 10) return;
        state.selfId = view.getUint16(2);
        state.maxDistance = view.getUint16(4) || 48;
        renderOverlay();
        break;
      case S_AUDIO: {
        if (frame.length < AUDIO_HEADER + 1) return;
        var senderId = view.getUint16(1);
        var codec = frame[5];
        var flags = frame[6];
        var right = view.getInt16(7) / POSITION_SCALE;
        var up = view.getInt16(9) / POSITION_SCALE;
        var forward = view.getInt16(11) / POSITION_SCALE;
        var distance = view.getUint16(13) / POSITION_SCALE;
        var peer = peerFor(senderId);
        peer.lastAudioAt = Date.now();
        peer.distance = distance;
        peer.whisper = (flags & 0x01) !== 0;
        ensurePeerNodes(peer);
        if (peer.panner) peer.panner.maxDistance = Math.max(2, frame[15] || state.maxDistance);
        positionPeer(peer, right, up, forward);
        decodeForPeer(peer, codec, frame.subarray(AUDIO_HEADER));
        break;
      }
      case S_ENV: {
        if (frame.length < 3) return;
        var listenerFlags = frame[1];
        var entries = frame[2];
        var at = 3;
        for (var e = 0; e < entries && at + 5 <= frame.length; e++, at += 5) {
          var envPeer = state.peers[view.getUint16(at)];
          if (!envPeer) continue;
          envPeer.env = { flags: frame[at + 2], cave: frame[at + 3] / 255, occluded: frame[at + 4] / 255 };
          applyPeerEnvironment(envPeer);
        }
        if (state.bus) {
          state.bus.submerged.frequency.setTargetAtTime(
            (listenerFlags & ENV_LISTENER_UNDERWATER) !== 0 ? 440 : OPEN_AIR_HZ,
            state.audio.currentTime, 0.15);
        }
        break;
      }
      case S_ROSTER: {
        var count = frame[1];
        var offset = 2;
        var seen = Object.create(null);
        for (var i = 0; i < count && offset + 4 <= frame.length; i++) {
          var id = view.getUint16(offset);
          var nameLength = frame[offset + 3];
          var name = "";
          try {
            name = new TextDecoder().decode(frame.subarray(offset + 4, offset + 4 + nameLength));
          } catch (ignored) { name = "Player"; }
          offset += 4 + nameLength;
          seen[id] = true;
          peerFor(id).name = name;
        }
        for (var existing in state.peers) {
          if (!seen[existing] && Date.now() - state.peers[existing].lastAudioAt > 2000) dropPeer(existing);
        }
        renderOverlay();
        break;
      }
      case S_NOTICE: {
        if (frame.length < 4) return;
        var length = view.getUint16(2);
        var text = "";
        try { text = new TextDecoder().decode(frame.subarray(4, 4 + length)); } catch (ignored) { text = ""; }
        if (text) setNotice(text, 6000);
        break;
      }
      case S_PONG:
      default:
        break;
    }
  }

  function sendState() {
    if (!state.socket || state.socket.readyState !== 1) return;
    var frame = new Uint8Array(2);
    frame[0] = C_STATE;
    frame[1] = (state.muted ? STATE_MIC_MUTED : 0);
    state.socket.send(frame);
  }

  function sendAudio(codec, payload) {
    if (!state.socket || state.socket.readyState !== 1) return;
    var frame = new Uint8Array(6 + payload.length);
    frame[0] = C_AUDIO;
    state.sequence = (state.sequence + 1) & 0xffff;
    frame[1] = state.sequence >>> 8;
    frame[2] = state.sequence & 0xff;
    frame[3] = codec;
    frame[4] = payload.length >>> 8;
    frame[5] = payload.length & 0xff;
    frame.set(payload, 6);
    state.socket.send(frame);
  }

  // ---------------------------------------------------------------- capture

  function emitEncoded(payload) {
    if (state.muted) return;
    if (state.speaking) {
      sendAudio(state.codec, payload);
      return;
    }
    state.preroll.push(payload);
    if (state.preroll.length > PREROLL_FRAMES) state.preroll.shift();
  }

  function openGate() {
    if (state.speaking) return;
    state.speaking = true;
    for (var i = 0; i < state.preroll.length; i++) sendAudio(state.codec, state.preroll[i]);
    state.preroll.length = 0;
    renderOverlay();
  }

  function closeGate() {
    if (!state.speaking) return;
    state.speaking = false;
    renderOverlay();
  }

  function onCapturedFrame(pcm, rms) {
    state.micLevel = state.micLevel * 0.7 + rms * 0.3;
    if (!state.speaking) state.noiseFloor = state.noiseFloor * 0.995 + rms * 0.005;
    var threshold = Math.max(0.0055, state.noiseFloor * 2.6);
    var now = Date.now();
    if (rms > threshold) { state.lastVoiceAt = now; openGate(); }
    else if (state.speaking && now - state.lastVoiceAt > RELEASE_MS) closeGate();

    if (state.muted) { state.preroll.length = 0; return; }

    if (state.codec === CODEC_OPUS && state.encoder) {
      try {
        var data = new AudioData({
          format: "f32-planar",
          sampleRate: SAMPLE_RATE,
          numberOfFrames: pcm.length,
          numberOfChannels: 1,
          timestamp: state.timestamp,
          data: pcm
        });
        state.encoder.encode(data);
        data.close();
        state.timestamp += FRAME_MICROS;
      } catch (error) {
        log("encode failed", error);
      }
    } else {
      emitEncoded(adpcmEncode(downsampleToInt16(pcm)));
    }
  }

  function startEncoder() {
    if (!opusSupported()) { state.codec = CODEC_ADPCM; return; }
    try {
      var encoder = new AudioEncoder({
        output: function (chunk) {
          var payload = new Uint8Array(chunk.byteLength);
          chunk.copyTo(payload);
          emitEncoded(payload);
        },
        error: function (error) {
          log("encoder error; falling back", error);
          state.codec = CODEC_ADPCM;
          state.encoder = null;
        }
      });
      var config = {
        codec: "opus",
        sampleRate: SAMPLE_RATE,
        numberOfChannels: 1,
        bitrate: 24000,
        opus: { frameDuration: FRAME_MICROS, application: "voip" }
      };
      try {
        encoder.configure(config);
      } catch (unsupported) {
        delete config.opus;
        encoder.configure(config);
      }
      state.encoder = encoder;
      state.codec = CODEC_OPUS;
    } catch (error) {
      log("opus unavailable; using adpcm", error);
      state.codec = CODEC_ADPCM;
      state.encoder = null;
    }
  }

  function hasMicrophone() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) return Promise.resolve(true);
    return navigator.mediaDevices.enumerateDevices().then(function (devices) {
      for (var i = 0; i < devices.length; i++) if (devices[i].kind === "audioinput") return true;
      return false;
    }).catch(function () { return true; });
  }

  function startMicrophone() {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      state.micStatus = "absent";
      renderOverlay();
      return;
    }
    hasMicrophone().then(function (present) {
      if (!present) {
        state.micStatus = "absent";
        setNotice("No microphone found — you can still hear everyone", 7000);
        return;
      }
      return navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }
      }).then(function (stream) {
        state.micStream = stream;
        state.micStatus = "live";
        return attachCapture(stream);
      }).catch(function (error) {
        state.micStatus = error && error.name === "NotAllowedError" ? "denied" : "absent";
        if (state.micStatus === "denied") {
          setNotice("Microphone blocked — listening only", 8000);
          // The permission prompt can only reappear from a user gesture.
          window.addEventListener("pointerdown", retryMicrophoneOnce, { once: true, capture: true });
        }
        renderOverlay();
      });
    }).then(renderOverlay);
  }

  function retryMicrophoneOnce() {
    if (state.micStatus === "live") return;
    startMicrophone();
  }

  function attachCapture(stream) {
    return ensureAudioContext().then(function (context) {
      if (context.state === "suspended") context.resume().catch(function () { /* resumed on first input */ });
      var source = context.createMediaStreamSource(stream);
      var node = new AudioWorkletNode(context, "jaspr-capture", {
        numberOfInputs: 1,
        numberOfOutputs: 1,
        outputChannelCount: [1]
      });
      node.port.onmessage = function (event) {
        var data = event.data;
        if (data && data.type === "frame") onCapturedFrame(data.pcm, data.rms);
      };
      var sink = context.createGain();
      sink.gain.value = 0;
      source.connect(node);
      node.connect(sink);
      sink.connect(context.destination);
      state.captureNode = node;
      startEncoder();
      renderOverlay();
    });
  }

  function ensureAudioContext() {
    if (state.audioReady) return state.audioReady;
    var Constructor = window.AudioContext || window.webkitAudioContext;
    if (!Constructor) return Promise.reject(new Error("Web Audio is unavailable."));
    var context;
    try {
      context = new Constructor({ sampleRate: SAMPLE_RATE, latencyHint: "interactive" });
    } catch (error) {
      context = new Constructor();
    }
    state.audio = context;
    var moduleUrl = "jaspercraft-voice-worklet.js?build=" + encodeURIComponent(buildTag());
    state.audioReady = context.audioWorklet.addModule(moduleUrl).then(function () {
      if (context.state === "suspended") context.resume().catch(function () { /* resumed on first input */ });
      state.workletReady = true;
      return context;
    });
    return state.audioReady;
  }

  function buildTag() {
    var scripts = document.getElementsByTagName("script");
    for (var i = 0; i < scripts.length; i++) {
      var match = /jaspercraft-voice\.js\?build=([^"&]+)/.exec(scripts[i].src || "");
      if (match) return decodeURIComponent(match[1]);
    }
    return "1";
  }

  // ---------------------------------------------------------------- lifecycle

  function tick() {
    var now = Date.now();
    for (var id in state.peers) {
      var peer = state.peers[id];
      if (now - peer.lastAudioAt > 800) peer.level = peer.level * 0.6;
    }
    if (!state.speaking) state.micLevel = state.micLevel * 0.85;
    renderOverlay();
  }

  function onKeyDown(event) {
    if (event.code !== MUTE_KEY || event.ctrlKey || event.altKey || event.metaKey) return;
    var target = event.target;
    if (target && /^(INPUT|TEXTAREA)$/.test(target.tagName || "")) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    toggleMute();
  }

  function boot() {
    if (state.started) return;
    state.started = true;
    buildOverlay();
    renderOverlay();
    connect();
    // Hearing never depends on the microphone: playback comes up regardless, and the microphone
    // is requested once the relay answers (see socket.onopen).
    ensureAudioContext().catch(function (error) { log("audio context unavailable", error); });
    setInterval(tick, 200);
    window.addEventListener("keydown", onKeyDown, true);
    window.addEventListener("pagehide", function () {
      if (state.socket) try { state.socket.close(); } catch (ignored) { /* closing */ }
    });
  }

  // The overlay belongs to the game frame only, and only once the client actually starts.
  if (window.JasperCraftClient && typeof window.JasperCraftClient.start === "function") {
    var originalStart = window.JasperCraftClient.start;
    window.JasperCraftClient.start = function (session) {
      var result = originalStart.apply(this, arguments);
      try { setTimeout(boot, 1500); } catch (error) { log("boot failed", error); }
      return result;
    };
  }

  window.JasprVoiceDiagnostics = {
    status: function () {
      var peers = [];
      for (var id in state.peers) {
        peers.push({
          name: state.peers[id].name,
          distance: Math.round(state.peers[id].distance * 10) / 10,
          whisper: state.peers[id].whisper
        });
      }
      return {
        connected: Boolean(state.socket && state.socket.readyState === 1),
        codec: state.codec === CODEC_OPUS ? "opus" : state.codec === CODEC_ADPCM ? "adpcm" : "none",
        audio: state.audio ? state.audio.state : "none",
        sampleRate: state.audio ? state.audio.sampleRate : 0,
        spatialAudioReady: state.workletReady,
        microphone: state.micStatus,
        muted: state.muted,
        speaking: state.speaking,
        maxDistance: state.maxDistance,
        audible: peers
      };
    }
  };
})();
