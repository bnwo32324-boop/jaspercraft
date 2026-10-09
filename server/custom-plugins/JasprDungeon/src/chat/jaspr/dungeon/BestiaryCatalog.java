package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Species;
import java.util.*;

/**
 * Generation 7 (owner 2026-10-05: "add ... custom mobs. Have them be totally original"; "a plethora of mobs"; "the further you
 * venture into the dungeons, it should progressively get harder and harder"): everything that makes a custom species itself, and
 * the elites, as data. Pure: no Bukkit objects, no clocks, no random state. Bestiary dresses and drives the mobs from it and
 * BestiaryAuditTest holds every bound.
 */
public final class BestiaryCatalog {
    private BestiaryCatalog() {}
    /** The chat colour introducer (the section sign). */
    public static final char SECTION = (char) 167;

    /** What a species does besides walking at you. */
    public enum Kind { NONE, LASH, FLIT, SPLIT, SCREAM, DRAIN, GUARD, EMBERS, GUN, POUNCE, BLAST, SLAM, PACK, BASH, BLINK, MEND, SUMMON, RALLY }

    /**
     * A custom species: its look (name, equipment as "MATERIAL" or "MATERIAL#rrggbb" for dyed leather), its body (factors of the
     * vanilla base's speed and of the health Encounters gave it, a knockback resistance floor, a follow range) and one ability.
     * Ability numbers by kind (cooldown and windup in ticks, ranges in blocks, damage as a factor of {@link #base}):
     * LASH near..far trigger band, radius half-width of the whip; FLIT far trigger, radius how far from the target it lands;
     * SPLIT near is the health fraction it splits at; SCREAM far trigger, radius of the cry; DRAIN far tether reach, windup between
     * pulses; GUARD cooldown shield down, windup shield up; EMBERS cooldown between patches, radius of a patch; POUNCE near..far
     * leap band; BLAST near..far throwing band, radius of the blast; SLAM far trigger, radius of the shock; BASH near..far dash band;
     * BLINK far trigger, radius of the marked circle behind the target; MEND far reach to an ally, damage is the share of health
     * it restores; SUMMON far trigger, damage of its fangs; RALLY far reach to allied gunners. A plain species (surprises) only
     * wears its dress: its attributes stay as Encounters and the Secrets that summon it set them.
     */
    public static final class Def {
        public final Species species;
        public final String name;
        public final char colour;
        public final int floor;
        public final double hp, speed, resist, follow;
        public final String head, chest, legs, feet, hand, off;
        public final Kind kind;
        public final int cooldown, windup;
        public final double near, far, radius, damage, meleeCap;
        public final boolean plain;
        Def(Species species, String name, char colour, int floor, double hp, double speed, double resist, double follow, String[] gear,
            Kind kind, int cooldown, int windup, double near, double far, double radius, double damage, double meleeCap, boolean plain) {
            this.species = species; this.name = name; this.colour = colour; this.floor = floor;
            this.hp = hp; this.speed = speed; this.resist = resist; this.follow = follow;
            head = gear[0]; chest = gear[1]; legs = gear[2]; feet = gear[3]; hand = gear[4]; off = gear[5];
            this.kind = kind; this.cooldown = cooldown; this.windup = windup; this.near = near; this.far = far;
            this.radius = radius; this.damage = damage; this.meleeCap = meleeCap; this.plain = plain;
        }
        /** The name as shown: its colour, then the name. */
        public String shown() { return SECTION + "" + colour + name; }
        public String[] gear() { return new String[]{head, chest, legs, feet, hand, off}; }
    }

