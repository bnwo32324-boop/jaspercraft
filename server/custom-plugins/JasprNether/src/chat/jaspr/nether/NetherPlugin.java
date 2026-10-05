package chat.jaspr.nether;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
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
 * mobs and the Ghast Queen coexist. JasperCraft adds five mega structures (Mega), their garrisons and the wonders, and
 * the owner's 98 GLM structures (GlmSites) with their creatures (Fiends), the ten Nether Lords (Lords) and their loot.
 * Everything the browser client sees is vanilla.
 */
public final class NetherPlugin extends JavaPlugin implements Listener {
    static final String VERSION = "1.5.0";
    /**
     * Regeneration epoch. Raising it regenerates the Nether once more on the next start (v1 2026-09-26: the port;
     * v2 2026-09-28: the owner asked for a fresh Nether with the mega structures and wonders; v3 2026-09-29: the owner
     * asked for the GLM structures, their creatures and the Nether Lords, "and then, once you're done, regenerate the Nether";
     * v4 2026-10-01: the owner removed N094, The Sulphur Sewers, and requested a fresh Nether;
     * v5 2026-10-01: the owner requested a fresh Nether with the audited crops, corrected stairs and doubled GLM density;
     * v6 2026-10-04: the owner asked for Nether structures "3x common" and "regenerate the Nether after applying the change":
     * the per-chunk structures, villages and wonders are three times as frequent; the GLM builds, megas and colossi keep
     * their layout, which already covers about 41% of the land - packing the GLM grid to its fitting limit added only 7%
     * more builds and lost half the megas);
     * v7 2026-10-04: the owner asked for ten new colossal structures ("I want them to be huge structures. All this update is
     * just about big, big, big structures"), each with its Lord and every hostile creature of the Nether: the colossi now
     * stand on an 896-block grid (fourteen slots, the Pyramid and the Citadel two each) and the GLM builds and mega
     * structures plan around them.
     */
    static final int REGEN_EPOCH = 7;
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
    NetherQuest quest;
    GuideKit guide;
    Lords lords;
    GlmLife glmLife;
    Relics relics;
    Ordeals ordeals;
    Spoils spoils;

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
            // Fortress/village metadata belongs to the discarded terrain, not the fresh dimension.
            File worldData = new File(new File(Bukkit.getWorldContainer(), name), "data");
            if (worldData.isDirectory()) { backup.mkdirs(); java.nio.file.Files.move(worldData.toPath(), new File(backup, "world-data").toPath()); }
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
        lords = new Lords(this);
        glmLife = new GlmLife(this);
        relics = new Relics(this);
        ordeals = new Ordeals(this);
        spoils = new Spoils(this);
        quest = new NetherQuest(this);
        guide = new GuideKit(this, quest);
        Bukkit.getPluginManager().registerEvents(quest, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(garrisons, this);
        Bukkit.getPluginManager().registerEvents(mobs, this);
        Bukkit.getPluginManager().registerEvents(mechanics, this);
        Bukkit.getPluginManager().registerEvents(crafting, this);
        Bukkit.getPluginManager().registerEvents(boss, this);
        Bukkit.getPluginManager().registerEvents(lords, this);
        Bukkit.getPluginManager().registerEvents(glmLife, this);
        Bukkit.getPluginManager().registerEvents(relics, this);
        Bukkit.getPluginManager().registerEvents(ordeals, this);
        Bukkit.getPluginManager().registerEvents(spoils, this);
        Bukkit.getPluginManager().registerEvents(effects, this);
        crafting.register();
        DimensionTravel.register(this);
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
        getLogger().info("NETHER_READY version=" + VERSION + " biomes=" + Biomes.BIOME_COUNT + " mobs=" + mobKinds + " fiends=" + Mobs.fiendKinds()
            + " lords=" + Lords.DEFS.size() + " glm=" + (gen == null ? 0 : gen.glm.activeSize())
            + " glmCatalog=" + (gen == null ? 0 : gen.glm.size())
            + " glmDensity=2.0 glmLayout=2 smallStructures=3x tpd=creative"
            + " structures=" + (Gen.TEMPLATE_NAMES.length + 1) + " mega=" + Mega.Kind.values().length + " wonders=8 items=" + Items.DEFS.size()
            + " blocks=" + BlockMap.rows + " world=" + worldName + " attached=" + (gen != null) + " disabled=" + genDisabled
            + " megaComplete=" + (gen != null && gen.megaComplete)
            + " dwellers=" + Mobs.dwellerKinds() + " colossi=" + Colossi.Kind.values().length
            + " catacombs=" + (gen != null && gen.depths != null ? gen.depths.hx + "," + gen.depths.hz : "-")
            + " history=" + (gen == null ? "-" : gen.history.fresh ? "fresh" : gen.history.chunks + "chunks"));
    }

