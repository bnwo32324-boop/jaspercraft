package chat.jaspr.nether;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Creative-only /tpd <id>, independent of the player's current dimension and operator status.
 * Register ONE instance as the tpd executor, tab completer AND event listener after onEnable.
 * The owning plugin's command declaration must have no permission gate. See tpd-integration.md.
 *
 * IDs are persisted by world name, never by Bukkit's load order or environment number. Only
 * the three named realm plugins may load their custom worlds; other targets must be loaded.
 * No blocks, inventories, world folders, permissions or realm progression are rewritten here.
 */
public final class DimensionTravel implements TabExecutor, Listener {
    private static final int SEARCH_RADIUS = 16;
    private static final int MAX_DIMENSIONS = 4096;
    private static final int WORLD_LIMIT = 29999900;
    private static final String MAPPING_FILE = "tpd-dimensions.properties";
    private static final Realm[] REALMS = {
        new Realm(4, "jaspr_atlas", "JasprAtlas", "chat.jaspr.atlas.AtlasPlugin", "ensureAtlas", "chat.jaspr.atlas.AtlasGenerator", "generator", "chat.jaspr.atlas.AtlasGenerator"),
        new Realm(5, "jaspr_ruins", "JasprRuins", "chat.jaspr.ruins.RuinsPlugin", "ensureRuins", "chat.jaspr.ruins.RuinsGenerator", "plans", "chat.jaspr.ruins.Plans"),
        new Realm(6, "jaspr_levels", "JasprBackrooms", "chat.jaspr.backrooms.BackroomsPlugin", "ensureWorld", "chat.jaspr.backrooms.BackroomsGenerator", "generator", "chat.jaspr.backrooms.BackroomsGenerator")
    };

    private final Plugin owner;
    private final Path mappingFile;
    private final TreeMap<Integer, String> dimensions = new TreeMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private String mappingError;

    /** Parent integration: declare tpd in plugin.yml, then call once from onEnable(). */
    public static DimensionTravel register(JavaPlugin owner) {
        PluginCommand command = owner.getCommand("tpd");
        if (command == null) throw new IllegalStateException("Declare the tpd command in JasprNether's plugin.yml first");
        DimensionTravel travel = new DimensionTravel(owner);
        command.setPermission(null); // Creative mode is the only command gate, including namespaced use.
        command.setExecutor(travel);
        command.setTabCompleter(travel);
        owner.getServer().getPluginManager().registerEvents(travel, owner);
        return travel;
    }