    private static final Map<Species, Def> DEFS = new EnumMap<Species, Def>(Species.class);
    private static String[] gear(String head, String chest, String legs, String feet, String hand, String off) { return new String[]{head, chest, legs, feet, hand, off}; }
    private static void def(Species s, String name, char colour, int floor, double hp, double speed, double resist, double follow, String[] gear,
                            Kind kind, int cooldown, int windup, double near, double far, double radius, double damage, double meleeCap) {
        DEFS.put(s, new Def(s, name, colour, floor, hp, speed, resist, follow, gear, kind, cooldown, windup, near, far, radius, damage, meleeCap, false));
    }
    private static void plain(Species s, String name, char colour, String[] gear) {
        DEFS.put(s, new Def(s, name, colour, 0, 1, 1, 0, 0, gear, Kind.NONE, 0, 0, 0, 0, 0, 0, 0, true));
    }
    static {
        // ---------------------------------------------------------------- Floor I: the House of Mercy (crypt, penitents, candles)
        def(Species.FLAGELLANT, "Flagellant", 'c', 1, 1.00, 1.10, .10, 36,
            gear("LEATHER_HELMET#4a4038", "LEATHER_CHESTPLATE#6e1a1a", "LEATHER_LEGGINGS#3a2f28", null, "LEASH", null),
            Kind.LASH, 70, 10, 2.2, 4.0, .95, .90, 0);
        def(Species.CANDLE_WISP, "Candle Wisp", 'e', 1, .55, 1.00, 0, 36,
            gear(null, null, null, null, "TORCH", null),
            Kind.FLIT, 70, 12, 0, 14, 3.0, 0, 0);
        def(Species.OSSUARY_CRAWLER, "Ossuary Crawler", 'f', 1, .60, 1.20, 0, 30,
            gear(null, null, null, null, null, null),
            Kind.SPLIT, 0, 0, .55, 0, 0, 0, 0);
        def(Species.CHOIR_BANSHEE, "Choir Banshee", 'd', 1, .90, 1.00, 0, 36,
            gear(null, null, null, null, "GHAST_TEAR", null),
            Kind.SCREAM, 170, 28, 0, 9, 7.5, .50, 0);
        def(Species.MIRE_LEECH, "Mire Leech", '2', 1, .80, 1.15, .10, 28,
            gear(null, null, null, null, null, null),
            Kind.DRAIN, 120, 12, 0, 5.5, 0, .25, 0);
        def(Species.GRAVEBOUND_KNIGHT, "Gravebound Knight", '8', 1, 1.50, .90, .40, 32,
            gear("IRON_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", null, "STONE_SWORD", "SHIELD"),
            Kind.GUARD, 50, 70, 0, 0, 0, 0, 0);
        // ---------------------------------------------------------------- Floor II: the Underworks (mines, ember, rust)
        def(Species.MAGMA_LURKER, "Magma Lurker", '6', 2, 1.20, 1.00, .20, 32,
            gear(null, null, null, null, null, null),
            Kind.EMBERS, 25, 10, 0, 0, 1.3, .30, 1.6);
        def(Species.TUNNEL_GUNNER, "Tunnel Gunner", '6', 2, .80, 1.00, .10, 30,
            gear("LEATHER_HELMET#c9a227", "LEATHER_CHESTPLATE#4a3b2a", "LEATHER_LEGGINGS#3a3a3a", "LEATHER_BOOTS#2a2a2a", "IRON_SWORD", null),
            Kind.GUN, 0, 0, 0, 0, 0, 0, 0);
        def(Species.CEILING_STALKER, "Ceiling Stalker", '8', 2, .90, 1.10, .20, 32,
            gear(null, null, null, null, null, null),
            Kind.POUNCE, 110, 14, 4.5, 10, 0, 1.10, 0);
        def(Species.DEEP_MINER, "Deep Miner", 'e', 2, 1.20, .95, .30, 32,
            gear("LEATHER_HELMET#d4a017", "LEATHER_CHESTPLATE#6b4423", "LEATHER_LEGGINGS#4a3a2a", "LEATHER_BOOTS#3a2a1a", "IRON_PICKAXE", null),
            Kind.BLAST, 150, 12, 4.5, 16, 4.0, 1.60, 0);
        def(Species.RUST_GOLEM, "Rust Golem", '4', 2, 2.00, .85, .70, 30,
            gear(null, null, null, null, null, null),
            Kind.SLAM, 120, 22, 0, 4.5, 4.2, 1.40, 2.4);
        def(Species.GNAWER_SWARM, "Gnawer", '5', 2, .45, 1.25, 0, 28,
            gear(null, null, null, null, null, null),
            Kind.PACK, 0, 0, 0, 0, 0, 0, 0);
        // ---------------------------------------------------------------- Floor III: the Abyssal Citadel (hellforged, void, blood)
        def(Species.ABYSSAL_GUNSLINGER, "Abyssal Gunslinger", '5', 3, .85, 1.05, .15, 34,
            gear("LEATHER_HELMET#1b1b3a", "LEATHER_CHESTPLATE#101028", "LEATHER_LEGGINGS#101028", "LEATHER_BOOTS#050510", "IRON_SWORD", null),
            Kind.GUN, 0, 0, 0, 0, 0, 0, 0);
        def(Species.HELLFORGED_SENTINEL, "Hellforged Sentinel", 'c', 3, 1.80, .95, .60, 32,
            gear("IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS", "IRON_SWORD", "SHIELD"),
            Kind.BASH, 130, 14, 3.0, 8, 0, 1.20, 0);
        def(Species.VOID_WRAITH, "Void Wraith", 'd', 3, 1.00, 1.05, .20, 36,
            gear(null, null, null, null, null, null),
            Kind.BLINK, 110, 14, 0, 15, 3.2, 1.10, 0);
        def(Species.BLOOD_TEMPLAR, "Blood Templar", '4', 3, 1.50, .95, .40, 32,
            gear(null, null, null, null, "IRON_AXE", null),
            Kind.MEND, 90, 20, 0, 10, 0, .25, 0);
        def(Species.DOOM_HERALD, "Doom Herald", '5', 3, 1.10, 1.00, .30, 32,
            gear(null, null, null, null, "TOTEM", null),
            Kind.SUMMON, 220, 24, 0, 16, 0, 1.20, 0);
        def(Species.SQUAD_CAPTAIN, "Squad Captain", '6', 3, 1.20, 1.00, .35, 34,
            gear("IRON_HELMET", "LEATHER_CHESTPLATE#8b0000", "LEATHER_LEGGINGS#2b2b2b", "IRON_BOOTS", "IRON_SWORD", null),
            Kind.RALLY, 220, 20, 0, 14, 0, 0, 0);
        // ---------------------------------------------------------------- surprises (Secrets drives them): a plain dress only
        plain(Species.MIMIC, "Mimic", '6', gear("CHEST", null, null, null, null, null));
        plain(Species.GILDED_THIEF, "Gilded Thief", 'e', gear("LEATHER_HELMET#d4af37", "LEATHER_CHESTPLATE#d4af37", "LEATHER_LEGGINGS#8a6d1a", "GOLD_BOOTS", "GOLD_INGOT", null));
    }

