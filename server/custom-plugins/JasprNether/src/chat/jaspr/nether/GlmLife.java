package chat.jaspr.nether;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * What keeps the GLM strongholds dangerous (owner, 2026-09-29: "make them dangerous and fun to adventure through"):
 * <ul>
 *   <li>their spawners: a spawner block of the build's creature (the spinning figure shows which) that wakes while a
 *       player is within 16 blocks and brings one or two of its kind every 10-25 s, at most four near it; breaking
 *       the spawner ends it. The plugin does the spawning so that any of the Nether creatures can come from a
 *       spawner, whatever the light and the floor (vanilla spawners would refuse most of them);</li>
 *   <li>trapped chests: opening one inside a stronghold springs an ambush of the build's guards, once;</li>
 *   <li>fire never destroys a stronghold: burning and fire spreading from lava or flames are stopped inside the
 *       builds' boxes (fireballs still set fires, they just do not eat the walls).</li>
 * </ul>
 */
final class GlmLife implements Listener {
    static final int NEAR = 16, CAP = 4;

    private final NetherPlugin plugin;
    private final Map<String, Long> next = new HashMap<>();
    private final Random random = new Random();
    long spawned, woken, ambushes, burnsStopped, spreadStopped;

    GlmLife(NetherPlugin plugin) { this.plugin = plugin; }

    String describe() {
        return "spawned=" + spawned + " woken=" + woken + " ambushes=" + ambushes + " burnsStopped=" + burnsStopped + " spreadStopped=" + spreadStopped;
    }

    void tick(long now) {
        World w = plugin.nether;
        if (w == null || plugin.registry == null || (now % 20) != 7) return;
        for (Player p : w.getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            for (Registry.Entry e : plugin.registry.near(x, z, NEAR, "glmspawner")) {
                if (Math.abs(e.y1 - y) > 10) continue;
                double dx = e.x1 - x, dz = e.z1 - z;
                if (dx * dx + dz * dz > NEAR * NEAR) continue;
                String key = e.x1 + "," + e.y1 + "," + e.z1;
                Long t = next.get(key);
                if (t != null && now < t) continue;
                next.put(key, now + 200 + random.nextInt(300));
                if (next.size() > 8192) next.clear();
                if (!w.isChunkLoaded(e.x1 >> 4, e.z1 >> 4)) continue;
                Block b = w.getBlockAt(e.x1, e.y1, e.z1);
                if (b.getType() != Material.MOB_SPAWNER) continue;        // broken: the spawner is done
                wake(w, e, p);
            }
        }
    }

    private void wake(World w, Registry.Entry e, Player p) {
        Location c = new Location(w, e.x1 + 0.5, e.y1 + 1, e.z1 + 0.5);
        int near = 0;
        for (Entity n : w.getNearbyEntities(c, 10, 6, 10)) if (n instanceof LivingEntity && !n.isDead() && n.getScoreboardTags().contains("jn_glm")) near++;
        if (near >= CAP) return;
        woken++;
        int n = 1 + (random.nextInt(3) == 0 ? 1 : 0);
        boolean flier = Fiends.flies(e.name) || e.name.equals("blaze") || e.name.equals("ghastling");
        for (int i = 0; i < n && near < CAP; i++) {
            Location at = spot(w, e.x1, e.y1, e.z1, flier);
            if (at == null) continue;
            LivingEntity m = spawn(e.name, at);
            if (m == null) continue;
            m.addScoreboardTag("jn_glm");
            if (m instanceof Creature) ((Creature) m).setTarget(p);
            w.spawnParticle(Particle.FLAME, at.clone().add(0, 0.8, 0), 16, 0.3, 0.6, 0.3, 0.02);
            spawned++;
            near++;
        }
        w.playSound(c, Sound.BLOCK_FIRE_AMBIENT, 1f, 0.6f);
    }

