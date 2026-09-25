package chat.jaspr.gear;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The fifteen Survivor Gear trinkets. Each is an original apocalyptic counterpart of one
 * Trinkets-and-Baubles trinket (named in {@link #inspiredBy}; design reference only, no code).
 *
 * model: unbreakable stone-hoe damage that selects the item's own 16x16 texture (assets.epk,
 * scripts/build-gear-pack.cjs). Keep models unique and below 40 (40+ are empty-slot icons).
 * rank: power 1 (weak utility) .. 5 (strongest). Loot tiers gate ranks (GearApi.rollLoot) and
 * mob drops weight by 6 - rank.
 *
 * Recipes are deliberately end-game (2026-09-24): every trinket costs a Nether Star and at least two
 * diamond blocks, rank 3+ adds an emerald block, rank 4+ a third diamond block, rank 5 a second
 * emerald block - as much as the dearest exoskeleton pieces or more - so dungeons and bosses stay
 * the way most survivors get them. Each keeps its thematic ingredients.
 */
public enum GearItem {
    CAPACITOR_BELT("capacitor_belt", "Capacitor Belt", GearType.BELT, 1, 4, "Arcing Orb",
        new String[]{"+10% speed; melee hits may discharge", "[G] Chain Arc Shot  [H] Dodge dash"},
        new String[]{"dBd", "LXL", "dfR"}, "d=DIAMOND_BLOCK,B=REDSTONE_BLOCK,L=LEATHER,X=NETHER_STAR,f=EMERALD_BLOCK,R=REDSTONE"),
    RIOT_VEST("riot_vest", "Riot Vest", GearType.BODY, 2, 4, "Damage Shield",
        new String[]{"Plates absorb 6 damage, then recharge", "30% of arrows glance off, -30% blasts"},
        new String[]{"i i", "dXd", "fOd"}, "i=IRON_BLOCK,d=DIAMOND_BLOCK,X=NETHER_STAR,f=EMERALD_BLOCK,O=OBSIDIAN"),
    THERMAL_GOGGLES("thermal_goggles", "Thermal Goggles", GearType.HEAD, 3, 3, "Dragon's Eye",
        new String[]{"Immune to burning, -50% lava damage", "Sneak still: thermal ore scan (8m)"},
        new String[]{"SMS", "GXG", "dfd"}, "S=STRING,M=MAGMA_CREAM,G=THIN_GLASS,X=NETHER_STAR,d=DIAMOND_BLOCK,f=EMERALD_BLOCK"),
    PHASE_HEADSET("phase_headset", "Phase Headset", GearType.HEAD, 4, 5, "Ender Queen's Crown",
        new String[]{"[H] Blink 8m, sneak+[H] ender chest", "10% phase through hits; endermen calm", "Shorts out while wet"},
        new String[]{"dRd", "EXE", "fdf"}, "d=DIAMOND_BLOCK,R=REDSTONE,E=ENDER_PEARL,X=NETHER_STAR,f=EMERALD_BLOCK"),
    FIELD_JOURNAL("field_journal", "Field Journal", GearType.ANY, 5, 1, "Experience Device",
        new String[]{"+20% experience from orbs", "Banks up to 30 levels: /gear bank"},
        new String[]{"dFd", "IXL", "gBg"}, "d=DIAMOND_BLOCK,F=FEATHER,I=INK_SACK:0,X=NETHER_STAR,L=INK_SACK:4,g=GOLD_BLOCK,B=BOOK"),
    RAZOR_CLAWS("razor_claws", "Razor Claws", GearType.ANY, 6, 3, "Faelis Claw",
        new String[]{"+1 melee, 20% chance to cause Bleeding", "Climb walls; sneak to cling"},
        new String[]{"FFF", "dXd", "IfI"}, "F=FLINT,d=DIAMOND_BLOCK,X=NETHER_STAR,I=IRON_INGOT,f=EMERALD_BLOCK"),
    TRITIUM_RING("tritium_ring", "Tritium Ring", GearType.RING, 7, 1, "Ring of Enchanted Eyes",
        new String[]{"Motion tracker: hostiles within 12m", "Immune to Blindness"},
        new String[]{"NGN", "dXd", "NgN"}, "N=IRON_NUGGET,G=GLOWSTONE_DUST,d=DIAMOND_BLOCK,X=NETHER_STAR,g=GOLD_BLOCK"),
    SPRINT_BRACE("sprint_brace", "Sprinter's Brace", GearType.ANY, 8, 2, "Stone of Greater Inertia",
        new String[]{"+15% speed, -50% fall, +30% KB resist", "Sprint-jumps carry momentum"},
        new String[]{"SLS", "dXd", "gRg"}, "S=STRING,L=LEATHER,d=DIAMOND_BLOCK,X=NETHER_STAR,g=GOLD_BLOCK,R=RABBIT_FOOT"),
    GYRO_STABILIZER("gyro_stabilizer", "Gyro Stabilizer", GearType.ANY, 9, 3, "Stone of Inertia Null",
        new String[]{"+80% KB resist, no wall impacts", "Gyro brake arrests long falls"},
        new String[]{"GdG", "IXI", "fCd"}, "G=GOLD_NUGGET,d=DIAMOND_BLOCK,I=IRON_INGOT,X=NETHER_STAR,f=EMERALD_BLOCK,C=COMPASS"),
    TOXIN_INJECTOR("toxin_injector", "Toxin Injector", GearType.ANY, 10, 3, "Poison Stone",
        new String[]{"Immune to poison and blight water", "Poisons foes and attackers, +2 vs poisoned"},
        new String[]{"dNd", "BXB", "EfE"}, "d=DIAMOND_BLOCK,N=IRON_NUGGET,B=GLASS_BOTTLE,X=NETHER_STAR,E=FERMENTED_SPIDER_EYE,f=EMERALD_BLOCK"),
    SCRAP_MAGNET("scrap_magnet", "Scrap Magnet", GearType.ANY, 11, 2, "Polarized Stone",
        new String[]{"[J] Magnet: pull items and XP (7m)", "Sneak+[J] repel pulse; arrows veer"},
        new String[]{"R R", "dXd", "IgI"}, "R=REDSTONE,d=DIAMOND_BLOCK,X=NETHER_STAR,I=IRON_INGOT,g=GOLD_BLOCK"),
    REBREATHER("rebreather", "Rebreather", GearType.NECK, 12, 1, "Stone of the Sea",
        new String[]{"Breathe underwater, no blight poison", "Swim faster, dig faster underwater"},
        new String[]{"dSd", "LXL", "gPg"}, "d=DIAMOND_BLOCK,S=STRING,L=LEATHER,X=NETHER_STAR,g=GOLD_BLOCK,P=RAW_FISH:3"),
    TEDDY_BEAR("teddy_bear", "Worn Teddy Bear", GearType.ANY, 13, 4, "Teddy Bear",
        new String[]{"Sneak still to rest and heal", "Last Stand: survive a fatal hit (20m)"},
        new String[]{"dWd", "WXW", "fSd"}, "d=DIAMOND_BLOCK,W=WOOL:12,X=NETHER_STAR,f=EMERALD_BLOCK,S=STRING"),
    GRAV_HARNESS("grav_harness", "Grav Harness", GearType.ANY, 14, 5, "Stone of Negative Gravity",
        new String[]{"Weightless: hover while airborne", "Swing to rise, sneak+swing to sink", "No fall damage"},
        new String[]{"dHd", "FXF", "fdf"}, "d=DIAMOND_BLOCK,H=SHULKER_SHELL,F=FEATHER,X=NETHER_STAR,f=EMERALD_BLOCK"),
    NECROTIC_RING("necrotic_ring", "Necrotic Ring", GearType.RING, 15, 3, "Wither Ring",
        new String[]{"Immune to Wither; withers foes", "Leech 1 HP from withered targets"},
        new String[]{"NCN", "dXd", "NfB"}, "N=IRON_NUGGET,C=COAL:0,d=DIAMOND_BLOCK,X=NETHER_STAR,f=EMERALD_BLOCK,B=BONE"),
    // 3.3.0 (owner request): a cheap, single-purpose answer to the blight water. Deliberately outside the
    // end-game recipe rule above, and craft-only (loot=false) so every existing loot roll stays identical.
    BLIGHT_FILTER("blight_filter", "Blight Filter", GearType.ANY, 35, 1, "(JasperCraft original)",
        new String[]{"Charcoal filter: immune to blight water", "Cheap to make; never found as loot"},
        new String[]{"SIS", "CBC", "SPS"}, "S=STRING,I=IRON_INGOT,C=COAL:1,B=GLASS_BOTTLE,P=PAPER", false);

    public final String id;
    public final String title;
    public final GearType type;
    public final int model;
    public final int rank;
    public final String inspiredBy;
    public final String[] effects;
    public final String[] shape;
    public final String ingredients;
    /** False: craft-only, never rolled by structure loot, boss loot or mob drops. */
    public final boolean loot;

    GearItem(String id, String title, GearType type, int model, int rank, String inspiredBy,
             String[] effects, String[] shape, String ingredients) {
        this(id, title, type, model, rank, inspiredBy, effects, shape, ingredients, true);
    }

    GearItem(String id, String title, GearType type, int model, int rank, String inspiredBy,
             String[] effects, String[] shape, String ingredients, boolean loot) {
        this.loot = loot;
        this.id = id;
        this.title = title;
        this.type = type;
        this.model = model;
        this.rank = rank;
        this.inspiredBy = inspiredBy;
        this.effects = effects;
        this.shape = shape;
        this.ingredients = ingredients;
    }

    /** Mob-drop weight: common utility drops most, rank 5 rarely. */
    public int weight() { return 6 - rank; }

    /** Section-sign colour by rank: 1-2 green, 3 aqua, 4 light purple, 5 gold. */
    public char color() { return rank <= 2 ? 'a' : rank == 3 ? 'b' : rank == 4 ? 'd' : '6'; }

    /** Parsed ingredient map: key char to "MATERIAL" or "MATERIAL:data". */
    public Map<Character, String> ingredientMap() {
        Map<Character, String> out = new LinkedHashMap<Character, String>();
        for (String part : ingredients.split(",")) {
            String[] kv = part.split("=", 2);
            out.put(kv[0].charAt(0), kv[1]);
        }
        return out;
    }

    private static final Map<String, GearItem> BY_ID = new LinkedHashMap<String, GearItem>();
    static {
        for (GearItem item : values()) {
            if (BY_ID.put(item.id, item) != null) throw new IllegalStateException("duplicate gear id " + item.id);
            if (item.rank < 1 || item.rank > 5) throw new IllegalStateException("bad rank " + item.id);
        }
    }

    /** The lootable trinkets, in declaration order (the loot and boss-loot pools). */
    public static final GearItem[] LOOT;
    static {
        java.util.List<GearItem> out = new java.util.ArrayList<GearItem>();
        for (GearItem item : values()) if (item.loot) out.add(item);
        LOOT = out.toArray(new GearItem[0]);
    }

    public static GearItem byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
