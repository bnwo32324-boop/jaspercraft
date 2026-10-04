package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The catacombs under Drownhollow: in every 128-block district a network sits two dozen blocks below the lowest ground,
 * one node under the centre of each chunk (a vaulted room or a crossing, or solid rock) joined by corridors, some of them
 * trapped with pressure plates that fire arrow dispensers. Rooms are crypts, treasure vaults, flooded halls, lava pits,
 * shrines, bone pits and prisons, most of them guarded. Catacomb gates on the surface (a field monument) lead down.
 *
 * Epoch 5 (owner 2026-10-04: "more dense with dungeons ... 2x it"): a second network, the deep catacombs, DEEP_DROP blocks
 * under the first wherever the rock is deep enough, with more rooms to its nodes and the hardest of them (altars); a
 * third of the upper rooms that stand over a deep room have a ladder shaft down in a corner. Both levels gain armouries,
 * libraries and spider nests.
 */
final class Catacombs {
    private Catacombs() {}

    static final int NONE = 0, CROSSING = 1, CRYPT = 2, TREASURE = 3, FLOODED = 4, LAVA = 5, SHRINE = 6, BONES = 7, PRISON = 8,
        ARMORY = 9, LIBRARY = 10, NEST = 11, ALTAR = 12;
    private static final long SALT = 0x43617461L, DEEP_SALT = 0x44656570L;
    /** The deep level's floor lies DEEP_DROP under the first level's, and never below DEEP_FLOOR. */
    static final int DEEP_DROP = 14, DEEP_FLOOR = 6;

    /** The node under the centre of chunk (i, j): NONE, CROSSING or a room type. */
    static int node(Plans plans, int i, int j) {
        long h = Hash.of(plans.seed ^ SALT, i, j);
        double r = Hash.unit(h);
        if (r < 0.18) return NONE;
        int x = i * 16 + 8, z = j * 16 + 8;
        if (plans.door().near(x, z, 8) || plans.reservedChunk(i, j)) return NONE;
        if (r < 0.40) return CROSSING;
        double k = Hash.unit(Hash.mix(h ^ 1));
        return k < 0.20 ? CRYPT : k < 0.30 ? TREASURE : k < 0.38 ? FLOODED : k < 0.44 ? LAVA : k < 0.54 ? SHRINE : k < 0.64 ? BONES
            : k < 0.74 ? PRISON : k < 0.83 ? ARMORY : k < 0.92 ? LIBRARY : NEST;
    }

    /** Floor level of the deep catacombs under a column, or -1 where the rock is not deep enough for them. */
    static int deepDepth(Plans plans, int wx, int wz) {
        int d = plans.depth(wx, wz) - DEEP_DROP;
        return d >= DEEP_FLOOR ? d : -1;
    }

    /** The deep level's node under chunk (i, j): more rooms than the first level, and the altars. */
    static int deepNode(Plans plans, int i, int j) {
        long h = Hash.of(plans.seed ^ DEEP_SALT, i, j);
        double r = Hash.unit(h);
        if (r < 0.12) return NONE;
        int x = i * 16 + 8, z = j * 16 + 8;
        if (deepDepth(plans, x, z) < 0 || plans.door().near(x, z, 8) || plans.reservedChunk(i, j)) return NONE;
        if (r < 0.26) return CROSSING;
        double k = Hash.unit(Hash.mix(h ^ 1));
        return k < 0.14 ? CRYPT : k < 0.26 ? TREASURE : k < 0.32 ? FLOODED : k < 0.40 ? LAVA : k < 0.46 ? SHRINE : k < 0.56 ? BONES
            : k < 0.64 ? PRISON : k < 0.74 ? ARMORY : k < 0.82 ? LIBRARY : k < 0.90 ? NEST : ALTAR;
    }

