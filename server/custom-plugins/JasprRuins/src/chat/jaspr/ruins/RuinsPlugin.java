package chat.jaspr.ruins;

import chat.jaspr.lostcities.CityApi;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprRuins: Drownhollow, the drowned city of the Choir of the Drowned Star. A dimension packed with cyclopean ruins,
 * weathered Lost Cities and cult monuments under an endless night, full of Lovecraftian horrors; five Wardens hold the
 * Seals of the Great Door, behind which the Dreamer's Herald waits. Beat it and the Herald's hoard is yours.
 *
 * <p>The world lives on disk while nobody is in it: it loads when someone enters (portal, owner command, or logging in
 * where they left off) and is saved and fully unloaded a minute after its last player leaves; its tasks then do nothing.
 * Logs RUINS_READY, RUINS_WORLD_LOADED/UNLOADED, RUINS_REGENERATED, RUINS_PORTAL_*, RUINS_TRAVEL, RUINS_BOSS_*,
 * RUINS_WARDEN_SLAIN, RUINS_HERALD_SLAIN, RUINS_DOOR_* and RUINS_METRICS.
 */
public final class RuinsPlugin extends JavaPlugin implements Listener {
    static final String WORLD = "jaspr_ruins";
    static final long SALT = 0x5275696E73L;   // "Ruins"
    /** Bumped when the dimension is redesigned: an older saved world is retired (renamed, not deleted) and regenerated. */
    static final int EPOCH = 3;
    /**
     * The owner asked for Drownhollow at half the difficulty and half the spawns (2026-09-28): everything hostile deals
     * this share of its damage to players, horrors and Wardens have this share of their health, and the hazards and
     * spawns below are scaled the same way.
     */
    static final double EASE = 0.5;
    static final String EPOCH_FILE = "jaspr-ruins-epoch.txt", COMPASS = "Drowned Star Compass", COMPASS_MARK = "Relic of Drownhollow - compass",
        OLD_COMPASS_MARK = "Relic of Ul'Nhaar - compass";   // compasses made before the rename

