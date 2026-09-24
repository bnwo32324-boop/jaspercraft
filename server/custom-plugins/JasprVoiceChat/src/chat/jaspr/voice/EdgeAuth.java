package chat.jaspr.voice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The Jaspr gateway is the only identity authority. It signs the account name it already
 * authenticated, using the shared bridge key, and this plugin verifies that signature.
 * A player name that arrives unsigned, expired, or from a non-loopback peer is never trusted.
 */
final class EdgeAuth {
    static final String HEADER_PLAYER = "x-jaspr-voice-player";
    static final String HEADER_EXPIRES = "x-jaspr-voice-expires";
    static final String HEADER_SIGNATURE = "x-jaspr-voice-auth";

    private final Path keyFile;
    private volatile String cachedKey;
    private volatile long cachedAt;

    EdgeAuth(Path keyFile) { this.keyFile = keyFile; }

    void checkKey() throws IOException { readKey(); }

    private String readKey() throws IOException {
        long now = System.currentTimeMillis();
        String cached = cachedKey;
        if (cached != null && now - cachedAt < 30_000L) return cached;
        if (!Files.isRegularFile(keyFile) || Files.size(keyFile) > 1024) throw new IOException("Bridge key unavailable.");
        String key = new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim();
        if (!key.matches("[a-f0-9]{64}")) throw new IOException("Bridge key invalid.");
        cachedKey = key;
        cachedAt = now;
        return key;
    }

    /**
     * Returns the verified account name, or null when the request is not a trustworthy
     * gateway hand-off. Never returns a name taken directly from the request.
     */
    String verify(Map<String, String> headers, boolean loopbackPeer) {
        if (!loopbackPeer) return null;
        String player = headers.get(HEADER_PLAYER);
        String expires = headers.get(HEADER_EXPIRES);
        String signature = headers.get(HEADER_SIGNATURE);
        if (player == null || expires == null || signature == null) return null;
        if (!player.matches("[A-Za-z0-9_]{3,16}") || !expires.matches("[0-9]{13}")) return null;
        long deadline;
        try { deadline = Long.parseLong(expires); } catch (NumberFormatException invalid) { return null; }
        long now = System.currentTimeMillis();
        if (now > deadline || deadline - now > 120_000L) return null;
        String expected;
        try { expected = sign(player + "\n" + expires); } catch (Exception unavailable) { return null; }
        return constantTimeEquals(expected, signature.toLowerCase(Locale.ROOT)) ? player : null;
    }

    private String sign(String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(readKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16));
            hex.append(Character.forDigit(value & 0xF, 16));
        }
        return hex.toString();
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) return false;
        int difference = 0;
        for (int i = 0; i < left.length(); i++) difference |= left.charAt(i) ^ right.charAt(i);
        return difference == 0;
    }
}
