// Audio worklets for JasperCraft proximity voice.
// Capture packs the microphone into fixed 20 ms frames and measures loudness; playback drains a
// small jitter buffer so that a late or missing packet becomes a short silence rather than a click.

const FRAME_SAMPLES = 960; // 20 ms at 48 kHz

class JasprCaptureProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.buffer = new Float32Array(FRAME_SAMPLES);
    this.filled = 0;
  }

  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (!channel) return true;
    for (let i = 0; i < channel.length; i++) {
      this.buffer[this.filled++] = channel[i];
      if (this.filled === FRAME_SAMPLES) {
        let sum = 0;
        let peak = 0;
        for (let s = 0; s < FRAME_SAMPLES; s++) {
          const value = this.buffer[s];
          sum += value * value;
          const magnitude = value < 0 ? -value : value;
          if (magnitude > peak) peak = magnitude;
        }
        const frame = this.buffer;
        this.buffer = new Float32Array(FRAME_SAMPLES);
        this.filled = 0;
        this.port.postMessage(
          { type: 'frame', pcm: frame, rms: Math.sqrt(sum / FRAME_SAMPLES), peak },
          [frame.buffer]
        );
      }
    }
    return true;
  }
}

class JasprPlaybackProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super();
    const settings = (options && options.processorOptions) || {};
    this.queue = [];
    this.queuedSamples = 0;
    this.offset = 0;
    this.primed = false;
    // Hold this many samples before starting, and never let the buffer grow past the ceiling.
    this.targetSamples = settings.targetSamples || FRAME_SAMPLES * 2;
    this.maxSamples = settings.maxSamples || FRAME_SAMPLES * 12;
    this.level = 0;
    this.reportCounter = 0;
    this.port.onmessage = (event) => {
      const data = event.data;
      if (!data) return;
      if (data.type === 'pcm') {
        this.queue.push(data.pcm);
        this.queuedSamples += data.pcm.length;
        // Late audio is worthless: if we fell far behind, drop the oldest frames and resync.
        while (this.queuedSamples > this.maxSamples && this.queue.length > 1) {
          const dropped = this.queue.shift();
          this.queuedSamples -= dropped.length - (this.queue.length === 0 ? this.offset : 0);
          this.offset = 0;
        }
      } else if (data.type === 'flush') {
        this.queue.length = 0;
        this.queuedSamples = 0;
        this.offset = 0;
        this.primed = false;
      }
    };
  }

  process(_inputs, outputs) {
    const output = outputs[0][0];
    if (!output) return true;
    if (!this.primed && this.queuedSamples < this.targetSamples) {
      output.fill(0);
      return true;
    }
    this.primed = true;

    let written = 0;
    let energy = 0;
    while (written < output.length) {
      const head = this.queue[0];
      if (!head) break;
      const available = head.length - this.offset;
      const need = output.length - written;
      const take = available < need ? available : need;
      for (let i = 0; i < take; i++) {
        const value = head[this.offset + i];
        output[written + i] = value;
        energy += value * value;
      }
      written += take;
      this.offset += take;
      this.queuedSamples -= take;
      if (this.offset >= head.length) {
        this.queue.shift();
        this.offset = 0;
      }
    }
    if (written < output.length) {
      output.fill(0, written);
      this.primed = false; // Re-prime after an underrun so we do not stutter repeatedly.
    }

    const frameLevel = written > 0 ? Math.sqrt(energy / written) : 0;
    this.level = this.level * 0.8 + frameLevel * 0.2;
    if (++this.reportCounter >= 8) {
      this.reportCounter = 0;
      this.port.postMessage({ type: 'level', level: this.level });
    }
    return true;
  }
}

registerProcessor('jaspr-capture', JasprCaptureProcessor);
registerProcessor('jaspr-playback', JasprPlaybackProcessor);
