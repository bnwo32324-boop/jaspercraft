package chat.jaspr.backrooms;

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
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprBackrooms: the Backrooms (owner, 2026-09-30). Seven liminal spaces in a row, each harder than the last and harder
 * the further in: the Yellow Rooms, the Warehouse, the Maintenance Tunnels, the Electrical Corridors, the Abandoned
 * Office, the Endless City and the Poolrooms. Each has its own monsters, hazards, boss and gear, and loot improves the
 * deeper it lies. Only a conqueror (Atlas, Drownhollow, the Nether or the Ender Dragon) can light the yellow glazed
 * terracotta portal and pass through it; the Threshold beyond is peaceful, with a guide who hands out a compass, a
 * checklist and a map. Nothing in here can be broken but what players placed. Beating the Lifeguard at the bottom of
 * the Poolrooms is the way out, and conquers the Backrooms.
 *
 * <p>The world lives on disk while nobody is in it: it loads when someone enters (portal, owner command, or logging
 * in where they left off) and is saved and fully unloaded a minute after its last player leaves. Logs BACKROOMS_READY,
 * BACKROOMS_WORLD_LOADED/UNLOADED, BACKROOMS_PORTAL_*, BACKROOMS_TRAVEL, BACKROOMS_LEVEL_REACHED, BACKROOMS_EXIT,
 * BACKROOMS_BOSS_*, BACKROOMS_RESCUED, BACKROOMS_CONQUEROR_TOLD, BACKROOMS_DRAGON_SLAIN and BACKROOMS_METRICS.
 */
public final class BackroomsPlugin extends JavaPlugin implements Listener {
    static final String WORLD = "jaspr_levels";
    static final long SALT = 0x4261636B726F6FL;   // "Backroo"
    /** Bumped when the levels are redesigned: an older saved world is renamed aside (never deleted) and regenerated. */
    static final int EPOCH = 1;
    static final String EPOCH_FILE = "jaspr-backrooms-epoch.txt";
    /** Fixed dusk over the Endless City (the other levels have ceilings). */
    static final long TIME = 12800L;

