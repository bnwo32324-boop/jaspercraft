package chat.jaspr.biomes;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.NBTTagList;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * So Many Enchantments books for structure loot (2026-09-26). Reads the server's own enchantment registry, where
 * JasprEnchantments registers SME's 130 enchantments under "somanyenchantments:*"; with that plugin absent the pool is
 * empty and callers keep their vanilla book. Curses and SME's loot blacklist (Supreme Protection, Pandora's Curse)
 * are never chosen. Pure function of the Random it is given.
 */
final class SmeLoot {
    private SmeLoot() {}
    private static List<net.minecraft.server.v1_12_R1.Enchantment> pool;

    private static synchronized List<net.minecraft.server.v1_12_R1.Enchantment> pool() {
        if (pool != null) return pool;
        List<net.minecraft.server.v1_12_R1.Enchantment> out = new ArrayList<>();
        for (net.minecraft.server.v1_12_R1.Enchantment e : net.minecraft.server.v1_12_R1.Enchantment.enchantments) {
            MinecraftKey key = net.minecraft.server.v1_12_R1.Enchantment.enchantments.b(e);
            if (key == null || !"somanyenchantments".equals(key.b()) || e.isCursed() || blacklisted(key.getKey())) continue;
            out.add(e);
        }
        out.sort((a, b) -> Integer.compare(net.minecraft.server.v1_12_R1.Enchantment.getId(a), net.minecraft.server.v1_12_R1.Enchantment.getId(b)));
        if (!out.isEmpty()) pool = out;
        return out;
    }

    static boolean blacklisted(String path) { return "supremeprotection".equals(path) || "pandorascurse".equals(path); }

    static boolean blacklisted(Enchantment e) {
        net.minecraft.server.v1_12_R1.Enchantment nms = net.minecraft.server.v1_12_R1.Enchantment.c(e.getId());
        MinecraftKey key = nms == null ? null : net.minecraft.server.v1_12_R1.Enchantment.enchantments.b(nms);
        return key != null && "somanyenchantments".equals(key.b()) && blacklisted(key.getKey());
    }

    /** A book with one SME enchantment, level scaled to the structure tier; null when SME is not installed. */
    static ItemStack book(Random r, int tier) {
        List<net.minecraft.server.v1_12_R1.Enchantment> p = pool();
        if (p.isEmpty()) return null;
        net.minecraft.server.v1_12_R1.Enchantment e = p.get(r.nextInt(p.size()));
        if (e.isTreasure() && tier < 4 && r.nextInt(3) != 0) e = p.get(r.nextInt(p.size()));
        int max = Math.max(1, e.getMaxLevel());
        int level = Math.max(1, Math.min(max, 1 + r.nextInt(Math.max(1, (max * Math.max(1, Math.min(5, tier)) + 4) / 5))));
        net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(new ItemStack(Material.ENCHANTED_BOOK));
        NBTTagCompound root = nms.hasTag() ? nms.getTag() : new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        NBTTagCompound c = new NBTTagCompound();
        c.setShort("id", (short) net.minecraft.server.v1_12_R1.Enchantment.getId(e));
        c.setShort("lvl", (short) level);
        list.add(c);
        root.set("StoredEnchantments", list);
        nms.setTag(root);
        return CraftItemStack.asBukkitCopy(nms);
    }
}
