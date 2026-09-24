/*
 * JasprImportedWorldgen 1.2.1 patch source. The importer's canonical source lives on the owner's PC
 * (C:\Users\AM\Documents\JasperCraft-Threefold-Structures-20260923); this file is the CFR 0.152
 * decompilation of the 1.2.0 GearLoot class with one change (picks). Apply the same change there.
 * Rebuild the jar with scripts/patch-imported-worldgen.sh.
 */
package chat.jaspr.imported;

import chat.jaspr.biomes.Terrain;
import chat.jaspr.imported.SiteSpec;
import chat.jaspr.imported.TileIndex;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class GearLoot {
    private static final long SALT = 5352322829007204658L;
    private static volatile Method roll;
    private static volatile ClassLoader loader;
    private static volatile boolean warned;
    static volatile long picked;
    static volatile long placed;
    static volatile long failures;
    private static final Map<String, Integer> CHESTS;

    private GearLoot() {
    }

    static boolean available() {
        return GearLoot.method() != null;
    }

    private static Method method() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("JasprGear");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        Method method = roll;
        if (method != null && loader == classLoader) {
            return method;
        }
        try {
            method = Class.forName("chat.jaspr.gear.GearApi", true, classLoader).getMethod("rollLoot", Random.class, Integer.TYPE);
        }
        catch (LinkageError | ReflectiveOperationException | RuntimeException throwable) {
            GearLoot.failed(null, throwable);
            return null;
        }
        roll = method;
        loader = classLoader;
        return method;
    }

    private static void failed(Logger logger, Throwable throwable) {
        Plugin plugin;
        ++failures;
        if (warned) {
            return;
        }
        warned = true;
        if (logger == null && (plugin = Bukkit.getPluginManager().getPlugin("JasprImportedWorldgen")) != null) {
            logger = plugin.getLogger();
        }
        if (logger != null) {
            logger.warning("IMPORTED_GEAR_UNAVAILABLE reason=" + throwable.getClass().getSimpleName());
        }
    }

    static int tier(SiteSpec siteSpec) {
        return Math.max(1, Math.min(5, siteSpec.tier));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    static int chests(SiteSpec siteSpec, TileIndex tileIndex) throws IOException {
        Map<String, Integer> map = CHESTS;
        synchronized (map) {
            Integer n = CHESTS.get(siteSpec.id);
            if (n != null) {
                return n;
            }
        }
        int n = 0;
        for (int i = 0; i < siteSpec.widthChunks(); ++i) {
            for (int j = 0; j < siteSpec.depthChunks(); ++j) {
                TileIndex.Tile tile = tileIndex.tile(siteSpec, i, j);
                if (tile == null) continue;
                TileIndex.Data data = tileIndex.decode(tile, siteSpec.dimensions[1]);
                for (int k = 0; k < data.size(); ++k) {
                    if (data.id[k] != 54 && data.id[k] != 146) continue;
                    ++n;
                }
            }
        }
        Map<String, Integer> map2 = CHESTS;
        synchronized (map2) {
            CHESTS.put(siteSpec.id, n);
        }
        return n;
    }

    static Random stream(long l, int n, int n2, int n3) {
        return new Random(Terrain.mix((long)(l ^ 0x4A47454152303132L ^ (long)n * 341873128712L ^ (long)n3 * 132897987541L ^ (long)n2 << 40)));
    }

    /**
     * 1.2.1 (2026-09-24): every chest of every imported design rolls once. Until 1.2.0 only about
     * 2 + tier chests per design were picked; JasprGear's GearApi.rollLoot now keeps the per-chest
     * trinket chance very small and scales it with the design's tier. The stream is still keyed to
     * the chest's position, so nothing that is placed changes.
     */
    static boolean picks(Random random, int n, int n2) {
        return n > 0;
    }

    static void fill(Logger logger, Chunk chunk, int n, TileIndex.Data data, SiteSpec siteSpec, TileIndex tileIndex) {
        Method method = GearLoot.method();
        if (method == null) {
            return;
        }
        try {
            int n2 = -1;
            int n3 = GearLoot.tier(siteSpec);
            long l = chunk.getWorld().getSeed();
            for (int i = 0; i < data.size(); ++i) {
                Block block;
                BlockState blockState;
                int n4;
                if (data.id[i] != 54 && data.id[i] != 146) continue;
                if (n2 < 0) {
                    n2 = GearLoot.chests(siteSpec, tileIndex);
                }
                int n5 = data.index[i];
                int n6 = n5 & 0xF;
                int n7 = n5 >>> 4 & 0xF;
                int n8 = n + (n5 >>> 8);
                int n9 = chunk.getX() * 16 + n6;
                Random random = GearLoot.stream(l, n9, n8, n4 = chunk.getZ() * 16 + n7);
                if (!GearLoot.picks(random, n2, n3)) continue;
                ++picked;
                Object object = method.invoke(null, random, n3);
                if (!(object instanceof ItemStack) || ((ItemStack)object).getType() == Material.AIR || !((blockState = (block = chunk.getBlock(n6, n8, n7)).getState()) instanceof Chest)) continue;
                Inventory inventory = ((Chest)blockState).getBlockInventory();
                ArrayList<Integer> arrayList = new ArrayList<Integer>();
                for (int j = 0; j < inventory.getSize(); ++j) {
                    ItemStack itemStack = inventory.getItem(j);
                    if (itemStack != null && itemStack.getType() != Material.AIR) continue;
                    arrayList.add(j);
                }
                if (arrayList.isEmpty()) continue;
                inventory.setItem(((Integer)arrayList.get(random.nextInt(arrayList.size()))).intValue(), (ItemStack)object);
                ++placed;
                logger.info("IMPORTED_GEAR_PLACED site=" + siteSpec.id + " tier=" + n3 + " at=" + n9 + "," + n8 + "," + n4);
            }
        }
        catch (Exception | LinkageError throwable) {
            GearLoot.failed(logger, throwable);
        }
    }

    static String metrics() {
        return "gearLoot=" + (GearLoot.available() ? "on" : "off") + " gearPicked=" + picked + " gearPlaced=" + placed + " gearFailures=" + failures;
    }

    static {
        CHESTS = new HashMap<String, Integer>();
    }
}
