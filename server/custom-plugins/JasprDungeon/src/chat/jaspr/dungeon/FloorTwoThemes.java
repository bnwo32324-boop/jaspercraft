package chat.jaspr.dungeon;

/**
 * Generation 7, Floor II (The Underworks): the 24 cavern looks. Owner 2026-10-05: "each tier should have completely
 * different room layouts, setups, and themes for their rooms". Pure data: the stone of walls, ceilings and outcrops (as
 * strata), the floors, pools, lamps, timber, which set pieces fill a theme's bays and how thickly its perils lie.
 * Construction materials only: no ores and no gold, iron, diamond, emerald, lapis, redstone or coal blocks.
 */
final class FloorTwoThemes {
    private FloorTwoThemes() {}
    static int b(int id, int data) { return id | data << 12; }

    // ---------------------------------------------------------------- set pieces (FloorTwoCaves draws one in each bay cell)
    static final int OPEN = 0, OUTCROP = 1, PILLARS = 2, STALAGMITES = 3, POOL = 4, MUSHROOM = 5, CRYSTALS = 6, GEODE = 7,
        TIMBERS = 8, CRATES = 9, BONES = 10, WEBS = 11, ROOTS = 12, MAZE = 13, FORGE = 14, TERRACES = 15, SPRINGS = 16,
        RAILS = 17, COLLAPSE = 18, FALLS = 19, CHANNEL = 20, QUARRY = 21, GLOWPOOL = 22, PIECES = 23;
    static final String[] PIECE_NAMES = {"open floor", "outcrop", "pillars", "stalagmites", "pool", "mushroom", "crystals",
        "geode", "timbers", "crates", "bones", "webs", "roots", "maze", "forge", "terraces", "springs", "rails", "collapse",
        "lava falls", "channel", "quarry", "glow pool"};
    /** Lamp styles at the bays' lane edges: a stone cairn, a post, a crystal, a brazier, a little glowing mushroom. */
    static final int CAIRN = 0, POST = 1, CRYSTAL = 2, BRAZIER = 3, SHROOM = 4;
    /** Small things scattered over open bay floor. */
    static final int STUB = 0, BOULDER = 1, BONE = 2, SHARD = 3, CRATE = 4, STUMP = 5, WEB = 6;

    static final int TORCH = b(50, 5), GLOWSTONE = 89, SEA_LANTERN = 169, JACK = 91;

    static final class Look {
        final String name;
        int rock, band, accent, floor, patch, bay, sub, rim, light, lamp, lampBase, glass, glass2, liquid, wood, log, fence;
        int leaves = b(18, 4), tip = b(139, 0), veins, scatter, scatterKind = STUB;
        int stalactites, geysers, crumbles, ceilingLights, lanterns = 35, threads;
        boolean river;
        int[] pieces = {OPEN, 1};
        int weight = 1;
        /** Wall, ceiling and outcrop stone by height: 64 strata, read at (y + wave + room shift) mod 64. */
        final int[] strata = new int[64];
        Look(String name) { this.name = name; }
        Look stone(int rock, int band, int accent) { this.rock = rock; this.band = band; this.accent = accent; return this; }
        /** Lane-safe floors (a full, opaque block that is neither magma nor soul sand), the bays' own floor, the stone under it. */
        Look ground(int floor, int patch, int bay, int sub) { this.floor = floor; this.patch = patch; this.bay = bay; this.sub = sub; return this; }
        Look pool(int liquid, int rim) { this.liquid = liquid; this.rim = rim; return this; }
        Look lamp(int style, int light, int base) { lamp = style; this.light = light; lampBase = base; return this; }
        Look glass(int a, int b) { glass = a; glass2 = b; return this; }
        Look wood(int planks, int log, int fence) { wood = planks; this.log = log; this.fence = fence; return this; }
        Look leaves(int leaves) { this.leaves = leaves; return this; }
        Look tip(int tip) { this.tip = tip; return this; }
        Look pieces(int... p) { pieces = p; weight = 0; for (int i = 1; i < p.length; i += 2) weight += p[i]; return this; }
        /** Peril odds, in a thousand, per candidate: stalactites (every 5 x 5 cells), vents (9 x 9), crumbling patches (10 x 10). */
        Look perils(int stalactites, int geysers, int crumbles) { this.stalactites = stalactites; this.geysers = geysers; this.crumbles = crumbles; return this; }
        /** Ceiling lamps (in a thousand per 7 x 7 cells), hanging lanterns (per 11 x 11), glowing threads under ceiling lamps. */
        Look ceiling(int lights, int lanterns, int threads) { ceilingLights = lights; this.lanterns = lanterns; this.threads = threads; return this; }
        Look scatter(int perThousand, int kind) { scatter = perThousand; scatterKind = kind; return this; }
        /** In a thousand: light set into the faces of pillars and great outcrops. */
        Look veins(int perThousand) { veins = perThousand; return this; }
        Look river() { river = true; return this; }
        /** The lamp that can hang or sit in stone: a torch theme lights its walls and ceilings with glowstone. */
        int solidLight() { return light == TORCH ? GLOWSTONE : light; }
        int piece(long roll) {
            int r = (int) Math.floorMod(roll, weight);
            for (int i = 0; i < pieces.length; i += 2) { r -= pieces[i + 1]; if (r < 0) return pieces[i]; }
            return pieces[0];
        }
        Look done(int index) {
            // Thick main stone, a band, main stone again, a thin accent seam; lengths vary per theme.
            long s = Layout.mix(0x5354524154414cL ^ index * 0x9e3779b97f4a7c15L);
            int i = 0, k = 0;
            while (i < 64) {
                int part = k & 3;
                long bits = Layout.mix(s + k);
                int len = part == 3 ? 1 + (int) (bits & 1) : part == 1 ? 2 + (int) (bits & 1) : 3 + (int) (bits & 3);
                int block = part == 1 ? band : part == 3 ? accent : rock;
                for (int n = 0; n < len && i < 64; n++) strata[i++] = block;
                k++;
            }
            return this;
        }
    }

