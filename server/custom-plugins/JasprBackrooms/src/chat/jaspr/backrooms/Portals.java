package chat.jaspr.backrooms;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

/**
 * The way in (owner, 2026-09-30: "Make the portal frame a vanilla block, but the way to light it has to be from beating
 * one of the dimensions, this includes the Ender Dragon"). A nether-portal frame of yellow glazed terracotta in the
 * overworld (inside 2-21 wide and 3-21 tall, corners optional) lights with flint and steel or a fire charge only in the
 * hands of a conqueror ({@link Conquest}); for anyone else the flame dies. Standing in a lit gate for three seconds (at
 * once in creative) crosses to the Threshold, again only for a conqueror. The Threshold's gate leads back to the gate
 * the player came through (or the overworld spawn when that gate is gone). Vanilla would tear the portal blocks down
 * (no obsidian) and send people to the Nether: both are prevented. Breaking or blowing up a frame block closes a gate.
 */
final class Portals implements Listener {
    static final Material FRAME = Material.YELLOW_GLAZED_TERRACOTTA;
    static final int MAX = 21, STAND_CHECKS = 15;

    static final class Gate {
        final String world;
        final boolean xAxis;   // the plane runs along x (the portal is thin in z)
        final int x, y, z, w, h;
        Gate(String world, boolean xAxis, int x, int y, int z, int w, int h) {
            this.world = world; this.xAxis = xAxis; this.x = x; this.y = y; this.z = z; this.w = w; this.h = h;
        }
        String id() { return world + ";" + x + ";" + y + ";" + z; }
        String encode() { return world + ";" + (xAxis ? "x" : "z") + ";" + x + ";" + y + ";" + z + ";" + w + ";" + h; }
        static Gate decode(String s) {
            String[] f = s.split(";");
            return new Gate(f[0], "x".equals(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]));
        }
        boolean inside(int bx, int by, int bz) {
            if (by < y || by >= y + h) return false;
            return xAxis ? bz == z && bx >= x && bx < x + w : bx == x && bz >= z && bz < z + w;
        }
        boolean frames(int bx, int by, int bz) {
            if (by < y - 1 || by > y + h) return false;
            if (xAxis) return bz == z && bx >= x - 1 && bx <= x + w && !inside(bx, by, bz);
            return bx == x && bz >= z - 1 && bz <= z + w && !inside(bx, by, bz);
        }
    }

    /** Block access for frame detection (a world in the plugin, a map in tests). */
    interface Blocks { Material type(int x, int y, int z); }

    private final BackroomsPlugin plugin;
    private final File file, travellers;
    private final List<Gate> gates = new ArrayList<>();
    private final Map<Long, Gate> interior = new HashMap<>();
    private final Map<UUID, String> cameFrom = new HashMap<>();
    private final Map<UUID, Integer> standing = new HashMap<>();
    private final Map<UUID, Long> refused = new HashMap<>();
    private final Set<UUID> mustLeave = new HashSet<>();
    long lit, refusedLights, refusedEntries, travelsIn, travelsOut, closed, vanillaBlocked;

    Portals(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gates.yml");
        this.travellers = new File(plugin.getDataFolder(), "travellers.yml");
        load();
    }

    int count() { return gates.size(); }

    // ---- registry -------------------------------------------------------------------------------------------------------
    private void load() {
        if (file.isFile()) for (String s : YamlConfiguration.loadConfiguration(file).getStringList("gates")) {
            try { index(Gate.decode(s)); } catch (RuntimeException bad) { plugin.getLogger().warning("BACKROOMS_GATE_SKIPPED"); }
        }
        if (travellers.isFile()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(travellers);
            for (String k : y.getKeys(false)) try { cameFrom.put(UUID.fromString(k), y.getString(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Gate g : gates) out.add(g.encode());
        y.set("gates", out);
        YamlConfiguration t = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : cameFrom.entrySet()) t.set(e.getKey().toString(), e.getValue());
        try { plugin.getDataFolder().mkdirs(); y.save(file); t.save(travellers); }
        catch (IOException e) { plugin.getLogger().warning("BACKROOMS_GATES_UNSAVED " + e.getClass().getSimpleName()); }
    }

    private void index(Gate g) {
        gates.add(g);
        for (int a = 0; a < g.w; a++) for (int b = 0; b < g.h; b++)
            interior.put(key(g.world, g.xAxis ? g.x + a : g.x, g.y + b, g.xAxis ? g.z : g.z + a), g);
    }

    private void unindex(Gate g) {
        gates.remove(g);
        interior.values().removeIf(x -> x == g);
    }

    private static long key(String world, int x, int y, int z) { return Protect.key(x, y, z) ^ ((long) world.hashCode() << 50); }

    Gate gateAt(Block b) { return interior.get(key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ())); }

    private Gate gateFramedBy(Block b) {
        String w = b.getWorld().getName();
        for (Gate g : gates) if (g.world.equals(w) && g.frames(b.getX(), b.getY(), b.getZ())) return g;
        return null;
    }

    // ---- finding a frame ------------------------------------------------------------------------------------------------
    static boolean open(Material m) { return m == Material.AIR || m == Material.FIRE; }

    /** A frame of FRAME around the open block at (x, y, z), along x or along z, or null. */
    static Gate detect(String world, Blocks w, int x, int y, int z) {
        for (int axis = 0; axis < 2; axis++) {
            Gate g = detect(world, w, x, y, z, axis == 0);
            if (g != null) return g;
        }
        return null;
    }

    private static Gate detect(String world, Blocks w, int x0, int y0, int z0, boolean xAxis) {
        int dx = xAxis ? 1 : 0, dz = xAxis ? 0 : 1;
        if (!open(w.type(x0, y0, z0))) return null;
        int by = y0;
        while (y0 - by < MAX && open(w.type(x0, by - 1, z0))) by--;
        if (w.type(x0, by - 1, z0) != FRAME) return null;
        int lo = 0, hi = 0;
        while (lo < MAX && open(w.type(x0 - dx * (lo + 1), by, z0 - dz * (lo + 1)))) lo++;
        while (hi < MAX && open(w.type(x0 + dx * (hi + 1), by, z0 + dz * (hi + 1)))) hi++;
        int sx = x0 - dx * lo, sz = z0 - dz * lo, width = lo + hi + 1;
        if (width < 2 || width > MAX) return null;
        if (w.type(sx - dx, by, sz - dz) != FRAME || w.type(sx + dx * width, by, sz + dz * width) != FRAME) return null;
        int top = by;
        while (top - by < MAX && open(w.type(sx, top + 1, sz))) top++;
        int height = top - by + 1;
        if (height < 3 || height > MAX || w.type(sx, top + 1, sz) != FRAME) return null;
        for (int a = 0; a < width; a++) {
            int cx = sx + dx * a, cz = sz + dz * a;
            if (w.type(cx, by - 1, cz) != FRAME || w.type(cx, top + 1, cz) != FRAME) return null;
            for (int yy = by; yy <= top; yy++) if (!open(w.type(cx, yy, cz))) return null;
        }
        for (int yy = by; yy <= top; yy++)
            if (w.type(sx - dx, yy, sz - dz) != FRAME || w.type(sx + dx * width, yy, sz + dz * width) != FRAME) return null;
        return new Gate(world, xAxis, sx, by, sz, width, height);
    }

    // ---- lighting -------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL && e.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) return;
        Block b = e.getBlock();
        World w = b.getWorld();
        Gate g = detect(w.getName(), (x, y, z) -> w.getBlockAt(x, y, z).getType(), b.getX(), b.getY(), b.getZ());
        if (g == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p == null) return;   // a dispenser's fire charge lights nothing
        if (plugin.isBackrooms(w)) { p.sendMessage(ChatColor.YELLOW + "No new way out can be made in here. The Threshold's gate is the only door home."); return; }
        if (w != plugin.main()) { p.sendMessage(ChatColor.YELLOW + "The yellow frame stays cold here. Build it in the overworld."); return; }
        if (!plugin.conquest().conqueror(p)) {
            refusedLights++;
            p.playSound(b.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
            p.sendMessage(ChatColor.YELLOW + "The flame dies in the frame. " + ChatColor.GRAY + "Only a conqueror can open the Backrooms: beat Atlas, Drownhollow, the Nether or the Ender Dragon first. "
                + "(If you did before today, visit that realm once and its guide will remember.)");
            plugin.getLogger().info("BACKROOMS_PORTAL_REFUSED reason=not_conqueror");
            return;
        }
        light(g, w);
        p.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "The frame hums. " + ChatColor.GRAY + "The way into the Backrooms is open: stand in it for three seconds.");
        plugin.getLogger().info("BACKROOMS_PORTAL_LIT w=" + g.w + " h=" + g.h + " gates=" + gates.size() + " realms=" + Conquest.conquered(p).replace(", ", "+").replace(' ', '_'));
    }

    @SuppressWarnings("deprecation")
    void light(Gate g, World w) {
        for (int a = 0; a < g.w; a++) for (int b = 0; b < g.h; b++) {
            Block blk = w.getBlockAt(g.xAxis ? g.x + a : g.x, g.y + b, g.xAxis ? g.z : g.z + a);
            blk.setTypeIdAndData(Material.PORTAL.getId(), (byte) (g.xAxis ? 1 : 2), false);
        }
        index(g);
        lit++;
        save();
        w.playSound(new Location(w, g.x + 0.5, g.y + 1, g.z + 0.5), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.4f);
    }

    @SuppressWarnings("deprecation")
    private void close(Gate g, String why) {
        World w = Bukkit.getWorld(g.world);
        if (w != null) for (int a = 0; a < g.w; a++) for (int b = 0; b < g.h; b++) {
            Block blk = w.getBlockAt(g.xAxis ? g.x + a : g.x, g.y + b, g.xAxis ? g.z : g.z + a);
            if (blk.getType() == Material.PORTAL) blk.setTypeIdAndData(0, (byte) 0, false);
        }
        unindex(g);
        closed++;
        save();
        plugin.getLogger().info("BACKROOMS_PORTAL_CLOSED reason=" + why + " gates=" + gates.size());
    }

    // ---- keeping gates standing, and vanilla out of them ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.PORTAL) return;
        if (plugin.isBackrooms(b.getWorld()) || gateAt(b) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { broke(e.getBlock(), "broken"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { for (Block b : e.blockList()) broke(b, "explosion"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { for (Block b : e.blockList()) broke(b, "explosion"); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (b.getType() == FRAME && gateFramedBy(b) != null || gateAt(b) != null) { e.setCancelled(true); return; }
    }

    private void broke(Block b, String why) {
        if (plugin.isBackrooms(b.getWorld())) return;
        Material m = b.getType();
        if (m != FRAME && m != Material.PORTAL) return;
        Gate g = m == Material.PORTAL ? gateAt(b) : gateFramedBy(b);
        if (g != null) close(g, why);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaTravel(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) return;
        Location f = e.getFrom();
        if (plugin.isBackrooms(f.getWorld()) || nearGate(f) != null) { e.setCancelled(true); vanillaBlocked++; }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTravel(EntityPortalEvent e) {
        Location f = e.getFrom();
        if (plugin.isBackrooms(f.getWorld()) || nearGate(f) != null) e.setCancelled(true);
    }

    /** The overworld gate a spot is in (feet or head block), or null. */
    private Gate nearGate(Location l) {
        Block feet = l.getBlock();
        Gate g = gateAt(feet);
        return g != null ? g : gateAt(feet.getRelative(0, 1, 0));
    }

    /** Whether a spot lies in the Threshold's gate. */
    static boolean inThreshold(Location l) {
        int[] g = Rooms.gate();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x == g[0] && z >= g[2] && z <= g[2] + 1 && y >= g[1] - 1 && y <= g[1] + 2;
    }

    // ---- crossing -------------------------------------------------------------------------------------------------------
    /** Every four ticks: players standing in a gate cross after three seconds (at once in creative). */
    void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            boolean here = plugin.isBackrooms(w);
            if (!here && w != plugin.main()) continue;
            UUID id = p.getUniqueId();
            Location l = p.getLocation();
            boolean inPortal = l.getBlock().getType() == Material.PORTAL || l.getBlock().getRelative(0, 1, 0).getType() == Material.PORTAL;
            Gate g = !inPortal ? null : here ? null : nearGate(l);
            boolean threshold = inPortal && here && inThreshold(l);
            if (g == null && !threshold) { standing.remove(id); mustLeave.remove(id); continue; }
            if (mustLeave.contains(id) || p.getGameMode() == GameMode.SPECTATOR) continue;
            int n = standing.merge(id, 1, Integer::sum);
            if (n == 1) p.spawnParticle(Particle.END_ROD, l.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.02);
            if (p.getGameMode() != GameMode.CREATIVE && n < STAND_CHECKS) continue;
            standing.remove(id);
            mustLeave.add(id);
            if (threshold) goHome(p); else goIn(p, g);
        }
    }

    private void goIn(Player p, Gate g) {
        if (!plugin.conquest().conqueror(p)) {
            refusedEntries++;
            Long last = refused.get(p.getUniqueId());
            if (last == null || System.currentTimeMillis() - last > 5000) {
                refused.put(p.getUniqueId(), System.currentTimeMillis());
                p.sendMessage(ChatColor.YELLOW + "The gate will not take you. " + ChatColor.GRAY + "Only a conqueror of Atlas, Drownhollow, the Nether or the End may enter the Backrooms.");
            }
            Vector away = g.xAxis ? new Vector(0, 0.25, p.getLocation().getZ() < g.z + 0.5 ? -0.6 : 0.6) : new Vector(p.getLocation().getX() < g.x + 0.5 ? -0.6 : 0.6, 0.25, 0);
            p.setVelocity(away);
            return;
        }
        World bw = plugin.ensureWorld();
        if (bw == null) { p.sendMessage(ChatColor.GRAY + "The Backrooms are not ready. Try again shortly."); return; }
        cameFrom.put(p.getUniqueId(), g.id());
        save();
        double[] t = Rooms.threshold();
        plugin.go(p, new Location(bw, t[0], t[1], t[2], (float) t[3], 0), true);
        travelsIn++;
        plugin.getLogger().info("BACKROOMS_TRAVEL direction=in travelsIn=" + travelsIn);
    }

    /** Through the Threshold's gate: back to the gate the player came from, else the overworld spawn. */
    void goHome(Player p) {
        World main = plugin.main();
        String from = cameFrom.get(p.getUniqueId());
        Gate g = null;
        if (from != null) for (Gate x : gates) if (x.id().equals(from)) g = x;
        Location to;
        if (g != null && Bukkit.getWorld(g.world) != null) to = front(Bukkit.getWorld(g.world), g);
        else to = p.getBedSpawnLocation() != null ? p.getBedSpawnLocation() : main.getSpawnLocation();
        plugin.go(p, to, false);
        travelsOut++;
        plugin.getLogger().info("BACKROOMS_TRAVEL direction=out toGate=" + (g != null) + " travelsOut=" + travelsOut);
    }

    /** A standing spot just in front of a gate (whichever side has room), facing away from it. */
    static Location front(World w, Gate g) {
        double cx = g.xAxis ? g.x + g.w / 2.0 : g.x + 0.5, cz = g.xAxis ? g.z + 0.5 : g.z + g.w / 2.0;
        for (int side : new int[] {1, -1}) {
            int fx = (int) Math.floor(g.xAxis ? cx : cx + side * 1.5), fz = (int) Math.floor(g.xAxis ? cz + side * 1.5 : cz);
            Block feet = w.getBlockAt(fx, g.y, fz);
            if (open(feet.getType()) && open(feet.getRelative(0, 1, 0).getType())) {
                Location l = new Location(w, g.xAxis ? cx : fx + 0.5, g.y, g.xAxis ? fz + 0.5 : cz);
                l.setDirection(new Vector(g.xAxis ? 0 : side, 0, g.xAxis ? side : 0));
                return l;
            }
        }
        return new Location(w, cx, g.y, cz);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); standing.remove(id); mustLeave.remove(id); refused.remove(id); }

    String describe() {
        return "gates=" + gates.size() + " lit=" + lit + " refusedLights=" + refusedLights + " refusedEntries=" + refusedEntries + " in=" + travelsIn + " out=" + travelsOut
            + " closed=" + closed + " vanillaBlocked=" + vanillaBlocked;
    }
}
