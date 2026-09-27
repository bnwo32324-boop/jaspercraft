package chat.jaspr.ruins;

import chat.jaspr.lostcities.CityApi;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprRuins: the Ancient Ruins dimension. A world of moss-grown land where the Lost Cities stand as weathered ruins
 * (built by the JasprLostCities plugin, restyled here) beside original old cities and wilderness ruins. Reached through
 * portals framed in mossy cobblestone. Logs RUINS_READY, RUINS_PORTAL_*, RUINS_TRAVEL and RUINS_METRICS.
 */
public final class RuinsPlugin extends JavaPlugin implements Listener {
    static final String WORLD = "jaspr_ruins";
    static final long SALT = 0x5275696E73L;   // "Ruins"

    private volatile World ruins;
    private World main;
    private RuinsGenerator generator;
    private Portals portals;
    private final Map<UUID, String> place = new HashMap<>();
    private final Set<String> told = new HashSet<>();
    private File toldFile;
    private long failuresLogged;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) { getLogger().info("RUINS_DISABLED by config"); return; }
        main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (main == null) { getLogger().severe("RUINS_REFUSED no overworld loaded"); return; }
        // A new world takes the overworld's seed (salted); an existing one keeps its own, even after an overworld reset,
        // so this plan and the Lost Cities' (which reads the world's seed) always agree.
        long seed = savedSeed(new File(Bukkit.getWorldContainer(), WORLD + File.separator + "level.dat"), main.getSeed() ^ SALT);
        // Registered before the world exists: the Lost Cities attach to it on WorldInitEvent.
        CityApi.registerWorld(WORLD, "The Ruins of %s", new Weathering(seed));
        generator = new RuinsGenerator(seed, (x, z, w, d) -> { World r = ruins; return r != null && CityApi.reserved(r, x, z, w, d); }, this::generationFailed);
        portals = new Portals(this);
        portals.load();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(portals, this);
        toldFile = new File(getDataFolder(), "told.txt");
        loadTold();
        World world = Bukkit.getWorld(WORLD);
        if (world == null) {
            world = new WorldCreator(WORLD).environment(World.Environment.NORMAL).seed(seed).generator(generator).generateStructures(false).createWorld();
        }
        if (world == null || !(world.getGenerator() instanceof RuinsGenerator)) {
            getLogger().severe("RUINS_REFUSED world=" + WORLD + " is not using the ruins generator");
            return;
        }
        ruins = world;
        world.setKeepSpawnInMemory(false);
        world.setDifficulty(main.getDifficulty());
        if (world.getSeed() != seed) getLogger().warning("RUINS_SEED_MISMATCH the saved world has another seed; ruins follow the saved generator plan");
        Bukkit.getScheduler().runTaskTimer(this, portals::tick, 20L, 4L);
        Bukkit.getScheduler().runTaskTimer(this, this::places, 60L, 40L);
        getLogger().info("RUINS_READY world=" + WORLD + " lostCities=registered portals=" + portals.count()
            + " cityGrid=" + Plans.CITY_GRID + " siteGrid=" + Plans.SITE_GRID);
    }

    @Override
    public void onDisable() {
        if (generator == null) return;
        RuinsPopulator p = generator.populator();
        getLogger().info("RUINS_METRICS chunks=" + generator.chunks + " failures=" + generator.failures + " chests=" + p.chests + " spawners=" + p.spawners
            + " trees=" + p.trees + " weatheredChunks=" + Weathering.chunks + (portals == null ? "" : " portalsLit=" + portals.lit + " portalsBuilt=" + portals.built
            + " travels=" + portals.travels + " portalsClosed=" + portals.closed));
    }

    private long savedSeed(File level, long fallback) {
        if (!level.isFile()) return fallback;
        try (java.io.InputStream in = new java.io.FileInputStream(level)) {
            return net.minecraft.server.v1_12_R1.NBTCompressedStreamTools.a(in).getCompound("Data").getLong("RandomSeed");
        } catch (IOException | RuntimeException e) {
            getLogger().warning("RUINS_SEED_UNREADABLE " + e.getClass().getSimpleName() + "; using the overworld's");
            return fallback;
        }
    }

    void generationFailed(String where, Throwable e) {
        if (++failuresLogged > 20) return;
        StackTraceElement at = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
        getLogger().severe("RUINS_CHUNK_FAILED " + where + " " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replaceAll("[\\r\\n]", " ")
            + (at == null ? "" : " at " + at.getClassName().replace("chat.jaspr.ruins.", "") + ":" + at.getLineNumber()));
    }

    // ------------------------------------------------------------------ worlds

    boolean isRuins(World w) { return w != null && WORLD.equals(w.getName()); }
    boolean portalWorld(World w) { return w != null && (w.equals(main) || isRuins(w)); }
    World otherWorld(World w) { return isRuins(w) ? main : w != null && w.equals(main) ? ruins : null; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void worldInit(WorldInitEvent e) {
        // Fired before CraftServer preloads spawn chunks (WorldCreator has no such flag in 1.12).
        if (isRuins(e.getWorld())) { e.getWorld().setKeepSpawnInMemory(false); ruins = e.getWorld(); }
    }

    // ------------------------------------------------------------------ names on entering

    private void places() {
        World r = ruins;
        if (r == null) return;
        for (Player p : r.getPlayers()) {
            Location l = p.getLocation();
            String now = null, title = null;
            Plans.City city = generator.plans.cityNear(l.getBlockX(), l.getBlockZ());
            if (city != null && city.outside(l.getBlockX(), l.getBlockZ()) <= 0) { now = "city:" + city.name; title = city.name; }
            else {
                Plans.Site site = generator.plans.siteAt(l.getBlockX(), l.getBlockZ());
                if (site != null) now = "site:" + site.name;
            }
            String before = now == null ? place.remove(p.getUniqueId()) : place.put(p.getUniqueId(), now);
            if (now == null || now.equals(before)) continue;
            if (title != null) p.sendTitle("", ChatColor.DARK_GREEN + "The Old City of " + title, 10, 50, 20);
            else p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + now.substring(5)));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { place.remove(e.getPlayer().getUniqueId()); }

    // ------------------------------------------------------------------ a one-time hint

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("hint-on-join", true)) return;
        Player p = e.getPlayer();
        if (!told.add(p.getUniqueId().toString())) return;
        saveTold();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(ChatColor.DARK_GREEN + "New: the Ancient Ruins. " + ChatColor.GRAY + "Build a nether-portal frame out of "
                + ChatColor.GREEN + "mossy cobblestone" + ChatColor.GRAY + " (at least 4 wide, 5 tall) and light it with flint and steel.");
        }, 200L);
    }

    private void loadTold() {
        try { if (toldFile.isFile()) told.addAll(Files.readAllLines(toldFile.toPath(), StandardCharsets.UTF_8)); }
        catch (IOException e) { getLogger().warning("RUINS_TOLD_UNREADABLE " + e.getClass().getSimpleName()); }
    }

    private void saveTold() {
        try { getDataFolder().mkdirs(); Files.write(toldFile.toPath(), told, StandardCharsets.UTF_8); }
        catch (IOException e) { getLogger().warning("RUINS_TOLD_UNSAVED " + e.getClass().getSimpleName()); }
    }

    // ------------------------------------------------------------------ owner commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            RuinsPopulator p = generator == null ? null : generator.populator();
            sender.sendMessage(ChatColor.DARK_GREEN + "Ancient Ruins: " + ChatColor.GRAY + (ruins == null ? "not loaded" : ruins.getPlayers().size() + " players")
                + ", " + (generator == null ? 0 : generator.chunks) + " chunks generated, " + (portals == null ? 0 : portals.count()) + " portals"
                + (p == null ? "" : ", " + p.chests + " chests, " + p.spawners + " spawners"));
            return true;
        }
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player player = (Player) sender;
        World r = ruins;
        if (r == null) { player.sendMessage(ChatColor.RED + "The Ancient Ruins are not loaded."); return true; }
        switch (sub) {
            case "tp": go(player, surface(r, player.getWorld().equals(r) ? 0 : player.getLocation().getBlockX(), player.getWorld().equals(r) ? 0 : player.getLocation().getBlockZ())); return true;
            case "back": go(player, surface(main, player.getLocation().getBlockX(), player.getLocation().getBlockZ())); return true;
            case "where": player.sendMessage(ChatColor.GRAY + where(player.getLocation())); return true;
            case "find": {
                String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "city";
                Location to = find(r, what, player.getWorld().equals(r) ? player.getLocation() : new Location(r, 0, 64, 0));
                if (to == null) player.sendMessage(ChatColor.RED + "Nothing like that nearby.");
                else go(player, to);
                return true;
            }
            default:
                player.sendMessage(ChatColor.GRAY + "/ruins [status|tp|back|where|find <city|lostcity|" + kinds() + ">]");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("status", "tp", "back", "where", "find");
        if (args.length == 2 && "find".equalsIgnoreCase(args[0])) {
            List<String> all = new java.util.ArrayList<>(Arrays.asList("city", "lostcity"));
            for (Plans.Kind k : Plans.Kind.values()) all.add(k.name().toLowerCase(Locale.ROOT));
            return all;
        }
        return Collections.emptyList();
    }

    private static String kinds() { return Arrays.stream(Plans.Kind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("|")); }

    private void go(Player p, Location to) {
        if (p.isInsideVehicle()) p.leaveVehicle();
        p.setFallDistance(0f);
        p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private static Location surface(World w, int x, int z) {
        settle(w, x, z, 0);
        return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 0.1, z + 0.5);
    }

    /**
     * Loads the chunks around (x, z) so that the chunks within {@code reach} of it are populated: the Lost Cities build
     * in their populator, which only runs once a chunk's +x/+z neighbours exist, so a surface read before that would
     * be the bare land under a future building.
     */
    static void settle(World w, int x, int z, int reach) {
        int cx = x >> 4, cz = z >> 4;
        for (int a = cx - reach; a <= cx + reach + 1; a++)
            for (int b = cz - reach; b <= cz + reach + 1; b++) w.loadChunk(a, b, true);
    }

    private String where(Location l) {
        if (!isRuins(l.getWorld())) return "Not in the Ancient Ruins.";
        int x = l.getBlockX(), z = l.getBlockZ();
        Plans.City c = generator.plans.cityNear(x, z);
        if (c != null) return "Old City of " + c.name + " (centre " + c.x + ", " + c.z + ", walls " + (2 * c.half + 1) + " wide)";
        Plans.Site s = generator.plans.siteAt(x, z);
        if (s != null) return s.name + " (" + s.kind.noun + " at " + s.x + ", " + s.z + ")";
        String lc = CityApi.cityName(l.getWorld().getSeed(), x >> 4, z >> 4);
        return lc != null ? "Ruins of " + lc + " (Lost Cities)" : "Wilderness";
    }

    /** Nearest old city, Lost City or site of a kind to a point, searched ring by ring over the plan (pure math). */
    private Location find(World r, String what, Location from) {
        int x0 = from.getBlockX(), z0 = from.getBlockZ();
        if (what.equals("lostcity")) {
            int cx0 = x0 >> 4, cz0 = z0 >> 4;
            for (int ring = 0; ring <= 160; ring += 4)
                for (int a = -ring; a <= ring; a += 4)
                    for (int b = -ring; b <= ring; b += 4) {
                        if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                        if (CityApi.cityRegion(r.getSeed(), cx0 + a, cz0 + b)) return surface(r, (cx0 + a) * 16 + 8, (cz0 + b) * 16 + 8);
                    }
            return null;
        }
        if (what.equals("city")) {
            int i0 = Math.floorDiv(x0, Plans.CITY_GRID), j0 = Math.floorDiv(z0, Plans.CITY_GRID);
            for (int ring = 0; ring <= 12; ring++)
                for (int a = -ring; a <= ring; a++)
                    for (int b = -ring; b <= ring; b++) {
                        if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                        Plans.City c = generator.plans.city(i0 + a, j0 + b);
                        if (c != null) return new Location(r, c.x + 0.5, c.ground + 1.1, c.z + 6.5);
                    }
            return null;
        }
        Plans.Kind kind;
        try { kind = Plans.Kind.valueOf(what.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        int i0 = Math.floorDiv(x0, Plans.SITE_GRID), j0 = Math.floorDiv(z0, Plans.SITE_GRID);
        for (int ring = 0; ring <= 40; ring++)
            for (int a = -ring; a <= ring; a++)
                for (int b = -ring; b <= ring; b++) {
                    if (Math.max(Math.abs(a), Math.abs(b)) != ring) continue;
                    Plans.Site s = generator.plans.site(i0 + a, j0 + b);
                    if (s != null && s.kind == kind) return surface(r, s.x, s.z - s.kind.radius - 2);
                }
        return null;
    }
}
