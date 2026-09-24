package chat.jaspr.voice;

/** One connected browser overlay, already bound to a gateway-verified account name. */
final class VoiceSession {
    final int id;
    final String playerName;
    final WebSocketConnection connection;
    final long connectedAt = System.currentTimeMillis();

    volatile boolean micMuted;
    volatile boolean deafened;
    volatile long lastAudioAt;
    volatile boolean helloReceived;
    /** Protocol the overlay announced. Stale tabs are version 1 and must not get v2 frames. */
    volatile int protocolVersion = 1;

    private long windowStartedAt;
    private int packetsInWindow;

    VoiceSession(int id, String playerName, WebSocketConnection connection) {
        this.id = id;
        this.playerName = playerName;
        this.connection = connection;
    }

    /** Simple fixed-window limiter so one client cannot flood the relay. */
    synchronized boolean allowPacket(int maxPerSecond) {
        long now = System.currentTimeMillis();
        if (now - windowStartedAt >= 1000L) {
            windowStartedAt = now;
            packetsInWindow = 0;
        }
        if (packetsInWindow >= maxPerSecond) return false;
        packetsInWindow++;
        return true;
    }

    boolean isSpeakingRecently(long now) { return now - lastAudioAt < 400L; }
}
