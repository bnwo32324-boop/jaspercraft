package chat.jaspr.voice;

/** Wire protocol shared by the browser overlay and this plugin. Big-endian, binary WebSocket frames. */
final class VoiceProtocol {
    private VoiceProtocol() {}

    static final int VERSION = 3;

    /**
     * Audio header size. This is fixed forever at the original sixteen bytes.
     *
     * Briefly it was nineteen, to carry acoustics inline, and that was a mistake: any overlay a
     * player had cached read the payload from the wrong offset and decoded pure noise. Acoustics
     * now travel as their own message instead, which an older overlay simply ignores, so the audio
     * path can never again be misread by a tab somebody left open.
     */
    static final int AUDIO_HEADER = 16;

    // Client -> server
    static final int C_HELLO = 0x01;
    static final int C_AUDIO = 0x02;
    static final int C_STATE = 0x03;
    static final int C_PING  = 0x04;

    // Server -> client
    static final int S_WELCOME = 0x81;
    static final int S_AUDIO   = 0x82;
    static final int S_ROSTER  = 0x83;
    static final int S_PONG    = 0x84;
    static final int S_NOTICE  = 0x85;
    static final int S_ENV     = 0x86;

    // Codec identifiers. The relay never inspects payload bytes.
    static final int CODEC_OPUS  = 1;
    static final int CODEC_ADPCM = 2;

    // Audio flags
    static final int FLAG_WHISPER = 0x01;

    // Environment flags, byte 16 of an audio frame.
    static final int ENV_SPEAKER_UNDERWATER  = 0x01;
    static final int ENV_LISTENER_UNDERWATER = 0x02;
    static final int ENV_SPEAKER_ENCLOSED    = 0x04;

    /** Packs a 0..1 acoustic amount into one byte. */
    static byte toAmount(double value) {
        if (value <= 0.0d || Double.isNaN(value)) return 0;
        if (value >= 1.0d) return (byte) 255;
        return (byte) Math.round(value * 255.0d);
    }

    // Client state flags
    static final int STATE_MIC_MUTED = 0x01;
    static final int STATE_DEAFENED  = 0x02;

    // Roster flags
    static final int PEER_MUTED    = 0x01;
    static final int PEER_DEAFENED = 0x02;
    static final int PEER_IN_RANGE = 0x04;

    /** Positions travel as signed 1/32-block fixed point, which covers +/-1024 blocks. */
    static final double POSITION_SCALE = 32.0d;

    static short toFixed(double blocks) {
        double scaled = blocks * POSITION_SCALE;
        if (scaled > Short.MAX_VALUE) scaled = Short.MAX_VALUE;
        if (scaled < Short.MIN_VALUE) scaled = Short.MIN_VALUE;
        return (short) Math.round(scaled);
    }
}
