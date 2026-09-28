package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The last layer before the fixed places: it looks at the finished chunk in 8x8 patches (four to a chunk, so a patch
 * never crosses a chunk edge) and gives every patch that is still bare, level ground a small scene of its own. In the
 * Concord: cottages, groves, gardens, pens, wells, carts, shrines, pools and benches under lumen lamps; near the Line,
 * tents, racks and supply stacks; in the Wound, craters, bones, broken automata and graves; in the Dominion, what each
 * province is made of (tents, stakes and watch fires in the Marches, stone trees and cages in the Weald, slag, ember
 * pits and chains in the Forges, rubble and toppled statues in the Fallen Cities, spikes and shards on the Plateau).
 * Liberated land gets what its returning people make: gardens, saplings, flowers on old stakes, clean water.
 *
 * <p>It reads the blocks already drawn, so it runs whenever the chunk is drawn in full (generation, the populator's
 * tile pass and healing all draw the whole chunk the same way). Fixed places and sites are left alone.
 */
final class Infill {
    private Infill() {}

    static final int P = 8;

    private static boolean natural(int id) {
        return id == GRASS || id == DIRT || id == GRAVEL || id == POWDER || id == SOUL_SAND || id == CLAY || id == STONE || id == MOSSY
            || id == SAND || id == CONCRETE || id == COBBLE || id == NETHERRACK || id == OBSIDIAN || id == MAGMA;
    }

    private static boolean open(int id) {
        return id == AIR || id == TALLGRASS || id == RED_FLOWER || id == YELLOW_FLOWER || id == DOUBLE_PLANT || id == DEADBUSH || id == CARPET || id == SNOW_LAYER;
    }

    static void draw(Plans plans, Canvas c) {
        if (c.data == null) return;   // needs the drawn chunk
        for (int qx = 0; qx < 16; qx += P)
            for (int qz = 0; qz < 16; qz += P) {
                int x0 = c.x0 + qx, z0 = c.z0 + qz;
                if (!patch(plans, c, x0, z0, P))
                    for (int sx = 0; sx < P; sx += P / 2) for (int sz = 0; sz < P; sz += P / 2) patch(plans, c, x0 + sx, z0 + sz, P / 2);
            }
    }

    /** One patch (8 or 4 blocks square): a scene if it is bare, level ground. True if something was drawn. */
    private static boolean patch(Plans plans, Canvas c, int x0, int z0, int size) {
        int cx = x0 + size / 2, cz = z0 + size / 2;
        Realm.Zone zone = Realm.zone(cx, cz);
        if (zone == Realm.Zone.RIM || zone == Realm.Zone.LINE || zone == Realm.Zone.TEETH) return false;
        if (reserved(plans, cx, cz, size)) return false;
        int base = free(c, x0, z0, size);
        if (base < 0) return false;
        long h = Hash.of(c.seed ^ 0x1F111L ^ size, cx, 0, cz);
        if (Hash.unit(h) < (size == P ? 0.06 : 0.15)) return false;   // a little breathing room
        Frame f = new Frame(c, cx, cz, base, (int) (Hash.mix(h ^ 3) & 3));
        boolean freed = zone.province != null && c.liberated(zone.province);
        try {
            if (size == P) scene(f, h, zone, freed, cx, cz); else detail(f, h, zone, freed, cx, cz);
        } catch (RuntimeException e) {
            throw new IllegalStateException("infill at " + cx + "," + cz, e);
        }
        return true;
    }

    /** Inside the fixed places (their streets and courts are designed), the centre of sites, roads and the Teeth. */
    private static boolean reserved(Plans plans, int x, int z, int size) {
        for (Realm.Place p : Realm.Place.values()) {
            int r = p == Realm.Place.ANTHRAKION ? Anthrakion.WALL_R + 4 : p.radius;
            if (Math.abs(x - p.x) <= r + 2 && Math.abs(z - p.z) <= r + 2) return true;
        }
        Plans.Site s = plans.siteAt(x, z, 0);
        if (s != null && Math.abs(x - s.x) <= 10 && Math.abs(z - s.z) <= 10) return true;
        if (plans.roadDistance(x, z) < (size == P ? 5 : 2.5)) return true;
        double tr = Realm.teethDistance(x, z);
        return tr > Realm.TEETH_R - 5 && tr < Realm.TEETH_R + Realm.TEETH_W + 5;
    }

    /** The ground level of a bare, nearly flat patch (nothing built on it, nothing fixed), or -1. */
    private static int free(Canvas c, int x0, int z0, int size) {
        int lo = 999, hi = -1, clear = size == P ? 4 : 3;
        for (int x = x0; x < x0 + size; x++)
            for (int z = z0; z < z0 + size; z++) {
                int g = c.ground(x, z);
                if (g < 2 || g > 240) return -1;
                if (!natural(c.get(x, g, z))) return -1;
                for (int y = g + 1; y <= g + clear; y++) if (!open(c.get(x, y, z))) return -1;
                if (c.fixedColumn(x, z)) return -1;
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
            }
        return hi - lo > (size == P ? 3 : 2) ? -1 : lo;
    }

    private static void scene(Frame f, long h, Realm.Zone zone, boolean freed, int x, int z) {
        double r = f.pick(h, 11);
        switch (zone) {
            case CONCORD:
                if (x > Realm.lineX(z) - 150 && r < 0.45) { frontier(f, h); return; }
                concord(f, h);
                return;
            case WOUND: wound(f, h); return;
            case MARCHES: case GATE_ROAD: if (freed) healing(f, h, false); else marches(f, h); return;
            case WEALD: if (freed) healing(f, h, true); else weald(f, h); return;
            case FORGES: if (freed) healing(f, h, false); else forges(f, h); return;
            case FALLEN: if (freed) fallenFreed(f, h); else fallen(f, h); return;
            case PLATEAU: if (freed) healing(f, h, false); else plateau(f, h); return;
            default:
        }
    }

