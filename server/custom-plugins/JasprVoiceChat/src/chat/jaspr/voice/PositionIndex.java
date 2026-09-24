package chat.jaspr.voice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Bukkit state may only be touched from the main thread, but audio arrives on network threads.
 * A repeating main-thread task publishes an immutable map that the relay reads without locking.
 *
 * As well as position, this works out what each player's surroundings should do to their voice:
 * whether their head is underwater, how much the space around them rings back, and how much solid
 * world sits between any two people who can hear each other. Acoustics change far more slowly than
 * positions do, so they are recomputed on a slower cycle and carried forward in between.
 */
final class PositionIndex implements Runnable, SnapshotSource {
    /** Probe directions for measuring enclosure: the cardinals, their diagonals, up and down. */
    private static final int[][] PROBES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, 0}, {0, -1, 0}
    };

    private final org.bukkit.Server server;
    private final VoiceConfig config;
    private volatile Map<String, PlayerSnapshot> snapshots = new HashMap<String, PlayerSnapshot>();
    private volatile Map<String, Float> occlusion = new HashMap<String, Float>();
    private final Map<String, float[]> acoustics = new HashMap<String, float[]>();
    private int cycle;

    PositionIndex(org.bukkit.Server server, VoiceConfig config) {
        this.server = server;
        this.config = config;
    }

    @Override public void run() {
        Collection<? extends Player> players = server.getOnlinePlayers();
        boolean refresh = config.environmentEnabled && (cycle++ % Math.max(1, config.acousticIntervalTicks)) == 0;

        Map<String, PlayerSnapshot> next = new HashMap<String, PlayerSnapshot>(Math.max(8, players.size() * 2));
        List<PlayerSnapshot> audible = new ArrayList<PlayerSnapshot>();

        for (Player player : players) {
            Location eye = player.getEyeLocation();
            if (eye.getWorld() == null) continue;
            String key = key(player.getName());

            float[] env = acoustics.get(key);
            if (refresh || env == null) {
                env = measure(eye);
                acoustics.put(key, env);
            }

            PlayerSnapshot snapshot = new PlayerSnapshot(
                    player.getName(),
                    eye.getWorld().getUID(),
                    eye.getX(), eye.getY(), eye.getZ(),
                    eye.getYaw(),
                    player.isSneaking(),
                    player.getGameMode() == GameMode.SPECTATOR,
                    player.isDead(),
                    env[0] > 0.5f,
                    env[1],
                    env[2] > 0.5f);
            next.put(key, snapshot);
            if (audible.size() < config.maxClients) audible.add(snapshot);
        }

        acoustics.keySet().retainAll(next.keySet());
        snapshots = next;
        if (refresh) occlusion = measureOcclusion(audible);
    }

    @Override public PlayerSnapshot get(String playerName) {
        return playerName == null ? null : snapshots.get(key(playerName));
    }

    @Override public float occlusion(String speakerName, String listenerName) {
        if (speakerName == null || listenerName == null) return 0f;
        Float value = occlusion.get(pairKey(key(speakerName), key(listenerName)));
        return value == null ? 0f : value.floatValue();
    }

    // ------------------------------------------------------------------ acoustics

    /** Returns {underwater, reverb, enclosed} for one head position. */
    private float[] measure(Location eye) {
        World world = eye.getWorld();
        Material head = eye.getBlock().getType();
        boolean water = head == Material.WATER || head == Material.STATIONARY_WATER;

        // How far below the surface this head is. Under a tree or a roof this is a block or two;
        // in a cave it is properly deep, and that difference is what earns a long tail.
        int depth = world.getHighestBlockYAt(eye.getBlockX(), eye.getBlockZ()) - eye.getBlockY();

        int hits = 0;
        for (int[] probe : PROBES) {
            if (blocked(world, eye, probe[0], probe[1], probe[2], config.probeReach)) hits++;
        }
        float enclosure = (float) hits / (float) PROBES.length;

        // Standing on open ground always trips the downward probe, and a tree or a wall trips a
        // couple more, so raw enclosure is never zero outdoors. Everything below this floor is
        // treated as no enclosure at all, which is what keeps a field and a forest dry.
        float shaped = enclosure <= 0.45f ? 0f : (enclosure - 0.45f) / 0.55f;

        // Reverb belongs to caves and nowhere else. Above ground, under a roof, under a tree:
        // all of it stays completely dry. Only genuinely deep, genuinely enclosed space rings, and
        // even then only a little - a hint of a space, not a cathedral.
        float reverb;
        boolean cave;
        if (depth < config.caveDepth || shaped <= 0f) {
            reverb = 0f;
            cave = false;
        } else {
            float deep = Math.min(1.0f, (depth - config.caveDepth) / 24.0f);
            reverb = shaped * (0.30f + 0.35f * deep);
            cave = true;
        }
        if (water) reverb *= 0.6f;
        return new float[]{ water ? 1f : 0f, reverb * (float) config.reverbStrength, cave ? 1f : 0f };
    }

    private static boolean blocked(World world, Location eye, int dx, int dy, int dz, int reach) {
        for (int step = 2; step <= reach; step++) {
            int x = eye.getBlockX() + dx * step;
            int y = eye.getBlockY() + dy * step;
            int z = eye.getBlockZ() + dz * step;
            if (y < 0 || y > 255) return true;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;
            if (world.getBlockAt(x, y, z).getType().isOccluding()) return true;
        }
        return false;
    }

    /** Samples the straight line between every audible pair and counts how much of it is solid. */
    private Map<String, Float> measureOcclusion(List<PlayerSnapshot> audible) {
        Map<String, Float> next = new HashMap<String, Float>();
        if (!config.occlusionEnabled) return next;
        double reach = config.maxDistance * config.maxDistance;

        for (int i = 0; i < audible.size(); i++) {
            PlayerSnapshot a = audible.get(i);
            World world = server.getWorld(a.worldId);
            if (world == null) continue;
            for (int j = i + 1; j < audible.size(); j++) {
                PlayerSnapshot b = audible.get(j);
                if (!a.worldId.equals(b.worldId)) continue;
                double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
                if (dx * dx + dy * dy + dz * dz > reach) continue;

                int samples = config.occlusionSamples;
                int hits = 0;
                for (int step = 1; step < samples; step++) {
                    double t = (double) step / samples;
                    int x = (int) Math.floor(a.x + (b.x - a.x) * t);
                    int y = (int) Math.floor(a.y + (b.y - a.y) * t);
                    int z = (int) Math.floor(a.z + (b.z - a.z) * t);
                    if (y < 0 || y > 255) continue;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    if (world.getBlockAt(x, y, z).getType().isOccluding()) hits++;
                }
                float value = (float) hits / (float) Math.max(1, samples - 1);
                if (value > 0.001f) next.put(pairKey(key(a.name), key(b.name)), Float.valueOf(value));
            }
        }
        return next;
    }

    /** Occlusion is symmetric, so both directions share one entry. Names cannot contain a pipe. */
    private static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    static String key(String playerName) { return playerName.toLowerCase(Locale.ROOT); }
}