    /** Whether deep nodes (i, j) and (i + di, j + dj) are joined (within one district, as above). */
    static boolean deepEdge(Plans plans, int i, int j, int di, int dj) {
        if (Math.floorDiv(i * 16, Plans.DISTRICT) != Math.floorDiv((i + di) * 16, Plans.DISTRICT)
            || Math.floorDiv(j * 16, Plans.DISTRICT) != Math.floorDiv((j + dj) * 16, Plans.DISTRICT)) return false;
        if (deepNode(plans, i, j) == NONE || deepNode(plans, i + di, j + dj) == NONE) return false;
        return Hash.unit(plans.seed ^ DEEP_SALT, i * 2 + di, 7, j * 2 + dj) < 0.66;
    }

    /** Whether an upper room has a ladder shaft down to the deep room under it (in its +x +z corner). */
    static boolean descent(Plans plans, int i, int j) {
        return node(plans, i, j) >= CRYPT && deepNode(plans, i, j) >= CRYPT && Hash.unit(plans.seed ^ DEEP_SALT, i, 5, j) < 0.35;
    }

    /** Whether nodes (i, j) and (i + di, j + dj) are joined (only within one district: its networks share a depth). */
    static boolean edge(Plans plans, int i, int j, int di, int dj) {
        if (Math.floorDiv(i * 16, Plans.DISTRICT) != Math.floorDiv((i + di) * 16, Plans.DISTRICT)
            || Math.floorDiv(j * 16, Plans.DISTRICT) != Math.floorDiv((j + dj) * 16, Plans.DISTRICT)) return false;
        if (node(plans, i, j) == NONE || node(plans, i + di, j + dj) == NONE) return false;
        return Hash.unit(plans.seed ^ SALT, i * 2 + di, 7, j * 2 + dj) < 0.62;
    }

    static void draw(Plans plans, Canvas c) {
        int i = c.cx, j = c.cz, depth = plans.depth(c.x0 + 8, c.z0 + 8), deep = deepDepth(plans, c.x0 + 8, c.z0 + 8);
        // The deep level first (14 blocks down: the two never touch), then the first level, then the shafts between them.
        if (deep >= 0) {
            corridor(plans, c, i, j, 1, 0, deep, true);
            corridor(plans, c, i, j, 0, 1, deep, true);
            corridor(plans, c, i - 1, j, 1, 0, deep, true);
            corridor(plans, c, i, j - 1, 0, 1, deep, true);
            int t = deepNode(plans, i, j);
            if (t != NONE) room(c, t, i * 16 + 8, j * 16 + 8, deep, Hash.of(plans.seed ^ DEEP_SALT, i, j, 3));
        }
        // Corridors first (this chunk's share of the four around its node), then the node's room over them.
        corridor(plans, c, i, j, 1, 0, depth, false);
        corridor(plans, c, i, j, 0, 1, depth, false);
        corridor(plans, c, i - 1, j, 1, 0, depth, false);
        corridor(plans, c, i, j - 1, 0, 1, depth, false);
        int type = node(plans, i, j);
        if (type != NONE) room(c, type, i * 16 + 8, j * 16 + 8, depth, Hash.of(plans.seed ^ SALT, i, j, 3));
        if (deep >= 0 && descent(plans, i, j)) shaft(c, i * 16 + 8, j * 16 + 8, depth, deep);
    }

    /**
     * A ladder down from an upper room's +x +z corner (behind its corner pillar; no room puts anything in its corners) to
     * the same corner of the deep room under it, backed by the rooms' walls and by masonry in the rock between.
     */
    private static void shaft(Canvas c, int x, int z, int depth, int deep) {
        Frame f = new Frame(c, x, z, deep, 0);
        int top = depth - deep;
        for (int y = 8; y < top; y++) f.masonry(5, y, 4);
        for (int y = 1; y <= top; y++) f.set(4, y, 4, LADDER, f.facing(-1, 0));
    }

