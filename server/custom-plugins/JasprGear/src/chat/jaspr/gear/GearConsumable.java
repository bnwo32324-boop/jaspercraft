package chat.jaspr.gear;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 2 consumables: apocalyptic counterparts of the Trinkets-and-Baubles mana items plus a
 * field bandage (the cure for Bleeding). Same unbreakable stone-hoe carrier as the trinkets, but
 * identity is the separate JasprGearUse:{id,doses} compound, so a consumable is never gear.
 *
 * model: unique stone-hoe damage 16-39 (own 16x16 texture). rarity 1..5 colours the name like a
 * trinket rank. minTier: lowest GearApi loot tier that can roll it. recipe: null = loot only.
 */
public enum GearConsumable {
    ADRENALINE_CANDY("adrenaline_candy", "Adrenaline Candy", 16, 1, 3, 0, 6, "Mana Candy",
        new String[]{"Caffeine chews: +20 adrenaline"},
        new String[]{"SRS", " P "}, "S=SUGAR,R=REDSTONE,P=PAPER"),
    FIELD_BANDAGE("field_bandage", "Field Bandage", 17, 1, 2, 0, 5, "(bleed cure)",
        new String[]{"Stops bleeding, heals 2"},
        new String[]{"PSP"}, "P=PAPER,S=STRING"),
    STIM_REAGENT("stim_reagent", "Stim Reagent", 18, 2, 1, 2, 3, "Mana Reagent",
        new String[]{"Auto-injector: +50 adrenaline", "Invigorated for 45s"},
        null, null),
    FULL_RESTORE("full_restore", "Full Restore", 19, 3, 1, 3, 2, "Restore",
        new String[]{"Energy drink: full adrenaline, +6 health", "Cures bleeding and paralysis", "Ice + lightning resistance 90s"},
        null, null),
    ADRENALINE_CRYSTAL("adrenaline_crystal", "Adrenaline Crystal", 20, 4, 1, 4, 1, "Mana Crystal",
        new String[]{"Permanently +10 max adrenaline", "Stacks up to +100"},
        null, null);

    public final String id;
    public final String title;
    public final int model;
    public final int rarity;
    public final int doses;
    public final int minTier;
    final int lootWeight;
    public final String inspiredBy;
    public final String[] effects;
    public final String[] shape;
    public final String ingredients;

    GearConsumable(String id, String title, int model, int rarity, int doses, int minTier, int lootWeight,
                   String inspiredBy, String[] effects, String[] shape, String ingredients) {
        this.id = id;
        this.title = title;
        this.model = model;
        this.rarity = rarity;
        this.doses = doses;
        this.minTier = minTier;
        this.lootWeight = lootWeight;
        this.inspiredBy = inspiredBy;
        this.effects = effects;
        this.shape = shape;
        this.ingredients = ingredients;
    }

    /** Same colours as trinket ranks: 1-2 green, 3 aqua, 4 light purple, 5 gold. */
    public char color() { return rarity <= 2 ? 'a' : rarity == 3 ? 'b' : rarity == 4 ? 'd' : '6'; }

    /** Loot weight at a tier: rarer supplies gain weight in harder tiers, none below minTier. */
    int weight(int tier) {
        if (tier < minTier) return 0;
        return lootWeight + (rarity >= 2 ? Math.max(0, tier - minTier) : 0);
    }

    public Map<Character, String> ingredientMap() {
        Map<Character, String> out = new LinkedHashMap<Character, String>();
        if (ingredients == null) return out;
        for (String part : ingredients.split(",")) {
            String[] kv = part.split("=", 2);
            out.put(kv[0].charAt(0), kv[1]);
        }
        return out;
    }

    private static final Map<String, GearConsumable> BY_ID = new LinkedHashMap<String, GearConsumable>();
    static {
        for (GearConsumable c : values()) {
            if (BY_ID.put(c.id, c) != null || GearItem.byId(c.id) != null) throw new IllegalStateException("duplicate id " + c.id);
            if (c.model < 16 || c.model >= GearItems.ICON_BASE_MODEL) throw new IllegalStateException("bad model " + c.id);
            for (GearItem g : GearItem.values()) if (g.model == c.model) throw new IllegalStateException("model clash " + c.id);
        }
    }

    public static GearConsumable byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
