package chat.jaspr.dungeon;

import chat.jaspr.dungeon.EncounterCatalog.Shape;
import chat.jaspr.dungeon.EncounterCatalog.Species;
import chat.jaspr.dungeon.EncounterCatalog.Status;
import chat.jaspr.dungeon.HazardCatalog.Type;

/**
 * Generation 7, Floor II: The Underworks (themes 54..77). Owner 2026-10-05: "each tier should have completely different
 * room layouts, setups, and themes for their rooms. Each tier progressively gets harder"; "physical dangers as well, such
 * as lava pools, falling stalactites, and other things of that nature"; "a plethora of mobs ... more spread out in large
 * rooms"; gunners, but "not ... in the easier stages"; mutants "prevalent throughout the entire dungeon experience". Pure.
 *
 * Plan: parcels split into caverns, not halls. Nine partitions (great caverns, a cavern held by galleries, four long
 * galleries, galleries flanking a grotto chain, grotto chains, asymmetric L clusters, a cavern ringed by grottos, a gallery
 * over a cavern, twin halls), each turned and mirrored by the parcel's hash; the origin parcel keeps the engine's four small
 * refuges and three 2 x 2 rooms. Threat 2..5 climbs with the distance toward the Descent. Bosses only in caverns at least
 * two tiles wide.
 *
 * Blocks: FloorTwoCaves (refuges are the House of Mercy's own rooms in cave stone). Perils are drawn exactly where
 * peril() says, for Perils to bring to life:
 * STALACTITE  a thin tip (a stone wall block, FloorTwoThemes.Look.tip) at y tip(r, x, z) under stone up to the ceiling,
 *             over open floor; at most one in every 5 x 5 cells.
 * LAVA        lava (11) at y 64 over solid stone at y 63: pools, channels, forge basins and fall moats.
 * CRUMBLE     cracked stone bricks (98:2) at y 64, air at y 63, magma at y 62: a pit one block deep. The cracked block is
 *             the only 98:2 at floor height, so restoring block(r, x, 64, z) mends it.
 * GEYSER      a magma block (213) at y 64, the only magma at floor height.
 * LAVA_FALL   lava from y 64 to the ceiling, sealed in a glass tube (the eight cells around it).
 * None of them on a lane (so never on a core, a doorway, a station or under the chest) and none in a refuge.
 */
final class FloorTwo {
    private FloorTwo() {}
    static final int BASE = Floors.FLOOR_TWO_BASE, COUNT = Floors.FLOOR_TWO_THEMES;
    private static final long SALT_PARCEL = 0x556e646572L, SALT_ROOM = 0x576f726b73L;

    // ---------------------------------------------------------------- the plan
    /** Parcel partitions, tiles {x, z, w, d} on the 4 x 4 grid of a parcel. */
    static final int[][][] PARTITIONS = {
        {{0, 0, 4, 4}},                                                                                  // the great cavern
        {{0, 0, 3, 3}, {3, 0, 1, 4}, {0, 3, 3, 1}},                                                      // a cavern held by two galleries
        {{0, 0, 1, 4}, {1, 0, 1, 4}, {2, 0, 1, 4}, {3, 0, 1, 4}},                                        // four long galleries
        {{0, 0, 1, 4}, {3, 0, 1, 4}, {1, 0, 1, 2}, {1, 2, 1, 1}, {1, 3, 1, 1}, {2, 0, 1, 1}, {2, 1, 1, 2}, {2, 3, 1, 1}}, // galleries and grottos
        {{0, 0, 1, 2}, {0, 2, 1, 1}, {0, 3, 2, 1}, {1, 0, 1, 1}, {1, 1, 1, 2}, {2, 0, 2, 1}, {2, 1, 1, 1}, {3, 1, 1, 2}, {2, 2, 1, 2}, {3, 3, 1, 1}}, // a grotto chain
        {{0, 0, 3, 1}, {3, 0, 1, 1}, {0, 1, 2, 3}, {2, 1, 2, 2}, {2, 3, 2, 1}},                          // an asymmetric L cluster
        {{1, 1, 3, 3}, {0, 0, 1, 1}, {1, 0, 2, 1}, {3, 0, 1, 1}, {0, 1, 1, 2}, {0, 3, 1, 1}},            // a cavern ringed by grottos
        {{0, 0, 4, 1}, {0, 1, 3, 3}, {3, 1, 1, 3}},                                                      // a gallery over a cavern
        {{0, 0, 2, 3}, {2, 0, 2, 3}, {0, 3, 4, 1}}                                                       // twin halls and a gallery
    };
    static final String[] PARTITION_NAMES = {"great cavern", "cavern and galleries", "long galleries", "galleries and grottos",
        "grotto chain", "L cluster", "ringed cavern", "gallery over cavern", "twin halls"};
    static final int[] PARTITION_WEIGHTS = {10, 14, 9, 12, 11, 14, 12, 10, 8};