    LivingEntity spawn(String kind, Location at) {
        if (Mobs.KINDS.containsKey(kind)) return plugin.mobs.spawn(kind, at, random.nextInt(12) == 0);
        org.bukkit.entity.EntityType t;
        switch (kind) {
            case "wither_skeleton": t = org.bukkit.entity.EntityType.WITHER_SKELETON; break;
            case "magma_cube": t = org.bukkit.entity.EntityType.MAGMA_CUBE; break;
            case "blaze": t = org.bukkit.entity.EntityType.BLAZE; break;
            case "zombie_pigman": t = org.bukkit.entity.EntityType.PIG_ZOMBIE; break;
            default: t = org.bukkit.entity.EntityType.SKELETON;
        }
        Entity raw = at.getWorld().spawnEntity(at, t);
        if (!(raw instanceof LivingEntity)) return null;
        plugin.mobs.scale((LivingEntity) raw);
        if (raw instanceof org.bukkit.entity.PigZombie) { ((org.bukkit.entity.PigZombie) raw).setAngry(true); ((org.bukkit.entity.PigZombie) raw).setAnger(1200); }
        return (LivingEntity) raw;
    }

    /** Two blocks of air on something solid beside the spawner (or any air for fliers). */
    private Location spot(World w, int x, int y, int z, boolean flier) {
        for (int k = 0; k < 16; k++) {
            int bx = x + random.nextInt(7) - 3, bz = z + random.nextInt(7) - 3;
            for (int dy = 2; dy >= -2; dy--) {
                Block b = w.getBlockAt(bx, y + dy, bz);
                if (b.getType() != Material.AIR || b.getRelative(0, 1, 0).getType() != Material.AIR) continue;
                if (flier || b.getRelative(0, -1, 0).getType().isSolid()) return b.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    // ---- trapped chests ----------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player) || plugin.registry == null || plugin.gen == null) return;
        if (!(e.getInventory().getHolder() instanceof org.bukkit.block.Chest)) return;
        Block b = ((org.bukkit.block.Chest) e.getInventory().getHolder()).getBlock();
        if (b.getType() != Material.TRAPPED_CHEST || !plugin.isNether(b.getWorld())) return;
        int x = b.getX(), y = b.getY(), z = b.getZ();
        if (plugin.registry.at(x, y, z, "glm") == null || plugin.registry.at(x, y, z, "sprung") != null) return;
        GlmSites.Site s = plugin.gen.builtGlmAt(x, z);
        if (s == null) return;
        plugin.registry.add("sprung", s.e.key, x, y, z, x, y, z);
        Player p = (Player) e.getPlayer();
        String[] roster = GlmSites.roster(s.e.theme);
        int made = 0;
        for (int i = 0; i < 3; i++) {
            Location at = spot(b.getWorld(), x, y, z, false);
            if (at == null) continue;
            LivingEntity m = spawn(roster[random.nextInt(roster.length)], at);
            if (m == null) continue;
            m.addScoreboardTag("jn_glm");
            if (m instanceof Creature) ((Creature) m).setTarget(p);
            made++;
        }
        ambushes++;
        p.sendTitle(ChatColor.DARK_RED + "Ambush!", ChatColor.GOLD + "the chest was a trap", 5, 40, 10);
        b.getWorld().playSound(b.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.5f, 0.6f);
        plugin.getLogger().info("NETHER_GLM_AMBUSH build=" + s.e.key + " at=" + x + "," + y + "," + z + " spawned=" + made);
    }

    // ---- fire never eats a stronghold ---------------------------------------------------------------------------------
    private boolean inBuild(Block b) {
        return plugin.registry != null && plugin.isNether(b.getWorld()) && plugin.registry.at(b.getX(), b.getY(), b.getZ(), "glm") != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inBuild(e.getBlock())) { e.setCancelled(true); burnsStopped++; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        BlockIgniteEvent.IgniteCause c = e.getCause();
        if ((c == BlockIgniteEvent.IgniteCause.SPREAD || c == BlockIgniteEvent.IgniteCause.LAVA) && inBuild(e.getBlock())) { e.setCancelled(true); spreadStopped++; }
    }
}