    private volatile World world;
    private World main;
    private long seed;
    private BackroomsGenerator generator;
    private Protect protect;
    private Conquest conquest;
    private Portals portals;
    private Travel travel;
    private Mobs mobs;
    private Bosses bosses;
    private Gear gear;
    private Hazards hazards;
    private Quest quest;
    private GuideKit guide;
    private long emptySince, loads, unloads, failuresLogged;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) { getLogger().info("BACKROOMS_DISABLED by config"); return; }
        main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (main == null) { getLogger().severe("BACKROOMS_REFUSED no overworld loaded"); return; }
        File folder = new File(Bukkit.getWorldContainer(), WORLD);
        protect = new Protect(this);
        retireOldWorld(folder);
        seed = savedSeed(new File(folder, "level.dat"), main.getSeed() ^ SALT);
        generator = new BackroomsGenerator(seed, this::generationFailed);
        generator.loot(Loot::fill);
        conquest = new Conquest(this);
        portals = new Portals(this);
        travel = new Travel(this);
        mobs = new Mobs(this);
        bosses = new Bosses(this);
        gear = new Gear(this);
        hazards = new Hazards(this);
        quest = new Quest(this);
        guide = new GuideKit(this, quest);   // registers itself: the guides, compass, checklist and map
        for (Listener l : new Listener[] {this, protect, conquest, portals, travel, mobs, bosses, gear, hazards}) Bukkit.getPluginManager().registerEvents(l, this);
        Bukkit.getScheduler().runTaskTimer(this, portals::tick, 20L, 4L);
        Bukkit.getScheduler().runTaskTimer(this, travel::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, mobs::direct, 60L, 40L);
        Bukkit.getScheduler().runTaskTimer(this, mobs::behave, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, bosses::tick, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, gear::tick, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, hazards::second, 40L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, hazards::half, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, conquest::tick, 200L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, protect::save, 600L, 600L);
        Bukkit.getScheduler().runTaskTimer(this, this::lifecycle, 100L, 40L);
        getLogger().info("BACKROOMS_READY world=" + WORLD + " epoch=" + EPOCH + " loaded=false levels=" + Level.ALL.length + " items=" + Items.DEFS.size()
            + " monsters=" + Mobs.Kind.values().length + " bosses=" + Bosses.Boss.values().length + " gates=" + portals.count() + " placed=" + protect.count());
        if (Boolean.getBoolean("jaspr.backrooms.selftest")) Bukkit.getScheduler().runTaskLater(this, () -> new SelfTest(this).run(null), 100L);
    }

    @Override
    public void onDisable() {
        if (generator == null) return;
        if (protect != null) protect.save();
        if (bosses != null) bosses.shutdown();
        getLogger().info("BACKROOMS_METRICS chunks=" + generator.chunks + " failures=" + generator.failures + " chests=" + generator.chests + " signs=" + generator.signs
            + " loads=" + loads + " unloads=" + unloads + " " + (protect == null ? "" : protect.describe()) + " " + (portals == null ? "" : portals.describe())
            + " " + (travel == null ? "" : travel.describe()) + " " + (mobs == null ? "" : mobs.describe()) + " " + (bosses == null ? "" : bosses.describe())
            + " " + (gear == null ? "" : gear.describe()) + " " + (hazards == null ? "" : hazards.describe()));
    }

    // ---- accessors ----------------------------------------------------------------------------------------------------
    World world() { return world; }
    World main() { return main; }
    long seed() { return seed; }
    Protect protect() { return protect; }
    Conquest conquest() { return conquest; }
    Portals portals() { return portals; }
    Travel travel() { return travel; }
    Mobs mobs() { return mobs; }
    Bosses bosses() { return bosses; }
    Gear gear() { return gear; }
    Hazards hazards() { return hazards; }
    Quest quest() { return quest; }
    GuideKit guide() { return guide; }
    BackroomsGenerator generator() { return generator; }
    boolean isBackrooms(World w) { return w != null && WORLD.equals(w.getName()); }

    // ---- the world on disk and in memory --------------------------------------------------------------------------------
    /** The Backrooms world, loading it from disk (or creating it) if nobody has it open. */
    synchronized World ensureWorld() {
        World w = world;
        if (w != null) return w;
        long t0 = System.nanoTime();
        w = Bukkit.getWorld(WORLD);
        if (w == null) w = new WorldCreator(WORLD).environment(World.Environment.NORMAL).seed(seed).generator(generator).generateStructures(false).createWorld();
        if (w == null || !(w.getGenerator() instanceof BackroomsGenerator)) {
            getLogger().severe("BACKROOMS_REFUSED world=" + WORLD + " is not using the Backrooms generator");
            return null;
        }
        world = w;
        lightBeforeSending(w);
        w.setKeepSpawnInMemory(false);
        w.setDifficulty(main.getDifficulty() == Difficulty.PEACEFUL ? Difficulty.EASY : main.getDifficulty());
        for (String[] rule : new String[][] {{"doDaylightCycle", "false"}, {"doWeatherCycle", "false"}, {"mobGriefing", "false"}, {"doFireTick", "false"},
            {"doMobSpawning", "false"}}) w.setGameRuleValue(rule[0], rule[1]);
        w.setTime(TIME);
        w.setStorm(false);
        w.setThundering(false);
        w.setMonsterSpawnLimit(0);
        w.setAnimalSpawnLimit(0);
        w.setWaterAnimalSpawnLimit(0);
        w.setAmbientSpawnLimit(0);
        double[] t = Rooms.threshold();
        w.setSpawnLocation((int) Math.floor(t[0]), (int) t[1], (int) Math.floor(t[2]));
        writeEpoch(new File(Bukkit.getWorldContainer(), WORLD));
        loads++;
        emptySince = 0;
        getLogger().info("BACKROOMS_WORLD_LOADED ms=" + (System.nanoTime() - t0) / 1_000_000L + " loads=" + loads);
        return w;
    }

    /** Whether new chunks here go out only once their light is worked out ("wait"), or as soon as generated. */
    volatile String lightMode = "unset";

    /**
     * The Backrooms are lit by blocks, not the sky. With Spigot's random-light-updates off (the server default) a new
     * chunk is sent before its light is worked out and stays dark on the client until it happens to be sent again; for
     * this world alone the server waits for the light first, as the game itself does.
     */
    private void lightBeforeSending(World w) {
        try {
            ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle().spigotConfig.randomLightUpdates = true;
            lightMode = "wait";
        } catch (Throwable t) {
            lightMode = "unavailable";
            getLogger().warning("BACKROOMS_LIGHT mode=unavailable error=" + t.getClass().getSimpleName());
        }
        getLogger().info("BACKROOMS_LIGHT mode=" + lightMode);
    }

    /** Every two seconds: a minute after the last player leaves, save and unload the world. */
    private void lifecycle() {
        World w = world;
        if (w == null) return;
        if (!w.getPlayers().isEmpty()) { emptySince = 0; return; }
        long now = System.currentTimeMillis();
        if (emptySince == 0) { emptySince = now; return; }
        if (now - emptySince < 60_000L) return;
        long t0 = System.nanoTime();
        int chunks = w.getLoadedChunks().length, entities = w.getEntities().size();
        bosses.shutdown();
        for (org.bukkit.entity.Entity e : w.getEntities()) if (Mobs.isOurs(e)) e.remove();
        if (Bukkit.unloadWorld(w, true)) {
            world = null;
            unloads++;
            getLogger().info("BACKROOMS_WORLD_UNLOADED chunks=" + chunks + " entities=" + entities + " ms=" + (System.nanoTime() - t0) / 1_000_000L + " unloads=" + unloads);
        } else getLogger().warning("BACKROOMS_WORLD_UNLOAD_REFUSED chunks=" + chunks + "; will retry");
        emptySince = 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void worldInit(WorldInitEvent e) {
        if (isBackrooms(e.getWorld())) { e.getWorld().setKeepSpawnInMemory(false); world = e.getWorld(); }
    }

    /** A player who logged out inside the Backrooms finds them loaded again, instead of waking up at the overworld spawn. */
    @EventHandler(priority = EventPriority.LOW)
    public void login(PlayerLoginEvent e) {
        if (world != null || e.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
        UUID worldId = uid(new File(Bukkit.getWorldContainer(), WORLD + File.separator + "uid.dat"));
        if (worldId == null) return;
        File data = new File(main.getWorldFolder(), "playerdata" + File.separator + e.getPlayer().getUniqueId() + ".dat");
        if (!data.isFile()) return;
        try (InputStream in = new FileInputStream(data)) {
            net.minecraft.server.v1_12_R1.NBTTagCompound root = net.minecraft.server.v1_12_R1.NBTCompressedStreamTools.a(in);
            if (root.getLong("WorldUUIDMost") == worldId.getMostSignificantBits() && root.getLong("WorldUUIDLeast") == worldId.getLeastSignificantBits()) ensureWorld();
        } catch (IOException | RuntimeException ignored) { }
    }

    private static UUID uid(File f) {
        if (!f.isFile()) return null;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) { return new UUID(in.readLong(), in.readLong()); }
        catch (IOException e) { return null; }
    }

    private void retireOldWorld(File folder) {
        if (!folder.isDirectory() || Bukkit.getWorld(WORLD) != null) return;
        String saved = "1";
        File epoch = new File(folder, EPOCH_FILE);
        try { if (epoch.isFile()) saved = new String(Files.readAllBytes(epoch.toPath()), StandardCharsets.UTF_8).trim(); } catch (IOException ignored) { }
        if (String.valueOf(EPOCH).equals(saved)) return;
        File retired = new File(folder.getParentFile(), WORLD + "-retired-epoch" + saved.replaceAll("[^0-9]", "") + "-" + System.currentTimeMillis());
        if (folder.renameTo(retired)) { protect.reset(); getLogger().info("BACKROOMS_REGENERATED epoch=" + EPOCH + " retiredTo=" + retired.getName()); }
        else getLogger().warning("BACKROOMS_REGENERATE_FAILED could not rename the old world; keeping it");
    }

    private void writeEpoch(File folder) {
        try { folder.mkdirs(); Files.write(new File(folder, EPOCH_FILE).toPath(), String.valueOf(EPOCH).getBytes(StandardCharsets.UTF_8)); }
        catch (IOException e) { getLogger().warning("BACKROOMS_EPOCH_UNSAVED " + e.getClass().getSimpleName()); }
    }

    private long savedSeed(File level, long fallback) {
        if (!level.isFile()) return fallback;
        try (InputStream in = new FileInputStream(level)) {
            return net.minecraft.server.v1_12_R1.NBTCompressedStreamTools.a(in).getCompound("Data").getLong("RandomSeed");
        } catch (IOException | RuntimeException e) {
            getLogger().warning("BACKROOMS_SEED_UNREADABLE " + e.getClass().getSimpleName() + "; using the overworld's");
            return fallback;
        }
    }

    void generationFailed(String where, Throwable e) {
        if (++failuresLogged > 20) return;
        StackTraceElement at = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
        getLogger().severe("BACKROOMS_CHUNK_FAILED " + where + " " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).replaceAll("[\\r\\n]", " ")
            + (at == null ? "" : " at " + at.getClassName().replace("chat.jaspr.backrooms.", "") + ":" + at.getLineNumber()));
    }

    // ---- moving players, arriving -------------------------------------------------------------------------------------
    /** Teleports a player (out of any vehicle, no fall damage); an arrival in the Backrooms meets the guide there. */
    /** The height a player can stand at in a column near walking height (feet and head clear, ground or water below), or MIN_VALUE. */
    static int standY(World w, int x, int z) {
        for (int y = Level.WALK + 2; y >= Level.WALK - 8; y--) {
            org.bukkit.block.Block feet = w.getBlockAt(x, y, z);
            if (!feet.getType().isSolid() && !feet.getRelative(0, 1, 0).getType().isSolid() && !feet.getRelative(0, 1, 0).isLiquid()
                && (feet.getRelative(0, -1, 0).getType().isSolid() || feet.isLiquid())) return y;
        }
        return Integer.MIN_VALUE;
    }

    void go(Player p, Location to, boolean arriving) {
        if (p.isInsideVehicle()) p.leaveVehicle();
        p.setFallDistance(0f);
        if (arriving && isBackrooms(to.getWorld())) { quest.gate(p, to); travel.arrived(p); }
        p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrived(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isBackrooms(p.getWorld())) Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline() && isBackrooms(p.getWorld())) welcome(p); }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void joined(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (isBackrooms(p.getWorld())) Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) welcome(p); }, 40L);
    }

    @EventHandler
    public void respawned(PlayerRespawnEvent e) {
        if (isBackrooms(e.getRespawnLocation().getWorld())) Bukkit.getScheduler().runTaskLater(this, () -> welcome(e.getPlayer()), 5L);
    }

    /** A guide stands where the player arrived (the Threshold or a landing); the kit and the goal are explained there. */
    void welcome(Player p) {
        if (guide == null) return;
        Location gate = quest.takeGate(p);
        if (gate == null) {
            Level lv = Level.at(p.getLocation().getX(), p.getLocation().getZ());
            double[] a = Rooms.arrival(lv == null ? Level.YELLOW : lv);
            if (p.getLocation().distanceSquared(new Location(p.getWorld(), a[0], a[1], a[2])) < 20 * 20) gate = new Location(p.getWorld(), a[0], a[1], a[2]);
        }
        guide.arrive(p, gate);
    }

    // ---- owner commands -----------------------------------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("goals")) return true;   // the guide kit prints each realm's part
        if (args.length >= 3 && args[0].equalsIgnoreCase("as")) {
            Player as = Bukkit.getPlayerExact(args[1]);
            if (as == null) { sender.sendMessage(ChatColor.RED + "No player " + args[1]); return true; }
            return onCommand(as, command, label, Arrays.copyOfRange(args, 2, args.length));
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        String arg = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("status")) {
            World w = world;
            sender.sendMessage(ChatColor.YELLOW + "The Backrooms: " + ChatColor.GRAY + (w == null ? "unloaded (on disk)" : "loaded, " + w.getPlayers().size() + " players, "
                + w.getLoadedChunks().length + " chunks, " + w.getEntities().size() + " entities, light " + lightMode) + "; " + generator.chunks + " chunks generated, loads " + loads + ", unloads " + unloads);
            for (String s : new String[] {protect.describe(), portals.describe(), travel.describe(), mobs.describe(), bosses.describe(), gear.describe(), hazards.describe(), guide.status()})
                sender.sendMessage(ChatColor.GRAY + "  " + s);
            return true;
        }
        if (sub.equals("selftest")) { new SelfTest(this).run(sender); return true; }
        if (!(sender instanceof Player)) { sender.sendMessage("Players only (or /backrooms as <player> <sub>)."); return true; }
        Player p = (Player) sender;
        switch (sub) {
            case "tp": {
                World w = ensureWorld();
                if (w == null) return true;
                Level lv = Level.of(arg.isEmpty() ? 1 : parse(arg));
                if (lv == null) lv = Level.YELLOW;
                go(p, travel.landing(w, lv), true);
                return true;
            }
            case "arena": {
                World w = ensureWorld();
                Level lv = Level.of(parse(arg));
                if (w == null || lv == null) { p.sendMessage(ChatColor.GRAY + "/backrooms arena <1-7>"); return true; }
                int ax = lv.arenaStart() + 3;
                w.loadChunk(ax >> 4, 0, true);
                int y = Level.WALK;
                while (y < Level.WALK + 4 && (w.getBlockAt(ax, y, 0).getType().isSolid() || w.getBlockAt(ax, y + 1, 0).getType().isSolid())) y++;
                go(p, new Location(w, ax + 0.5, y, 0.5, -90, 0), true);
                return true;
            }
            case "peek": {
                // An open spot on a level's spine, a fraction of the way across (owner tool): /backrooms peek <1-7> <0..1>
                World w = ensureWorld();
                Level lv = Level.of(parse(arg));
                double f = args.length > 2 ? Math.max(0, Math.min(1, Double.parseDouble(args[2]))) : 0.5;
                if (w == null || lv == null) { p.sendMessage(ChatColor.GRAY + "/backrooms peek <1-7> [0..1]"); return true; }
                int x = (int) Math.round(lv.entryEnd() + f * (lv.arenaStart() - lv.entryEnd()));
                int z = lv == Level.CITY ? 0 : (int) Math.round(lv.spineZ(seed, x + 0.5));   // the city's way is the avenue
                w.loadChunk(x >> 4, z >> 4, true);
                // The nearest open spot, ring by ring (a maze level's corridors do not always lie on the spine).
                for (int r = 0; r <= 24; r++)
                    for (int dx = -r; dx <= r; dx++)
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                            int y = standY(w, x + dx, z + dz);
                            if (y == Integer.MIN_VALUE) continue;
                            go(p, new Location(w, x + dx + 0.5, y, z + dz + 0.5, -90, 0), false);
                            return true;
                        }
                p.sendMessage(ChatColor.GRAY + "No open spot there.");
                return true;
            }
            case "back": portals.goHome(p); return true;
            case "where": {
                Level lv = isBackrooms(p.getWorld()) ? Level.at(p.getLocation().getX(), p.getLocation().getZ()) : null;
                p.sendMessage(ChatColor.GRAY + (lv == null ? "Not in a level." : lv.label() + ", progress " + Math.round(100 * lv.progress(p.getLocation().getX())) + "%, danger "
                    + Math.round(100 * lv.danger(p.getLocation().getX(), p.getLocation().getZ())) + "%"));
                return true;
            }
            case "guide": guide.offer(p, true); if (isBackrooms(p.getWorld())) guide.ensureGuide(p.getLocation()); return true;
            case "quest": for (String line : guide.goalLines(p)) p.sendMessage(line); return true;
            case "boss": {
                Level lv = Level.of(parse(arg));
                if (lv == null || world == null) { p.sendMessage(ChatColor.GRAY + "/backrooms boss <1-7> (the Backrooms must be loaded)"); return true; }
                bosses.wake(lv);
                return true;
            }
            case "mob": {
                Mobs.Kind k = null;
                for (Mobs.Kind x : Mobs.Kind.values()) if (x.name().equalsIgnoreCase(arg)) k = x;
                if (k == null) { p.sendMessage(ChatColor.GRAY + "/backrooms mob <" + String.join("|", Mobs.names()) + ">"); return true; }
                mobs.test(k, p);
                return true;
            }
            case "item": {
                if (arg.equals("all") || arg.startsWith("level")) {
                    Level only = arg.startsWith("level") ? Level.of(parse(arg.replace("level", ""))) : null;
                    for (Items.Def d : Items.DEFS.values()) if (only == null || d.level == only) give(p, Items.make(d));
                    return true;
                }
                ItemStack s = Items.make(arg);
                if (s == null) { p.sendMessage(ChatColor.GRAY + "/backrooms item <id|all|level<1-7>>: " + String.join(", ", Items.ids())); return true; }
                give(p, s);
                return true;
            }
            case "clear": { int n = parse(arg); if (Level.of(n) != null) { p.addScoreboardTag(Travel.CLEAR + n); p.sendMessage(ChatColor.GRAY + "Level " + n + " marked beaten."); } return true; }
            case "reach": { int n = parse(arg); if (Level.of(n) != null) { p.addScoreboardTag(Travel.REACH + n); p.sendMessage(ChatColor.GRAY + "Level " + n + " marked reached."); } return true; }
            case "conqueror": {
                p.addScoreboardTag(Conquest.END_TAG);
                p.sendMessage(ChatColor.GRAY + "Marked as conqueror of the End (for testing the portal).");
                return true;
            }
            case "reset": {
                for (String t : new ArrayList<>(p.getScoreboardTags())) if (t.startsWith(Travel.CLEAR) || t.startsWith(Travel.REACH) || t.startsWith("jr_beat_backrooms") || t.equals("jr_kit_backrooms")) p.removeScoreboardTag(t);
                p.sendMessage(ChatColor.GRAY + "Your Backrooms progress is reset.");
                return true;
            }
            case "unload": emptySince = 1; lifecycle(); return true;
            default:
                p.sendMessage(ChatColor.GRAY + "/backrooms [status|tp [1-7]|arena <1-7>|peek <1-7> [0..1]|back|where|guide|quest|boss <1-7>|mob <kind>|item <id|all|level<n>>|clear <n>|reach <n>|conqueror|reset|unload|selftest|as <player> <sub>]");
                return true;
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; } }

    static void give(Player p, ItemStack item) { for (ItemStack left : p.getInventory().addItem(item).values()) p.getWorld().dropItemNaturally(p.getLocation(), left); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("backrooms")) return Collections.emptyList();
        if (args.length == 1) return Arrays.asList("status", "tp", "arena", "peek", "back", "where", "guide", "quest", "boss", "mob", "item", "clear", "reach", "conqueror", "reset", "unload", "selftest", "as");
        if (args.length == 2 && "mob".equalsIgnoreCase(args[0])) return Mobs.names();
        if (args.length == 2 && "item".equalsIgnoreCase(args[0])) { List<String> all = new ArrayList<>(Items.ids()); all.add("all"); return all; }
        return Collections.emptyList();
    }
}
