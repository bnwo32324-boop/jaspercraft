package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/** The Concord's cultivated countryside, one 24-block cell at a time (local -12..11 around the cell's centre). */
final class CellsConcord {
    private CellsConcord() {}

    static void draw(Plans plans, Frame f, Plans.Cell cell) {
        long h = cell.hash;
        switch (cell.kind) {
            case "wheat": fieldWith(f, h, CROPS, "farmer"); break;
            case "barley": fieldWith(f, h, BEETROOT, "farmer"); break;
            case "flax": fieldWith(f, h, POTATOES, "farmer"); break;
            case "olives": olives(f, h); break;
            case "vines": vines(f, h); break;
            case "orchard": orchard(f, h); break;
            case "meadow": meadow(f, h); break;
            case "lavender": lavender(f, h); break;
            case "pasture": pasture(f, h); break;
            case "garden": garden(f, h); break;
            case "shrine": case "wayshrine": shrine(f, h, cell.kind.equals("wayshrine")); break;
            case "tomb": tomb(f, h); break;
            case "statue": statuePlinth(f, h); break;
            case "well": well(f, h); break;
            case "threshing": threshing(f, h); break;
            case "windmill": windmill(f, h); break;
            case "lumen_well": lumenWell(f, h); break;
            case "aqueduct": aqueduct(f, h); break;
            case "pond": pond(f, h); break;
            case "farmhouse": farmhouse(f, h); break;
            case "potter": potter(f, h); break;
            case "beehives": beehives(f, h); break;
            case "cypress_walk": cypressWalk(f, h); break;
            case "herm_grove": hermGrove(f, h); break;
            case "fountain": fountain(f, h); break;
            case "stoa": wayStoa(f, h); break;
            case "orrery": orrery(f, h); break;
            case "milestone": milestone(plans, f, h, cell); break;
            case "drill_yard": drillYard(f, h); break;
            case "depot": depot(f, h); break;
            case "automaton_shed": automatonShed(f, h); break;
            case "signal_tower": signalTower(f, h); break;
            case "watch_camp": watchCamp(f, h); break;
            case "barracks": barracks(f, h); break;
            default: meadow(f, h);
        }
    }

    // ------------------------------------------------------------------ fields and groves

    private static void fieldWith(Frame f, long h, int crop, String worker) {
        boolean alongZ = f.pick(h, 1) < 0.5;
        Build.field(f, -11, -11, 10, 10, crop, alongZ);
        Build.fence(f, -12, -12, 11, 11, FENCE, 0, 0, -12);
        // A field shrine (a small herm) and a farmer at work.
        Build.onGround(f, -12, -12, 1, QUARTZ, 2);
        Build.onGround(f, -12, -12, 2, SKULL, 1);
        f.npc(3, 1 + dy(f, 3, 3), 3, 0, -1, "citizen:farmer", null);
        if (f.pick(h, 2) < 0.4) f.stand(-6, 1 + dy(f, -6, -6), -6, 0, -1, "scarecrow");
    }

    /** Height of the ground at a local column relative to the frame base (0 if outside the chunk). */
    static int dy(Frame f, int x, int z) { int g = f.ground(x, z); return g < 0 ? 0 : g - f.base; }

    private static void olives(Frame f, long h) {
        for (int x = -9; x <= 9; x += 6) for (int z = -9; z <= 9; z += 6) if (f.roll(x, 0, z, 81) < 0.9) Build.olive(f, x + (int) (f.roll(x, 1, z, 82) * 3) - 1, z);
        // An olive press under a lean-to.
        f.set(4, dy(f, 4, -3) + 1, -3, CAULDRON, 3);
        f.set(5, dy(f, 5, -3) + 1, -3, STONE, 5);
        f.set(5, dy(f, 5, -3) + 2, -3, SLAB, 0);
        f.npc(3, dy(f, 3, -1) + 1, -1, 1, 0, "citizen:farmer", null);
    }

    private static void vines(Frame f, long h) {
        for (int x = -10; x <= 10; x += 3)
            for (int z = -10; z <= 10; z++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                f.c.set(f.wx(x, z), g, f.wz(x, z), GRASS);
                f.c.set(f.wx(x, z), g + 1, f.wz(x, z), FENCE);
                if (f.roll(x, 2, z, 83) < 0.8) f.c.set(f.wx(x, z), g + 2, f.wz(x, z), LEAVES, 7);
            }
        // Baskets of the harvest and a treading vat.
        f.set(-11, dy(f, -11, 11) + 1, 11, HAY);
        f.set(-10, dy(f, -10, 11) + 1, 11, CAULDRON, 3);
        f.npc(-8, dy(f, -8, 9) + 1, 9, 0, -1, "citizen:farmer", null);
    }

