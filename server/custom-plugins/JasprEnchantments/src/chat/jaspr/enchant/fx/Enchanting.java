package chat.jaspr.enchant.fx;

import chat.jaspr.enchant.nms.SmeEnchantment;
import net.minecraft.server.v1_12_R1.Enchantment;
import net.minecraft.server.v1_12_R1.EnchantmentManager;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.Items;
import net.minecraft.server.v1_12_R1.MathHelper;
import net.minecraft.server.v1_12_R1.MinecraftKey;
import net.minecraft.server.v1_12_R1.WeightedRandom;
import net.minecraft.server.v1_12_R1.WeightedRandomEnchant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Forge EnchantmentHelper.buildEnchantmentList / getEnchantmentDatas as they behave with SME installed:
 * candidates use SME's "Can apply on enchantment table" sets (canApplyAtEnchantingTable) and
 * isAllowedOnBooks (SME curses never go on books), then SME's blacklist mixin filters the list
 * (Enchanting Table blacklist for the table, Level blacklist for everything else).
 */
public final class Enchanting {
    private Enchanting() {}

    public static final Set<Enchantment> TABLE_BLACKLIST = new HashSet<>();
    public static final Set<Enchantment> LIBRARIAN_BLACKLIST = new HashSet<>();
    public static final Set<Enchantment> RANDOM_BLACKLIST = new HashSet<>();
    public static final Set<Enchantment> LEVEL_BLACKLIST = new HashSet<>();

    private static final String[] TABLE_AND_LIBRARIAN = {
            "ancientswordmastery", "ancientsealedcurses", "advancedbaneofarthropods", "advancedblastprotection",
            "advancedefficiency", "advancedfeatherfalling", "advancedfireaspect", "advancedfireprotection", "advancedflame",
            "advancedknockback", "advancedlooting", "advancedluckofthesea", "advancedlure", "advancedmending", "advancedpower",
            "advancedprojectileprotection", "advancedprotection", "advancedpunch", "advancedsharpness", "advancedsmite",
            "advancedthorns", "supremebaneofarthropods", "supremefireaspect", "supremeflame", "supremesharpness", "supremesmite",
            "supremeprotection", "pandorascurse"};
    private static final String[] RANDOM_AND_LEVEL = {"supremeprotection", "pandorascurse"};

    public static void initBlacklists() {
        for (String s : TABLE_AND_LIBRARIAN) {
            Enchantment e = Enchantment.enchantments.get(new MinecraftKey("somanyenchantments", s));
            TABLE_BLACKLIST.add(e);
            LIBRARIAN_BLACKLIST.add(e);
        }
        for (String s : RANDOM_AND_LEVEL) {
            Enchantment e = Enchantment.enchantments.get(new MinecraftKey("somanyenchantments", s));
            RANDOM_BLACKLIST.add(e);
            LEVEL_BLACKLIST.add(e);
        }
    }

    /** Forge Enchantment.canApplyAtEnchantingTable for this enchantment and item */
    public static boolean tableApplies(Enchantment e, ItemStack stack) {
        if (e instanceof SmeEnchantment) return ((SmeEnchantment) e).tableApplies(stack);
        return e.itemTarget.canEnchant(stack.getItem());
    }

    /** Enchantment.isAllowedOnBooks with SME (EnchantmentBase: enabled; EnchantmentCurse: canCursesBeAppliedToBooks=false) */
    public static boolean allowedOnBooks(Enchantment e) {
        if (e instanceof SmeEnchantment) return !((SmeEnchantment) e).def.curse;
        return true;
    }

    /** getEnchantmentDatas + SME blacklist filter */
    public static List<WeightedRandomEnchant> candidates(int level, ItemStack stack, boolean allowTreasure, Set<Enchantment> blacklist) {
        List<WeightedRandomEnchant> list = new ArrayList<>();
        boolean book = stack.getItem() == Items.BOOK;
        for (Enchantment e : Enchantment.enchantments) {
            if (e.isTreasure() && !allowTreasure) continue;
            if (!(tableApplies(e, stack) || (book && allowedOnBooks(e)))) continue;
            for (int i = e.getMaxLevel(); i > e.getStartLevel() - 1; --i) {
                if (level >= e.a(i) && level <= e.b(i)) {
                    list.add(new WeightedRandomEnchant(e, i));
                    break;
                }
            }
        }
        if (blacklist != null && !blacklist.isEmpty()) list.removeIf(d -> blacklist.contains(d.enchantment));
        return list;
    }

    /** EnchantmentHelper.buildEnchantmentList */
    public static List<WeightedRandomEnchant> build(Random rand, ItemStack stack, int level, boolean allowTreasure, Set<Enchantment> blacklist) {
        List<WeightedRandomEnchant> list = new ArrayList<>();
        int i = stack.getItem().c();
        if (i <= 0) return list;
        level = level + 1 + rand.nextInt(i / 4 + 1) + rand.nextInt(i / 4 + 1);
        float f = (rand.nextFloat() + rand.nextFloat() - 1.0F) * 0.15F;
        level = MathHelper.clamp(Math.round((float) level + (float) level * f), 1, Integer.MAX_VALUE);
        List<WeightedRandomEnchant> list1 = candidates(level, stack, allowTreasure, blacklist);
        if (!list1.isEmpty()) {
            list.add(WeightedRandom.a(rand, list1));
            while (rand.nextInt(50) <= level) {
                EnchantmentManager.a(list1, list.get(list.size() - 1));
                if (list1.isEmpty()) break;
                list.add(WeightedRandom.a(rand, list1));
                level /= 2;
            }
        }
        return list;
    }

    /** ContainerEnchantment.getEnchantmentList with the Enchanting Table blacklist */
    public static List<WeightedRandomEnchant> tableList(ItemStack stack, int xpSeed, int slot, int cost) {
        Random rand = new Random((long) (xpSeed + slot));
        List<WeightedRandomEnchant> list = build(rand, stack, cost, false, TABLE_BLACKLIST);
        if (stack.getItem() == Items.BOOK && list.size() > 1) list.remove(rand.nextInt(list.size()));
        return list;
    }

    /** EnchantRandomly (enchant_randomly) with SME's Random blacklist: one uniform pick, uniform level */
    public static WeightedRandomEnchant randomly(Random rand, ItemStack stack) {
        List<Enchantment> list = new ArrayList<>();
        boolean book = stack.getItem() == Items.BOOK || stack.getItem() == Items.ENCHANTED_BOOK;
        for (Enchantment e : Enchantment.enchantments) {
            if (RANDOM_BLACKLIST.contains(e)) continue;
            if (book || e.canEnchant(stack)) list.add(e);
        }
        if (list.isEmpty()) return null;
        Enchantment e = list.get(rand.nextInt(list.size()));
        int lvl = MathHelper.nextInt(rand, e.getStartLevel(), e.getMaxLevel());
        return new WeightedRandomEnchant(e, lvl);
    }

    public static org.bukkit.enchantments.Enchantment bukkit(Enchantment e) {
        return org.bukkit.enchantments.Enchantment.getById(Enchantment.getId(e));
    }
}
