package chat.jaspr.atlas;

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
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Quartz gates between the overworld and Atlas. A frame shaped like a nether portal (inside 2-21 wide, 3-21 tall, corners
 * optional) built of quartz blocks is kindled with both halves of the divided light: lapis lazuli (the blue) and coal or
 * charcoal (the black), used on the frame or thrown in. Its surface then burns half blue, half black (the browser client
 * colours it). Standing in a gate for three seconds (at once in creative) crosses over:
 * <ul>
 *   <li>from the overworld, to the Atlas gate you last left by, else to the Great Quartz Gate at the Gate of Strangers;</li>
 *   <li>from Atlas, back to the overworld gate you came through, else to the overworld spawn.</li>
 * </ul>
 * Vanilla would tear these frames down and send people to the Nether: their blocks are shielded from physics and
 * vanilla travel is cancelled for any body touching them (vanilla starts a trip on any overlap with the portal cell).
 */
final class Gates implements Listener {
    static final int FRAME = 155, PORTAL = 90, AIR = 0, FIRE = 51;
    private static final int STAND_CHECKS = 15, PERIOD = 4;

    static final class Gate {
        final String world;
        final boolean xAxis;
        final int x, y, z, w, h;
        final boolean fixed;   // the Great Quartz Gate: never closes
        Gate(String world, boolean xAxis, int x, int y, int z, int w, int h, boolean fixed) {
            this.world = world; this.xAxis = xAxis; this.x = x; this.y = y; this.z = z; this.w = w; this.h = h; this.fixed = fixed;
        }
        int dx() { return xAxis ? 1 : 0; }
        int dz() { return xAxis ? 0 : 1; }
        String id() { return world + ";" + x + ";" + y + ";" + z; }
        String encode() { return world + ";" + (xAxis ? "x" : "z") + ";" + x + ";" + y + ";" + z + ";" + w + ";" + h + ";" + (fixed ? "fixed" : "built"); }
        static Gate decode(String s) {
            String[] f = s.split(";");
            return new Gate(f[0], "x".equals(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]), f.length > 7 && f[7].equals("fixed"));
        }
    }

    interface Blocks { int id(int x, int y, int z); }

    static long key(int x, int y, int z) { return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL); }

    private final AtlasPlugin plugin;
    private final File file;
    private final List<Gate> gates = new ArrayList<>();
    private final Map<String, Map<Long, Gate>> inner = new HashMap<>(), frames = new HashMap<>();
    private final Map<String, Gate> ids = new HashMap<>();
    private final Map<UUID, Integer> standing = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> mustLeave = new HashSet<>();
    private final Map<Long, Integer> charge = new HashMap<>();   // frame bottom-left key -> 1 blue, 2 black
    private final Set<UUID> thrown = new HashSet<>();
    private long tick;
    long lit, travels, closed, vanillaBlocked;

    Gates(AtlasPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gates.yml");
    }

    int count() { return gates.size(); }

    // ------------------------------------------------------------------ registry

    void load() {
        gates.clear(); inner.clear(); frames.clear(); ids.clear();
        if (file.isFile())
            for (String s : YamlConfiguration.loadConfiguration(file).getStringList("gates")) {
                try { index(Gate.decode(s)); } catch (RuntimeException bad) { plugin.getLogger().warning("ATLAS_GATE_SKIPPED"); }
            }
        // The Great Quartz Gate at the Gate of Strangers is always known.
        Gate great = great();
        if (!ids.containsKey(great.id())) { index(great); save(); }
    }

    static Gate great() {
        return new Gate(AtlasPlugin.WORLD, false, Threshold.GATE_X, Threshold.gateY(), Threshold.GATE_Z0, Threshold.GATE_W, Threshold.GATE_H, true);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> out = new ArrayList<>();
        for (Gate g : gates) out.add(g.encode());
        y.set("gates", out);
        try { plugin.getDataFolder().mkdirs(); y.save(file); } catch (IOException e) { plugin.getLogger().warning("ATLAS_GATES_SAVE_FAILED " + e.getClass().getSimpleName()); }
    }

    private void index(Gate g) {
        gates.add(g);
        ids.put(g.id(), g);
        Map<Long, Gate> in = inner.computeIfAbsent(g.world, k -> new HashMap<>()), fr = frames.computeIfAbsent(g.world, k -> new HashMap<>());
        for (int a = 0; a < g.w; a++) for (int b = 0; b < g.h; b++) in.put(key(g.x + g.dx() * a, g.y + b, g.z + g.dz() * a), g);
        for (int a = -1; a <= g.w; a++) { fr.put(key(g.x + g.dx() * a, g.y - 1, g.z + g.dz() * a), g); fr.put(key(g.x + g.dx() * a, g.y + g.h, g.z + g.dz() * a), g); }
        for (int b = 0; b < g.h; b++) { fr.put(key(g.x - g.dx(), g.y + b, g.z - g.dz()), g); fr.put(key(g.x + g.dx() * g.w, g.y + b, g.z + g.dz() * g.w), g); }
    }

    private void unindex(Gate g) {
        gates.remove(g);
        if (ids.get(g.id()) == g) ids.remove(g.id());
        Map<Long, Gate> in = inner.get(g.world), fr = frames.get(g.world);
        if (in != null) in.values().removeIf(v -> v == g);
        if (fr != null) fr.values().removeIf(v -> v == g);
    }

    Gate byId(String id) { return id == null ? null : ids.get(id); }

    /** Forgets the player-built gates of a world whose land was drawn again (the Great Gate is drawn with it). */
    int forgetWorld(String world) {
        int n = 0;
        for (Gate g : new ArrayList<>(gates)) if (g.world.equals(world) && !g.fixed) { unindex(g); n++; }
        if (n > 0) save();
        return n;
    }

    private Gate innerAt(Block b) { Map<Long, Gate> m = inner.get(b.getWorld().getName()); return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ())); }

    private Gate frameAt(Block b) { Map<Long, Gate> m = frames.get(b.getWorld().getName()); return m == null ? null : m.get(key(b.getX(), b.getY(), b.getZ())); }

    // ------------------------------------------------------------------ frame detection (pure)

    private static boolean hollow(int id) { return id == AIR || id == FIRE || id == PORTAL; }

    /** The gate whose interior contains (x, y, z), if a complete quartz frame surrounds it along that axis. */
    static Gate detect(Blocks b, String world, int x, int y, int z, boolean xAxis) {
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
        return new Gate(world, xAxis, x0, by, z0, width, height, false);
    }

    @SuppressWarnings("deprecation")
    private static Blocks of(World w) { return (x, y, z) -> y < 0 || y > 255 ? -1 : w.getBlockAt(x, y, z).getTypeId(); }

    @SuppressWarnings("deprecation")
    private static void fill(World w, Gate g) {
        byte axis = (byte) (g.xAxis ? 1 : 2);
        for (int a = 0; a < g.w; a++) for (int b = 0; b < g.h; b++) w.getBlockAt(g.x + g.dx() * a, g.y + b, g.z + g.dz() * a).setTypeIdAndData(PORTAL, axis, false);
    }

    private Gate findFrame(World w, Block at) {
        Gate g = detect(of(w), w.getName(), at.getX(), at.getY(), at.getZ(), true);
        return g != null ? g : detect(of(w), w.getName(), at.getX(), at.getY(), at.getZ(), false);
    }

    /** The frame a clicked quartz block belongs to, whichever face was clicked (the inside, the front, the top...). */
    private Gate frameOf(World w, Block clicked, BlockFace face) {
        Gate g = findFrame(w, clicked.getRelative(face));
        if (g != null) return g;
        for (BlockFace f : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (f == face) continue;
            Block n = clicked.getRelative(f);
            if (!hollow(n.getTypeId())) continue;
            g = findFrame(w, n);
            if (g != null) return g;
        }
        return null;
    }

    // ------------------------------------------------------------------ kindling: lapis (blue) and coal (black)

    private static int half(ItemStack item) {
        if (item == null) return 0;
        if (item.getType() == Material.INK_SACK && item.getDurability() == 4) return 1;
        if (item.getType() == Material.COAL) return 2;
        return 0;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void kindle(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getBlockFace() == null) return;
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND && half(e.getItem()) == 0) return;   // the off hand's echo of a main-hand click
        World w = e.getClickedBlock().getWorld();
        if (!plugin.gateWorld(w) || e.getClickedBlock().getType() != Material.QUARTZ_BLOCK) return;
        ItemStack item = e.getItem();
        int half = half(item);
        Player p = e.getPlayer();
        Gate g = frameOf(w, e.getClickedBlock(), e.getBlockFace());
        if (g == null) {
            if (half != 0) {
                e.setCancelled(true);
                p.sendMessage(ChatColor.GRAY + "This quartz is not a closed gate frame yet. Build it like a nether portal: a ring of quartz blocks at least "
                    + ChatColor.WHITE + "4 wide and 5 tall" + ChatColor.GRAY + " (inside 2 by 3 or more), standing upright, then kindle it.");
            }
            return;
        }
        if (ids.containsKey(g.id())) return;   // already burning
        if (half == 0) {
            p.sendMessage(ChatColor.AQUA + "The quartz frame hums, waiting. " + ChatColor.GRAY + "Kindle it with both halves of the divided light: "
                + ChatColor.BLUE + "lapis lazuli" + ChatColor.GRAY + " and " + ChatColor.DARK_GRAY + "coal" + ChatColor.GRAY + ".");
            return;
        }
        e.setCancelled(true);
        feed(p, w, g, half, () -> { if (p.getGameMode() != GameMode.CREATIVE) consume(p, e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND); });
    }

    private static void consume(Player p, boolean offHand) {
        ItemStack held = offHand ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (held == null) return;
        if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
        else if (offHand) p.getInventory().setItemInOffHand(null);
        else p.getInventory().setItemInMainHand(null);
    }

    /** Feeds one half of the light to a frame; when both halves are in, the gate burns. */
    private void feed(Player p, World w, Gate g, int half, Runnable pay) {
        long k = key(g.x, g.y, g.z);
        int had = charge.getOrDefault(k, 0);
        if ((had & half) != 0) {
            if (p != null) p.sendMessage(ChatColor.GRAY + "The frame already holds its " + (half == 1 ? "blue" : "black") + " half. It waits for the " + (half == 1 ? "black" : "blue") + ".");
            return;
        }
        pay.run();
        int now = had | half;
        Location mid = new Location(w, g.x + g.dx() * g.w / 2.0 + 0.5, g.y + g.h / 2.0, g.z + g.dz() * g.w / 2.0 + 0.5);
        if (now != 3) {
            charge.put(k, now);
            w.playSound(mid, half == 1 ? Sound.BLOCK_NOTE_CHIME : Sound.BLOCK_NOTE_BASS, 1f, half == 1 ? 1.4f : 0.5f);
            w.spawnParticle(half == 1 ? Particle.WATER_SPLASH : Particle.SMOKE_LARGE, mid, 30, 0.5, 1, 0.5, 0.02);
            if (p != null) p.sendMessage(half == 1 ? ChatColor.BLUE + "The quartz drinks the blue light. " + ChatColor.GRAY + "It waits for its darker half: coal."
                : ChatColor.DARK_GRAY + "The quartz drinks the black. " + ChatColor.GRAY + "It waits for its brighter half: lapis lazuli.");
            return;
        }
        charge.remove(k);
        fill(w, g);
        index(g);
        save();
        lit++;
        w.playSound(mid, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.2f);
        w.playSound(mid, Sound.ENTITY_WITHER_SPAWN, 0.25f, 1.8f);
        w.spawnParticle(Particle.END_ROD, mid, 60, 0.6, 1.2, 0.6, 0.05);
        if (p != null) p.sendMessage(ChatColor.AQUA + "The gate wakes, half blue, half black. " + ChatColor.GRAY + "Stand in it to cross "
            + (plugin.isAtlas(w) ? "home." : "to Atlas, the Divided Realm."));
        plugin.getLogger().info("ATLAS_GATE_LIT world=" + w.getName() + " size=" + g.w + "x" + g.h + (p == null ? "" : " by=" + p.getName()));
    }

    @EventHandler(ignoreCancelled = true)
    public void dropped(PlayerDropItemEvent e) {
        if (half(e.getItemDrop().getItemStack()) != 0 && plugin.gateWorld(e.getPlayer().getWorld())) thrown.add(e.getItemDrop().getUniqueId());
    }

    /** Thrown lapis or coal that has come to rest inside a quartz frame feeds it. */
    private void checkThrown() {
        if (thrown.isEmpty()) return;
        for (UUID id : new ArrayList<>(thrown)) {
            Entity en = Bukkit.getEntity(id);
            if (!(en instanceof Item) || en.isDead()) { thrown.remove(id); continue; }
            if (!en.isOnGround() || en.getTicksLived() < 10) continue;
            thrown.remove(id);
            Item item = (Item) en;
            Gate g = findFrame(en.getWorld(), en.getLocation().getBlock());
            if (g == null || ids.containsKey(g.id())) continue;
            int half = half(item.getItemStack());
            feed(null, en.getWorld(), g, half, () -> {
                ItemStack s = item.getItemStack();
                if (s.getAmount() > 1) { s.setAmount(s.getAmount() - 1); item.setItemStack(s); } else item.remove();
            });
        }
    }

    // ------------------------------------------------------------------ closing and protection

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent e) {
        if (gates.isEmpty()) return;
        Block b = e.getBlock();
        Map<Long, Gate> m = inner.get(b.getWorld().getName());
        if (m != null && m.containsKey(key(b.getX(), b.getY(), b.getZ()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        Gate g = frameAt(e.getBlock());
        if (g == null) g = innerAt(e.getBlock());
        if (g == null) return;
        if (g.fixed) { e.setCancelled(true); e.getPlayer().sendMessage(ChatColor.GRAY + "The Great Quartz Gate does not yield."); return; }
        close(g, "broken");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(EntityExplodeEvent e) { protectAndClose(e.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(BlockExplodeEvent e) { protectAndClose(e.blockList()); }

    private void protectAndClose(List<Block> blocks) {
        for (Block b : new ArrayList<>(blocks)) {
            Gate g = frameAt(b);
            if (g == null) g = innerAt(b);
            if (g == null) continue;
            if (g.fixed) blocks.remove(b); else close(g, "explosion");
        }
    }

    private void close(Gate g, String why) {
        if (!gates.contains(g) || g.fixed) return;
        unindex(g);
        save();
        closed++;
        World w = Bukkit.getWorld(g.world);
        if (w != null) Bukkit.getScheduler().runTask(plugin, () -> {
            for (int a = 0; a < g.w; a++)
                for (int b = 0; b < g.h; b++) {
                    Block block = w.getBlockAt(g.x + g.dx() * a, g.y + b, g.z + g.dz() * a);
                    if (block.getType() == Material.PORTAL) block.setType(Material.AIR, false);
                }
        });
        plugin.getLogger().info("ATLAS_GATE_CLOSED world=" + g.world + " reason=" + why);
    }

    // ------------------------------------------------------------------ vanilla behaviour stays out

    /** The gate whose portal cells this body touches (vanilla starts a portal trip on any overlap). */
    @SuppressWarnings("deprecation")
    private Gate touching(Location l, double halfWidth, double height) {
        World w = l.getWorld();
        if (w == null) return null;
        Map<Long, Gate> m = inner.get(w.getName());
        if (m == null) return null;
        int x0 = (int) Math.floor(l.getX() - halfWidth + 0.001), x1 = (int) Math.floor(l.getX() + halfWidth - 0.001);
        int y0 = (int) Math.floor(l.getY() + 0.001), y1 = (int) Math.floor(l.getY() + height - 0.001);
        int z0 = (int) Math.floor(l.getZ() - halfWidth + 0.001), z1 = (int) Math.floor(l.getZ() + halfWidth - 0.001);
        for (int x = x0; x <= x1; x++)
            for (int y = Math.max(0, y0); y <= Math.min(255, y1); y++)
                for (int z = z0; z <= z1; z++) {
                    Gate g = m.get(key(x, y, z));
                    if (g != null && w.getBlockAt(x, y, z).getTypeId() == PORTAL) return g;
                }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void vanillaTravel(PlayerPortalEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || e.getFrom() == null) return;
        if (touching(e.getFrom(), 0.3, 1.8) == null) return;
        e.setCancelled(true);
        vanillaBlocked++;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void vanillaTravel(EntityPortalEvent e) {
        if (e.getFrom() != null && touching(e.getFrom(), 0.8, 2.0) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pigmen(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NETHER_PORTAL && touching(e.getLocation(), 1.0, 2.0) != null) e.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { UUID id = e.getPlayer().getUniqueId(); standing.remove(id); cooldown.remove(id); mustLeave.remove(id); }

    /** A vanilla Nether trip that lands in a quartz gate is stepped out in front of it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void changedWorld(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            Gate g = touching(player.getLocation(), 0.3, 1.8);
            if (g == null) return;
            UUID id = player.getUniqueId();
            Long until = cooldown.get(id);
            if (until != null && tick < until) return;
            cooldown.put(id, tick + 100);
            mustLeave.add(id);
            player.teleport(arrival(player.getWorld(), g, player.getLocation()), PlayerTeleportEvent.TeleportCause.PLUGIN);
        });
    }

    // ------------------------------------------------------------------ travel

    void tick() {
        tick += PERIOD;
        checkThrown();
        if (gates.isEmpty()) { standing.clear(); return; }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.gateWorld(player.getWorld()) || player.isDead()) continue;
            UUID id = player.getUniqueId();
            Gate g = touching(player.getLocation(), 0.3, 1.8);
            if (g == null) { standing.remove(id); mustLeave.remove(id); continue; }
            if (mustLeave.contains(id)) continue;
            Long until = cooldown.get(id);
            if (until != null && tick < until) continue;
            int n = standing.merge(id, 1, Integer::sum);
            if (n >= (player.getGameMode() == GameMode.CREATIVE ? 1 : STAND_CHECKS)) {
                standing.remove(id);
                cooldown.put(id, tick + 100);
                travel(player, g);
            }
        }
    }

    private void travel(Player player, Gate from) {
        long t0 = System.nanoTime();
        State.Player rec = plugin.state().player(player.getUniqueId(), player.getName());
        World src = player.getWorld();
        Location arrive;
        String route;
        if (plugin.isAtlas(src)) {
            // Home: the gate they came through, else the overworld spawn.
            rec.atlasGate = from.id();
            Gate home = byId(rec.homeGate);
            World main = plugin.main();
            if (home != null && Bukkit.getWorld(home.world) != null) { arrive = arrival(Bukkit.getWorld(home.world), home, player.getLocation()); route = "home"; }
            else { arrive = main.getSpawnLocation().add(0.5, 0, 0.5); route = "spawn"; }
        } else {
            World atlas = plugin.ensureAtlas();
            if (atlas == null) { player.sendMessage(ChatColor.RED + "Atlas cannot be reached right now."); return; }
            rec.homeGate = from.id();
            Gate back = byId(rec.atlasGate);
            if (back == null || !back.world.equals(atlas.getName())) back = great();
            if (!ids.containsKey(back.id())) back = great();
            arrive = arrival(atlas, back, null);
            route = back.fixed ? "threshold" : "atlasGate";
        }
        if (player.isInsideVehicle()) player.leaveVehicle();
        player.setFallDistance(0f);
        player.setVelocity(new Vector());
        boolean ok = player.teleport(arrive, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (ok) mustLeave.add(player.getUniqueId());
        travels++;
        plugin.saveStateSoon();
        plugin.getLogger().info("ATLAS_TRAVEL player=" + player.getName() + " from=" + src.getName() + " to=" + arrive.getWorld().getName()
            + " route=" + route + " ok=" + ok + " ms=" + (System.nanoTime() - t0) / 1_000_000L);
    }

    /** Beside the gate on the first floor within three blocks below its sill, facing away; inside only if boxed in. */
    @SuppressWarnings("deprecation")
    static Location arrival(World w, Gate g, Location facing) {
        if (g.fixed) {
            // The Great Gate faces east onto the court.
            return new Location(w, g.x + 2.5, g.y, g.z + g.w / 2.0, -90f, 0f);
        }
        int mid = g.w / 2, bx = g.x + g.dx() * mid, bz = g.z + g.dz() * mid, px = g.dz(), pz = g.dx();
        for (int drop = 0; drop <= 3; drop++)
            for (int side = 1; side >= -1; side -= 2) {
                int x = bx + px * side, z = bz + pz * side, y = g.y - drop;
                boolean clear = solid(w.getBlockAt(x, y - 1, z).getTypeId());
                for (int yy = y; clear && yy <= g.y + 1; yy++) clear = passable(w.getBlockAt(x, yy, z).getTypeId());
                if (!clear) continue;
                float yaw = (float) Math.toDegrees(Math.atan2(-px * side, pz * side));
                return new Location(w, x + 0.5, y, z + 0.5, yaw, 0f);
            }
        return new Location(w, bx + 0.5, g.y, bz + 0.5, facing == null ? 0f : facing.getYaw(), 0f);
    }

    private static boolean passable(int id) { return id == AIR || id == 31 || id == 37 || id == 38 || id == 175 || id == 78 || id == 106 || id == 32 || id == 171; }
    private static boolean solid(int id) { return id != AIR && id != 8 && id != 9 && id != 10 && id != 11 && id != 18 && id != 161 && id != 51 && id != PORTAL && !passable(id); }

    /** Re-kindles the Great Gate's surface if it was lost (after a world regeneration or an edit). */
    @SuppressWarnings("deprecation")
    void repairGreatGate(World atlas) {
        Gate g = great();
        if (atlas == null || !atlas.isChunkLoaded(g.x >> 4, g.z >> 4)) return;
        if (atlas.getBlockAt(g.x, g.y, g.z).getTypeId() != PORTAL) { fill(atlas, g); plugin.getLogger().info("ATLAS_GREAT_GATE_REKINDLED"); }
    }
}