    private static void corridor(Plans plans, Canvas c, int i, int j, int di, int dj, int depth, boolean deep) {
        if (deep ? !deepEdge(plans, i, j, di, dj) : !edge(plans, i, j, di, dj)) return;
        int x0 = i * 16 + 8, z0 = j * 16 + 8, len = 16;
        long h = Hash.of(plans.seed ^ (deep ? DEEP_SALT : SALT), i * 2 + di, j * 2 + dj, 11);
        boolean lined = Hash.unit(h) < 0.6, trap = Hash.unit(Hash.mix(h)) < 0.3;
        Frame f = new Frame(c, x0, z0, depth, di == 1 ? 3 : 0);   // local +z runs along the corridor
        for (int k = 0; k <= len; k++)
            for (int w = -2; w <= 2; w++) {
                for (int y = 0; y <= 5; y++) {
                    boolean shell = Math.abs(w) == 2 || y == 0 || y == 5;
                    if (shell) { if (lined || y == 0) f.masonry(w, y, k); }
                    else f.set(w, y, k, AIR);
                }
                if (Math.abs(w) < 2 && f.roll(w, 4, k, 110) < 0.05) f.set(w, 4, k, WEB);
            }
        if (trap) {   // a dart trap half way along: the plate fires the dispenser hidden in the floor under it
            f.set(0, 1, 8, PLATE, 0);
            f.dispenser(0, 0, 8, 0, 0);
        }
    }

