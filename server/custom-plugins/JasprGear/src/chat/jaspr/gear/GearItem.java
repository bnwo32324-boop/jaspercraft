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
 */
public enum GearItem {
    CAPACITOR_BELT("capacitor_belt", "Capacitor Belt", GearType.BELT, 1, 4, "Arcing Orb",
        new String[]{"+10% speed; melee hits may discharge", "[G] Chain Arc Shot  [H] Dodge dash"},
        new String[]{"LRL", "IBI", "LRL"}, "L=LEATHER,R=REDSTONE,I=IRON_INGOT,B=REDSTONE_BLOCK"),
    RIOT_VEST("riot_vest", "Riot Vest", GearType.BODY, 2, 4, "Damage Shield",
        new String[]{"Plates absorb 6 damage, then recharge", "30% of arrows glance off, -30% blasts"},
        new String[]{"I I", "LOL", "ILI"}, "I=IRON_INGOT,L=LEATHER,O=OBSIDIAN"),
    THERMAL_GOGGLES("thermal_goggles", "Thermal Goggles", GearType.HEAD, 3, 3, "Dragon's Eye",
        new String[]{"Immune to burning, -50% lava damage", "Sneak still: thermal ore scan (8m)"},
        new String[]{"SLS", "GMG"}, "S=STRING,L=LEATHER,G=THIN_GLASS,M=MAGMA_CREAM"),
    PHASE_HEADSET("phase_headset", "Phase Headset", GearType.HEAD, 4, 5, "Ender Queen's Crown",
        new String[]{"[H] Blink 8m, sneak+[H] ender chest", "10% phase through hits; endermen calm", "Shorts out while wet"},
        new String[]{"IRI", "E E"}, "I=IRON_INGOT,R=REDSTONE,E=ENDER_PEARL"),
    FIELD_JOURNAL("field_journal", "Field Journal", GearType.ANY, 5, 1, "Experience Device",
        new String[]{"+20% experience from orbs", "Banks up to 30 levels: /gear bank"},
        new String[]{" F ", "IBL"}, "F=FEATHER,I=INK_SACK:0,B=BOOK,L=INK_SACK:4"),
    RAZOR_CLAWS("razor_claws", "Razor Claws", GearType.ANY, 6, 3, "Faelis Claw",
        new String[]{"+1 melee, 20% chance to cause Bleeding", "Climb walls; sneak to cling"},
        new String[]{"FFF", "III"}, "F=FLINT,I=IRON_INGOT"),
    TRITIUM_RING("tritium_ring", "Tritium Ring", GearType.RING, 7, 1, "Ring of Enchanted Eyes",
        new String[]{"Motion tracker: hostiles within 12m", "Immune to Blindness"},
        new String[]{"NGN", "N N", "NNN"}, "N=IRON_NUGGET,G=GLOWSTONE_DUST"),
    SPRINT_BRACE("sprint_brace", "Sprinter's Brace", GearType.ANY, 8, 2, "Stone of Greater Inertia",
        new String[]{"+15% speed, -50% fall, +30% KB resist", "Sprint-jumps carry momentum"},
        new String[]{"SLS", "LRL", "SLS"}, "S=STRING,L=LEATHER,R=RABBIT_FOOT"),
    GYRO_STABILIZER("gyro_stabilizer", "Gyro Stabilizer", GearType.ANY, 9, 3, "Stone of Inertia Null",
        new String[]{"+80% KB resist, no wall impacts", "Gyro brake arrests long falls"},
        new String[]{"GIG", "ICI", "GIG"}, "G=GOLD_NUGGET,I=IRON_INGOT,C=COMPASS"),
    TOXIN_INJECTOR("toxin_injector", "Toxin Injector", GearType.ANY, 10, 3, "Poison Stone",
        new String[]{"Immune to poison and blight water", "Poisons foes and attackers, +2 vs poisoned"},
        new String[]{"N", "B", "E"}, "N=IRON_NUGGET,B=GLASS_BOTTLE,E=FERMENTED_SPIDER_EYE"),
    SCRAP_MAGNET("scrap_magnet", "Scrap Magnet", GearType.ANY, 11, 2, "Polarized Stone",
        new String[]{"[J] Magnet: pull items and XP (7m)", "Sneak+[J] repel pulse; arrows veer"},
        new String[]{"R R", "I I", "III"}, "R=REDSTONE,I=IRON_INGOT"),
    REBREATHER("rebreather", "Rebreather", GearType.NECK, 12, 1, "Stone of the Sea",
        new String[]{"Breathe underwater, no blight poison", "Swim faster, dig faster underwater"},
        new String[]{" S ", "LPL", " I "}, "S=STRING,L=LEATHER,P=RAW_FISH:3,I=IRON_INGOT"),
    TEDDY_BEAR("teddy_bear", "Worn Teddy Bear", GearType.ANY, 13, 4, "Teddy Bear",
        new String[]{"Sneak still to rest and heal", "Last Stand: survive a fatal hit (20m)"},
        new String[]{" W ", "WSW", "W W"}, "W=WOOL:12,S=STRING"),
    GRAV_HARNESS("grav_harness", "Grav Harness", GearType.ANY, 14, 5, "Stone of Negative Gravity",
        new String[]{"Weightless: hover while airborne", "Swing to rise, sneak+swing to sink", "No fall damage"},
        new String[]{"LHL", "FIF", "L L"}, "L=LEATHER,H=SHULKER_SHELL,F=FEATHER,I=IRON_INGOT"),
    NECROTIC_RING("necrotic_ring", "Necrotic Ring", GearType.RING, 15, 3, "Wither Ring",
        new String[]{"Immune to Wither; withers foes", "Leech 1 HP from withered targets"},
        new String[]{"NCN", "N N", "NBN"}, "N=IRON_NUGGET,C=COAL:0,B=BONE");

    public final String id;
    public final String title;
    public final GearType type;
    public final int model;
    public final int rank;
    public final String inspiredBy;
    public final String[] effects;
    public final String[] shape;
    public final String ingredients;

    GearItem(String id, String title, GearType type, int model, int rank, String inspiredBy,
             String[] effects, String[] shape, String ingredients) {
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

    public static GearItem byId(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
