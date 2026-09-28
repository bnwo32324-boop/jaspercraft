package chat.jaspr.nether;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprNether: BetterNether 0.1.8.6 and NetherEx 2.2.5 ported to Paper 1.12.2 as one harmonised Nether.
 * NetherEx supplies the large regions, BetterNether fills its Hell regions; both mods' features, structures, items,
 * mobs and the Ghast Queen coexist. JasperCraft adds five mega structures (Mega), their garrisons and the wonders.
 * Everything the browser client sees is vanilla.
 */
public final class NetherPlugin extends JavaPlugin implements Listener {
    static final String VERSION = "1.1.0";
    /**
     * Regeneration epoch. Raising it regenerates the Nether once more on the next start (v1 2026-09-26: the port;
     * v2 2026-09-28: the owner asked for a fresh Nether with the mega structures and wonders).
     */
    static final int REGEN_EPOCH = 2;
    static final String OUTER_REALMS = "chat.jaspr.biomes.OuterRealms";

    String worldName = "world_nether";
    World nether;
    Gen gen;
    Registry registry;
    StructureOps structures;
    Mobs mobs;
    Effects effects;
    Mechanics mechanics;
    Crafting crafting;
    Boss boss;
    Fireflies fireflies;
    Garrisons garrisons;

    // generation health
    long populated, totalNanos, maxNanos, blocksWritten;
    int failures, consecutiveFailures, warnings;
    boolean genDisabled;
    private int outerRealmsRemoved;