    /** Which partition a parcel uses (an index into PARTITIONS); its symmetry is (hash >>> 24) mod 8. */
    static int partition(long parcelHash) {
        int roll = (int) Math.floorMod(parcelHash, 100);
        for (int i = 0; i < PARTITION_WEIGHTS.length; i++) { roll -= PARTITION_WEIGHTS[i]; if (roll < 0) return i; }
        return 0;
    }
    /** The room at (x, z) of a Floor II layout (Layout plans the Descent's parcel before it asks). */
    static Layout.Room at(Layout l, int x, int z) {
        int tx = Math.floorDiv(x, Layout.TILE), tz = Math.floorDiv(z, Layout.TILE), px = Math.floorDiv(tx, 4), pz = Math.floorDiv(tz, 4);
        int lx = Math.floorMod(tx, 4), lz = Math.floorMod(tz, 4), ax, az, w, d;
        boolean refuge = px == 0 && pz == 0 && lx < 2 && lz < 2;
        if (px == 0 && pz == 0) {
            // The arrival parcel keeps every floor's contract: four small refuges, three ordinary 2 x 2 rooms.
            if (refuge) { ax = tx; az = tz; w = d = 1; }
            else { ax = (lx / 2) * 2; az = (lz / 2) * 2; w = d = 2; }
        } else {
            long ph = l.hash(px, pz, SALT_PARCEL);
            int[][] parts = PARTITIONS[partition(ph)];
            int sym = (int) Math.floorMod(ph >>> 24, 8);
            boolean swap = (sym & 4) != 0, flipA = (sym & 1) != 0, flipB = (sym & 2) != 0;
            int a = swap ? lz : lx, b = swap ? lx : lz;
            if (flipA) a = 3 - a;
            if (flipB) b = 3 - b;
            int[] q = parts[0];
            for (int[] p : parts) if (a >= p[0] && a < p[0] + p[2] && b >= p[1] && b < p[1] + p[3]) { q = p; break; }
            int a0 = q[0], a1 = q[0] + q[2] - 1, b0 = q[1], b1 = q[1] + q[3] - 1;
            if (flipA) { int s = 3 - a1; a1 = 3 - a0; a0 = s; }
            if (flipB) { int s = 3 - b1; b1 = 3 - b0; b0 = s; }
            int x0 = swap ? b0 : a0, x1 = swap ? b1 : a1, z0 = swap ? a0 : b0, z1 = swap ? a1 : b1;
            ax = px * 4 + x0; az = pz * 4 + z0; w = x1 - x0 + 1; d = z1 - z0 + 1;
        }
        long h = l.hash(ax, az, SALT_ROOM);
        int theme = BASE + (int) Math.floorMod(h, COUNT);
        Layout.Kind kind = refuge ? Layout.Kind.REFUGE : kind(w, d, (int) Math.floorMod(h >>> 8, 100));
        return new Layout.Room(ax * Layout.TILE, az * Layout.TILE, w * Layout.TILE, d * Layout.TILE, theme,
            (int) Math.floorMod(h >>> 16, Layout.MOTIF_COUNT), tier(kind, w, d, px, pz), kind, h, 2);
    }
    /** Caverns two tiles wide hold the bosses; galleries run gauntlets; grottos hide treasure and shrines. */
    static Layout.Kind kind(int w, int d, int roll) {
        int tiles = w * d;
        if (Math.min(w, d) >= 2) {
            boolean big = tiles >= 9;
            return roll < (big ? 6 : 8) ? Layout.Kind.TREASURE : roll < (big ? 11 : 15) ? Layout.Kind.SHRINE
                : roll < (big ? 23 : 30) ? Layout.Kind.GAUNTLET : roll < (big ? 68 : 60) ? Layout.Kind.BOSS : Layout.Kind.BATTLE;
        }
        if (tiles >= 3) return roll < 8 ? Layout.Kind.TREASURE : roll < 15 ? Layout.Kind.SHRINE : roll < 50 ? Layout.Kind.GAUNTLET : Layout.Kind.BATTLE;
        if (tiles == 2) return roll < 12 ? Layout.Kind.TREASURE : roll < 22 ? Layout.Kind.SHRINE : roll < 42 ? Layout.Kind.GAUNTLET : Layout.Kind.BATTLE;
        return roll < 20 ? Layout.Kind.TREASURE : roll < 35 ? Layout.Kind.SHRINE : roll < 50 ? Layout.Kind.GAUNTLET : Layout.Kind.BATTLE;
    }
    /** Threat 2 by the arrival, one step per parcel ring toward the Descent, one more for great caverns and gauntlets. */
    static int tier(Layout.Kind kind, int w, int d, int px, int pz) {
        if (kind == Layout.Kind.REFUGE) return 0;
        int distance = (int) Math.floor(3 * Floors.depth(px, pz));
        return Math.min(5, 2 + distance + (w * d >= 9 ? 1 : 0) + (kind == Layout.Kind.GAUNTLET ? 1 : 0));
    }

