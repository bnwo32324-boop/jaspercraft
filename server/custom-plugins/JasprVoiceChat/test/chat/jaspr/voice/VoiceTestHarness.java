package chat.jaspr.voice;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Offline fixture: starts the real relay with scripted player positions so the WebSocket
 * handshake, identity check, proximity culling and listener geometry can be exercised
 * without a Paper server. Never shipped in the plugin jar.
 *
 * Usage: VoiceTestHarness <port> <keyFile>
 */
public final class VoiceTestHarness {
    static final UUID OVERWORLD = UUID.nameUUIDFromBytes("overworld".getBytes(StandardCharsets.UTF_8));
    static final UUID NETHER = UUID.nameUUIDFromBytes("nether".getBytes(StandardCharsets.UTF_8));

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        Path keyFile = Paths.get(args[1]);

        final Map<String, PlayerSnapshot> world = new HashMap<String, PlayerSnapshot>();
        // Listener faces +Z (yaw 0) at the origin.
        world.put("listener", new PlayerSnapshot("listener", OVERWORLD, 0, 64, 0, 0f, false, false, false));
        // Ten blocks due south: directly in front of the listener.
        world.put("ahead", new PlayerSnapshot("ahead", OVERWORLD, 0, 64, 10, 0f, false, false, false));
        // Ten blocks east: on the listener's left while facing south.
        world.put("east", new PlayerSnapshot("east", OVERWORLD, 10, 64, 0, 0f, false, false, false));
        // Far away in the same world.
        world.put("distant", new PlayerSnapshot("distant", OVERWORLD, 400, 64, 0, 0f, false, false, false));
        // Close by, but in another world.
        world.put("nether", new PlayerSnapshot("nether", NETHER, 1, 64, 1, 0f, false, false, false));
        // Close by and sneaking, so only audible inside the whisper radius.
        world.put("sneaker", new PlayerSnapshot("sneaker", OVERWORLD, 0, 64, 30, 0f, true, false, false));
        // Sneaking and close: audible, and marked as a whisper.
        world.put("whisperer", new PlayerSnapshot("whisperer", OVERWORLD, 0, 64, 5, 0f, true, false, false));

        SnapshotSource source = new SnapshotSource() {
            @Override public PlayerSnapshot get(String playerName) {
                return world.get(PositionIndex.key(playerName));
            }
        };

        File configFile = File.createTempFile("jaspr-voice-test", ".yml");
        configFile.deleteOnExit();
        Files.write(configFile.toPath(), ("port: " + port + "\nmax-distance: 48.0\nwhisper-distance: 12.0\n")
                .getBytes(StandardCharsets.UTF_8));
        VoiceConfig settings = new VoiceConfig(YamlConfiguration.loadConfiguration(configFile));

        VoiceLog log = new VoiceLog(Logger.getLogger("jaspr-voice-test"));
        final VoiceHub hub = new VoiceHub(settings, source, log);
        VoiceServer server = new VoiceServer(port, new EdgeAuth(keyFile), log, new VoiceServer.Acceptor() {
            @Override public void accepted(WebSocketConnection connection, String playerName) {
                hub.attach(connection, playerName);
                connection.readLoop(hub);
            }
        });
        server.start();
        System.out.println("READY " + port);
        System.out.flush();
        Thread.sleep(60_000L);
        server.stop();
    }
}
