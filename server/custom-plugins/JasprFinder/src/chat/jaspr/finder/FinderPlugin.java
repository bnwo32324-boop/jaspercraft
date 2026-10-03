package chat.jaspr.finder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

/**
 * Chest Finder (owner, 2026-10-03: "right-click [an item in the crafting panel] ... a UI element that says Find. If you
 * click Find, it should show particle effects on the chest that has that particular item").
 *
 * The browser client's recipe panel sends "find &lt;id&gt; &lt;damage&gt; &lt;exact&gt; &lt;title&gt;" on the plugin channel jaspr:find.
 * This searches the storage near the player and makes every container holding the item sparkle for the player alone,
 * with a column of sparks above it, for fifteen seconds; the chat says how many and where the nearest is.
 *
 * Privacy: by default only containers this player has opened themselves are searched (search: opened), so nobody can
 * learn what another player keeps in a chest they never saw; "search: all" searches every container in range. The
 * player's own ender chest contents light up every ender chest nearby. Remembered containers are kept per player (at
 * most 4096, oldest first out) in plugins/JasprFinder/players/&lt;uuid&gt;.txt.
 *
 * Bounded: one request per player every two seconds, a 48-block radius (config), loaded chunks only, at most 600
 * containers looked at and 16 highlighted per request, one highlight per player at a time.
 * Logs FINDER_READY, FINDER_FIND (player id, item, found, scanned, ms), FINDER_METRICS.
 */
