package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * An enhanced weapon or piece of armour, stored in the item's own NBT.
 *
 * Keeping the data on the item rather than in a side table is the whole point: a levelled sword is
 * still levelled after it is dropped, traded, put in a chest or carried across a restart, and two
 * copies of the same weapon have their own separate histories.
 */
final class Armament {
    private static final String ROOT = "JasprArmament";
    private static final String LEVEL = "Level";
    private static final String EXP = "Exp";
    private static final String RARITY = "Rarity";
    private static final String TOKENS = "Tokens";
    private static final String ABILITIES = "Abilities";

    private Armament() {}

    // ------------------------------------------------------------------ eligibility

    static boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW");
    }

    static boolean isArmour(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    static boolean isEligible(ItemStack item) {
        return item != null && item.getType() != Material.AIR && (isWeapon(item) || isArmour(item));
    }

    static boolean isEnhanced(ItemStack item) {
        NBTTagCompound tag = read(item);
        return tag != null && tag.hasKey(LEVEL);
    }

    // ------------------------------------------------------------------ raw NBT

    private static NBTTagCompound read(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
            if (nms == null || !nms.hasTag()) return null;
            NBTTagCompound tag = nms.getTag();
            if (tag == null || !tag.hasKeyOfType(ROOT, 10)) return null;
            return tag.getCompound(ROOT);
        } catch (Throwable unsupported) {
            return null;
        }
    }

    /** Applies a change to the item's own compound and hands back the rewritten stack. */
    private static ItemStack write(ItemStack item, Mutator mutator) {
        try {
            net.minecraft.server.v1_12_R1.ItemStack nms = CraftItemStack.asNMSCopy(item);
            if (nms == null) return item;
            NBTTagCompound tag = nms.hasTag() ? nms.getTag() : new NBTTagCompound();
            NBTTagCompound ours = tag.hasKeyOfType(ROOT, 10) ? tag.getCompound(ROOT) : new NBTTagCompound();
            mutator.apply(ours);
            tag.set(ROOT, ours);
            nms.setTag(tag);
            return CraftItemStack.asBukkitCopy(nms);
        } catch (Throwable unsupported) {
            return item;
        }
    }

    interface Mutator { void apply(NBTTagCompound tag); }

    // ------------------------------------------------------------------ accessors

    static int level(ItemStack item) {
        NBTTagCompound tag = read(item);
        return tag == null ? 0 : tag.getInt(LEVEL);
    }

    static int experience(ItemStack item) {
        NBTTagCompound tag = read(item);
        return tag == null ? 0 : tag.getInt(EXP);
    }

    static int tokens(ItemStack item) {
        NBTTagCompound tag = read(item);
        return tag == null ? 0 : tag.getInt(TOKENS);
    }

    static Rarity rarity(ItemStack item) {
        NBTTagCompound tag = read(item);
        return tag == null ? Rarity.DEFAULT : Rarity.byName(tag.getString(RARITY));
    }

    static int abilityLevel(ItemStack item, AbilityType ability) {
        NBTTagCompound tag = read(item);
        if (tag == null || !tag.hasKeyOfType(ABILITIES, 10)) return 0;
        return tag.getCompound(ABILITIES).getInt(ability.key());
    }

    static List<AbilityType> abilitiesOn(ItemStack item) {
        List<AbilityType> found = new ArrayList<AbilityType>();
        NBTTagCompound tag = read(item);
        if (tag == null || !tag.hasKeyOfType(ABILITIES, 10)) return found;
        NBTTagCompound abilities = tag.getCompound(ABILITIES);
        for (AbilityType ability : AbilityType.values()) {
            if (abilities.getInt(ability.key()) > 0) found.add(ability);
        }
        return found;
    }

    // ------------------------------------------------------------------ mutation

    /** Turns a plain item into an enhanced one, rolling its rarity once and for all. */
    static ItemStack enhance(ItemStack item, final Rarity rarity) {
        return decorate(write(item, new Mutator() {
            @Override public void apply(NBTTagCompound tag) {
                tag.setInt(LEVEL, 1);
                tag.setInt(EXP, 0);
                tag.setInt(TOKENS, 0);
                tag.setString(RARITY, rarity.name());
                tag.set(ABILITIES, new NBTTagCompound());
            }
        }));
    }

    static ItemStack setLevel(ItemStack item, final int level, final int exp, final int tokens) {
        return decorate(write(item, new Mutator() {
            @Override public void apply(NBTTagCompound tag) {
                tag.setInt(LEVEL, Math.max(1, level));
                tag.setInt(EXP, Math.max(0, exp));
                tag.setInt(TOKENS, Math.max(0, tokens));
            }
        }));
    }

    static ItemStack setAbilityLevel(ItemStack item, final AbilityType ability, final int level, final int tokensLeft) {
        return decorate(write(item, new Mutator() {
            @Override public void apply(NBTTagCompound tag) {
                NBTTagCompound abilities = tag.hasKeyOfType(ABILITIES, 10)
                        ? tag.getCompound(ABILITIES) : new NBTTagCompound();
                abilities.setInt(ability.key(), Math.max(0, level));
                tag.set(ABILITIES, abilities);
                tag.setInt(TOKENS, Math.max(0, tokensLeft));
            }
        }));
    }

    // ------------------------------------------------------------------ progression

    /** Experience needed to move from {@code level} to the next one. */
    static int experienceForNext(int level, RpgConfig settings) {
        double needed = settings.level1Experience * Math.pow(settings.experienceMultiplier, Math.max(0, level - 1));
        return (int) Math.max(1, Math.round(needed));
    }

    /**
     * Adds experience and levels the item up as far as the total allows. Returns how many levels
     * were gained, so the caller can announce it and hand out the tokens.
     */
    static int addExperience(ItemStack item, int amount, RpgConfig settings, int[] outLevelExp) {
        int level = level(item);
        if (level <= 0 || amount <= 0) return 0;
        int exp = experience(item) + amount;
        int gained = 0;

        while (level < settings.maxLevel) {
            int needed = experienceForNext(level, settings);
            if (exp < needed) break;
            exp -= needed;
            level++;
            gained++;
        }
        if (level >= settings.maxLevel) exp = 0;

        outLevelExp[0] = level;
        outLevelExp[1] = exp;
        return gained;
    }

    // ------------------------------------------------------------------ presentation

    /** Rewrites the lore so the item reads at a glance. Regenerated from NBT every time. */
    static ItemStack decorate(ItemStack item) {
        NBTTagCompound tag = read(item);
        if (tag == null || !tag.hasKey(LEVEL)) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Rarity rarity = Rarity.byName(tag.getString(RARITY));
        int level = tag.getInt(LEVEL);
        int tokens = tag.getInt(TOKENS);

        List<String> lore = new ArrayList<String>();
        lore.add(rarity.coloured() + ChatColor.GRAY + "  Level " + ChatColor.WHITE + level);
        if (rarity.bonus > 0.0d) {
            int percent = (int) Math.round(rarity.bonus * 100.0d);
            lore.add(ChatColor.GRAY + (isWeapon(item) ? "+" + percent + "% damage" : "+" + percent + "% protection"));
        }
        if (tokens > 0) {
            lore.add(ChatColor.AQUA + "" + tokens + ChatColor.GRAY + " ability token(s) unspent");
        }

        List<AbilityType> abilities = abilitiesOn(item);
        if (!abilities.isEmpty()) {
            lore.add("");
            NBTTagCompound stored = tag.getCompound(ABILITIES);
            for (AbilityType ability : abilities) {
                lore.add(ability.color + ability.display + ChatColor.GRAY + " "
                        + roman(stored.getInt(ability.key())));
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String roman(int value) {
        switch (value) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(value);
        }
    }

    /** Rolls whether a freshly acquired item becomes enhanced at all. */
    static ItemStack maybeEnhance(ItemStack item, RpgConfig settings, Random random) {
        return maybeEnhance(item, settings, random, settings.enchantChance);
    }

    /** As above, but with the roll chance supplied - creative uses its own. */
    static ItemStack maybeEnhance(ItemStack item, RpgConfig settings, Random random, double chance) {
        if (!isEligible(item) || isEnhanced(item)) return item;
        if (isArmour(item) && !settings.armorEnabled) return item;
        if (chance < 1.0d && random.nextDouble() > chance) return item;
        Rarity rarity = Rarity.roll(random);
        if (rarity == Rarity.DEFAULT) return item;
        return enhance(item, rarity);
    }
}
