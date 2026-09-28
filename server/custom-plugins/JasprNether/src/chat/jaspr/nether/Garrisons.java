package chat.jaspr.nether;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * The mega structures' guards. Each structure registers garrison points ("garrison" entries named like
 * "!spinout+wither_skeleton": the kinds of the pack, '!' when an elite leads it). A point rouses when a player comes
 * within 18 blocks, spawns its pack around itself (bounded: at most 10 garrison mobs near a point, the plugin's
 * tracked-mob cap still applies), then rests for eight minutes. Packs are ordinary Nether mobs that despawn like any
 * other; wither skeletons of a garrison drop no skulls (owner rule: generation never hands out wither skulls).
 */
final class Garrisons implements Listener {
    static final int NEAR = 18, CAP = 10;
    static final long REST = 20L * 60 * 8;

    private final NetherPlugin plugin;
    private final Map<String, Long> roused = new HashMap<>();
    private final Random random = new Random();
    long rousedTotal, spawnedTotal;

    Garrisons(NetherPlugin plugin) { this.plugin = plugin; }

    void tick(long now) {
        World w = plugin.nether;
        if (w == null || plugin.registry == null || (now % 20) != 0) return;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            for (Registry.Entry e : plugin.registry.near(x, z, NEAR, "garrison")) {
                if (Math.abs(e.y1 - y) > 12) continue;
                double dx = e.x1 - x, dz = e.z1 - z;
                if (dx * dx + dz * dz > NEAR * NEAR) continue;
                String key = e.x1 + "," + e.y1 + "," + e.z1;
                Long last = roused.get(key);
                if (last != null && now - last < REST) continue;
                roused.put(key, now);
                if (roused.size() > 4096) roused.clear();
                rouse(w, e);
            }
        }
    }

    private void rouse(World w, Registry.Entry e) {
        String spec = e.name;
        boolean elite = spec.startsWith("!");
        if (elite) spec = spec.substring(1);
        String[] kinds = spec.split("\\+");
        Location at = new Location(w, e.x1 + 0.5, e.y1, e.z1 + 0.5);
        int alive = 0;
        for (Entity n : w.getNearbyEntities(at, 40, 20, 40)) if (n instanceof LivingEntity && !n.isDead() && n.getScoreboardTags().contains("jn_garrison")) alive++;
        int want = Math.min(kinds.length + random.nextInt(2), CAP - alive), made = 0;
        for (int i = 0; i < want; i++) {
            String kind = kinds[i % kinds.length];
            Location spot = spot(w, e.x1, e.y1, e.z1, i, flies(kind));
            if (spot == null) continue;
            LivingEntity m = spawn(kind, spot, elite && i == 0);
            if (m == null) continue;
            m.addScoreboardTag("jn_garrison");
            made++;
        }
        rousedTotal++;
        spawnedTotal += made;
        plugin.getLogger().info("NETHER_GARRISON_ROUSED at=" + e.x1 + "," + e.y1 + "," + e.z1 + " pack=" + e.name + " spawned=" + made + " nearby=" + alive);
    }

    private static boolean flies(String kind) { return kind.equals("ghastling") || kind.equals("frost") || kind.equals("blaze"); }

    /** A free spot near the point: two blocks of air on solid ground (or just air for fliers). */
    private static Location spot(World w, int x, int y, int z, int i, boolean flier) {
        int[][] ring = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 1}, {-2, -1}, {1, -2}, {-1, 2}, {2, -2}, {-2, 2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int k = 0; k < ring.length; k++) {
            int[] o = ring[(k + i * 3) % ring.length];
            for (int dy = 0; dy <= 2; dy++) {
                Block b = w.getBlockAt(x + o[0], y + dy, z + o[1]);
                if (b.getType() != Material.AIR || b.getRelative(0, 1, 0).getType() != Material.AIR) continue;
                if (flier || b.getRelative(0, -1, 0).getType().isSolid()) return b.getLocation().add(0.5, flier ? 1 : 0, 0.5);
            }
        }
        return null;
    }

    private LivingEntity spawn(String kind, Location at, boolean elite) {
        if (Mobs.KINDS.containsKey(kind)) return plugin.mobs.spawn(kind, at, elite);
        EntityType t;
        switch (kind) {
            case "wither_skeleton": t = EntityType.WITHER_SKELETON; break;
            case "magma_cube": t = EntityType.MAGMA_CUBE; break;
            case "blaze": t = EntityType.BLAZE; break;
            case "zombie_pigman": t = EntityType.PIG_ZOMBIE; break;
            default: t = EntityType.SKELETON;
        }
        Entity raw = at.getWorld().spawnEntity(at, t);
        if (!(raw instanceof LivingEntity)) return null;
        plugin.mobs.scale((LivingEntity) raw);
        return (LivingEntity) raw;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        if (!e.getEntity().getScoreboardTags().contains("jn_garrison")) return;
        if (e.getEntity().getType() == EntityType.WITHER_SKELETON)
            e.getDrops().removeIf(s -> s.getType() == Material.SKULL_ITEM && s.getDurability() == 1);
    }
}
