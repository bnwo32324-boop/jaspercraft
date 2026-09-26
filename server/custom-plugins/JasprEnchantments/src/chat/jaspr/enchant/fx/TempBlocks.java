package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.E;
import chat.jaspr.enchant.util.Guard;
import chat.jaspr.enchant.util.Log;
import chat.jaspr.enchant.util.Nms;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.BlockFluids;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.EntityLiving;
import net.minecraft.server.v1_12_R1.EnumDirection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.Material;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftServer;
import net.minecraft.server.v1_12_R1.World;
import net.minecraft.server.v1_12_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * SME frostedicetemp and magmatemp without custom blocks (Blocks.df = magma block):
 *  - temp ice is vanilla frosted ice whose own melt logic (light/neighbour/age, like SME's BlockTemporaryIce
 *    which extends it) runs unchanged; the final "turn into water" (BlockFadeEvent) becomes air instead;
 *  - temp magma is a vanilla magma block whose age (0..3), scheduled ticks, random ticks and neighbour
 *    melting are simulated here exactly as BlockTemporaryMagma does, ending as source lava.
 * Every tracked block is persisted and reverted on disable and on the next start after a crash.
 */
public final class TempBlocks implements Listener {
    private static final class Key {
        final String world;
        final int x, y, z;

        Key(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return k.x == x && k.y == y && k.z == z && k.world.equals(world);
        }

        @Override
        public int hashCode() {
            return ((x * 31 + y) * 31 + z) * 31 + world.hashCode();
        }

        BlockPosition pos() {
            return new BlockPosition(x, y, z);
        }
    }

    private static final class Magma {
        int age;
        long due; // server tick of the next scheduled update
        long placed;
    }

    /**
     * Safety cap (not in SME): frosted ice only melts in light and temp magma only next to lava, so SME's
     * blocks can survive forever in dark caves or fully converted pools. After 30 minutes they are reverted.
     */
    public static final long MAX_AGE_TICKS = 36000L;

    private static final Map<Key, Long> ICE = new HashMap<>();
    private static final Map<Key, Magma> MAGMA = new HashMap<>();
    private static File file;
    private static boolean dirty = false;
    public static long placedIce = 0, placedMagma = 0, revertedIce = 0, revertedMagma = 0;

    static WorldServer world(String name) {
        org.bukkit.World w = Bukkit.getWorld(name);
        return w == null ? null : ((CraftWorld) w).getHandle();
    }

    static Key key(World w, BlockPosition p) {
        return new Key(w.getWorld().getName(), p.getX(), p.getY(), p.getZ());
    }

    // ------------------------------------------------------------------ ice

    public static void placeIce(World w, BlockPosition p, int delay) {
        w.setTypeAndData(p, Blocks.FROSTED_ICE.getBlockData(), 3);
        w.a(p, Blocks.FROSTED_ICE, delay);
        ICE.put(key(w, p), (long) MinecraftServer.currentTick);
        placedIce++;
        dirty = true;
    }

