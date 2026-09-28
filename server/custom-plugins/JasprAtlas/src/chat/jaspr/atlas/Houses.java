package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * Asterian buildings that people live and work in: courtyard houses, townhouses over shops, shops of every trade,
 * temples, schools, baths, stoas and libraries. Each is furnished for its use and has its people in it. A building's
 * frame origin is its centre; its front (local -z) faces the street. Sizes are given as half-widths.
 */
final class Houses {
    private Houses() {}

    // ------------------------------------------------------------------ dwellings

    /**
     * A courtyard house (oikos): rooms round an open court with a cistern, a kitchen, an andron (dining room with couches),
     * bedrooms, a study or loom room and a household shrine. hw x hd are half-sizes (>= 6 x 5).
     */
    static void courtyardHouse(Frame f, long h, int hw, int hd) {
        int x0 = -hw, x1 = hw, z0 = -hd, z1 = hd;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 4, f.pick(h, 1) < 0.6 ? 4 : 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 4);
        // The court, open to the sky, with a cistern and a column at each corner.
        int cw = Math.max(1, hw - 4), cd = Math.max(1, hd - 4);
        for (int x = -cw; x <= cw; x++) for (int z = -cd; z <= cd; z++) f.set(x, 0, z, x == 0 && z == 0 ? WATER : DOUBLE_SLAB, 8);
        for (int[] p : new int[][] {{-cw - 1, -cd - 1}, {cw + 1, -cd - 1}, {-cw - 1, cd + 1}, {cw + 1, cd + 1}}) Build.column(f, p[0], p[1], 1, 4);
        // Interior walls between the rooms round the court.
        for (int z = z0 + 1; z <= z1 - 1; z++) { { f.marble(-cw - 2, 1, z); f.marble(-cw - 2, 2, z); f.marble(-cw - 2, 3, z); f.marble(cw + 2, 1, z); f.marble(cw + 2, 2, z); f.marble(cw + 2, 3, z); } }
        for (int x = -cw - 2; x <= cw + 2; x++) { f.marble(x, 1, cd + 2); f.marble(x, 2, cd + 2); f.marble(x, 3, cd + 2); }
        for (int y = 1; y <= 2; y++) { f.set(-cw - 2, y, 0, AIR); f.set(cw + 2, y, 0, AIR); f.set(0, y, cd + 2, AIR); }
        // Front door and windows.
        Build.door(f, 0, 1, z0, 0, -1, WOOD_DOOR);
        f.set(0, 3, z0, QUARTZ, 1);
        Build.window(f, x0, 2, 0, WHITE); Build.window(f, x1, 2, 0, WHITE); Build.window(f, -3, 2, z1, LIGHT_BLUE); Build.window(f, 3, 2, z1, LIGHT_BLUE);
        // West wing: kitchen.
        int kx = x0 + 1;
        f.set(kx, 1, z0 + 1, FURNACE, f.facing(1, 0));
        f.set(kx, 1, z0 + 2, CAULDRON, 2);
        f.set(kx, 2, z0 + 1, BRICK_BLOCK);
        Build.table(f, kx + 1, 1, 0, false);
        f.chest(kx, 1, z1 - 1, 1, 0, "atlas:larder", null);
        f.set(kx, 1, z1 - 2, WORKBENCH);
        // East wing: the andron, couches round low tables.
        int ax = x1 - 1;
        for (int z = z0 + 1; z <= z1 - 2; z += 2) Build.seat(f, ax, 1, z, -1, 0, BIRCH_STAIRS);
        Build.table(f, ax - 1, 1, z0 + 2, false);
        f.set(ax, 1, z1 - 1, FLOWER_POT); f.pot(ax, 1, z1 - 1, "red_flower:" + (int) (f.pick(h, 2) * 9));
        // Back rooms: bedrooms and a study or loom room.
        f.bed(-2, 1, z1 - 1, 1, 0, f.pick(h, 3) < 0.5 ? BLUE : WHITE);
        f.bed(-cw - 1, 1, cd + 3, 0, 1, LIGHT_BLUE);
        if (f.pick(h, 4) < 0.5) {   // a study
            Build.table(f, 2, 1, z1 - 1, true);
            Build.seat(f, 2, 1, z1 - 2, 0, 1, BIRCH_STAIRS);
            Build.shelves(f, 4, z1 - 1, 1, 2);
            f.chest(3, 1, z1 - 1, 0, -1, "atlas:study", "book:any");
        } else {   // a loom room
            f.set(2, 1, z1 - 1, WOOL, WHITE); f.set(3, 1, z1 - 1, WOOL, LIGHT_BLUE); f.set(2, 2, z1 - 1, FENCE); f.set(3, 2, z1 - 1, FENCE);
            f.chest(4, 1, z1 - 1, -1, 0, "atlas:household", null);
        }
        // The household shrine and lamps.
        f.set(x0 + 1, 1, 0, QUARTZ, 1); f.set(x0 + 1, 2, 0, END_ROD, 1);
        Build.ceilingLamp(f, x0 + 2, 4, z0 + 2); Build.ceilingLamp(f, x1 - 2, 4, z1 - 2); Build.ceilingLamp(f, 0, 4, z1 - 1);
        // Roof: terracotta, open over the court.
        Build.flatRoof(f, x0, z0, x1, z1, 5, 0);
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) f.roofTile(x, 5, z);
        for (int x = -cw; x <= cw; x++) for (int z = -cd; z <= cd; z++) f.set(x, 5, z, AIR);
        // The household.
        f.npc(-1, 1, z0 + 2, 0, 1, "citizen:householder", null);
        if (f.pick(h, 5) < 0.7) f.npc(1, 1, -cd - 1, 0, 1, f.pick(h, 6) < 0.5 ? "citizen:child" : "citizen:elder", null);
    }

    /** A two-storey townhouse over a shop: the family above, the trade below, a stair between. */
    static void townhouse(Frame f, long h, String trade) {
        int x0 = -4, x1 = 4, z0 = -5, z1 = 5;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 8, 4);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 8);
        Build.floor(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 4, PLANKS, 2);
        // The shop front: an open counter under an awning.
        for (int x = -2; x <= 2; x++) { f.set(x, 1, z0, x == 0 ? AIR : SLAB, 8 | 7); f.set(x, 2, z0, AIR); f.set(x, 3, z0, AIR); f.set(x, 4, z0 - 1, WOOL, f.pick(h, 1) < 0.5 ? LIGHT_BLUE : WHITE); }
        shopFittings(f, h, trade, x0 + 1, z0 + 1, x1 - 1, 2);
        f.npc(0, 1, z0 + 2, 0, -1, "merchant:" + trade, null);
        // Stairs up along the east wall.
        for (int k = 0; k < 3; k++) { f.set(x1 - 1, 1 + k, 2 - k, BIRCH_STAIRS, f.stairs(0, -1, false)); f.set(x1 - 1, 4, 2 - k, AIR); }
        f.set(x1 - 1, 4, -1, AIR);
        // Upstairs: a bedroom and a sitting room.
        f.bed(x0 + 1, 5, z1 - 2, 0, 1, f.pick(h, 2) < 0.5 ? BLUE : CYAN);
        f.chest(x0 + 1, 5, z1 - 3, 1, 0, "atlas:household", null);
        Build.table(f, 0, 5, 0, false); Build.seat(f, 0, 5, 1, 0, -1, BIRCH_STAIRS);
        f.pot(x0 + 1, 5, z0 + 1, "red_flower:" + (int) (f.pick(h, 3) * 9));
        Build.window(f, x0, 6, 0, WHITE); Build.window(f, x1, 6, -2, WHITE); Build.window(f, 0, 6, z1, LIGHT_BLUE);
        Build.ceilingLamp(f, 0, 8, 2); Build.ceilingLamp(f, -2, 3, 2);
        Build.gable(f, x0, z0, x1, z1, 9, true, 0);
        if (f.pick(h, 4) < 0.6) f.npc(-1, 5, 2, 0, -1, "citizen:householder", null);
    }

    /** Fittings for a trade inside a shop area (local box, floor at y 0). */
    static void shopFittings(Frame f, long h, String trade, int x0, int z0, int x1, int z1) {
        switch (trade) {
            case "baker":
                f.set(x0, 1, z1, FURNACE, f.facing(0, -1)); f.set(x0 + 1, 1, z1, FURNACE, f.facing(0, -1)); f.set(x0, 2, z1, BRICK_BLOCK); f.set(x0 + 1, 2, z1, BRICK_BLOCK);
                f.set(x1, 1, z1, HAY); f.chest(x1, 1, z0, -1, 0, "atlas:bakery", null);
                break;
            case "smith":
                f.set(x0, 1, z1, FURNACE, f.facing(0, -1)); f.set(x0 + 1, 1, z1, ANVIL, 0); f.set(x1, 1, z1, CAULDRON, 3);
                f.chest(x1, 1, z0, -1, 0, "atlas:smithy", null); f.stand(x0, 1, z0, 1, 0, "hoplite_rack");
                break;
            case "scribe":
                Build.shelves(f, x0, z1, 1, 3); Build.shelves(f, x0 + 1, z1, 1, 3); Build.table(f, x1 - 1, 1, z1 - 1, true);
                f.chest(x1, 1, z0, -1, 0, "atlas:scriptorium", "book:any");
                break;
            case "apothecary":
                f.set(x0, 1, z1, BREWING, 0); f.set(x0 + 1, 1, z1, CAULDRON, 2); f.set(x1, 1, z1, BOOKSHELF);
                f.pot(x0, 1, z0, "red_flower:2"); f.chest(x1, 1, z0, -1, 0, "atlas:apothecary", null);
                break;
            case "lumenwright":
                f.set(x0, 1, z1, SEA_LANTERN); f.set(x0 + 1, 1, z1, PRISMARINE, 1); f.set(x1, 1, z1, GLOWSTONE); f.set(x0, 2, z1, END_ROD, 1);
                f.chest(x1, 1, z0, -1, 0, "atlas:lumen", null);
                break;
            case "potter":
                f.set(x0, 1, z1, HARD_CLAY); f.pot(x0 + 1, 1, z1, "empty"); f.pot(x1, 1, z1, "red_flower:4"); f.set(x0, 2, z1, FLOWER_POT);
                f.chest(x1, 1, z0, -1, 0, "atlas:household", null);
                break;
            case "weaver":
                for (int x = x0; x <= x1; x++) f.set(x, 1, z1, WOOL, (x * 3) & 15);
                f.set(x0, 2, z1, FENCE); f.set(x1, 2, z1, FENCE); f.chest(x1, 1, z0, -1, 0, "atlas:weaver", null);
                break;
            case "cartographer":
                Build.table(f, x0 + 1, 1, z1 - 1, true); Build.shelves(f, x0, z1, 1, 2);
                f.chest(x1, 1, z0, -1, 0, "atlas:maps", null);
                break;
            default:   // grocer
                for (int x = x0; x <= x1; x++) f.set(x, 1, z1, x % 2 == 0 ? HAY : MELON);
                f.set(x0, 2, z1, PUMPKIN, 0); f.chest(x1, 1, z0, -1, 0, "atlas:grocer", null);
        }
    }

    static final String[] TRADES = {"baker", "smith", "scribe", "apothecary", "lumenwright", "potter", "weaver", "grocer", "cartographer"};

    static String trade(long h) { return TRADES[(int) Math.floorMod(Hash.mix(h) >>> 3, TRADES.length)]; }

    // ------------------------------------------------------------------ civic and sacred

    /**
     * A temple on a stepped podium: a ring of columns round a walled cella with a statue, an altar and a lumen flame,
     * under a marble pediment roof. hw x hd are half-sizes of the podium (>= 6 x 8).
     */
    static void temple(Frame f, long h, int hw, int hd, String deity) {
        int top = 3;
        for (int s = 0; s < top; s++)
            for (int x = -hw + s; x <= hw - s; x++) for (int z = -hd + s; z <= hd - s; z++) { f.footing(x, z, s, true); f.ashlar(x, s, z); }
        int x0 = -hw + top, x1 = hw - top, z0 = -hd + top, z1 = hd - top;
        Build.pavedFloor(f, x0, z0, x1, z1, top - 1);
        f.air(x0, top, z0, x1, top + 9, z1);
        int colTop = top + 6;
        for (int x = x0; x <= x1; x += 2) { Build.column(f, x, z0, top, colTop); Build.column(f, x, z1, top, colTop); }
        for (int z = z0; z <= z1; z += 2) { Build.column(f, x0, z, top, colTop); Build.column(f, x1, z, top, colTop); }
        // Entablature and pediment.
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (x == x0 || x == x1 || z == z0 || z == z1) { f.marble(x, colTop + 1, z); }
        Build.floor(f, x0, z0, x1, z1, colTop + 2, DOUBLE_SLAB, 7);
        Build.gable(f, x0, z0, x1, z1, colTop + 3, true, 1);
        // The cella.
        int cx0 = x0 + 2, cx1 = x1 - 2, cz0 = z0 + 3, cz1 = z1 - 2;
        Build.walls(f, cx0, cz0, cx1, cz1, top, colTop, 0);
        Build.hollow(f, cx0 + 1, cz0 + 1, cx1 - 1, cz1 - 1, top, colTop);
        Build.mosaic(f, cx0 + 1, cz0 + 1, cx1 - 1, cz1 - 1, top - 1);
        for (int y = top; y <= top + 3; y++) for (int x = -1; x <= 1; x++) f.set(x, y, cz0, AIR);
        Build.statue(f, 0, top, cz1 - 1, 0, -1, 5);
        f.set(0, top, cz1 - 3, QUARTZ, 1);
        f.set(0, top + 1, cz1 - 3, SEA_LANTERN);
        f.set(0, top + 2, cz1 - 3, END_ROD, 1);
        for (int z = cz0 + 2; z <= cz1 - 4; z += 2) { Build.seat(f, cx0 + 1, top, z, 1, 0, QUARTZ_STAIRS); Build.seat(f, cx1 - 1, top, z, -1, 0, QUARTZ_STAIRS); }
        for (int[] p : new int[][] {{cx0 + 1, cz1 - 1}, {cx1 - 1, cz1 - 1}}) { f.set(p[0], top, p[1], QUARTZ, 2); f.set(p[0], top + 1, p[1], END_ROD, 1); }
        f.sign(0, top + 1, cz1 - 4, 0, -1, "TEMPLE OF\n" + deity.toUpperCase() + "\n~\n" + Texts.motto(h));
        f.chest(cx1 - 1, top, cz0 + 1, -1, 0, "atlas:temple", "book:hymns");
        f.npc(1, top, cz1 - 5, 0, -1, "citizen:priest", null);
        if (f.pick(h, 1) < 0.6) f.npc(-2, top, cz0 + 3, 0, 1, "citizen:worshipper", null);
    }

    /** A school: a classroom with desks and a lectern, a small library corner and an orrery model. */
    static void school(Frame f, long h) {
        int x0 = -7, x1 = 7, z0 = -6, z1 = 6;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 5, 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 5);
        Build.gable(f, x0, z0, x1, z1, 6, true, 0);
        Build.door(f, 0, 1, z0, 0, -1, WOOD_DOOR);
        for (int x = x0 + 2; x <= x1 - 2; x += 3) Build.window(f, x, 2, z1, WHITE);
        for (int z = z0 + 2; z <= z1 - 2; z += 3) { Build.window(f, x0, 2, z, WHITE); Build.window(f, x1, 2, z, WHITE); }
        // Desks in rows facing the teacher's lectern.
        for (int x = -4; x <= 4; x += 2)
            for (int z = -3; z <= 1; z += 2) { f.set(x, 1, z, SLAB, 8 | 2); Build.seat(f, x, 1, z - 1, 0, 1, BIRCH_STAIRS); }
        f.set(0, 1, 4, BOOKSHELF); f.set(0, 2, 4, SLAB, 7);
        for (int x = x0 + 1; x <= x0 + 3; x++) Build.shelves(f, x, z1 - 1, 1, 3);
        f.chest(x1 - 1, 1, z1 - 1, -1, 0, "atlas:school", "book:primer");
        f.set(x1 - 2, 1, z1 - 1, GLOWSTONE); f.set(x1 - 2, 2, z1 - 1, STAINED_GLASS, BLUE);
        Build.ceilingLamp(f, -3, 5, 0); Build.ceilingLamp(f, 3, 5, 0);
        f.npc(0, 1, 3, 0, -1, "citizen:teacher", null);
        f.npc(-2, 1, -2, 0, 1, "citizen:student", null);
        f.npc(2, 1, 0, 0, 1, "citizen:student", null);
        f.sign(0, 3, z0 - 1, 0, -1, "SCHOOL OF\nTHE DEME\nPAIDEIA FOR\nEVERY CHILD");
    }

    /** A bath house: a cold pool, a warm pool, a heated room over a furnace, benches and towels. */
    static void bath(Frame f, long h) {
        int x0 = -8, x1 = 8, z0 = -6, z1 = 6;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 5, 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 5);
        Build.flatRoof(f, x0, z0, x1, z1, 6, 0);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.set(x, 6, z, STAINED_GLASS, LIGHT_BLUE);
        Build.door(f, 0, 1, z0, 0, -1, WOOD_DOOR);
        for (int x = -6; x <= -1; x++) for (int z = -3; z <= 3; z++) { f.set(x, 0, z, WATER); f.set(x, -1, z, QUARTZ, 0); }
        for (int x = 2; x <= 6; x++) for (int z = -3; z <= 3; z++) { f.set(x, 0, z, WATER); f.set(x, -1, z, MAGMA); }
        for (int x = -6; x <= 6; x++) { Build.seat(f, x, 1, 5, 0, -1, QUARTZ_STAIRS); }
        f.set(x1 - 1, 1, z0 + 1, FURNACE, f.facing(-1, 0));
        f.chest(x0 + 1, 1, z0 + 1, 1, 0, "atlas:bath", null);
        for (int x = -6; x <= 6; x += 4) Build.ceilingLamp(f, x, 5, -5);
        f.npc(-4, 1, 5, 0, -1, "citizen:bather", null);
        f.npc(4, 1, -5, 0, 1, "citizen:bath_keeper", null);
    }

    /** A stoa: a long colonnaded hall with shops at the back, facing a street or agora. len is the half-length. */
    static void stoa(Frame f, long h, int len) {
        int x0 = -len, x1 = len, z0 = -4, z1 = 4;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.pavedFloor(f, x0, z0, x1, z1, 0);
        f.air(x0, 1, z0, x1, 7, z1);
        for (int x = x0; x <= x1; x += 3) Build.column(f, x, z0, 1, 5);
        Build.walls(f, x0, z1, x1, z1, 1, 5, 0);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) f.set(x, 6, z, DOUBLE_SLAB, 7);
        for (int x = x0; x <= x1; x++) { f.set(x, 7, z0, QUARTZ_STAIRS, f.stairs(0, 1, false)); f.set(x, 7, z1, QUARTZ_STAIRS, f.stairs(0, -1, false)); for (int z = z0 + 1; z < z1; z++) f.set(x, 7, z, SLAB, 7); }
        // Stalls along the back wall, each with a trader.
        int n = 0;
        for (int x = x0 + 2; x <= x1 - 2; x += 5, n++) {
            String trade = trade(Hash.of(h, n, 7));
            f.set(x - 1, 1, z1 - 2, SLAB, 8 | 7); f.set(x, 1, z1 - 2, SLAB, 8 | 7); f.set(x + 1, 1, z1 - 2, SLAB, 8 | 7);
            shopFittings(f, Hash.of(h, n, 8), trade, x - 1, z1 - 1, x + 1, z1 - 1);
            f.npc(x, 1, z1 - 1, 0, -1, "merchant:" + trade, null);
        }
        for (int x = x0 + 1; x <= x1 - 1; x += 6) Build.ceilingLamp(f, x, 5, 0);
    }

    /**
     * A library: a two-level reading hall lined with shelves (every shelf can be read), desks with lamps, a copy desk and
     * a librarian. hw x hd are half-sizes (>= 7 x 6). {@code collection} names the shelves' collection.
     */
    static void library(Frame f, long h, int hw, int hd, String collection) {
        int x0 = -hw, x1 = hw, z0 = -hd, z1 = hd, top = 9;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, top, 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, top);
        Build.mosaic(f, -2, -2, 2, 2, 0);
        // Shelves on every wall, two storeys, with a gallery.
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int y = 1; y <= top - 1; y++) { if (y == 5) continue; f.set(x, y, z1 - 1, BOOKSHELF); if (Math.abs(x) > 1) f.set(x, y, z0 + 1, BOOKSHELF); }
        for (int z = z0 + 1; z <= z1 - 1; z++) for (int y = 1; y <= top - 1; y++) { if (y == 5) continue; f.set(x0 + 1, y, z, BOOKSHELF); f.set(x1 - 1, y, z, BOOKSHELF); }
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z1 - 1; z++) {
            boolean edge = x <= x0 + 2 || x >= x1 - 2 || z <= z0 + 2 || z >= z1 - 2;
            if (edge) f.set(x, 5, z, SLAB, 8 | 7);
        }
        for (int x = x0 + 3; x <= x1 - 3; x++) { f.set(x, 6, z0 + 3, FENCE); f.set(x, 6, z1 - 3, FENCE); }
        for (int k = 0; k < 4; k++) f.set(x1 - 3, 1 + k, z1 - 3 - k, BIRCH_STAIRS, f.stairs(0, -1, false));
        // Doors and clerestory windows.
        for (int y = 1; y <= 3; y++) for (int x = -1; x <= 1; x++) f.set(x, y, z0, AIR);
        f.set(0, 4, z0, QUARTZ, 1);
        for (int x = x0 + 3; x <= x1 - 3; x += 3) f.set(x, top, z0, STAINED_PANE, LIGHT_BLUE);
        // Reading desks with lumen lamps and chairs.
        for (int x = -hw + 4; x <= hw - 4; x += 4)
            for (int z = -hd + 4; z <= hd - 4; z += 4) {
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) continue;
                f.set(x, 1, z, SLAB, 8 | 2); f.set(x + 1, 1, z, SLAB, 8 | 2);
                f.set(x, 2, z, END_ROD, 1);
                Build.seat(f, x, 1, z - 1, 0, 1, BIRCH_STAIRS);
                Build.seat(f, x + 1, 1, z + 1, 0, -1, BIRCH_STAIRS);
            }
        // The copy desk: a scribe copies any volume for a reader who asks.
        f.set(0, 1, 2, BOOKSHELF); f.set(1, 1, 2, SLAB, 8 | 7);
        f.chest(-1, 1, 2, 1, 0, "atlas:library_desk", "book:" + collection);
        f.sign(0, 2, 3, 0, -1, "READ ANY\nSHELF. ASK\nTHE SCRIBE\nFOR A COPY");
        Build.flatRoof(f, x0, z0, x1, z1, top + 1, 0);
        for (int x = x0 + 3; x <= x1 - 3; x += 4) for (int z = z0 + 3; z <= z1 - 3; z += 4) f.set(x, top, z, SEA_LANTERN);
        f.npc(0, 1, 3, 0, -1, "citizen:librarian", null);
        f.npc(-hw + 5, 1, 0, 1, 0, "citizen:reader", null);
        f.npc(hw - 5, 6, 0, -1, 0, "citizen:reader", null);
    }

    /** A smithy with an open front: forge, anvil, quenching trough, racks of blades, a smith. */
    static void smithy(Frame f, long h) {
        int x0 = -5, x1 = 5, z0 = -4, z1 = 4;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0 + 1, x1, z1, 1, 4, 1);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 4);
        for (int x = x0; x <= x1; x += 5) Build.column(f, x, z0, 1, 4);
        Build.gable(f, x0, z0, x1, z1, 5, false, 0);
        f.set(x0 + 1, 1, z1 - 1, FURNACE, f.facing(1, 0)); f.set(x0 + 2, 1, z1 - 1, FURNACE, f.facing(0, -1));
        f.set(x0 + 1, 2, z1 - 1, BRICK_BLOCK); f.set(x0 + 2, 2, z1 - 1, BRICK_BLOCK); f.set(x0 + 1, 3, z1 - 1, BRICK_BLOCK);
        f.set(0, 1, 1, ANVIL, 0); f.set(2, 1, 1, CAULDRON, 3);
        f.stand(x1 - 1, 1, z1 - 1, -1, 0, "hoplite_rack");
        f.chest(x1 - 1, 1, 0, -1, 0, "atlas:smithy", null);
        f.npc(0, 1, 0, 0, -1, "merchant:smith", null);
    }
}
