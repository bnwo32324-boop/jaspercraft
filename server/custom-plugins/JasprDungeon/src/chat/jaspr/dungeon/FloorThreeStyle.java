package chat.jaspr.dungeon;

/**
 * Generation 7, Floor III (The Abyssal Citadel): the look of each of its 24 themes. Pure tables, read by FloorThreeBlocks.
 * Owner 2026-10-05: "each tier should have completely different room layouts, setups, and themes for their rooms." Every
 * theme has its own masonry, causeway, light and glass, its own sea (a lava sea, or soul sand pits where the theme is a
 * garden or a catacomb) and the bay structures it raises (towers, forges, cages, thrones ...). No valuable blocks anywhere:
 * the rich-looking themes (Molten Reliquary, Cursed Treasury, Pyre of Kings) use terracotta, glazed terracotta, quartz and
 * glowstone, never gold, iron, diamond or emerald.
 */
final class FloorThreeStyle {
    private FloorThreeStyle() {}
    private static int d(int id, int data) { return id | (data << 12); }

    /** Palette slots. DECK, EDGE and INLAY are the causeway surface: always a full, opaque, non-burning floor (audited). */
    static final int WALL = 0, TRIM = 1, DECK = 2, EDGE = 3, INLAY = 4, TOWER = 5, ACCENT = 6, LIGHT = 7, GLASS = 8,
        ISLAND = 9, BED = 10, CEIL = 11, RIB = 12;
    /** Bay structures (one complete structure per 7 x 7 bay). */
    static final int TOWER_KEEP = 0, PILLAR = 1, DAIS = 2, POOL = 3, SEA = 4, OBELISK = 5, CAGE = 6, GALLOWS = 7, BOOKS = 8,
        CHORUS = 9, THRONE = 10, PYRE = 11, MIRRORS = 12, HOARD = 13, FORGE = 14, RUIN = 15, GATE = 16, OSSUARY = 17,
        BUNKS = 18, ORRERY = 19, STRUCTURES = 20;
    static final String[] STRUCTURE_NAMES = {"Keep Tower", "Pillar", "Brazier Dais", "Lava Pool", "Open Sea", "Obelisk", "Hanging Cage",
        "Gallows", "Book Stacks", "Chorus Garden", "Throne", "Pyre", "Mirror Screens", "Hoard", "Forge Crucible", "Ruined Tower",
        "Abyss Frame", "Ossuary Arch", "Bunks", "Orrery"};

