package chat.jaspr.gear;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Backpacks (JasprGear 3.2.0): carried in the inventory, right-click to open, like most backpack
 * mods. Same unbreakable stone-hoe carrier as the trinkets; identity is the separate
 * JasprGearPack:{id[,uuid]} compound, so a backpack is never gear or a consumable. The contents
 * live on disk under the backpack's uuid (stamped on first open), never inside the item.
 *
 * tier 1..5: 2..6 rows (tier 1 = 18 slots, half the 36-slot player inventory). Recipes start
 * cheap (leather and string) and climb with the tier. lootWeight: share of the backpack band of
 * GearApi.rollLoot (percent), so higher tiers are much rarer in chests.
 */
public enum GearBackpack {
    SATCHEL("satchel", "Leather Satchel", 30, 1, 50,
        new String[]{"SLS", "L L", "LLL"}, "S=STRING,L=LEATHER"),
    RUCKSACK("rucksack", "Rucksack", 31, 2, 25,
        new String[]{"SLS", "ICI", "LLL"}, "S=STRING,L=LEATHER,I=IRON_INGOT,C=CHEST"),
    FIELD_PACK("field_pack", "Field Pack", 32, 3, 14,
        new String[]{"LiL", "gCg", "LLL"}, "L=LEATHER,i=IRON_BLOCK,g=GOLD_INGOT,C=CHEST"),
    EXPEDITION_PACK("expedition_pack", "Expedition Pack", 33, 4, 8,
        new String[]{"LDL", "GCG", "LDL"}, "L=LEATHER,D=DIAMOND,G=GOLD_BLOCK,C=CHEST"),
    FRAME_PACK("frame_pack", "Hauler's Frame Pack", 34, 5, 3,
        new String[]{"LdL", "HCH", "LdL"}, "L=LEATHER,d=DIAMOND_BLOCK,H=SHULKER_SHELL,C=CHEST");

    public final String id;
    public final String title;
    public final int model;
    public final int tier;
    final int lootWeight;
    public final String[] shape;
    public final String ingredients;

    GearBackpack(String id, String title, int model, int tier, int lootWeight, String[] shape, String ingredients) {
        this.id = id;
        this.title = title;
        this.model = model;
        this.tier = tier;
        this.lootWeight = lootWeight;
        this.shape = shape;
        this.ingredients = ingredients;
    }

    public int rows() { return tier + 1; }

    public int slots() { return rows() * 9; }

    /** Name colour by tier: white, green, aqua, light purple, gold. */
    public char color() { return "fabd6".charAt(tier - 1); }

    public Map<Character, String> ingredientMap() {
        Map<Character, String> out = new LinkedHashMap<Character, String>();
        for (String part : ingredients.split(",")) {
            String[] kv = part.split("=", 2);
            out.put(kv[0].charAt(0), kv[1]);
        }
        return out;
    }

    private static final Map<String, GearBackpack> BY_ID = new LinkedHashMap<String, GearBackpack>();
    static {
        for (GearBackpack b : values()) {
            if (BY_ID.put(b.id, b) != null || GearItem.byId(b.id) != null || GearConsumable.byId(b.id) != null)
                throw new IllegalStateException("duplicate id " + b.id);
            if (b.model < 16 || b.model >= GearItems.ICON_BASE_MODEL) throw new IllegalStateException("bad model " + b.id);
            for (GearItem g : GearItem.values()) if (g.model == b.model) throw new IllegalStateException("model clash " + b.id);
            for (GearConsumable c : GearConsumable.values()) if (c.model == b.model) throw new IllegalStateException("model clash " + b.id);
        }
    }

    public static GearBackpack byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
