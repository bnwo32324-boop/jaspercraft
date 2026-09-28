package chat.jaspr.ruins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/**
 * Mossy cobblestone portals between the overworld and the Ancient Ruins. A frame shaped like a nether portal (inside
 * 2-21 wide and 3-21 tall, corners optional) built of mossy cobblestone and lit with flint and steel or a fire charge
 * fills with portal blocks. Vanilla would tear them down (they have no obsidian frame) and would send whoever stands in
 * them to the Nether, so their blocks are shielded from physics and vanilla portal travel; instead, standing in one for
 * three seconds (at once in creative) crosses to the same x/z in the other world, arriving in front of the nearest
 * mossy portal there or a new one built on the surface. Breaking or blowing up a frame block closes the portal.
 */
final class Portals implements Listener {
    static final int FRAME = 48, PORTAL = 90, AIR = 0, FIRE = 51;
    private static final int STAND_CHECKS = 15, PERIOD = 4, LINK_RADIUS = 48;

    /** A lit portal: interior from (x, y, z) extending {@code w} along its axis and {@code h} up. */
    static final class Portal {
        final String world;
        final boolean xAxis;
        final int x, y, z, w, h;
        Portal(String world, boolean xAxis, int x, int y, int z, int w, int h) {
            this.world = world; this.xAxis = xAxis; this.x = x; this.y = y; this.z = z; this.w = w; this.h = h;
        }
        int dx() { return xAxis ? 1 : 0; }
        int dz() { return xAxis ? 0 : 1; }
        double cx() { return x + dx() * (w / 2.0) + (xAxis ? 0 : 0.5); }
        double cz() { return z + dz() * (w / 2.0) + (xAxis ? 0.5 : 0); }
        String encode() { return world + ";" + (xAxis ? "x" : "z") + ";" + x + ";" + y + ";" + z + ";" + w + ";" + h; }
        static Portal decode(String s) {
            String[] f = s.split(";");
            return new Portal(f[0], "x".equals(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]));
        }
    }

    /** Block access for frame detection (a world in the plugin, a map in tests). */
    interface Blocks { int id(int x, int y, int z); }

    static long key(int x, int y, int z) { return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL); }

    private final RuinsPlugin plugin;
    private final File file;
    private final List<Portal> portals = new ArrayList<>();
    private final Map<String, Map<Long, Portal>> inner = new HashMap<>(), frames = new HashMap<>();
    private final Map<UUID, Integer> standing = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private long tick;
    long lit, travels, built, closed;

    Portals(RuinsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    int count() { return portals.size(); }

    // ------------------------------------------------------------------ registry

    void load() {
        portals.clear(); inner.clear(); frames.clear();
        if (!file.isFile()) return;
        for (String s : YamlConfiguration.loadConfiguration(file).getStringList("portals")) {
            try { index(Portal.decode(s)); } catch (RuntimeException bad) { plugin.getLogger().warning("RUINS_PORTAL_SKIPPED entry=" + s.replaceAll("[^A-Za-z0-9_;:-]", "?")); }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Portal p : portals) out.add(p.encode());
        y.set("portals", out);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("RUINS_PORTALS_SAVE_FAILED " + e.getClass().getSimpleName());
        }
    }

    private void index(Portal p) {
        portals.add(p);
        Map<Long, Portal> in = inner.computeIfAbsent(p.world, k -> new HashMap<>()), fr = frames.computeIfAbsent(p.world, k -> new HashMap<>());
        for (int a = 0; a < p.w; a++)
            for (int b = 0; b < p.h; b++) in.put(key(p.x + p.dx() * a, p.y + b, p.z + p.dz() * a), p);
        for (int a = 0; a < p.w; a++) {
            fr.put(key(p.x + p.dx() * a, p.y - 1, p.z + p.dz() * a), p);
            fr.put(key(p.x + p.dx() * a, p.y + p.h, p.z + p.dz() * a), p);
        }
        for (int b = 0; b < p.h; b++) {
            fr.put(key(p.x - p.dx(), p.y + b, p.z - p.dz()), p);
            fr.put(key(p.x + p.dx() * p.w, p.y + b, p.z + p.dz() * p.w), p);
        }
    }

    /** Forgets every portal of a world (a regenerated world has none of the old ones). */
    void forgetWorld(String world) {
        for (Portal p : new ArrayList<>(portals)) if (p.world.equals(world)) unindex(p);
        save();
    }

    private void unindex(Portal p) {
        portals.remove(p);
        Map<Long, Portal> in = inner.get(p.world), fr = frames.get(p.world);
        if (in != null) in.values().removeIf(v -> v == p);
        if (fr != null) fr.values().removeIf(v -> v == p);
    }

    private Portal innerAt(Block b) {
        Map<Long, Portal> m = inner.get(b.getWorld().getName());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    private Portal frameAt(Block b) {
        Map<Long, Portal> m = frames.get(b.getWorld().getName());
        return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ()));
    }

    // ------------------------------------------------------------------ frame detection (pure)

    private static boolean hollow(int id) { return id == AIR || id == FIRE; }

    /** The portal whose interior contains (x, y, z), if a complete mossy frame surrounds it along that axis. */
    static Portal detect(Blocks b, String world, int x, int y, int z, boolean xAxis) {
        int dx = xAxis ? 1 : 0, dz = xAxis ? 0 : 1;
        if (!hollow(b.id(x, y, z))) return null;
        int by = y;
        for (int n = 0; hollow(b.id(x, by - 1, z)); n++) { if (n > 21) return null; by--; }
        if (b.id(x, by - 1, z) != FRAME) return null;
        int left = 0;
        while (hollow(b.id(x - dx * (left + 1), by, z - dz * (left + 1)))) if (++left > 21) return null;
        if (b.id(x - dx * (left + 1), by, z - dz * (left + 1)) != FRAME) return null;
        int right = 0;
        while (hollow(b.id(x + dx * (right + 1), by, z + dz * (right + 1)))) if (++right > 21) return null;
        if (b.id(x + dx * (right + 1), by, z + dz * (right + 1)) != FRAME) return null;
        int width = left + right + 1;
        if (width < 2 || width > 21) return null;
        int x0 = x - dx * left, z0 = z - dz * left;
        for (int i = 0; i < width; i++) if (b.id(x0 + dx * i, by - 1, z0 + dz * i) != FRAME) return null;
        int height = 0;
        while (true) {
            if (height > 21) return null;
            int yy = by + height;
            boolean allHollow = true, allFrame = true;
            for (int i = 0; i < width; i++) {
                int id = b.id(x0 + dx * i, yy, z0 + dz * i);
                if (!hollow(id)) allHollow = false;
                if (id != FRAME) allFrame = false;
            }
            if (allFrame) break;
            if (!allHollow) return null;
            if (b.id(x0 - dx, yy, z0 - dz) != FRAME || b.id(x0 + dx * width, yy, z0 + dz * width) != FRAME) return null;
            height++;
        }
        if (height < 3) return null;
        return new Portal(world, xAxis, x0, by, z0, width, height);
    }

    @SuppressWarnings("deprecation")
    private static Blocks of(World w) { return (x, y, z) -> y < 0 || y > 255 ? -1 : w.getBlockAt(x, y, z).getTypeId(); }

    @SuppressWarnings("deprecation")
    private void fill(World w, Portal p) {
        byte axis = (byte) (p.xAxis ? 1 : 2);
        for (int a = 0; a < p.w; a++)
            for (int b = 0; b < p.h; b++) w.getBlockAt(p.x + p.dx() * a, p.y + b, p.z + p.dz() * a).setTypeIdAndData(PORTAL, axis, false);
    }

    // ------------------------------------------------------------------ lighting and closing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (e.getPlayer() == null) return;
        if (e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) return;
        Block b = e.getBlock();
        World w = b.getWorld();
        if (!plugin.portalWorld(w)) return;
        Portal p = detect(of(w), w.getName(), b.getX(), b.getY(), b.getZ(), true);
        if (p == null) p = detect(of(w), w.getName(), b.getX(), b.getY(), b.getZ(), false);
        if (p == null) return;
        e.setCancelled(true);
        fill(w, p);
        index(p);
        save();
        lit++;
        w.playSound(new Location(w, p.cx(), p.y + 1, p.cz()), Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 1.4f);
        e.getPlayer().sendMessage(ChatColor.DARK_GREEN + "The moss-grown gate hums. " + ChatColor.GRAY + "Stand in it to cross to "
            + (plugin.isRuins(w) ? "the overworld." : "the Ancient Ruins."));
        plugin.getLogger().info("RUINS_PORTAL_LIT world=" + w.getName() + " size=" + p.w + "x" + p.h + " by=" + e.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent e) {
        if (portals.isEmpty()) return;
        Block b = e.getBlock();
        Map<Long, Portal> m = inner.get(b.getWorld().getName());
        if (m != null && m.containsKey(key(b.getX(), b.getY(), b.getZ()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        Portal p = frameAt(e.getBlock());
        if (p == null) p = innerAt(e.getBlock());
        if (p != null) close(p, "broken");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(EntityExplodeEvent e) { for (Block b : new ArrayList<>(e.blockList())) closeAt(b, "explosion"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(BlockExplodeEvent e) { for (Block b : new ArrayList<>(e.blockList())) closeAt(b, "explosion"); }

    private void closeAt(Block b) { closeAt(b, "changed"); }

    private void closeAt(Block b, String why) {
        Portal p = frameAt(b);
        if (p == null) p = innerAt(b);
        if (p != null) close(p, why);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent e) { for (Block b : e.getBlocks()) if (frameAt(b) != null || innerAt(b) != null) { e.setCancelled(true); return; } }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent e) { for (Block b : e.getBlocks()) if (frameAt(b) != null || innerAt(b) != null) { e.setCancelled(true); return; } }

    /** Closes a portal: forget it, and clear what is left of its portal blocks next tick (after any explosion). */
    private void close(Portal p, String why) {
        if (!portals.contains(p)) return;
        unindex(p);
        save();
        closed++;
        World w = Bukkit.getWorld(p.world);
        if (w != null) Bukkit.getScheduler().runTask(plugin, () -> clearPortalBlocks(w, p));
        plugin.getLogger().info("RUINS_PORTAL_CLOSED world=" + p.world + " reason=" + why);
    }

    @SuppressWarnings("deprecation")
    private static void clearPortalBlocks(World w, Portal p) {
        for (int a = 0; a < p.w; a++)
            for (int b = 0; b < p.h; b++) {
                Block block = w.getBlockAt(p.x + p.dx() * a, p.y + b, p.z + p.dz() * a);
                if (block.getTypeId() == PORTAL) block.setTypeIdAndData(AIR, (byte) 0, false);
            }
    }

    // ------------------------------------------------------------------ no vanilla behaviour in mossy portals

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void vanillaTravel(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || e.getFrom() == null) return;
        Block b = e.getFrom().getBlock();
        if (innerAt(b) != null || innerAt(b.getRelative(0, 1, 0)) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void vanillaTravel(EntityPortalEvent e) {
        if (e.getFrom() == null) return;
        Block b = e.getFrom().getBlock();
        if (innerAt(b) != null || innerAt(b.getRelative(0, 1, 0)) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pigmen(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NETHER_PORTAL) return;
        Block b = e.getLocation().getBlock();
        if (innerAt(b) != null || innerAt(b.getRelative(0, -1, 0)) != null || innerAt(b.getRelative(0, 1, 0)) != null) e.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { standing.remove(e.getPlayer().getUniqueId()); cooldown.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ travel

    @SuppressWarnings("deprecation")
    void tick() {
        tick += PERIOD;
        if (portals.isEmpty()) { standing.clear(); return; }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Location l = player.getLocation();
            Block feet = l.getBlock();
            Portal p = innerAt(feet);
            if (p == null) { feet = feet.getRelative(0, 1, 0); p = innerAt(feet); }
            if (p == null || feet.getTypeId() != PORTAL) { standing.remove(id); continue; }
            Long until = cooldown.get(id);
            if (until != null && tick < until) continue;
            int n = standing.merge(id, 1, Integer::sum);
            if (n >= (player.getGameMode() == GameMode.CREATIVE ? 1 : STAND_CHECKS)) {
                standing.remove(id);
                cooldown.put(id, tick + 100);
                travel(player, p);
            }
        }
    }

    private void travel(Player player, Portal from) {
        World src = player.getWorld(), dst = plugin.otherWorld(src);
        if (dst == null) return;
        long t0 = System.nanoTime();
        Location at = player.getLocation();
        Portal target = nearest(dst.getName(), at.getX(), at.getZ());
        boolean made = false;
        if (target == null) {
            target = build(dst, at.getBlockX(), at.getBlockZ(), from.xAxis);
            if (target == null) {
                player.sendMessage(ChatColor.GRAY + "The gate flickers: there is no room on the other side here.");
                plugin.getLogger().info("RUINS_PORTAL_NO_ROOM from=" + src.getName() + " player=" + player.getName());
                return;
            }
            index(target);
            save();
            built++;
            made = true;
        }
        Location arrive = arrival(dst, target, at);
        if (player.isInsideVehicle()) player.leaveVehicle();
        player.setFallDistance(0f);
        player.setVelocity(new Vector());
        boolean ok = player.teleport(arrive, PlayerTeleportEvent.TeleportCause.PLUGIN);
        travels++;
        plugin.getLogger().info("RUINS_TRAVEL player=" + player.getName() + " from=" + src.getName() + " to=" + dst.getName()
            + " builtPortal=" + made + " ok=" + ok + " ms=" + (System.nanoTime() - t0) / 1_000_000L);
        if (ok && plugin.isRuins(dst)) player.sendTitle(ChatColor.DARK_GREEN + "The Ancient Ruins", ChatColor.GRAY + "Moss-grown cities of a forgotten age", 10, 60, 20);
    }

    private Portal nearest(String world, double x, double z) {
        Portal best = null;
        double bestD = LINK_RADIUS * (double) LINK_RADIUS;
        for (Portal p : portals) {
            if (!p.world.equals(world)) continue;
            double dx = p.cx() - x, dz = p.cz() - z, d = dx * dx + dz * dz;
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** In front of (or behind) the portal, facing away from it; inside it only if both sides are blocked. */
    @SuppressWarnings("deprecation")
    private static Location arrival(World w, Portal p, Location facing) {
        int mid = p.w / 2, bx = p.x + p.dx() * mid, bz = p.z + p.dz() * mid, px = p.dz(), pz = p.dx();
        for (int side = 1; side >= -1; side -= 2) {
            int x = bx + px * side, z = bz + pz * side;
            if (passable(w.getBlockAt(x, p.y, z).getTypeId()) && passable(w.getBlockAt(x, p.y + 1, z).getTypeId()) && solid(w.getBlockAt(x, p.y - 1, z).getTypeId())) {
                float yaw = (float) Math.toDegrees(Math.atan2(-px * side, pz * side));
                return new Location(w, x + 0.5, p.y, z + 0.5, yaw, 0f);
            }
        }
        return new Location(w, bx + 0.5, p.y, bz + 0.5, facing.getYaw(), 0f);
    }

    private static boolean passable(int id) { return id == AIR || id == 31 || id == 37 || id == 38 || id == 175 || id == 78 || id == 106 || id == 32 || id == 39 || id == 40; }
    private static boolean solid(int id) { return id != AIR && id != 8 && id != 9 && id != 10 && id != 11 && id != 18 && id != 161 && id != 51 && !passable(id); }

    /** A 4x5 mossy frame with a lit 2x3 portal on open, level ground near (x, z); raised on a platform if it must be. */
    @SuppressWarnings("deprecation")
    private Portal build(World w, int x, int z, boolean xAxis) {
        RuinsPlugin.settle(w, x, z, 1);
        int dx = xAxis ? 1 : 0, dz = xAxis ? 0 : 1, px = dz, pz = dx;
        for (int r = 0; r <= 16; r += 2)
            for (int ox = -r; ox <= r; ox += 2)
                for (int oz = -r; oz <= r; oz += 2) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
                    int bx = x + ox, bz = z + oz, gy = w.getHighestBlockYAt(bx, bz) - 1;
                    if (gy < 2 || gy > 240) continue;
                    if (fits(w, bx, gy, bz, dx, dz, px, pz)) return place(w, bx, gy, bz, xAxis, false);
                }
        // Nowhere level and open: stand it on a small mossy platform above whatever is there.
        int gy = Math.max(w.getHighestBlockYAt(x, z) - 1, Plans.SEA);
        for (int lift = 0; lift <= 24 && gy + lift < 240; lift++) {
            if (open(w, x, gy + lift, z, dx, dz, px, pz)) return place(w, x, gy + lift, z, xAxis, true);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static boolean fits(World w, int bx, int gy, int bz, int dx, int dz, int px, int pz) {
        for (int a = -1; a <= 2; a++) {
            int x = bx + dx * a, z = bz + dz * a;
            if (!solid(w.getBlockAt(x, gy, z).getTypeId())) return false;
            for (int y = gy + 1; y <= gy + 4; y++) if (!passable(w.getBlockAt(x, y, z).getTypeId())) return false;
        }
        for (int a = 0; a <= 1; a++)
            for (int side = -1; side <= 1; side += 2) {
                int x = bx + dx * a + px * side, z = bz + dz * a + pz * side;
                if (!solid(w.getBlockAt(x, gy, z).getTypeId())) return false;
                for (int y = gy + 1; y <= gy + 2; y++) if (!passable(w.getBlockAt(x, y, z).getTypeId())) return false;
            }
        return true;
    }

    @SuppressWarnings("deprecation")
    private static boolean open(World w, int bx, int gy, int bz, int dx, int dz, int px, int pz) {
        for (int a = -1; a <= 2; a++)
            for (int y = gy + 1; y <= gy + 4; y++) { int id = w.getBlockAt(bx + dx * a, y, bz + dz * a).getTypeId(); if (!passable(id) && id != 8 && id != 9) return false; }
        for (int a = 0; a <= 1; a++)
            for (int side = -1; side <= 1; side += 2)
                for (int y = gy + 1; y <= gy + 2; y++) {
                    int id = w.getBlockAt(bx + dx * a + px * side, y, bz + dz * a + pz * side).getTypeId();
                    if (!passable(id) && id != 8 && id != 9) return false;
                }
        return true;
    }

    @SuppressWarnings("deprecation")
    private Portal place(World w, int bx, int gy, int bz, boolean xAxis, boolean platform) {
        int dx = xAxis ? 1 : 0, dz = xAxis ? 0 : 1, px = dz, pz = dx;
        for (int a = -1; a <= 2; a++) {
            int x = bx + dx * a, z = bz + dz * a;
            w.getBlockAt(x, gy, z).setTypeIdAndData(FRAME, (byte) 0, false);
            w.getBlockAt(x, gy + 4, z).setTypeIdAndData(FRAME, (byte) 0, false);
            for (int y = gy + 1; y <= gy + 3; y++) {
                boolean side = a == -1 || a == 2;
                w.getBlockAt(x, y, z).setTypeIdAndData(side ? FRAME : AIR, (byte) 0, false);
            }
        }
        for (int a = 0; a <= 1; a++)
            for (int side = -1; side <= 1; side += 2) {
                int x = bx + dx * a + px * side, z = bz + dz * a + pz * side;
                if (platform || !solid(w.getBlockAt(x, gy, z).getTypeId())) w.getBlockAt(x, gy, z).setTypeIdAndData(FRAME, (byte) 0, false);
                for (int y = gy + 1; y <= gy + 2; y++) w.getBlockAt(x, y, z).setTypeIdAndData(AIR, (byte) 0, false);
            }
        Portal p = new Portal(w.getName(), xAxis, bx, gy + 1, bz, 2, 3);
        fill(w, p);
        plugin.getLogger().info("RUINS_PORTAL_BUILT world=" + w.getName() + " platform=" + platform);
        return p;
    }
}
