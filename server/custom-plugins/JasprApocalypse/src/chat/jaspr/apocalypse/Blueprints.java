package chat.jaspr.apocalypse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * One crafting-bench recipe for every custom item, built only from vanilla ingredients.
 *
 * The point of the grammar is that the arsenal can be learned rather than looked up.
 * Firearms use iron-block receivers and ingot barrels, redstone triggers, and gunpowder
 * for conventional firing assemblies. Repeaters represent burst controls; glass and
 * quartz represent optics; pistons and hoppers represent bolts and ammunition feeds.
 * Energy weapons use gold conductors, redstone blocks and specialized vanilla minerals.
 * A basic pistol costs twelve iron ingots in total; heavier frames cost more. Melee weapons
 * keep vanilla's blade-over-handle silhouette but give the head two different materials,
 * which is what keeps two dozen of them distinct from each other and from a vanilla sword.
 * Exoskeletons are deliberately extreme end-game crafts. A complete set consumes eight
 * diamond blocks, eight iron blocks, six gold blocks, four Nether Stars and four
 * set-specific cores. The four silhouettes stay distinct and use only vanilla materials.
 *
 * Every pattern here was checked, in both orientations, against all 432 vanilla 1.12.2
 * recipes and against every other pattern in this table. Nothing shadows anything.
 * A shapeless vanilla recipe can shadow a shaped one by ingredient multiset alone, so
 * that case is checked too rather than just the patterns.
 */
public final class Blueprints {
    private Blueprints() { }

    /** A recipe: the three rows, then the ingredient letters used in them. */
    public static final class Blueprint {
        public final String id, kind;
        public final String[] shape;
        Blueprint(String id, String kind, String[] shape) { this.id = id; this.kind = kind; this.shape = shape; }
    }

    /** Letter -> the vanilla ingredient it stands for. */
    public static Material material(char letter) {
        switch (letter) {
            case 'A': return Material.GLOWSTONE_DUST;
            case 'B': return Material.BLAZE_ROD;
            case 'C': return Material.COAL;
            case 'D': return Material.DIAMOND;
            case 'E': return Material.EMERALD;
            case 'F': return Material.FLINT;
            case 'G': return Material.GOLD_INGOT;
            case 'H': return Material.LEATHER;
            case 'I': return Material.IRON_INGOT;
            case 'J': return Material.PAPER;
            case 'K': return Material.BONE;
            case 'L': return Material.INK_SACK;
            case 'M': return Material.SLIME_BALL;
            case 'N': return Material.IRON_NUGGET;
            case 'O': return Material.OBSIDIAN;
            case 'P': return Material.ENDER_PEARL;
            case 'Q': return Material.QUARTZ;
            case 'R': return Material.REDSTONE;
            case 'S': return Material.STICK;
            case 'T': return Material.STRING;
            case 'U': return Material.SULPHUR;
            case 'V': return Material.GLASS;
            case 'W': return Material.PRISMARINE_SHARD;
            case 'X': return Material.NETHER_STAR;
            case 'Y': return Material.PRISMARINE_CRYSTALS;
            case 'a': return Material.GOLDEN_APPLE;
            case 'b': return Material.BOOK;
            case 'c': return Material.CLAY_BALL;
            case 'd': return Material.DIAMOND_BLOCK;
            case 'e': return Material.EYE_OF_ENDER;
            case 'f': return Material.EMERALD_BLOCK;
            case 'g': return Material.GOLD_BLOCK;
            case 'h': return Material.HOPPER;
            case 'i': return Material.IRON_BLOCK;
            case 'o': return Material.ROTTEN_FLESH;
            case 'p': return Material.PISTON_BASE;
            case 'r': return Material.REDSTONE_BLOCK;
            case 's': return Material.SLIME_BLOCK;
            case 't': return Material.DIODE;
            case 'w': return Material.WHEAT;
            default: throw new IllegalArgumentException("Unknown ingredient " + letter);
        }
    }

    /** Data value for the letter; 0 for everything that is not a dye-style subtype. */
    public static short data(char letter) {
        switch (letter) {
            case 'L': return 4;
            default: return 0;
        }
    }

    private static final Map<String, Blueprint> ALL = build();