    /** Wall, trim, deck, edge, inlay, tower, accent, light, glass, island, sea bed, ceiling, rib; in theme order 78..101. */
    static final int[][] PALETTES = {
        {112, 215, 112, 215, d(159, 1), 112, 215, 89, d(95, 1), 112, 87, 112, 215},                                   // Hellforge Bridges
        {155, 215, 155, 215, d(159, 14), d(155, 2), 215, 169, d(95, 14), 155, 49, 155, 215},                          // Throne Approach
        {215, 214, 215, 112, 214, 215, 214, 89, d(95, 14), 215, 87, 215, 112},                                        // Bleeding Ramparts
        {201, 206, 206, 201, d(251, 10), 202, 49, 169, d(95, 15), 121, 49, 49, 201},                                  // Void Gardens
        {49, 112, 49, 112, d(251, 10), 49, d(251, 10), 169, d(95, 10), 49, 49, 49, 112},                              // Obsidian Spire
        {112, 155, 155, 112, d(159, 3), d(155, 2), 88, 169, d(95, 3), 88, 88, 112, 155},                              // Soulfire Chapel
        {112, 49, 112, 49, d(159, 7), 112, 49, 89, d(95, 7), 112, 87, 112, 49},                                       // Chain Hall
        {112, 215, 112, 215, d(159, 1), 112, 213, 89, d(95, 1), 87, 213, 112, 215},                                   // Furnace of Souls
        {206, 155, 155, 206, d(159, 3), 155, d(251, 3), 169, d(95, 3), 206, 49, 206, 155},                            // Shattered Sky Halls
        {215, 216, 24, 216, 179, 216, 215, 89, d(95, 0), 24, 87, 215, 216},                                           // Bone Colosseum
        {112, 98, 98, 112, d(98, 3), 112, d(5, 5), 89, d(95, 15), 98, 87, 112, 98},                                   // The Gallows Keep
        {112, 215, d(5, 5), 112, d(5, 1), 112, d(17, 1), 89, d(95, 1), d(5, 5), 87, 112, d(5, 5)},                    // Lava Sea Docks
        {201, 202, 206, 201, 49, 201, 47, 169, d(95, 2), 206, 49, 201, 202},                                          // Ender Archive
        {215, 112, 215, 112, d(159, 14), 215, d(35, 14), 89, d(95, 14), 215, 87, 112, 215},                           // Crimson Barracks
        {112, 49, 112, 49, d(159, 1), 49, 213, 89, d(95, 1), 112, 213, 49, 112},                                      // Doom Foundry
        {155, d(155, 1), 155, d(155, 1), d(251, 0), d(155, 2), d(251, 8), 169, d(95, 0), 155, 49, 155, d(155, 1)},    // Hall of Mirrors
        {d(251, 7), d(251, 15), d(159, 8), d(251, 15), d(159, 7), d(251, 15), d(159, 15), 89, d(95, 8), d(159, 8), 87, d(251, 7), d(251, 15)}, // Ashen Throne Room
        {49, d(251, 10), 49, d(251, 15), d(251, 10), 49, 245, 169, d(95, 10), 49, 49, 49, d(251, 10)},                // The Abyss Gate
        {112, d(159, 4), 215, d(159, 4), 236, 112, 216, 89, d(95, 4), 215, 213, 112, d(159, 4)},                      // Pyre of Kings
        {206, 201, 206, d(251, 11), d(251, 3), 155, d(251, 11), 169, d(95, 11), 121, 49, d(251, 11), 201},            // Starfall Observatory
        {112, 216, 112, 216, d(159, 15), 112, 216, 169, d(95, 15), 88, 88, 112, 216},                                 // Wraith Catacombs
        {155, 236, 155, d(159, 1), d(159, 4), 155, 236, 89, d(95, 1), 155, 213, 155, d(159, 4)},                      // Molten Reliquary
        {d(159, 15), d(159, 4), 155, d(159, 4), 239, d(159, 15), 239, 89, d(95, 10), d(159, 15), 49, d(159, 15), d(159, 4)}, // Cursed Treasury
        {49, 112, 112, 49, 215, 49, 112, 89, d(95, 15), 112, 87, 49, 112}                                             // The Last Bastion
    };
    /** The structures each theme raises in its bays (picked per bay); pools, open sea and braziers are mixed in by threat. */
    static final int[][] STRUCTURES_OF = {
        {TOWER_KEEP, FORGE, PILLAR, OBELISK}, {PILLAR, OBELISK, THRONE, PILLAR}, {TOWER_KEEP, RUIN, CAGE, PILLAR},
        {CHORUS, CHORUS, ORRERY, OBELISK}, {TOWER_KEEP, TOWER_KEEP, OBELISK, PILLAR}, {PYRE, PILLAR, OSSUARY, THRONE},
        {CAGE, GALLOWS, PILLAR, CAGE}, {FORGE, FORGE, PYRE, TOWER_KEEP}, {RUIN, RUIN, PILLAR, ORRERY},
        {OSSUARY, PILLAR, THRONE, OSSUARY}, {GALLOWS, GALLOWS, CAGE, TOWER_KEEP}, {BUNKS, GALLOWS, PILLAR, HOARD},
        {BOOKS, BOOKS, ORRERY, PILLAR}, {BUNKS, BUNKS, TOWER_KEEP, CAGE}, {FORGE, FORGE, PILLAR, TOWER_KEEP},
        {MIRRORS, MIRRORS, PILLAR, ORRERY}, {THRONE, PILLAR, PYRE, OBELISK}, {GATE, GATE, OBELISK, PILLAR},
        {PYRE, PYRE, THRONE, OBELISK}, {ORRERY, ORRERY, OBELISK, RUIN}, {OSSUARY, OSSUARY, CAGE, OBELISK},
        {HOARD, PILLAR, FORGE, THRONE}, {HOARD, HOARD, CAGE, PILLAR}, {TOWER_KEEP, TOWER_KEEP, BUNKS, PILLAR}
    };
    /** The Throne of the Abyss: pillars, the throne itself, pyres and obelisks around the final arena. */
    static final int[] THRONE_STRUCTURES = {PILLAR, THRONE, PYRE, OBELISK};
    /** Soul sand pits instead of the lava sea (the gardens and the catacombs): "falls land in lava or soul sand pits". */
    static final boolean[] SOUL_SEA = flags(81, 98);
    /** A glass sky over the halls, lit from above. */
    static final boolean[] SKY = flags(86);
    /** Lamps scattered in the ceiling like stars. */
    static final boolean[] STARS = flags(81, 97);
    /** Dense hanging chains. */
    static final boolean[] CHAINS = flags(84, 88);
    /** Per theme, in a hundred: how often a bay pours an open lava fall from the roof. */
    static final int[] FALLS = {35, 12, 35, 4, 12, 10, 12, 40, 12, 12, 12, 30, 8, 12, 40, 8, 12, 12, 30, 10, 4, 35, 12, 12};

    private static boolean[] flags(int... themes) {
        boolean[] out = new boolean[Floors.FLOOR_THREE_THEMES];
        for (int t : themes) out[t - Floors.FLOOR_THREE_BASE] = true;
        return out;
    }
    /** A theme's index 0..23 (anything outside Floor III's range wraps, so a stray theme still draws). */
    static int index(int theme) { return Math.floorMod(theme - Floors.FLOOR_THREE_BASE, Floors.FLOOR_THREE_THEMES); }
    static int[] palette(int theme) { return PALETTES[index(theme)]; }
}
