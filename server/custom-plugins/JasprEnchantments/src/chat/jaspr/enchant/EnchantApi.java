package chat.jaspr.enchant;

import chat.jaspr.enchant.fx.Enchanting;
import chat.jaspr.enchant.nms.Registrar;
import chat.jaspr.enchant.nms.SmeEnchantment;
import net.minecraft.server.v1_12_R1.ItemEnchantedBook;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.WeightedRandomEnchant;
import org.bukkit.craftbukkit.v1_12_R1.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Public helpers for other plugins (call them by reflection on chat.jaspr.enchant.EnchantApi). All
 * methods draw from every registered enchantment (vanilla + the 130 SME ones) and obey SME's default
 * Random/Level loot blacklists (Supreme Protection, Pandora's Curse) and its "no curses on books" rule.
 * Pass {@code null} as the item to roll for a book. Must be called on the server thread.
 */
public final class EnchantApi {
    private EnchantApi() {}

    public static boolean isReady() {
        return Registrar.registered;
    }

    private static net.minecraft.server.v1_12_R1.ItemStack nms(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || item.getType() == org.bukkit.Material.ENCHANTED_BOOK) {
            return new net.minecraft.server.v1_12_R1.ItemStack(Items.BOOK);
        }
        return CraftItemStack.asNMSCopy(item);
    }

    private static Map.Entry<Enchantment, Integer> entry(WeightedRandomEnchant w) {
        return new AbstractMap.SimpleImmutableEntry<>(Enchanting.bukkit(w.enchantment), w.level);
    }

    /**
     * One enchantment + level as the first pick of enchant_with_levels ("levels" = power), i.e. SME's
     * EnchantmentHelper.buildEnchantmentList with the Level blacklist. Returns null when nothing fits.
     */
    public static Map.Entry<Enchantment, Integer> randomLootEnchantment(Random random, ItemStack item, int power, boolean treasure) {
        List<WeightedRandomEnchant> list = Enchanting.build(random, nms(item), power, treasure, Enchanting.LEVEL_BLACKLIST);
        return list.isEmpty() ? null : entry(list.get(0));
    }

    /** The full enchant_with_levels result (one or more enchantments, mutually compatible). */
    public static Map<Enchantment, Integer> randomLootEnchantments(Random random, ItemStack item, int power, boolean treasure) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        for (WeightedRandomEnchant w : Enchanting.build(random, nms(item), power, treasure, Enchanting.LEVEL_BLACKLIST)) {
            out.put(Enchanting.bukkit(w.enchantment), w.level);
        }
        return out;
    }

    /** enchant_randomly: uniform over enchantments applicable to the item (all for books), uniform level, Random blacklist. */
    public static Map.Entry<Enchantment, Integer> randomUniformEnchantment(Random random, ItemStack item) {
        WeightedRandomEnchant w = Enchanting.randomly(random, nms(item));
        return w == null ? null : entry(w);
    }

    /** Applies enchant_with_levels to a copy of the item (null / book -> a new enchanted book). */
    public static ItemStack enchantWithLevels(Random random, ItemStack item, int power, boolean treasure) {
        net.minecraft.server.v1_12_R1.ItemStack base = nms(item);
        List<WeightedRandomEnchant> list = Enchanting.build(random, base, power, treasure, Enchanting.LEVEL_BLACKLIST);
        return apply(base, list);
    }

    /** Applies enchant_randomly to a copy of the item (null / book -> a new enchanted book). */
    public static ItemStack enchantRandomly(Random random, ItemStack item) {
        net.minecraft.server.v1_12_R1.ItemStack base = nms(item);
        WeightedRandomEnchant w = Enchanting.randomly(random, base);
        return apply(base, w == null ? java.util.Collections.<WeightedRandomEnchant>emptyList() : java.util.Collections.singletonList(w));
    }

    private static ItemStack apply(net.minecraft.server.v1_12_R1.ItemStack base, List<WeightedRandomEnchant> list) {
        if (base.getItem() == Items.BOOK) {
            if (list.isEmpty()) return CraftItemStack.asBukkitCopy(base);
            net.minecraft.server.v1_12_R1.ItemStack book = new net.minecraft.server.v1_12_R1.ItemStack(Items.ENCHANTED_BOOK);
            for (WeightedRandomEnchant w : list) ItemEnchantedBook.a(book, w);
            return CraftItemStack.asBukkitCopy(book);
        }
        net.minecraft.server.v1_12_R1.ItemStack out = base.cloneItemStack();
        for (WeightedRandomEnchant w : list) out.addEnchantment(w.enchantment, w.level);
        return CraftItemStack.asBukkitCopy(out);
    }

    /** SME registry name (e.g. "advancedsharpness") of a Bukkit enchantment, or null for vanilla ones. */
    public static String smeName(Enchantment e) {
        if (!(e instanceof CraftEnchantment)) return null;
        net.minecraft.server.v1_12_R1.Enchantment n = ((CraftEnchantment) e).getHandle();
        return n instanceof SmeEnchantment ? ((SmeEnchantment) n).def.regName : null;
    }

    /** Bukkit enchantment for an SME registry name (e.g. "lifesteal"), or null. */
    public static Enchantment bySmeName(String regName) {
        SmeEnchantment s = Registrar.byReg(regName);
        return s == null ? null : Enchantment.getById(s.def.id);
    }

    public static int smeCount() {
        return Registrar.bukkitRegistered;
    }
}