    private static Map<String, Blueprint> build() {
        Map<String, Blueprint> map = new LinkedHashMap<String, Blueprint>();
        add(map, "sepulcher", "gun/sidearm", "iII", "SRU", ".I.");
        add(map, "vesper", "gun/sidearm", "iII", "SRT", ".Ui");
        add(map, "ossuary", "gun/sidearm", "iiI", "SRU", ".GI");
        add(map, "turnstile", "gun/sidearm", "iIF", "SRU", ".Ii");
        add(map, "cinder", "gun/sidearm", "iII", "SRU", "Iri");
        add(map, "whisper", "gun/sidearm", "iiI", "SRT", "IUi");
        add(map, "tunnelrat", "gun/smg", "iII", "SRU", "iFI");
        add(map, "tempest", "gun/smg", "iiI", "StU", "iRI");
        add(map, "blackbox", "gun/smg", "iiI", "StU", "iQI");
        add(map, "quarantine", "gun/smg", "iiI", "SRU", "iOI");
        add(map, "adjudicator", "gun/carbine", "iiI", "SRU", "iGI");
        add(map, "bastion", "gun/carbine", "iiI", "OrU", "iOI");
        add(map, "deadfrequency", "gun/carbine", "iiI", "GtU", "irQ");
        add(map, "whiteout", "gun/carbine", "iiV", "SRT", "iUi");
        add(map, "frostbite", "gun/carbine", "iiD", "GrQ", "iYI");
        add(map, "rifle", "gun/rifle", "iiI", "SRX", "UGI");
        add(map, "longwatch", "gun/rifle", "ViI", "SRU", "iQi");
        add(map, "signal", "gun/rifle", "ViI", "StU", "iri");
        add(map, "gallows", "gun/rifle", "ViI", "SRU", "ipi");
        add(map, "watchtower", "gun/rifle", "Vii", "OrU", "iiD");
        add(map, "sunlance", "gun/rifle", "iiQ", "GrB", "iDI");
        add(map, "hexbreaker", "gun/rifle", "iiQ", "GrP", "iDI");
        add(map, "witchlight", "gun/rifle", "iiQ", "GrA", "iDI");
        add(map, "shotgun", "gun/shotgun", "iiI", "SRX", "UUi");
        add(map, "cyclops", "gun/shotgun", "iiI", "SRU", "iFU");
        add(map, "bellringer", "gun/shotgun", "iII", "SRU", "iUI");
        add(map, "lockjaw", "gun/shotgun", "iII", "SRU", "ipI");
        add(map, "choir", "gun/shotgun", "iii", "SRU", "iUD");
        add(map, "ashfall", "gun/shotgun", "iiI", "SRU", "iUh");
        add(map, "railgun", "gun/energy", "iiD", "grX", "iQI");
        add(map, "nullpoint", "gun/energy", "iiQ", "GrD", "iQI");
        add(map, "cenotaph", "gun/energy", "iii", "OrD", "iGI");
        add(map, "stormcoil", "gun/energy", "iiQ", "grD", "iRI");
        add(map, "pallbearer", "gun/energy", "iii", "hrU", "iDI");
        add(map, "ironpsalm", "gun/heavy", "iii", "hrU", "ipi");
        // Common sidearms (2026-09-26): the cheapest a firearm may be (12 iron-equivalent), well under the arsenal's
        // average of 28, but still an iron-block receiver, a redstone trigger and gunpowder, like every gun here.
        add(map, "rapture", "gun/sidearm", "iII", "GRU", "SI.");    // brass Rapture furniture: gold fittings
        add(map, "g18", "gun/sidearm", "iII", "SRt", "UI.");        // repeater: the burst selector
        add(map, "magnum44", "gun/sidearm", "iII", "SRU", "OI.");   // obsidian-weighted heavy frame
        add(map, "wingman", "gun/sidearm", "iIV", "SRU", ".II");    // glass: the Elite's precision sight
        add(map, "mozambique", "gun/sidearm", "III", "iRU", "SU."); // three barrels over the receiver
        // Melee (2026-09-25 owner overhaul). Every one of these out-hits a diamond sword (7 damage, ~11 DPS):
        // 1.4-4x per hit and 1.2-2.5x per second. Cost follows power = sqrt(DPS ratio x hit ratio) against the
        // diamond sword (2 diamonds), in four bands, counted in diamond-equivalents (block = 9):
        //   power < 1.93  ~5-6.5   (2.5-3x a diamond sword)   power 2.06-2.15  ~12.5-16  (6-8x)
        //   power 1.93-2.05 ~9-12  (4.5-6x)                   power > 2.15     ~18.5-20  (9-10x)
        // Shapes follow the weapon: short blades one stick; swords one stick under a guard row; axes, picks,
        // hammers and the scythe two sticks (vanilla haft); polearms a head on two diagonal sticks; the baton and
        // the whip a leather grip. Edges are diamond, heavy heads are metal blocks, fire weapons carry blaze rods.
        add(map, "trench_blade", "melee/blade", ".D.", "DiD", ".S.");    // [< 1.93] diamond knife over an iron knuckle guard, one stick
        add(map, "mono_katana", "melee/blade", ".dD", "Dd.", "TST");     // [> 2.15] two diamond blocks, diamond edges, string-wrapped hilt
        add(map, "gravespike", "melee/blade", ".D.", "KdK", ".S.");      // [1.93-2.05] diamond point, diamond-block dirk, bone guard, one stick
        add(map, "vesper_dagger", "melee/blade", ".e.", ".d.", ".S.");   // [1.93-2.05] ritual eye-of-ender tip, diamond-block blade, one stick
        add(map, "cautery_sabre", "melee/blade", ".D.", "DdD", "BSB");   // [2.06-2.15] diamond-edged diamond-block sabre, blaze-rod heated grip
        add(map, "rebar_sword", "melee/blade", ".i.", "idi", ".S.");     // [2.06-2.15] iron-block rebar around a diamond-block core, one stick
        add(map, "execution_sword", "melee/blade", ".d.", "DOD", "OSO"); // [2.06-2.15] diamond-block blade, diamond/obsidian guard, obsidian pommel
        add(map, "wardcleaver", "melee/blade", "DDE", "iE.", ".S.");     // [< 1.93] diamond edge, emerald wards, iron spine, one stick
        add(map, "shock_baton", "melee/blade", "DrD", "GiG", ".H.");     // [< 1.93] diamond electrodes, redstone battery, gold contacts, iron shaft, leather grip
        add(map, "suture_sickle", "melee/blade", ".DD", "TiD", "S..");   // [< 1.93] curved diamond blade, iron hub, string sutures, one stick
        add(map, "breacher_axe", "melee/axe", "dD.", "iS.", ".S.");      // [1.93-2.05] diamond-block bit with a diamond edge, iron poll, two sticks
        add(map, "railpick", "melee/axe", "iDi", ".S.", ".S.");          // [< 1.93] rail-iron pick head, diamond point, two-stick haft
        add(map, "thermal_machete", "melee/blade", ".d.", "BCB", ".S."); // [1.93-2.05] diamond-block machete over blaze-rod and coal heaters, one stick
        add(map, "ember_falchion", "melee/blade", ".BD", ".d.", ".S.");  // [1.93-2.05] blaze-heated diamond-block falchion, one stick
        add(map, "gravity_maul", "melee/axe", "dOd", ".S.", ".S.");      // [> 2.15] two diamond blocks around an obsidian core, two sticks
        add(map, "tollhammer", "melee/axe", "dGd", "GSG", ".S.");        // [> 2.15] diamond-block head, gold bell bands, two sticks
        add(map, "altar_mallet", "melee/axe", "gdg", ".S.", ".S.");      // [2.06-2.15] gold-capped diamond-block head, two-stick haft
        add(map, "sentinel_spear", "melee/polearm", ".DD", ".SD", "Si."); // [< 1.93] diamond head, two-stick shaft, iron butt spike
        add(map, "pilgrim_lance", "melee/polearm", ".Dd", ".SG", "S.."); // [1.93-2.05] diamond tip, diamond-block vamplate, gold trim, two sticks
        add(map, "hollow_halberd", "melee/polearm", ".dD", ".Sg", "S.."); // [2.06-2.15] diamond-block axe blade, diamond spike, gold langet, two sticks
        add(map, "mourning_glaive", "melee/polearm", ".Dd", ".SL", "S.."); // [1.93-2.05] diamond-block glaive blade, ink mourning band, two sticks
        add(map, "reaper_scythe", "melee/scythe", "DDD", "KiS", "..S");  // [< 1.93] diamond blade, bone collar, iron tang, two-stick snath
        add(map, "ossuary_flail", "melee/scythe", "KdK", ".N.", ".S.");  // [1.93-2.05] bone-spiked diamond-block head on a nugget chain, one stick
        add(map, "wire_whip", "melee/scythe", "DTD", "DiD", ".H.");      // [< 1.93] razorwire lash with diamond barbs on an iron reel, leather handle
        add(map, "bulwark_helmet", "armor/bulwark", "dXd", "iOi", "...");
        add(map, "bulwark_chestplate", "armor/bulwark", "i.i", "dXd", "gOg");
        add(map, "bulwark_leggings", "armor/bulwark", "dXd", "iOi", "g.g");
        add(map, "bulwark_boots", "armor/bulwark", "i.i", "dOd", "gXg");
        add(map, "ranger_helmet", "armor/ranger", "dXd", "ifi", "...");
        add(map, "ranger_chestplate", "armor/ranger", "i.i", "dXd", "gfg");
        add(map, "ranger_leggings", "armor/ranger", "dXd", "ifi", "g.g");
        add(map, "ranger_boots", "armor/ranger", "i.i", "dfd", "gXg");
        add(map, "spectre_helmet", "armor/spectre", "dXd", "iei", "...");
        add(map, "spectre_chestplate", "armor/spectre", "i.i", "dXd", "geg");
        add(map, "spectre_leggings", "armor/spectre", "dXd", "iei", "g.g");
        add(map, "spectre_boots", "armor/spectre", "i.i", "ded", "gXg");
        add(map, "hazmat_helmet", "armor/hazmat", "dXd", "isi", "...");
        add(map, "hazmat_chestplate", "armor/hazmat", "i.i", "dXd", "gsg");
        add(map, "hazmat_leggings", "armor/hazmat", "dXd", "isi", "g.g");
        add(map, "hazmat_boots", "armor/hazmat", "i.i", "dsd", "gXg");
        add(map, "sentry_turret", "other", "III", "IiI", "IRI");
        add(map, "portal_gun", "other", "iQD", "QrP", "iQG");
        add(map, "alloy_plate", "other", "III", "IOI", "III");
        add(map, "weapon_core", "other", ".Q.", "QRQ", ".Q.");
        add(map, "power_cell", "other", ".I.", "IRI", ".A.");
        add(map, "ballistic_fiber", "other", "TTT", "THT", "TTT");
        add(map, "trauma_kit", "other", ".T.", "TaT", ".T.");
        add(map, "field_ration", "other", ".w.", "wcw", ".w.");
        add(map, "coolant_injector", "other", ".M.", "MWM", ".M.");
        add(map, "sanitized_flesh", "other", ".C.", "CoC", ".C.");
        add(map, "scrap", "other", ".N.", "NIN", ".N.");
        add(map, "relic", "other", ".Y.", "YeY", ".Y.");
        add(map, "expedition_trophy", "other", ".G.", "GgG", ".G.");
        add(map, "guide", "other", ".J.", "JbJ", ".o.");
        return Collections.unmodifiableMap(map);
    }