    private volatile World ruins;
    private World main;
    private long seed;
    private RuinsGenerator generator;
    private Portals portals;
    private Horrors horrors;
    private Bosses bosses;
    private Trinkets trinkets;
    private Sky sky;
    private final Map<UUID, String> place = new HashMap<>();
    private final Set<String> told = new HashSet<>();
    private final Set<UUID> compassAimed = new HashSet<>();
    private File toldFile;
    private long failuresLogged, emptySince, loads, unloads;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) { getLogger().info("RUINS_DISABLED by config"); return; }
        main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (main == null) { getLogger().severe("RUINS_REFUSED no overworld loaded"); return; }
        portals = new Portals(this);
        portals.load();
        bosses = new Bosses(this);
        File folder = new File(Bukkit.getWorldContainer(), WORLD);
        retireOldWorld(folder);
        // A new world takes the overworld's seed (salted); an existing one keeps its own, so this plan and the Lost Cities'
        // (which reads the world's seed) always agree.
        seed = savedSeed(new File(folder, "level.dat"), main.getSeed() ^ SALT);
        // Registered before the world exists: the Lost Cities attach to it on WorldInitEvent.
        CityApi.registerWorld(WORLD, "The Ruins of %s", new Weathering(seed));
        generator = new RuinsGenerator(seed, (x, z, w, d) -> { World r = ruins; return r != null && CityApi.reserved(r, x, z, w, d); }, this::generationFailed);
        horrors = new Horrors(this);
        trinkets = new Trinkets(this);
        sky = new Sky(this);
        for (Listener l : new Listener[] {this, portals, horrors, bosses, trinkets, sky}) Bukkit.getPluginManager().registerEvents(l, this);
        toldFile = new File(getDataFolder(), "told.txt");
        loadTold();
        Bukkit.getScheduler().runTaskTimer(this, portals::tick, 20L, 4L);
        Bukkit.getScheduler().runTaskTimer(this, this::places, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, horrors::tick, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, horrors::dreadTick, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, bosses::tick, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, trinkets::tick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, sky::tick, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, this::lifecycle, 100L, 40L);
        getLogger().info("RUINS_READY world=" + WORLD + " epoch=" + EPOCH + " loaded=false lostCities=registered portals=" + portals.count()
            + " cityGrid=" + Plans.CITY_GRID + " siteGrid=" + Plans.SITE_GRID + " cell=" + Plans.CELL);
    }

    @Override
    public void onDisable() {
        if (generator == null) return;
        if (bosses != null) bosses.save();
        RuinsPopulator p = generator.populator();
        getLogger().info("RUINS_METRICS chunks=" + generator.chunks + " failures=" + generator.failures + " chests=" + p.chests + " spawners=" + p.spawners
            + " signs=" + p.signs + " weatheredChunks=" + Weathering.chunks + " loads=" + loads + " unloads=" + unloads
            + (horrors == null ? "" : " horrorsRisen=" + horrors.transformed + " horrorsSlain=" + horrors.slain + " elders=" + horrors.elites
                + " ambushes=" + horrors.ambushes + " shadows=" + horrors.shadows + " crumbles=" + horrors.crumbles + " easedHits=" + horrors.eased) + " traps=" + p.traps
            + (sky == null ? "" : " skyFlashes=" + sky.flashes)
            + (bosses == null ? "" : " wardensSlain=" + bosses.wardensSlain + " heraldsSlain=" + bosses.heraldsSlain)
            + (portals == null ? "" : " portalsLit=" + portals.lit + " portalsBuilt=" + portals.built + " travels=" + portals.travels + " portalsClosed=" + portals.closed
                + " portalsLinked=" + portals.linked + " strayArrivals=" + portals.strayArrivals
                + " portalsAdopted=" + portals.adopted + " vanillaBlocked=" + portals.vanillaBlocked));
    }

    // ------------------------------------------------------------------ the world on disk and in memory

    World ruins() { return ruins; }
    Plans plans() { return generator.plans; }
    Horrors horrors() { return horrors; }

    /** The ruins world, loading it from disk (or creating it) if nobody has it open. */
    synchronized World ensureRuins() {
        World w = ruins;
        if (w != null) return w;
        long t0 = System.nanoTime();
        w = Bukkit.getWorld(WORLD);
        if (w == null) w = new WorldCreator(WORLD).environment(World.Environment.NORMAL).seed(seed).generator(generator).generateStructures(false).createWorld();
        if (w == null || !(w.getGenerator() instanceof RuinsGenerator)) {
            getLogger().severe("RUINS_REFUSED world=" + WORLD + " is not using the ruins generator");
            return null;
        }
        ruins = w;
        w.setKeepSpawnInMemory(false);
        w.setDifficulty(main.getDifficulty());
        // Endless night over Drownhollow: the horrors spawn on every stone, and beds do not work.
        w.setGameRuleValue("doDaylightCycle", "false");
        w.setTime(18000L);
        w.setMonsterSpawnLimit((int) Math.round(75 * EASE));   // 150 at first, then 75, now halved again (owner, 2026-09-28)
        w.setTicksPerMonsterSpawns(1);
        w.setAnimalSpawnLimit(0);
        w.setAmbientSpawnLimit(0);
        writeEpoch(new File(Bukkit.getWorldContainer(), WORLD));
        loads++;
        emptySince = 0;
        Plans.Door d = plans().door();
        getLogger().info("RUINS_WORLD_LOADED ms=" + (System.nanoTime() - t0) / 1_000_000L + " loads=" + loads + " monsterCap=" + w.getMonsterSpawnLimit() + " ease=" + EASE + " door=" + d.x + "," + d.base + "," + d.z);
        return w;
    }

    /** Every two seconds: aim compasses; a minute after the last player leaves, save and unload the world. */
    private void lifecycle() {
        compasses();
        World w = ruins;
        if (w == null) return;
        if (!w.getPlayers().isEmpty()) { emptySince = 0; return; }
        long now = System.currentTimeMillis();
        if (emptySince == 0) { emptySince = now; return; }
        if (now - emptySince < 60_000L) return;
        long t0 = System.nanoTime();
        int chunks = w.getLoadedChunks().length, entities = w.getEntities().size();
        bosses.tick();   // lets active bosses notice (bars are cleared once the world is gone)
        if (Bukkit.unloadWorld(w, true)) {
            ruins = null;
            unloads++;
            place.clear();
            getLogger().info("RUINS_WORLD_UNLOADED chunks=" + chunks + " entities=" + entities + " ms=" + (System.nanoTime() - t0) / 1_000_000L + " unloads=" + unloads);
        } else {
            getLogger().warning("RUINS_WORLD_UNLOAD_REFUSED chunks=" + chunks + "; will retry");
        }
        emptySince = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void worldInit(WorldInitEvent e) {
        // Fired before CraftServer preloads spawn chunks (WorldCreator has no such flag in 1.12).
        if (isRuins(e.getWorld())) { e.getWorld().setKeepSpawnInMemory(false); ruins = e.getWorld(); }
    }

    /** A player who logged out inside the ruins finds them loaded again, instead of waking up at the overworld spawn. */
    @EventHandler(priority = EventPriority.LOW)
    public void login(PlayerLoginEvent e) {
        if (ruins != null || e.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        UUID worldId = uid(new File(Bukkit.getWorldContainer(), WORLD + File.separator + "uid.dat"));
        if (worldId == null) return;
        File data = new File(main.getWorldFolder(), "playerdata" + File.separator + e.getPlayer().getUniqueId() + ".dat");
        if (!data.isFile()) return;
        try (InputStream in = new FileInputStream(data)) {
            net.minecraft.server.v1_12_R1.NBTTagCompound root = net.minecraft.server.v1_12_R1.NBTCompressedStreamTools.a(in);
            if (root.getLong("WorldUUIDMost") == worldId.getMostSignificantBits() && root.getLong("WorldUUIDLeast") == worldId.getLeastSignificantBits()) ensureRuins();
        } catch (IOException | RuntimeException ignored) { }
    }

    private static UUID uid(File f) {
        if (!f.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) { return new UUID(in.readLong(), in.readLong()); }
        catch (IOException e) { return null; }
    }

    /** A saved world from an older design is renamed aside (never deleted) so the new design generates from scratch. */
    private void retireOldWorld(File folder) {
        if (!folder.isDirectory() || Bukkit.getWorld(WORLD) != null) return;
        String saved = "1";
        File epoch = new File(folder, EPOCH_FILE);
        try { if (epoch.isFile()) saved = new String(Files.readAllBytes(epoch.toPath()), StandardCharsets.UTF_8).trim(); } catch (IOException ignored) { }
        if (String.valueOf(EPOCH).equals(saved)) return;
        File retired = new File(folder.getParentFile(), WORLD + "-retired-epoch" + saved.replaceAll("[^0-9]", "") + "-" + System.currentTimeMillis());
        if (folder.renameTo(retired)) {
            portals.forgetWorld(WORLD);
            bosses.resetWorldState();
            getLogger().info("RUINS_REGENERATED epoch=" + EPOCH + " retiredTo=" + retired.getName());
        } else {
            getLogger().warning("RUINS_REGENERATE_FAILED could not rename the old world; keeping it");
        }
    }

    private void writeEpoch(File folder) {
        try { folder.mkdirs(); Files.write(new File(folder, EPOCH_FILE).toPath(), String.valueOf(EPOCH).getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { getLogger().warning("RUINS_EPOCH_UNSAVED " + e.getClass().getSimpleName()); }
    }

    private long savedSeed(File level, long fallback) {
        if (!level.isFile()) return fallback;
        try (InputStream in = new FileInputStream(level)) {
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

    boolean isRuins(World w) { return w != null && WORLD.equals(w.getName()); }
    boolean portalWorld(World w) { return w != null && (w.equals(main) || isRuins(w)); }
    World otherWorld(World w) { return isRuins(w) ? main : w != null && w.equals(main) ? ensureRuins() : null; }

    // ------------------------------------------------------------------ arriving: the guide, the compass, the night

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrived(PlayerChangedWorldEvent e) { if (isRuins(e.getPlayer().getWorld())) welcome(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (isRuins(p.getWorld())) Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) welcome(p); }, 40L);
        if (!getConfig().getBoolean("hint-on-join", true)) return;
        if (!told.add(p.getUniqueId().toString())) return;
        saveTold();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            p.sendMessage(ChatColor.DARK_GREEN + "New: the drowned city of Drownhollow. " + ChatColor.GRAY + "Build a nether-portal frame out of "
                + ChatColor.GREEN + "mossy cobblestone" + ChatColor.GRAY + " (at least 4 wide, 5 tall) and light it with flint and steel. Conquer it to win its treasures.");
        }, 200L);
    }

    @EventHandler
    public void respawned(PlayerRespawnEvent e) {
        if (isRuins(e.getRespawnLocation().getWorld())) Bukkit.getScheduler().runTaskLater(this, () -> welcome(e.getPlayer()), 5L);
    }

    /** Anyone in the ruins without the Pilgrim's Primer or the compass is handed them. */
    void welcome(Player p) {
        boolean guide = false, compass = false;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.WRITTEN_BOOK && item.hasItemMeta() && Lore.GUIDE_TITLE.equals(((BookMeta) item.getItemMeta()).getTitle())) guide = true;
            if (isCompass(item)) compass = true;
        }
        Plans.Door d = plans().door();
        List<ItemStack> give = new ArrayList<>();
        if (!guide) give.add(Lore.guide(d.x, d.z));
        if (!compass) give.add(compass());
        for (ItemStack item : give) for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        if (!give.isEmpty()) {
            getLogger().info("RUINS_WELCOME player=" + p.getName() + " guide=" + !guide + " compass=" + !compass);
            p.sendTitle(ChatColor.DARK_GREEN + "Drownhollow", ChatColor.GRAY + "The drowned city of the Choir", 10, 70, 20);
            p.sendMessage(ChatColor.DARK_GREEN + "The city presses a book into your hands. " + ChatColor.GRAY + "Read the "
                + ChatColor.WHITE + Lore.GUIDE_TITLE + ChatColor.GRAY + ": it tells you how to conquer this place. Hold the "
                + ChatColor.DARK_AQUA + COMPASS + ChatColor.GRAY + " to find your way.");
        }
    }

    static ItemStack compass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + COMPASS);
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Points to the nearest Warden whose Seal", ChatColor.GRAY + "you lack; with three Seals, to the Great Door.",
            ChatColor.DARK_GRAY + COMPASS_MARK));
        item.setItemMeta(meta);
        return item;
    }

    static boolean isCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        for (String line : item.getItemMeta().getLore()) {
            String plain = ChatColor.stripColor(line);
            if (COMPASS_MARK.equals(plain) || OLD_COMPASS_MARK.equals(plain)) return true;
        }
        return false;
    }

    /** Compasses in the ruins point at the next Warden (or the Door); elsewhere they point home again. */
    private void compasses() {
        World w = ruins;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (w == null || p.getWorld() != w) {
                if (compassAimed.remove(p.getUniqueId())) p.setCompassTarget(p.getWorld().getSpawnLocation());
                continue;
            }
            p.setCompassTarget(target(p));
            compassAimed.add(p.getUniqueId());
        }
    }

    Location target(Player p) {
        Plans.Door d = plans().door();
        Location door = new Location(p.getWorld(), d.x, d.base, d.z);
        EnumSet<Trinkets.Seal> have = Trinkets.seals(p);
        if (have.size() >= Bosses.SEALS_NEEDED) return door;
        Location at = p.getLocation(), best = null;
        double bestD = Double.MAX_VALUE;
        int i0 = Math.floorDiv(at.getBlockX(), Plans.SITE_GRID), j0 = Math.floorDiv(at.getBlockZ(), Plans.SITE_GRID);
        for (int a = i0 - 14; a <= i0 + 14; a++)
            for (int b = j0 - 14; b <= j0 + 14; b++) {
                Plans.Site s = plans().site(a, b);
                if (s == null || !s.kind.cult) continue;
                Bosses.Boss boss = Bosses.Boss.of(s.kind);
                if (boss == null || have.contains(boss.seal)) continue;
                double dx = s.x - at.getX(), dz = s.z - at.getZ(), dd = dx * dx + dz * dz;
                if (dd < bestD) { bestD = dd; best = new Location(p.getWorld(), s.x, s.base, s.z); }
            }
        return best != null ? best : door;
    }

    @EventHandler(ignoreCancelled = true)
    public void noSleep(PlayerBedEnterEvent e) {
        if (!isRuins(e.getPlayer().getWorld())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "The Dreamer allows no other dreams in Drownhollow.");
    }

    // ------------------------------------------------------------------ names on entering

    private void places() {
        World r = ruins;
        if (r == null) return;
        for (Player p : r.getPlayers()) {
            Location l = p.getLocation();
            String now = null, title = null;
            Plans.City city = plans().cityNear(l.getBlockX(), l.getBlockZ());
            Plans.Door d = plans().door();
            if (Math.abs(l.getBlockX() - d.x) <= 44 && Math.abs(l.getBlockZ() - d.z) <= 48) { now = "door"; title = "The Great Door"; }
            else if (city != null && city.outside(l.getBlockX(), l.getBlockZ()) <= 0) { now = "city:" + city.name; title = "The Old City of " + city.name; }
            else {
                Plans.Site site = plans().siteAt(l.getBlockX(), l.getBlockZ());
                if (site != null) { now = "site:" + site.name; if (site.kind.cult) title = site.name; }
            }
            String before = now == null ? place.remove(p.getUniqueId()) : place.put(p.getUniqueId(), now);
            if (now == null || now.equals(before)) continue;
            if (title != null) p.sendTitle("", ChatColor.DARK_GREEN + title, 10, 50, 20);
            else p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + now.substring(5)));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) { place.remove(e.getPlayer().getUniqueId()); compassAimed.remove(e.getPlayer().getUniqueId()); }

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
            World r = ruins;
            sender.sendMessage(ChatColor.DARK_GREEN + "Drownhollow: " + ChatColor.GRAY + (r == null ? "unloaded (on disk)" : "loaded, " + r.getPlayers().size() + " players, "
                + r.getLoadedChunks().length + " chunks, " + r.getEntities().size() + " entities") + "; " + (generator == null ? 0 : generator.chunks) + " chunks generated, "
                + (portals == null ? 0 : portals.count()) + " portals" + (p == null ? "" : ", " + p.chests + " chests") + "; loads " + loads + ", unloads " + unloads);
            if (bosses != null) sender.sendMessage(ChatColor.GRAY + bosses.status());
            return true;
        }
        if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
        Player player = (Player) sender;
        String arg = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "tp": { World r = ensureRuins(); if (r != null) go(player, surface(r, 0, 0)); return true; }
            case "back": go(player, surface(main, player.getLocation().getBlockX(), player.getLocation().getBlockZ())); return true;
            case "where": player.sendMessage(ChatColor.GRAY + where(player.getLocation())); return true;
            case "find": {
                World r = ensureRuins();
                if (r == null) return true;
                Location to = find(r, arg.isEmpty() ? "city" : arg, player.getWorld().equals(r) ? player.getLocation() : new Location(r, 0, 64, 0));
                if (to == null) player.sendMessage(ChatColor.RED + "Nothing like that nearby.");
                else go(player, to);
                return true;
            }
            case "guide": give(player, Lore.guide(plans().door().x, plans().door().z)); give(player, compass()); return true;
            case "lore": for (int i = 0; i < Lore.bookCount(); i++) give(player, Lore.book(i)); return true;
            case "seal": { for (Trinkets.Seal s : Trinkets.Seal.values()) if (arg.isEmpty() || s.key().equals(arg)) give(player, Trinkets.seal(s)); return true; }
            case "trinket": { for (Trinkets.Trinket t : Trinkets.Trinket.values()) if (arg.isEmpty() || t.key().equals(arg)) give(player, Trinkets.item(t)); return true; }
            case "boss": {
                Bosses.Boss b = Bosses.parse(arg);
                if (b == null || !isRuins(player.getWorld())) { player.sendMessage(ChatColor.GRAY + "/ruins boss <" + Arrays.stream(Bosses.Boss.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("|")) + "> (in Drownhollow)"); return true; }
                bosses.spawn(b, "test_" + System.currentTimeMillis(), player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(6)));
                return true;
            }
            case "horror": {
                Horrors.Kind k = null;
                for (Horrors.Kind x : Horrors.Kind.values()) if (x.name().equalsIgnoreCase(arg)) k = x;
                if (k == null || !isRuins(player.getWorld())) { player.sendMessage(ChatColor.GRAY + "/ruins horror <kind> (in Drownhollow)"); return true; }
                horrors.spawn(k, player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(5)));
                return true;
            }
            case "door": {
                World r = ruins;
                if (r == null) { player.sendMessage(ChatColor.GRAY + "Drownhollow is not loaded."); return true; }
                if (arg.equals("open")) bosses.openDoor(r); else if (arg.equals("close")) bosses.closeDoor(r);
                else { Plans.Door d = plans().door(); go(player, surface(r, d.x, d.z - 30)); }
                return true;
            }
            case "dread": player.sendMessage(ChatColor.DARK_PURPLE + "Dread " + horrors.dreadOf(player)); return true;
            case "unload": emptySince = 1; lifecycle(); return true;
            default:
                player.sendMessage(ChatColor.GRAY + "/ruins [status|tp|back|where|find <city|lostcity|" + kinds() + ">|door [open|close]|guide|lore|seal [type]|trinket [type]|boss <type>|horror <kind>|dread|unload]");
                return true;
        }
    }

    private static void give(Player p, ItemStack item) { for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("status", "tp", "back", "where", "find", "door", "guide", "lore", "seal", "trinket", "boss", "horror", "dread", "unload");
        if (args.length == 2 && "find".equalsIgnoreCase(args[0])) {
            List<String> all = new ArrayList<>(Arrays.asList("city", "lostcity"));
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
        if (!isRuins(l.getWorld())) return "Not in Drownhollow.";
        int x = l.getBlockX(), z = l.getBlockZ();
        Plans.Door d = plans().door();
        if (Math.abs(x - d.x) <= 44 && Math.abs(z - d.z) <= 48) return "The Great Door (" + d.x + ", " + d.z + ")";
        Plans.City c = plans().cityNear(x, z);
        if (c != null) return "Old City of " + c.name + " (centre " + c.x + ", " + c.z + ", walls " + (2 * c.half + 1) + " wide)";
        Plans.Site s = plans().siteAt(x, z);
        if (s != null) return s.name + " (" + s.kind.noun + " at " + s.x + ", " + s.z + ")";
        String lc = CityApi.cityName(l.getWorld().getSeed(), x >> 4, z >> 4);
        return lc != null ? "Ruins of " + lc + " (Lost Cities)" : "The ruin field";
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
                        Plans.City c = plans().city(i0 + a, j0 + b);
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
                    Plans.Site s = plans().site(i0 + a, j0 + b);
                    if (s != null && s.kind == kind) return surface(r, s.x, s.z - s.kind.radius - 2);
                }
        return null;
    }
}