    // ---------------------------------------------------------------- blocks and perils
    private static int data(int id, int value) { return id | (value << 12); }
    /** The refuges (the Last Candle of Floor II): the House of Mercy's room in cave stone -- stone bricks, polished andesite, spruce. */
    static final int[] REFUGE = {98, data(1, 6), data(98, 3), data(5, 1), data(95, 8)};
    static int block(Layout.Room r, int x, int y, int z) {
        if (r.kind == Layout.Kind.REFUGE) return DungeonGenerator.classic(r, x, y, z, REFUGE, DungeonGenerator.shellBottom(2), DungeonGenerator.shellTop(2));
        return FloorTwoCaves.block(r, x, y, z);
    }
    /** Where this floor draws each physical peril (see the class comment for how each one looks). */
    static boolean peril(PerilMarks.Kind k, Layout.Room r, int x, int z) {
        if (k == null || r == null || r.kind == Layout.Kind.REFUGE || !r.inner(x + .5, z + .5)) return false;
        return FloorTwoCaves.peril(r, x, z) == k.ordinal() + 1;
    }
    /** For Perils: the y of a stalactite's lowest block over (x, z), or -1 where none hangs. */
    static int stalactiteTip(Layout.Room r, int x, int z) {
        if (r == null || r.kind == Layout.Kind.REFUGE || !r.inner(x + .5, z + .5)) return -1;
        return FloorTwoCaves.tip(r, x, z);
    }

