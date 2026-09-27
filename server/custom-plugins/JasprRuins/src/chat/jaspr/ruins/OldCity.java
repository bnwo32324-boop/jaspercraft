package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * An original old city: a walled square on levelled ground with a gate in each side, towers at the corners and along
 * the walls, a street grid of 16-block lots, a plaza with a fountain and obelisk and, north of it, a grand temple.
 * The lots hold ruined houses, courtyard houses with wells, overgrown gardens, rubble heaps, tower houses, shrines,
 * sunken cisterns and the odd house with a vault beneath it. Everything is relative to the city centre (u, v).
 */
final class OldCity {
    private OldCity() {}

    private static final int LOT = 16, PLAZA = 12;

    static void draw(Plans.City city, Canvas c) {
        int H = city.half, X = city.x, Z = city.z, G = city.ground;
        if (!c.touches(X - H - 3, Z - H - 3, X + H + 3, Z + H + 3)) return;
        double sturdy = 0.8 + Hash.unit(Hash.mix(city.hash ^ 11)) * 0.15;
        Frame f = new Frame(c, X, Z, G, 0);
        // Ground: plants cleared, streets and the plaza paved.
        for (int wx = c.x0; wx < c.x0 + 16; wx++)
            for (int wz = c.z0; wz < c.z0 + 16; wz++) {
                int u = wx - X, v = wz - Z;
                if (Math.abs(u) > H || Math.abs(v) > H) continue;
                c.clear(wx, wz, G + 1, G + 3);
                if (street(u) || street(v) || Math.abs(u) <= PLAZA && Math.abs(v) <= PLAZA) c.paving(wx, G, wz);
            }
        walls(f, city, sturdy);
        plaza(f, city);
        boolean landmark = H >= 56;
        if (landmark) Sites.temple(new Frame(c, X, Z - 32, G, 2), Hash.mix(city.hash ^ 12), 10, 10, 3, 8, true);
        int n = Math.floorDiv(H - 3 - 13, LOT);
        for (int i = -n - 1; i <= n; i++)
            for (int j = -n - 1; j <= n; j++) {
                int cu = i * LOT + 8, cv = j * LOT + 8;
                if (Math.abs(cu) + 5 > H - 3 || Math.abs(cv) + 5 > H - 3) continue;
                if ((i == -1 || i == 0) && (j == -1 || j == 0)) continue;                  // the plaza
                if (landmark && (i == -1 || i == 0) && (j == -3 || j == -2)) continue;     // the grand temple
                if (!c.touches(X + cu - 6, Z + cv - 6, X + cu + 6, Z + cv + 6)) continue;
                lot(c, city, cu, cv, Hash.of(city.hash, i, j), sturdy);
            }
    }

    /** A street runs along every lot boundary (3 wide) and the two avenues through the centre (5 wide). */
    static boolean street(int u) {
        if (Math.abs(u) <= 2) return true;
        int m = Math.floorMod(u, LOT);
        return m == 0 || m == 1 || m == 15;
    }

    // ------------------------------------------------------------------ walls, gates and towers

