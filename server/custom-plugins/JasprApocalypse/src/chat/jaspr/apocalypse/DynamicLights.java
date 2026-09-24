package chat.jaspr.apocalypse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Server-authoritative dynamic lights feed. Every sweep recomputes the full set
 * of glowing points (held items, dropped glowing items, burning entities) and
 * mirrors them into a hidden vanilla scoreboard objective ("jdl", never given a
 * display slot), so the browser renderer lights meshed terrain with no custom
 * protocol. The JavaScript mirror lives in client-mods/dynamic-lights.js; both
 * sides pin the same holder: "#jdl." + x + "." + y + "." + z + "." + level.
 */
public final class DynamicLights implements Listener {
    static final String OBJECTIVE = "jdl";
    static final String DISPLAY_PREFIX = "JDL v1 n=";
    static final String HOLDER_PREFIX = "#jdl.";

    private static final Map<String, Integer> DEFAULT_LEVELS = new HashMap<String, Integer>();
    static {
        DEFAULT_LEVELS.put("TORCH", 14);
        DEFAULT_LEVELS.put("REDSTONE_TORCH_ON", 7);
        DEFAULT_LEVELS.put("GLOWSTONE", 15);
        DEFAULT_LEVELS.put("JACK_O_LANTERN", 15);
        DEFAULT_LEVELS.put("LAVA_BUCKET", 15);
        DEFAULT_LEVELS.put("LAVA", 15);
        DEFAULT_LEVELS.put("STATIONARY_LAVA", 15);
        DEFAULT_LEVELS.put("FIRE", 15);
        DEFAULT_LEVELS.put("BURNING_FURNACE", 13);
        DEFAULT_LEVELS.put("END_ROD", 14);
        DEFAULT_LEVELS.put("SEA_LANTERN", 15);
        DEFAULT_LEVELS.put("MAGMA", 3);
        DEFAULT_LEVELS.put("BEACON", 15);
        DEFAULT_LEVELS.put("ENDER_CHEST", 7);
    }