    /**
     * The owner asked for the Nether to be regenerated (the port, then again for the mega structures). Once per epoch,
     * before any world loads, the old Nether's region files (and this plugin's records of that terrain) are MOVED --
     * never deleted -- to plugins/JasprNether/nether-before-v&lt;epoch&gt;/; the marker file makes it one-shot.
     * "regenerate-once: false" in config.yml skips it. To undo: stop the server and move nether-before-v&lt;epoch&gt;/region
     * back to &lt;world&gt;/DIM-1/region.
     */
    @Override public void onLoad() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("regenerate-once", true)) return;
        File marker = new File(getDataFolder(), "regenerated-v" + REGEN_EPOCH + ".txt");
        if (marker.exists()) return;
        String name = getConfig().getString("world", "world_nether");
        File region = new File(new File(new File(Bukkit.getWorldContainer(), name), "DIM-1"), "region");
        File backup = new File(getDataFolder(), "nether-before-v" + REGEN_EPOCH);
        try {
            if (new File(backup, "region").exists()) throw new java.io.IOException("backup folder already exists");
            int files = 0;
            if (region.isDirectory()) {
                String[] list = region.list();
                files = list == null ? 0 : list.length;
                backup.mkdirs();
                java.nio.file.Files.move(region.toPath(), new File(backup, "region").toPath());   // same volume: a rename
            }
            File records = new File(getDataFolder(), "data" + File.separator + name), rime = new File(getDataFolder(), "rime.txt");
            if (records.exists()) { backup.mkdirs(); java.nio.file.Files.move(records.toPath(), new File(backup, "data-" + name).toPath()); }
            if (rime.exists()) { backup.mkdirs(); java.nio.file.Files.move(rime.toPath(), new File(backup, "rime.txt").toPath()); }
            java.nio.file.Files.write(marker.toPath(), ("regenerated " + java.time.Instant.now() + " world=" + name + " movedRegionFiles=" + files + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            getLogger().info("NETHER_REGENERATED epoch=" + REGEN_EPOCH + " world=" + name + " movedRegionFiles=" + files + " backup=plugins/" + getDataFolder().getName() + "/nether-before-v" + REGEN_EPOCH);
        } catch (java.io.IOException | RuntimeException e) {
            getLogger().severe("NETHER_REGENERATE_FAILED reason=" + e.getClass().getSimpleName() + ": " + safe(e.getMessage()) + " -- the old Nether stays");
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        worldName = getConfig().getString("world", "world_nether");
        try {
            File override = new File(getDataFolder(), "blocks.tsv");
            try (java.io.InputStream in = override.exists() ? new java.io.FileInputStream(override) : getResource("blocks.tsv")) { BlockMap.load(in); }
            Blocks.load();
            getLogger().info("NETHER_BLOCK_TABLE rows=" + BlockMap.rows + " source=" + (override.exists() ? "plugins/JasprNether/blocks.tsv" : "bundled"));
        } catch (Throwable t) {
            genDisabled = true;
            getLogger().severe("NETHER_BLOCK_TABLE_FAILED reason=" + t.getClass().getSimpleName() + ": " + safe(t.getMessage()) + " -- generation disabled");
        }
        structures = new StructureOps(this);
        effects = new Effects(this);
        mobs = new Mobs(this);
        mechanics = new Mechanics(this);
        crafting = new Crafting(this);
        boss = new Boss(this);
        fireflies = new Fireflies(this);
        garrisons = new Garrisons(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(garrisons, this);
        Bukkit.getPluginManager().registerEvents(mobs, this);
        Bukkit.getPluginManager().registerEvents(mechanics, this);
        Bukkit.getPluginManager().registerEvents(crafting, this);
        Bukkit.getPluginManager().registerEvents(boss, this);
        Bukkit.getPluginManager().registerEvents(effects, this);
        crafting.register();
        for (World w : Bukkit.getWorlds()) attach(w);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::slowTick, 40L, 100L);
        Bukkit.getScheduler().runTask(this, this::ready); // after the worlds exist
        if (Boolean.getBoolean("jaspr.nether.selftest")) Bukkit.getScheduler().runTaskLater(this, () -> new SelfTest(this).run(), 60L);
    }

    private boolean readyLogged;
    private void ready() {
        if (readyLogged) return;
        readyLogged = true;
        int mobKinds = Mobs.netherExKinds() + 1; // NetherEx mobs + Ghast Queen + BetterNether firefly swarms
        getLogger().info("NETHER_READY version=" + VERSION + " biomes=" + Biomes.BIOME_COUNT + " mobs=" + mobKinds
            + " structures=" + (Gen.TEMPLATE_NAMES.length + 1) + " mega=" + Mega.Kind.values().length + " wonders=8 items=" + Items.DEFS.size()
            + " blocks=" + BlockMap.rows + " world=" + worldName + " attached=" + (gen != null) + " disabled=" + genDisabled
            + " megaComplete=" + (gen != null && gen.megaComplete));
    }

    @Override public void onDisable() {
        if (mobs != null) mobs.shutdown();
        if (boss != null) boss.shutdown();
        if (registry != null) registry.close();
        getLogger().info("NETHER_STOPPED populated=" + populated + " failures=" + failures);
    }

    // ---- world attachment ------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent e) { attach(e.getWorld()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent e) {
        attach(e.getWorld());
        if (isNether(e.getWorld())) Bukkit.getScheduler().runTask(this, () -> removeOuterRealms(e.getWorld()));
    }

    boolean isNether(World w) { return w != null && w.getEnvironment() == World.Environment.NETHER && w.getName().equals(worldName); }

    private void attach(World w) {
        if (!isNether(w)) return;
        if (genDisabled && gen == null && BlockMap.rows == 0) return;
        removeOuterRealms(w);
        if (gen != null && nether == w) return;
        try {
            nether = w;
            registry = new Registry(new File(getDataFolder(), "data" + File.separator + w.getName()), getLogger());
            gen = new Gen(this, w, registry);
            gen.megaComplete = megaComplete(w);
            boolean present = false;
            for (BlockPopulator p : w.getPopulators()) if (p instanceof NetherPopulator) present = true;
            if (!present) w.getPopulators().add(new NetherPopulator());
            Bukkit.getScheduler().runTask(this, () -> applySpawnLimits(w));
            getLogger().info("NETHER_ATTACHED world=" + w.getName() + " templates=" + gen.templates.size() + " registry=" + registry.size());
        } catch (Throwable t) {
            gen = null;
            genDisabled = true;
            getLogger().severe("NETHER_ATTACH_FAILED world=" + w.getName() + " reason=" + t.getClass().getSimpleName() + ": " + safe(t.getMessage()));
        }
    }

    /**
     * Whether every chunk of this Nether is populated by a JasprNether that plans mega structures: true when the world
     * had no region files when this version first attached (the regenerated Nether), remembered in a marker file.
     */
    private boolean megaComplete(World w) {
        File marker = new File(getDataFolder(), "data" + File.separator + w.getName() + File.separator + "mega-complete.txt");
        if (marker.exists()) return true;
        File region = new File(new File(w.getWorldFolder(), "DIM-1"), "region");
        String[] files = region.isDirectory() ? region.list((dir, n) -> n.endsWith(".mca")) : null;
        if (files != null && files.length > 0) {
            getLogger().info("NETHER_MEGA_HISTORY complete=false regionFiles=" + files.length + " -- mega sites only where their centre is new land");
            return false;
        }
        try {
            marker.getParentFile().mkdirs();
            java.nio.file.Files.write(marker.toPath(), ("fresh Nether at " + java.time.Instant.now() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            getLogger().warning("NETHER_MEGA_HISTORY_WRITE_FAILED reason=" + e.getClass().getSimpleName());
        }
        getLogger().info("NETHER_MEGA_HISTORY complete=true -- every mega site is built");
        return true;
    }

    /** JasperCraft's other Nether populator (JasprHorrorBiomes OuterRealms) is removed from the Nether by class name. */
    void removeOuterRealms(World w) {
        if (!isNether(w)) return;
        Iterator<BlockPopulator> it = w.getPopulators().iterator();
        while (it.hasNext()) {
            BlockPopulator p = it.next();
            if (p.getClass().getName().equals(OUTER_REALMS)) {
                it.remove();
                outerRealmsRemoved++;
                getLogger().info("NETHER_OUTER_REALMS_REMOVED world=" + w.getName() + " count=" + outerRealmsRemoved);
            }
        }
    }

    private void applySpawnLimits(World w) {
        int base = Bukkit.getMonsterSpawnLimit();
        int scaled = (int) Math.round(base * getConfig().getDouble("difficulty.density-multiplier", 1.5));
        w.setMonsterSpawnLimit(scaled);
        getLogger().info("NETHER_SPAWN_LIMIT world=" + w.getName() + " monsters=" + scaled + " base=" + base);
    }

    final class NetherPopulator extends BlockPopulator {
        @Override public void populate(World w, Random random, Chunk chunk) {
            if (genDisabled || gen == null || w != nether) return;
            for (BlockPopulator p : w.getPopulators()) if (p.getClass().getName().equals(OUTER_REALMS)) {
                Bukkit.getScheduler().runTask(NetherPlugin.this, () -> removeOuterRealms(w));
                break;
            }
            long t0 = System.nanoTime();
            try {
                int n = gen.populate(chunk.getX(), chunk.getZ());
                long dt = System.nanoTime() - t0;
                populated++;
                totalNanos += dt;
                maxNanos = Math.max(maxNanos, dt);
                blocksWritten += n;
                consecutiveFailures = 0;
                if (populated % 500 == 0)
                    getLogger().info("NETHER_GEN_STATS chunks=" + populated + " avgMs=" + fmt(avgMs()) + " maxMs=" + fmt(maxNanos / 1e6)
                        + " blocksPerChunk=" + (blocksWritten / populated) + " failures=" + failures + " phaseMs[" + gen.phases(populated) + "]");
            } catch (Throwable t) {
                failures++;
                consecutiveFailures++;
                if (warnings++ < 20)
                    getLogger().warning("NETHER_GEN_FAILED chunk=" + chunk.getX() + "," + chunk.getZ() + " reason="
                        + t.getClass().getSimpleName() + ": " + safe(t.getMessage()) + " at=" + where(t));
                if (consecutiveFailures >= 5 || failures >= 40) {
                    genDisabled = true;
                    getLogger().severe("NETHER_GEN_DISABLED failures=" + failures + " consecutive=" + consecutiveFailures
                        + " -- Nether chunks now generate as plain vanilla terrain until the plugin is fixed and restarted");
                }
            }
        }
    }

    static String safe(String s) {
        if (s == null) return "-";
        s = s.replaceAll("[\\r\\n]", " ");
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    static String where(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) if (e.getClassName().startsWith("chat.jaspr.nether")) return e.getClassName().replace("chat.jaspr.nether.", "") + ":" + e.getLineNumber();
        return "-";
    }

    double avgMs() { return populated == 0 ? 0 : totalNanos / 1e6 / populated; }
    static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.2f", d); }

    // ---- ticking -----------------------------------------------------------------------------------------------------
    private long ticks;
    private void tick() {
        ticks++;
        try {
            effects.tick(ticks);
            mobs.tick(ticks);
            boss.tick(ticks);
            garrisons.tick(ticks);
            if ((ticks & 3) == 0) mechanics.tick(ticks);
            if ((ticks % 5) == 0) fireflies.tick(ticks);
        } catch (Throwable t) {
            if (warnings++ < 40) getLogger().warning("NETHER_TICK_FAILED reason=" + t.getClass().getSimpleName() + ": " + safe(t.getMessage()) + " at=" + where(t));
        }
    }

    private void slowTick() {
        if (nether != null) removeOuterRealms(nether);
        if (registry != null) registry.flush();
        mobs.slowTick();
    }

    // ---- commands ------------------------------------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWhere(PlayerCommandPreprocessEvent e) {
        String m = e.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        if (!(m.equals("/where") || m.startsWith("/where ") || m.equals("/biome") || m.equals("/survey"))) return;
        Player p = e.getPlayer();
        if (!isNether(p.getWorld()) || gen == null) return;
        Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline()) for (String line : whereLines(p.getLocation())) p.sendMessage(line); });
    }

    java.util.List<String> whereLines(Location l) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        out.add(ChatColor.GOLD + "Nether biome " + ChatColor.WHITE + gen.biomes.describe(x, y, z));
        Registry.Entry s = registry.structureAt(x, y, z);
        if (s != null) {
            String name, origin;
            if (s.type.equals("mega")) { Mega.Kind k = Mega.Kind.byId(s.name); name = k == null ? pretty(s.name) : k.display; origin = "JasperCraft"; }
            else if (s.type.equals("wonder")) { name = Wonders.display(s.name); origin = "JasperCraft"; }
            else { name = pretty(s.name); origin = s.type.equals("bn") || s.type.equals("city") ? "BetterNether" : "NetherEx"; }
            out.add(ChatColor.GOLD + "Nether structure " + ChatColor.WHITE + name + ChatColor.GRAY + " (" + origin + ")");
        }
        return out;
    }

    // ---- players who log in inside rock or lava -------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (isNether(p.getWorld())) Bukkit.getScheduler().runTaskLater(this, () -> rescue(p), 10L);
    }

    int rescued;

    /** A player who logs in inside rock or lava (the Nether was regenerated around them) is moved to the nearest safe floor. */
    void rescue(Player p) {
        if (!p.isOnline() || p.isDead() || !isNether(p.getWorld())) return;
        Location l = p.getLocation();
        org.bukkit.block.Block feet = l.getBlock(), head = feet.getRelative(0, 1, 0);
        boolean stuck = feet.getType().isOccluding() || head.getType().isOccluding() || feet.isLiquid() || head.isLiquid();
        if (!stuck) return;
        int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
        for (int r = 0; r <= 32; r += 2) for (int i = -r; i <= r; i += 2) for (int k = 0; k < 4; k++) {
            if (r > 0 && Math.abs(i) == r && k > 0) continue;
            int x = bx + (k == 0 ? i : k == 1 ? r : k == 2 ? -i : -r), z = bz + (k == 0 ? -r : k == 1 ? i : k == 2 ? r : -i);
            Location to = Navigator.safe(p.getWorld(), x, z, by + 20, by - 20);
            if (to == null || to.getBlock().isLiquid()) continue;
            to.setYaw(l.getYaw());
            p.teleport(to);
            rescued++;
            getLogger().info("NETHER_RESCUED cause=join moved=" + (to.getBlockX() - bx) + "," + (to.getBlockY() - by) + "," + (to.getBlockZ() - bz));
            return;
        }
        feet.setType(org.bukkit.Material.AIR);
        head.setType(org.bukkit.Material.AIR);
        if (!feet.getRelative(0, -1, 0).getType().isSolid()) feet.getRelative(0, -1, 0).setType(org.bukkit.Material.NETHERRACK);
        rescued++;
        getLogger().info("NETHER_RESCUED cause=join moved=0,0,0 carved=true");
    }

    static String pretty(String n) {
        StringBuilder b = new StringBuilder();
        for (String part : n.split("_")) { if (part.isEmpty()) continue; if (b.length() > 0) b.append(' '); b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); }
        return b.toString();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("jnether")) return false;
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "where": {
                if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
                Player p = (Player) sender;
                if (!isNether(p.getWorld()) || gen == null) { sender.sendMessage(ChatColor.GRAY + "Not in the JasprNether world."); return true; }
                Location l = p.getLocation();
                Biomes.Nex n = gen.biomes.nex(l.getBlockX(), l.getBlockZ());
                sender.sendMessage(ChatColor.GOLD + "NetherEx region: " + ChatColor.WHITE + n.display);
                if (n == Biomes.Nex.HELL) sender.sendMessage(ChatColor.GOLD + "BetterNether biome: " + ChatColor.WHITE + gen.biomes.bn(l.getBlockX(), l.getBlockY(), l.getBlockZ()).display);
                for (String line : whereLines(l)) sender.sendMessage(line);
                Cities.City c = gen.cities.nearest(l.getBlockX() >> 4, l.getBlockZ() >> 4);
                sender.sendMessage(ChatColor.GRAY + "Grid-cell city centre: " + c.x + ", 40, " + c.z);
                return true;
            }
            case "status": {
                sender.sendMessage(ChatColor.GOLD + "JasprNether " + VERSION + ChatColor.GRAY + " world=" + worldName + " attached=" + (gen != null)
                    + " disabled=" + genDisabled);
                sender.sendMessage(ChatColor.GRAY + "chunks=" + populated + " avgMs=" + fmt(avgMs()) + " maxMs=" + fmt(maxNanos / 1e6)
                    + " failures=" + failures + " registry=" + (registry == null ? 0 : registry.size()));
                if (gen != null) sender.sendMessage(ChatColor.GRAY + "phaseMs " + gen.phases(populated));
                sender.sendMessage(ChatColor.GRAY + "mobs tracked=" + mobs.tracked() + " spawned=" + mobs.spawnedTotal + " effects=" + effects.active()
                    + " boss=" + boss.describe());
                sender.sendMessage(ChatColor.GRAY + "garrisons roused=" + garrisons.rousedTotal + " spawned=" + garrisons.spawnedTotal
                    + " peaceKept=" + mobs.peaceKept + " journals=" + structures.journals + " rescued=" + rescued
                    + " megaComplete=" + (gen != null && gen.megaComplete));
                if (gen != null) sender.sendMessage(ChatColor.GRAY + "placed " + gen.placed);
                return true;
            }
            default:
                if (!sender.hasPermission("jaspr.nether.admin")) { sender.sendMessage(ChatColor.RED + "Admins only."); return true; }
                return adminCommand(sender, sub, args);
        }
    }

    private boolean adminCommand(CommandSender sender, String sub, String[] args) {
        Player p = sender instanceof Player ? (Player) sender : null;
        switch (sub) {
            case "spawn": {
                if (p == null || args.length < 2) { sender.sendMessage("/jnether spawn <kind> [elite]"); return true; }
                boolean elite = args.length > 2 && args[2].equalsIgnoreCase("elite");
                Object m = mobs.spawn(args[1], p.getLocation().add(p.getLocation().getDirection().multiply(3)), elite);
                sender.sendMessage(m == null ? ChatColor.RED + "Unknown kind. Kinds: " + Mobs.KINDS.keySet() : ChatColor.GREEN + "Spawned " + args[1]);
                return true;
            }
            case "give": {
                if (p == null || args.length < 2) { sender.sendMessage("/jnether give <item> [n]"); return true; }
                if (!Items.DEFS.containsKey(args[1])) { sender.sendMessage(ChatColor.RED + "Items: " + Items.DEFS.keySet()); return true; }
                p.getInventory().addItem(Items.create(args[1], args.length > 2 ? Integer.parseInt(args[2]) : 1));
                return true;
            }
            case "effect": {
                if (p == null || args.length < 2) { sender.sendMessage("/jnether effect <frozen|frostbitten|infested|fire_burning|soul_sucked|crying> [ticks]"); return true; }
                effects.apply(p, Effects.Kind.valueOf(args[1].toUpperCase(java.util.Locale.ROOT)), args.length > 2 ? Integer.parseInt(args[2]) : 200);
                return true;
            }
            case "queen": {
                if (p == null) return true;
                boss.summon(p.getLocation().add(0, 8, 0), null);
                return true;
            }
            case "selftest":
                // The self-test builds test blocks and summons a Ghast Queen: never on a live server by accident.
                if (!Boolean.getBoolean("jaspr.nether.selftest")) { sender.sendMessage(ChatColor.RED + "Only on a test server started with -Djaspr.nether.selftest=true"); return true; }
                new SelfTest(this).run();
                return true;
            case "mobs":
                sender.sendMessage(ChatColor.GRAY + "tracked " + mobs.countsByKind());
                sender.sendMessage(ChatColor.GRAY + "abilities " + mobs.abilityCounts);
                sender.sendMessage(ChatColor.GRAY + "mechanics thorn=" + mechanics.thornHits + " egg=" + mechanics.eggPoisons + " cactus=" + mechanics.cactusHits
                    + " blueFire=" + mechanics.blueBurns + " rime=" + mechanics.rimeFreezes + " arctic=" + mechanics.arcticFreezes + " ores=" + mechanics.oreDrops
                    + " nethermites=" + mechanics.nethermites + " effectsApplied=" + effects.applied + " replacedSpawns=" + mobs.replaced + " elites=" + mobs.elites
                    + " fireflySwarms=" + fireflies.swarmsSpawned + " brewed=" + crafting.brewed);
                return true;
            case "goto": {
                if (args.length >= 3) p = Bukkit.getPlayerExact(args[2]);   // /jnether goto <target> <player> (console, tests)
                if (p == null || args.length < 2 || gen == null) { sender.sendMessage("/jnether goto <biome|city|shrine|village|bn|mega|wonder|golden_bazaar|soul_pyramid|cinder_forge|spore_cathedral|frozen_citadel>"); return true; }
                Location from = isNether(p.getWorld()) ? p.getLocation() : new Location(nether, 0, 64, 0);
                Location to = Navigator.find(this, from, args[1].toLowerCase(java.util.Locale.ROOT));
                if (to == null) { sender.sendMessage(ChatColor.RED + "Nothing found for " + args[1]); return true; }
                p.teleport(to);
                sender.sendMessage(ChatColor.GREEN + "Teleported " + p.getName() + " to " + args[1] + " at " + to.getBlockX() + " " + to.getBlockY() + " " + to.getBlockZ());
                if (args[1].equalsIgnoreCase("urn") || args[1].equalsIgnoreCase("statue")) {
                    Registry.Entry u = registry.near(to.getBlockX(), to.getBlockZ(), 4, args[1].toLowerCase(java.util.Locale.ROOT)).stream().findFirst().orElse(null);
                    if (u != null) sender.sendMessage(ChatColor.GREEN + "Point " + u.x1 + " " + u.y1 + " " + u.z1);
                }
                getLogger().info("NETHER_GOTO target=" + args[1] + " at=" + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ());
                return true;
            }
            case "test": {
                if (p == null || args.length < 2) { sender.sendMessage("/jnether test <wight|coolmar|brute|spore|frost|thorn|egg|bluefire|creeper>"); return true; }
                Navigator.scenario(this, p, args[1].toLowerCase(java.util.Locale.ROOT));
                return true;
            }
            default: sender.sendMessage("/jnether [where|status|mobs|goto|test|spawn|give|effect|queen|selftest]"); return true;
        }
    }

    Map<String, Integer> placedCounts() { return gen == null ? java.util.Collections.emptyMap() : gen.placed; }
}
