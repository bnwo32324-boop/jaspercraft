// Exercises the fallback codec and resamplers that ship in jaspercraft-voice.js.
import fs from "node:fs";

// test/ -> JasprVoiceChat/ -> custom-plugins/ -> server/ -> project root -> site/
const overlay = new URL("../../../../site/jaspercraft-voice.js", import.meta.url);
const source = fs.readFileSync(overlay, "utf8");
const body = source.replace(/^\(function \(\) \{/, "").replace(/\}\)\(\);\s*$/, "");
const expose = `
  return { adpcmEncode, adpcmDecode, downsampleToInt16, upsampleToFloat,
           OPUS_DESCRIPTION, FRAME_SAMPLES, ADPCM_SAMPLES };`;
const factory = new Function("window", "document", "navigator", "location", "setInterval", body + expose);
const api = factory({ console }, { getElementsByTagName: () => [] }, {}, { protocol: "https:", host: "x" }, () => {});

const failures = [];
const check = (name, ok, detail = "") => {
  console.log((ok ? "  PASS  " : "  FAIL  ") + name + (detail ? "  -> " + detail : ""));
  if (!ok) failures.push(name);
};

// A 20 ms frame of speech-band tone at 48 kHz.
const frame = new Float32Array(api.FRAME_SAMPLES);
for (let i = 0; i < frame.length; i++) {
  frame[i] = 0.5 * Math.sin((2 * Math.PI * 440 * i) / 48000)
           + 0.2 * Math.sin((2 * Math.PI * 1200 * i) / 48000);
}

console.log("\nFraming");
const narrow = api.downsampleToInt16(frame);
check("48 kHz frame downsamples to a 20 ms 16 kHz frame", narrow.length === api.ADPCM_SAMPLES, String(narrow.length));

const packet = api.adpcmEncode(narrow);
check("packet is 4-byte header plus 4 bits per sample", packet.length === 4 + api.ADPCM_SAMPLES / 2, String(packet.length));
check("packet fits well inside the relay's size guard", packet.length < 700, String(packet.length));

console.log("\nRound trip");
const decoded = api.adpcmDecode(packet);
check("decode returns the same sample count", decoded.length === narrow.length, String(decoded.length));

let signal = 0, noise = 0;
for (let i = 0; i < narrow.length; i++) {
  signal += narrow[i] * narrow[i];
  const error = narrow[i] - decoded[i];
  noise += error * error;
}
const snr = 10 * Math.log10(signal / Math.max(noise, 1e-9));
check("ADPCM round trip keeps usable quality (>18 dB SNR)", snr > 18, snr.toFixed(1) + " dB");

const wide = api.upsampleToFloat(decoded);
check("upsampled frame is back to 20 ms at 48 kHz", Math.abs(wide.length - api.FRAME_SAMPLES) <= 1, String(wide.length));

let peakIn = 0, peakOut = 0;
for (let i = 0; i < frame.length; i++) peakIn = Math.max(peakIn, Math.abs(frame[i]));
for (let i = 0; i < wide.length; i++) peakOut = Math.max(peakOut, Math.abs(wide[i]));
check("amplitude survives the round trip", Math.abs(peakIn - peakOut) < 0.12, `${peakIn.toFixed(3)} -> ${peakOut.toFixed(3)}`);

console.log("\nSilence and clipping");
const silence = api.adpcmDecode(api.adpcmEncode(new Int16Array(api.ADPCM_SAMPLES)));
let maxSilence = 0;
for (let i = 0; i < silence.length; i++) maxSilence = Math.max(maxSilence, Math.abs(silence[i]));
check("silence stays silent", maxSilence <= 4, String(maxSilence));

const loud = new Int16Array(api.ADPCM_SAMPLES).fill(32767);
const loudBack = api.adpcmDecode(api.adpcmEncode(loud));
let overflow = false;
for (let i = 0; i < loudBack.length; i++) if (loudBack[i] > 32767 || loudBack[i] < -32768) overflow = true;
check("full-scale input never overflows", !overflow);

console.log("\nOpus header");
const head = api.OPUS_DESCRIPTION;
check("magic is OpusHead", String.fromCharCode(...head.slice(0, 8)) === "OpusHead");
check("channel count is mono", head[9] === 1, String(head[9]));
check("pre-skip is 3840", head[10] | (head[11] << 8), (head[10] | (head[11] << 8)) === 3840 ? "3840" : String(head[10] | (head[11] << 8)));
const rate = head[12] | (head[13] << 8) | (head[14] << 16) | (head[15] << 24);
check("sample rate is 48000", rate === 48000, String(rate));

console.log("\n" + (failures.length ? `${failures.length} FAILED: ${failures}` : "ALL CHECKS PASSED"));
process.exit(failures.length ? 1 : 0);
