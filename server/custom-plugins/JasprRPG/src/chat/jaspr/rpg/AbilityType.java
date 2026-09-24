package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * The ability roster, rebuilt from Enhanced Armaments.
 *
 * Two numbers govern every ability and both are taken from the original: {@code tier} is the item
 * level you must reach before the ability can be unlocked at all, and {@code maxLevel} is how far
 * it can then be pushed. Each step, unlock included, costs one ability token, and tokens are only
 * earned by levelling the item itself - which is what stops a fresh sword wearing a full loadout.
 */
enum AbilityType {
    // ---- weapons -------------------------------------------------- tier  max
    FIRE          ("Fire",           true,  true,  ChatColor.RED,          1, 3, "Sets the target alight", Material.BLAZE_POWDER),
    FROST         ("Frost",          true,  true,  ChatColor.AQUA,         1, 3, "Freezes the target in place", Material.ICE),
    POISON        ("Poison",         true,  true,  ChatColor.DARK_GREEN,   1, 3, "Poisons the target", Material.SPIDER_EYE),
    INNATE        ("Innate",         true,  true,  ChatColor.DARK_RED,     2, 3, "Wounds stack and bleed", Material.REDSTONE),
    ILLUMINATION  ("Illumination",   true,  false, ChatColor.YELLOW,       2, 1, "Weakens the target", Material.GLOWSTONE_DUST),
    ETHEREAL      ("Ethereal",       true,  false, ChatColor.LIGHT_PURPLE, 2, 2, "Kills repair the weapon", Material.GHAST_TEAR),
    BOMBASTIC     ("Bombastic",      true,  true,  ChatColor.GOLD,         3, 3, "Detonates around the target", Material.TNT),
    CRITICAL_POINT("Critical Point", true,  true,  ChatColor.DARK_AQUA,    3, 3, "Chance to strike for a share of max health", Material.ARROW),
    BLOODTHIRST   ("Bloodthirst",    true,  false, ChatColor.DARK_PURPLE,  3, 2, "Heals you for part of the damage dealt", Material.GOLDEN_APPLE),

    // ---- armour --------------------------------------------------- tier  max
    MOLTEN        ("Molten",         false, true,  ChatColor.RED,          2, 2, "Burns whoever strikes you", Material.MAGMA_CREAM),
    FROZEN        ("Frozen",         false, true,  ChatColor.AQUA,         2, 2, "Chills whoever strikes you", Material.SNOW_BALL),
    TOXIC         ("Toxic",          false, true,  ChatColor.DARK_GREEN,   2, 2, "Poisons whoever strikes you", Material.FERMENTED_SPIDER_EYE),
    ADRENALINE    ("Adrenaline",     false, false, ChatColor.GOLD,         2, 2, "Chance to regenerate when hurt", Material.SUGAR),
    BEASTIAL      ("Beastial",       false, false, ChatColor.DARK_RED,     3, 2, "You hit harder at low health", Material.BONE),
    REMEDIAL      ("Remedial",       false, false, ChatColor.GREEN,        3, 2, "Slowly mends your wounds", Material.SPECKLED_MELON),
    HARDENED      ("Hardened",       false, false, ChatColor.GRAY,         3, 2, "Chance to shrug off a blow entirely", Material.IRON_BLOCK);

    final String display;
    final boolean weapon;
    final boolean active;
    final ChatColor color;
    final int tier;
    final int maxLevel;
    final String description;
    /** What this ability looks like in the menu. Chosen to read at a glance. */
    final Material icon;

    AbilityType(String display, boolean weapon, boolean active, ChatColor color,
                int tier, int maxLevel, String description, Material icon) {
        this.display = display;
        this.weapon = weapon;
        this.active = active;
        this.color = color;
        this.tier = tier;
        this.maxLevel = maxLevel;
        this.description = description;
        this.icon = icon;
    }

    String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    /** Item level required before this can be unlocked. */
    int requiredItemLevel() { return tier; }

    /** Tokens to go from {@code current} to the next level. Unlocking counts as the first step. */
    int tokenCost(int current) { return 1 + current; }

    static List<AbilityType> forWeapons() {
        List<AbilityType> list = new ArrayList<AbilityType>();
        for (AbilityType ability : values()) if (ability.weapon) list.add(ability);
        return list;
    }

    static List<AbilityType> forArmour() {
        List<AbilityType> list = new ArrayList<AbilityType>();
        for (AbilityType ability : values()) if (!ability.weapon) list.add(ability);
        return list;
    }

    static AbilityType byKey(String key) {
        if (key == null) return null;
        for (AbilityType ability : values()) if (ability.key().equals(key.toLowerCase(java.util.Locale.ROOT))) return ability;
        return null;
    }
}
