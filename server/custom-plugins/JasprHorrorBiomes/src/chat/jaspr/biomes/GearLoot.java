package chat.jaspr.biomes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Survivor Gear trinkets in structure chests (3.26.0).
 *
 * JasprGear owns the items and the odds: chat.jaspr.gear.GearApi.rollLoot(Random, int tier) returns one
 * trinket or (mostly) null, gating rank by tier -- ranks 1-2 anywhere, 3 from tier 3, 4 from tier 4, 5 only
 * at tier 5. Tiers here: catalogue sites I-V -> 1-5, set-piece chests depth tier 0-4 -> 1-5, troves 5,
 * dungeon rooms 0 (1 for a room's rich chest).
 *
 * Like ExpeditionLoot's equipment factory this goes through reflection and JasprGear's own class loader,
 * resolved once and cached, and only while JasprGear is enabled. It is never a hard dependency and never
 * throws: it runs from world generation, and a missing trinket must never cost a chunk. It draws only from
 * the Random it is handed, so a seeded chest rolls the same trinket every time; with JasprGear absent it
 * draws nothing at all.
 */
public final class GearLoot {
    private GearLoot() { }

    private static volatile Method roll;
    private static volatile ClassLoader loader;
    private static volatile long rolls, hits, failures;
    private static volatile boolean linked, warned;

    private static void log(boolean warn, String line) {
        Plugin self = Bukkit.getPluginManager().getPlugin("JasprHorrorBiomes");
        if (self == null) return;
        if (warn) self.getLogger().warning(line); else self.getLogger().info(line);
    }

    private static void failed(Throwable e) {
        failures++;
        if (warned) return;
        warned = true;                                                  // once per run: the reason, never the item
        log(true, "GEAR_LOOT_UNAVAILABLE reason=" + e.getClass().getSimpleName());
    }

    private static Method method() {
        Plugin gear = Bukkit.getPluginManager().getPlugin("JasprGear");
        if (gear == null || !gear.isEnabled()) return null;
        ClassLoader cl = gear.getClass().getClassLoader();
        Method m = roll;
        if (m != null && loader == cl) return m;
        try {
            m = Class.forName("chat.jaspr.gear.GearApi", true, cl).getMethod("rollLoot", Random.class, int.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed(e);
            return null;
        }
        roll = m;
        loader = cl;
        if (!linked) { linked = true; log(false, "GEAR_LOOT_LINKED api=chat.jaspr.gear.GearApi.rollLoot"); }
        return m;
    }

    /** At most one trinket for a chest of this tier (clamped to 0-5), or null. */
    public static ItemStack roll(Random r, int tier) {
        if (r == null) return null;
        Method m = method();
        if (m == null) return null;
        try {
            Object item = m.invoke(null, r, Math.max(0, Math.min(5, tier)));
            rolls++;
            if (item instanceof ItemStack && ((ItemStack) item).getType() != Material.AIR) {
                hits++;
                return (ItemStack) item;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed(e);
        }
        return null;
    }

    /**
     * Rolls for a chest that is being filled in place and puts a trinket, if any, into a free slot picked with
     * the same Random. getBlockInventory() is the LIVE tile inventory: never call BlockState.update() after
     * this, it would copy the empty pre-fill snapshot back over it. Returns true when a trinket went in.
     */
    public static boolean add(Inventory inv, Random r, int tier) {
        if (inv == null) return false;
        ItemStack item = roll(r, tier);
        if (item == null) return false;
        try {
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack at = inv.getItem(i);
                if (at == null || at.getType() == Material.AIR) free.add(i);
            }
            if (free.isEmpty()) return false;                   // a full chest keeps what it has
            inv.setItem(free.get(r.nextInt(free.size())), item);
            return true;
        } catch (RuntimeException e) {
            failed(e);
            return false;
        }
    }

    /**
     * The stream a set-piece chest rolls its trinket from: a copy of the builder's Random at the chest (so r
     * itself does not move, and every block and chest the builder places after this one comes out exactly as
     * before), keyed to where the chest stands. Every chunk restarts the builder's Random from the site seed,
     * so without the key the first chest in one chunk would roll the same trinket as the first in the next.
     */
    public static Random at(Random r, int wx, int wy, int wz) {
        if (r == null || method() == null) return null;             // JasprGear absent: no copy, no draws
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(r); }
            Random copy;
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                copy = (Random) in.readObject();
            }
            return new Random(Terrain.mix(copy.nextLong() ^ (wx * 341873128712L) ^ (wz * 132897987541L) ^ ((long) wy << 40)));
        } catch (Exception e) {
            failed(e);
            return null;
        }
    }

    public static String metrics() {
        Plugin gear = Bukkit.getPluginManager().getPlugin("JasprGear");
        return "gearLoot=" + (gear != null && gear.isEnabled() ? "on" : "off") + " gearRolls=" + rolls + " gearHits=" + hits
            + " gearFailures=" + failures;
    }
}