    public static boolean isTempIce(World w, BlockPosition p) {
        return ICE.containsKey(key(w, p));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFade(BlockFadeEvent e) {
        Guard.run("tempice.fade", () -> {
            org.bukkit.block.Block b = e.getBlock();
            Key k = new Key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (!ICE.containsKey(k)) return;
            if (b.getType() != org.bukkit.Material.FROSTED_ICE) {
                ICE.remove(k);
                return;
            }
            // BlockTemporaryIce.turnIntoWater -> setBlockToAir
            e.setCancelled(true);
            ICE.remove(k);
            dirty = true;
            WorldServer w = ((CraftWorld) b.getWorld()).getHandle();
            w.setAir(k.pos());
            revertedIce++;
        });
    }

    // ------------------------------------------------------------------ magma

    public static void placeMagma(World w, BlockPosition p, int delay) {
        w.setTypeAndData(p, Blocks.df.getBlockData(), 3);
        Magma m = new Magma();
        m.age = 0;
        m.due = MinecraftServer.currentTick + delay;
        m.placed = MinecraftServer.currentTick;
        MAGMA.put(key(w, p), m);
        placedMagma++;
        dirty = true;
    }

    private static boolean isTempMagma(World w, BlockPosition p) {
        Magma m = MAGMA.get(key(w, p));
        return m != null && w.getType(p).getBlock() == Blocks.df;
    }

    private static int countNeighbors(World w, BlockPosition p, boolean magma) {
        int n = 0;
        for (EnumDirection d : EnumDirection.values()) {
            BlockPosition q = p.shift(d);
            boolean hit = magma ? isTempMagma(w, q) : w.getType(q).getBlock() == Blocks.LAVA;
            if (hit) {
                ++n;
                if (n >= 4) return n;
            }
        }
        return n;
    }

    /** BlockTemporaryMagma.updateTick */
    private static void updateTick(World w, BlockPosition p, Magma m, Random rand) {
        if (countNeighbors(w, p, true) < 4 && countNeighbors(w, p, false) > 2 - m.age) {
            slightlyMelt(w, p, m, rand, true);
        } else {
            m.due = MinecraftServer.currentTick + MathHelper.nextInt(rand, 20, 40);
        }
    }

    private static void slightlyMelt(World w, BlockPosition p, Magma m, Random rand, boolean meltNeighbors) {
        if (m.age < 3) {
            m.age++;
            m.due = MinecraftServer.currentTick + MathHelper.nextInt(rand, 20, 40);
            dirty = true;
        } else {
            turnIntoLava(w, p);
            if (!meltNeighbors) return;
            for (EnumDirection d : EnumDirection.values()) {
                BlockPosition q = p.shift(d);
                if (isTempMagma(w, q)) slightlyMelt(w, q, MAGMA.get(key(w, q)), rand, false);
            }
        }
    }

    private static void turnIntoLava(World w, BlockPosition p) {
        MAGMA.remove(key(w, p));
        dirty = true;
        revertedMagma++;
        if (w.getType(p).getBlock() == Blocks.df) w.setTypeUpdate(p, Blocks.LAVA.getBlockData());
    }

    /** neighborChanged(blockIn == this && fewer than 2 temp-magma neighbours) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (MAGMA.isEmpty() || e.getChangedType() != org.bukkit.Material.MAGMA) return;
        org.bukkit.block.Block b = e.getBlock();
        Key k = new Key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (!MAGMA.containsKey(k)) return;
        Guard.run("tempmagma.physics", () -> {
            WorldServer w = ((CraftWorld) b.getWorld()).getHandle();
            BlockPosition p = k.pos();
            if (w.getType(p).getBlock() != Blocks.df) {
                MAGMA.remove(k);
                return;
            }
            if (countNeighbors(w, p, true) < 2) turnIntoLava(w, p);
        });
    }

    /** temp blocks drop nothing (SME registers no item for them) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        org.bukkit.block.Block b = e.getBlock();
        Key k = new Key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (MAGMA.remove(k) != null || ICE.remove(k) != null) {
            e.setDropItems(false);
            e.setExpToDrop(0);
            dirty = true;
        }
    }

    public static void tick() {
        long now = MinecraftServer.currentTick;
        if (now % 100 == 0 && !ICE.isEmpty()) {
            for (Key k : new ArrayList<>(ICE.keySet())) {
                if (now - ICE.get(k) < MAX_AGE_TICKS) continue;
                ICE.remove(k);
                dirty = true;
                WorldServer w = world(k.world);
                if (w != null && w.isLoaded(k.pos()) && w.getType(k.pos()).getBlock() == Blocks.FROSTED_ICE) {
                    w.setAir(k.pos());
                    revertedIce++;
                }
            }
        }
        if (MAGMA.isEmpty()) return;
        List<Key> keys = new ArrayList<>(MAGMA.keySet());
        for (Key k : keys) {
            Magma m = MAGMA.get(k);
            if (m == null) continue;
            WorldServer w = world(k.world);
            if (w == null) continue;
            BlockPosition p = k.pos();
            if (!w.isLoaded(p)) continue;
            if (w.getType(p).getBlock() != Blocks.df) {
                MAGMA.remove(k);
                dirty = true;
                continue;
            }
            if (now - m.placed >= MAX_AGE_TICKS) {
                turnIntoLava(w, p);
                continue;
            }
            boolean randomTick = w.random.nextInt(4096) < w.getGameRules().c("randomTickSpeed");
            if (m.due <= now || randomTick) updateTick(w, p, m, w.random);
        }
    }

    // ------------------------------------------------------------------ Magma Walker

    /** EnchantmentMagmaWalker.walkOnMagma, called when the wearer's block position changes */
    public static void walkOnMagma(EntityLiving living, BlockPosition pos, int level) {
        if (!living.onGround) return;
        World world = living.world;
        float range = (float) Math.min(16, 2 + level);
        BlockPosition from = new BlockPosition(pos.getX() + (double) -range, pos.getY() - 1, pos.getZ() + (double) -range);
        BlockPosition to = new BlockPosition(pos.getX() + (double) range, pos.getY() - 1, pos.getZ() + (double) range);
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                int y = from.getY();
                double dx = x + 0.5 - living.locX, dy = y + 0.5 - living.locY, dz = z + 0.5 - living.locZ;
                if (dx * dx + dy * dy + dz * dz > (double) (range * range)) continue;
                BlockPosition above = new BlockPosition(x, y + 1, z);
                if (world.getType(above).getMaterial() != Material.AIR) continue;
                BlockPosition m = new BlockPosition(x, y, z);
                IBlockData state = world.getType(m);
                Block block = state.getBlock();
                if (state.getMaterial() == Material.LAVA && (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA)
                        && state.get(BlockFluids.LEVEL) == 0 && world.a(Blocks.df, m, false, EnumDirection.DOWN, null)) {
                    placeMagma(world, m, MathHelper.nextInt(living.getRandom(), 60, 120));
                }
            }
        }
    }

    public static int magmaWalkerLevel(EntityLiving e) {
        return Nms.maxLevel(E.MAGMAWALKER, e);
    }

    // ------------------------------------------------------------------ persistence

    public static void init(File dataFolder) {
        file = new File(dataFolder, "tempblocks.txt");
        if (!file.isFile()) return;
        int n = 0;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] a = line.split("\t");
                if (a.length != 5) continue;
                WorldServer w = world(a[1]);
                if (w == null) continue;
                BlockPosition p = new BlockPosition(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4]));
                if (a[0].equals("ice") && w.getType(p).getBlock() == Blocks.FROSTED_ICE) {
                    w.setAir(p);
                    n++;
                } else if (a[0].equals("magma") && w.getType(p).getBlock() == Blocks.df) {
                    w.setTypeUpdate(p, Blocks.LAVA.getBlockData());
                    n++;
                }
            }
        } catch (Exception ex) {
            Log.error("tempblocks.load", ex);
        }
        file.delete();
        if (n > 0) Log.info("SME_TEMPBLOCKS reverted_from_previous_run=" + n);
    }

    public static void save() {
        if (!dirty || file == null) return;
        dirty = false;
        if (ICE.isEmpty() && MAGMA.isEmpty()) {
            file.delete();
            return;
        }
        try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
            for (Key k : ICE.keySet()) w.write("ice\t" + k.world + "\t" + k.x + "\t" + k.y + "\t" + k.z + "\n");
            for (Key k : MAGMA.keySet()) w.write("magma\t" + k.world + "\t" + k.x + "\t" + k.y + "\t" + k.z + "\n");
        } catch (Exception ex) {
            Log.error("tempblocks.save", ex);
        }
    }

    /** on disable: every temp block is reverted immediately */
    public static int revertAll() {
        int n = 0;
        for (Iterator<Key> it = ICE.keySet().iterator(); it.hasNext(); ) {
            Key k = it.next();
            it.remove();
            WorldServer w = world(k.world);
            if (w != null && w.getType(k.pos()).getBlock() == Blocks.FROSTED_ICE) {
                w.setAir(k.pos());
                n++;
            }
        }
        for (Iterator<Key> it = MAGMA.keySet().iterator(); it.hasNext(); ) {
            Key k = it.next();
            it.remove();
            WorldServer w = world(k.world);
            if (w != null && w.getType(k.pos()).getBlock() == Blocks.df) {
                w.setTypeUpdate(k.pos(), Blocks.LAVA.getBlockData());
                n++;
            }
        }
        dirty = true;
        save();
        return n;
    }

    public static int iceCount() {
        return ICE.size();
    }

    public static int magmaCount() {
        return MAGMA.size();
    }
}
