package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

/**
 * In-game self-test (-Djaspr.nether.selftest=true or /jnether selftest): biome distribution, forced generation around
 * every biome with the average population cost, a block census of the stand-ins, every mob kind and its ability,
 * elites, effects, loot, brewing and a full Ghast Queen summon-and-kill. Runs in timed stages so the server never
 * stalls; every result is a NETHER_SELFTEST line and the last line is NETHER_SELFTEST_DONE pass=N fail=M.
 */
final class SelfTest implements Listener {
    private final NetherPlugin plugin;
    private final World w;
    private int pass, fail;
    private final List<long[]> chunks = new ArrayList<>();
    private Location site;
    private final List<ItemStack> queenDrops = new ArrayList<>();
    private boolean queenDied;

    SelfTest(NetherPlugin plugin) { this.plugin = plugin; this.w = plugin.nether; }

    private void check(String name, boolean ok, String detail) {
        if (ok) pass++; else fail++;
        plugin.getLogger().info("NETHER_SELFTEST " + (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " " + detail));
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { r.run(); } catch (Throwable t) {
                check("stage_exception", false, t.getClass().getSimpleName() + ": " + NetherPlugin.safe(t.getMessage()) + " at=" + NetherPlugin.where(t));
            }
        }, ticks);
    }

    void run() {
        if (w == null || plugin.gen == null) { plugin.getLogger().warning("NETHER_SELFTEST_SKIPPED reason=no-nether-world"); return; }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("NETHER_SELFTEST_START world=" + w.getName() + " seed=" + w.getSeed());
        biomes();
    }

    // ---- stage 1: biome map --------------------------------------------------------------------------------------
    private final Map<Biomes.Nex, int[]> nexAt = new EnumMap<>(Biomes.Nex.class);
    private final Map<Biomes.Bn, int[]> bnAt = new EnumMap<>(Biomes.Bn.class);

    private void biomes() {
        Map<Biomes.Nex, Integer> nex = new EnumMap<>(Biomes.Nex.class);
        Map<Biomes.Bn, Integer> bn = new EnumMap<>(Biomes.Bn.class);
        long t0 = System.nanoTime();
        int samples = 0;
        for (int x = -4096; x < 4096; x += 64) for (int z = -4096; z < 4096; z += 64) {
            Biomes.Nex n = plugin.gen.biomes.nex(x, z);
            nex.merge(n, 1, Integer::sum);
            if (!nexAt.containsKey(n) || Math.abs(x) + Math.abs(z) < Math.abs(nexAt.get(n)[0]) + Math.abs(nexAt.get(n)[1])) nexAt.put(n, new int[]{x, z});
            samples++;
            if (n == Biomes.Nex.HELL) for (int y = 40; y <= 100; y += 30) {
                Biomes.Bn b = plugin.gen.biomes.bn(x, y, z);
                bn.merge(b, 1, Integer::sum);
                if (!bnAt.containsKey(b) || Math.abs(x) + Math.abs(z) < Math.abs(bnAt.get(b)[0]) + Math.abs(bnAt.get(b)[1])) bnAt.put(b, new int[]{x, y, z});
            }
        }
        long us = (System.nanoTime() - t0) / 1000;
        check("biome_nex_all_regions", nex.size() == Biomes.Nex.values().length, "samples=" + samples + " " + nex + " us=" + us);
        double hellShare = nex.getOrDefault(Biomes.Nex.HELL, 0) / (double) samples;
        check("biome_nex_weights", hellShare > 0.2 && hellShare < 0.5 && nex.getOrDefault(Biomes.Nex.ARCTIC_ABYSS, 0) < nex.getOrDefault(Biomes.Nex.HELL, 0),
            "hell=" + NetherPlugin.fmt(hellShare) + " expected~0.33");
        check("biome_bn_all", bn.size() == Biomes.Bn.values().length, bn.toString());
        // vertical stacking: some Hell columns change BetterNether biome between y 40 and y 100
        int stacked = 0, cols = 0;
        for (int x = -2048; x < 2048; x += 97) for (int z = -2048; z < 2048; z += 97) {
            if (plugin.gen.biomes.nex(x, z) != Biomes.Nex.HELL) continue;
            cols++;
            if (plugin.gen.biomes.bn(x, 40, z) != plugin.gen.biomes.bn(x, 100, z)) stacked++;
        }
        check("biome_bn_vertical_stacking", stacked > 0, "columns=" + cols + " stacked=" + stacked);
        later(5, this::generate);
    }

    // ---- stage 2: forced generation ------------------------------------------------------------------------------------
    private void generate() {
        java.util.LinkedHashSet<Long> want = new java.util.LinkedHashSet<>();
        addSquare(want, 0, 0, 10);
        for (int[] p : nexAt.values()) addSquare(want, p[0] >> 4, p[1] >> 4, 6);
        for (int[] p : bnAt.values()) addSquare(want, p[0] >> 4, p[2] >> 4, 4);
        Cities.City c = plugin.gen.cities.city(0, 0);
        addSquare(want, c.chunkX, c.chunkZ, 6);
        for (long k : want) chunks.add(new long[]{k >> 32, (int) k});
        plugin.getLogger().info("NETHER_SELFTEST generating chunks=" + chunks.size() + " city=" + c.x + "," + c.z + " parts=" + c.parts.size());
        long before = plugin.populated;
        loadBatch(0, before, System.currentTimeMillis());
    }

    private static void addSquare(java.util.Set<Long> s, int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) s.add(((long) x << 32) | (z & 0xffffffffL));
    }

    private void loadBatch(int from, long before, long start) {
        int to = Math.min(chunks.size(), from + 24);
        for (int i = from; i < to; i++) w.loadChunk((int) chunks.get(i)[0], (int) chunks.get(i)[1], true);
        if (to < chunks.size()) { later(1, () -> loadBatch(to, before, start)); return; }
        long n = plugin.populated - before;
        check("gen_populated", n > chunks.size() / 2, "chunks=" + n + " avgMs=" + NetherPlugin.fmt(plugin.avgMs()) + " maxMs="
            + NetherPlugin.fmt(plugin.maxNanos / 1e6) + " blocksPerChunk=" + (plugin.populated == 0 ? 0 : plugin.blocksWritten / plugin.populated)
            + " wallMs=" + (System.currentTimeMillis() - start) + " phaseMs[" + plugin.gen.phases(plugin.populated) + "] bnCacheHit="
            + NetherPlugin.fmt(plugin.gen.biomes.cacheHits / (double) Math.max(1, plugin.gen.biomes.cacheHits + plugin.gen.biomes.cacheMisses)));
        check("gen_cost", plugin.avgMs() < 25, "avgMs=" + NetherPlugin.fmt(plugin.avgMs()));
        check("gen_no_failures", plugin.failures == 0 && !plugin.genDisabled, "failures=" + plugin.failures);
        later(5, this::census);
    }

    // ---- stage 3: census of stand-ins ------------------------------------------------------------------------------
    private void census() {
        Map<String, Integer> blocks = new TreeMap<>();
        int[] watch = {Blocks.THORNSTALK, Blocks.EGG_PLANT, Blocks.FROSTBURN_ICE, Blocks.GLOOMY_NETHERRACK, Blocks.FIERY_NETHERRACK, Blocks.LIVELY_NETHERRACK,
            Blocks.ICY_NETHERRACK, Blocks.BASALT, Blocks.HYPHAE, Blocks.AMETHYST_ORE, Blocks.RIME_ORE, Blocks.CINCINNASITE_ORE, Blocks.NETHERRACK_MOSS,
            Blocks.NETHER_REED, Blocks.SMOKER, Blocks.STALAGNATE_MIDDLE, Blocks.LUCIS_CENTER, Blocks.EYE_VINE, Blocks.BLACK_BUSH, Blocks.INK_BUSH,
            Blocks.BLACK_APPLE, Blocks.RED_MOLD, Blocks.GRAY_MOLD, Blocks.ENOKI_STEM, Blocks.ELDER_STEM, Blocks.NETHER_CACTUS, Blocks.BARREL_CACTUS,
            Blocks.AGAVE, Blocks.MAGMA_FLOWER, Blocks.ICHOR, (Blocks.MAGMA << 4), (Blocks.GLOWSTONE << 4), (Blocks.GRAVEL << 4)};
        String[] names = {"thornstalk", "egg_plant", "frostburn_ice", "gloomy_netherrack", "fiery_netherrack", "lively_netherrack", "icy_netherrack",
            "basalt", "hyphae/mycelium", "amethyst_ore", "rime_ore", "cincinnasite_ore", "netherrack_moss", "nether_reed", "smoker", "stalagnate",
            "lucis/glowstone", "eye_vine", "black_bush", "ink_bush", "black_apple", "red_mold", "gray_mold", "enoki_stem", "elder_stem", "nether_cactus",
            "barrel_cactus", "agave", "magma_flower", "ichor", "magma", "glowstone", "gravel"};
        int[] counts = new int[watch.length];
        int scanned = 0;
        for (long[] c : chunks) {
            ChunkSnapshot s = w.getChunkAt((int) c[0], (int) c[1]).getChunkSnapshot(false, false, false);
            scanned++;
            for (int y = 1; y < 127; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int v = (s.getBlockTypeId(x, y, z) << 4) | s.getBlockData(x, y, z);
                for (int i = 0; i < watch.length; i++) if (watch[i] == v) { counts[i]++; break; }
            }
        }
        for (int i = 0; i < names.length; i++) blocks.put(names[i], counts[i]);
        plugin.getLogger().info("NETHER_SELFTEST census chunks=" + scanned + " " + blocks);
        plugin.getLogger().info("NETHER_SELFTEST placed " + plugin.gen.placed);
        String[] mustExist = {"thornstalk", "frostburn_ice", "gloomy_netherrack", "fiery_netherrack", "lively_netherrack", "icy_netherrack", "basalt",
            "amethyst_ore", "rime_ore", "cincinnasite_ore", "hyphae/mycelium", "stalagnate", "enoki_stem", "egg_plant", "eye_vine"};
        for (String m : mustExist) check("census_" + m, blocks.get(m) > 0, "count=" + blocks.get(m));
        String[] features = {"glowstone_cluster", "elder_mushroom_brown", "elder_mushroom_red", "enoki", "thornstalk", "lava_pool", "blue_fire",
            "bn_stalagnate", "bn_lucis", "bn_eye_vine", "bn_wart_tree", "bn_red_large_mushroom", "bn_brown_large_mushroom", "bn_bone", "cincinnasite_cluster",
            "bn_city"};
        for (String f : features) check("feature_" + f, plugin.gen.placed.getOrDefault(f, 0) > 0, "count=" + plugin.gen.placed.getOrDefault(f, 0));
        int bnStructures = 0;
        for (Map.Entry<String, Integer> e : plugin.gen.placed.entrySet())
            if (e.getKey().startsWith("bn_altar") || e.getKey().startsWith("bn_portal") || e.getKey().startsWith("bn_garden") || e.getKey().startsWith("bn_pillar")
                || e.getKey().startsWith("bn_respawn") || e.getKey().equals("bn_cave_room")) bnStructures += e.getValue();
        check("feature_bn_random_structures", bnStructures > 0, "count=" + bnStructures);
        check("feature_structure_tiles", plugin.structures.chests + plugin.structures.spawners > 0 || plugin.gen.placed.getOrDefault("pigtificate_village", 0) > 0,
            "chests=" + plugin.structures.chests + " spawners=" + plugin.structures.spawners + " residents=" + plugin.structures.residents
                + " villages=" + plugin.gen.placed.getOrDefault("pigtificate_village", 0) + " shrines=" + plugin.gen.placed.getOrDefault("ghast_queen_shrine", 0));
        int wonders = 0;
        for (Map.Entry<String, Integer> e : plugin.gen.placed.entrySet()) if (e.getKey().startsWith("wonder_")) wonders += e.getValue();
        check("feature_wonders", wonders > 0, "count=" + wonders);
        later(5, this::mega);
    }

    // ---- stage 3b: the five mega structures ----------------------------------------------------------------------------
    private final List<Mega.Site> megaSites = new ArrayList<>();

    /** Generates the land around the nearest planned site of each kind, then inspects what was built there. */
    private void mega() {
        java.util.LinkedHashSet<Long> want = new java.util.LinkedHashSet<>();
        for (Mega.Kind k : Mega.Kind.values()) {
            Mega.Site s = plugin.gen.nearestMega(k, 0, 0, 8);
            if (s == null) { check("mega_planned_" + k.id, false, "no site within 8 cells"); continue; }
            megaSites.add(s);
            addSquare(want, s.x >> 4, s.z >> 4, Mega.REACH / 16 + 1);
        }
        List<long[]> list = new ArrayList<>();
        for (long k : want) list.add(new long[]{k >> 32, (int) k});
        plugin.getLogger().info("NETHER_SELFTEST mega sites=" + megaSites.size() + " chunks=" + list.size());
        long t0 = System.currentTimeMillis();
        megaBatch(list, 0, t0);
    }

    private void megaBatch(List<long[]> list, int from, long t0) {
        int to = Math.min(list.size(), from + 24);
        for (int i = from; i < to; i++) w.loadChunk((int) list.get(i)[0], (int) list.get(i)[1], true);
        if (to < list.size()) { later(1, () -> megaBatch(list, to, t0)); return; }
        later(10, () -> megaInspect(System.currentTimeMillis() - t0));
    }

    private void megaInspect(long wallMs) {
        int[] forbidden = {41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 137, 210, 211, 255, 166};
        for (Mega.Site s : megaSites) {
            // without players the generated chunks unload at once: load the whole reach again before looking
            for (int cx = (s.x - Mega.REACH) >> 4; cx <= (s.x + Mega.REACH) >> 4; cx++)
                for (int cz = (s.z - Mega.REACH) >> 4; cz <= (s.z + Mega.REACH) >> 4; cz++) w.getChunkAt(cx, cz);
            Boolean built = plugin.registry.megaDecision(s.cellX, s.cellZ);
            StringBuilder empty = new StringBuilder();
            int chests = 0, filled = 0, bad = 0, garrisons = plugin.registry.near(s.x, s.z, Mega.REACH, "garrison").size();
            for (int cx = (s.x - Mega.REACH) >> 4; cx <= (s.x + Mega.REACH) >> 4; cx++)
                for (int cz = (s.z - Mega.REACH) >> 4; cz <= (s.z + Mega.REACH) >> 4; cz++) {
                    org.bukkit.Chunk c = w.getChunkAt(cx, cz);
                    for (org.bukkit.block.BlockState t : c.getTileEntities()) if (t instanceof Chest) {
                        // only this site's chests (a neighbouring GLM build's empty chests may share its chunks)
                        if (t.getX() < s.minX || t.getX() > s.maxX || t.getZ() < s.minZ || t.getZ() > s.maxZ) continue;
                        chests++;
                        boolean any = false;
                        for (ItemStack it : ((Chest) t).getBlockInventory().getContents()) if (it != null) { any = true; break; }
                        // a chest whose loot is rolled when first opened (vanilla loot tables) counts as stocked
                        net.minecraft.server.v1_12_R1.TileEntity te = ((org.bukkit.craftbukkit.v1_12_R1.CraftWorld) w).getHandle()
                            .getTileEntity(new net.minecraft.server.v1_12_R1.BlockPosition(t.getX(), t.getY(), t.getZ()));
                        String lazy = te instanceof net.minecraft.server.v1_12_R1.TileEntityLootable && ((net.minecraft.server.v1_12_R1.TileEntityLootable) te).getLootTableKey() != null
                            ? ((net.minecraft.server.v1_12_R1.TileEntityLootable) te).getLootTableKey().toString() : null;
                        if (lazy != null) { any = true; plugin.getLogger().info("NETHER_SELFTEST lazy loot chest " + t.getX() + "," + t.getY() + "," + t.getZ() + " table=" + lazy); }
                        if (any) filled++;
                        else if (empty.length() < 200) {
                            Registry.Entry owner = plugin.registry.structureAt(t.getX(), t.getY(), t.getZ());
                            GlmSites.Site g = plugin.gen.glm.at(t.getX(), t.getZ());
                            empty.append(' ').append(t.getX()).append(',').append(t.getY()).append(',').append(t.getZ()).append(':')
                                .append(owner == null ? "-" : owner.type + "/" + owner.name).append(':').append(t.getType())
                                .append(":glm=").append(g == null ? "-" : g.e.key + "/" + g.tier + "@" + g.minX + "," + g.minZ + ".." + g.maxX + "," + g.maxZ
                                + "/built=" + plugin.registry.glmDecision(g.decisionTier(), g.cellX, g.cellZ));
                        }
                    }
                    ChunkSnapshot snap = c.getChunkSnapshot(false, false, false);
                    for (int y = 1; y < 127; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        int id = snap.getBlockTypeId(x, y, z);
                        for (int f : forbidden) if (id == f) bad++;
                    }
                }
            boolean named = String.join(" ", plugin.whereLines(new Location(w, s.x + 20, s.y + 20, s.z + 20))).contains(s.kind.display);
            if (empty.length() > 0) plugin.getLogger().info("NETHER_SELFTEST empty chests of " + s.kind.id + ":" + empty);
            String detail = "at=" + s.x + "," + s.y + "," + s.z + " built=" + built + " chests=" + chests + " filled=" + filled + " garrisons=" + garrisons
                + " forbidden=" + bad + " where=" + named;
            boolean ok = Boolean.TRUE.equals(built) && chests >= 6 && filled == chests && bad == 0 && garrisons >= (s.kind == Mega.Kind.BAZAAR ? 1 : 3) && named;
            if (s.kind == Mega.Kind.CATHEDRAL) {
                List<Registry.Entry> urns = plugin.registry.near(s.x, s.z, 12, "urn");
                boolean cauldron = !urns.isEmpty() && w.getBlockAt(urns.get(0).x1, urns.get(0).y1, urns.get(0).z1).getType() == Material.CAULDRON;
                ok &= cauldron;
                detail += " urn=" + cauldron;
            }
            if (s.kind == Mega.Kind.BAZAAR) {
                boolean statue = !plugin.registry.near(s.x, s.z, 12, "statue").isEmpty();
                int residents = 0;
                for (org.bukkit.entity.Entity e : w.getNearbyEntities(new Location(w, s.x, s.y + 12, s.z), 80, 40, 80))
                    if ("pigtificate".equals(plugin.mobs.kind(e)) || "gold_golem".equals(plugin.mobs.kind(e))) residents++;
                ok &= statue && residents >= 8 && plugin.gen.peaceful(s.x, s.z, false);
                detail += " statue=" + statue + " residents=" + residents;
            }
            check("mega_" + s.kind.id, ok, detail);
        }
        check("mega_gen_cost", plugin.avgMs() < 25, "avgMs=" + NetherPlugin.fmt(plugin.avgMs()) + " maxMs=" + NetherPlugin.fmt(plugin.maxNanos / 1e6) + " wallMs=" + wallMs
            + " phaseMs[" + plugin.gen.phases(plugin.populated) + "]");
        java.util.Random jr = new java.util.Random(3);
        List<String> pages = Wonders.journalPages(plugin.gen, 0, 0, jr);
        check("wonder_journal_rumours", pages.size() >= 4, "pages=" + pages.size() + " first=" + NetherPlugin.safe(pages.size() > 1 ? pages.get(1).replace('\n', ' ') : "-"));
        later(5, this::glm);
    }

    // ---- stage 3c: the GLM builds (the nearest of each tier) -------------------------------------------------------------
    private final List<GlmSites.Site> glmSites = new ArrayList<>();

    private void glm() {
        check("glm_builds_loaded", plugin.gen.glm.size() >= 90 && plugin.gen.glm.lords.size() == GlmSites.LORD_ORDER.size(),
            "builds=" + plugin.gen.glm.size() + " lords=" + plugin.gen.glm.lords.size());
        java.util.LinkedHashSet<Long> want = new java.util.LinkedHashSet<>();
        for (GlmSites.Tier t : GlmSites.Tier.values()) {
            GlmSites.Site best = null;
            double bd = Double.MAX_VALUE;
            for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) {
                GlmSites.Site s = plugin.gen.glm.site(t, cx, cz);
                if (s == null) continue;
                double d = s.dist(0, 0) + (s.maxX - s.minX) * 2;      // near and small: a quick test
                if (d < bd) { bd = d; best = s; }
            }
            if (best == null) { check("glm_planned_" + t.name().toLowerCase(java.util.Locale.ROOT), false, "none within 3 cells"); continue; }
            glmSites.add(best);
            for (int cx = (best.minX - 16) >> 4; cx <= (best.maxX + 16) >> 4; cx++)
                for (int cz = (best.minZ - 16) >> 4; cz <= (best.maxZ + 16) >> 4; cz++) want.add(((long) cx << 32) | (cz & 0xffffffffL));
        }
        List<long[]> list = new ArrayList<>();
        for (long k : want) list.add(new long[]{k >> 32, (int) k});
        plugin.getLogger().info("NETHER_SELFTEST glm sites=" + glmSites.size() + " chunks=" + list.size());
        glmBatch(list, 0, System.currentTimeMillis());
    }

    private void glmBatch(List<long[]> list, int from, long t0) {
        int to = Math.min(list.size(), from + 24);
        for (int i = from; i < to; i++) w.loadChunk((int) list.get(i)[0], (int) list.get(i)[1], true);
        if (to < list.size()) { later(1, () -> glmBatch(list, to, t0)); return; }
        later(10, () -> glmInspect(System.currentTimeMillis() - t0));
    }

    private void glmInspect(long wallMs) {
        int[] forbidden = {41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 120, 137, 210, 211, 255, 166, 116, 130, 145, 84, 154, 27, 28, 147, 148, 71, 167};
        for (GlmSites.Site s : glmSites) {
            Boolean built = plugin.registry.glmDecision(s.decisionTier(), s.cellX, s.cellZ);
            int chests = 0, filled = 0, bad = 0, spawnerBlocks = 0;
            for (int cx = s.minX >> 4; cx <= s.maxX >> 4; cx++)
                for (int cz = s.minZ >> 4; cz <= s.maxZ >> 4; cz++) {
                    org.bukkit.Chunk c = w.getChunkAt(cx, cz);
                    for (org.bukkit.block.BlockState t : c.getTileEntities()) {
                        if (t instanceof Chest) {
                            chests++;
                            for (ItemStack it : ((Chest) t).getBlockInventory().getContents()) if (it != null) { filled++; break; }
                        }
                        if (t instanceof org.bukkit.block.CreatureSpawner) spawnerBlocks++;
                    }
                    ChunkSnapshot snap = c.getChunkSnapshot(false, false, false);
                    for (int y = 1; y < 127; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                        int id = snap.getBlockTypeId(x, y, z);
                        for (int f : forbidden) if (id == f) bad++;
                    }
                }
            int span = Math.max(s.maxX - s.minX, s.maxZ - s.minZ);
            int spawners = 0, garrisons = 0, lords = 0;
            for (Registry.Entry e : plugin.registry.near(s.x, s.z, span, "glmspawner")) if (e.x1 >= s.minX && e.x1 <= s.maxX && e.z1 >= s.minZ && e.z1 <= s.maxZ) spawners++;
            for (Registry.Entry e : plugin.registry.near(s.x, s.z, span, "garrison")) if (e.x1 >= s.minX && e.x1 <= s.maxX && e.z1 >= s.minZ && e.z1 <= s.maxZ) garrisons++;
            for (Registry.Entry e : plugin.registry.near(s.x, s.z, span, "lord")) if (e.x1 >= s.minX && e.x1 <= s.maxX && e.z1 >= s.minZ && e.z1 <= s.maxZ) lords++;
            boolean named = String.join(" ", plugin.whereLines(new Location(w, s.x, s.floor + 2, s.z))).contains(s.e.title);
            String detail = "build=" + s.e.key + " at=" + s.x + "," + s.floor + "," + s.z + " built=" + built + " chests=" + chests + " filled=" + filled
                + " spawners=" + spawners + " spawnerBlocks=" + spawnerBlocks + " garrisons=" + garrisons + " lordPoints=" + lords + " forbidden=" + bad + " where=" + named;
            boolean ok = Boolean.TRUE.equals(built) && filled >= 3 && spawners >= 1 && spawnerBlocks >= spawners && garrisons >= 1 && bad == 0 && named
                && (s.e.lord == null || lords == 1);
            check("glm_" + s.tier.name().toLowerCase(java.util.Locale.ROOT), ok, detail);
        }
        check("glm_gen_cost", plugin.avgMs() < 25, "avgMs=" + NetherPlugin.fmt(plugin.avgMs()) + " maxMs=" + NetherPlugin.fmt(plugin.maxNanos / 1e6) + " wallMs=" + wallMs
            + " loads=" + plugin.gen.glm.loads + " loadMs=" + NetherPlugin.fmt(plugin.gen.glm.loadNanos / 1e6) + " phaseMs[" + plugin.gen.phases(plugin.populated) + "]");
        later(5, this::colossi);
    }

    // ---- stage 3d: the colossal structures and the Endless Catacombs ----------------------------------------------------
    private final List<Colossi.Site> colossal = new ArrayList<>();

    /** Generates the nearest colossus of every kind whole, and the Catacombs' Heart and a Warden's vault. */
    private void colossi() {
        java.util.LinkedHashSet<Long> want = new java.util.LinkedHashSet<>();
        for (Colossi.Kind k : Colossi.Kind.values()) {
            List<Colossi.Site> near = plugin.gen.colossi.near(k, 0, 0, 3);
            if (near.isEmpty()) { check("colossus_planned_" + k.id, false, "none within 3 cells"); continue; }
            Colossi.Site s = near.get(0);
            colossal.add(s);
            addSquare(want, s.x >> 4, s.z >> 4, Colossi.REACH / 16 + 2);
        }
        Depths dp = plugin.gen.depths;
        check("catacombs_attached", dp != null, dp == null ? "none" : "heart=" + dp.hx + "," + dp.hz + " history=" + (plugin.gen.history.fresh ? "fresh" : plugin.gen.history.chunks + "chunks"));
        if (dp != null) {
            addSquare(want, dp.hx >> 4, dp.hz >> 4, 6);
            addSquare(want, dp.hx >> 4, (dp.hz - Depths.WARD * Depths.P) >> 4, 4);
        }
        List<long[]> list = new ArrayList<>();
        for (long k : want) list.add(new long[]{k >> 32, (int) k});
        plugin.getLogger().info("NETHER_SELFTEST colossi sites=" + colossal.size() + " chunks=" + list.size());
        colossusBatch(list, 0, System.currentTimeMillis());
    }

    private void colossusBatch(List<long[]> list, int from, long t0) {
        int to = Math.min(list.size(), from + 24);
        for (int i = from; i < to; i++) w.loadChunk((int) list.get(i)[0], (int) list.get(i)[1], true);
        if (to < list.size()) { later(1, () -> colossusBatch(list, to, t0)); return; }
        later(10, () -> colossusInspect(System.currentTimeMillis() - t0));
    }

    /** What one ordeal's blocks are: its seal still standing, its levers in place (the drawing and the runtime agree). */
    private String ordealBlocks(Ordeals.Ordeal o) {
        int want = 0, found = 0;
        if (o.type == Ordeals.Type.KEYSEAL || o.type == Ordeals.Type.BOSSSEAL)
            for (int x = o.x1; x <= o.x2; x++) for (int y = o.y1; y <= o.y2; y++) for (int z = o.z1; z <= o.z2; z++) {
                want++;
                Block b = w.getBlockAt(x, y, z);
                if (b.getTypeId() == (o.block >> 4) && b.getData() == (o.block & 15)) found++;
            }
        if (o.parts != null) for (int[] q : o.parts) {
            want++;
            Material m = w.getBlockAt(q[0], q[1], q[2]).getType();
            if (o.type == Ordeals.Type.LEVERS ? m == Material.LEVER : m == Material.NETHERRACK) found++;
        }
        if (o.seal != null) for (int x = o.seal[0]; x <= o.seal[3]; x++) for (int y = o.seal[1]; y <= o.seal[4]; y++) for (int z = o.seal[2]; z <= o.seal[5]; z++) {
            want++;
            Block b = w.getBlockAt(x, y, z);
            if (b.getTypeId() == (o.block >> 4) && b.getData() == (o.block & 15)) found++;
        }
        return found + "/" + want;
    }

    private static boolean whole(String counts) { String[] p = counts.split("/"); return p[0].equals(p[1]); }

    /** A boss seal parts when its boss falls and closes again. */
    private boolean sealCycle(Ordeals.Ordeal o, String boss, int x, int z) {
        plugin.ordeals.bossFell(boss, w, x, z);
        boolean opened = w.getBlockAt(o.x1, o.y1, o.z1).getType() == Material.AIR && w.getBlockAt(o.x2, o.y2, o.z2).getType() == Material.AIR;
        plugin.ordeals.closeSeals(true);
        return opened && whole(ordealBlocks(o));
    }

    private void colossusInspect(long wallMs) {
        int[] forbidden = {41, 42, 57, 133, 22, 152, 138, 46, 90, 119, 137, 210, 211, 255, 166};
        for (Colossi.Site s : colossal) {
            for (int cx = (s.minX >> 4) - 1; cx <= (s.maxX >> 4) + 1; cx++) for (int cz = (s.minZ >> 4) - 1; cz <= (s.maxZ >> 4) + 1; cz++) w.getChunkAt(cx, cz);
            Boolean built = plugin.registry.colossusDecision(s.cellX, s.cellZ);
            int chests = 0, filled = 0, trapped = 0, signs = 0, bad = 0;
            for (int cx = s.minX >> 4; cx <= s.maxX >> 4; cx++) for (int cz = s.minZ >> 4; cz <= s.maxZ >> 4; cz++) {
                org.bukkit.Chunk c = w.getChunkAt(cx, cz);
                for (org.bukkit.block.BlockState t : c.getTileEntities()) {
                    if (t.getX() < s.minX || t.getX() > s.maxX || t.getZ() < s.minZ || t.getZ() > s.maxZ) continue;
                    if (t instanceof org.bukkit.block.Sign) signs++;
                    if (!(t instanceof Chest)) continue;
                    chests++;
                    if (t.getType() == Material.TRAPPED_CHEST) trapped++;
                    for (ItemStack it : ((Chest) t).getBlockInventory().getContents()) if (it != null) { filled++; break; }
                }
                ChunkSnapshot snap = c.getChunkSnapshot(false, false, false);
                for (int y = 1; y < 127; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    int id = snap.getBlockTypeId(x, y, z);
                    for (int f : forbidden) if (id == f) bad++;
                }
            }
            java.util.Set<String> bosses = new java.util.TreeSet<>();
            for (Registry.Entry e : plugin.registry.near(s.x, s.z, Colossi.REACH, "lord")) bosses.add(e.name);
            int garrisons = plugin.registry.near(s.x, s.z, Colossi.REACH, "garrison").size(), spawners = plugin.registry.near(s.x, s.z, Colossi.REACH, "glmspawner").size();
            List<Ordeals.Ordeal> os = Colossi.design(s.kind).ordeals(s);
            StringBuilder seals = new StringBuilder();
            boolean sealsOk = true, cycled = false;
            for (Ordeals.Ordeal o : os) {
                if (o.type != Ordeals.Type.KEYSEAL && o.type != Ordeals.Type.BOSSSEAL && o.type != Ordeals.Type.LEVERS && o.type != Ordeals.Type.BRAZIERS) continue;
                String got = ordealBlocks(o);
                seals.append(' ').append(o.id).append('=').append(got);
                sealsOk &= whole(got);
                if (o.type == Ordeals.Type.BOSSSEAL) cycled = sealCycle(o, o.boss, s.x, s.z);
            }
            boolean named = String.join(" ", plugin.whereLines(new Location(w, s.x + 10, s.y + 4, s.z + 10))).contains(s.kind.display);
            String[] want = s.kind == Colossi.Kind.PYRAMID ? new String[]{"scarab_matriarch", "sphinx_sentinel", "sunless_pharaoh", "vizier_hekkat"}
                : s.kind == Colossi.Kind.CITADEL ? new String[]{"blazing_admiral", "boiling_warden", "ember_sovereign", "high_fire_sage"} : new String[]{s.kind.lord};
            boolean allBosses = bosses.containsAll(java.util.Arrays.asList(want));
            String detail = "at=" + s.x + "," + s.y + "," + s.z + " built=" + built + " chests=" + chests + " filled=" + filled + " trapped=" + trapped + " signs=" + signs
                + " bosses=" + bosses + " garrisons=" + garrisons + " spawners=" + spawners + " ordeals=" + os.size() + " forbidden=" + bad + " where=" + named
                + " seals[" + seals.toString().trim() + "] treasuryCycle=" + cycled;
            boolean titan = s.kind.titan();      // the ten of 2026-10-04: one Lord each, and its vault behind the Lord's seal
            check("colossus_" + s.kind.id, Boolean.TRUE.equals(built) && chests >= 15 && filled == chests && bad == 0 && allBosses && garrisons >= 10 && spawners >= 1
                && signs >= (titan ? 4 : 8) && os.size() >= (titan ? 1 : 6) && named && sealsOk && cycled, detail);
        }
        Depths dp = plugin.gen.depths;
        if (dp != null) {
            for (int cx = (dp.hx >> 4) - 6; cx <= (dp.hx >> 4) + 6; cx++) for (int cz = (dp.hz >> 4) - 6; cz <= (dp.hz >> 4) + 6; cz++) w.getChunkAt(cx, cz);
            java.util.Set<String> bosses = new java.util.TreeSet<>();
            for (Registry.Entry e : plugin.registry.near(dp.hx, dp.hz, 80, "lord")) bosses.add(e.name);
            int chests = 0, filled = 0;
            for (int cx = (dp.hx - 66) >> 4; cx <= (dp.hx + 66) >> 4; cx++) for (int cz = (dp.hz - 66) >> 4; cz <= (dp.hz + 66) >> 4; cz++)
                for (org.bukkit.block.BlockState t : w.getChunkAt(cx, cz).getTileEntities()) {
                    if (!(t instanceof Chest) || t.getY() > Depths.TOP || Math.max(Math.abs(t.getX() - dp.hx), Math.abs(t.getZ() - dp.hz)) > 66) continue;
                    chests++;
                    for (ItemStack it : ((Chest) t).getBlockInventory().getContents()) if (it != null) { filled++; break; }
                }
            StringBuilder seals = new StringBuilder();
            boolean sealsOk = true, cycled = false;
            for (Ordeals.Ordeal o : dp.ordealsNear(dp.hx, dp.hz, 66)) {
                if (o.type != Ordeals.Type.KEYSEAL && o.type != Ordeals.Type.BOSSSEAL && o.type != Ordeals.Type.LEVERS) continue;
                String got = ordealBlocks(o);
                seals.append(' ').append(o.id).append('=').append(got);
                sealsOk &= whole(got);
                if (o.type == Ordeals.Type.BOSSSEAL) cycled = sealCycle(o, o.boss, dp.hx, dp.hz);
            }
            boolean hall = w.getBlockAt(dp.hx + 3, Depths.FLOOR + 2, dp.hz + 3).getType() == Material.AIR && w.getBlockAt(dp.hx + 3, Depths.FLOOR, dp.hz + 3).getType().isSolid();
            boolean named = String.join(" ", plugin.whereLines(new Location(w, dp.hx + 3, Depths.FLOOR + 1, dp.hz + 3))).contains("Endless Catacombs");
            check("catacombs_heart", bosses.contains("hollow_king") && chests >= 12 && filled == chests && sealsOk && cycled && hall && named,
                "heart=" + dp.hx + "," + dp.hz + " bosses=" + bosses + " chests=" + chests + " filled=" + filled + " seals[" + seals.toString().trim() + "] treasuryCycle=" + cycled
                + " hall=" + hall + " where=" + named);
            int vx = dp.hx, vz = dp.hz - Depths.WARD * Depths.P;
            java.util.Set<String> wardens = new java.util.TreeSet<>();
            for (Registry.Entry e : plugin.registry.near(vx, vz, 40, "lord")) wardens.add(e.name);
            check("catacombs_vault_gaol", wardens.contains("gaoler"), "at=" + vx + "," + vz + " bosses=" + wardens);
            Map<String, Integer> census = dp.census(40);
            check("catacombs_census", census.getOrDefault("stair", 0) > 0 && census.getOrDefault("puzzle", 0) > 0 && census.getOrDefault("hall", 0) > 0,
                census + " columns=" + dp.drawnColumns + " shafts=" + dp.shafts + " capped=" + dp.capped);
        }
        check("colossus_gen_cost", plugin.avgMs() < 25, "avgMs=" + NetherPlugin.fmt(plugin.avgMs()) + " maxMs=" + NetherPlugin.fmt(plugin.maxNanos / 1e6) + " wallMs=" + wallMs
            + " phaseMs[" + plugin.gen.phases(plugin.populated) + "]");
        later(5, this::mobs);
    }

    // ---- stage 4: mobs and abilities --------------------------------------------------------------------------------
    private final Map<String, LivingEntity> spawned = new TreeMap<>();
    private LivingEntity dummy, dummy2;

    private Location findSite() {
        int[] p = nexAt.getOrDefault(Biomes.Nex.HELL, new int[]{0, 0});
        for (int dx = 0; dx < 64; dx += 4) for (int y = 100; y > 34; y--) {
            Block b = w.getBlockAt(p[0] + dx, y, p[1]);
            if (b.getType().isSolid() && isAir(b, 1, 6)) return b.getLocation().add(0.5, 1, 0.5);
        }
        return new Location(w, p[0] + 0.5, 64, p[1] + 0.5);
    }

    private static boolean isAir(Block b, int from, int to) {
        for (int i = from; i <= to; i++) if (b.getRelative(0, i, 0).getType() != Material.AIR) return false;
        return true;
    }

    /** Without players the test area would unload; hold it (and the queen's) loaded while the test runs. */
    @EventHandler
    public void onUnload(org.bukkit.event.world.ChunkUnloadEvent e) {
        if (site == null || e.getWorld() != w) return;
        int dx = e.getChunk().getX() - (site.getBlockX() >> 4), dz = e.getChunk().getZ() - (site.getBlockZ() >> 4);
        if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4) e.setCancelled(true);
    }

    private void mobs() {
        site = findSite();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) w.loadChunk((site.getBlockX() >> 4) + dx, (site.getBlockZ() >> 4) + dz, true);
        plugin.getLogger().info("NETHER_SELFTEST site=" + site.getBlockX() + "," + site.getBlockY() + "," + site.getBlockZ());
        int i = 0;
        for (Mobs.Spec s : Mobs.KINDS.values()) {
            if (s.kind.equals("ghast_queen") || s.kind.equals("ghast") || Lords.isLord(s.kind)) continue;   // the Lords have their own stage
            Location at = site.clone().add((i % 5) * 6 - 12, s.base == EntityType.GHAST ? 12 : 0, (i / 5) * 6 - 6);
            LivingEntity e = plugin.mobs.spawn(s.kind, at, false);
            if (e != null) e.setRemoveWhenFarAway(false);
            i++;
            boolean ok = e != null && e.isValid() && s.kind.equals(plugin.mobs.kind(e));
            double expect = s.hp * (s.hostile ? plugin.mobs.hpMult : 1);
            if (ok && !s.kind.equals("spore")) ok = Math.abs(e.getMaxHealth() - expect) < 0.01;
            check("mob_spawn_" + s.kind, ok, e == null ? "null" : "base=" + e.getType() + " hp=" + NetherPlugin.fmt(e.getMaxHealth()) + " expect=" + NetherPlugin.fmt(expect)
                + " name=" + org.bukkit.ChatColor.stripColor(e.getCustomName()));
            if (e != null) spawned.put(s.kind, e);
        }
        for (String k : new String[]{"wight", "spinout", "salamander", "spore_creeper", "brute", "ghast"}) {
            Location at = site.clone().add(-20, k.equals("ghast") ? 14 : 0, 8);
            LivingEntity e = plugin.mobs.spawn(k, at, true);
            if (e != null) e.setRemoveWhenFarAway(false);
            Mobs.T t = e == null ? null : plugin.mobs.track(e);
            check("elite_" + k, t != null && t.elite && e.getMaxHealth() > Mobs.KINDS.get(k).hp * 1.9,
                e == null ? "null" : "name=" + org.bukkit.ChatColor.stripColor(e.getCustomName()) + " hp=" + NetherPlugin.fmt(e.getMaxHealth()));
            if (e != null && !k.equals("spore_creeper")) e.remove();
        }
        dummy = (LivingEntity) w.spawnEntity(site.clone().add(3, 0, 3), EntityType.ZOMBIE);
        dummy.setAI(false);
        dummy.setRemoveWhenFarAway(false);
        dummy2 = (LivingEntity) w.spawnEntity(site.clone().add(-3, 0, 3), EntityType.HUSK);
        dummy2.setAI(false);
        dummy2.setRemoveWhenFarAway(false);
        abilities();
    }

    private void abilities() {
        // Difficulty: a scaled Nether hostile deals +25% damage (a 4-damage hit on an armourless pig should take 5)
        LivingEntity dmgPig = (LivingEntity) w.spawnEntity(site.clone().add(0, 0, 6), EntityType.PIG);
        dmgPig.setAI(false);
        dmgPig.setRemoveWhenFarAway(false);
        LivingEntity ws = (LivingEntity) w.spawnEntity(site.clone().add(1, 0, 6), EntityType.WITHER_SKELETON);
        ws.setAI(false);
        plugin.mobs.scale(ws);
        double hpBefore = dmgPig.getHealth();
        ((CraftEntity) dmgPig).getHandle().damageEntity(net.minecraft.server.v1_12_R1.DamageSource.mobAttack(
            (net.minecraft.server.v1_12_R1.EntityLiving) ((CraftEntity) ws).getHandle()), 4f);
        double taken = hpBefore - dmgPig.getHealth();
        check("difficulty_damage_x1.25", Math.abs(taken - 5.0) < 0.01, "taken=" + NetherPlugin.fmt(taken) + " expect=5.00");
        check("difficulty_health_x1.25", Math.abs(ws.getMaxHealth() - 25.0) < 0.01, "witherSkeletonHp=" + NetherPlugin.fmt(ws.getMaxHealth()) + " expect=25.00");
        dmgPig.remove();
        ws.remove();
        // Wight freezes what it hits
        LivingEntity wight = spawned.get("wight");
        if (wight != null) {
            dummy.damage(1.0, wight);
            check("ability_wight_freeze", plugin.effects.has(dummy, Effects.Kind.FROZEN) && !dummy.hasAI(), "frozen=" + plugin.effects.has(dummy, Effects.Kind.FROZEN));
        }
        // Spinout: projectile-immune while spinning, hurt while resting
        LivingEntity spin = spawned.get("spinout");
        if (spin != null) {
            Mobs.T t = plugin.mobs.track(spin);
            double hp0 = spin.getHealth();
            Arrow a = w.spawnArrow(spin.getLocation().add(0, 3, 0), new org.bukkit.util.Vector(0, -1, 0), 0.1f, 0);
            a.setShooter(dummy2);
            boolean spinning = plugin.mobs.spinning(t);
            ((CraftEntity) spin).getHandle().damageEntity(net.minecraft.server.v1_12_R1.DamageSource.arrow(
                (net.minecraft.server.v1_12_R1.EntityArrow) ((CraftEntity) a).getHandle(), ((CraftEntity) dummy2).getHandle()), 3f);
            check("ability_spinout_projectile_immunity", spinning && spin.getHealth() == hp0, "spinning=" + spinning + " hp=" + hp0 + "->" + spin.getHealth());
            a.remove();
        }
        // Salamander ignites its victim; neutral anger spreads
        LivingEntity sal = spawned.get("salamander");
        if (sal != null) {
            dummy2.setFireTicks(0);
            dummy2.damage(1.0, sal);
            check("ability_salamander_ignite", dummy2.getFireTicks() > 0, "fireTicks=" + dummy2.getFireTicks());
            sal.damage(0.5, dummy);
            Mobs.T t = plugin.mobs.track(sal);
            check("ability_salamander_neutral_anger", t != null && t.angerAt != null, null);
        }
        // Coolmar spider: Frostbitten applies to players only (checked by the bot run); Frost volley below
        LivingEntity frost = spawned.get("frost");
        if (frost instanceof Creature) ((Creature) frost).setTarget(dummy2);
        // Spore growth and stage persistence
        LivingEntity spore = spawned.get("spore");
        if (spore != null) {
            Mobs.T t = plugin.mobs.track(spore);
            plugin.mobs.setStage(t, 3);
            t.stageSince = -100000;
        }
        // Spore Creeper: ignite -> infesting cloud, no block damage
        LivingEntity sc = spawned.get("spore_creeper");
        int[] before = new int[1];
        if (sc != null) {
            Location l = sc.getLocation();
            before[0] = solidAround(l);
            dummy.teleport(l.clone().add(1.5, 0, 0));
            ((net.minecraft.server.v1_12_R1.EntityCreeper) ((CraftEntity) sc).getHandle()).do_();
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                // no player -> the creeper is outside every activation range and would not tick its fuse: detonate directly
                try {
                    java.lang.reflect.Method m = net.minecraft.server.v1_12_R1.EntityCreeper.class.getDeclaredMethod("dr");
                    m.setAccessible(true);
                    m.invoke(((CraftEntity) sc).getHandle());
                } catch (ReflectiveOperationException ex) { check("creeper_detonate_reflection", false, ex.getClass().getSimpleName()); }
            }
        }
        // Effects on the second dummy
        for (Effects.Kind k : new Effects.Kind[]{Effects.Kind.FROSTBITTEN, Effects.Kind.INFESTED, Effects.Kind.SOUL_SUCKED, Effects.Kind.CRYING}) {
            plugin.effects.apply(dummy2, k, 400);
        }
        double hpBurn = dummy2.getHealth();
        dummy2.setFireTicks(0);
        dummy2.setNoDamageTicks(0);
        plugin.effects.apply(dummy2, Effects.Kind.FIRE_BURNING, 60);
        later(80, () -> {
            check("effect_fire_burning_damage", dummy2.getHealth() < hpBurn, "hp=" + NetherPlugin.fmt(hpBurn) + "->" + NetherPlugin.fmt(dummy2.getHealth()));
            check("effect_tracking", plugin.effects.has(dummy2, Effects.Kind.FROSTBITTEN) && plugin.effects.has(dummy2, Effects.Kind.INFESTED), "active=" + plugin.effects.active());
            if (sc != null) {
                Location l = sc.getLocation();
                check("ability_spore_creeper_burst", !sc.isValid() && plugin.mobs.abilityCounts.getOrDefault("spore_creeper_burst", 0) > 0
                    && plugin.effects.has(dummy, Effects.Kind.INFESTED), "infestedDummy=" + plugin.effects.has(dummy, Effects.Kind.INFESTED)
                    + " solidBefore=" + before[0] + " solidAfter=" + solidAround(l));
                check("rule_no_mob_block_damage", solidAround(l) >= before[0], null);
                // the spore cloud is particles only: an AreaEffectCloud entity froze browser clients built before 2026-09-29
                long clouds = l.getWorld().getNearbyEntities(l, 8, 8, 8).stream().filter(n -> n.getType() == org.bukkit.entity.EntityType.AREA_EFFECT_CLOUD).count();
                check("spore_cloud_particles_only", plugin.mobs.sporeClouds > 0 && clouds == 0, "sporeClouds=" + plugin.mobs.sporeClouds + " cloudEntities=" + clouds);
            }
            check("ability_spore_growth", spore == null || plugin.mobs.abilityCounts.getOrDefault("spore_grow", 0) > 0, "stage=" + (spore == null ? -1 : plugin.mobs.track(spore) == null ? -1 : plugin.mobs.track(spore).stage));
            if (Bukkit.getOnlinePlayers().isEmpty()) plugin.getLogger().info("NETHER_SELFTEST SKIP ability_frost_volley (needs an online player: blaze AI is inactive without one; covered by the bot run)");
            else check("ability_frost_volley", plugin.mobs.abilityCounts.getOrDefault("frost_volley_shot", 0) > 0, "shots=" + plugin.mobs.abilityCounts.getOrDefault("frost_volley_shot", 0));
            LivingEntity pig = spawned.get("pigtificate");
            check("pigtificate_trades", pig instanceof Villager && ((Villager) pig).getRecipeCount() > 0
                && ((Villager) pig).getRecipe(0).getIngredients().size() > 0, pig == null ? "null" : "career=" + org.bukkit.ChatColor.stripColor(pig.getCustomName())
                + " trades=" + ((Villager) pig).getRecipeCount());
            loot();
        });
    }

    private int solidAround(Location l) {
        int n = 0;
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++)
            if (l.getBlock().getRelative(dx, dy, dz).getType().isSolid()) n++;
        return n;
    }

    // ---- stage 5: loot, items, brewing ------------------------------------------------------------------------------
    private void loot() {
        java.util.Random r = new java.util.Random(7);
        for (String table : Loot.TABLES) {
            Block b = site.getBlock().getRelative(6, 0, -6);
            b.setType(Material.CHEST);
            Chest c = (Chest) b.getState();
            c.getBlockInventory().clear();
            int n = Loot.fill(c.getBlockInventory(), table, r);
            check("loot_" + table.replace(':', '_').replace('/', '_'), n > 0, "stacks=" + n);
            c.getBlockInventory().clear();
            b.setType(Material.AIR);
        }
        int ok = 0;
        for (String id : Items.ids()) if (id.equals(Items.id(Items.create(id, 1)))) ok++;
        check("items_roundtrip", ok == Items.DEFS.size(), ok + "/" + Items.DEFS.size());
        check("recipes_registered", plugin.crafting.recipes.size() > 40, "recipes=" + plugin.crafting.recipes.size());
        ItemStack[] m = new ItemStack[9];
        for (int i = 0; i < 9; i++) m[i] = Items.create("amethyst_crystal", 1);
        Crafting.R rr = plugin.crafting.match(m);
        check("recipe_match_amethyst_block", rr != null && rr.result.equals("amethyst_block"), null);
        m[4] = new ItemStack(Material.CHORUS_FRUIT_POPPED);
        check("recipe_rejects_vanilla_standin", plugin.crafting.match(m) == null, null);
        // brewing: awkward + rime crystal -> Potion of Freezing
        Block bs = site.getBlock().getRelative(-6, 0, -6);
        bs.setType(Material.BREWING_STAND);
        BrewerInventory inv = ((BrewingStand) bs.getState()).getInventory();
        for (int i = 0; i < 3; i++) {
            ItemStack p = new ItemStack(Material.POTION);
            PotionMeta pm = (PotionMeta) p.getItemMeta();
            pm.setBasePotionData(new PotionData(PotionType.AWKWARD));
            p.setItemMeta(pm);
            inv.setItem(i, p);
        }
        inv.setIngredient(Items.create("rime_crystal", 1));
        inv.setFuel(new ItemStack(Material.BLAZE_POWDER, 1));
        later(460, () -> {
            BrewerInventory live = ((BrewingStand) bs.getState()).getInventory();
            String got = Items.id(live.getItem(0));
            ItemStack s0 = live.getItem(0);
            String base = s0 != null && s0.getItemMeta() instanceof PotionMeta ? ((PotionMeta) s0.getItemMeta()).getBasePotionData().getType().name() : "-";
            if (Bukkit.getOnlinePlayers().isEmpty() && !"potion_freezing".equals(got)) {
                plugin.getLogger().info("NETHER_SELFTEST SKIP brewing_freezing (tile entities near no player did not tick; covered by /jnether test brew with a player)");
                queen();
                return;
            }
            check("brewing_freezing", "potion_freezing".equals(got), "slot0=" + got + " type=" + (s0 == null ? "-" : s0.getType()) + " base=" + base
                + " ingredient=" + (live.getIngredient() == null ? "-" : live.getIngredient().getAmount()) + " fuel=" + (live.getFuel() == null ? "-" : live.getFuel().getAmount())
                + " brewTime=" + ((BrewingStand) bs.getState()).getBrewingTime() + " block=" + bs.getType());
            queen();
        });
    }

    // ---- stage 6: the Ghast Queen -----------------------------------------------------------------------------------
    private void queen() {
        Block urn = site.getBlock().getRelative(0, 0, -12);
        for (int y = 1; y <= 20; y++) for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            Block b = urn.getRelative(dx, y, dz);
            if (b.getType() != Material.AIR && b.getType() != Material.BEDROCK) b.setType(Material.AIR, false);
        }
        urn.setType(Material.CAULDRON);
        plugin.registry.add("urn", "selftest", urn.getX(), urn.getY(), urn.getZ(), urn.getX(), urn.getY(), urn.getZ());
        plugin.boss.startSummon(urn);
        later(150, () -> {
            Boss.Queen q = plugin.boss.any();
            check("queen_summoned", q != null && q.e.isValid() && q.e.getScoreboardTags().contains("jaspr_boss"),
                q == null ? "none" : "hp=" + NetherPlugin.fmt(q.e.getMaxHealth()) + " bar=" + q.bar.getTitle());
            if (q == null) { finish(); return; }
            double max = q.e.getMaxHealth();
            q.e.damage(max * 0.1);
            later(20, () -> {
                q.cooldownUntil = 0;
                q.e.setNoDamageTicks(0);
                q.e.damage(max * 0.2);
                later(20, () -> {
                    q.cooldownUntil = 0;
                    q.e.setNoDamageTicks(0);
                    q.e.damage(max * 0.25);
                    later(20, () -> {
                        q.cooldownUntil = 0;
                        q.e.setNoDamageTicks(0);
                        q.e.damage(max * 0.25);
                        later(20, () -> {
                            check("queen_waves", q.stage == 4 && plugin.boss.waves >= 4, "stage=" + q.stage + " waves=" + plugin.boss.waves);
                            q.e.setNoDamageTicks(0);
                            q.e.damage(max * 5);
                            later(20, () -> {
                                boolean tear = false;
                                for (ItemStack s : queenDrops) if (Items.is(s, "ghast_queen_tear")) tear = true;
                                check("queen_killed_drops_tear", queenDied && tear, "drops=" + queenDrops.size() + " defeated=" + plugin.boss.defeated);
                                check("queen_urn_reset", urn.getType() == Material.CAULDRON && urn.getData() == 0, "data=" + urn.getData());
                                lords(new ArrayList<>(Lords.DEFS.keySet()), 0);
                            });
                        });
                    });
                });
            });
        });
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity().getScoreboardTags().contains("jn_k_ghast_queen")) {
            queenDied = true;
            queenDrops.addAll(e.getDrops());
        }
        if (e.getEntity().getScoreboardTags().contains("jn_lord")) lordDrops.put(plugin.mobs.kind(e.getEntity()), new ArrayList<>(e.getDrops()));
    }

    // ---- stage 7: the Nether Lords and the champions, one at a time -------------------------------------------------------
    private final Map<String, List<ItemStack>> lordDrops = new TreeMap<>();

    private void lords(List<String> ids, int i) {
        if (i >= ids.size()) {
            check("lords_all_slain", plugin.lords.slain >= ids.size(), "slain=" + plugin.lords.slain + " " + plugin.lords.slainBy);
            finish();
            return;
        }
        String id = ids.get(i);
        Lords.Def d = Lords.DEFS.get(id);
        Location at = site.clone().add(0, d.flies ? 10 : 0, 14);
        int risen = plugin.lords.risen;
        LivingEntity e = plugin.lords.rise(w, d, at.getBlockX(), at.getBlockY(), at.getBlockZ());
        double expect = d.hp * plugin.mobs.hpMult;
        boolean ok = e != null && e.isValid() && plugin.lords.risen == risen + 1 && e.getScoreboardTags().contains("jn_lord")
            && e.getType() == d.base && Math.abs(e.getMaxHealth() - expect) < 0.5;
        check("lord_rise_" + id, ok, e == null ? "null" : "base=" + e.getType() + " hp=" + NetherPlugin.fmt(e.getMaxHealth()) + " expect=" + NetherPlugin.fmt(expect)
            + " active=" + plugin.lords.active() + " name=" + org.bukkit.ChatColor.stripColor(e.getCustomName()));
        if (e == null) { lords(ids, i + 1); return; }
        later(60, () -> {
            boolean alive = e.isValid() && !e.isDead();
            check("lord_fights_" + id, alive, "valid=" + e.isValid() + " at=" + e.getLocation().getBlockX() + "," + e.getLocation().getBlockY() + "," + e.getLocation().getBlockZ());
            e.setHealth(0);
            later(d.base == EntityType.ENDER_DRAGON ? 220 : 20, () -> {
                List<ItemStack> drops = lordDrops.getOrDefault(id, new ArrayList<>());
                boolean shards = false;
                for (ItemStack s : drops) if (Items.is(s, "hellforged_shard")) shards = true;
                check("lord_hoard_" + id, drops.size() >= 4 && shards, "drops=" + drops.size() + " slain=" + plugin.lords.slainBy.getOrDefault(id, 0));
                lords(ids, i + 1);
            });
        });
    }

    private void finish() {
        for (LivingEntity e : spawned.values()) if (e.isValid()) e.remove();
        if (dummy != null) dummy.remove();
        if (dummy2 != null) dummy2.remove();
        HandlerList.unregisterAll(this);
        plugin.getLogger().info("NETHER_SELFTEST abilities " + plugin.mobs.abilityCounts);
        plugin.getLogger().info("NETHER_SELFTEST_DONE pass=" + pass + " fail=" + fail + " avgMs=" + NetherPlugin.fmt(plugin.avgMs()) + " chunks=" + plugin.populated);
    }
}