    // ------------------------------------------------------------------ helpers (local -3..2 round the patch centre)

    private static void on(Frame f, int x, int z, int dy, int id, int meta) { Build.onGround(f, x, z, dy, id, meta); }
    private static void on(Frame f, int x, int z, int dy, int id) { Build.onGround(f, x, z, dy, id, 0); }
    private static int g(Frame f, int x, int z) { return CellsConcord.dy(f, x, z); }
    private static boolean chance(Frame f, long h, int salt, double p) { return f.pick(h, salt) < p; }

    /** Solid footing from each column's ground up to the base (a small building on slightly uneven land). */
    private static void footing(Frame f, int x0, int z0, int x1, int z1, int id, int meta) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = g(f, x, z); y <= 0; y++) f.set(x, y, z, id, meta);
    }

    // ------------------------------------------------------------------ the Concord

    private static void concord(Frame f, long h) {
        switch ((int) (f.pick(h, 12) * 16)) {
            case 0: case 1: cottage(f, h); break;
            case 2: grove(f, h); break;
            case 3: garden(f, h); break;
            case 4: hayCart(f, h); break;
            case 5: sheepPen(f, h); break;
            case 6: well(f, h); break;
            case 7: vegetablePatch(f, h); break;
            case 8: lampBench(f, h); break;
            case 9: shrine(f, h); break;
            case 10: pool(f, h); break;
            case 11: pergola(f, h); break;
            case 12: woodpile(f, h); break;
            case 13: brokenColumns(f, h); break;
            case 14: beeYard(f, h); break;
            default: grove(f, h);
        }
    }

    private static void cottage(Frame f, long h) {
        int wall = chance(f, h, 20, 0.5) ? 0 : 1;   // 0 whitewashed, 1 ashlar
        footing(f, -3, -3, 2, 2, COBBLE, 0);
        for (int x = -3; x <= 2; x++)
            for (int z = -3; z <= 2; z++) {
                boolean edge = x == -3 || x == 2 || z == -3 || z == 2;
                f.set(x, 0, z, edge ? COBBLE : PLANKS, 0);
                for (int y = 1; y <= 3; y++) {
                    if (!edge) { f.set(x, y, z, AIR); continue; }
                    boolean corner = (x == -3 || x == 2) && (z == -3 || z == 2);
                    if (corner) f.set(x, y, z, LOG, 0);
                    else if (wall == 0) f.set(x, y, z, CONCRETE, WHITE); else f.ashlar(x, y, z);
                }
            }
        for (int x = -4; x <= 3; x++) for (int z = -4; z <= 3; z++) { f.roofTile(x, 4, z); if (x > -4 && x < 3 && z > -4 && z < 3) f.roofTile(x, 5, z); }
        f.set(0, 6, 0, BRICK_BLOCK); f.set(0, 7, 0, BRICK_BLOCK);
        f.set(-1, 1, 2, AIR); f.set(-1, 2, 2, AIR);
        Build.door(f, -1, 1, 2, 0, 1, WOOD_DOOR);
        f.set(2, 2, -1, PANE); f.set(-3, 2, -1, PANE); f.set(0, 2, -3, PANE);
        f.set(1, 1, -2, FURNACE, f.facing(0, 1)); f.set(-2, 1, -2, WORKBENCH); f.set(-2, 1, 1, CAULDRON, 2);
        f.pot(1, 2, -2, chance(f, h, 21, 0.5) ? "red_flower:3" : "red_flower:8");
        if (chance(f, h, 22, 0.35)) f.npc(0, 1, 0, 0, 1, chance(f, h, 23, 0.5) ? "citizen:householder" : "citizen:elder", null);
        Build.flowers(f, -3, 3, 2, 3, 0.8);
    }

    private static void grove(Frame f, long h) {
        int kind = (int) (f.pick(h, 30) * 3);
        if (kind == 0) { Build.olive(f, -2, -1); Build.olive(f, 1, 1); }
        else if (kind == 1) { Build.cypress(f, -2, -2); Build.cypress(f, 1, -2); Build.cypress(f, -2, 1); Build.cypress(f, 1, 1); }
        else { Build.fruitTree(f, -1, 0); Build.olive(f, 2, -2); }
        Build.flowers(f, -3, -3, 2, 2, 0.35);
        if (chance(f, h, 31, 0.3)) { f.set(0, g(f, 0, 2) + 1, 2, OAK_STAIRS, f.stairs(0, -1, false)); f.set(-1, g(f, -1, 2) + 1, 2, OAK_STAIRS, f.stairs(0, -1, false)); }
    }

    private static void garden(Frame f, long h) {
        for (int x = -3; x <= 2; x++) for (int z = -3; z <= 2; z++) {
            boolean edge = x == -3 || x == 2 || z == -3 || z == 2;
            if (edge && !(x == -1 && z == 2)) on(f, x, z, 1, LEAVES, 4);
        }
        Build.flowers(f, -2, -2, 1, 1, 0.9);
        on(f, -1, -1, 1, QUARTZ, 1); on(f, -1, -1, 2, END_ROD, 1);
        on(f, 1, 1, 1, QUARTZ_STAIRS, f.stairs(-1, 0, false));
        if (chance(f, h, 40, 0.3)) f.npc(0, g(f, 0, 0) + 1, 0, 0, -1, "citizen:gardener", null);
    }

    private static void hayCart(Frame f, long h) {
        on(f, -3, -2, 1, HAY); on(f, -3, -1, 1, HAY); on(f, -2, -2, 1, HAY); on(f, -3, -2, 2, HAY);
        // The cart: a plank bed on four wheels (trapdoors) with a pole.
        for (int x = 0; x <= 2; x++) { on(f, x, 0, 2, WOOD_SLAB, 8); on(f, x, 1, 2, WOOD_SLAB, 8); }
        on(f, 0, -1, 1, TRAPDOOR, 12); on(f, 2, -1, 1, TRAPDOOR, 12); on(f, 0, 2, 1, TRAPDOOR, 12); on(f, 2, 2, 1, TRAPDOOR, 12);
        on(f, 1, 0, 3, HAY); on(f, -1, 0, 1, FENCE);
        if (chance(f, h, 50, 0.3)) f.npc(-1, g(f, -1, 2) + 1, 2, 1, 0, chance(f, h, 51, 0.5) ? "animal:cow" : "citizen:farmer", null);
    }

    private static void sheepPen(Frame f, long h) {
        Build.fence(f, -3, -3, 2, 2, FENCE, 0, -3, 0);
        on(f, 1, 1, 1, HAY); on(f, 1, -2, 1, CAULDRON, 3);
        if (chance(f, h, 60, 0.5)) f.npc(-1, g(f, -1, -1) + 1, -1, 0, 1, "animal:sheep", null);
        if (chance(f, h, 61, 0.3)) f.npc(0, g(f, 0, 1) + 1, 1, 0, 1, "animal:sheep", null);
    }

    private static void well(Frame f, long h) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            boolean mid = x == 0 && z == 0;
            for (int y = -3; y <= 0; y++) f.set(x, y, z, mid ? (y == 0 ? AIR : WATER) : BRICK);
            if (!mid) f.set(x, 1, z, x == 0 || z == 0 ? AIR : BRICK);
        }
        for (int[] c : new int[][] {{-1, -1}, {1, 1}}) { f.set(c[0], 1, c[1], FENCE); f.set(c[0], 2, c[1], FENCE); }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.set(x, 3, z, WOOD_SLAB, 0);
        f.set(0, 1, 0, AIR); f.set(0, 2, 0, AIR);
        Build.flowers(f, -3, -3, 2, 2, 0.25);
        if (chance(f, h, 70, 0.25)) f.npc(2, g(f, 2, 0) + 1, 0, -1, 0, "citizen:water_carrier", null);
    }

    private static void vegetablePatch(Frame f, long h) {
        Build.field(f, -2, -2, 1, 1, chance(f, h, 80, 0.5) ? CARROTS : POTATOES, chance(f, h, 81, 0.5));
        Build.fence(f, -3, -3, 2, 2, SPRUCE_FENCE, 0, 2, 0);
        if (chance(f, h, 82, 0.35)) f.stand(-2, g(f, -2, 2) + 1, 2, 0, -1, "scarecrow");
    }

    private static void lampBench(Frame f, long h) {
        for (int x = -3; x <= 2; x++) on(f, x, 0, 0, GRAVEL);
        Build.lamp(f, -2, g(f, -2, -1) + 1, -1);
        Build.lamp(f, 1, g(f, 1, 1) + 1, 1);
        f.set(-1, g(f, -1, -1) + 1, -1, QUARTZ_STAIRS, f.stairs(0, 1, false));
        f.set(0, g(f, 0, -1) + 1, -1, QUARTZ_STAIRS, f.stairs(0, 1, false));
        Build.cypress(f, 2, -3);
        if (chance(f, h, 90, 0.25)) f.npc(0, g(f, 0, 1) + 1, 1, 0, -1, "citizen:traveller", null);
    }

    private static void shrine(Frame f, long h) {
        footing(f, -2, -2, 1, 1, QUARTZ, 0);
        for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) f.set(x, 0, z, DOUBLE_SLAB, 7);
        for (int[] c : new int[][] {{-2, -2}, {1, -2}, {-2, 1}, {1, 1}}) Build.column(f, c[0], c[1], 1, 3);
        for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) f.set(x, 4, z, SLAB, 7);
        f.set(0, 1, -1, QUARTZ, 1); f.set(0, 2, -1, END_ROD, 1);
        f.pot(-1, 1, -1, "red_flower:" + (int) (f.pick(h, 100) * 9));
        Build.flowers(f, -3, 2, 2, 2, 0.9);
    }

    private static void pool(Frame f, long h) {
        for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) {
            boolean edge = x == -2 || x == 1 || z == -2 || z == 1;
            f.set(x, 0, z, edge ? QUARTZ : WATER, 0);
            f.set(x, -1, z, QUARTZ, 0);
            if (!edge && f.roll(x, 0, z, 110) < 0.5) f.set(x, 1, z, LILY);
        }
        Build.flowers(f, -3, -3, 2, 2, 0.3);
        if (chance(f, h, 111, 0.4)) Build.cypress(f, 2, 2);
    }

    private static void pergola(Frame f, long h) {
        for (int[] c : new int[][] {{-2, -2}, {1, -2}, {-2, 1}, {1, 1}}) for (int y = 1; y <= 3; y++) on(f, c[0], c[1], y, FENCE);
        for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) f.set(x, g(f, x, z) + 4, z, WOOD_SLAB, 0);
        for (int x = -2; x <= 1; x++) { f.set(x, g(f, x, -3) + 3, -3, VINE, 1); }
        f.set(-1, g(f, -1, 0) + 1, 0, OAK_STAIRS, f.stairs(1, 0, false));
        f.set(0, g(f, 0, 0) + 1, 0, WOOD_SLAB, 8);
        if (chance(f, h, 120, 0.3)) f.npc(-1, g(f, -1, -1) + 1, -1, 1, 0, chance(f, h, 121, 0.5) ? "citizen:poet" : "citizen:reader", null);
    }

    private static void woodpile(Frame f, long h) {
        for (int x = -2; x <= 1; x++) for (int y = 1; y <= 2; y++) on(f, x, -2, y, LOG, 4);
        on(f, 0, 0, 1, LOG, 0); on(f, 1, 1, 1, LOG, 0);
        on(f, -2, 1, 1, WOOD_SLAB, 0);
        Build.olive(f, 2, 2);
    }

    private static void brokenColumns(Frame f, long h) {
        Build.column(f, -2, -2, g(f, -2, -2) + 1, g(f, -2, -2) + 4);
        on(f, 1, -2, 1, QUARTZ, 1); on(f, 1, -2, 2, QUARTZ, 2);
        for (int x = -2; x <= 1; x++) on(f, x, 1, 1, QUARTZ, 4);   // a fallen drum, lying east-west
        on(f, 0, -1, 1, MOSSY);
        Build.flowers(f, -3, -3, 2, 2, 0.4);
    }

    private static void beeYard(Frame f, long h) {
        for (int x = -2; x <= 1; x += 3) for (int z = -2; z <= 1; z += 3) { on(f, x, z, 1, PLANKS, 2); on(f, x, z, 2, HAY); on(f, x, z, 3, WOOD_SLAB, 2); }
        Build.flowers(f, -3, -3, 2, 2, 0.8);
        if (chance(f, h, 130, 0.3)) f.npc(0, g(f, 0, 0) + 1, 0, 0, 1, "citizen:beekeeper", null);
    }

    /** Near the Line: the Concord's army in the fields. */
    private static void frontier(Frame f, long h) {
        switch ((int) (f.pick(h, 140) * 4)) {
            case 0: {   // a white tent
                for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) {
                    int y = x == -2 || x == 1 ? 1 : 2;
                    on(f, x, z, y, WOOL, WHITE);
                    if (y == 2) on(f, x, z, 1, AIR);
                }
                on(f, -1, -2, 1, AIR); on(f, -1, -2, 2, AIR);
                on(f, 0, 1, 1, CARPET, LIGHT_BLUE);
                f.banner(2, g(f, 2, -2) + 1, -2, 0, 1, false, "concord");
                if (chance(f, h, 141, 0.35)) f.npc(0, g(f, 0, -3) + 1, -3, 0, -1, "citizen:hoplite", null);
                break;
            }
            case 1:   // a weapon rack and training post
                f.stand(-1, g(f, -1, 0) + 1, 0, 0, 1, "hoplite_rack");
                f.stand(1, g(f, 1, 0) + 1, 0, 0, 1, "target");
                on(f, -2, -2, 1, HAY);
                break;
            case 2:   // supply stack
                for (int x = -2; x <= 0; x++) for (int z = -1; z <= 0; z++) on(f, x, z, 1, PLANKS, 1);
                on(f, -2, -1, 2, PLANKS, 1);
                f.chest(1, g(f, 1, -1) + 1, -1, 0, 1, "atlas:depot", null);
                on(f, 1, 1, 1, HAY);
                break;
            default:   // signal pole with a lumen head
                for (int y = 1; y <= 5; y++) on(f, 0, 0, y, FENCE);
                on(f, 0, 0, 6, SEA_LANTERN);
                on(f, -1, 0, 1, QUARTZ, 2);
                Build.flowers(f, -3, -3, 2, 2, 0.2);
        }
    }

    // ------------------------------------------------------------------ the Wound

    private static void wound(Frame f, long h) {
        switch ((int) (f.pick(h, 150) * 6)) {
            case 0:   // a shell crater
                for (int x = -3; x <= 2; x++) for (int z = -3; z <= 2; z++) {
                    double d = Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5));
                    if (d > 3.2) continue;
                    int deep = d < 1.5 ? 2 : 1, gy = g(f, x, z);
                    for (int y = gy; y > gy - deep; y--) f.set(x, y, z, AIR);
                    f.set(x, gy - deep, z, f.roll(x, 0, z, 151) < 0.5 ? GRAVEL : DIRT, f.roll(x, 0, z, 151) < 0.5 ? 0 : 1);
                }
                break;
            case 1:   // a fallen automaton
                on(f, -2, 0, 1, CONCRETE, SILVER); on(f, -1, 0, 1, CONCRETE, SILVER); on(f, 0, 0, 1, CONCRETE, SILVER);
                on(f, 1, 0, 1, STAINED_GLASS, LIGHT_BLUE); on(f, -1, 1, 1, BARS); on(f, -1, -1, 1, BARS);
                on(f, 2, -2, 1, CONCRETE, SILVER);
                break;
            case 2:   // bones and a broken spear
                on(f, -1, -1, 1, BONE, 0); on(f, 0, -1, 1, BONE, 4); on(f, 1, 1, 1, SKULL, 1);
                on(f, -2, 1, 1, FENCE); on(f, -2, 1, 2, FENCE);
                break;
            case 3:   // graves of the Line
                for (int x = -2; x <= 1; x += 3) { on(f, x, -1, 1, WALL, 1); on(f, x, 0, 0, DIRT, 1); on(f, x, 1, 0, DIRT, 1); }
                f.post(-1, g(f, -1, -2) + 1, -2, 0, -1, "HERE LIE\nTHE WATCH\nOF PHAROS " + (1 + (int) (f.pick(h, 152) * 30)) + "\n~");
                break;
            case 4:   // a burnt wagon
                for (int x = -2; x <= 1; x++) on(f, x, 0, 1, LOG2, 1 | 4);
                on(f, -2, -1, 1, TRAPDOOR, 12); on(f, 1, 1, 1, TRAPDOOR, 12);
                on(f, 0, 1, 1, WOOL, BLACK);
                break;
            default:   // a broken column of an outpost
                Build.column(f, 0, 0, g(f, 0, 0) + 1, g(f, 0, 0) + 2 + (int) (f.pick(h, 153) * 3));
                on(f, 1, -1, 1, QUARTZ, 4); on(f, 2, -1, 1, QUARTZ, 4);
                on(f, -2, 1, 1, DEADBUSH);
        }
    }

    // ------------------------------------------------------------------ the Dominion

    private static void marches(Frame f, long h) {
        switch ((int) (f.pick(h, 160) * 9)) {
            case 0: case 1: {   // an Ashborn tent
                int col = f.pick(h, 161) < 0.5 ? BLACK : GRAY;
                for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) {
                    int y = x == -2 || x == 1 ? 1 : 2;
                    on(f, x, z, y, WOOL, col);
                    if (y == 2) on(f, x, z, 1, AIR);
                }
                on(f, -1, -2, 1, AIR); on(f, -1, -2, 2, AIR);
                on(f, 0, 1, 1, CARPET, BROWN);
                if (f.pick(h, 162) < 0.25) f.chest(0, g(f, 0, 0) + 1, 0, -1, 0, "atlas:orc_stash", null);
                break;
            }
            case 2:   // stakes with skulls
                for (int x = -2; x <= 1; x += 3) for (int z = -2; z <= 1; z += 3) { on(f, x, z, 1, FENCE); on(f, x, z, 2, FENCE); on(f, x, z, 3, SKULL, 1); }
                on(f, 0, 0, 1, BONE, 0);
                break;
            case 3:   // a watch fire
                for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) on(f, d[0], d[1], 1, COBBLE);
                on(f, 0, 0, 0, NETHERRACK); on(f, 0, 0, 1, FIRE);
                f.set(-2, g(f, -2, -2) + 1, -2, DARK_OAK_STAIRS, f.stairs(1, 1, false));
                if (f.pick(h, 163) < 0.2) f.npc(2, g(f, 2, 1) + 1, 1, -1, 0, "dominion:ashborn", null);
                break;
            case 4:   // a barricade of sharpened logs
                for (int x = -3; x <= 2; x++) { on(f, x, 0, 1, LOG2, 1); if ((x & 1) == 0) on(f, x, 0, 2, NETHER_FENCE); }
                break;
            case 5:   // a bone pile
                on(f, 0, 0, 1, BONE, 0); on(f, 1, 0, 1, BONE, 0); on(f, 0, 1, 1, BONE, 8); on(f, 0, 0, 2, SKULL, 1); on(f, -2, -1, 1, BONE, 4);
                break;
            case 6:   // a prisoner's cage
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    boolean edge = x != 0 || z != 0;
                    for (int y = 1; y <= 2; y++) if (edge) on(f, x, z, y, BARS);
                    on(f, x, z, 3, NETHER_BRICK);
                }
                if (f.pick(h, 164) < 0.3) f.npc(0, g(f, 0, 0) + 1, 0, 0, 1, "bound:laborer", null);
                break;
            case 7:   // a war banner on a black pole
                for (int y = 1; y <= 3; y++) on(f, 0, 0, y, NETHER_FENCE);
                f.banner(0, g(f, 0, 0) + 4, 0, 0, 1, false, "black_ash");
                on(f, 1, 1, 1, SKULL, 1);
                break;
            default:   // ash drifts and a burnt tree
                for (int x = -3; x <= 2; x++) for (int z = -3; z <= 2; z++) if (f.roll(x, 0, z, 165) < 0.35) on(f, x, z, 1, POWDER, f.roll(x, 1, z, 165) < 0.5 ? GRAY : BLACK);
                Build.deadTree(f, 0, 0, true);
        }
    }

    private static void weald(Frame f, long h) {
        switch ((int) (f.pick(h, 170) * 6)) {
            case 0: case 1: Build.stoneTree(f, 0, 0, (int) (f.pick(h, 171) * 2)); break;
            case 2:   // a stilled figure
                on(f, 0, 0, 1, STONE, 0); on(f, 0, 0, 2, STONE, 5); on(f, 0, 0, 3, STONE, 6);
                on(f, 1, 0, 2, COBBLE_STAIRS, f.stairs(-1, 0, true));
                on(f, -2, 1, 1, WEB);
                break;
            case 3:   // a still pool
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) { f.set(x, g(f, x, z), z, CONCRETE, BLACK); }
                on(f, 0, 0, 0, WATER); on(f, 2, 2, 1, WEB);
                break;
            case 4:   // a hanging cage on a stone gibbet
                for (int y = 1; y <= 4; y++) on(f, -1, 0, y, STONE, 6);
                on(f, 0, 0, 4, STONE, 6); on(f, 1, 0, 4, STONE, 6); on(f, 1, 0, 3, BARS); on(f, 1, 0, 2, BARS);
                break;
            default:   // stone stumps and cobwebs
                on(f, -2, -1, 1, STONE, 5); on(f, 1, 1, 1, STONE, 5); on(f, 1, 1, 2, STONE, 5); on(f, -1, 2, 1, WEB); on(f, 0, -2, 1, MOSSY);
        }
    }

    private static void forges(Frame f, long h) {
        switch ((int) (f.pick(h, 180) * 6)) {
            case 0: case 1:   // a slag heap
                for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) {
                    int t = 1 + (int) (f.roll(x, 0, z, 181) * (3 - Math.abs(x + 0.5) - Math.abs(z + 0.5) / 2));
                    for (int y = 1; y <= t; y++) on(f, x, z, y, f.roll(x, y, z, 182) < 0.2 ? MAGMA : CONCRETE, BLACK);
                }
                break;
            case 2:   // an ember pit: lava sunk in a closed basin
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    f.set(x, g(f, x, z), z, x == 0 && z == 0 ? LAVA : MAGMA);
                    f.set(x, g(f, x, z) - 1, z, OBSIDIAN);
                }
                for (int[] d : new int[][] {{-2, 0}, {2, 0}, {0, -2}, {0, 2}}) on(f, d[0], d[1], 1, NETHER_FENCE);
                break;
            case 3:   // chains on a post
                for (int y = 1; y <= 4; y++) on(f, 0, 0, y, NETHER_BRICK);
                for (int y = 2; y <= 4; y++) { on(f, 1, 0, y, BARS); on(f, -1, 0, y, BARS); }
                if (f.pick(h, 183) < 0.3) f.npc(1, g(f, 1, 1) + 1, 1, 0, -1, "bound:laborer", null);
                break;
            case 4:   // a furnace row
                for (int x = -2; x <= 1; x++) { on(f, x, 0, 1, FURNACE, f.facing(0, 1)); on(f, x, 0, 2, NETHER_BRICK); }
                on(f, -2, 2, 1, COBBLE); on(f, 1, 2, 1, COBBLE);
                break;
            default:   // an ore spill and a cauldron of slag
                for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) if (f.roll(x, 0, z, 184) < 0.4) on(f, x, z, 1, GRAVEL);
                on(f, 0, 0, 1, CAULDRON, 0); on(f, 1, 0, 1, NETHER_FENCE);
        }
    }

    private static void fallen(Frame f, long h) {
        switch ((int) (f.pick(h, 190) * 6)) {
            case 0: case 1:   // the corner of a ruined house
                for (int x = -2; x <= 1; x++) { int t = 1 + (int) (f.roll(x, 0, -2, 191) * 3); for (int y = 1; y <= t; y++) on(f, x, -2, y, BRICK, f.roll(x, y, -2, 192) < 0.4 ? 2 : 0); }
                for (int z = -1; z <= 1; z++) { int t = 1 + (int) (f.roll(-2, 0, z, 193) * 2); for (int y = 1; y <= t; y++) on(f, -2, z, y, BRICK, 2); }
                on(f, 0, 0, 1, COBBLE); on(f, 1, 1, 1, COBBLE_STAIRS, f.stairs(0, 1, false));
                break;
            case 2:   // a toppled statue
                for (int x = -2; x <= 1; x++) on(f, x, 0, 1, x == 1 ? QUARTZ : CONCRETE, x == 1 ? 1 : WHITE);
                on(f, -3, 1, 1, QUARTZ, 1);
                break;
            case 3:   // an edict post
                for (int y = 1; y <= 2; y++) on(f, 0, 0, y, CONCRETE, BLACK);
                f.sign(0, g(f, 0, 1) + 2, 1, 0, 1, Texts.edict(h));
                break;
            case 4:   // an abandoned market stall
                for (int[] c : new int[][] {{-2, -1}, {1, -1}, {-2, 1}, {1, 1}}) { on(f, c[0], c[1], 1, FENCE); on(f, c[0], c[1], 2, FENCE); }
                for (int x = -2; x <= 1; x++) for (int z = -1; z <= 1; z++) if (f.roll(x, 3, z, 194) < 0.6) f.set(x, g(f, x, z) + 3, z, CARPET, GRAY);
                on(f, -1, 0, 1, WOOD_SLAB, 8); on(f, 0, 0, 1, WOOD_SLAB, 8);
                break;
            default:   // rubble
                for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) if (f.roll(x, 0, z, 195) < 0.45) on(f, x, z, 1, f.roll(x, 1, z, 195) < 0.5 ? COBBLE : BRICK, f.roll(x, 1, z, 195) < 0.5 ? 0 : 2);
        }
    }

    private static void fallenFreed(Frame f, long h) {
        if (f.pick(h, 200) < 0.5) {
            for (int[] c : new int[][] {{-2, -1}, {1, -1}, {-2, 1}, {1, 1}}) { on(f, c[0], c[1], 1, FENCE); on(f, c[0], c[1], 2, FENCE); }
            for (int x = -2; x <= 1; x++) for (int z = -1; z <= 1; z++) f.set(x, g(f, x, z) + 3, z, CARPET, (x + z & 1) == 0 ? WHITE : LIGHT_BLUE);
            on(f, -1, 0, 1, WOOD_SLAB, 8); on(f, 0, 0, 1, WOOD_SLAB, 8);
            if (f.pick(h, 201) < 0.35) f.npc(0, g(f, 0, 2) + 1, 2, 0, -1, "citizen:grocer", null);
        } else healing(f, h, false);
    }

    private static void plateau(Frame f, long h) {
        switch ((int) (f.pick(h, 210) * 4)) {
            case 0:   // black spikes
                for (int k = 0; k < 4; k++) {
                    int x = -2 + (int) (f.pick(h, 211 + k) * 4), z = -2 + (int) (f.pick(h, 215 + k) * 4), t = 2 + (int) (f.pick(h, 219 + k) * 4);
                    for (int y = 1; y <= t; y++) on(f, x, z, y, y == t ? NETHER_FENCE : OBSIDIAN);
                }
                break;
            case 1:   // an obsidian shard, leaning
                for (int y = 1; y <= 4; y++) on(f, y / 2 - 1, 0, y, OBSIDIAN);
                break;
            case 2:   // an ash vent
                on(f, 0, 0, 0, MAGMA); for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) on(f, d[0], d[1], 1, CONCRETE, BLACK);
                break;
            default:   // bones in the cinders
                on(f, -1, 0, 1, BONE, 4); on(f, 1, -1, 1, BONE, 0); on(f, 0, 1, 1, SKULL, 1);
        }
    }

    /** Freed land: gardens, saplings, flowers on the old stakes, clean water, a returning family's tent. */
    private static void healing(Frame f, long h, boolean weald) {
        switch ((int) (f.pick(h, 220) * 6)) {
            case 0:
                Build.fence(f, -3, -3, 2, 2, FENCE, 0, -3, 0);
                Build.field(f, -2, -2, 1, 1, CROPS, true);
                break;
            case 1: if (weald) Build.fruitTree(f, 0, 0); else Build.olive(f, 0, 0); Build.flowers(f, -3, -3, 2, 2, 0.5); break;
            case 2:   // an old stake with flowers round it
                on(f, 0, 0, 1, FENCE); on(f, 0, 0, 2, FLOWER_POT);
                Build.flowers(f, -2, -2, 1, 1, 0.8);
                break;
            case 3:   // a returning family's tent
                for (int x = -2; x <= 1; x++) for (int z = -2; z <= 1; z++) {
                    int y = x == -2 || x == 1 ? 1 : 2;
                    on(f, x, z, y, WOOL, (x + z & 1) == 0 ? WHITE : BROWN);
                    if (y == 2) on(f, x, z, 1, AIR);
                }
                on(f, -1, -2, 1, AIR); on(f, -1, -2, 2, AIR);
                if (f.pick(h, 221) < 0.3) f.npc(-1, g(f, -1, -3) + 1, -3, 0, -1, "citizen:settler", null);
                break;
            case 4:   // clean water in an old crater
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) f.set(x, g(f, x, z), z, WATER);
                Build.flowers(f, -3, -3, 2, 2, 0.3);
                break;
            default:   // saplings in a row
                for (int x = -2; x <= 1; x += 1) if (f.roll(x, 0, 0, 222) < 0.7) { on(f, x, 0, 0, GRASS); on(f, x, 0, 1, SAPLING, (int) (f.roll(x, 1, 0, 222) * 3)); }
                Build.flowers(f, -3, -3, 2, 2, 0.4);
        }
    }

    // ------------------------------------------------------------------ details: small things in the gaps (local -1..1)

    private static void detail(Frame f, long h, Realm.Zone zone, boolean freed, int x, int z) {
        int k = (int) (f.pick(h, 300) * 12);
        switch (zone) {
            case CONCORD:
                if (x > Realm.lineX(z) - 150 && k < 3) { lineDetail(f, h, k); return; }
                concordDetail(f, h, k);
                return;
            case WOUND: woundDetail(f, h, k); return;
            case MARCHES: case GATE_ROAD: if (freed) freedDetail(f, h, k, false); else marchesDetail(f, h, k); return;
            case WEALD: if (freed) freedDetail(f, h, k, true); else wealdDetail(f, h, k); return;
            case FORGES: if (freed) freedDetail(f, h, k, false); else forgesDetail(f, h, k); return;
            case FALLEN: if (freed) freedDetail(f, h, k, false); else fallenDetail(f, h, k); return;
            case PLATEAU: if (freed) freedDetail(f, h, k, false); else plateauDetail(f, h, k); return;
            default:
        }
    }

    private static void concordDetail(Frame f, long h, int k) {
        switch (k) {
            case 0: Build.olive(f, 0, 0); break;
            case 1: Build.cypress(f, 0, 0); break;
            case 2: Build.fruitTree(f, 0, 0); break;
            case 3: Build.flowers(f, -1, -1, 1, 1, 0.95); break;
            case 4: on(f, 0, 0, 1, HAY); on(f, 1, 0, 1, HAY); on(f, 0, 0, 2, HAY); break;
            case 5: on(f, 0, 0, 1, LEAVES, 4); on(f, 1, 0, 1, LEAVES, 4); on(f, 0, 1, 1, LEAVES, 4); break;   // a clipped bush
            case 6: Build.lamp(f, 0, g(f, 0, 0) + 1, 0); on(f, 1, 0, 1, QUARTZ_STAIRS, f.stairs(-1, 0, false)); break;
            case 7: on(f, 0, 0, 1, COBBLE); on(f, 0, 1, 1, MOSSY); on(f, 1, 0, 1, COBBLE_STAIRS, f.stairs(-1, 0, false)); break;   // a mossy boulder
            case 8: on(f, 0, 0, 1, CAULDRON, 3); on(f, 1, 0, 1, FENCE); break;   // a water trough
            case 9: Build.statue(f, 0, g(f, 0, 0) + 1, 0, 0, 1, 2); break;
            case 10: for (int x = -1; x <= 1; x++) on(f, x, 0, 1, DOUBLE_PLANT, 1); for (int x = -1; x <= 1; x++) on(f, x, 0, 2, DOUBLE_PLANT, 9); break;   // lilac row
            default: on(f, 0, 0, 1, PLANKS, 0); on(f, 1, 0, 1, PLANKS, 0); on(f, 0, 0, 2, WOOD_SLAB, 0);   // crates
        }
    }

    private static void lineDetail(Frame f, long h, int k) {
        if (k == 0) f.stand(0, g(f, 0, 0) + 1, 0, 0, 1, "target");
        else if (k == 1) { for (int y = 1; y <= 3; y++) on(f, 0, 0, y, FENCE); on(f, 0, 0, 4, SEA_LANTERN); }
        else { on(f, 0, 0, 1, PLANKS, 1); on(f, 1, 0, 1, PLANKS, 1); on(f, 0, 1, 1, HAY); }
    }

    private static void woundDetail(Frame f, long h, int k) {
        switch (k % 6) {
            case 0: on(f, 0, 0, 1, SKULL, 1); on(f, 1, 0, 1, BONE, 4); break;
            case 1: on(f, 0, 0, 1, FENCE); on(f, 0, 0, 2, FENCE); break;   // a broken spear
            case 2: Build.deadTree(f, 0, 0, true); break;
            case 3: on(f, 0, 0, 1, CONCRETE, SILVER); on(f, 1, 0, 1, BARS); break;   // automaton debris
            case 4: on(f, 0, 0, 0, GRAVEL); on(f, 1, 0, 0, GRAVEL); on(f, 0, 1, 0, DIRT, 1); on(f, 0, 0, 1, POWDER, BLACK); break;   // scorch
            default: on(f, 0, 0, 1, WALL, 1); on(f, 0, 1, 1, CARPET, BROWN);   // a grave marker
        }
    }

    private static void marchesDetail(Frame f, long h, int k) {
        switch (k % 8) {
            case 0: on(f, 0, 0, 1, FENCE); on(f, 0, 0, 2, FENCE); on(f, 0, 0, 3, SKULL, 1); break;   // a stake
            case 1: on(f, 0, 0, 1, BONE, 0); on(f, 1, 0, 1, BONE, 4); on(f, 0, 0, 2, SKULL, 1); break;
            case 2: on(f, 0, 0, 1, PLANKS, 5); on(f, 1, 0, 1, PLANKS, 5); on(f, 0, 0, 2, PLANKS, 5); break;   // dark crates
            case 3: Build.spikePost(f, 0, 0, g(f, 0, 0) + 1, g(f, 0, 0) + 2); break;
            case 4: Build.deadTree(f, 0, 0, true); break;
            case 5: for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (f.roll(x, 0, z, 301) < 0.5) on(f, x, z, 1, POWDER, BLACK); break;   // an ash drift
            case 6: on(f, 0, 0, 1, NETHER_FENCE); on(f, 1, 0, 1, NETHER_FENCE); on(f, -1, 0, 1, NETHER_FENCE); break;   // caltrops
            default: on(f, 0, 0, 1, COBBLE); on(f, 0, 0, 2, NETHERRACK); on(f, 0, 0, 3, FIRE);   // a signal brazier
        }
    }

    private static void wealdDetail(Frame f, long h, int k) {
        switch (k % 5) {
            case 0: case 1: Build.stoneTree(f, 0, 0, 0); break;
            case 2: on(f, 0, 0, 1, STONE, 5); on(f, 0, 0, 2, STONE, 5); break;   // a stump
            case 3: on(f, 0, 0, 1, WEB); on(f, 1, 1, 1, WEB); break;
            default: on(f, 0, 0, 1, STONE, 0); on(f, 0, 0, 2, STONE, 6); on(f, 0, 0, 3, STONE, 5);   // a stilled figure
        }
    }

    private static void forgesDetail(Frame f, long h, int k) {
        switch (k % 5) {
            case 0: on(f, 0, 0, 1, CONCRETE, BLACK); on(f, 1, 0, 1, MAGMA); on(f, 0, 1, 1, CONCRETE, BLACK); on(f, 0, 0, 2, CONCRETE, BLACK); break;   // slag
            case 1: on(f, 0, 0, 1, NETHER_BRICK); on(f, 0, 0, 2, BARS); on(f, 0, 0, 3, BARS); break;   // a chain post
            case 2: on(f, 0, 0, 1, FURNACE, f.facing(0, 1)); on(f, 0, 0, 2, NETHER_BRICK); break;
            case 3: on(f, 0, 0, 1, GRAVEL); on(f, 1, 0, 1, GRAVEL); on(f, 0, 0, 2, CAULDRON, 0); break;
            default: on(f, 0, 0, 0, MAGMA); on(f, 1, 1, 0, MAGMA);   // cracks
        }
    }

    private static void fallenDetail(Frame f, long h, int k) {
        switch (k % 5) {
            case 0: on(f, 0, 0, 1, BRICK, 2); on(f, 1, 0, 1, COBBLE); on(f, 0, 0, 2, BRICK, 2); break;   // rubble
            case 1: on(f, 0, 0, 1, QUARTZ, 4); on(f, 1, 0, 1, QUARTZ, 4); break;   // a fallen drum
            case 2: on(f, 0, 0, 1, CONCRETE, BLACK); on(f, 0, 0, 2, CONCRETE, BLACK); break;   // an edict marker
            case 3: Build.deadTree(f, 0, 0, false); break;
            default: on(f, 0, 0, 1, WOOD_SLAB, 8); on(f, 1, 0, 1, FENCE); on(f, 0, 1, 1, CARPET, GRAY);   // a broken bench
        }
    }

    private static void plateauDetail(Frame f, long h, int k) {
        int t = 2 + k % 3;
        for (int y = 1; y <= t; y++) on(f, 0, 0, y, y == t ? NETHER_FENCE : OBSIDIAN);
        if (k % 2 == 0) on(f, 1, 0, 1, BONE, 4);
    }

    private static void freedDetail(Frame f, long h, int k, boolean weald) {
        switch (k % 5) {
            case 0: if (weald) Build.fruitTree(f, 0, 0); else on(f, 0, 0, 1, SAPLING, k % 3); break;
            case 1: Build.flowers(f, -1, -1, 1, 1, 0.9); break;
            case 2: on(f, 0, 0, 1, FENCE); on(f, 0, 0, 2, FLOWER_POT); break;
            case 3: on(f, 0, 0, 1, HAY); break;
            default: on(f, 0, 0, 1, TALLGRASS, 1); on(f, 1, 0, 1, TALLGRASS, 1); on(f, 0, 1, 1, RED_FLOWER, 3);
        }
    }
}