public final class FinderPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    static final String CHANNEL = "jaspr:find";
    static final int MAX_REMEMBERED = 4096, MAX_SCAN = 600, MAX_SHOWN = 16, HIGHLIGHT_TICKS = 300, PULSE = 10;
    static final long COOLDOWN_MS = 2000;

    private final Map<UUID, LinkedHashSet<String>> opened = new HashMap<UUID, LinkedHashSet<String>>();
    private final Map<UUID, Long> lastFind = new HashMap<UUID, Long>();
    private final Map<UUID, BukkitTask> highlights = new HashMap<UUID, BukkitTask>();
    private final java.util.Set<UUID> dirty = new java.util.HashSet<UUID>();
    private File folder;
    private int radius;
    private boolean searchAll;
    long requests, found, misses, rejected, limited, remembered;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        radius = Math.max(8, Math.min(96, getConfig().getInt("radius", 48)));
        searchAll = "all".equalsIgnoreCase(getConfig().getString("search", "opened"));
        folder = new File(getDataFolder(), "players");
        if (!folder.isDirectory() && !folder.mkdirs()) getLogger().warning("FINDER_STORE_UNAVAILABLE");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        for (Player p : getServer().getOnlinePlayers()) load(p.getUniqueId());
        getServer().getScheduler().runTaskTimer(this, this::saveDirty, 6000L, 6000L);
        getLogger().info("FINDER_READY channel=" + CHANNEL + " radius=" + radius + " search=" + (searchAll ? "all" : "opened")
            + " maxShown=" + MAX_SHOWN + " seconds=" + HIGHLIGHT_TICKS / 20);
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : highlights.values()) t.cancel();
        highlights.clear();
        dirty.addAll(opened.keySet());
        saveDirty();
        getLogger().info("FINDER_METRICS requests=" + requests + " found=" + found + " misses=" + misses + " rejected=" + rejected
            + " limited=" + limited + " remembered=" + remembered);
    }

    // ------------------------------------------------------------------ remembering what a player opened

    static String key(Block b) { return b.getWorld().getName() + ':' + b.getX() + ':' + b.getY() + ':' + b.getZ(); }

    private LinkedHashSet<String> load(UUID id) {
        LinkedHashSet<String> set = opened.get(id);
        if (set != null) return set;
        set = new LinkedHashSet<String>();
        File f = new File(folder, id + ".txt");
        if (f.isFile()) {
            try {
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    String k = line.trim();
                    if (k.matches("[A-Za-z0-9_\\-]+:-?\\d+:-?\\d+:-?\\d+")) set.add(k);
                    if (set.size() >= MAX_REMEMBERED) break;
                }
            } catch (IOException e) {
                getLogger().warning("FINDER_LOAD_FAILED player=" + id + " error=" + e.getClass().getSimpleName());
            }
        }
        opened.put(id, set);
        return set;
    }

    private void saveDirty() {
        for (UUID id : new ArrayList<UUID>(dirty)) {
            LinkedHashSet<String> set = opened.get(id);
            if (set == null) continue;
            try {
                Files.write(new File(folder, id + ".txt").toPath(), set, StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("FINDER_SAVE_FAILED player=" + id + " error=" + e.getClass().getSimpleName());
            }
        }
        dirty.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { load(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitTask t = highlights.remove(id);
        if (t != null) t.cancel();
        if (dirty.contains(id)) saveDirty();
        opened.remove(id);
        lastFind.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        InventoryHolder holder = e.getInventory().getHolder();
        List<Block> blocks = new ArrayList<Block>(2);
        if (holder instanceof DoubleChest) {
            DoubleChest dc = (DoubleChest) holder;
            if (dc.getLeftSide() instanceof BlockState) blocks.add(((BlockState) dc.getLeftSide()).getBlock());
            if (dc.getRightSide() instanceof BlockState) blocks.add(((BlockState) dc.getRightSide()).getBlock());
        } else if (holder instanceof BlockState && storage(((BlockState) holder).getType())) {
            blocks.add(((BlockState) holder).getBlock());
        }
        if (blocks.isEmpty()) return;
        UUID id = e.getPlayer().getUniqueId();
        LinkedHashSet<String> set = load(id);
        for (Block b : blocks) {
            String k = key(b);
            boolean known = set.remove(k);   // re-added last: the most recently opened survive the cap
            set.add(k);
            if (!known) remembered++;
            while (set.size() > MAX_REMEMBERED) { Iterator<String> it = set.iterator(); it.next(); it.remove(); }
        }
        dirty.add(id);
    }

    /** Containers worth searching: chests, trapped chests, shulker boxes, hoppers, dispensers and droppers. */
    static boolean storage(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST || m == Material.HOPPER || m == Material.DISPENSER
            || m == Material.DROPPER || m.name().endsWith("SHULKER_BOX");
    }

    // ------------------------------------------------------------------ the request

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] data) {
        if (!CHANNEL.equals(channel) || p == null || !p.isOnline()) return;
        long now = System.currentTimeMillis();
        Long last = lastFind.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) { limited++; return; }
        lastFind.put(p.getUniqueId(), now);
        FindRequest request = FindRequest.parse(decode(data));
        if (request == null || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) { rejected++; return; }
        requests++;
        find(p, request, now);
    }

    /** PacketBuffer.writeString: a VarInt byte length, then UTF-8. Null for anything else. */
    static String decode(byte[] data) {
        if (data == null || data.length < 1 || data.length > 640) return null;
        int value = 0, shift = 0, at = 0;
        while (true) {
            if (at >= data.length || shift > 28) return null;
            byte b = data[at++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (value < 0 || at + value != data.length) return null;
        return new String(data, at, value, StandardCharsets.UTF_8);
    }

    void find(Player p, FindRequest request, long started) {
        World w = p.getWorld();
        Location at = p.getLocation();
        double r2 = (double) radius * radius;
        List<Block> hits = new ArrayList<Block>();
        int scanned = 0;
        // Ender chests: the player's own ender chest contents light up every ender chest nearby.
        boolean ender = false;
        for (ItemStack s : p.getEnderChest().getContents()) if (request.matches(s)) { ender = true; break; }
        int cr = (radius >> 4) + 1, cx = at.getBlockX() >> 4, cz = at.getBlockZ() >> 4;
        if (searchAll || ender) {
            for (int dx = -cr; dx <= cr && scanned < MAX_SCAN; dx++) for (int dz = -cr; dz <= cr && scanned < MAX_SCAN; dz++) {
                if (!w.isChunkLoaded(cx + dx, cz + dz)) continue;
                Chunk chunk = w.getChunkAt(cx + dx, cz + dz);
                for (BlockState st : chunk.getTileEntities()) {
                    if (scanned >= MAX_SCAN) break;
                    Material type = st.getType();
                    boolean enderChest = type == Material.ENDER_CHEST;
                    if (!(enderChest && ender) && !(searchAll && storage(type))) continue;
                    if (st.getLocation().distanceSquared(at) > r2) continue;
                    scanned++;
                    if (enderChest || holds(st, request)) hits.add(st.getBlock());
                }
            }
        }
        if (!searchAll) {
            LinkedHashSet<String> known = load(p.getUniqueId());
            List<String> keys = new ArrayList<String>(known);
            List<String> gone = new ArrayList<String>();
            for (int i = keys.size() - 1; i >= 0 && scanned < MAX_SCAN; i--) {   // most recently opened first
                String k = keys.get(i);
                String[] parts = k.split(":");
                if (parts.length != 4 || !parts[0].equals(w.getName())) continue;
                int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
                double ddx = x + 0.5 - at.getX(), ddy = y + 0.5 - at.getY(), ddz = z + 0.5 - at.getZ();
                if (ddx * ddx + ddy * ddy + ddz * ddz > r2 || !w.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block b = w.getBlockAt(x, y, z);
                if (!storage(b.getType())) { gone.add(k); continue; }   // broken or replaced: forget it
                scanned++;
                if (holds(b.getState(), request)) hits.add(b);
            }
            if (!gone.isEmpty()) { known.removeAll(gone); dirty.add(p.getUniqueId()); }
        }
        Collections.sort(hits, Comparator.comparingDouble(b -> b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(at)));
        int total = hits.size();
        if (hits.size() > MAX_SHOWN) hits = new ArrayList<Block>(hits.subList(0, MAX_SHOWN));
        long ms = System.currentTimeMillis() - started;
        getLogger().info("FINDER_FIND player=" + p.getUniqueId() + " item=" + request.material.name() + ":" + request.damage
            + (request.exact ? ":exact" : "") + " found=" + total + " scanned=" + scanned + " ms=" + ms);
        if (hits.isEmpty()) {
            misses++;
            p.sendMessage(ChatColor.GOLD + "[Find] " + ChatColor.GRAY + "No " + (searchAll ? "chest" : "chest you have opened")
                + " within " + radius + " blocks holds " + ChatColor.WHITE + request.title + ChatColor.GRAY + "."
                + (searchAll ? "" : " Chests count once you have opened them."));
            p.playSound(at, Sound.BLOCK_NOTE_BASS, 0.6f, 0.8f);
            return;
        }
        found++;
        Block nearest = hits.get(0);
        Location n = nearest.getLocation().add(0.5, 0.5, 0.5);
        p.sendMessage(ChatColor.GOLD + "[Find] " + ChatColor.WHITE + request.title + ChatColor.GRAY + " is in " + ChatColor.GREEN + total
            + ChatColor.GRAY + (total == 1 ? " chest" : " chests") + " nearby; the nearest is " + ChatColor.WHITE
            + Math.round(Math.sqrt(n.distanceSquared(at))) + " m " + direction(at, n) + ChatColor.GRAY + ". Follow the green sparks.");
        p.playSound(at, Sound.BLOCK_NOTE_PLING, 0.7f, 1.6f);
        highlight(p, hits);
    }

    /** True when this container (one half of a double chest on its own) holds the item. */
    static boolean holds(BlockState st, FindRequest request) {
        Inventory inv = st instanceof Chest ? ((Chest) st).getBlockInventory()
            : st instanceof InventoryHolder ? ((InventoryHolder) st).getInventory() : null;
        if (inv == null) return false;
        for (ItemStack s : inv.getContents()) if (request.matches(s)) return true;
        return false;
    }

    static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ(), dy = to.getY() - from.getY();
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        int i = (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0);
        String flat = Math.abs(dx) + Math.abs(dz) < 1.5 ? "right here" : names[((i % 8) + 8) % 8];
        if (dy > 2.5) flat += ", above";
        else if (dy < -2.5) flat += ", below";
        return flat;
    }

    /** Sparks for this player alone: the chest's outline and a short column above it, every half second for 15 s. */
    void highlight(Player p, List<Block> blocks) {
        UUID id = p.getUniqueId();
        BukkitTask old = highlights.remove(id);
        if (old != null) old.cancel();
        final int[] age = {0};
        BukkitTask task = getServer().getScheduler().runTaskTimer(this, () -> {
            age[0] += PULSE;
            if (!p.isOnline() || age[0] > HIGHLIGHT_TICKS) {
                BukkitTask self = highlights.remove(id);
                if (self != null) self.cancel();
                return;
            }
            for (Block b : blocks) {
                if (b.getWorld() != p.getWorld()) continue;
                double x = b.getX(), y = b.getY(), z = b.getZ();
                for (int cx = 0; cx <= 1; cx++) for (int cz = 0; cz <= 1; cz++) {
                    p.spawnParticle(Particle.VILLAGER_HAPPY, x + 0.05 + 0.9 * cx, y + 0.95, z + 0.05 + 0.9 * cz, 1, 0, 0, 0, 0);
                    p.spawnParticle(Particle.VILLAGER_HAPPY, x + 0.05 + 0.9 * cx, y + 0.45, z + 0.05 + 0.9 * cz, 1, 0, 0, 0, 0);
                }
                for (double up = 1.3; up <= 3.3; up += 0.5) p.spawnParticle(Particle.VILLAGER_HAPPY, x + 0.5, y + up, z + 0.5, 1, 0.02, 0, 0.02, 0);
            }
        }, 0L, PULSE);
        highlights.put(id, task);
    }
}