    private static void orchard(Frame f, long h) {
        for (int x = -8; x <= 8; x += 8) for (int z = -8; z <= 8; z += 8) Build.fruitTree(f, x, z);
        Build.flowers(f, -11, -11, 10, 10, 0.08);
        f.set(2, dy(f, 2, 2) + 1, 2, LADDER, f.facing(0, 1));
        f.npc(1, dy(f, 1, 4) + 1, 4, 0, -1, "citizen:farmer", null);
    }

    private static void meadow(Frame f, long h) {
        Build.flowers(f, -11, -11, 10, 10, 0.22);
        if (f.pick(h, 1) < 0.5) Build.olive(f, 5, -4);
        if (f.pick(h, 2) < 0.5) f.npc(-2, dy(f, -2, 2) + 1, 2, 0, 1, "animal:sheep", null);
    }

    private static void lavender(Frame f, long h) {
        for (int x = -10; x <= 10; x += 2)
            for (int z = -10; z <= 10; z++) Build.onGround(f, x, z, 1, RED_FLOWER, 2);
        f.npc(0, dy(f, 0, 0) + 1, 0, 1, 0, "citizen:farmer", null);
    }

    private static void pasture(Frame f, long h) {
        Build.fence(f, -11, -11, 10, 10, FENCE, 0, 0, -11);
        // A shelter.
        f.fill(4, 0, 4, 9, 0, 8, DOUBLE_SLAB, 8);
        for (int[] p : new int[][] {{4, 4}, {9, 4}, {4, 8}, {9, 8}}) for (int y = 1; y <= 3; y++) f.set(p[0], y, p[1], FENCE);
        f.fill(3, 4, 3, 10, 4, 9, WOOD_SLAB, 2);
        f.fill(5, 1, 5, 8, 1, 7, HAY, 0);
        String animal = f.pick(h, 3) < 0.5 ? "animal:sheep" : "animal:cow";
        for (int k = 0; k < 4; k++) f.npc(-6 + 3 * k, dy(f, -6 + 3 * k, -3) + 1, -3, 0, 1, animal, null);
        f.npc(-8, dy(f, -8, 6) + 1, 6, 1, 0, "citizen:shepherd", null);
    }

