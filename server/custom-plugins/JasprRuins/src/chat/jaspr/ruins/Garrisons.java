package chat.jaspr.ruins;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * The garrisons of the great structures (owner, 2026-10-04: "they should include all types of mobs and custom mobs"):
 * each structure's design lays out garrison points whose packs together hold every horror of Drownhollow and every
 * monster of the overworld. A point rouses when a player comes within {@value #NEAR} blocks (and 10 up or down) of it,
 * never within a gate's sanctuary: its pack rises on free ground beside it, as many as the danger ramp allows (a quarter
 * by the gates, all of it far out), at most {@value #CAP} of a garrison near a point; then it rests for eight minutes.
 * Horrors are made as the horrors are (half health, see {@link RuinsPlugin#EASE}); monsters as they are, except that a
 * garrison's creeper blasts and endermen and silverfish leave the stone where it is.
 */
final class Garrisons implements Listener {
    static final String TAG = "jaspr_garrison";
    static final int NEAR = 16, CAP = 8;
    static final long REST_MS = 8L * 60_000L;

    private final RuinsPlugin plugin;
    private final Map<String, Long> roused = new HashMap<>();
    private final Random random = new Random();
    long rousedTotal, spawnedTotal;

    Garrisons(RuinsPlugin plugin) { this.plugin = plugin; }

    /** Every second: wake the points players come near. */
    void tick() {
        World w = plugin.ruins();
        if (w == null || w.getPlayers().isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            for (Plans.GreatSite s : plugin.plans().greatsNear(l.getBlockX(), l.getBlockZ(), 1)) {
                if (!s.covers(l.getBlockX(), l.getBlockZ(), NEAR + 4)) continue;
                for (Greats.Point g : Greats.garrisons(s)) {
                    if (Math.abs(g.y - l.getY()) > 10) continue;
                    double dx = g.x + 0.5 - l.getX(), dz = g.z + 0.5 - l.getZ();
                    if (dx * dx + dz * dz > NEAR * NEAR) continue;
                    String key = g.x + "," + g.y + "," + g.z;
                    Long last = roused.get(key);
                    if (last != null && now - last < REST_MS) continue;
                    roused.put(key, now);
                    if (roused.size() > 4096) roused.clear();
                    rouse(w, g);
                }
            }
        }
    }

    private void rouse(World w, Greats.Point g) {
        Location at = new Location(w, g.x + 0.5, g.y, g.z + 0.5);
        if (!w.isChunkLoaded(g.x >> 4, g.z >> 4) || plugin.danger().sanctuary(at)) return;
        String spec = g.pack;
        boolean elder = spec.startsWith("!");
        if (elder) spec = spec.substring(1);
        String[] kinds = spec.split("\\+");
        int alive = 0;
        for (Entity n : w.getNearbyEntities(at, 24, 12, 24)) if (n instanceof LivingEntity && !n.isDead() && n.getScoreboardTags().contains(TAG)) alive++;
        int want = Math.max(1, (int) Math.round(kinds.length * Danger.strength(plugin.danger().level(at))));
        want = Math.min(want, CAP - alive);
        int made = 0;
        for (int i = 0; i < want; i++) {
            String kind = kinds[i % kinds.length];
            Location spot = spot(w, g.x, g.y, g.z, i, kind);
            if (spot == null) continue;
            LivingEntity m = spawn(kind, spot);
            if (m == null) continue;
            m.addScoreboardTag(TAG);
            m.addScoreboardTag(Horrors.DAYLIGHT_EXEMPT);
            if (elder && i == 0) {
                Horrors.Kind k = horror(kind);
                if (k != null) plugin.horrors().elder(m, k);
            }
            made++;
        }
        rousedTotal++;
        spawnedTotal += made;
        plugin.getLogger().info("RUINS_GARRISON_ROUSED at=" + g.x + "," + g.y + "," + g.z + " pack=" + g.pack + " spawned=" + made + " nearby=" + alive);
    }

    static Horrors.Kind horror(String kind) {
        try { return Horrors.Kind.valueOf(kind.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    private LivingEntity spawn(String kind, Location at) {
        Horrors.Kind k = horror(kind);
        if (k != null) return plugin.horrors().spawn(k, at);
        EntityType t;
        try { t = EntityType.valueOf(kind.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        if (t.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(t.getEntityClass())) return null;
        Entity e = at.getWorld().spawnEntity(at, t);   // a plugin spawn: Horrors leaves it a monster
        if (e instanceof Slime) ((Slime) e).setSize(2);
        if (e instanceof LivingEntity) ((LivingEntity) e).setRemoveWhenFarAway(true);
        return e instanceof LivingEntity ? (LivingEntity) e : null;
    }

    /** A free spot beside the point: two blocks of air on solid ground (water for the water dwellers, any air for fliers). */
    private static Location spot(World w, int x, int y, int z, int i, String kind) {
        boolean water = GreatDesign.WATER.contains(kind), flier = kind.equals("nightgaunt");
        int[][] ring = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 1}, {-2, -1}, {1, -2}, {-1, 2}, {2, -2}, {-2, 2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int k = 0; k < ring.length; k++) {
            int[] o = ring[(k + i * 3) % ring.length];
            for (int dy = 0; dy <= 2; dy++) {
                Block b = w.getBlockAt(x + o[0], y + dy, z + o[1]);
                if (water) { if (b.isLiquid() && b.getRelative(0, 1, 0).isLiquid()) return b.getLocation().add(0.5, 0, 0.5); continue; }
                if (b.getType() != Material.AIR || b.getRelative(0, 1, 0).getType() != Material.AIR) continue;
                if (flier || b.getRelative(0, -1, 0).getType().isSolid()) return b.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    /** A garrison's creeper never blasts the stone; its endermen and silverfish never move it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void blast(EntityExplodeEvent e) {
        if (e.getEntity() != null && e.getEntity().getScoreboardTags().contains(TAG)) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void stone(EntityChangeBlockEvent e) {
        if (e.getEntity().getScoreboardTags().contains(TAG) && e.getEntityType() != EntityType.FALLING_BLOCK) e.setCancelled(true);
    }

    String describe() { return "garrisonsRoused=" + rousedTotal + " garrisonSpawned=" + spawnedTotal; }

    static List<String> roster() { return java.util.Arrays.asList(GreatDesign.ROSTER); }
}
