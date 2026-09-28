package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/** The Wound: three centuries of war between the Lampwall and the Dominion's edge. */
final class CellsWound {
    private CellsWound() {}

    static void draw(Plans plans, Frame f, Plans.Cell cell) {
        long h = cell.hash;
        switch (cell.kind) {
            case "craters": craters(f, h); break;
            case "dead_talos": deadTalos(f, h); break;
            case "trench": trench(f, h); break;
            case "burnt_farm": burntFarm(f, h); break;
            case "dead_orchard": for (int x = -9; x <= 9; x += 6) for (int z = -9; z <= 9; z += 6) Build.deadTree(f, x, z, true); scatter(f, h, 6); break;
            case "bones": scatter(f, h, 22); break;
            case "fallen_column": fallenColumn(f, h); break;
            case "ember_pit": emberPit(f, h); break;
            case "broken_wagon": brokenWagon(f, h); break;
            case "grave_field": graveField(f, h); break;
            default: craters(f, h);
        }
    }

    /** Bones, skulls, rusted blades (fences) and torn Concord banners over the ground. */
    static void scatter(Frame f, long h, int count) {
        for (int k = 0; k < count; k++) {
            int x = (int) (Hash.unit(Hash.of(h, k, 1)) * 22) - 11, z = (int) (Hash.unit(Hash.of(h, k, 2)) * 22) - 11;
            double r = Hash.unit(Hash.of(h, k, 3));
            if (r < 0.35) Build.onGround(f, x, z, 1, BONE, (int) (r * 12) % 3 == 0 ? 4 : 8);
            else if (r < 0.55) Build.onGround(f, x, z, 1, SKULL, 1);
            else if (r < 0.75) Build.onGround(f, x, z, 1, FENCE, 0);
            else if (r < 0.85) Build.onGround(f, x, z, 1, CARPET, BLACK);
            else Build.onGround(f, x, z, 1, DEADBUSH, 0);
        }
    }

