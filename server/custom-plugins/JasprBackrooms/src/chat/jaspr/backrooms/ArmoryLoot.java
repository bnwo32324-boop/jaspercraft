package chat.jaspr.backrooms;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The realm armoury's share of this realm's chests (2026-10-02). JasprGear owns the items and the odds:
 * chat.jaspr.gear.GearApi.rollRealmLoot(Random, String world, int tier) returns a piece of the realm's own set now and
 * then (and, where the realm has one, its forging material). Reached through reflection and JasprGear's class loader,
 * never a hard dependency and never throwing: it runs from world generation, and a missing piece must never cost a chunk.
 * It draws only from the Random it is handed (after the chest's own loot), so a seeded chest rolls the same every time.
 */
final class ArmoryLoot {
    private ArmoryLoot() { }

    private static volatile Method roll;
    private static volatile ClassLoader loader;
    private static volatile boolean warned;
    static volatile long rolls, added;

    private static Method method() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return null;   // offline tests and tools: no armoury
        Plugin gear = Bukkit.getPluginManager().getPlugin("JasprGear");
        if (gear == null || !gear.isEnabled()) return null;
        ClassLoader cl = gear.getClass().getClassLoader();
        Method m = roll;
        if (m != null && loader == cl) return m;
        try {
            m = Class.forName("chat.jaspr.gear.GearApi", true, cl).getMethod("rollRealmLoot", Random.class, String.class, int.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e);
            return null;
        }
        roll = m;
        loader = cl;
        return m;
    }

    private static void warn(Throwable e) {
        if (warned) return;
        warned = true;
        Plugin self = Bukkit.getPluginManager().getPlugin("JasprBackrooms");
        if (self != null) self.getLogger().warning("ARMORY_LOOT_UNAVAILABLE reason=" + e.getClass().getSimpleName());
    }

    /** Adds the armoury's roll for a chest of this tier (0..5; below 0: none) into random empty slots. */
    @SuppressWarnings("unchecked")
    static void add(Inventory inv, Random r, String world, int tier) {
        if (inv == null || r == null || tier < 0) return;
        Method m = method();
        if (m == null) return;
        List<ItemStack> items;
        try {
            Object out = m.invoke(null, r, world, Math.min(5, tier));
            if (!(out instanceof List)) return;
            items = (List<ItemStack>) out;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            warn(e);
            return;
        }
        rolls++;
        for (ItemStack item : items) {
            if (item == null) continue;
            List<Integer> empty = new ArrayList<Integer>();
            for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) empty.add(i);
            if (empty.isEmpty()) return;
            inv.setItem(empty.get(r.nextInt(empty.size())), item);
            added++;
        }
    }
}