    /** The definition of a custom species, or null for a vanilla one or a mutant. */
    public static Def def(Species s) { return s == null ? null : DEFS.get(s); }
    /** Custom species are the ones that stand on a vanilla base under another name (mutants excepted). */
    public static boolean custom(Species s) { return s != null && !s.vanilla() && !s.mutant(); }
    public static List<Def> all() { return Collections.unmodifiableList(new ArrayList<Def>(DEFS.values())); }
    public static boolean gunner(Species s) { Def d = def(s); return d != null && (d.kind == Kind.GUN || d.kind == Kind.RALLY); }

    // ---------------------------------------------------------------- equipment specs
    /** The material name of a spec ("LEATHER_HELMET#4a4038" is LEATHER_HELMET). */
    public static String material(String spec) { int i = spec.indexOf('#'); return i < 0 ? spec : spec.substring(0, i); }
    /** The dye of a spec as 0xrrggbb, or -1 when it has none. */
    public static int color(String spec) { int i = spec.indexOf('#'); return i < 0 ? -1 : Integer.parseInt(spec.substring(i + 1), 16); }

    // ---------------------------------------------------------------- how hard a blow of this room is
    /**
     * The blow every ability is a factor of: Encounters' own ordinary-mob damage formula ((2 + 0.65 threat) times the floor's
     * factor), so a custom mob never out-hits the floor it stands on.
     */
    public static double base(int tier, int floor) { return (2 + .65 * Math.max(0, Math.min(5, tier))) * Floors.damage(floor); }

    // ---------------------------------------------------------------- gunners: when and where (owner: "I don't think it's appropriate to place them in the easier stages")
    /**
     * Guns come out only on Floors II and III, never on Floor I and its rift pockets, never beside the arrival (depth 0 on
     * Floor II) and never in a room of threat 0. A species that may not carry its gun fights with a sword instead.
     */
    public static boolean gunsAllowed(int floor, double depth, int tier) { return floor >= 2 && (floor >= 3 || depth > 0) && tier >= 1; }
    /** Armed gunners standing in one room at once. */
    public static int gunnerCap(int floor, int tier) { int t = Math.max(0, Math.min(5, tier)); return floor < 2 ? 0 : floor == 2 ? 2 + t / 2 : 3 + t; }
    /** Gunners that may aim at the same moment: the others reposition. */
    public static int aimCap(int floor, int tier) { return floor < 2 ? 0 : floor == 2 ? 2 : tier >= 4 ? 4 : 3; }