    public DimensionTravel(Plugin owner) {
        if (owner == null) throw new IllegalArgumentException("owner");
        this.owner = owner;
        mappingFile = owner.getDataFolder().toPath().resolve(MAPPING_FILE);
        dimensions.put(1, "world");
        dimensions.put(2, owner.getConfig().getString("world", "world_nether"));
        dimensions.put(3, "world_the_end");
        for (Realm realm : REALMS) dimensions.put(realm.id, realm.world);
        try {
            validateNames(dimensions);
            if (Files.exists(mappingFile)) readMapping();
            refreshMapping(!Files.exists(mappingFile));
        } catch (IOException | RuntimeException e) {
            mappingError = "The saved dimension IDs are unavailable. Ask an administrator to check the server log.";
            logFailure("mapping", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!creative(sender)) {
            sender.sendMessage(ChatColor.RED + "Only Creative-mode players can use /tpd.");
            return true;
        }
        Player player = (Player) sender;
        if (!owner.getServer().isPrimaryThread()) {
            player.sendMessage(ChatColor.RED + "Dimension travel must run on the server thread.");
            return true;
        }
        if (!mappingReady(player)) return true;
        if (args == null || args.length != 1) { usage(player); return true; }
        Integer id = parseId(args[0]);
        if (id == null || !dimensions.containsKey(id)) {
            player.sendMessage(ChatColor.RED + "Unknown dimension ID. Use /tpd for the list.");
            return true;
        }
        if (!player.isOnline() || player.isDead()) {
            player.sendMessage(ChatColor.RED + "You must be online and alive to travel.");
            return true;
        }
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "A dimension trip is already in progress.");
            return true;
        }
        try {
            World target = resolve(id, dimensions.get(id));
            Location arrival = safeArrival(target);
            if (arrival == null) throw new TravelFailure("No safe, dry arrival exists near this world's spawn. Travel was refused.");
            // World loading/population can dispatch events: check again after those callbacks.
            if (!creative(player) || !player.isOnline() || player.isDead())
                throw new TravelFailure("You must still be online, alive and in Creative mode to travel.");
            if (owner.getServer().getWorld(target.getName()) != target)
                throw new TravelFailure("That dimension was unloaded before travel. Try again.");
            Location from = player.getLocation();
            Pending trip = new Pending(target);
            boolean moved;
            pending.put(player.getUniqueId(), trip);
            try { moved = player.teleport(arrival, PlayerTeleportEvent.TeleportCause.COMMAND); }
            finally { pending.remove(player.getUniqueId()); }
            if (!moved) throw new TravelFailure(trip.refusal == null ? "Teleport was cancelled or refused; you were not moved." : trip.refusal);
            if (player.getWorld() != target || !safeFeet(player.getLocation()))
                throw new TravelFailure("Another teleport handler changed the arrival. Check your location before continuing.");
            player.setFallDistance(0f);
            player.setVelocity(new Vector(0, 0, 0));
            player.sendMessage(ChatColor.AQUA + "Travelled to " + title(id, target.getName()) + " (dimension " + id + ").");
            owner.getLogger().info("TPD_TRAVEL player=" + clean(player.getName()) + " id=" + id + " from="
                + (from.getWorld() == null ? "-" : clean(from.getWorld().getName())) + " world=" + clean(target.getName()));
        } catch (TravelFailure e) {
            player.sendMessage(ChatColor.RED + e.getMessage());
            owner.getLogger().warning("TPD_REFUSED player=" + clean(player.getName()) + " id=" + id + " reason=" + clean(e.getMessage()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            player.sendMessage(ChatColor.RED + "Dimension travel failed safely. Check the server log before retrying.");
            logFailure("id=" + id, e);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!creative(sender) || args == null || args.length != 1 || !owner.getServer().isPrimaryThread())
            return Collections.emptyList();
        if (!mappingReady(null)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (int id : dimensions.keySet()) if (String.valueOf(id).startsWith(args[0])) result.add(String.valueOf(id));
        return result;
    }

    /** Checks only this command's pending teleport; never un-cancels another plugin's event. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardArrival(PlayerTeleportEvent event) {
        Pending trip = pending.get(event.getPlayer().getUniqueId());
        if (trip == null) return;
        try {
            if (!creative(event.getPlayer()) || event.getPlayer().isDead()) trip.refusal = "Creative mode is required throughout dimension travel.";
            else if (event.getTo() == null || event.getTo().getWorld() != trip.world
                    || owner.getServer().getWorld(trip.world.getName()) != trip.world || !safeFeet(event.getTo()))
                trip.refusal = "Teleport was redirected to an unsafe or unavailable arrival; travel was cancelled.";
        } catch (RuntimeException | LinkageError e) {
            trip.refusal = "Arrival safety could not be verified; travel was cancelled.";
            logFailure("arrival-guard", e);
        }
        if (trip.refusal != null) event.setCancelled(true);
    }

    private static boolean creative(CommandSender sender) {
        return sender instanceof Player && ((Player) sender).getGameMode() == GameMode.CREATIVE;
    }

    private boolean mappingReady(CommandSender sender) {
        if (mappingError == null) {
            try { refreshMapping(false); }
            catch (IOException | RuntimeException e) {
                // Keep the last committed mapping and refuse this request, rather than allocating ephemeral IDs.
                logFailure("mapping-save", e);
                if (sender != null) sender.sendMessage(ChatColor.RED + "New dimension IDs could not be saved. Try again after the server storage is fixed.");
                return false;
            }
        }
        if (mappingError != null) {
            if (sender != null) sender.sendMessage(ChatColor.RED + mappingError);
            return false;
        }
        return true;
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /tpd <dimension ID> (Creative mode only)");
        for (Map.Entry<Integer, String> entry : dimensions.entrySet()) {
            String state = owner.getServer().getWorld(entry.getValue()) != null ? "loaded" : entry.getKey() >= 4 && entry.getKey() <= 6 ? "loads on travel" : "unloaded";
            player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + " = " + title(entry.getKey(), entry.getValue()) + " [" + entry.getValue() + "; " + state + "]");
        }
    }

    private static String title(int id, String name) {
        switch (id) {
            case 1: return "Overworld";
            case 2: return "Nether";
            case 3: return "End";
            case 4: return "Atlas";
            case 5: return "Drownhollow";
            case 6: return "Backrooms";
            default: return name;
        }
    }

    private World resolve(int id, String name) throws ReflectiveOperationException, TravelFailure {
        World world;
        if (id >= 4 && id <= 6) {
            Realm realm = REALMS[id - 4];
            Plugin plugin = owner.getServer().getPluginManager().getPlugin(realm.plugin);
            if (plugin == null || !plugin.isEnabled() || !plugin.getConfig().getBoolean("enabled", true))
                throw new TravelFailure(title(id, name) + " is unavailable: its world plugin is disabled or missing.");
            if (!plugin.getClass().getName().equals(realm.pluginClass))
                throw new TravelFailure(title(id, name) + " has an unsupported loader; travel was refused.");
            // Ensure methods can otherwise create vanilla terrain with a null generator after a failed enable.
            // Ruins exposes plans(), not generator(): plans() dereferences its generator and
            // therefore fails before ensureRuins() if enable never initialized the generator.
            Object ready = invokeKnown(plugin, realm.readyMethod);
            if (ready == null || !ready.getClass().getName().equals(realm.readyClass))
                throw new TravelFailure(title(id, name) + " is not ready with its own generator.");
            Object ensured = invokeKnown(plugin, realm.ensure);
            if (!(ensured instanceof World)) throw new TravelFailure(title(id, name) + " could not be loaded by its own plugin.");
            world = (World) ensured;
            if (world.getGenerator() == null || !world.getGenerator().getClass().getName().equals(realm.generatorClass))
                throw new TravelFailure(title(id, name) + " is using the wrong generator; travel was refused.");
        } else world = owner.getServer().getWorld(name);
        if (world == null || !name.equals(world.getName()) || owner.getServer().getWorld(name) != world)
            throw new TravelFailure("Dimension " + id + " (" + name + ") is not loaded. Its owning loader must load it first.");
        World.Environment expected = id == 1 || id >= 4 && id <= 6 ? World.Environment.NORMAL
            : id == 2 ? World.Environment.NETHER : id == 3 ? World.Environment.THE_END : null;
        if (expected != null && world.getEnvironment() != expected)
            throw new TravelFailure("Dimension " + id + " has the wrong environment; travel was refused.");
        return world;
    }

    /** Public API if available; otherwise one exact declared, zero-argument method on an exact known class. */
    private static Object invokeKnown(Plugin plugin, String name) throws ReflectiveOperationException {
        Method method;
        try { method = plugin.getClass().getMethod(name); }
        catch (NoSuchMethodException e) { method = plugin.getClass().getDeclaredMethod(name); }
        if (Modifier.isStatic(method.getModifiers())) throw new NoSuchMethodException("Instance method required: " + name);
        if (!Modifier.isPublic(method.getModifiers())) method.setAccessible(true);
        return method.invoke(plugin);
    }

    private Location safeArrival(World world) throws TravelFailure {
        Location spawn = world.getSpawnLocation();
        if (spawn == null || spawn.getWorld() != world || !finite(spawn)
                || Math.abs(spawn.getX()) > WORLD_LIMIT || Math.abs(spawn.getZ()) > WORLD_LIMIT)
            throw new TravelFailure("This dimension has no valid spawn location.");
        int sx = spawn.getBlockX(), sz = spawn.getBlockZ();
        // Include a population halo: Ruins/Lost Cities and Nether structures decorate neighboring chunks.
        // At most 36 chunks, 1089 columns, and maxHeight-2 standing heights; no unbounded terrain search.
        for (int cx = ((sx - SEARCH_RADIUS - 1) >> 4) - 1; cx <= ((sx + SEARCH_RADIUS + 1) >> 4) + 1; cx++)
            for (int cz = ((sz - SEARCH_RADIUS - 1) >> 4) - 1; cz <= ((sz + SEARCH_RADIUS + 1) >> 4) + 1; cz++)
                if (!world.loadChunk(cx, cz, true)) throw new TravelFailure("Arrival chunks could not be loaded; travel was refused.");
        int top = Math.min(world.getMaxHeight() - 2, world.getEnvironment() == World.Environment.NETHER ? 126 : 254);
        int preferredY = Math.max(1, Math.min(top, spawn.getBlockY()));
        for (int radius = 0; radius <= SEARCH_RADIUS; radius++)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    Location result = new Location(world, sx + dx + 0.5, preferredY + 0.01, sz + dz + 0.5, spawn.getYaw(), spawn.getPitch());
                    if (!insideBorder(result)) continue;
                    for (int distance = 0; distance <= top; distance++) {
                        int above = preferredY + distance, below = preferredY - distance;
                        if (above <= top) { result.setY(above + 0.01); if (safeFeet(result)) return result; }
                        if (distance > 0 && below >= 1) { result.setY(below + 0.01); if (safeFeet(result)) return result; }
                    }
                }
        return null;
    }

    /** Full body fits over a full, dry floor, within the border, away from liquids/damage/portal blocks. */
    static boolean safeFeet(Location location) {
        if (location == null || location.getWorld() == null || !finite(location) || !insideBorder(location)) return false;
        World world = location.getWorld();
        int y = location.getBlockY();
        if (y < 1 || y > world.getMaxHeight() - 2 || location.getY() - y > 0.1
                || world.getEnvironment() == World.Environment.NETHER && y > 126) return false;
        int x0 = (int) Math.floor(location.getX() - 0.3), x1 = (int) Math.floor(location.getX() + 0.3);
        int z0 = (int) Math.floor(location.getZ() - 0.3), z1 = (int) Math.floor(location.getZ() + 0.3);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
            Material floor = world.getBlockAt(x, y - 1, z).getType();
            if (!floor.isSolid() || !floor.isOccluding() || hazardous(floor)
                    || world.getBlockAt(x, y, z).getType() != Material.AIR || world.getBlockAt(x, y + 1, z).getType() != Material.AIR) return false;
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
            for (int h = y - 1; h <= y + 1; h++) if (hazardous(world.getBlockAt(x, h, z).getType())) return false;
        }
        return true;
    }