    /** A 9x9 vaulted room around (x, z), floor at {@code depth}. */
    private static void room(Canvas c, int type, int x, int z, int depth, long h) {
        Frame f = new Frame(c, x, z, depth, Hash.range(h, 0, 3));
        int r = type == CROSSING ? 2 : 4, height = type == CROSSING ? 5 : 7;
        for (int a = -r - 1; a <= r + 1; a++)
            for (int b = -r - 1; b <= r + 1; b++)
                for (int y = 0; y <= height; y++) {
                    boolean shell = Math.abs(a) == r + 1 || Math.abs(b) == r + 1 || y == 0 || y == height;
                    boolean door = Math.abs(a) == r + 1 && Math.abs(b) <= 1 && y >= 1 && y <= 4 || Math.abs(b) == r + 1 && Math.abs(a) <= 1 && y >= 1 && y <= 4;
                    if (door) f.set(a, y, b, AIR);
                    else if (shell) { if (y == 0 && type != CROSSING) f.set(a, y, b, (a + b & 1) == 0 ? PRISMARINE : BRICK, (a + b & 1) == 0 ? 2 : 2); else f.eldritch(a, y, b); }
                    else f.set(a, y, b, AIR);
                }
        if (type == CROSSING) return;
        for (int sa = -1; sa <= 1; sa += 2)
            for (int sb = -1; sb <= 1; sb += 2) for (int y = 1; y < height; y++) if (y % 3 == 0) f.glyph(3 * sa, y, 3 * sb); else f.eldritch(3 * sa, y, 3 * sb);
        switch (type) {
            case CRYPT:
                for (int b = -2; b <= 2; b += 2) for (int side = -1; side <= 1; side += 2) { f.set(2 * side, 1, b, DOUBLE_SLAB, 5); f.set(2 * side, 2, b, SLAB, 5); }
                f.spawner(0, 1, 0, Hash.unit(h ^ 5) < 0.5 ? "SKELETON" : "ZOMBIE");
                for (int a = -3; a <= 3; a += 6) f.set(a, height - 1, 0, WEB);
                break;
            case TREASURE:
                f.set(0, 1, 2, OBSIDIAN);
                f.chest(0, 2, 2, 0, -1, Sites.DESERT, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.3");
                f.spawner(0, 1, -2, "ZOMBIE");
                f.set(0, 1, -3, PLATE, 0);
                f.dispenser(0, 0, -3, 0, 0);
                break;
            case FLOODED:
                for (int a = -4; a <= 4; a++) for (int b = -4; b <= 4; b++) if (Math.max(Math.abs(a), Math.abs(b)) <= 3) f.set(a, 1, b, WATER);
                f.spawner(0, 0, 0, "ZOMBIE");
                f.set(0, 3, 0, SEA_LANTERN);
                break;
            case LAVA:
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) { f.set(a, 0, b, 11); f.set(a, -1, b, OBSIDIAN); }
                for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) if (Math.max(Math.abs(a), Math.abs(b)) == 2) f.set(a, 0, b, MAGMA);
                f.chest(3, 1, 0, -1, 0, Sites.DUNGEON, "trinket:0.2");
                break;
            case SHRINE:
                f.set(0, 1, 3, OBSIDIAN); f.set(0, 2, 3, PRISMARINE, 2); f.set(0, 3, 3, PRISMARINE, 0); f.set(0, 3, 2, WALL, 1); f.set(0, 4, 3, SEA_LANTERN);
                f.sign(0, 1, 2, 0, -1, Lore.chant(h));
                f.chest(-2, 1, 3, 1, 0, Sites.JUNGLE, "lore:" + Hash.range(Hash.mix(h), 0, 99));
                break;
            case BONES:
                for (int a = -3; a <= 3; a++) for (int b = -3; b <= 3; b++) {
                    double v = f.roll(a, 1, b, 111);
                    if (v < 0.35) f.set(a, 1, b, BONE, 0);
                    if (v < 0.12) f.set(a, 2, b, BONE, 0);
                    if (v > 0.95) f.set(a, 1, b, SKULL, 1);
                }
                f.spawner(0, 1, 0, "CAVE_SPIDER");
                break;
            case ARMORY:   // weapon racks of fences and bars along two walls, the garrison's chests, a guard
                for (int b = -2; b <= 2; b += 2)
                    for (int side = -1; side <= 1; side += 2) { f.set(4 * side, 1, b, FENCE); f.set(4 * side, 2, b, IRON_BARS); }
                f.chest(-1, 1, 4, 0, -1, Sites.SMITH);
                f.chest(1, 1, 4, 0, -1, Sites.DUNGEON, "trinket:0.15");
                f.spawner(0, 1, -2, "SKELETON");
                break;
            case LIBRARY:   // shelves on three walls, a reading desk, a chest of the Choir's writings
                for (int k = -2; k <= 2; k++)
                    for (int y = 1; y <= 3; y++) {
                        if (f.keep(-4, y, k, 0.85)) f.set(-4, y, k, BOOKSHELF);
                        if (f.keep(4, y, k, 0.85)) f.set(4, y, k, BOOKSHELF);
                        if (f.keep(k, y, 4, 0.85)) f.set(k, y, 4, BOOKSHELF);
                    }
                f.set(0, 1, 1, DOUBLE_SLAB, 5);
                f.chest(0, 1, 3, 0, -1, Sites.LIBRARY, "lore:" + Hash.range(h, 0, 99));
                if (Hash.unit(h ^ 7) < 0.4) f.spawner(0, 1, -2, "ZOMBIE");
                break;
            case NEST:   // webs from wall to wall, bones underfoot, the brood's cage and what it left
                for (int a = -4; a <= 4; a++)
                    for (int b = -4; b <= 4; b++) {
                        if (Math.abs(a) == 4 && Math.abs(b) == 4) continue;
                        double v = f.roll(a, 2, b, 112);
                        if (v < 0.35) f.set(a, 2 + (int) (f.roll(a, 3, b, 113) * 4), b, WEB);
                        else if (v < 0.45) f.set(a, 1, b, BONE, 0);
                    }
                f.spawner(0, 1, 0, "CAVE_SPIDER");
                f.chest(2, 1, -2, -1, 0, Sites.DUNGEON, "trinket:0.12");
                break;
            case ALTAR:   // (deep level only) the Choir's black altar, its chant, its hoard and its keeper
                f.set(0, 1, 2, OBSIDIAN);
                f.set(0, 2, 2, BRICK, 3);
                f.set(0, 3, 2, SEA_LANTERN);
                f.sign(0, 1, 1, 0, -1, Lore.chant(h));
                f.chest(-2, 1, 3, 1, 0, Sites.DESERT, "lore:" + Hash.range(h, 0, 99) + ";trinket:0.3");
                f.spawner(2, 1, -2, "ZOMBIE");
                for (int side = -1; side <= 1; side += 2) f.set(2 * side, 1, 3, SKULL, 1);
                break;
            default:   // PRISON: cells along two walls
                for (int b = -3; b <= 3; b += 3)
                    for (int side = -1; side <= 1; side += 2) {
                        for (int y = 1; y <= 3; y++) f.set(side * 2, y, b, IRON_BARS);
                        f.set(side * 3, 1, b, SKULL, 1);
                    }
                f.spawner(0, 1, 0, "ZOMBIE");
                break;
        }
    }

    /**
     * A catacomb gate: a sunken stone entrance whose ladder shaft drops to the district's depth, then a passage to the
     * node under the chunk (a small chamber if that node is solid rock).
     */
    static void gate(Plans plans, Frame f, long h) {
        int depth = plans.depth(f.ox, f.oz), drop = f.base - depth;
        for (int a = -3; a <= 3; a++)
            for (int b = -3; b <= 3; b++) {
                f.footing(a, b, -1);
                f.eldritch(a, 0, b);
                boolean wall = Math.max(Math.abs(a), Math.abs(b)) == 3;
                for (int y = 1; y <= 5; y++) {
                    if (wall && !(b == -3 && Math.abs(a) <= 1 && y <= 3)) { if (y == 5) f.glyph(a, y, b); else f.eldritch(a, y, b); }
                    else if (!wall) f.set(a, y, b, AIR);
                }
                if (Math.max(Math.abs(a), Math.abs(b)) <= 2 && f.keep(a, 6, b, 0.7)) f.set(a, 6, b, PRISMARINE, 2);
            }
        f.set(-2, 1, -2, SKULL, 1); f.set(2, 1, -2, SKULL, 1);
        f.sign(0, 4, -4, 0, -1, "THE DEAD\nOF THE CHOIR\nSLEEP BELOW");
        // The node under the chunk; the shaft drops into its room through the vault (only the ladder's pillar inside it),
        // or beside it and a passage leads in through the wall. What is inside the room (tiles, pools) is left alone.
        int nx = Math.floorDiv(f.wx(0, 0), 16) * 16 + 8, nz = Math.floorDiv(f.wz(0, 0), 16) * 16 + 8;
        int type = node(plans, Math.floorDiv(nx, 16), Math.floorDiv(nz, 16));
        int keep = type == NONE ? -1 : type == CROSSING ? 2 : 4, roof = depth + (type == CROSSING ? 5 : 7);
        // The shaft: 3x3 of air lined with masonry, a ladder on its north face.
        for (int y = -drop + 1; y <= 0; y++)
            for (int a = -2; a <= 2; a++)
                for (int b = -2; b <= 2; b++) {
                    int wy = f.base + y;
                    boolean inRoom = wy > depth && wy < roof && Math.max(Math.abs(f.wx(a, b) - nx), Math.abs(f.wz(a, b) - nz)) <= keep;
                    boolean shell = Math.abs(a) == 2 || Math.abs(b) == 2;
                    if (inRoom) { if (a == 0 && b == 2) f.masonry(a, y, b); }
                    else if (shell) f.masonry(a, y, b); else f.set(a, y, b, AIR);
                }
        for (int y = -drop + 1; y <= 0; y++) f.set(0, y, 1, LADDER, f.facing(0, -1));
        f.set(0, 1, 1, LADDER, f.facing(0, -1));
        Frame u = new Frame(f.c, f.wx(0, 0), f.wz(0, 0), depth, 0);
        int dx = nx - u.ox, dz = nz - u.oz;
        int sx = Integer.signum(dx), sz = Integer.signum(dz);
        for (int k = 0; ; k += sx) { tunnel(u, k, 0, dx, dz, keep); if (k == dx) break; }
        for (int k = 0; ; k += sz) { tunnel(u, dx, k, dx, dz, keep); if (k == dz) break; }
        if (type == NONE)
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) for (int y = 1; y <= 4; y++) u.set(dx + a, y, dz + b, AIR);
    }

    private static void tunnel(Frame u, int a, int b, int dx, int dz, int keep) {
        for (int da = -1; da <= 1; da++)
            for (int db = -1; db <= 1; db++) {
                if (Math.max(Math.abs(a + da - dx), Math.abs(b + db - dz)) <= keep) continue;
                for (int y = 1; y <= 4; y++) u.set(a + da, y, b + db, AIR);
                u.masonry(a + da, 0, b + db);
            }
    }
}