    private static void garden(Frame f, long h) {
        Build.clearGround(f, -11, -11, 10, 10, 2);
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                boolean path = x == 0 || z == 0 || Math.abs(x) == 10 || Math.abs(z) == 10;
                int g = f.ground(x, z);
                if (g < 0) continue;
                f.c.set(f.wx(x, z), g, f.wz(x, z), path ? PATH : GRASS);
                if (!path && (Math.abs(x) == 1 || Math.abs(z) == 1)) f.c.set(f.wx(x, z), g + 1, f.wz(x, z), LEAVES, 4);   // box hedges
            }
        Build.flowers(f, 3, 3, 8, 8, 0.7);
        Build.flowers(f, -8, -8, -3, -3, 0.7);
        Build.flowers(f, 3, -8, 8, -3, 0.5);
        Build.flowers(f, -8, 3, -3, 8, 0.5);
        // A sundial at the crossing and benches.
        f.set(0, dy(f, 0, 0) + 1, 0, QUARTZ, 2);
        f.set(0, dy(f, 0, 0) + 2, 0, SLAB, 7);
        f.set(0, dy(f, 0, 0) + 3, 0, END_ROD, 1);
        Build.seat(f, 0, dy(f, 0, 5) + 1, 5, 0, -1, QUARTZ_STAIRS);
        Build.seat(f, 0, dy(f, 0, -5) + 1, -5, 0, 1, QUARTZ_STAIRS);
        f.npc(2, dy(f, 2, 0) + 1, 0, -1, 0, "citizen:gardener", null);
    }

    // ------------------------------------------------------------------ sacred and funerary

    private static void shrine(Frame f, long h, boolean wayside) {
        // A small round shrine: four columns, a slab dome, an altar with a lumen flame.
        f.footings(-4, -4, 4, 4, 0, true);
        Build.pavedFloor(f, -4, -4, 4, 4, 0);
        f.air(-4, 1, -4, 4, 7, 4);
        for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) Build.column(f, p[0], p[1], 1, 5);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (Math.abs(x) + Math.abs(z) <= 6) f.set(x, 6, z, DOUBLE_SLAB, 7);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) + Math.abs(z) <= 3) f.set(x, 7, z, SLAB, 7);
        f.set(0, 1, 1, QUARTZ, 1);
        f.set(0, 2, 1, SEA_LANTERN);
        f.set(0, 3, 1, END_ROD, 1);
        f.sign(0, 1, 0, 0, -1, Texts.shrine(h));
        if (f.pick(h, 2) < 0.7) f.npc(-2, 1, -1, 1, 0, "citizen:priest", null);
        if (!wayside) Build.flowers(f, -11, -11, 10, 10, 0.12);
        if (f.pick(h, 4) < 0.5) f.pot(2, 1, 2, "red_flower:" + (int) (f.pick(h, 5) * 9));
    }

    private static void tomb(Frame f, long h) {
        // A family tomb: a stepped plinth with a stele, cypresses behind, an offering bowl.
        f.footings(-3, -2, 3, 2, 0, true);
        for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) f.ashlar(x, 0, z);
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) f.set(x, 1, z, DOUBLE_SLAB, 8);
        for (int y = 2; y <= 5; y++) f.set(0, y, 0, QUARTZ, y == 5 ? 1 : 0);
        f.set(0, 6, 0, QUARTZ_STAIRS, f.stairs(0, 1, false));
        f.sign(0, 3, -1, 0, -1, Texts.epitaph(h));
        f.set(-1, 2, -1, FLOWER_POT);
        f.pot(1, 2, -1, "red_flower:" + (f.pick(h, 3) < 0.5 ? 0 : 8));
        Build.cypress(f, -5, 3);
        Build.cypress(f, 5, 3);
        if (f.pick(h, 6) < 0.3) f.npc(2, dy(f, 2, -4) + 1, -4, 0, 1, "citizen:mourner", null);
    }

    private static void statuePlinth(Frame f, long h) {
        f.footings(-2, -2, 2, 2, 1, true);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.ashlar(x, 1, z);
        Build.statue(f, 0, 2, 0, 0, -1, 4);
        f.sign(0, 1, -3, 0, -1, Texts.statue(h));
        Build.flowers(f, -11, -11, 10, 10, 0.1);
    }

    private static void hermGrove(Frame f, long h) {
        // A grove of herms (pillar busts) among olives: a place of philosophers' walks.
        for (int k = 0; k < 5; k++) {
            int x = -8 + k * 4, z = k % 2 == 0 ? -4 : 4;
            Build.onGround(f, x, z, 1, QUARTZ, 2);
            Build.onGround(f, x, z, 2, QUARTZ, 2);
            Build.onGround(f, x, z, 3, SKULL, 1);
        }
        Build.olive(f, -9, 8);
        Build.olive(f, 8, -8);
        f.npc(0, dy(f, 0, 0) + 1, 0, 1, 0, "citizen:philosopher", null);
        f.npc(2, dy(f, 2, 0) + 1, 0, -1, 0, "citizen:student", null);
    }

    private static void cypressWalk(Frame f, long h) {
        for (int z = -10; z <= 10; z += 4) { Build.cypress(f, -4, z); Build.cypress(f, 4, z); }
        for (int z = -11; z <= 10; z++) Build.onGround(f, 0, z, 0, PATH, 0);
        Build.onGround(f, 0, 0, 1, QUARTZ, 1);
        Build.onGround(f, 0, 0, 2, FLOWER_POT, 0);
    }

    // ------------------------------------------------------------------ works and dwellings

    private static void well(Frame f, long h) {
        f.footings(-2, -2, 2, 2, 0, true);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.ashlar(x, 0, z);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { f.set(x, 1, z, QUARTZ, 0); }
        f.set(0, 1, 0, WATER);
        for (int y = -4; y <= 0; y++) f.set(0, y, 0, WATER);
        for (int[] p : new int[][] {{-1, -1}, {1, 1}}) for (int y = 2; y <= 3; y++) f.set(p[0], y, p[1], FENCE);
        f.set(-1, 4, -1, WOOD_SLAB, 2); f.set(0, 4, 0, WOOD_SLAB, 2); f.set(1, 4, 1, WOOD_SLAB, 2);
        f.set(3, 1, 0, CAULDRON, 3);
        f.npc(4, 1, 1, -1, 0, "citizen:water_carrier", null);
        Build.flowers(f, -11, -11, 10, 10, 0.1);
    }

    private static void threshing(Frame f, long h) {
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 6.4) continue;
                f.footing(x, z, 0, true);
                f.set(x, 0, z, d > 5.5 ? QUARTZ : DOUBLE_SLAB, d > 5.5 ? 0 : 8);
                f.clear(x, z, 1, 3);
            }
        for (int[] p : new int[][] {{-8, -8}, {-9, -7}, {8, 7}, {7, 9}, {-8, 8}}) Build.onGround(f, p[0], p[1], 1, HAY, 0);
        f.npc(0, 1, 0, 0, -1, "animal:cow", null);
        f.npc(2, 1, 2, -1, 0, "citizen:farmer", null);
    }

    private static void windmill(Frame f, long h) {
        // A white round mill with four canvas sails on its east face.
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 3.4) continue;
                f.footing(x, z, 0, true);
                for (int y = 0; y <= 9; y++) {
                    if (d > 2.4) { if (y == 1 && x == 0 && z == -3) { Build.door(f, 0, 1, -3, 0, -1, WOOD_DOOR); y++; continue; } f.set(x, y, z, CONCRETE, WHITE); }
                    else f.set(x, y, z, y == 0 || y == 5 ? PLANKS : AIR, 0);
                }
                if (d <= 3.4) f.roofTile(x, 10, z);
                if (d <= 2) f.roofTile(x, 11, z);
            }
        f.set(0, 12, 0, SLAB, 4);
        // The sails: a hub and four spars of fence with wool canvas.
        f.set(0, 7, 4, LOG, f.axisX(false));
        for (int k = 1; k <= 5; k++) {
            f.set(0, 7 + k, 4, FENCE); f.set(0, 7 - k, 4, FENCE); f.set(k, 7, 4, FENCE); f.set(-k, 7, 4, FENCE);
            if (k > 1) { f.set(1, 7 + k, 4, WOOL, WHITE); f.set(-1, 7 - k, 4, WOOL, WHITE); f.set(k, 6, 4, WOOL, WHITE); f.set(-k, 8, 4, WOOL, WHITE); }
        }
        f.set(1, 1, 1, CHEST, f.facing(-1, 0));
        f.chest(1, 1, 1, -1, 0, "atlas:granary", null);
        f.set(-1, 1, 1, HAY);
        f.npc(0, 1, 0, 0, -1, "citizen:miller", null);
        Build.field(f, -11, 6, 10, 10, CROPS, false);
    }

    private static void lumenWell(Frame f, long h) {
        // Lumen works: a prismarine conduit ring round a sea-lantern core that feeds the lamps of the district.
        f.footings(-4, -4, 4, 4, 0, true);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                f.set(x, 0, z, d > 3.4 ? QUARTZ : PRISMARINE, d > 3.4 ? 0 : 1);
                f.clear(x, z, 1, 6);
            }
        for (int y = 1; y <= 3; y++) f.set(0, y, 0, y == 2 ? SEA_LANTERN : PRISMARINE, y == 2 ? 0 : 2);
        for (int[] p : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) { f.set(p[0], 1, p[1], PRISMARINE, 2); f.set(p[0], 2, p[1], END_ROD, 1); }
        f.set(0, 4, 0, END_ROD, 1);
        f.sign(4, 1, 0, 1, 0, "LUMEN WELL\nOF THE DEME\nDO NOT BREAK\nTHE CONDUIT");
        f.npc(-3, 1, -2, 1, 0, "citizen:lumenwright", null);
    }

    private static void aqueduct(Frame f, long h) {
        // A span of the aqueduct: arches carrying a covered water channel across the cell.
        int top = 9;
        for (int z = -12; z <= 11; z++) {
            boolean pier = Math.floorMod(z, 6) == 0;
            for (int x = -1; x <= 1; x++) {
                if (pier) { f.footing(x, z, 0, true); for (int y = 1; y < top; y++) f.ashlar(x, y, z); }
                else { f.set(x, top - 1, z, QUARTZ_STAIRS, 4 | 0); for (int y = top - 2; y <= top - 2; y++) f.ashlar(x, y + 1, z); }
                f.ashlar(x, top, z);
            }
            f.set(-1, top + 1, z, QUARTZ, 0);
            f.set(0, top + 1, z, WATER);
            f.set(1, top + 1, z, QUARTZ, 0);
            if (Math.floorMod(z, 3) == 0) f.set(0, top + 2, z, SLAB, 7);
        }
    }

    private static void pond(Frame f, long h) {
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x * 1.0 + z * z * 1.4);
                if (d > 8) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                int depth = d < 4 ? 2 : 1;
                for (int y = g - depth + 1; y <= g; y++) f.c.set(wx, y, wz, WATER);
                f.c.set(wx, g - depth, wz, d < 6 ? CLAY_BLOCK : SAND);
                f.c.clear(wx, wz, g + 1, g + 2);
                if (d > 6.5 && f.roll(x, 0, z, 84) < 0.5) f.c.set(wx, g + 1, wz, REEDS);
                if (d < 6 && f.roll(x, 0, z, 85) < 0.08) f.c.set(wx, g + 1, wz, LILY);
            }
        // A small pavilion on the shore.
        for (int[] p : new int[][] {{8, 8}, {11, 8}, {8, 11}, {11, 11}}) for (int y = 1; y <= 3; y++) Build.onGround(f, p[0], p[1], y, QUARTZ, 2);
        for (int x = 7; x <= 11; x++) for (int z = 7; z <= 11; z++) Build.onGround(f, x, z, 4, SLAB, 7);
        f.npc(9, dy(f, 9, 9) + 1, 9, -1, -1, "citizen:poet", null);
    }

    private static void farmhouse(Frame f, long h) {
        // A courtyard farmhouse: rooms round a small court, furnished and lived in.
        int x0 = -6, x1 = 6, z0 = -5, z1 = 5;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 4, 4);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 6);
        Build.walls(f, -2, -1, 2, 2, 1, 4, 4);   // the court's inner wall
        Build.hollow(f, -1, 0, 1, 1, 1, 6);
        f.set(0, 0, 0, WATER); f.set(0, 1, 1, FLOWER_POT);
        Build.door(f, 0, 1, z0, 0, -1, WOOD_DOOR);
        Build.door(f, 0, 1, -1, 0, -1, WOOD_DOOR);
        Build.window(f, x0, 2, -2, LIGHT_BLUE); Build.window(f, x1, 2, 2, LIGHT_BLUE); Build.window(f, 3, 2, z1, WHITE);
        // Kitchen (west): furnace, cauldron, table.
        f.set(x0 + 1, 1, z0 + 1, FURNACE, f.facing(1, 0)); f.set(x0 + 1, 1, z0 + 2, CAULDRON, 2);
        Build.table(f, x0 + 3, 1, z0 + 2, false); Build.seat(f, x0 + 3, 1, z0 + 3, 0, -1, OAK_STAIRS);
        f.chest(x0 + 1, 1, z0 + 3, 1, 0, "atlas:larder", null);
        // Bedroom (east): two beds and a chest.
        f.bed(x1 - 1, 1, z1 - 3, 0, 1, BLUE);
        f.bed(x1 - 3, 1, z1 - 3, 0, 1, WHITE);
        f.chest(x1 - 1, 1, z0 + 1, -1, 0, "atlas:household", null);
        f.set(x1 - 2, 1, z0 + 1, WORKBENCH);
        // Lights and a household shrine.
        Build.ceilingLamp(f, -4, 5, 0); Build.ceilingLamp(f, 4, 5, 0);
        f.set(x0 + 1, 1, z1 - 1, QUARTZ, 1); f.set(x0 + 1, 2, z1 - 1, END_ROD, 1);
        Build.flatRoof(f, x0, z0, x1, z1, 5, 0);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (Math.abs(x) <= 1 && z >= 0 && z <= 1) f.set(x, 5, z, AIR);
        f.npc(-3, 1, 3, 1, 0, "citizen:farmer", null);
        f.npc(3, 1, -3, 0, 1, "citizen:weaver", null);
        Build.field(f, -11, 7, 10, 10, CARROTS, false);
    }

    private static void potter(Frame f, long h) {
        // A potter's yard: a clay kiln, a wheel, drying racks of pots.
        f.footings(-5, -5, 5, 5, 0, true);
        Build.pavedFloor(f, -5, -5, 5, 5, 0);
        f.air(-5, 1, -5, 5, 5, 5);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 2.4) continue;
            for (int y = 1; y <= 3; y++) f.set(x, y, z, d > 1.4 || y == 3 ? HARD_CLAY : AIR, 0);
        }
        f.set(0, 1, 0, FURNACE, f.facing(0, -1)); f.set(0, 1, -2, AIR); f.set(0, 2, -2, AIR);
        f.set(0, 4, 0, BRICK_BLOCK); f.set(0, 5, 0, BRICK_BLOCK);
        for (int x = -4; x <= 4; x += 2) { f.set(x, 1, 4, SLAB, 8); f.pot(x, 2, 4, x % 4 == 0 ? "red_flower:" + Math.abs(x) : "empty"); }
        f.set(3, 1, -3, NOTE); f.set(3, 2, -3, SLAB, 8);   // the wheel
        f.npc(3, 1, -2, 0, -1, "citizen:potter", null);
    }

    private static void beehives(Frame f, long h) {
        Build.flowers(f, -11, -11, 10, 10, 0.4);
        for (int k = 0; k < 6; k++) {
            int x = -7 + (k % 3) * 7, z = k < 3 ? -4 : 4;
            Build.onGround(f, x, z, 1, HAY, 0);
            Build.onGround(f, x, z, 2, SLAB, 0);
        }
        f.npc(0, dy(f, 0, 0) + 1, 0, 0, -1, "citizen:beekeeper", null);
    }

    private static void fountain(Frame f, long h) {
        f.footings(-4, -4, 4, 4, 0, true);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 4.4) continue;
                f.pave(x, 0, z);
                f.clear(x, z, 1, 6);
                if (d > 3.4) f.set(x, 1, z, QUARTZ, 0);
                else if (d > 1.5) f.set(x, 1, z, WATER);
            }
        for (int y = 1; y <= 3; y++) f.set(0, y, 0, QUARTZ, 2);
        f.set(0, 4, 0, WATER);
        Build.seat(f, 6, 1 + dy(f, 6, 0), 0, -1, 0, QUARTZ_STAIRS);
        Build.seat(f, -6, 1 + dy(f, -6, 0), 0, 1, 0, QUARTZ_STAIRS);
        f.npc(5, 1 + dy(f, 5, 2), 2, -1, 0, "citizen:traveller", null);
    }

    private static void wayStoa(Frame f, long h) {
        // A roadside stoa: a colonnade with benches, a water jar and a stranger's cup, open to any traveller (xenia).
        f.footings(-9, -3, 9, 3, 0, true);
        Build.pavedFloor(f, -9, -3, 9, 3, 0);
        f.air(-9, 1, -3, 9, 6, 3);
        for (int x = -8; x <= 8; x += 4) Build.column(f, x, -3, 1, 4);
        Build.walls(f, -9, 3, 9, 3, 1, 4, 4);
        for (int x = -8; x <= 8; x += 2) Build.seat(f, x, 1, 2, 0, -1, QUARTZ_STAIRS);
        for (int x = -9; x <= 9; x++) for (int z = -3; z <= 3; z++) f.set(x, 5, z, DOUBLE_SLAB, 7);
        for (int x = -9; x <= 9; x++) f.set(x, 6, -3, QUARTZ_STAIRS, f.stairs(0, 1, false));
        f.set(0, 1, 1, CAULDRON, 3);
        f.chest(1, 1, 1, 0, -1, "atlas:wayhouse", null);
        f.sign(0, 3, 2, 0, -1, "STRANGER,\nDRINK AND\nREST. THIS\nIS XENIA.");
        f.npc(-4, 1, 0, 0, -1, "citizen:traveller", null);
    }

    private static void orrery(Frame f, long h) {
        // An open-air orrery: rings of quartz round a lumen sun, spheres of glass: the Lyceum teaches the Vault here.
        f.footings(-7, -7, 7, 7, 0, true);
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) if (x * x + z * z <= 52) { f.pave(x, 0, z); f.clear(x, z, 1, 8); }
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            int d2 = x * x + z * z;
            if (d2 >= 30 && d2 <= 38) f.set(x, 1, z, QUARTZ, 0);
            if (d2 >= 12 && d2 <= 17) f.set(x, 2, z, SLAB, 7);
        }
        f.set(0, 1, 0, QUARTZ, 2); f.set(0, 2, 0, QUARTZ, 2); f.set(0, 3, 0, GLOWSTONE);
        f.set(4, 3, 0, STAINED_GLASS, BLUE); f.set(-3, 4, 3, STAINED_GLASS, RED); f.set(0, 3, -5, STAINED_GLASS, WHITE);
        f.sign(0, 1, -7, 0, -1, "THE LYCEUM'S\nORRERY\nTHE VAULT\nIN LITTLE");
        f.npc(2, 1, -2, -1, 1, "citizen:astronomer", null);
        f.npc(-2, 1, -2, 1, 1, "citizen:student", null);
    }

    private static void milestone(Plans plans, Frame f, long h, Plans.Cell cell) {
        // A milestone naming the distance to Astreion, a lamp, a seat and a cypress.
        int x = 0, z = 0;
        int stadia = (int) Math.round(Math.hypot(cell.x - Realm.Place.ASTREION.x, cell.z - Realm.Place.ASTREION.z) / 8.0);
        Build.onGround(f, x, z, 1, QUARTZ, 2);
        Build.onGround(f, x, z, 2, QUARTZ, 1);
        f.post(x, dy(f, x, z - 1) + 1, z - 1, 0, -1, "ASTREION\n" + stadia + " STADIA\n~\n" + Texts.motto(h));
        Build.cypress(f, 4, 4);
        Build.flowers(f, -11, -11, 10, 10, 0.15);
        if (f.pick(h, 7) < 0.5) Build.olive(f, -6, 5);
    }

    // ------------------------------------------------------------------ the frontier behind the Line

    private static void drillYard(Frame f, long h) {
        Build.clearGround(f, -10, -10, 9, 9, 3);
        for (int x = -10; x <= 9; x++) for (int z = -10; z <= 9; z++) Build.onGround(f, x, z, 0, SAND, 0);
        for (int k = 0; k < 4; k++) f.stand(-6 + k * 4, dy(f, -6 + k * 4, 5) + 1, 5, 0, -1, "target");
        f.npc(0, dy(f, 0, -2) + 1, -2, 0, 1, "citizen:hoplite", null);
        f.npc(-4, dy(f, -4, -3) + 1, -3, 0, 1, "talos", null);
        f.sign(0, dy(f, 0, -9) + 1, -9, 0, -1, "DRILL YARD\nOF THE LINE\nFOR THE LAMP\nAND THE LIVING");
    }

    private static void depot(Frame f, long h) {
        int x0 = -7, x1 = 7, z0 = -4, z1 = 4;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 4, 1);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 4);
        Build.gable(f, x0, z0, x1, z1, 5, false, 0);
        f.set(0, 1, z0, AIR); f.set(0, 2, z0, AIR);
        for (int x = x0 + 1; x <= x1 - 1; x += 2) { f.set(x, 1, z1 - 1, HAY); f.set(x, 2, z1 - 1, x % 4 == 0 ? HAY : AIR); }
        f.chest(x0 + 1, 1, z0 + 1, 1, 0, "atlas:depot", null);
        f.chest(x1 - 1, 1, z0 + 1, -1, 0, "atlas:depot", null);
        Build.ceilingLamp(f, 0, 4, 0);
        f.npc(0, 1, 0, 0, -1, "citizen:quartermaster", null);
    }

    private static void automatonShed(Frame f, long h) {
        // Where Talos automata are mended: a long open hall, a frame with a half-built automaton, tools, lumen cells.
        int x0 = -8, x1 = 8, z0 = -5, z1 = 5;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.pavedFloor(f, x0, z0, x1, z1, 0);
        f.air(x0, 1, z0, x1, 7, z1);
        for (int x = x0; x <= x1; x += 4) { Build.column(f, x, z0, 1, 6); Build.column(f, x, z1, 1, 6); }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) f.set(x, 7, z, DOUBLE_SLAB, 8);
        // The half-built automaton: an iron-grey body of andesite and bars in a frame.
        for (int y = 1; y <= 3; y++) f.set(0, y, 0, STONE, 6);
        f.set(1, 3, 0, STONE, 6); f.set(-1, 3, 0, STONE, 6); f.set(0, 4, 0, PUMPKIN, 0);
        for (int y = 1; y <= 5; y++) { f.set(2, y, -1, FENCE); f.set(-2, y, -1, FENCE); }
        f.set(4, 1, 2, ANVIL, 0); f.set(5, 1, 2, WORKBENCH); f.set(-5, 1, 2, SEA_LANTERN); f.set(-6, 1, 2, SEA_LANTERN);
        f.chest(-4, 1, 3, 0, -1, "atlas:workshop", null);
        f.npc(3, 1, 0, -1, 0, "citizen:mechanic", null);
        f.npc(-3, 1, -3, 0, 1, "talos", null);
    }

    private static void signalTower(Frame f, long h) {
        // A heliograph tower: a slim marble shaft with a mirror head that flashes news along the Line.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            f.footing(x, z, 0, true);
            boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2;
            for (int y = 0; y <= 16; y++) {
                if (shell) { if (y == 1 && x == 0 && z == -2) { f.set(x, y, z, AIR); f.set(x, y + 1, z, AIR); y++; continue; } f.marble(x, y, z); }
                else f.set(x, y, z, y % 8 == 0 ? DOUBLE_SLAB : AIR, 8);
            }
        }
        for (int y = 1; y <= 16; y++) f.set(0, y, 1, LADDER, f.facing(0, -1));
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) f.set(x, 17, z, DOUBLE_SLAB, 7);
        f.set(0, 18, 0, STAINED_GLASS, WHITE); f.set(0, 19, 0, SEA_LANTERN); f.set(1, 18, 0, STAINED_PANE, LIGHT_BLUE); f.set(-1, 18, 0, STAINED_PANE, LIGHT_BLUE);
        f.npc(1, 1, -1, 0, -1, "citizen:signaller", null);
    }

    private static void watchCamp(Frame f, long h) {
        // A camp of the Line's watch: white tents, a lumen fire, weapon stands.
        for (int k = 0; k < 3; k++) tent(f, -8 + k * 7, -4, WHITE);
        f.set(0, dy(f, 0, 4) + 1, 4, SEA_LANTERN);
        for (int[] p : new int[][] {{-2, 4}, {2, 4}, {0, 6}}) Build.seat(f, p[0], dy(f, p[0], p[1]) + 1, p[1], 0, 1, SPRUCE_STAIRS);
        f.stand(6, dy(f, 6, 6) + 1, 6, 0, -1, "hoplite_rack");
        f.npc(1, dy(f, 1, 3) + 1, 3, 0, 1, "citizen:hoplite", null);
    }

    static void tent(Frame f, int x, int z, int color) {
        int g = dy(f, x, z);
        for (int a = -2; a <= 2; a++)
            for (int b = -2; b <= 2; b++) {
                int y = 3 - Math.abs(a);
                if (y < 1) continue;
                f.set(x + a, g + y, z + b, WOOL, color);
                for (int yy = g + 1; yy < g + y; yy++) if (Math.abs(a) < 2 && b > -2) f.set(x + a, yy, z + b, AIR);
            }
        f.set(x, g + 1, z + 1, WOOL, color == WHITE ? LIGHT_BLUE : RED);
    }

    private static void barracks(Frame f, long h) {
        int x0 = -8, x1 = 8, z0 = -4, z1 = 4;
        f.footings(x0, z0, x1, z1, 0, true);
        Build.floor(f, x0, z0, x1, z1, 0, DOUBLE_SLAB, 8);
        Build.walls(f, x0, z0, x1, z1, 1, 4, 0);
        Build.hollow(f, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 4);
        Build.gable(f, x0, z0, x1, z1, 5, false, 0);
        Build.door(f, 0, 1, z0, 0, -1, WOOD_DOOR);
        for (int x = x0 + 2; x <= x1 - 2; x += 3) { f.bed(x, 1, z1 - 2, 0, 1, BLUE); f.chest(x + 1, 1, z1 - 1, 0, -1, "atlas:soldier", null); }
        for (int x = x0 + 2; x <= x1 - 2; x += 4) Build.window(f, x, 2, z0, LIGHT_BLUE);
        Build.ceilingLamp(f, -4, 4, 0); Build.ceilingLamp(f, 4, 4, 0);
        f.stand(x0 + 1, 1, 0, 1, 0, "hoplite_rack");
        f.npc(0, 1, 0, 0, 1, "citizen:hoplite", null);
    }
}