    // ---------------------------------------------------------------- elites
    /** One modifier makes a mob an elite: its name prefix, health, speed, damage dealt and damage taken. */
    public enum Mod {
        FRENZIED("Frenzied", '6', 1.15, 1.30, 1.25, 1.00),
        ARMOURED("Armoured", '7', 1.30, .95, 1.00, .65),
        VAMPIRIC("Vampiric", 'c', 1.20, 1.00, 1.00, 1.00),
        VOLATILE("Volatile", '4', 1.10, 1.00, 1.00, 1.00),
        SWIFT("Swift", 'b', 1.00, 1.55, .90, 1.00),
        WARDED("Warded", '9', 1.15, 1.00, 1.00, 1.00),
        THORNED("Thorned", '2', 1.25, 1.00, 1.00, 1.00),
        CHILLING("Chilling", '3', 1.15, 1.00, 1.00, 1.00);
        public final String prefix;
        public final char colour;
        public final double hp, speed, damage, taken;
        Mod(String prefix, char colour, double hp, double speed, double damage, double taken) {
            this.prefix = prefix; this.colour = colour; this.hp = hp; this.speed = speed; this.damage = damage; this.taken = taken;
        }
        public String shown() { return SECTION + "" + colour + prefix; }
    }
    /** Share of a warded elite's health that its ward absorbs before the body takes damage. */
    public static final double WARD_SHARE = .30;
    /** Elite share of the mobs of a room: rare on Floor I and only deep in it, more below, and rising with depth (0 at the origin parcel, 1 at the Descent's distance). */
    public static double eliteChance(int floor, double depth) {
        double d = Math.max(0, Math.min(1, depth));
        if (floor >= 3) return .07 + .145 * d;
        if (floor == 2) return .04 + .08 * d;
        return d < .6 ? 0 : .03 + .075 * (d - .6);
    }
    /** The modifiers a species can carry (a shulker never walks, a ghast flies by other rules). */
    public static List<Mod> mods(Species s) {
        List<Mod> out = new ArrayList<Mod>(Arrays.asList(Mod.values()));
        if (s == Species.SHULKER || s == Species.GHAST) { out.remove(Mod.FRENZIED); out.remove(Mod.SWIFT); }
        return out;
    }
    /**
     * The elite modifier of the mob in this slot of this room, or null: deterministic in the room's hash and the slot, so a room
     * that sleeps and wakes again keeps its elites. Mutants, surprises, bosses and runtime adds (slot below 0) are never elites.
     */
    public static Mod elite(long roomHash, int slot, boolean boss, int floor, double depth, Species s) {
        if (s == null || s.mutant() || slot < 0 || boss) return null;
        Def d = def(s);
        if (d != null && d.plain) return null;
        long n = Layout.mix(roomHash ^ 0x4c6974654c697465L ^ (slot * 0x9e3779b97f4a7c15L));
        double roll = (n >>> 11) * (1.0 / (1L << 53));
        if (roll >= eliteChance(floor, depth)) return null;
        List<Mod> pool = mods(s);
        return pool.get((int) Math.floorMod(Layout.mix(n ^ 0x4d6f64696669L), pool.size()));
    }

    // ---------------------------------------------------------------- small geometry the abilities share
    /** Distance from the point (px, pz) to the segment a-b on the floor plane. */
    public static double segment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
        double t = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
        return Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
    }
    /** A point inside the room's floor space, at least margin cells from every wall. */
    public static boolean inside(Layout.Room r, double x, double z, double margin) {
        return x >= r.x + margin && x < r.x + r.w - margin && z >= r.z + margin && z < r.z + r.d - margin;
    }
    /**
     * The spot dist behind a target that looks along yaw (Minecraft yaw: 0 faces +z, 90 faces -x), pulled in until it lies inside
     * the room, margin cells from the walls.
     */
    public static double[] behind(Layout.Room r, double tx, double tz, double yawDegrees, double dist, double margin) {
        double a = Math.toRadians(yawDegrees), fx = -Math.sin(a), fz = Math.cos(a);
        double x = tx - fx * dist, z = tz - fz * dist;
        double minX = r.x + margin, maxX = r.x + r.w - margin - 1e-6, minZ = r.z + margin, maxZ = r.z + r.d - margin - 1e-6;
        return new double[]{Math.max(minX, Math.min(maxX, x)), Math.max(minZ, Math.min(maxZ, z))};
    }
}
