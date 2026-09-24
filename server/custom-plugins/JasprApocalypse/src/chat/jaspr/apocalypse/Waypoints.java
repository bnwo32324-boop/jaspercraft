package chat.jaspr.apocalypse;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Player waypoints: deathpoints, a management menu (/waypoints, M key), a live
 * action-bar compass, and a hidden vanilla scoreboard objective ("jwp") that feeds
 * the browser marker renderer at any distance. Storage is one YAML file; per-player
 * scoreboards keep every browser on vanilla protocol with no custom packets.
 */
public final class Waypoints implements Listener {
    static final int MANUAL_SLOTS = 12;
    static final int DEATH_SLOTS = 3;
    static final int[] DEATH_POOL = {30, 31, 32};
    static final int MAX_NAME = 24;
    /** Redraw cadence while held. Two ticks keeps the vanilla overlay permanently opaque. */
    static final long COMPASS_PERIOD_TICKS = 2L;
    /** A hold survives this long without a heartbeat, covering lost focus and packet jitter. */
    static final long COMPASS_HOLD_EXPIRY_MS = 3500L;

    static final class PendingCreate {
        final String world;
        final int x, y, z;
        PendingCreate(String world, int x, int y, int z) { this.world = world; this.x = x; this.y = y; this.z = z; }
    }

    static final class DeleteArm {
        final int slot;
        final long expiresAt;
        DeleteArm(int slot, long expiresAt) { this.slot = slot; this.expiresAt = expiresAt; }
    }

    private final ApocalypsePlugin plugin;
    private BukkitTask task;
    private boolean started;
    private File file;
    private final Map<UUID, List<Waypoint>> data = new LinkedHashMap<UUID, List<Waypoint>>();
    private final Map<UUID, Integer> active = new LinkedHashMap<UUID, Integer>();
    private final Map<UUID, PendingCreate> pending = new ConcurrentHashMap<UUID, PendingCreate>();
    private final Map<UUID, DeleteArm> armed = new LinkedHashMap<UUID, DeleteArm>();
    /** Players whose Tab key is down right now, with the time of their last heartbeat. */
    private final Map<UUID, Long> compassHold = new ConcurrentHashMap<UUID, Long>();

    public Waypoints(ApocalypsePlugin plugin) { this.plugin = plugin; }

    ApocalypsePlugin plugin() { return plugin; }