    private static void add(Map<String, Blueprint> map, String id, String kind, String a, String b, String c) {
        map.put(id, new Blueprint(id, kind, new String[]{a, b, c}));
    }

    public static Map<String, Blueprint> all() { return ALL; }
    public static Blueprint of(String id) { return ALL.get(id); }
    public static boolean has(String id) { return ALL.containsKey(id); }

    /** The shape as Bukkit wants it: spaces, not dots, and no all-empty trailing rows. */
    public static String[] bukkitShape(Blueprint blueprint) {
        int rows = 3;
        while (rows > 1 && blueprint.shape[rows - 1].indexOf('.') == 0
                && blueprint.shape[rows - 1].equals("...")) rows--;
        String[] out = new String[rows];
        for (int i = 0; i < rows; i++) out[i] = blueprint.shape[i].replace('.', ' ');
        return out;
    }

    /** Every distinct ingredient letter in a blueprint. */
    public static String letters(Blueprint blueprint) {
        StringBuilder out = new StringBuilder();
        for (String row : blueprint.shape)
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != '.' && out.indexOf(String.valueOf(c)) < 0) out.append(c);
            }
        return out.toString();
    }

    /**
     * Whether a bench grid really is this blueprint.
     *
     * Bukkit's own matcher ignores NBT, so without this a marked custom item would pass
     * as its plain vanilla counterpart -- Military Salvage is an iron nugget, for one.
     * Shaped recipes match mirrored as well, so both orientations are accepted here too.
     */
    public static boolean matches(Blueprint blueprint, ItemStack[] matrix, boolean mirrored) {
        if (matrix == null || matrix.length != 9) return false;
        for (int slot = 0; slot < 9; slot++) {
            int row = slot / 3, col = slot % 3;
            char want = blueprint.shape[row].charAt(mirrored ? 2 - col : col);
            ItemStack item = matrix[slot];
            boolean blank = item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
            if (want == '.') { if (!blank) return false; continue; }
            if (blank) return false;
            if (item.getType() != material(want)) return false;
            if (item.getDurability() != data(want)) return false;
            // A custom item wearing a vanilla material must not count as that material.
            String id = ApocalypseItems.id(item);
            if (id != null && !id.isEmpty()) return false;
        }
        return true;
    }

    public static boolean matches(Blueprint blueprint, ItemStack[] matrix) {
        return matches(blueprint, matrix, false) || matches(blueprint, matrix, true);
    }

    /**
     * How many the recipe yields. Weapons and armour are one-offs; the bulk materials
     * and field supplies come out in useful handfuls so keeping stocked is not a chore.
     */
    public static int amount(String id) {
        if ("scrap".equals(id)) return 4;
        if ("alloy_plate".equals(id)) return 4;
        if ("ballistic_fiber".equals(id)) return 2;
        if ("field_ration".equals(id)) return 2;
        if ("sanitized_flesh".equals(id)) return 2;
        if ("power_cell".equals(id)) return 2;
        return 1;
    }
}
