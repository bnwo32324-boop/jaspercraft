package chat.jaspr.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * The stat roster, rebuilt from the GokiStats design and widened (2026-10-02, owner: "add many more at your discretion").
 *
 * Each stat carries the numbers that define it: how many levels it can reach, what one level is worth, and what it
 * actually does. Most stats grow geometrically - a stat at level n is worth base^n - which is what makes early levels
 * cheap and meaningful and late levels a real investment; the newer chance-based stats grow by a fixed amount per level
 * ({@link #perLevel}). Levels 1-10 are the ordinary ranks; levels 11-15 are mastery ranks, each worth half an ordinary
 * level ({@link #effective}). Upgrade cost is shared by every stat and lives in {@link StatCosts}.
 */
enum StatType {
    MINING       ("Mining",          Category.GATHERING, Material.DIAMOND_PICKAXE, 15, 1.3000d, 0, "Break stone and ore faster"),
    DIGGING      ("Digging",         Category.GATHERING, Material.DIAMOND_SPADE, 15, 1.3000d, 0, "Break earth faster"),
    CHOPPING     ("Chopping",        Category.GATHERING, Material.DIAMOND_AXE, 15, 1.3000d, 0, "Break wood faster"),
    TRIMMING     ("Trimming",        Category.GATHERING, Material.SHEARS, 15, 1.3000d, 0, "Break foliage faster"),
    PROTECTION   ("Protection",      Category.DEFENSE, Material.DIAMOND_CHESTPLATE, 15, 1.0400d, 0, "Take less damage while armoured"),
    TEMPERING    ("Tempering",       Category.SURVIVAL, Material.ANVIL, 15, 1.0600d, 0, "Your gear wears out more slowly"),
    TOUGH_SKIN   ("Tough Skin",      Category.DEFENSE, Material.LEATHER_CHESTPLATE, 15, 1.0300d, 0, "Take less damage from everything"),
    FEATHER_FALL ("Feather Fall",    Category.DEFENSE, Material.FEATHER, 15, 1.1200d, 0, "Take less falling damage"),
    LEAPER_H     ("Leaper",          Category.MOVEMENT, Material.GOLD_BOOTS, 15, 1.0650d, 0, "Jump further forward"),
    LEAPER_V     ("High Leaper",     Category.MOVEMENT, Material.RABBIT_FOOT, 15, 1.0650d, 0, "Jump higher"),
    SWIMMING     ("Swimming",        Category.MOVEMENT, Material.WATER_BUCKET, 15, 1.1000d, 0, "Swim faster"),
    CLIMBING     ("Climbing",        Category.MOVEMENT, Material.LADDER, 15, 1.1000d, 0, "Climb ladders and vines faster"),
    PUGILISM     ("Pugilism",        Category.COMBAT, Material.SKULL_ITEM, 15, 1.0300d, 0, "Hit harder with bare hands"),
    SWORDSMANSHIP("Swordsmanship",   Category.COMBAT, Material.DIAMOND_SWORD, 15, 1.0895d, 0, "Hit harder with a sword"),
    BOWMANSHIP   ("Bowmanship",      Category.COMBAT, Material.BOW, 15, 1.0895d, 0, "Shoot harder with a bow"),
    REAPER       ("Reaper",          Category.COMBAT, Material.ROTTEN_FLESH, 15, 1.0768d, 0, "Gain more experience from kills"),
    FURNACE      ("Furnace Finesse", Category.GATHERING, Material.FURNACE, 15, 1.1000d, 0, "Your furnaces smelt faster"),
    TREASURE     ("Treasure Finder", Category.GATHERING, Material.CHEST, 5, 1.0000d, 0, "Find treasure in grass, dirt, sand and leaves"),
    STEALTH      ("Stealth",         Category.SURVIVAL, Material.EYE_OF_ENDER, 15, 1.3416d, 0, "Monsters notice you from further away"),
    STEADY_GUARD ("Steady Guard",    Category.DEFENSE, Material.SHIELD, 15, 1.3615d, 0, "Blocking absorbs far more"),
    MAGICIAN     ("Mining Magician", Category.GATHERING, Material.EMERALD, 15, 1.1000d, 0, "Ores sometimes yield more"),
    HEALTH       ("Health",          Category.DEFENSE, Material.GOLDEN_APPLE, 15, 1.0000d, 0, "Raises your maximum health"),
    ROLL         ("Roll",            Category.DEFENSE, Material.LEATHER_BOOTS, 15, 1.1000d, 0, "Roll with a blow and take less of it"),

    // 2026-10-02: combat
    PRECISION    ("Precision",       Category.COMBAT, Material.ARROW, 15, 1.0d, 0.02d, "Chance to land a critical hit (+50% damage)"),
    FEROCITY     ("Ferocity",        Category.COMBAT, Material.BLAZE_POWDER, 15, 1.0d, 0.08d, "Your critical hits deal more damage"),
    BLOODTHIRST  ("Bloodthirst",     Category.COMBAT, Material.REDSTONE, 15, 1.0d, 0.01d, "Heal from the melee damage you deal"),
    AXEMANSHIP   ("Axemanship",      Category.COMBAT, Material.IRON_AXE, 15, 1.0895d, 0, "Hit harder with an axe"),
    GUNSLINGER   ("Gunslinger",      Category.COMBAT, Material.FLINT_AND_STEEL, 15, 1.0600d, 0, "Your firearms hit harder"),
    SLAYER       ("Slayer",          Category.COMBAT, Material.NETHER_STAR, 15, 1.0500d, 0, "Hit bosses and elite monsters harder"),
    // defense
    EVASION      ("Evasion",         Category.DEFENSE, Material.ELYTRA, 15, 1.0d, 0.015d, "Chance to dodge an attack entirely"),
    FIRE_WARD    ("Fire Ward",       Category.DEFENSE, Material.MAGMA_CREAM, 15, 1.0500d, 0, "Take less damage from fire and lava"),
    BLAST_WARD   ("Blast Ward",      Category.DEFENSE, Material.TNT, 15, 1.0600d, 0, "Take less damage from explosions"),
    ANTIDOTE     ("Antidote",        Category.DEFENSE, Material.MILK_BUCKET, 15, 1.0500d, 0, "Take less damage from poison, wither and magic"),
    STEADFAST    ("Steadfast",       Category.DEFENSE, Material.IRON_BLOCK, 15, 1.0d, 0.04d, "Resist knockback"),
    SECOND_WIND  ("Second Wind",     Category.DEFENSE, Material.GHAST_TEAR, 15, 1.0d, 8.0d, "Near death, catch your breath and heal"),
    // survival
    RECOVERY     ("Recovery",        Category.SURVIVAL, Material.SPECKLED_MELON, 15, 1.0600d, 0, "Heal faster while well fed"),
    ENDURANCE    ("Endurance",       Category.SURVIVAL, Material.COOKED_BEEF, 15, 1.0600d, 0, "Grow hungry more slowly"),
    LUCK         ("Luck",            Category.SURVIVAL, Material.GOLD_NUGGET, 15, 1.0d, 0.4d, "Better fishing catches and chest loot"),
    SCHOLAR      ("Scholar",         Category.SURVIVAL, Material.BOOK, 15, 1.0350d, 0, "Gain more experience from everything"),
    SCAVENGER    ("Scavenger",       Category.SURVIVAL, Material.BONE, 15, 1.0d, 0.03d, "Monsters sometimes drop extra loot"),
    ENGINEERING  ("Engineering",     Category.SURVIVAL, Material.DISPENSER, 15, 1.0400d, 0, "Your sentry turrets hit harder"),
    // gathering
    ANGLER       ("Angler",          Category.GATHERING, Material.FISHING_ROD, 15, 1.0d, 0.05d, "Sometimes catch two fish at once"),
    GREEN_THUMB  ("Green Thumb",     Category.GATHERING, Material.WHEAT, 15, 1.0d, 0.05d, "Harvests sometimes yield more"),
    LUMBERJACK   ("Lumberjack",      Category.GATHERING, Material.LOG, 15, 1.0d, 0.04d, "Trees sometimes give an extra log"),
    // movement
    FLEET_FOOT   ("Fleet Foot",      Category.MOVEMENT, Material.SUGAR, 15, 1.0d, 0.015d, "Move faster on foot");

    /** The menu's tabs. */
    enum Category {
        COMBAT("Combat", Material.IRON_SWORD, "Weapons, criticals and the hunt"),
        DEFENSE("Defense", Material.IRON_CHESTPLATE, "Armour, wards, dodging and health"),
        SURVIVAL("Survival", Material.GOLDEN_APPLE, "Hunger, healing, luck, experience and turrets"),
        GATHERING("Gathering", Material.IRON_PICKAXE, "Mining, woodcutting, farming and fishing"),
        MOVEMENT("Movement", Material.FEATHER, "Running, jumping, swimming and climbing");
        final String title;
        final Material icon;
        final String blurb;
        Category(String title, Material icon, String blurb) { this.title = title; this.icon = icon; this.blurb = blurb; }

        List<StatType> stats() {
            List<StatType> out = new ArrayList<StatType>();
            for (StatType s : StatType.values()) if (s.category == this) out.add(s);
            return out;
        }
    }

    /** The highest ordinary rank; ranks above it are mastery ranks worth half as much. */
    static final int ORDINARY = 10;

    final String display;
    final Category category;
    final Material icon;
    final int cap;
    final double base;
    /** For chance-based stats: what one (effective) level adds. Zero for geometric stats. */
    final double perLevel;
    final String description;

    StatType(String display, Category category, Material icon, int cap, double base, double perLevel, String description) {
        this.display = display;
        this.category = category;
        this.icon = icon;
        this.cap = cap;
        this.base = base;
        this.perLevel = perLevel;
        this.description = description;
    }

    String key() { return name().toLowerCase(Locale.ROOT); }

    /**
     * Levels as they count: ordinary ranks count fully, mastery ranks (above {@link #ORDINARY}) half, and nothing past
     * the cap counts at all.
     */
    static double effective(int level, int cap) {
        int l = Math.max(0, Math.min(level, cap));
        return Math.min(l, ORDINARY) + 0.5d * Math.max(0, l - ORDINARY);
    }

    double effective(int level) { return effective(level, cap); }

    /**
     * What this stat is worth at the given level. Geometric growth, so the value at level n is base to the power n
     * (mastery ranks count half), and level 0 is always exactly 1.0 - no bonus, no penalty.
     */
    double multiplier(int level, int cap) {
        if (level <= 0) return 1.0d;
        return Math.pow(base, effective(level, cap));
    }

    double multiplier(int level) { return multiplier(level, cap); }

    /** For chance-based stats: the value at a level ({@link #perLevel} per effective level). */
    double linear(int level, int cap) { return perLevel * effective(level, cap); }

    /** The same growth expressed as a fraction, for stats that read better as "+18%". */
    double bonusFraction(int level) {
        return multiplier(level) - 1.0d;
    }

    boolean isLinear() { return perLevel > 0; }

    static StatType byKey(String key) {
        if (key == null) return null;
        for (StatType stat : values()) if (stat.key().equals(key.toLowerCase(Locale.ROOT))) return stat;
        return null;
    }
}