    // ---------------------------------------------------------------- encounters
    private static final EncounterCatalog.Entry[] ENTRIES = new EncounterCatalog.Entry[COUNT];
    private static Species[] pool(Species... s) { return s; }
    /**
     * A theme's boss and pool on one of the generation-6 signature patterns, a little harder than the House of Mercy:
     * windups four ticks shorter (never under 36), cooldowns fifteen shorter (never under 130), multi-pulse gaps two shorter.
     */
    private static void entry(int theme, String boss, Species species, Species[] pool, Shape shape, Status status, int statusTicks, String cue) {
        EncounterCatalog.Entry like = EncounterCatalog.entry(shape.ordinal());
        ENTRIES[theme - BASE] = new EncounterCatalog.Entry(theme, Layout.THEMES[theme], boss, species, pool, shape, status, statusTicks,
            like.bossCentered, Math.max(36, like.windupTicks - 4), Math.max(130, like.cooldownTicks - 15), like.pulses,
            like.pulses > 1 ? Math.max(28, like.pulseTicks - 2) : 0, cue);
    }
    static {
        // Gunners (TUNNEL_GUNNER) only where guns belong: the depot, the junction, the smugglers and the deep workings.
        // Mutants in every pool; five of them are bosses.
        entry(54, "The Slag Matriarch", Species.MAGMA_CUBE, pool(Species.MAGMA_LURKER, Species.BLAZE, Species.WITHER_SKELETON, Species.CREEPER_MINION, Species.MAGMA_CUBE),
            Shape.FURNACE_FAN, Status.WEAKNESS, 50, "Slag breath: get beside or behind the glowing fan of molten stone!");
        entry(55, "The Hanging Prior", Species.CEILING_STALKER, pool(Species.CEILING_STALKER, Species.SKELETON, Species.STRAY, Species.MUTANT_SKELETON, Species.CAVE_SPIDER),
            Shape.STARLESS_FALL, Status.SLOW, 50, "Falling spires: leave the six small marked impact discs before the points drop!");
        entry(56, "Mother Mycelia", Species.SLIME, pool(Species.SLIME, Species.WITCH, Species.ZOMBIE_VILLAGER, Species.SPIDER_PIG, Species.CAVE_SPIDER),
            Shape.FUNGAL_BLOOM, Status.HUNGER, 60, "Spore bloom: four caps swell outward three times; keep to the diagonal gaps!");
        entry(57, "The Drowned Foreman", Species.ZOMBIE, pool(Species.ZOMBIE, Species.DEEP_MINER, Species.STRAY, Species.SLIME, Species.CREEPER_MINION),
            Shape.DROWNING_RING, Status.SLOW, 60, "Flood ring: the floodwater rises in a ring; its dry center and outer shore are safe!");
        entry(58, "The Prism Seer", Species.EVOKER, pool(Species.ENDERMAN, Species.ENDERMITE, Species.STRAY, Species.MUTANT_SKELETON, Species.WITCH),
            Shape.OPAL_PRISM, Status.BLINDNESS, 30, "Crystal prism: a triangle of shards closes in three steps; cross its spent edges!");
        entry(59, "The Brood Sow", Species.SPIDER_PIG, pool(Species.SPIDER, Species.CAVE_SPIDER, Species.CEILING_STALKER, Species.SPIDER_PIG, Species.SILVERFISH),
            Shape.THORN_CROWN, Status.SLOW, 60, "Web snare: slip through the four gaps or stay inside the ring of silk!");
        entry(60, "The Ossified Titan", Species.MUTANT_SKELETON, pool(Species.SKELETON, Species.WITHER_SKELETON, Species.HUSK, Species.MUTANT_SKELETON, Species.GNAWER_SWARM),
            Shape.BONE_CROSS, Status.WEAKNESS, 50, "Ossuary cross: bones burst along a cross; step diagonally out of its arms!");
        entry(61, "The Lost Shift-Boss", Species.ZOMBIE_VILLAGER, pool(Species.DEEP_MINER, Species.ZOMBIE_VILLAGER, Species.CAVE_SPIDER, Species.SKELETON, Species.CREEPER_MINION),
            Shape.GALLOWS_BAR, Status.SLOW_DIGGING, 50, "Falling timber: a beam comes down crosswise; move forward or back out of its band!");
        entry(62, "The Cascade Tyrant", Species.BLAZE, pool(Species.BLAZE, Species.MAGMA_LURKER, Species.MAGMA_CUBE, Species.WITHER_SKELETON, Species.MUTANT_CREEPER),
            Shape.SALT_SWEEP, Status.NONE, 0, "Molten surge: three waves of lava roll away from the tyrant; get behind or beside them!");
        entry(63, "The Burrow Matron", Species.SILVERFISH, pool(Species.SILVERFISH, Species.GNAWER_SWARM, Species.ENDERMITE, Species.VINDICATOR, Species.MUTANT_SKELETON),
            Shape.MOURNING_MAZE, Status.SLOW_DIGGING, 60, "Collapsing burrows: square walls close in as their gate turns; follow the gap or get out!");
        entry(64, "The Powder Keeper", Species.MUTANT_CREEPER, pool(Species.TUNNEL_GUNNER, Species.CREEPER_MINION, Species.MUTANT_CREEPER, Species.SKELETON, Species.VINDICATOR),
            Shape.PLAGUE_PATCHES, Status.BLINDNESS, 30, "Powder charges: three kegs are lit; leave the three marked blast patches!");
        entry(65, "The Last Conductor", Species.TUNNEL_GUNNER, pool(Species.TUNNEL_GUNNER, Species.DEEP_MINER, Species.ZOMBIE, Species.CREEPER_MINION, Species.CAVE_SPIDER),
            Shape.COURT_LUNGE, Status.SLOW, 40, "Runaway cart: a cart comes down the line; sidestep the long narrow track!");
        entry(66, "The Brimstone Hag", Species.WITCH, pool(Species.WITCH, Species.SLIME, Species.MAGMA_CUBE, Species.ZOMBIE_VILLAGER, Species.SPIDER_PIG),
            Shape.RELIQUARY_TIDES, Status.HUNGER, 60, "Boiling tide: the outer ring of the spring erupts, then the inner ring!");
        entry(67, "The Black Mason", Species.WITHER_SKELETON, pool(Species.WITHER_SKELETON, Species.DEEP_MINER, Species.RUST_GOLEM, Species.MUTANT_SKELETON, Species.ENDERMITE),
            Shape.OBSIDIAN_SCISSORS, Status.WEAKNESS, 50, "Quarry saws: two black blades close toward the mason's line; get behind their hinge!");
        entry(68, "The Lantern Mother", Species.CAVE_SPIDER, pool(Species.CAVE_SPIDER, Species.CEILING_STALKER, Species.SILVERFISH, Species.WITCH, Species.CREEPER_MINION),
            Shape.SILENT_ECHO, Status.BLINDNESS, 40, "Glowworm pulse: three oval waves of light spread; step inside each spent wave!");
        entry(69, "The Cinder Smith", Species.DEEP_MINER, pool(Species.VINDICATOR, Species.RUST_GOLEM, Species.BLAZE, Species.MAGMA_LURKER, Species.MUTANT_CREEPER),
            Shape.FOUNDRY_PISTONS, Status.SLOW_DIGGING, 50, "Trip hammers: staggered hammers switch lanes three times; stand in the clear lane!");
        entry(70, "The Ferryman Below", Species.STRAY, pool(Species.STRAY, Species.ZOMBIE, Species.SLIME, Species.CEILING_STALKER, Species.MUTANT_SKELETON),
            Shape.ROOT_FORK, Status.SLOW, 50, "Forking current: the river splits in two; keep between or outside its branches!");
        entry(71, "The Corroded Sentinel", Species.RUST_GOLEM, pool(Species.RUST_GOLEM, Species.DEEP_MINER, Species.HUSK, Species.GNAWER_SWARM, Species.CREEPER_MINION),
            Shape.IRON_JAWS, Status.WEAKNESS, 60, "Rusted jaws: two bars grind shut in three steps; follow the gap or step past their ends!");
        entry(72, "The Geode Heart", Species.ENDERMAN, pool(Species.ENDERMAN, Species.ENDERMITE, Species.EVOKER, Species.SILVERFISH, Species.SPIDER_PIG),
            Shape.AMBER_LATTICE, Status.NONE, 0, "Crystal lattice: diagonal seams of shards shift; move between the clear diamonds!");
        entry(73, "The Rootbound Hermit", Species.HUSK, pool(Species.HUSK, Species.SPIDER, Species.ZOMBIE_VILLAGER, Species.SLIME, Species.SPIDER_PIG),
            Shape.CRADLE_PAIR, Status.SLOW, 60, "Twin taproots: two roots burst up; stand between the two marked discs!");
        entry(74, "Captain Saltgrin", Species.VINDICATOR, pool(Species.TUNNEL_GUNNER, Species.VINDICATOR, Species.ZOMBIE, Species.SPIDER, Species.CREEPER_MINION),
            Shape.ARCHIVE_LINES, Status.WEAKNESS, 40, "Crossfire: the crew fires three parallel volleys; step between the shot lines!");
        entry(75, "The Colonnade Colossus", Species.MUTANT_SKELETON, pool(Species.SKELETON, Species.STRAY, Species.CEILING_STALKER, Species.MUTANT_SKELETON, Species.GNAWER_SWARM),
            Shape.CLOCK_HANDS, Status.SLOW, 40, "Toppling pillars: columns fall in three quarter-turns; follow the fresh marks!");
        entry(76, "The Smouldering Mother", Species.MUTANT_CREEPER, pool(Species.MAGMA_LURKER, Species.BLAZE, Species.MUTANT_CREEPER, Species.CREEPER_MINION, Species.HUSK),
            Shape.HOLLOW_HALO, Status.HUNGER, 50, "Ember ring: a ring of embers flares; hug the burrower or get beyond the ring!");
        entry(77, "The Deep Overseer", Species.TUNNEL_GUNNER, pool(Species.TUNNEL_GUNNER, Species.DEEP_MINER, Species.RUST_GOLEM, Species.MUTANT_SKELETON, Species.WITHER_SKELETON),
            Shape.BASILICA_CHECKER, Status.SLOW_DIGGING, 60, "Blasting grid: alternate squares of the floor blow; switch to a spent square!");
    }
    static EncounterCatalog.Entry entry(int theme) {
        int i = theme - BASE;
        return i < 0 || i >= COUNT ? null : ENTRIES[i];
    }