    @Override public void onDisable() {
        if (mobs != null) mobs.shutdown();
        if (boss != null) boss.shutdown();
        if (lords != null) lords.shutdown();
        if (ordeals != null) ordeals.shutdown();
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

    private java.lang.ref.WeakReference<World> lightWorld = new java.lang.ref.WeakReference<>(null);

    /**
     * The Nether is lit by lava, glowstone and fire, not the sky. With Spigot's random-light-updates off (the server default) a
     * new chunk is sent before its light is worked out, so lava seas and the structures arrive dark until something sends the
     * chunk again; for this world alone the server waits for the light first, as the game itself does. Once per world object.
     */
    private void lightBeforeSending(World w) {
        if (lightWorld.get() == w) return;
        lightWorld = new java.lang.ref.WeakReference<>(w);
        String mode = "wait";
        try {
            ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle().spigotConfig.randomLightUpdates = true;
        } catch (Throwable t) {
            mode = "unavailable";
            getLogger().warning("NETHER_LIGHT mode=unavailable world=" + w.getName() + " error=" + t.getClass().getSimpleName());
        }
        getLogger().info("NETHER_LIGHT mode=" + mode + " world=" + w.getName());
    }

    private void attach(World w) {
        if (!isNether(w)) return;
        lightBeforeSending(w);   // before the first chunk is sent, whether or not the generator attaches
        if (genDisabled && gen == null && BlockMap.rows == 0) return;
        removeOuterRealms(w);
        if (gen != null && nether == w) return;
        try {
            nether = w;
            registry = new Registry(new File(getDataFolder(), "data" + File.separator + w.getName()), getLogger());
            gen = new Gen(this, w, registry);
            gen.megaComplete = megaComplete(w);
            // the colossal structures and the Endless Catacombs (2026-10-01): never drawn into land that already exists
            File data = new File(getDataFolder(), "data" + File.separator + w.getName());
            gen.history = History.of(w, data, getLogger());
            int[] heart = catacombs(data, gen);
            final Gen g = gen;
            gen.depths = new Depths(gen.seed, heart[0], heart[1], gen.history, new Depths.Claims() {
                @Override public boolean deep(int x0, int z0, int x1, int z1) { return g.deepClaim(x0, z0, x1, z1); }
                @Override public boolean above(int x0, int z0, int x1, int z1) { return g.shaftClaim(x0, z0, x1, z1); }
            });
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

    /** Where the Endless Catacombs' Heart lies: chosen once (on land newer than the colossal update) and kept. */
    private int[] catacombs(File data, Gen g) {
        File f = new File(data, "catacombs.txt");
        try {
            if (f.exists()) for (String line : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String[] p = line.trim().split(" ");
                if (p.length == 3 && p[0].equals("heart")) {
                    int[] h = {Integer.parseInt(p[1]), Integer.parseInt(p[2])};
                    getLogger().info("NETHER_CATACOMBS heart=" + h[0] + "," + h[1] + " source=file");
                    return h;
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            getLogger().warning("NETHER_CATACOMBS_READ_FAILED reason=" + e.getClass().getSimpleName());
        }
        int[] h = Depths.chooseHeart(g.seed, g.history, g::builtDeep);
        try {
            data.mkdirs();
            java.nio.file.Files.write(f.toPath(), ("# The Endless Catacombs (JasprNether " + VERSION + ")\nheart " + h[0] + " " + h[1] + "\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            getLogger().warning("NETHER_CATACOMBS_WRITE_FAILED reason=" + e.getClass().getSimpleName());
        }
        getLogger().info("NETHER_CATACOMBS heart=" + h[0] + "," + h[1] + " source=chosen");
        return h;
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
            lords.tick(ticks);
            garrisons.tick(ticks);
            glmLife.tick(ticks);
            ordeals.tick(ticks);
            if ((ticks % 20) == 3) relics.tick(ticks);
            if ((ticks % 20) == 11) spoils.tick();
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
            else if (s.type.equals("colossus")) { Colossi.Kind k = Colossi.Kind.byId(s.name); name = k == null ? pretty(s.name) : k.display; origin = "JasperCraft"; }
            else if (s.type.equals("wonder")) { name = Wonders.display(s.name); origin = "JasperCraft"; }
            else if (s.type.equals("glm")) {
                GlmSites.Entry en = gen.glm.entry(s.name);
                name = en == null ? s.name : en.title;
                origin = en != null && en.lord != null ? "stronghold of " + Lords.DEFS.get(en.lord).name : "GLM";
            }
            else { name = pretty(s.name); origin = s.type.equals("bn") || s.type.equals("city") ? "BetterNether" : "NetherEx"; }
            out.add(ChatColor.GOLD + "Nether structure " + ChatColor.WHITE + name + ChatColor.GRAY + " (" + origin + ")");
        }
        if (s == null && gen.depths != null && y <= Depths.TOP + 1 && gen.depths.top(x, z) >= 0) {
            String[] halls = {"the Old Halls", "the Ember Halls", "the Black Halls"};
            out.add(ChatColor.GOLD + "Nether structure " + ChatColor.WHITE + "The Endless Catacombs" + ChatColor.GRAY + " (" + halls[gen.depths.zone(x, z)] + ", JasperCraft)");
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
        if (cmd.getName().equalsIgnoreCase("goals")) return true;   // GuideKit prints each realm's part (it listens for the command)
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
                sender.sendMessage(ChatColor.GRAY + guide.status() + " fonts=" + quest.fonts);
                if (gen != null) sender.sendMessage(ChatColor.GRAY + "glm builds=" + gen.glm.size() + " drawn=" + gen.glm.drawn + " loads=" + gen.glm.loads
                    + " cached=" + gen.glm.cached() + " cacheKb=" + (gen.glm.cachedBytes() >> 10) + " loadMs=" + fmt(gen.glm.loadNanos / 1e6)
                    + " loadFailures=" + gen.glm.loadFailures + " tiles=" + structures.glmTiles + " loot=" + structures.glmLoot);
                sender.sendMessage(ChatColor.GRAY + "lords " + lords.describe() + " slain=" + lords.slainBy + " life " + glmLife.describe() + " relics " + relics.describe());
                sender.sendMessage(ChatColor.GRAY + "ordeals " + ordeals.describe() + " spoils " + spoils.describe()
                    + (gen != null && gen.depths != null ? " catacombs heart=" + gen.depths.hx + "," + gen.depths.hz + " columns=" + gen.depths.drawnColumns
                    + " shafts=" + gen.depths.shafts + " capped=" + gen.depths.capped : ""));
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
                // /jnether spawn <kind> [elite|normal] [player] (a player name lets the console spawn beside someone)
                if (args.length > 3) p = Bukkit.getPlayerExact(args[3]);
                if (p == null || args.length < 2) { sender.sendMessage("/jnether spawn <kind> [elite|normal] [player]"); return true; }
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
            case "guide": {
                // /jnether guide [player]: hand them the three items and stand a Nether Guide beside them
                Player to = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : p;
                if (to == null) { sender.sendMessage("/jnether guide [player]"); return true; }
                guide.offer(to, true);
                if (isNether(to.getWorld())) guide.ensureGuide(to.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Gave the Nether compass, checklist and map to " + to.getName());
                return true;
            }
            case "quest": {
                Player of = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : p;
                if (of == null) { sender.sendMessage("/jnether quest [player]"); return true; }
                for (String line : guide.goalLines(of)) sender.sendMessage(line);
                return true;
            }
            case "queen": {
                if (p == null) return true;
                boss.summon(p.getLocation().add(0, 8, 0), null);
                return true;
            }
            case "lord": {
                // /jnether lord <id> [player]: raise a Nether Lord beside a player (tests); its arena is where it rose
                if (args.length > 2) p = Bukkit.getPlayerExact(args[2]);
                if (p == null || args.length < 2 || !Lords.DEFS.containsKey(args[1])) { sender.sendMessage("/jnether lord <" + String.join("|", Lords.DEFS.keySet()) + "> [player]"); return true; }
                Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(8));
                if (Lords.DEFS.get(args[1]).flies) at.add(0, 6, 0);
                Object l = lords.rise(p.getWorld(), Lords.DEFS.get(args[1]), at.getBlockX(), at.getBlockY(), at.getBlockZ());
                sender.sendMessage(l == null ? ChatColor.RED + "Could not raise " + args[1] : ChatColor.GREEN + "Raised " + args[1]);
                return true;
            }
            case "lords": {
                for (Lords.Fight f : lords.all()) {
                    Location at = f.e.getLocation();
                    sender.sendMessage(ChatColor.GRAY + f.d.id + " at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + " home " + f.hx + " " + f.hy + " " + f.hz
                        + " hp " + (int) f.e.getHealth() + "/" + (int) f.e.getMaxHealth() + " phase " + f.phase + " fought " + f.fought.size());
                    getLogger().info("NETHER_LORD_STATE lord=" + f.d.id + " at=" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " home=" + f.point
                        + " hp=" + (int) f.e.getHealth() + " phase=" + f.phase + " ai=" + f.e.hasAI() + " " + lords.debug(f));
                }
                sender.sendMessage(ChatColor.GRAY + lords.describe() + " abilities " + mobs.abilityCounts);
                return true;
            }
            case "conquer": {
                // /jnether conquer <id|all|none> [player]: set a player's Lord credit (tests and support)
                if (args.length > 2) p = Bukkit.getPlayerExact(args[2]);
                if (p == null || args.length < 2) { sender.sendMessage("/jnether conquer <lord|all|none> [player]"); return true; }
                if (args[1].equals("none")) { for (String id : Lords.DEFS.keySet()) p.removeScoreboardTag("jn_lord_" + id); }
                else if (args[1].equals("all")) { for (String id : Lords.COUNTED) p.addScoreboardTag("jn_lord_" + id); }
                else if (Lords.DEFS.containsKey(args[1])) p.addScoreboardTag("jn_lord_" + args[1]);
                sender.sendMessage(ChatColor.GREEN + p.getName() + " has conquered " + Lords.conquered(p));
                getLogger().info("NETHER_LORD_CONQUER_SET player=" + p.getUniqueId() + " lords=" + Lords.conquered(p));
                return true;
            }
            case "glm": {
                // /jnether glm [trap] [player]: the GLM build here, or the nearest of each tier; "trap" moves to its nearest trapped chest
                if (args.length > 2) p = Bukkit.getPlayerExact(args[2]);
                if (p == null || gen == null) return true;
                Location l = p.getLocation();
                if (args.length > 1 && args[1].equals("trap")) {
                    GlmSites.Site here = gen.builtGlmAt(l.getBlockX(), l.getBlockZ());
                    GlmBuild b = here == null ? null : (here.source == null ? gen.glm : here.source).build(here.e.key);
                    if (b == null) { sender.sendMessage(ChatColor.RED + "Not in a GLM build"); return true; }
                    List<Location> traps = new ArrayList<>();
                    for (GlmBuild.Tile t : b.tiles) if (t.type == GlmBuild.T_CHEST && t.trapped) {
                        int X = t.x + b.margin, Z = t.z + b.margin;
                        traps.add(new Location(l.getWorld(), here.minX + b.rotU(X, Z, here.rot) + 0.5, here.base() + t.y, here.minZ + b.rotV(X, Z, here.rot) + 0.5));
                    }
                    if (traps.isEmpty()) { sender.sendMessage(ChatColor.RED + "No trapped chest in " + here.e.title); return true; }
                    final Location from = l;
                    traps.sort((u, v) -> Double.compare(u.distanceSquared(from), v.distanceSquared(from)));
                    for (Location best : traps) for (int[] o : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 1}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1},
                        {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}, {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}}) {
                        org.bukkit.block.Block f = best.clone().add(o[0], o[1], o[2]).getBlock();
                        if (f.getType() == org.bukkit.Material.AIR && f.getRelative(0, 1, 0).getType() == org.bukkit.Material.AIR
                            && f.getRelative(0, -1, 0).getType().isSolid()) {
                            Location to = f.getLocation().add(0.5, 0, 0.5);
                            to.setDirection(best.toVector().subtract(to.toVector()).setY(-0.6));
                            p.teleport(to);
                            sender.sendMessage(ChatColor.GREEN + "Trapped chest at " + best.getBlockX() + " " + best.getBlockY() + " " + best.getBlockZ());
                            return true;
                        }
                    }
                    sender.sendMessage(ChatColor.RED + "No trapped chest of " + here.e.title + " has a free side (" + traps.size() + ")");
                    return true;
                }
                GlmSites.Site s = gen.builtGlmAt(l.getBlockX(), l.getBlockZ());
                if (s != null) sender.sendMessage(ChatColor.GOLD + s.e.title + ChatColor.GRAY + " (" + s.e.key + ", " + s.tier + ", " + s.e.theme + ", " + s.region
                    + ") floor=" + s.floor + " rot=" + s.rot + " box=" + s.minX + "," + s.minZ + ".." + s.maxX + "," + s.maxZ);
                for (GlmSites.Tier t : GlmSites.Tier.values()) {
                    GlmSites.Site n = null; double bd = Double.MAX_VALUE;
                    int cx = Math.floorDiv(l.getBlockX(), t.cell), cz = Math.floorDiv(l.getBlockZ(), t.cell);
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        GlmSites.Site c = gen.glm.site(t, cx + dx, cz + dz);
                        if (c != null && c.dist(l.getX(), l.getZ()) < bd) { bd = c.dist(l.getX(), l.getZ()); n = c; }
                    }
                    if (n != null) sender.sendMessage(ChatColor.GRAY + t.name().toLowerCase(java.util.Locale.ROOT) + ": " + n.e.title + " (" + n.e.key + ") at "
                        + n.x + " " + n.floor + " " + n.z + " -- " + (int) bd + " blocks");
                }
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
                    + " fireflySwarms=" + fireflies.swarmsSpawned + " brewed=" + crafting.brewed + " sporeClouds=" + mobs.sporeClouds);
                return true;
            case "goto": {
                if (args.length >= 3) p = Bukkit.getPlayerExact(args[2]);   // /jnether goto <target> <player> (console, tests)
                if (p == null || args.length < 2 || gen == null) { sender.sendMessage("/jnether goto <biome|city|shrine|village|bn|mega|wonder|golden_bazaar|soul_pyramid|cinder_forge|spore_cathedral|frozen_citadel|great_pyramid|caldera_citadel|catacombs|heart|warden|lord id|ordeal:id>"); return true; }
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
            default: sender.sendMessage("/jnether [where|status|mobs|goto|test|spawn|give|effect|queen|lord|conquer|glm|selftest]"); return true;
        }
    }

    Map<String, Integer> placedCounts() { return gen == null ? java.util.Collections.emptyMap() : gen.placed; }
}
