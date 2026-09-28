package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Where the story's fixed things are, found from the plan itself (the same drawing that places them): key figures' posts,
 * bosses' arenas, the Ash-Crowned's mechanisms, heliodromes, the House of Return's berths, the Wards, captives and the
 * monument. Computed once, off the main thread, by drawing every fixed place's chunks without a block target. Also
 * keeps the key figures alive: one whose post is loaded but who is missing is placed again.
 */
final class Registry {
    static final class Spot {
        final String kind;     // e.g. "key:kleio", "boss:kallias", "mech:font:0", "heliodrome:lampsa", "berth:3", "ward:marches"
        final int x, y, z, yaw;
        final String extra;    // heliodrome display name
        Spot(String kind, int x, int y, int z, int yaw, String extra) { this.kind = kind; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.extra = extra; }
        Location at(World w) { return new Location(w, x + 0.5, y, z + 0.5, yaw, 0); }
    }

    private final AtlasPlugin plugin;
    private volatile Map<String, Spot> spots = Collections.emptyMap();
    private volatile boolean ready;
    private final Map<String, UUID> keyEntities = new HashMap<>();

    Registry(AtlasPlugin plugin) { this.plugin = plugin; }

    boolean ready() { return ready; }

    /** Scans the fixed places' chunks (pure computation; safe off the main thread). */
    void scan(AtlasGenerator gen) {
        long t0 = System.nanoTime();
        Map<String, Spot> found = new LinkedHashMap<>();
        int chunks = 0;
        for (Realm.Place p : Realm.Place.values()) {
            int r = p == Realm.Place.ANTHRAKION ? Realm.WARD_R + 10 : p.radius + 16;
            for (int cx = Math.floorDiv(p.x - r, 16); cx <= Math.floorDiv(p.x + r, 16); cx++)
                for (int cz = Math.floorDiv(p.z - r, 16); cz <= Math.floorDiv(p.z + r, 16); cz++) {
                    chunks++;
                    // Occupied and liberated drawings both: some things (a freed fort's heliodrome) exist only in one.
                    for (int mask : new int[] {0, Realm.ALL_LIBERATED})
                        for (Canvas.Tile t : AtlasPopulator.tilesOf(gen, cx, cz, mask)) {
                            if (t.kind != Canvas.NPC_TILE) continue;
                            String k = t.what;
                            if (k.startsWith("key:") || k.startsWith("boss:") || k.startsWith("mech:") || k.startsWith("heliodrome:") || k.startsWith("berth:")
                                || k.startsWith("ward:") || k.equals("monument") || k.startsWith("captive:") || k.startsWith("choir:") || k.startsWith("talker:"))
                                found.putIfAbsent(k, new Spot(k, t.x, t.y, t.z, t.meta, t.extras));
                        }
                }
        }
        spots = found;
        ready = true;
        plugin.getLogger().info("ATLAS_REGISTRY_READY spots=" + found.size() + " chunks=" + chunks + " ms=" + (System.nanoTime() - t0) / 1_000_000L);
    }

    Spot spot(String kind) { return spots.get(kind); }

    List<Spot> all(String prefix) {
        List<Spot> out = new ArrayList<>();
        for (Spot s : spots.values()) if (s.kind.startsWith(prefix)) out.add(s);
        return out;
    }

    // ------------------------------------------------------------------ key figures

    boolean keyAlive(String id) {
        UUID u = keyEntities.get(id);
        if (u == null) return false;
        Entity e = org.bukkit.Bukkit.getEntity(u);
        return e != null && !e.isDead();
    }

    void remember(String id, Entity e) { keyEntities.put(id, e.getUniqueId()); }

    boolean talkerAlive(String id) {
        UUID u = keyEntities.get("talker:" + id);
        if (u == null) return false;
        Entity e = org.bukkit.Bukkit.getEntity(u);
        return e != null && !e.isDead();
    }

    /** Every 30 seconds: find key figures and talkers by tag, and put back any whose post is loaded but who is gone. */
    void maintain(World w) {
        if (w == null || !ready) return;
        Map<String, Entity> present = new HashMap<>();
        for (Entity e : w.getEntities()) {
            String id = Npcs.tagValue(e, Npcs.KEY);
            String talker = Npcs.tagValue(e, "atlas_talker:");
            String k = id != null ? id : talker != null ? "talker:" + talker : null;
            if (k == null) continue;
            if (present.containsKey(k)) { e.remove(); continue; }   // a duplicate (should not happen): keep one
            present.put(k, e);
            keyEntities.put(k, e.getUniqueId());
        }
        for (Spot s : spots.values()) {
            boolean key = s.kind.startsWith("key:"), talker = s.kind.startsWith("talker:");
            if (!key && !talker) continue;
            String k = key ? s.kind.substring(4) : s.kind;
            if (present.containsKey(k) || !w.isChunkLoaded(s.x >> 4, s.z >> 4)) continue;
            boolean playerNear = false;
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(s.at(w)) < 96 * 96) playerNear = true;
            if (!playerNear) continue;
            if (key) plugin.npcs().keyFigure(s.at(w), k);
            else plugin.npcs().talker(s.at(w), s.kind.substring(7));
            plugin.getLogger().info("ATLAS_KEY_FIGURE_RESTORED id=" + k);
        }
    }
}