    private static boolean hazardous(Material material) {
        switch (material) {
            case LAVA: case STATIONARY_LAVA: case WATER: case STATIONARY_WATER:
            case FIRE: case MAGMA: case CACTUS: case PORTAL: case ENDER_PORTAL: case END_GATEWAY:
                return true;
            default: return false;
        }
    }

    private static boolean insideBorder(Location location) {
        if (!finite(location) || Math.abs(location.getX()) > WORLD_LIMIT || Math.abs(location.getZ()) > WORLD_LIMIT) return false;
        WorldBorder border = location.getWorld().getWorldBorder();
        if (border == null) return false;
        for (double dx : new double[] {-0.3, 0.3}) for (double dz : new double[] {-0.3, 0.3})
            if (!border.isInside(location.clone().add(dx, 0, dz))) return false;
        return true;
    }

    private static boolean finite(Location location) {
        return Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
            && Float.isFinite(location.getYaw()) && Float.isFinite(location.getPitch());
    }

    static Integer parseId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}")) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException e) { return null; }
    }

    private void readMapping() throws IOException {
        if (!Files.isRegularFile(mappingFile) || Files.size(mappingFile) > 1048576L) throw new IOException("Invalid dimension mapping file");
        Properties saved = new UniqueProperties();
        try (Reader reader = Files.newBufferedReader(mappingFile, StandardCharsets.UTF_8)) { saved.load(reader); }
        if (!"1".equals(saved.remove("format")) || saved.size() < 6 || saved.size() > MAX_DIMENSIONS)
            throw new IOException("Invalid dimension mapping format");
        TreeMap<Integer, String> restored = new TreeMap<>();
        for (String key : saved.stringPropertyNames()) {
            Integer id = parseId(key);
            if (id == null) throw new IOException("Invalid saved dimension ID");
            restored.put(id, saved.getProperty(key));
        }
        for (int id = 1; id <= 6; id++) if (!dimensions.get(id).equals(restored.get(id)))
            throw new IOException("Reserved dimension " + id + " changed; keep the original mapping");
        validateNames(restored);
        dimensions.clear();
        dimensions.putAll(restored);
    }

    private void refreshMapping(boolean force) throws IOException {
        TreeMap<Integer, String> next = new TreeMap<>(dimensions);
        Set<String> names = new HashSet<>(next.values());
        List<String> loaded = new ArrayList<>();
        for (World world : owner.getServer().getWorlds()) if (world != null && !names.contains(world.getName())) loaded.add(world.getName());
        Collections.sort(loaded);
        for (String name : loaded) {
            if (!names.add(name)) continue;
            if (next.size() >= MAX_DIMENSIONS || next.lastKey() == Integer.MAX_VALUE) throw new IOException("Dimension ID capacity exhausted");
            next.put(next.lastKey() + 1, name);
        }
        if (!force && next.equals(dimensions)) return;
        validateNames(next);
        Properties saved = new Properties();
        saved.setProperty("format", "1");
        for (Map.Entry<Integer, String> entry : next.entrySet()) saved.setProperty(String.valueOf(entry.getKey()), entry.getValue());
        Files.createDirectories(mappingFile.getParent());
        Path temporary = Files.createTempFile(mappingFile.getParent(), "tpd-dimensions-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { saved.store(writer, "Stable /tpd world IDs; do not renumber or reuse IDs"); }
            // On failure the committed file and in-memory IDs stay intact. Never serve unsaved IDs.
            Files.move(temporary, mappingFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            dimensions.clear();
            dimensions.putAll(next);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void validateNames(Map<Integer, String> mapping) throws IOException {
        Set<String> names = new HashSet<>();
        for (String name : mapping.values()) {
            if (name == null || name.isEmpty() || name.length() > 256 || !name.equals(name.trim()) || !names.add(name))
                throw new IOException("Invalid or duplicate dimension name");
            for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i))) throw new IOException("Invalid dimension name");
        }
    }

    private void logFailure(String stage, Throwable error) {
        Throwable cause = error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
        owner.getLogger().warning("TPD_FAILED stage=" + stage + " error=" + cause.getClass().getSimpleName() + " reason=" + clean(cause.getMessage()));
    }

    private static String clean(String text) {
        if (text == null) return "-";
        String value = text.replace('\r', ' ').replace('\n', ' ');
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    private static final class UniqueProperties extends Properties {
        private static final long serialVersionUID = 1L;
        @Override public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) throw new IllegalArgumentException("Duplicate dimension key: " + key);
            return super.put(key, value);
        }
    }

    private static final class Pending {
        final World world;
        String refusal;
        Pending(World world) { this.world = world; }
    }

    private static final class Realm {
        final int id;
        final String world, plugin, pluginClass, ensure, generatorClass, readyMethod, readyClass;
        Realm(int id, String world, String plugin, String pluginClass, String ensure, String generatorClass, String readyMethod, String readyClass) {
            this.id = id; this.world = world; this.plugin = plugin; this.pluginClass = pluginClass;
            this.ensure = ensure; this.generatorClass = generatorClass;
            this.readyMethod = readyMethod; this.readyClass = readyClass;
        }
    }

    private static final class TravelFailure extends Exception {
        private static final long serialVersionUID = 1L;
        TravelFailure(String message) { super(message); }
    }
}