    private static void craters(Frame f, long h) {
        for (int k = 0; k < 3; k++) {
            int cx = (int) (Hash.unit(Hash.of(h, k, 4)) * 14) - 7, cz = (int) (Hash.unit(Hash.of(h, k, 5)) * 14) - 7;
            int r = 2 + (int) (Hash.unit(Hash.of(h, k, 6)) * 3);
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                    if (d > r) continue;
                    int g = f.ground(x, z);
                    if (g < 0) continue;
                    int depth = (int) Math.round((r - d) * 0.8);
                    int wx = f.wx(x, z), wz = f.wz(x, z);
                    f.c.clear(wx, wz, g - depth + 1, g + 2);
                    f.c.set(wx, g - depth, wz, d < 1.2 ? POWDER : GRAVEL, BLACK);
                    if (d < 1 && f.roll(x, 0, z, 91) < 0.3) { f.c.set(wx, g - depth, wz, NETHERRACK); f.c.set(wx, g - depth + 1, wz, FIRE); }
                }
        }
        scatter(f, h, 5);
    }

    private static void deadTalos(Frame f, long h) {
        // A fallen Talos automaton, face down in the ash: legs, torso, arms and a cracked head of lumen glass.
        int g = f.base;
        for (int z = -6; z <= 6; z++) {
            int w = z < -2 ? 1 : z < 3 ? 2 : 1;
            for (int x = -w; x <= w; x++) {
                if (z < -2 && x == 0) continue;   // the gap between the legs
                f.set(x, 1, z, STONE, 6);
                if (z > -2 && z < 3) f.set(x, 2, z, IRON_TRAPDOOR, 8 | 3);
            }
        }
        for (int z = -1; z <= 2; z++) { f.set(-4, 1, z, STONE, 6); f.set(4, 1, z + 2, STONE, 6); }
        f.set(0, 1, 7, STAINED_GLASS, LIGHT_BLUE); f.set(0, 2, 7, STAINED_GLASS, BLACK); f.set(0, 1, 8, STONE, 6);
        f.sign(3, 1, -4, 0, -1, "TALOS NO. " + (20 + Hash.range(h, 0, 400)) + "\nOF THE LINE\nFELL HOLDING\nTHE GATE");
        scatter(f, h, 8);
    }

    private static void trench(Frame f, long h) {
        for (int x = -12; x <= 11; x++)
            for (int z = -2; z <= 2; z++) {
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                if (Math.abs(z) <= 1) { f.c.clear(wx, wz, g - 1, g + 2); f.c.set(wx, g - 2, wz, GRAVEL); }
                else { f.c.set(wx, g + 1, wz, SAND); if (Math.floorMod(x, 3) == 0) f.c.set(wx, g + 2, wz, FENCE); }
            }
        for (int x = -10; x <= 10; x += 5) Build.onGround(f, x, 0, -1, SKULL, 1);
        scatter(f, h, 8);
    }

    private static void burntFarm(Frame f, long h) {
        int x0 = -5, x1 = 5, z0 = -4, z1 = 4;
        f.footings(x0, z0, x1, z1, 0, true);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                f.set(x, 0, z, f.roll(x, 0, z, 92) < 0.5 ? DOUBLE_SLAB : GRAVEL, 8);
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                if (!wall) { f.clear(x, z, 1, 4); continue; }
                int hgt = 1 + (int) (f.roll(x, 1, z, 93) * 4);
                for (int y = 1; y <= hgt; y++) f.set(x, y, z, f.roll(x, y, z, 94) < 0.6 ? CONCRETE : LOG2, f.roll(x, y, z, 94) < 0.6 ? GRAY : 1);
            }
        f.set(-3, 1, -2, FURNACE, f.facing(1, 0));
        f.set(2, 1, 2, CAULDRON, 0);
        f.chest(3, 1, -2, -1, 0, "atlas:burnt_farm", null);
        f.sign(0, 1, z0 - 1, 0, -1, "THEY BURNED\nTHE BARLEY\nFIRST. WE\nNEVER CAME BACK");
        for (int x = -10; x <= 10; x += 4) Build.deadTree(f, x, 9, true);
    }

    private static void fallenColumn(Frame f, long h) {
        // A great column of the old Heliotheion road, fallen and broken in three.
        for (int k = -9; k <= 9; k++) {
            if (Math.abs(k) == 3 || Math.abs(k) == 7) continue;
            f.set(k, 1, 0, QUARTZ, f.quartzAxis(true));
            if (Math.abs(k) < 8) { f.set(k, 1, 1, QUARTZ, f.quartzAxis(true)); f.set(k, 2, 0, QUARTZ, f.quartzAxis(true)); }
        }
        f.set(-11, 1, 0, QUARTZ, 1); f.set(-11, 2, 0, QUARTZ, 1);
        scatter(f, h, 6);
    }

    private static void emberPit(Frame f, long h) {
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 3.4) continue;
                int g = f.ground(x, z);
                if (g < 0) continue;
                int wx = f.wx(x, z), wz = f.wz(x, z);
                f.c.clear(wx, wz, g, g + 2);
                f.c.set(wx, g - 1, wz, d < 2 ? MAGMA : NETHERRACK);
                if (d < 2.5 && f.roll(x, 0, z, 95) < 0.5) f.c.set(wx, g, wz, FIRE);
            }
        scatter(f, h, 10);
    }

    private static void brokenWagon(Frame f, long h) {
        f.fill(-2, 1, -1, 2, 1, 1, WOOD_SLAB, 2 | 8);
        f.set(-3, 1, -2, FENCE); f.set(3, 1, 2, FENCE);
        f.set(-2, 1, -2, LOG, f.axisX(false)); f.set(2, 1, 2, LOG, f.axisX(false));
        f.chest(0, 2, 0, 0, -1, "atlas:wagon", null);
        f.set(1, 2, 0, HAY);
        scatter(f, h, 10);
    }

    private static void graveField(Frame f, long h) {
        // The Line buries its dead where they fell: rows of white markers, some with names.
        for (int x = -9; x <= 9; x += 3)
            for (int z = -9; z <= 9; z += 4) {
                Build.onGround(f, x, z, 1, QUARTZ, 2);
                if (f.roll(x, 0, z, 96) < 0.35) {
                    int g = f.ground(x, z - 1);
                    if (g >= 0) f.c.sign(f.wx(x, z - 1), g + 1, f.wz(x, z - 1), f.facing(0, -1), Texts.grave(Hash.of(h, x, z)));
                }
            }
    }
}
