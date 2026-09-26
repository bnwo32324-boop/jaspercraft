package chat.jaspr.enchant.data;

/**
 * One So Many Enchantments 1.0.9 enchantment with its default configuration.
 * Values come from {@link SmeTable} (generated from the mod source).
 */
public final class SmeDef {
    public final int index;
    public final int id;
    public final String regName;
    public final String configKey;
    public final String smeClass;
    /** type argument of the multi-instance classes (TierDamage, TierFA, TierFlame, Subject), -1 otherwise */
    public final int variant;
    public final String slots;
    public final int minLevel;
    public final int maxLevel;
    /** {start, lvlSpan, range, maxMode}: SME EnchantabilityConfig semantics */
    public final int[] enchantability;
    public final boolean treasure;
    public final boolean curse;
    public final String rarity;
    public final String[] tableTypes;
    public final String[] anvilTypes;
    public final String style;

    public SmeDef(int index, int id, String regName, String configKey, String smeClass, int variant, String slots,
                  int minLevel, int maxLevel, int[] enchantability, boolean unused, boolean treasure, boolean curse,
                  String rarity, String[] tableTypes, String[] anvilTypes, String style) {
        this.index = index;
        this.id = id;
        this.regName = regName;
        this.configKey = configKey;
        this.smeClass = smeClass;
        this.variant = variant;
        this.slots = slots;
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.enchantability = enchantability;
        this.treasure = treasure;
        this.curse = curse;
        this.rarity = rarity;
        this.tableTypes = tableTypes;
        this.anvilTypes = anvilTypes;
        this.style = style;
    }

    public static final int MIN = 0, SUPER = 1, FIXED = 2, LINEAR = 3;
    private static final int[] SUPER_ENCHANTABILITY = {11, 10, 5, MIN};

    /** EnchantabilityConfig.getMinEnchantability */
    public static int minEnchantability(int[] cfg, int level) {
        return cfg[0] + cfg[1] * (level - 1);
    }

    /** EnchantabilityConfig.getMaxEnchantability */
    public static int maxEnchantability(int[] cfg, int level) {
        int range = cfg[2];
        switch (cfg[3]) {
            case MIN: return minEnchantability(cfg, level) + range;
            case SUPER: return minEnchantability(SUPER_ENCHANTABILITY, level) + range;
            case FIXED: return range;
            case LINEAR: return range * level;
            default: return 0;
        }
    }

    public int minEnchantability(int level) {
        return minEnchantability(enchantability, level);
    }

    public int maxEnchantability(int level) {
        return maxEnchantability(enchantability, level);
    }

    public static SmeDef byRegName(String reg) {
        for (SmeDef d : SmeTable.ALL) if (d.regName.equals(reg)) return d;
        return null;
    }
}