    private static void walls(Frame f, Plans.City city, double sturdy) {
        int H = city.half;
        Canvas c = f.c;
        for (int wx = c.x0; wx < c.x0 + 16; wx++)
            for (int wz = c.z0; wz < c.z0 + 16; wz++) {
                int u = wx - city.x, v = wz - city.z;
                int m = Math.max(Math.abs(u), Math.abs(v));
                if (m < H - 1 || m > H) continue;
                int t = Math.abs(u) >= Math.abs(v) ? v : u;            // position along the side
                int seg = Math.floorDiv(t + 1000, 8) + (Math.abs(u) >= Math.abs(v) ? (u < 0 ? 0 : 1) : (v < 0 ? 2 : 3)) * 97;
                double s = sturdy - Hash.unit(city.hash, seg, 5, 13) * 0.55;
                f.footing(u, v, -1);
                f.masonry(u, 0, v);
                if (Math.abs(t) <= 2) {                                // gate
                    f.clear(u, v, 1, 4);
                    for (int y = 5; y <= 7; y++) if (f.keep(u, y, v, s)) f.masonry(u, y, v);
                    continue;
                }
                f.wall(u, v, 1, 7, s);
                if (m == H && f.roll(u, 1, v, 14) < 0.12) {
                    int du = Math.abs(u) >= Math.abs(v) ? (u < 0 ? -1 : 1) : 0, dv = du == 0 ? (v < 0 ? -1 : 1) : 0;
                    f.vines(u, 5, v, du, dv, 3 + (int) (f.roll(u, 2, v, 15) * 3));
                }
            }
        // Corner towers, towers along the sides every 32 blocks and a pair flanking each gate.
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) tower(f, city, su * (H - 1), sv * (H - 1), 3, 12, sturdy);
        for (int side = 0; side < 4; side++)
            for (int t = -H + 16; t <= H - 16; t += 16) {
                if (t == 0 || Math.abs(t) % 32 != 0) continue;
                int u = side == 0 ? -(H - 1) : side == 1 ? H - 1 : t, v = side == 2 ? -(H - 1) : side == 3 ? H - 1 : t;
                tower(f, city, u, v, 2, 10, sturdy);
            }
        for (int side = 0; side < 4; side++)
            for (int t = -5; t <= 5; t += 10) {
                int u = side == 0 ? -(H - 1) : side == 1 ? H - 1 : t, v = side == 2 ? -(H - 1) : side == 3 ? H - 1 : t;
                tower(f, city, u, v, 2, 10, sturdy);
            }
    }

    private static void tower(Frame f, Plans.City city, int cu, int cv, int r, int height, double sturdy) {
        if (!f.touches(cu - r, cv - r, cu + r, cv + r)) return;
        for (int u = cu - r; u <= cu + r; u++)
            for (int v = cv - r; v <= cv + r; v++) {
                if (!f.inside(u, v)) continue;
                f.footing(u, v, -1);
                f.masonry(u, 0, v);
                f.clear(u, v, 1, height + 2);
                if (Math.abs(u - cu) == r || Math.abs(v - cv) == r) f.wall(u, v, 1, height, Math.min(0.99, sturdy + 0.08));
            }
        // A doorway from inside the city.
        int du = cu == 0 ? 0 : -Integer.signum(cu), dv = cv == 0 ? 0 : -Integer.signum(cv);
        if (Math.abs(cu) >= Math.abs(cv)) f.clear(cu + du * r, cv, 1, 2); else f.clear(cu, cv + dv * r, 1, 2);
    }

    // ------------------------------------------------------------------ plaza

    private static void plaza(Frame f, Plans.City city) {
        if (!f.touches(-PLAZA, -PLAZA, PLAZA, PLAZA)) return;
        boolean broken = Hash.unit(city.hash, 1, 2, 16) < 0.4;
        for (int u = -4; u <= 4; u++)
            for (int v = -4; v <= 4; v++) {
                int m = Math.max(Math.abs(u), Math.abs(v));
                if (m == 4) { f.masonry(u, 1, v); continue; }
                f.masonry(u, -1, v);
                f.set(u, 0, v, WATER);
            }
        int top = broken ? 3 + Hash.range(city.hash, 0, 3) : 9;
        for (int y = 0; y <= top; y++) f.set(0, y, 0, BRICK, y == top && !broken ? 3 : 0);
        for (int su = -1; su <= 1; su += 2)
            for (int sv = -1; sv <= 1; sv += 2) {
                int u = su * 8, v = sv * 8;
                f.set(u, 1, v, BRICK, 3);
                f.set(u, 2, v, BRICK, 0);
                if (f.roll(u, 3, v, 17) < 0.6) {
                    f.set(u, 3, v, STONE, 0);
                    f.set(u, 4, v, STONE, 0);
                    if (f.roll(u, 5, v, 18) < 0.5) f.set(u, 5, v, STONE, 5);
                } else {
                    f.onGround(u + su, v, 1, STONE, 0);
                    f.onGround(u + su * 2, v, 1, STONE, 5);
                }
            }
    }

    // ------------------------------------------------------------------ lots

    private static void lot(Canvas c, Plans.City city, int cu, int cv, long h, double sturdy) {
        Frame f = new Frame(c, city.x + cu, city.z + cv, city.ground, Hash.range(Hash.mix(h ^ 1), 0, 3));
        double r = Hash.unit(h);
        if (r < 0.30) house(f, h, sturdy, false);
        else if (r < 0.35) house(f, h, sturdy, true);
        else if (r < 0.47) courtyard(f, h, sturdy);
        else if (r < 0.58) garden(f, h);
        else if (r < 0.69) rubble(f, h, sturdy);
        else if (r < 0.77) towerHouse(f, h, sturdy);
        else if (r < 0.85) shrine(f, h);
        else if (r < 0.91) cistern(f, h);
        else meadow(f, h);
    }

    /** A house of one or two storeys: floor, crumbling walls, windows, fallen roof; sometimes a vault below. */
    private static void house(Frame f, long h, double sturdy, boolean vault) {
        boolean tall = Hash.unit(h, 1, 0, 20) < 0.4;
        int height = tall ? 9 : 5;
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                if (!f.inside(lx, lz)) continue;
                boolean edge = Math.abs(lx) == 5 || Math.abs(lz) == 5;
                if (edge) {
                    f.wall(lx, lz, 1, height, sturdy);
                    if (f.roll(lx, 0, lz, 21) < 0.08) {
                        int dx = Math.abs(lx) == 5 ? Integer.signum(lx) : 0, dz = dx == 0 ? Integer.signum(lz) : 0;
                        f.vines(lx, height - 2, lz, dx, dz, 3);
                    }
                } else {
                    f.paving(lx, 0, lz);
                    if (tall && f.keep(lx, 5, lz, 0.45)) f.set(lx, 5, lz, PLANKS, 5);
                    if (f.keep(lx, height + 1, lz, 0.12)) f.set(lx, height, lz, SLAB, 5);
                }
            }
        f.clear(0, -5, 1, 2);
        for (int y = 2; y < height; y += 4)
            for (int k = -3; k <= 3; k += 6) { f.clear(k, 5, y, y + 1); f.clear(5, k, y, y + 1); f.clear(-5, k, y, y + 1); }
        if (f.roll(4, 4, 4, 22) < 0.5) f.set(4, 4, 4, WEB);
        if (Hash.unit(h, 2, 0, 23) < 0.35) f.chest(4, 1, 4, -1, 0, Sites.SMITH);
        if (!vault) return;
        // The vault: a ladder down through a hole in the floor.
        for (int lx = -4; lx <= 4; lx++)
            for (int lz = -4; lz <= 4; lz++) {
                boolean shell = Math.abs(lx) == 4 || Math.abs(lz) == 4;
                for (int y = -6; y <= -1; y++) {
                    if (y == -6 || y == -1 || shell) f.masonry(lx, y, lz); else f.set(lx, y, lz, AIR);
                }
            }
        for (int y = -5; y <= 0; y++) f.set(-3, y, 0, LADDER, f.facing(1, 0));
        f.set(-3, 1, 0, AIR);
        f.spawner(1, -5, 0, Hash.unit(h, 3, 0, 24) < 0.5 ? "SPIDER" : "SKELETON");
        f.chest(3, -5, 3, 0, -1, Sites.DUNGEON);
        f.chest(3, -5, -3, 0, 1, Sites.CORRIDOR);
        for (int k = -3; k <= 3; k += 6) if (f.roll(k, -2, k, 25) < 0.6) f.set(k, -2, -k, WEB);
    }

    /** Rooms around an open court with a well. */
    private static void courtyard(Frame f, long h, double sturdy) {
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                if (!f.inside(lx, lz)) continue;
                int m = Math.max(Math.abs(lx), Math.abs(lz));
                if (m == 5) f.wall(lx, lz, 1, 5, sturdy);
                else if (m == 2 && !(lz == -2 && lx == 0)) f.wall(lx, lz, 1, 4, sturdy);
                else f.paving(lx, 0, lz);
            }
        f.clear(0, -5, 1, 2);
        f.clear(5, 0, 1, 2);
        for (int lx = -1; lx <= 1; lx++)
            for (int lz = -1; lz <= 1; lz++) {
                if (lx == 0 && lz == 0) { for (int y = -4; y <= 0; y++) f.set(0, y, 0, WATER); f.set(0, -5, 0, MOSSY); }
                else { f.masonry(lx, 0, lz); f.masonry(lx, 1, lz); for (int y = -5; y < 0; y++) f.masonry(lx, y, lz); }
            }
        if (Hash.unit(h, 4, 0, 26) < 0.5) f.set(-1, 2, -1, WALL, 1);
    }

    /** An overgrown garden: two old trees, grass and flowers inside a low broken wall. */
    private static void garden(Frame f, long h) {
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                if (!f.inside(lx, lz)) continue;
                if (Math.max(Math.abs(lx), Math.abs(lz)) == 5) { if (f.keep(lx, 1, lz, 0.55)) f.rubble(lx, 1, lz); continue; }
                double r = f.roll(lx, 1, lz, 27);
                if (r < 0.25) f.set(lx, 1, lz, TALLGRASS, 1);
                else if (r < 0.32) f.set(lx, 1, lz, 38, (int) (f.roll(lx, 2, lz, 28) * 9));
                else if (r < 0.36) f.set(lx, 1, lz, TALLGRASS, 2);
            }
        tree(f, -2, -2, 5 + Hash.range(h, 0, 2));
        if (Hash.unit(h, 5, 0, 29) < 0.6) tree(f, 3, 2, 4 + Hash.range(Hash.mix(h), 0, 2));
    }

    /** A small tree drawn directly (no world access during generation): an oak trunk and a no-decay crown. */
    static void tree(Frame f, int lx, int lz, int height) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = height - 2; y <= height + 1; y++) {
                    int reach = y > height - 1 ? 1 : 2;
                    if (Math.abs(dx) > reach || Math.abs(dz) > reach) continue;
                    if (Math.abs(dx) == reach && Math.abs(dz) == reach && f.roll(lx + dx, y, lz + dz, 30) < 0.6) continue;
                    f.set(lx + dx, y, lz + dz, LEAVES, 4);
                }
        for (int y = 1; y <= height; y++) f.set(lx, y, lz, LOG, 0);
        f.set(lx, 0, lz, DIRT, 0);
    }

    /** A collapsed building: a mound of rubble with one corner still standing. */
    private static void rubble(Frame f, long h, double sturdy) {
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                if (!f.inside(lx, lz)) continue;
                double d = Math.sqrt(lx * lx + lz * lz);
                int mound = (int) Math.round(3.2 - d * 0.6 + f.roll(lx, 0, lz, 31) * 1.5);
                for (int y = 1; y <= mound; y++) f.rubble(lx, y, lz);
                if (mound < 1 && f.roll(lx, 1, lz, 32) < 0.2) f.set(lx, 1, lz, TALLGRASS, 1);
                if ((lx == 5 && lz >= 1) || (lz == 5 && lx >= 1)) f.wall(lx, lz, 1, 7, Math.min(0.99, sturdy + 0.1));
            }
    }

    /** A narrow tower house with floors, a ladder and a chest at the top. */
    private static void towerHouse(Frame f, long h, double sturdy) {
        int height = 13 + Hash.range(h, 0, 4);
        for (int lx = -3; lx <= 3; lx++)
            for (int lz = -3; lz <= 3; lz++) {
                if (!f.inside(lx, lz)) continue;
                if (Math.max(Math.abs(lx), Math.abs(lz)) == 3) f.wall(lx, lz, 1, height, Math.min(0.99, sturdy + 0.1));
                else {
                    f.paving(lx, 0, lz);
                    for (int y = 4; y < height - 1; y += 4) if (f.keep(lx, y, lz, 0.7)) f.set(lx, y, lz, PLANKS, 1);
                }
            }
        f.clear(0, -3, 1, 2);
        for (int y = 2; y < height - 1; y += 4) { f.clear(3, 0, y, y); f.clear(-3, 0, y, y); f.clear(0, 3, y, y); }
        for (int y = 1; y < height - 2; y++) f.set(0, y, 2, LADDER, f.facing(0, -1));
        int top = 4 * ((height - 2) / 4);
        f.set(-2, top, -2, PLANKS, 1);
        f.chest(-2, top + 1, -2, 1, 0, Sites.DUNGEON);
    }

    /** A little shrine: four columns on a platform, what is left of the lintel, a basin on a pedestal. */
    private static void shrine(Frame f, long h) {
        for (int lx = -3; lx <= 3; lx++)
            for (int lz = -3; lz <= 3; lz++) {
                if (!f.inside(lx, lz)) continue;
                f.masonry(lx, 1, lz);
                if (Math.max(Math.abs(lx), Math.abs(lz)) == 3 && f.keep(lx, 7, lz, 0.5)) f.masonry(lx, 7, lz);
            }
        for (int sx = -3; sx <= 3; sx += 6)
            for (int sz = -3; sz <= 3; sz += 6) f.pillar(sx, sz, 2, 4, f.roll(sx, 2, sz, 33) < 0.3);
        f.set(0, 2, 0, BRICK, 3);
        f.set(0, 3, 0, CAULDRON, 0);
        f.set(0, 1, -4, BRICK_STAIRS, f.stairs(0, 1, false));
    }

    /** A sunken cistern: a stepped pool inside a masonry rim. */
    private static void cistern(Frame f, long h) {
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                if (!f.inside(lx, lz)) continue;
                int m = Math.max(Math.abs(lx), Math.abs(lz));
                if (m == 5) { f.masonry(lx, 0, lz); if (f.keep(lx, 1, lz, 0.6)) f.masonry(lx, 1, lz); continue; }
                for (int y = -4; y <= 0; y++) {
                    if (y == -4 || m == 4) f.masonry(lx, y, lz);
                    else f.set(lx, y, lz, y <= -1 ? WATER : AIR);
                }
                if (m == 4) f.set(lx, 1, lz, AIR);
            }
        for (int k = 0; k <= 3; k++) f.set(0, -k, -4 + k, BRICK_STAIRS, f.stairs(0, -1, false));
    }

    /** An empty lot: meadow grass and the base of something long gone. */
    private static void meadow(Frame f, long h) {
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                double r = f.roll(lx, 1, lz, 34);
                if (r < 0.3) f.set(lx, 1, lz, TALLGRASS, 1);
                else if (r < 0.34) f.set(lx, 1, lz, 37, 0);
            }
        if (Hash.unit(h, 6, 0, 35) < 0.5) { f.set(0, 1, 0, BRICK, 3); f.set(0, 2, 0, STONE, 0); }
    }
}