    static final Look[] LOOKS = new Look[Floors.FLOOR_TWO_THEMES];
    static Look look(int theme) { return LOOKS[Math.floorMod(theme - Floors.FLOOR_TWO_BASE, LOOKS.length)]; }

    static {
        int i = 0;
        // 54 Magma Galleries: dark andesite over netherrack, magma seams, lava pools and channels behind obsidian rims.
        LOOKS[i] = new Look("Magma Galleries").stone(b(1, 5), 87, 213).ground(4, b(1, 5), 87, 1).pool(11, 49)
            .lamp(BRAZIER, GLOWSTONE, 112).glass(b(95, 1), b(95, 14)).wood(b(5, 5), b(162, 1), 191)
            .pieces(POOL, 4, CHANNEL, 3, OUTCROP, 2, STALAGMITES, 2, FALLS, 1, PILLARS, 1, OPEN, 1)
            .perils(260, 420, 120).ceiling(220, 30, 0).scatter(350, BOULDER).veins(60).done(i++);
        // 55 Stalactite Cathedral: pale stone naves, columns and a forest of hanging points.
        LOOKS[i] = new Look("Stalactite Cathedral").stone(1, b(1, 3), b(1, 4)).ground(1, b(1, 3), b(1, 6), 1).pool(9, b(1, 4))
            .lamp(CRYSTAL, SEA_LANTERN, b(95, 0)).glass(b(95, 0), b(95, 8)).wood(b(5, 1), b(17, 1), 188).tip(b(139, 0))
            .pieces(PILLARS, 4, STALAGMITES, 4, OUTCROP, 2, GLOWPOOL, 1, OPEN, 1)
            .perils(850, 0, 100).ceiling(200, 45, 0).scatter(500, STUB).veins(80).done(i++);
        // 56 Fungal Grotto: mycelium floors under giant red and brown mushrooms, glowing gills and spore vents.
        LOOKS[i] = new Look("Fungal Grotto").stone(1, 48, b(3, 2)).ground(b(3, 2), 110, 110, b(3, 0)).pool(9, 48)
            .lamp(SHROOM, GLOWSTONE, b(99, 10)).glass(b(95, 5), b(95, 13)).wood(b(5, 0), b(17, 0), 85).tip(b(139, 1))
            .pieces(MUSHROOM, 6, OUTCROP, 1, STALAGMITES, 1, GLOWPOOL, 1, ROOTS, 1, OPEN, 1)
            .perils(150, 260, 100).ceiling(150, 25, 0).scatter(300, STUMP).done(i++);
        // 57 Drowned Mine: flooded galleries, spruce timbering, rails under water-stained stone.
        LOOKS[i] = new Look("Drowned Mine").stone(4, 48, b(168, 2)).ground(48, 4, 13, 82).pool(9, b(98, 1))
            .lamp(POST, SEA_LANTERN, 188).glass(b(95, 3), b(95, 9)).wood(b(5, 1), b(17, 1), 188).tip(b(139, 1))
            .pieces(POOL, 4, TIMBERS, 3, RAILS, 2, CRATES, 1, COLLAPSE, 1)
            .perils(200, 0, 250).ceiling(100, 40, 0).scatter(250, BOULDER).done(i++);
        // 58 Crystal Hollow: white stone, crystal clusters and geodes lit from within.
        LOOKS[i] = new Look("Crystal Hollow").stone(b(1, 3), b(1, 4), 155).ground(b(1, 4), b(1, 3), 155, 1).pool(9, 155)
            .lamp(CRYSTAL, SEA_LANTERN, b(95, 3)).glass(b(95, 3), b(95, 2)).wood(b(5, 2), b(17, 2), 189)
            .pieces(CRYSTALS, 5, GEODE, 2, PILLARS, 1, GLOWPOOL, 1, OPEN, 1)
            .perils(350, 100, 50).ceiling(300, 30, 0).scatter(400, SHARD).veins(120).done(i++);
        // 59 Spider Warrens: coarse earth, web curtains, egg sacs and little light.
        LOOKS[i] = new Look("Spider Warrens").stone(4, b(3, 1), 48).ground(b(3, 1), 4, b(3, 1), b(3, 0)).pool(0, 4)
            .lamp(CAIRN, TORCH, 4).glass(b(95, 8), b(95, 0)).wood(b(5, 0), b(17, 0), 85).tip(b(139, 1))
            .pieces(WEBS, 5, OUTCROP, 2, STALAGMITES, 1, BONES, 1, OPEN, 1)
            .perils(250, 0, 250).ceiling(50, 20, 0).scatter(300, WEB).done(i++);
        // 60 Bone Pit: earth and gravel, bone mounds and ribs, cracked pits over magma.
        LOOKS[i] = new Look("Bone Pit").stone(1, b(3, 1), 216).ground(b(3, 1), 13, b(3, 1), 1).pool(11, 216)
            .lamp(CAIRN, JACK, 216).glass(b(95, 0), b(95, 14)).wood(b(5, 0), b(17, 0), 85)
            .pieces(BONES, 5, COLLAPSE, 1, POOL, 1, OUTCROP, 1, OPEN, 1)
            .perils(150, 100, 550).ceiling(80, 30, 0).scatter(350, BONE).done(i++);
        // 61 Forgotten Mineshaft: oak timbering, rails and plank floors gone rotten.
        LOOKS[i] = new Look("Forgotten Mineshaft").stone(1, b(1, 5), 4).ground(1, 13, b(5, 0), 1).pool(0, 4)
            .lamp(POST, TORCH, 85).glass(b(95, 4), b(95, 1)).wood(b(5, 0), b(17, 0), 85)
            .pieces(TIMBERS, 4, RAILS, 3, COLLAPSE, 2, CRATES, 1, WEBS, 1)
            .perils(150, 0, 350).ceiling(50, 45, 0).scatter(250, CRATE).done(i++);
        // 62 Lava Falls: molten falls in glass, obsidian and netherrack, the brightest cavern of the floor.
        LOOKS[i] = new Look("Lava Falls").stone(1, 87, 49).ground(87, 4, 49, 49).pool(11, 49)
            .lamp(BRAZIER, GLOWSTONE, 112).glass(b(95, 14), b(95, 1)).wood(b(5, 5), b(162, 1), 191)
            .pieces(FALLS, 5, POOL, 2, OUTCROP, 1, STALAGMITES, 1)
            .perils(150, 350, 100).ceiling(100, 20, 0).scatter(300, BOULDER).veins(40).done(i++);
        // 63 Silverfish Labyrinth: brick-faced rock and knee-high labyrinth walls over crumbling floors.
        LOOKS[i] = new Look("Silverfish Labyrinth").stone(98, b(98, 1), 1).ground(1, 4, 98, 1).pool(0, 98)
            .lamp(CAIRN, GLOWSTONE, 98).glass(b(95, 8), b(95, 7)).wood(b(5, 1), b(17, 1), 188)
            .pieces(MAZE, 5, OUTCROP, 1, COLLAPSE, 1, STALAGMITES, 1)
            .perils(150, 0, 500).ceiling(100, 30, 0).scatter(250, BOULDER).done(i++);
        // 64 Gunpowder Depot: crates, sacks and hay; no open flame among the powder, only cold lamps.
        LOOKS[i] = new Look("Gunpowder Depot").stone(1, b(1, 5), 98).ground(b(1, 6), b(1, 5), b(5, 1), 1).pool(0, 98)
            .lamp(CAIRN, SEA_LANTERN, 98).glass(b(95, 7), b(95, 15)).wood(b(5, 1), b(17, 1), 188)
            .pieces(CRATES, 5, TIMBERS, 2, RAILS, 1, OUTCROP, 1)
            .perils(150, 0, 250).ceiling(100, 30, 0).scatter(250, CRATE).done(i++);
        // 65 Rail Junction: gravel ballast, crossing tracks, signal lamps.
        LOOKS[i] = new Look("Rail Junction").stone(1, 4, b(1, 5)).ground(13, 1, 13, 1).pool(0, 98)
            .lamp(POST, JACK, 85).glass(b(95, 14), b(95, 5)).wood(b(5, 0), b(17, 0), 85)
            .pieces(RAILS, 5, TIMBERS, 2, CRATES, 1, COLLAPSE, 1)
            .perils(150, 0, 200).ceiling(100, 35, 0).scatter(200, STUMP).done(i++);
        // 66 Sulfur Springs: yellow stone, hot pools with sulfur crystals and steam vents.
        LOOKS[i] = new Look("Sulfur Springs").stone(1, b(159, 4), b(24, 2)).ground(b(24, 2), b(159, 0), b(159, 4), b(24, 0)).pool(9, b(159, 1))
            .lamp(CRYSTAL, GLOWSTONE, b(95, 4)).glass(b(95, 4), b(95, 1)).wood(b(5, 3), b(17, 3), 190)
            .pieces(SPRINGS, 5, CRYSTALS, 1, OUTCROP, 1, STALAGMITES, 1)
            .perils(150, 600, 100).ceiling(150, 25, 0).scatter(250, SHARD).done(i++);
        // 67 Obsidian Quarry: black stone cut in blocks and stepped terraces, purple glass, lava sumps.
        LOOKS[i] = new Look("Obsidian Quarry").stone(49, 1, 4).ground(1, 4, 49, 1).pool(11, 98)
            .lamp(POST, GLOWSTONE, 188).glass(b(95, 10), b(95, 15)).wood(b(5, 1), b(17, 1), 188)
            .pieces(QUARRY, 3, TERRACES, 3, POOL, 1, TIMBERS, 1)
            .perils(200, 100, 300).ceiling(150, 40, 0).scatter(250, BOULDER).done(i++);
        // 68 Glowworm Caves: a dark cave whose ceiling glitters with glowworms and their threads.
        LOOKS[i] = new Look("Glowworm Caves").stone(1, b(1, 5), 48).ground(1, 48, 48, 1).pool(9, 48)
            .lamp(CAIRN, SEA_LANTERN, 48).glass(b(95, 3), b(95, 9)).wood(b(5, 0), b(17, 0), 85).tip(b(139, 1))
            .pieces(GLOWPOOL, 2, STALAGMITES, 2, PILLARS, 2, OUTCROP, 1, OPEN, 1)
            .perils(300, 0, 100).ceiling(700, 15, 600).scatter(250, STUB).done(i++);
        // 69 Collapsed Forge: brick and nether-brick workings, lava basins under hoods, rubble everywhere.
        LOOKS[i] = new Look("Collapsed Forge").stone(98, 4, 112).ground(4, 98, 112, 1).pool(11, 112)
            .lamp(BRAZIER, GLOWSTONE, 112).glass(b(95, 1), b(95, 14)).wood(b(5, 5), b(162, 1), 191)
            .pieces(FORGE, 4, COLLAPSE, 3, CHANNEL, 1, CRATES, 1)
            .perils(150, 150, 500).ceiling(100, 30, 0).scatter(300, BOULDER).done(i++);
        // 70 Underground River: a sunken river winds through the room under every lane's bridge.
        LOOKS[i] = new Look("Underground River").stone(1, b(1, 5), b(1, 3)).ground(1, b(1, 5), 82, 1).pool(9, 1)
            .lamp(CAIRN, SEA_LANTERN, b(1, 5)).glass(b(95, 3), b(95, 11)).wood(b(5, 1), b(17, 1), 188)
            .pieces(OUTCROP, 2, STALAGMITES, 2, PILLARS, 1, OPEN, 1)
            .perils(350, 0, 150).ceiling(150, 30, 0).scatter(300, STUB).river().done(i++);
        // 71 Rust Cavern: granite and rust-coloured clay, old rails and heaps of corroded stone.
        LOOKS[i] = new Look("Rust Cavern").stone(b(1, 1), b(159, 1), b(159, 12)).ground(b(1, 2), b(159, 12), b(159, 14), 1).pool(0, b(1, 1))
            .lamp(CAIRN, JACK, b(159, 12)).glass(b(95, 1), b(95, 12)).wood(b(5, 4), b(162, 0), 192)
            .pieces(OUTCROP, 2, RAILS, 2, COLLAPSE, 2, TIMBERS, 1, PILLARS, 1)
            .perils(300, 0, 300).ceiling(100, 30, 0).scatter(300, BOULDER).done(i++);
        // 72 Geode Chamber: hollow geodes with crystal hearts, smooth grey shells.
        LOOKS[i] = new Look("Geode Chamber").stone(b(1, 6), 1, b(1, 3)).ground(b(1, 6), 1, b(1, 4), 1).pool(0, b(1, 4))
            .lamp(CRYSTAL, SEA_LANTERN, b(95, 10)).glass(b(95, 10), b(95, 2)).wood(b(5, 2), b(17, 2), 189)
            .pieces(GEODE, 5, CRYSTALS, 2, PILLARS, 1, OPEN, 1)
            .perils(300, 100, 50).ceiling(150, 25, 0).scatter(350, SHARD).veins(80).done(i++);
        // 73 Hollow Roots: earth walls pierced by giant roots, leaves under the ceiling, root lamps.
        LOOKS[i] = new Look("Hollow Roots").stone(b(3, 0), b(3, 1), 1).ground(b(3, 2), b(3, 1), b(3, 0), b(3, 0)).pool(9, b(3, 1))
            .lamp(POST, GLOWSTONE, 191).glass(b(95, 5), b(95, 13)).wood(b(5, 5), b(162, 1), 191).leaves(b(161, 5))
            .pieces(ROOTS, 5, OUTCROP, 1, MUSHROOM, 1, GLOWPOOL, 1, OPEN, 1)
            .perils(100, 0, 200).ceiling(80, 30, 0).scatter(250, STUMP).done(i++);
        // 74 Smugglers' Tunnels: spruce crates and sacks hidden in tight tunnels, lanterns and webs.
        LOOKS[i] = new Look("Smugglers' Tunnels").stone(1, 4, b(1, 5)).ground(4, b(1, 5), b(5, 1), 1).pool(0, 4)
            .lamp(POST, JACK, 188).glass(b(95, 12), b(95, 15)).wood(b(5, 1), b(17, 1), 188)
            .pieces(CRATES, 5, TIMBERS, 2, RAILS, 1, WEBS, 1)
            .perils(150, 0, 300).ceiling(50, 40, 0).scatter(250, CRATE).done(i++);
        // 75 Pillar Caves: banded natural columns from floor to ceiling, lights set into the stone.
        LOOKS[i] = new Look("Pillar Caves").stone(1, b(1, 5), b(1, 3)).ground(1, b(1, 5), 1, 1).pool(0, 98)
            .lamp(CAIRN, GLOWSTONE, b(1, 6)).glass(b(95, 0), b(95, 8)).wood(b(5, 1), b(17, 1), 188)
            .pieces(PILLARS, 6, STALAGMITES, 2, OUTCROP, 1)
            .perils(600, 0, 100).ceiling(100, 30, 0).scatter(300, STUB).veins(300).done(i++);
        // 76 Ember Burrows: netherrack burrows, soul-sand hollows, ember vents and small lava pockets.
        LOOKS[i] = new Look("Ember Burrows").stone(87, b(1, 1), 213).ground(87, b(3, 1), 88, 87).pool(11, 215)
            .lamp(BRAZIER, GLOWSTONE, 215).glass(b(95, 14), b(95, 1)).wood(b(5, 5), b(162, 1), 191)
            .pieces(POOL, 2, OUTCROP, 2, STALAGMITES, 2, CHANNEL, 1, COLLAPSE, 1)
            .perils(150, 550, 300).ceiling(80, 25, 0).scatter(300, BOULDER).veins(40).done(i++);
        // 77 The Deep Workings: the deepest mine, dressed stone, dark oak, forges, rails and lava channels.
        LOOKS[i] = new Look("The Deep Workings").stone(98, b(1, 6), 49).ground(b(1, 6), 98, 98, 1).pool(11, 49)
            .lamp(POST, JACK, 191).glass(b(95, 7), b(95, 1)).wood(b(5, 5), b(162, 1), 191)
            .pieces(TIMBERS, 2, RAILS, 2, FORGE, 2, CHANNEL, 1, CRATES, 1, COLLAPSE, 1, TERRACES, 1)
            .perils(250, 200, 350).ceiling(120, 40, 0).scatter(250, CRATE).done(i++);
    }
}
