package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * The ability roster, rebuilt from Enhanced Armaments, plus the Gunsmith roster for firearms.
 *
 * Two numbers govern every ability and both are taken from the original: {@code tier} is the item
 * level you must reach before the ability can be unlocked at all, and {@code maxLevel} is how far
 * it can then be pushed. Each step, unlock included, costs one ability token, and tokens are only
 * earned by levelling the item itself - which is what stops a fresh sword wearing a full loadout.
 *
 * Gun abilities (owner request 2026-09-25: "K on a gun ... the same amount of upgrades, specialised
 * for guns"): nine, like the melee roster. The four gun parts are read by JasprApocalypse's Arsenal from
 * the item NBT (keys below are part of that contract); the five rounds work here, on hit or on kill.
 */
enum AbilityType {
    // ---- weapons -------------------------------------------------- tier  max
    FIRE          ("Fire",           Kind.WEAPON, true,  ChatColor.RED,          1, 3, "Sets the target alight", Material.BLAZE_POWDER),
    FROST         ("Frost",          Kind.WEAPON, true,  ChatColor.AQUA,         1, 3, "Freezes the target in place", Material.ICE),
    POISON        ("Poison",         Kind.WEAPON, true,  ChatColor.DARK_GREEN,   1, 3, "Poisons the target", Material.SPIDER_EYE),
    INNATE        ("Innate",         Kind.WEAPON, true,  ChatColor.DARK_RED,     2, 3, "Wounds stack and bleed", Material.REDSTONE),
    ILLUMINATION  ("Illumination",   Kind.WEAPON, false, ChatColor.YELLOW,       2, 1, "Weakens the target", Material.GLOWSTONE_DUST),
    ETHEREAL      ("Ethereal",       Kind.WEAPON, false, ChatColor.LIGHT_PURPLE, 2, 2, "Kills repair the weapon", Material.GHAST_TEAR),
    BOMBASTIC     ("Bombastic",      Kind.WEAPON, true,  ChatColor.GOLD,         3, 3, "Detonates around the target", Material.TNT),
    CRITICAL_POINT("Critical Point", Kind.WEAPON, true,  ChatColor.DARK_AQUA,    3, 3, "Chance to strike for a share of max health", Material.ARROW),
    BLOODTHIRST   ("Bloodthirst",    Kind.WEAPON, false, ChatColor.DARK_PURPLE,  3, 2, "Heals you for part of the damage dealt", Material.GOLDEN_APPLE),

    // ---- armour --------------------------------------------------- tier  max
    MOLTEN        ("Molten",         Kind.ARMOUR, true,  ChatColor.RED,          2, 2, "Burns whoever strikes you", Material.MAGMA_CREAM),
    FROZEN        ("Frozen",         Kind.ARMOUR, true,  ChatColor.AQUA,         2, 2, "Chills whoever strikes you", Material.SNOW_BALL),
    TOXIC         ("Toxic",          Kind.ARMOUR, true,  ChatColor.DARK_GREEN,   2, 2, "Poisons whoever strikes you", Material.FERMENTED_SPIDER_EYE),
    ADRENALINE    ("Adrenaline",     Kind.ARMOUR, false, ChatColor.GOLD,         2, 2, "Chance to regenerate when hurt", Material.SUGAR),
    BEASTIAL      ("Beastial",       Kind.ARMOUR, false, ChatColor.DARK_RED,     3, 2, "You hit harder at low health", Material.BONE),
    REMEDIAL      ("Remedial",       Kind.ARMOUR, false, ChatColor.GREEN,        3, 2, "Slowly mends your wounds", Material.SPECKLED_MELON),
    HARDENED      ("Hardened",       Kind.ARMOUR, false, ChatColor.GRAY,         3, 2, "Chance to shrug off a blow entirely", Material.IRON_BLOCK),

    // ---- guns: parts (read by Arsenal) ------------------------------ tier  max
    EXTENDED_MAG  ("Extended Magazine", Kind.GUN_PART, false, ChatColor.WHITE,   1, 3, "+20% magazine capacity per rank", Material.HOPPER),
    SPEED_LOADER  ("Speed Loader",      Kind.GUN_PART, false, ChatColor.YELLOW,  1, 3, "-15% reload time per rank", Material.WATCH),
    HAIR_TRIGGER  ("Hair Trigger",      Kind.GUN_PART, false, ChatColor.RED,     2, 3, "-10% time between shots per rank", Material.TRIPWIRE_HOOK),
    MATCH_BARREL  ("Match Barrel",      Kind.GUN_PART, false, ChatColor.AQUA,    2, 2, "+15% range and a tighter spread per rank", Material.END_ROD),
    // ---- guns: rounds (applied on hit / kill here) --------------------------------------------
    INCENDIARY    ("Incendiary Rounds", Kind.GUN_ROUND, true, ChatColor.GOLD,    1, 3, "Hits set the target burning", Material.FIREBALL),
    CRYO          ("Cryo Rounds",       Kind.GUN_ROUND, true, ChatColor.AQUA,    1, 3, "Hits slow the target", Material.PACKED_ICE),
    ARMOR_PIERCING("Armor-Piercing",    Kind.GUN_ROUND, true, ChatColor.GRAY,    2, 3, "Bonus damage against armoured targets", Material.FLINT),
    DEADEYE       ("Deadeye",           Kind.GUN_ROUND, true, ChatColor.DARK_AQUA, 3, 3, "Long shots (20m+) hit harder and can double", Material.EYE_OF_ENDER),
    SCAVENGER     ("Scavenger",         Kind.GUN_ROUND, false, ChatColor.GREEN,  3, 2, "Kills recover iron-nugget ammunition", Material.IRON_NUGGET);

    enum Kind { WEAPON, ARMOUR, GUN_PART, GUN_ROUND }

    final String display;
    final Kind kind;
    /** Kept for the original callers: true for melee weapon abilities. */
    final boolean weapon;
    final boolean active;
    final ChatColor color;
    final int tier;
    final int maxLevel;
    final String description;
    /** What this ability looks like in the menu. Chosen to read at a glance. */
    final Material icon;

    AbilityType(String display, Kind kind, boolean active, ChatColor color,
                int tier, int maxLevel, String description, Material icon) {
        this.display = display;
        this.kind = kind;
        this.weapon = kind == Kind.WEAPON;
        this.active = active;
        this.color = color;
        this.tier = tier;
        this.maxLevel = maxLevel;
        this.description = description;
        this.icon = icon;
    }

    String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    boolean gun() { return kind == Kind.GUN_PART || kind == Kind.GUN_ROUND; }

    /** Item level required before this can be unlocked. */
    int requiredItemLevel() { return tier; }

    /** Tokens to go from {@code current} to the next level. Unlocking counts as the first step. */
    int tokenCost(int current) { return 1 + current; }

    static List<AbilityType> forWeapons() { return of(Kind.WEAPON, Kind.WEAPON); }

    static List<AbilityType> forArmour() { return of(Kind.ARMOUR, Kind.ARMOUR); }

    /** Parts first, then rounds - the order the Gunsmith sheet lays them out in. */
    static List<AbilityType> forGuns() { return of(Kind.GUN_PART, Kind.GUN_ROUND); }

    private static List<AbilityType> of(Kind first, Kind second) {
        List<AbilityType> list = new ArrayList<AbilityType>();
        for (AbilityType ability : values()) if (ability.kind == first) list.add(ability);
        if (second != first) for (AbilityType ability : values()) if (ability.kind == second) list.add(ability);
        return list;
    }

    static AbilityType byKey(String key) {
        if (key == null) return null;
        for (AbilityType ability : values()) if (ability.key().equals(key.toLowerCase(java.util.Locale.ROOT))) return ability;
        return null;
    }
}