    // ---------------------------------------------------------------- traps
    private static final Type VENTS = Type.FLAME_VENTS, DARTS = Type.DART_SLITS, RUNES = Type.SPIKE_RUNES, MASONRY = Type.FALLING_MASONRY,
        MIASMA = Type.MIASMA, FROST = Type.FROST_GUSTS, SMITE = Type.SMITE, SHOCK = Type.SHOCKWAVE, BLADES = Type.PHANTOM_BLADES,
        RAIN = Type.POTION_RAIN, WELL = Type.GRAVITY_WELL, DARK = Type.CREEPING_DARK, SPORES = Type.BLAST_SPORES, EMBERS = Type.EMBER_BOLTS;
    /** Three dangers that suit each theme: at most one seizing danger in a trio, the gravity well in two themes only. */
    private static final Type[][] FAVOURED = {
        {VENTS, EMBERS, SHOCK}, {MASONRY, SMITE, BLADES}, {SPORES, MIASMA, DARK}, {RAIN, MASONRY, FROST}, {SMITE, BLADES, RUNES}, {MIASMA, DARTS, DARK},
        {RUNES, MASONRY, WELL}, {MASONRY, DARTS, SHOCK}, {EMBERS, VENTS, RAIN}, {DARTS, RUNES, BLADES}, {SPORES, EMBERS, SHOCK}, {BLADES, DARTS, SHOCK},
        {VENTS, MIASMA, RAIN}, {RUNES, SMITE, WELL}, {SMITE, RAIN, DARK}, {VENTS, MASONRY, EMBERS}, {FROST, RAIN, BLADES}, {DARTS, MASONRY, SPORES},
        {SMITE, RUNES, FROST}, {SPORES, MIASMA, RUNES}, {DARTS, BLADES, DARK}, {MASONRY, SHOCK, SMITE}, {VENTS, EMBERS, MIASMA}, {VENTS, DARTS, SHOCK}
    };
    static HazardCatalog.Type[] favoured(int theme) {
        int i = theme - BASE;
        return i < 0 || i >= COUNT ? null : FAVOURED[i].clone();
    }
}