    public void start() {
        if (started) return;
        started = true;
        file = new File(plugin.getDataFolder(), "waypoints.yml");
        load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // The compass is a live readout, redrawn every other tick so the arrow
        // swings with the player's head and the distance counts down as they walk.
        // It runs only for players currently holding Tab, so idle screens stay clean.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { tickCompass(); }
        }, 20L, COMPASS_PERIOD_TICKS);
        plugin.getLogger().info("WAYPOINTS_READY players=" + data.size());
    }

    public void stop() {
        started = false;
        if (task != null) task.cancel();
        task = null;
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof WaypointMenu.Menu)
                    player.closeInventory();
            } catch (RuntimeException ignored) { }
        }
        save();
        pending.clear();
        armed.clear();
        compassHold.clear();
        try {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Player player : Bukkit.getOnlinePlayers()) {
                try { player.setScoreboard(main); } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }
    }

    public String metrics() {
        int total = 0;
        for (List<Waypoint> list : data.values()) total += list.size();
        return "waypoints=" + total + "/" + data.size();
    }

    int maxManual() { return Math.max(1, Math.min(12, plugin.getConfig().getInt("waypoints.max-manual", MANUAL_SLOTS))); }
    int maxDeath() { return Math.max(1, Math.min(3, plugin.getConfig().getInt("waypoints.max-death", DEATH_SLOTS))); }
    boolean enabled() { return plugin.getConfig().getBoolean("waypoints.enabled", true); }

    // -- storage -------------------------------------------------------------
    /** Public for server-side features and fixture probes; identity stays server-side. */
    public List<Waypoint> mine(UUID id) {
        List<Waypoint> list = data.get(id);
        if (list == null) { list = new ArrayList<Waypoint>(); data.put(id, list); }
        return list;
    }

    public List<Waypoint> visible(UUID id) { return new ArrayList<Waypoint>(mine(id)); }

    public Waypoint bySlot(UUID id, int slot) {
        for (Waypoint waypoint : mine(id)) if (waypoint.slot == slot) return waypoint;
        return null;
    }

    public Integer activeSlot(UUID id) { return active.get(id); }

    public boolean setActive(UUID id, int slot) {
        if (bySlot(id, slot) == null) return false;
        active.put(id, slot);
        return true;
    }

    private int lowestFree(UUID id, int[] pool, int count) {
        List<Waypoint> list = mine(id);
        outer:
        for (int i = 0; i < count && i < pool.length; i++) {
            for (Waypoint waypoint : list) if (waypoint.slot == pool[i]) continue outer;
            return pool[i];
        }
        return -1;
    }

    private static int[] manualPool(int count) {
        int[] pool = new int[count];
        for (int i = 0; i < count; i++) pool[i] = i;
        return pool;
    }

    /** Returns null when the manual cap is reached. */
    public Waypoint create(UUID id, String name, Location at, int color) {
        if (!enabled()) return null;
        int slot = lowestFree(id, manualPool(maxManual()), maxManual());
        if (slot < 0) return null;
        Waypoint waypoint = new Waypoint(slot, Waypoint.sanitizeName(name), at.getBlockX(), at.getBlockY(), at.getBlockZ(),
            at.getWorld().getName(), color, false, System.currentTimeMillis());
        if (waypoint.coordHolder() == null) return null;
        if (waypoint.name.length() > MAX_NAME) waypoint.name = waypoint.name.substring(0, MAX_NAME);
        mine(id).add(waypoint);
        if (!active.containsKey(id)) active.put(id, slot);
        save();
        return waypoint;
    }

    public boolean recolor(UUID id, int slot) {
        Waypoint waypoint = bySlot(id, slot);
        if (waypoint == null) return false;
        waypoint.color = (waypoint.color + 1) & 15;
        save();
        return true;
    }

    public boolean delete(UUID id, int slot) {
        List<Waypoint> list = mine(id);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).slot == slot) {
                list.remove(i);
                if (Integer.valueOf(slot).equals(active.get(id))) active.remove(id);
                save();
                return true;
            }
        }
        return false;
    }

    public void recordDeath(Player player) {
        if (!enabled()) return;
        Location at = player.getLocation();
        UUID id = player.getUniqueId();
        List<Waypoint> list = mine(id);
        List<Waypoint> deaths = new ArrayList<Waypoint>();
        for (Waypoint waypoint : list) if (waypoint.death) deaths.add(waypoint);
        while (deaths.size() >= maxDeath()) {
            Waypoint oldest = deaths.get(0);
            for (Waypoint candidate : deaths) if (candidate.createdAt < oldest.createdAt) oldest = candidate;
            deaths.remove(oldest);
            delete(id, oldest.slot);
        }
        int slot = lowestFree(id, DEATH_POOL, DEATH_POOL.length);
        if (slot < 0) return;
        Waypoint waypoint = new Waypoint(slot, "Deathpoint", at.getBlockX(), at.getBlockY(), at.getBlockZ(),
            at.getWorld().getName(), 12, true, System.currentTimeMillis());
        if (waypoint.coordHolder() == null) return;
        list.add(waypoint);
        active.put(id, slot);
        save();
        player.sendMessage(ChatColor.RED + "\u2620 Waypoint created at " + waypoint.x + ", " + waypoint.y + ", " + waypoint.z
            + " \u2014 press M to manage waypoints.");
    }

    private void load() {
        data.clear();
        active.clear();
        if (!file.isFile()) return;
        org.bukkit.configuration.file.YamlConfiguration yaml;
        try {
            yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("WAYPOINTS_LOAD_FAILED " + e.getMessage());
            return;
        }
        // NOTE: nested YAML sections are MemorySection, not Map: read them with
        // getConfigurationSection/getMapList, never instanceof Map.
        org.bukkit.configuration.ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); }
            catch (IllegalArgumentException e) { continue; }
            org.bukkit.configuration.ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) continue;
            for (Map<?, ?> row : section.getMapList("waypoints")) {
                Waypoint waypoint = Waypoint.deserialize(row);
                if (waypoint != null && bySlotLoaded(id, waypoint.slot) == null) mine(id).add(waypoint);
            }
            if (section.isInt("active")) {
                int slot = section.getInt("active");
                if (bySlot(id, slot) != null) active.put(id, slot);
            }
        }
    }

    private Waypoint bySlotLoaded(UUID id, int slot) {
        List<Waypoint> list = data.get(id);
        if (list == null) return null;
        for (Waypoint waypoint : list) if (waypoint.slot == slot) return waypoint;
        return null;
    }

    private void save() {
        Map<String, Object> players = new LinkedHashMap<String, Object>();
        for (Map.Entry<UUID, List<Waypoint>> entry : data.entrySet()) {
            if (entry.getValue().isEmpty() && !active.containsKey(entry.getKey())) continue;
            Map<String, Object> section = new LinkedHashMap<String, Object>();
            List<Object> rows = new ArrayList<Object>();
            for (Waypoint waypoint : entry.getValue()) rows.add(waypoint.serialize());
            section.put("waypoints", rows);
            if (active.containsKey(entry.getKey())) section.put("active", active.get(entry.getKey()));
            players.put(entry.getKey().toString(), section);
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("players", players);
        try { yaml.save(file); } catch (java.io.IOException e) { plugin.getLogger().warning("WAYPOINTS_SAVE_FAILED " + e.getMessage()); }
    }

    // -- scoreboard sync (hidden objective, vanilla packets only) -------------
    /** Public for fixtures; the board is per-player vanilla state. */
    public Scoreboard buildBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        List<Waypoint> same = new ArrayList<Waypoint>();
        String here = player.getWorld().getName();
        for (Waypoint waypoint : mine(player.getUniqueId())) {
            if (waypoint.coordHolder() != null && here.equals(waypoint.world)) same.add(waypoint);
        }
        Objective objective = board.registerNewObjective(Waypoint.OBJECTIVE, "dummy");
        objective.setDisplayName(Waypoint.DISPLAY_PREFIX + same.size());
        // No display slot: invisible, but every score still syncs to this player.
        for (Waypoint waypoint : same) {
            objective.getScore(waypoint.coordHolder()).setScore(waypoint.y);
            objective.getScore(waypoint.nameHolder()).setScore(0);
        }
        return board;
    }

    void sync(Player player) {
        if (!started) return;
        try { player.setScoreboard(buildBoard(player)); }
        catch (RuntimeException e) { plugin.getLogger().warning("WAYPOINTS_SYNC_FAILED " + player.getName()); }
        // Board rebuilds drop every foreign objective: re-push dynamic lights.
        try { plugin.dynamicLights().pushTo(player); } catch (RuntimeException ignored) { }
    }

    // -- compass ---------------------------------------------------------------
    // The browser opens the readout when Tab goes down, heartbeats while it stays
    // down, and closes it on release. Between those edges the server redraws the
    // line continuously, so the arrow tracks the player's facing in real time and
    // the vanilla overlay never reaches its fade-out.
    public static String compassLine(String name, int color, double dirX, double dirZ, double toX, double toZ, int distance, int dy) {
        return Waypoint.CHAT[color & 15] + Waypoint.arrow(dirX, dirZ, toX, toZ) + " " + name
            + ChatColor.GRAY + " \u00b7 " + ChatColor.YELLOW + Waypoint.formatDistance(distance) + Waypoint.verticalTag(dy);
    }

    void sendCompass(Player player) {
        if (!started || !enabled()) return;
        try {
            if (!plugin.authenticated(player) || player.isDead()) return;
            Integer slot = active.get(player.getUniqueId());
            if (slot == null) return;
            Waypoint waypoint = bySlot(player.getUniqueId(), slot);
            if (waypoint == null || !player.getWorld().getName().equals(waypoint.world)) return;
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector look = eye.getDirection();
            double dx = (waypoint.x + 0.5) - eye.getX(), dz = (waypoint.z + 0.5) - eye.getZ();
            int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz + Math.pow(waypoint.y - eye.getY(), 2)));
            String line = compassLine(waypoint.name, waypoint.color, look.getX(), look.getZ(), dx, dz, distance, waypoint.y - eye.getBlockY());
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(line));
        } catch (RuntimeException ignored) { }
    }

    /** Tab went down, or is still down: refresh the hold and draw immediately. */
    void holdCompass(Player player) {
        if (!started || !enabled()) return;
        compassHold.put(player.getUniqueId(), Long.valueOf(System.currentTimeMillis()));
        sendCompass(player);
    }

    /** Tab came up: stop drawing and wipe the line the player is still looking at. */
    void releaseCompass(Player player) {
        compassHold.remove(player.getUniqueId());
        clearCompass(player);
    }

    /** Redraws every held compass. Holds that stop heartbeating are dropped and cleared. */
    void tickCompass() {
        if (compassHold.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Long>> it = compassHold.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) { it.remove(); continue; }
            if (now - entry.getValue().longValue() > COMPASS_HOLD_EXPIRY_MS) {
                it.remove();
                clearCompass(player);
                continue;
            }
            sendCompass(player);
        }
    }

    void clearCompass(Player player) {
        if (!started) return;
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        } catch (RuntimeException ignored) { }
    }

    // -- events ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH)
    public void menuClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        WaypointMenu.Menu menu = WaypointMenu.menuOf(event);
        if (menu == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getClickedInventory() == null || event.getRawSlot() < 0) return;
        Player player = (Player) event.getWhoClicked();
        if (!plugin.authenticated(player)) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot >= WaypointMenu.SIZE) return;
        WaypointMenu.click(this, menu, player, slot, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void menuDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (WaypointMenu.menuDragged(event)) event.setCancelled(true);
    }

    @EventHandler public void join(PlayerJoinEvent event) { sync(event.getPlayer()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { sync(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() { sync(event.getPlayer()); }
        }, 20L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        armed.remove(event.getPlayer().getUniqueId());
        compassHold.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(PlayerDeathEvent event) { recordDeath(event.getEntity()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void naming(AsyncPlayerChatEvent event) {
        PendingCreate pendingCreate = pending.get(event.getPlayer().getUniqueId());
        if (pendingCreate == null) return;
        event.setCancelled(true);
        final Player player = event.getPlayer();
        final String text = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() { completeNaming(player, text); }
        });
    }

    /** Finish a chat naming (public so fixtures can drive it synchronously). */
    public void completeNaming(Player player, String text) {
        PendingCreate create = pending.remove(player.getUniqueId());
        if (create == null || !plugin.authenticated(player)) return;
        if (text.trim().equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "Waypoint naming cancelled.");
            WaypointMenu.open(Waypoints.this, player);
            return;
        }
        Location at = new Location(Bukkit.getWorld(create.world), create.x, create.y, create.z);
        if (at.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "That world is gone; waypoint not created.");
            return;
        }
        Waypoint waypoint = create(player.getUniqueId(), text, at, nextColor(player.getUniqueId()));
        if (waypoint == null) {
            player.sendMessage(ChatColor.RED + "Waypoint limit reached (" + maxManual() + "). Delete one first (Shift+Left-click).");
            return;
        }
        sync(player);
        player.sendMessage(ChatColor.GREEN + "Waypoint '" + waypoint.name + "' created. Press M to manage.");
        WaypointMenu.open(Waypoints.this, player);
    }

    private int nextColor(UUID id) {
        return mine(id).size() % 16;
    }

    boolean armDelete(UUID id, int slot) {
        long now = System.currentTimeMillis();
        DeleteArm arm = armed.get(id);
        if (arm != null && arm.slot == slot && now < arm.expiresAt) {
            armed.remove(id);
            return true;
        }
        armed.put(id, new DeleteArm(slot, now + 10000L));
        return false;
    }

    public void beginCreate(Player player) {
        Location eye = player.getLocation();
        pending.put(player.getUniqueId(), new PendingCreate(
            eye.getWorld().getName(), eye.getBlockX(), eye.getBlockY(), eye.getBlockZ()));
        player.closeInventory();
        player.sendMessage(ChatColor.AQUA + "Stand where the waypoint belongs and type a name in chat "
            + ChatColor.GRAY + "(or 'cancel').");
    }

    // -- command -------------------------------------------------------------------
    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Only players manage waypoints."); return true; }
        Player player = (Player) sender;
        if (!enabled()) { player.sendMessage(ChatColor.RED + "Waypoints are disabled."); return true; }
        if (args.length == 0) {
            WaypointMenu.open(this, player);
            return true;
        }
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if ("help".equals(sub)) {
            player.sendMessage(ChatColor.AQUA + "Waypoints: /wp list, /wp add <name>, /wp track <name|#>, /wp delete <name|#>.");
            player.sendMessage(ChatColor.GRAY + "Or press M for the menu. Deaths are marked automatically.");
            return true;
        }
        if ("list".equals(sub)) {
            List<Waypoint> list = visible(player.getUniqueId());
            if (list.isEmpty()) { player.sendMessage(ChatColor.GRAY + "No waypoints yet. Press M and create one."); return true; }
            player.sendMessage(ChatColor.AQUA + "Waypoints (" + list.size() + "):");
            int n = 1;
            for (Waypoint waypoint : list) {
                int distance = -1;
                if (player.getWorld().getName().equals(waypoint.world)) {
                    Location eye = player.getEyeLocation();
                    double dx = waypoint.x - eye.getX(), dy = waypoint.y - eye.getY(), dz = waypoint.z - eye.getZ();
                    distance = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
                }
                player.sendMessage(ChatColor.GRAY + "" + (n++) + ". " + Waypoint.CHAT[waypoint.color] + waypoint.name
                    + ChatColor.GRAY + " @ " + waypoint.x + "," + waypoint.y + "," + waypoint.z
                    + (distance >= 0 ? " (" + Waypoint.formatDistance(distance) + ")" : " (" + waypoint.world + ")"));
            }
            return true;
        }
        if ("add".equals(sub)) {
            if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /wp add <name>"); return true; }
            String name = joinArgs(args, 1);
            Waypoint waypoint = create(player.getUniqueId(), name, player.getLocation(), nextColor(player.getUniqueId()));
            if (waypoint == null) { player.sendMessage(ChatColor.RED + "Waypoint limit reached (" + maxManual() + ")."); return true; }
            sync(player);
            player.sendMessage(ChatColor.GREEN + "Waypoint '" + waypoint.name + "' created.");
            return true;
        }
        if ("delete".equals(sub) || "remove".equals(sub)) {
            if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /wp delete <name|#>"); return true; }
            Waypoint waypoint = resolve(player, joinArgs(args, 1));
            if (waypoint == null) { player.sendMessage(ChatColor.RED + "No waypoint matches that."); return true; }
            delete(player.getUniqueId(), waypoint.slot);
            sync(player);
            player.sendMessage(ChatColor.GRAY + "Deleted '" + waypoint.name + "'.");
            return true;
        }
        if ("track".equals(sub) || "go".equals(sub)) {
            if (args.length < 2) { player.sendMessage(ChatColor.RED + "Usage: /wp track <name|#>"); return true; }
            Waypoint waypoint = resolve(player, joinArgs(args, 1));
            if (waypoint == null) { player.sendMessage(ChatColor.RED + "No waypoint matches that."); return true; }
            setActive(player.getUniqueId(), waypoint.slot);
            player.sendMessage(ChatColor.GREEN + "Tracking '" + waypoint.name + "'.");
            return true;
        }
        if ("compass".equals(sub)) {
            holdCompass(player);
            return true;
        }
        if ("compassoff".equals(sub)) {
            releaseCompass(player);
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /wp [list|add|delete|track|help]");
        return true;
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) out.append(' ');
            out.append(args[i]);
        }
        return out.toString();
    }

    private Waypoint resolve(Player player, String query) {
        String clean = query.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            int n = Integer.parseInt(clean);
            List<Waypoint> list = visible(player.getUniqueId());
            if (n >= 1 && n <= list.size()) return list.get(n - 1);
            return null;
        } catch (NumberFormatException e) { }
        Waypoint prefix = null;
        for (Waypoint waypoint : visible(player.getUniqueId())) {
            String name = waypoint.name.toLowerCase(java.util.Locale.ROOT);
            if (name.equals(clean)) return waypoint;
            if (prefix == null && name.startsWith(clean)) prefix = waypoint;
        }
        return prefix;
    }
}
