package local.eagler.testserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** The Jaspr gateway is the sole ticket store and identity authority. */
final class TicketVerifier {
    static final String CONSUME_URL = "http://127.0.0.1:3200/api/jaspercraft/internal/consume";
    static final String SOCKET_PATH = "/jaspercraft/socket";
    private final URL endpoint;
    private final Path keyFile;
    private final Semaphore requests = new Semaphore(8);

    TicketVerifier(Path keyFile) throws IOException { this(keyFile, new URL(CONSUME_URL)); }
    TicketVerifier(Path keyFile, URL endpoint) { this.keyFile = keyFile; this.endpoint = endpoint; }

    static String ticketFromPath(String path) throws IOException {
        try {
            if (path == null || path.length() > 1024) throw new IllegalArgumentException();
            URI uri = new URI(path);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null
                    || !SOCKET_PATH.equals(uri.getRawPath())) throw new IllegalArgumentException();
            String query = uri.getRawQuery();
            if (query == null || !query.matches("ticket=[A-Za-z0-9_-]{32,512}")) throw new IllegalArgumentException();
            return query.substring(7);
        } catch (Exception invalid) { throw new IOException("Open JasperCraft through Jaspr.chat."); }
    }

    void checkKey() throws IOException { readKey(); }
    private String readKey() throws IOException {
        if (!Files.isRegularFile(keyFile) || Files.size(keyFile) > 1024) throw new IOException("Bridge key unavailable.");
        String key = new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8).trim();
        if (!key.matches("[!-~]{32,512}")) throw new IOException("Bridge key invalid.");
        return key;
    }

    /**
     * The ticket is the identity. Nothing the client claims about itself is
     * consulted here: a browser that has an Eaglercraft profile of its own
     * sends that profile's name, and comparing it against the account would
     * reject the player for having once typed a name into the game's own
     * profile screen. The caller overwrites the claim instead.
     */
    Identity consume(String path) throws IOException {
        String ticket = ticketFromPath(path);
        if (!requests.tryAcquire()) throw new IOException("Sign-in busy. Please reconnect.");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2500);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Jaspr-Craft-Key", readKey());
            byte[] body = ("{\"ticket\":\"" + ticket + "\"}").getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            // The status code says which of several very different things happened --
            // an expired ticket, a refused bridge key, a gateway that is not up --
            // and a status code is not a secret. Carrying it lets the game server
            // name the cause in its log instead of guessing.
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Sign-in rejected:" + status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[1024]; int size;
                while ((size = input.read(chunk)) != -1) {
                    if (bytes.size() + size > 8192) throw new IOException("Invalid sign-in response.");
                    bytes.write(chunk, 0, size);
                }
            }
            return parseIdentity(new String(bytes.toByteArray(), StandardCharsets.UTF_8), System.currentTimeMillis());
        } catch (RuntimeException invalid) { throw new IOException("Invalid sign-in response."); }
        finally { if (connection != null) connection.disconnect(); requests.release(); }
    }

    static Identity parseIdentity(String json, long now) throws IOException {
        try {
            JsonObject data = new JsonParser().parse(json).getAsJsonObject();
            JsonElement userId = data.get("userId"), name = data.get("gameName"), privileged = data.get("privileged");
            if (userId == null || !userId.isJsonPrimitive()
                    || !(userId.getAsJsonPrimitive().isString() || userId.getAsJsonPrimitive().isNumber())
                    || userId.getAsString().isEmpty() || userId.getAsString().length() > 128
                    || name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                    || !name.getAsString().matches("[A-Za-z0-9_]{3,16}")
                    || privileged == null || !privileged.isJsonPrimitive() || !privileged.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException();
            JsonElement expires = data.get("expiresAt"); long deadline;
            if (expires.getAsJsonPrimitive().isNumber()) {
                deadline = expires.getAsBigDecimal().longValueExact();
                if (deadline < 100000000000L) deadline = Math.multiplyExact(deadline, 1000L);
            } else { deadline = Instant.parse(expires.getAsString()).toEpochMilli(); }
            // Upper bound on the ticket lifetime the gateway is allowed to mint, kept
            // clear of it so the two never disagree. A first load pulls ~15 MB before
            // the socket opens, which on a slow or tunnelled link outruns a short one.
            if (deadline <= now || deadline > now + 900000L) throw new IllegalArgumentException();
            String gameName = name.getAsString(); boolean isPrivileged = privileged.getAsBoolean();
            if (gameName.equalsIgnoreCase("jasper") && (!gameName.equals("jasper") || !isPrivileged)) throw new IllegalArgumentException();
            return new Identity(userId.getAsString(), gameName, isPrivileged, deadline);
        } catch (RuntimeException invalid) { throw new IOException("Invalid sign-in identity."); }
    }

    static final class Identity {
        final String userId, gameName;
        final boolean privileged;
        final long expiresAt;
        Identity(String userId, String gameName, boolean privileged, long expiresAt) {
            this.userId = userId; this.gameName = gameName; this.privileged = privileged; this.expiresAt = expiresAt;
        }
        UUID uuid() { return UUID.nameUUIDFromBytes(("OfflinePlayer:" + gameName).getBytes(StandardCharsets.UTF_8)); }
        void requirePlayer(String name, UUID uuid) throws IOException {
            if (!gameName.equals(name) || !uuid().equals(uuid)) throw new IOException("Character does not match the Jaspr account.");
        }
    }
}