    private final ApocalypsePlugin plugin;
    private BukkitTask task;
    private boolean started;
    private long lastResync = 0;
    /** Last computed holder set; pushed incrementally into every viewer board. */
    private final Set<String> desired = new LinkedHashSet<String>();
    private boolean hadSources = false;
    /** Boards already sent a full objective sync, with the holder set they show. */
    private final Map<Scoreboard, Set<String>> synced = new java.util.WeakHashMap<Scoreboard, Set<String>>();
    /** Boards warned about once, so a broken wire shows in the log instead of hiding. */
    private final Set<Scoreboard> syncWarned =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Scoreboard, Boolean>());

    public DynamicLights(ApocalypsePlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (started) return;
        started = true;
        pruneAll();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        int interval = Math.max(5, plugin.getConfig().getInt("dynamic-lights.scan-interval-ticks", 10));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { sweep(); }
        }, interval, interval);
        try {
            for (Player player : Bukkit.getOnlinePlayers()) pushTo(player);
        } catch (RuntimeException ignored) { }
        plugin.getLogger().info("DYNAMIC_LIGHTS_READY " + metrics());
    }

    public void stop() {
        started = false;
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        desired.clear();
        synced.clear();
        pruneAll();
    }

    public String metrics() {
        return "dynamiclights=" + desired.size();
    }

    boolean enabled() { return plugin.getConfig().getBoolean("dynamic-lights.enabled", true); }

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    static String holder(int x, int y, int z, int level) {
        if (level < 1 || level > 15 || y < 0 || y > 255) return null;
        if (Math.abs((long) x) > 30000000L || Math.abs((long) z) > 30000000L) return null;
        return HOLDER_PREFIX + x + "." + y + "." + z + "." + level;
    }

    private int levelOf(Material material) {
        if (material == null) return 0;
        String key = material.name();
        if (plugin.getConfig().isSet("dynamic-lights.levels." + key))
            return Math.max(0, Math.min(15, plugin.getConfig().getInt("dynamic-lights.levels." + key, 0)));
        Integer def = DEFAULT_LEVELS.get(key);
        return def == null ? 0 : def.intValue();
    }

    private int heldLevel(Player player) {
        int best = 0;
        try {
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null) best = Math.max(best, levelOf(main.getType()));
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null) best = Math.max(best, levelOf(off.getType()));
        } catch (RuntimeException ignored) { }
        return best;
    }

    private void sweep() {
        if (!started) return;
        if (!enabled()) { pruneAll(); return; }
        try {
            Set<String> next = new LinkedHashSet<String>();
            int cap = Math.max(8, Math.min(256, plugin.getConfig().getInt("dynamic-lights.max-sources", 48)));
            boolean items = plugin.getConfig().getBoolean("dynamic-lights.dropped-items", true);
            boolean burning = plugin.getConfig().getBoolean("dynamic-lights.burning-entities", true);
            int burnLevel = Math.max(1, Math.min(15, plugin.getConfig().getInt("dynamic-lights.burning-level", 15)));
            for (World world : Bukkit.getWorlds()) {
                if (next.size() >= cap) break;
                for (Player player : world.getPlayers()) {
                    if (next.size() >= cap) break;
                    try {
                        if (!plugin.authenticated(player) || player.isDead()) continue;
                        int level = plugin.getConfig().getBoolean("dynamic-lights.player-held", true)
                            ? heldLevel(player) : 0;
                        if (level < 1) continue;
                        Location at = player.getLocation();
                        String holder = holder(at.getBlockX(), at.getBlockY() + 1, at.getBlockZ(), level);
                        if (holder != null) next.add(holder);
                    } catch (RuntimeException ignored) { }
                }
                if (items && next.size() < cap) {
                    java.util.Collection<Item> drops;
                    try { drops = world.getEntitiesByClass(Item.class); }
                    catch (RuntimeException e) { drops = new ArrayList<Item>(); }
                    for (Item drop : drops) {
                        if (next.size() >= cap) break;
                        try {
                            if (drop == null || drop.isDead() || !drop.isValid()) continue;
                            ItemStack stack = drop.getItemStack();
                            if (stack == null) continue;
                            int level = levelOf(stack.getType());
                            if (level < 1) continue;
                            Location at = drop.getLocation();
                            String holder = holder(at.getBlockX(), at.getBlockY(), at.getBlockZ(), level);
                            if (holder != null) next.add(holder);
                        } catch (RuntimeException ignored) { }
                    }
                }
                if (burning && next.size() < cap) {
                    java.util.Collection<LivingEntity> living;
                    try { living = world.getEntitiesByClass(LivingEntity.class); }
                    catch (RuntimeException e) { living = new ArrayList<LivingEntity>(); }
                    for (LivingEntity entity : living) {
                        if (next.size() >= cap) break;
                        try {
                            if (entity == null || entity.isDead() || !entity.isValid()) continue;
                            if (entity instanceof Player) continue;
                            if (entity.getFireTicks() <= 0) continue;
                            Location at = entity.getLocation();
                            String holder = holder(at.getBlockX(), at.getBlockY() + 1, at.getBlockZ(), burnLevel);
                            if (holder != null) next.add(holder);
                        } catch (RuntimeException ignored) { }
                    }
                }
            }
            desired.clear();
            desired.addAll(next);
            if (!desired.isEmpty() && !hadSources)
                plugin.getLogger().info("DYNAMIC_LIGHTS_SOURCES n=" + desired.size() + " sample=" + next.iterator().next());
            hadSources = !desired.isEmpty();
            for (Player player : Bukkit.getOnlinePlayers()) pushTo(player);
            lastResync = System.currentTimeMillis();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("DYNAMIC_LIGHTS_SWEEP_FAILED " + e.getMessage());
        }
    }

    /**
     * Push the current holder set into one viewer's live scoreboard. Every
     * online player sits on a personal board (see Waypoints.sync), so the main
     * board alone reaches nobody.
     */
    public void pushTo(Player player) {
        if (!started || player == null) return;
        try {
            Scoreboard view = player.getScoreboard();
            if (view == null) return;
            Objective objective = view.getObjective(OBJECTIVE);
            if (objective == null) {
                try { objective = view.registerNewObjective(OBJECTIVE, "dummy"); }
                catch (RuntimeException e) { return; }
            }
            try { objective.setDisplayName(DISPLAY_PREFIX + desired.size()); }
            catch (RuntimeException ignored) { }
            Set<String> stale = currentHolders(view);
            stale.removeAll(desired);
            for (String holder : stale) {
                try { view.resetScores(holder); } catch (RuntimeException ignored) { }
            }
            for (String holder : desired) {
                try { objective.getScore(holder).setScore(0); } catch (RuntimeException ignored) { }
            }
            Set<String> last = synced.get(view);
            if (last == null || !last.equals(desired)) {
                if (syncObjective(view)) {
                    synced.put(view, new LinkedHashSet<String>(desired));
                    syncWarned.remove(view);
                } else if (syncWarned.add(view)) {
                    plugin.getLogger().warning("DYNAMIC_LIGHTS_SYNC_FAILED player=" + player.getName());
                }
            } else {
                syncWarned.remove(view);
            }
        } catch (RuntimeException ignored) { }
    }

    /**
     * Score edits on a freshly registered objective never reach the wire: the
     * server only broadcasts scores for objectives it has fully synced before.
     * Push one full objective sync now (create + current scores + display
     * name); later edits broadcast on their own. NMS use matches the existing
     * NBTTagCompound precedent and is guarded for non-NMS harnesses.
     */
    private boolean syncObjective(Scoreboard view) {
        try {
            if (!(view instanceof org.bukkit.craftbukkit.v1_12_R1.scoreboard.CraftScoreboard)) return false;
            net.minecraft.server.v1_12_R1.Scoreboard handle =
                ((org.bukkit.craftbukkit.v1_12_R1.scoreboard.CraftScoreboard) view).getHandle();
            if (!(handle instanceof net.minecraft.server.v1_12_R1.ScoreboardServer)) return false;
            net.minecraft.server.v1_12_R1.ScoreboardObjective nms = handle.getObjective(OBJECTIVE);
            if (nms == null) return false;
            ((net.minecraft.server.v1_12_R1.ScoreboardServer) handle).e(nms);
            return true;
        } catch (RuntimeException | LinkageError e) { return false; }
    }

    private Set<String> currentHolders(Scoreboard board) {
        Set<String> out = new HashSet<String>();
        try {
            for (String entry : board.getEntries()) {
                if (entry != null && entry.startsWith(HOLDER_PREFIX)) out.add(entry);
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    private void pruneAll() {
        try {
            Scoreboard board = board();
            for (String holder : currentHolders(board)) {
                try { board.resetScores(holder); } catch (RuntimeException ignored) { }
            }
            Objective objective = board.getObjective(OBJECTIVE);
            if (objective != null) {
                try { objective.unregister(); } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }
    }

    public boolean command(CommandSender sender, String[] args) {
        if (args.length > 0 && "resync".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player && !sender.hasPermission("jaspr.apocalypse.admin")) {
                sender.sendMessage(ChatColor.RED + "Operator permission required.");
                return true;
            }
            pruneAll();
            sweep();
            sender.sendMessage(ChatColor.GREEN + "Dynamic lights resynced. " + metrics());
            return true;
        }
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.GOLD + "Dynamic lights: " + ChatColor.GRAY + metrics()
                + ChatColor.GRAY + ". Press " + ChatColor.YELLOW + "L"
                + ChatColor.GRAY + " (Controls: Dynamic Lights) to cycle Fast / Smooth / Off.");
        } else {
            sender.sendMessage("Dynamic lights " + metrics());
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) { lastResync = 0; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) { lastResync = 0; }

    /** Shared golden vectors with client-mods/dynamic-lights.js. */
    static Map<String, String> goldenHolders() {
        Map<String, String> goldens = new HashMap<String, String>();
        goldens.put("123,64,-456,14", "#jdl.123.64.-456.14");
        goldens.put("-30000000,0,30000000,15", "#jdl.-30000000.0.30000000.15");
        return goldens;
    }
}
