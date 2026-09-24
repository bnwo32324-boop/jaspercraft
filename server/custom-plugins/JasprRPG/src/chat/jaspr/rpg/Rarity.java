package chat.jaspr.rpg;

import java.util.Random;
import org.bukkit.ChatColor;

/**
 * How good a piece of gear turned out to be.
 *
 * Rarity is rolled once, when an item first becomes enhanced, and never changes afterwards. It
 * decides two things: how much the item's abilities are worth, and how many ability slots it can
 * ever hold. Weights fall off steeply, so Ancient gear is something you remember finding.
 */
enum Rarity {
    DEFAULT   ("Default",    ChatColor.GRAY,        0,    1.00d, 0, 0.00d),
    BASIC     ("Basic",      ChatColor.WHITE,      50,    1.00d, 1, 0.00d),
    UNCOMMON  ("Uncommon",   ChatColor.GREEN,      25,    1.10d, 2, 0.05d),
    RARE      ("Rare",       ChatColor.BLUE,       12,    1.20d, 3, 0.11d),
    ULTRA_RARE("Ultra Rare", ChatColor.DARK_PURPLE, 7,    1.35d, 4, 0.18d),
    LEGENDARY ("Legendary",  ChatColor.GOLD,        4,    1.55d, 5, 0.28d),
    ANCIENT   ("Ancient",    ChatColor.DARK_RED,    2,    1.80d, 6, 0.40d);

    final String display;
    final ChatColor color;
    final int weight;
    final double effect;
    final int abilitySlots;
    /**
     * The buff the rarity carries on its own, before any ability is unlocked: extra damage on a
     * weapon, or damage shrugged off by a piece of armour. This is why a Legendary sword is worth
     * carrying even with no abilities bought yet.
     */
    final double bonus;

    Rarity(String display, ChatColor color, int weight, double effect, int abilitySlots, double bonus) {
        this.display = display;
        this.color = color;
        this.weight = weight;
        this.effect = effect;
        this.abilitySlots = abilitySlots;
        this.bonus = bonus;
    }

    String coloured() { return color + display; }

    /** Weighted roll across everything above Default. */
    static Rarity roll(Random random) {
        int total = 0;
        for (Rarity rarity : values()) total += rarity.weight;
        int pick = random.nextInt(Math.max(1, total));
        for (Rarity rarity : values()) {
            pick -= rarity.weight;
            if (pick < 0) return rarity;
        }
        return BASIC;
    }

    static Rarity byName(String name) {
        if (name == null) return DEFAULT;
        for (Rarity rarity : values()) if (rarity.name().equalsIgnoreCase(name)) return rarity;
        return DEFAULT;
    }
}
