package chat.jaspr.ruins;

import static chat.jaspr.ruins.Canvas.*;

/**
 * The original wilderness ruins. Each is drawn in its own rotated frame with the floor at the site's base; the canvas
 * keeps only the blocks of the chunk being generated, and every decision is a function of position, so a ruin that
 * spans several chunks comes out whole.
 */
final class Sites {
    private Sites() {}

    static final String JUNGLE = "minecraft:chests/jungle_temple", DESERT = "minecraft:chests/desert_pyramid",
        DUNGEON = "minecraft:chests/simple_dungeon", LIBRARY = "minecraft:chests/stronghold_library",
        CORRIDOR = "minecraft:chests/stronghold_corridor", SMITH = "minecraft:chests/village_blacksmith";

    static void draw(Plans.Site s, Canvas c) {
        Frame f = new Frame(c, s.x, s.z, s.base, s.rot);
        int r = s.kind.radius;
        if (!c.touches(s.x - r - 1, s.z - r - 1, s.x + r + 1, s.z + r + 1)) return;
        switch (s.kind) {
            case TEMPLE: temple(f, s.hash, 7, 11, 2, 6, false); break;
            case COLONNADE: colonnade(f, s.hash); break;
            case ZIGGURAT: ziggurat(f, s.hash); break;
            case WATCHTOWER: watchtower(f, s.hash); break;
            case AQUEDUCT: aqueduct(f, s.hash); break;
            case AMPHITHEATER: amphitheater(f, s.hash); break;
            case STONES: stones(f, s.hash); break;
            case CRYPT: crypt(f, s.hash); break;
            case GATEHOUSE: gatehouse(f, s.hash); break;
            case COLOSSUS: colossus(f, s.hash); break;
            case SANCTUM: case MONOLITHS: case PIT: case POOL: case CHAPEL: Cult.draw(s, c); break;
            case FORTRESS: case LABYRINTH: case OSSUARY: case DEEP_TEMPLE: case OBSERVATORY: case GREAT_IDOL: Dungeons.draw(s, c); break;
            case BELFRY: case CLOISTER: case SCRIPTORIUM: Remnants.draw(s, c); break;
            case NECROPOLIS: case UNDERCROFT: case OUBLIETTE: case KINGS_HALL: Undercrofts.draw(s, c); break;
            case SUNKEN_TEMPLE: case WRECK: case LIGHTHOUSE: case TIDE_SHRINE: Shallows.draw(s, c); break;
            default: break;
        }
    }

    private static int sign(int v) { return v < 0 ? -1 : 1; }

    /**
     * A peripteral temple on a stepped platform: floor half-size (fx, fz), {@code tiers} steps around it, columns
     * {@code ch} tall, a walled cella with an altar and a chest. Also the landmark of the old cities (grand).
     */
    static void temple(Frame f, long h, int fx, int fz, int tiers, int ch, boolean grand) {
        int ox = fx + tiers, oz = fz + tiers, top = ch + 4;
        if (!f.touches(-ox, -oz, ox, oz)) return;
        for (int lx = -ox; lx <= ox; lx++)
            for (int lz = -oz; lz <= oz; lz++) {
                if (!f.inside(lx, lz)) continue;
                int ring = Math.max(0, Math.max(Math.abs(lx) - fx, Math.abs(lz) - fz)), y = -ring;
                f.footing(lx, lz, y - 1);
                if (ring == 0) f.paving(lx, 0, lz); else f.masonry(lx, y, lz);
                f.clear(lx, lz, y + 1, top + 2);
            }
        for (int k = 1; k <= tiers; k++)
            for (int lx = -3; lx <= 3; lx++) f.set(lx, 1 - k, -fz - k, BRICK_STAIRS, f.stairs(0, 1, false));
        // Peristyle: a column every second block around the floor; some broken, a few only stumps.
        for (int lx = -fx; lx <= fx; lx++)
            for (int lz = -fz; lz <= fz; lz++) {
                boolean ring = Math.abs(lx) == fx || Math.abs(lz) == fz;
                if (!ring || ((lx + fx) & 1) != 0 || ((lz + fz) & 1) != 0 || !f.inside(lx, lz)) continue;
                double r = f.roll(lx, 0, lz, 21);
                if (r < 0.12) f.set(lx, 1, lz, BRICK, 3);
                else f.pillar(lx, lz, 1, ch, r < 0.4);
                if (r >= 0.4 && f.roll(lx, 1, lz, 22) < 0.35) {
                    int dx = Math.abs(lx) == fx ? sign(lx) : 0, dz = dx == 0 ? sign(lz) : 0;
                    f.vines(lx, ch, lz, dx, dz, 2 + (int) (f.roll(lx, 2, lz, 23) * 4));
                }
            }
        // Architrave: surviving stretches of four blocks, only where the columns under both ends still stand.
        for (int lx = -fx; lx <= fx; lx++)
            for (int lz = -fz; lz <= fz; lz++) {
                if (Math.abs(lx) != fx && Math.abs(lz) != fz || !f.inside(lx, lz)) continue;
                int p = Math.abs(lz) == fz ? lx + (lz < 0 ? 0 : 1000) : lz + (lx < 0 ? 2000 : 3000);
                if (Hash.unit(h, Math.floorDiv(p, 4), 77, 0) >= 0.5) continue;
                boolean alongX = Math.abs(lz) == fz;
                int a = alongX ? lx : lz, lo = a - Math.floorMod(a + (alongX ? fx : fz), 2), hi = lo + 2;
                int lim = alongX ? fx : fz;
                boolean ok = true;
                for (int c : new int[] {lo, Math.min(hi, lim)}) {
                    int cx = alongX ? c : lx, cz = alongX ? lz : c;
                    if (f.roll(cx, 0, cz, 21) < 0.4) ok = false;
                }
                if (ok) f.masonry(lx, ch + 2, lz);
            }
        // Cella with a doorway at the front, the altar and the offering chest at the back.
        int cw = fx - 3, cz0 = -fz + 5, cz1 = fz - 2;
        for (int lx = -cw; lx <= cw; lx++)
            for (int lz = cz0; lz <= cz1; lz++) {
                if (!f.inside(lx, lz)) continue;
                if (Math.abs(lx) == cw || lz == cz0 || lz == cz1) {
                    f.wall(lx, lz, 1, ch, 0.97);
                    if (lz == cz0 && Math.abs(lx) <= 1) f.clear(lx, lz, 1, grand ? 5 : 4);
                } else if (grand && f.roll(lx, ch + 3, lz, 24) < 0.25) {
                    f.set(lx, ch + 3, lz, SLAB, 5);   // what is left of the roof
                }
            }
        for (int lx = -1; lx <= 1; lx++) f.set(lx, 1, cz1 - 3, BRICK, 3);
        f.chest(0, 1, cz1 - 1, 0, -1, JUNGLE);
        if (grand) {
            f.chest(-cw + 1, 1, cz1 - 1, 1, 0, LIBRARY);
            f.set(cw - 1, 1, cz1 - 1, CAULDRON, 0);
        }
        for (int sx = -1; sx <= 1; sx += 2)
            if (f.roll(sx, ch, cz1, 25) < 0.6) f.set(sx * (cw - 1), ch - 1, cz1 - 1, WEB);
    }

    /** A paved avenue between two rows of columns; some lintels still span it, some columns lie where they fell. */
    static void colonnade(Frame f, long h) {
        if (!f.touches(-14, -21, 14, 21)) return;
        for (int lx = -6; lx <= 6; lx++)
            for (int lz = -20; lz <= 20; lz++) {
                if (!f.inside(lx, lz)) continue;
                f.footing(lx, lz, -1);
                if (Math.abs(lx) <= 3) f.paving(lx, 0, lz); else f.masonry(lx, 0, lz);
                f.clear(lx, lz, 1, 12);
            }
        for (int k = 0; k <= 8; k++) {
            int lz = -16 + 4 * k;
            boolean[] standing = new boolean[2];
            for (int i = 0; i < 2; i++) {
                int side = i == 0 ? -1 : 1, lx = 5 * side;
                double r = f.roll(lx, 0, lz, 31);
                standing[i] = r >= 0.45;
                if (r < 0.22) {
                    f.set(lx, 1, lz, BRICK, 3);
                    for (int d = 2; d <= 8; d++) f.masonryOnGround(lx + side * d, lz, 1);   // fallen outward
                } else {
                    f.pillar(lx, lz, 1, 7, r < 0.45);
                }
            }
            if (standing[0] && standing[1] && Hash.unit(h, k, 0, 33) < 0.6)
                for (int lx = -5; lx <= 5; lx++) f.masonry(lx, 9, lz);
        }
        for (int end = -1; end <= 1; end += 2) {
            int lz = 19 * end;
            for (int lx = -1; lx <= 1; lx++) f.masonry(lx, 1, lz);
            f.set(0, 2, lz, BRICK, 3);
            f.set(0, 3, lz, STONE, 0);
            if (Hash.unit(h, end, 1, 34) < 0.5) { f.set(0, 4, lz, STONE, 0); f.set(0, 5, lz, STONE, 5); }
        }
    }

    /** A stepped pyramid: five tiers, a stair up the front, a shrine on top and a guarded chamber inside. */
    static void ziggurat(Frame f, long h) {
        if (!f.touches(-15, -15, 15, 15)) return;
        for (int lx = -14; lx <= 14; lx++)
            for (int lz = -14; lz <= 14; lz++) {
                if (!f.inside(lx, lz)) continue;
                int m = Math.max(Math.abs(lx), Math.abs(lz));
                if (m == 14) { f.footing(lx, lz, -1); f.paving(lx, 0, lz); f.clear(lx, lz, 1, 22); continue; }
                int top = ((13 - m) / 3 + 1) * 3;
                boolean edge = (13 - m) % 3 == 0;
                f.footing(lx, lz, 0);
                f.masonry(lx, 0, lz);
                for (int y = 1; y <= top; y++) {
                    if (y == top && edge && !f.keep(lx, y, lz, 0.75)) continue;
                    f.masonry(lx, y, lz);
                }
                f.clear(lx, lz, top + 1, 22);
                if (edge && m > 1 && f.roll(lx, top, lz, 42) < 0.22) {
                    int dx = Math.abs(lx) == m ? sign(lx) : 0, dz = dx == 0 ? sign(lz) : 0;
                    f.vines(lx, top, lz, dx, dz, 2 + (int) (f.roll(lx, 0, lz, 43) * 2));
                }
            }
        // The front stair, cut into the tiers.
        for (int lz = -15; lz <= -2; lz++) {
            int y = 16 + lz;
            for (int lx = -1; lx <= 1; lx++) {
                if (!f.inside(lx, lz)) continue;
                f.footing(lx, lz, 0);
                for (int yy = 1; yy < y; yy++) f.masonry(lx, yy, lz);
                f.set(lx, y, lz, BRICK_STAIRS, f.stairs(0, 1, false));
                f.clear(lx, lz, y + 1, y + 4);
            }
        }
        // The chamber, reached by a tunnel from the back.
        for (int lx = -3; lx <= 3; lx++)
            for (int lz = -3; lz <= 3; lz++) {
                f.clear(lx, lz, 1, 4);
                f.paving(lx, 0, lz);
                if (Math.abs(lx) == 3 && Math.abs(lz) == 3 && f.roll(lx, 4, lz, 44) < 0.7) f.set(lx, 4, lz, WEB);
            }
        for (int lx = -1; lx <= 1; lx++)
            for (int lz = 4; lz <= 13; lz++) f.clear(lx, lz, 1, 3);
        f.spawner(0, 1, 0, "ZOMBIE");
        f.chest(-3, 1, -3, 0, 1, DUNGEON);
        // The shrine on the summit.
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) f.pillar(sx, sz, 16, 2, f.roll(sx, 16, sz, 45) < 0.3);
        f.chest(0, 16, 0, 0, -1, DESERT);
        for (int lx = -1; lx <= 1; lx++)
            for (int lz = -1; lz <= 1; lz++) if (f.roll(lx, 19, lz, 46) < 0.5) f.set(lx, 19, lz, SLAB, 5);
    }

    /** A square watchtower with a broken crown, floors, a ladder and a chest. */
    static void watchtower(Frame f, long h) {
        if (!f.touches(-6, -6, 6, 6)) return;
        int height = 13 + Hash.range(h, 0, 8);
        for (int lx = -5; lx <= 5; lx++)
            for (int lz = -5; lz <= 5; lz++) {
                if (!f.inside(lx, lz)) continue;
                int m = Math.max(Math.abs(lx), Math.abs(lz));
                if (m <= 3) {
                    f.footing(lx, lz, -1);
                    f.masonry(lx, 0, lz);
                    f.clear(lx, lz, 1, height + 2);
                    if (m == 3) f.wall(lx, lz, 1, height, 0.99);
                    else for (int y = 5; y < height - 1; y += 5) if (f.keep(lx, y, lz, 0.65)) f.set(lx, y, lz, PLANKS, 1);
                } else if (f.roll(lx, 0, lz, 41) < 0.3) {
                    f.rubbleOnGround(lx, lz, 1);
                }
            }
        f.clear(0, -3, 1, 2);
        for (int y = 3; y < height - 2; y += 4) {
            f.clear(0, 3, y, y);
            f.clear(3, 0, y, y);
            f.clear(-3, 0, y, y);
            if (y > 4) f.clear(0, -3, y, y);
        }
        for (int y = 1; y < Math.min(height - 2, 16); y++) f.set(0, y, 2, LADDER, f.facing(0, -1));
        f.set(-2, 5, -2, PLANKS, 1);
        f.chest(-2, 6, -2, 1, 0, SMITH);
    }

    /** A line of arches that once carried water across the land; some spans and piers have fallen. */
    static void aqueduct(Frame f, long h) {
        if (!f.touches(-25, -2, 25, 2)) return;
        for (int lx = -24; lx <= 25; lx++) {
            int k = Math.floorDiv(lx + 24, 6), off = lx + 24 - 6 * k;
            boolean pier = off <= 1;
            boolean down = pier ? pierBroken(h, k) : spanDown(h, k);
            for (int lz = -1; lz <= 1; lz++) {
                if (!f.inside(lx, lz)) continue;
                if (pier) {
                    int g = f.ground(lx, lz), top = down ? -4 - Hash.range(Hash.of(h, k, 53), 1, 6) : 0;
                    for (int y = Math.max(1, g - 1); y <= f.base + top; y++) f.c.masonry(f.wx(lx, lz), y, f.wz(lx, lz));
                    if (down && f.roll(lx, 0, lz, 56) < 0.4) f.rubbleOnGround(lx + (lz == 0 ? 1 : 0), lz, 1);
                } else if (down) {
                    if (f.roll(lx, 0, lz, 54) < 0.45) f.rubbleOnGround(lx, lz, 1);
                    if (f.roll(lx, 1, lz, 54) < 0.15) f.rubbleOnGround(lx, lz, 2);
                    continue;
                } else {
                    f.masonry(lx, -1, lz);
                    f.masonry(lx, -2, lz);
                    if (off == 2 || off == 5) f.masonry(lx, -3, lz);
                }
                if (pier && down) continue;
                f.masonry(lx, 0, lz);
                if (lz != 0) {
                    if (f.keep(lx, 1, lz, 0.85)) f.masonry(lx, 1, lz);
                    if (f.roll(lx, 0, lz, 57) < 0.2) f.vines(lx, 0, lz, 0, lz, 2 + (int) (f.roll(lx, 1, lz, 58) * 4));
                } else if (f.roll(lx, 1, lz, 55) < 0.25) {
                    f.rubble(lx, 1, lz);
                }
                f.clear(lx, lz, 2, 4);
            }
        }
    }

    private static boolean pierBroken(long h, int k) { return Hash.unit(h, k, 1, 52) < 0.2; }
    private static boolean spanDown(long h, int k) { return Hash.unit(h, k, 0, 51) < 0.3 || pierBroken(h, k) || pierBroken(h, k + 1); }

    /** Rings of seats around a sandy arena inside an arcaded wall; one side has slumped into rubble. */
    static void amphitheater(Frame f, long h) {
        if (!f.touches(-17, -17, 17, 17)) return;
        double sector = Hash.unit(Hash.mix(h ^ 61)) * 360;
        for (int lx = -17; lx <= 17; lx++)
            for (int lz = -17; lz <= 17; lz++) {
                if (!f.inside(lx, lz)) continue;
                double d = Math.sqrt(lx * lx + lz * lz);
                if (d > 17.4) continue;
                int r = (int) Math.round(d);
                double ang = (Math.toDegrees(Math.atan2(lz, lx)) + 360) % 360;
                boolean ruined = Math.abs(((ang - sector) % 360 + 540) % 360 - 180) < 50;
                boolean gate = Math.abs(lz) <= 1;
                if (r <= 6) {
                    f.footing(lx, lz, -1);
                    f.set(lx, 0, lz, f.roll(lx, 0, lz, 62) < 0.7 ? SAND : GRAVEL);
                    f.clear(lx, lz, 1, 14);
                } else if (r <= 15) {
                    int seat = r - 6;
                    f.footing(lx, lz, 0);
                    int solid = ruined ? Math.max(0, seat - 1 - (int) (f.roll(lx, 0, lz, 63) * 4)) : seat - 1;
                    for (int y = 0; y <= solid; y++) f.masonry(lx, y, lz);
                    if (!ruined) {
                        int dx = Math.abs(lx) >= Math.abs(lz) ? sign(lx) : 0, dz = dx == 0 ? sign(lz) : 0;
                        f.set(lx, seat, lz, BRICK_STAIRS, f.stairs(dx, dz, false));
                        f.clear(lx, lz, seat + 1, 14);
                    } else {
                        if (f.roll(lx, 1, lz, 64) < 0.5) f.rubble(lx, solid + 1, lz);
                        f.clear(lx, lz, solid + 2, 14);
                    }
                    if (gate) f.clear(lx, lz, 1, 3);
                } else {
                    f.footing(lx, lz, 0);
                    f.wall(lx, lz, 1, 11, ruined ? 0.35 : 0.97);
                    if (gate || ang % 20 < 5) f.clear(lx, lz, 1, 4);
                }
            }
        for (int lx = -1; lx <= 1; lx++) f.masonry(lx, 1, -5);
        f.chest(0, 1, -6, 0, 1, DUNGEON);
    }

    /** A ring of standing stones with two trilithons and an altar; something is buried beneath the altar. */
    static void stones(Frame f, long h) {
        if (!f.touches(-10, -10, 10, 10)) return;
        for (int lx = -9; lx <= 9; lx++)
            for (int lz = -9; lz <= 9; lz++) {
                double d = Math.sqrt(lx * lx + lz * lz);
                if (d > 6.5 && d < 8.8 && f.roll(lx, 0, lz, 74) < 0.4) f.onGround(lx, lz, 0, DIRT, 2);
            }
        int n = 9 + Hash.range(h, 0, 4);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n + (Hash.unit(h, i, 1, 71) - 0.5) * 0.25;
            int px = (int) Math.round(7.5 * Math.cos(a)), pz = (int) Math.round(7.5 * Math.sin(a));
            double tx = -Math.sin(a), tz = Math.cos(a);
            int tdx = Math.abs(tx) > Math.abs(tz) ? (tx < 0 ? -1 : 1) : 0, tdz = tdx == 0 ? (tz < 0 ? -1 : 1) : 0;
            int rdx = tdz != 0 ? (Math.cos(a) < 0 ? -1 : 1) : 0, rdz = rdx == 0 ? (Math.sin(a) < 0 ? -1 : 1) : 0;
            int height = 3 + Hash.range(Hash.of(h, i, 72), 0, 3);
            boolean fallen = Hash.unit(h, i, 2, 73) < 0.2;
            for (int k = 0; k < 2; k++) {
                int sx = px + tdx * k, sz = pz + tdz * k;
                if (!fallen) for (int y = 1; y <= height; y++) stone(f, sx, y, sz, true);
                else for (int d = 0; d < height; d++) stone(f, sx + rdx * d, 1, sz + rdz * d, true);
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int lz = -1; lz <= 1; lz += 2) {
                f.footing(3 * side, lz, 0);
                for (int y = 1; y <= 3; y++) stone(f, 3 * side, y, lz, false);
            }
            for (int lz = -1; lz <= 1; lz++) if (f.keep(3 * side, 4, lz, 0.8)) stone(f, 3 * side, 4, lz, false);
        }
        for (int lx = -1; lx <= 1; lx++) {
            f.footing(lx, 0, 0);
            f.set(lx, 1, 0, lx == 0 ? BRICK : DOUBLE_SLAB, lx == 0 ? 3 : 5);
            f.clear(lx, 0, 2, 4);
        }
        f.chest(0, 0, 0, 0, -1, JUNGLE);   // under the altar stone: it has to be moved first
    }

    private static void stone(Frame f, int lx, int y, int lz, boolean onGround) {
        double r = f.roll(lx, y, lz, 75);
        int id = r < 0.45 ? MOSSY : r < 0.8 ? STONE : BRICK, meta = r < 0.45 ? 0 : r < 0.8 ? 0 : 1;
        if (onGround) f.onGround(lx, lz, y, id, meta); else f.set(lx, y, lz, id, meta);
    }

    /** A mausoleum over a stair that descends into a pillared vault with sarcophagi, a spawner and two chests. */
    static void crypt(Frame f, long h) {
        if (!f.touches(-8, -8, 8, 8)) return;
        for (int lx = -7; lx <= 7; lx++)
            for (int lz = -7; lz <= 8; lz++) {
                if (!f.inside(lx, lz)) continue;
                boolean shell = Math.abs(lx) == 7 || lz == -7 || lz == 8;
                for (int y = -10; y <= -3; y++) {
                    if (y == -10 || y == -3 || shell) f.masonry(lx, y, lz); else f.set(lx, y, lz, AIR);
                }
                if (y4corner(lx, lz) && f.roll(lx, -4, lz, 82) < 0.4) f.set(lx, -4, lz, WEB);
            }
        for (int sx = -3; sx <= 3; sx += 6)
            for (int sz = -3; sz <= 4; sz += 7)
                for (int y = -9; y <= -4; y++) f.masonry(sx, y, sz);
        for (int sx = -5; sx <= 5; sx += 10)
            for (int sz = -4; sz <= 4; sz += 7) {
                f.set(sx, -9, sz, DOUBLE_SLAB, 5);
                f.set(sx, -9, sz + 1, DOUBLE_SLAB, 5);
                f.set(sx, -8, sz, SLAB, 5);
                f.set(sx, -8, sz + 1, SLAB, 5);
            }
        f.spawner(-4, -9, 0, Hash.unit(Hash.mix(h ^ 81)) < 0.5 ? "SKELETON" : "ZOMBIE");
        f.chest(6, -9, -6, -1, 0, DUNGEON);
        f.chest(-6, -9, 7, 1, 0, CORRIDOR);
        // The mausoleum.
        for (int lx = -3; lx <= 3; lx++)
            for (int lz = -4; lz <= 4; lz++) {
                if (!f.inside(lx, lz)) continue;
                f.footing(lx, lz, -1);
                f.masonry(lx, 0, lz);
                f.clear(lx, lz, 1, 8);
                if (Math.abs(lx) == 3 || Math.abs(lz) == 4) {
                    f.wall(lx, lz, 1, 5, 0.99);
                    if (lx == 0 && lz == -4) f.clear(lx, lz, 1, 3);
                }
                if (f.keep(lx, 6, lz, 0.7)) f.set(lx, 6, lz, SLAB, 5);
            }
        for (int sx = -2; sx <= 2; sx += 4) {
            f.footing(sx, -5, 0);
            f.pillar(sx, -5, 1, 4, false);
        }
        for (int lx = -2; lx <= 2; lx++) if (f.keep(lx, 6, -5, 0.8)) f.set(lx, 6, -5, SLAB, 5);
        // The stair down into the vault, rising toward the door.
        for (int k = 1; k <= 9; k++) {
            int y = -k, lz = k - 3, head = Math.min(y + 3, 0);
            for (int yy = Math.max(y, -3); yy <= head; yy++) { f.masonry(-1, yy, lz); f.masonry(1, yy, lz); }   // shaft walls in the earth
            f.clear(0, lz, y + 1, head);
            if (y <= -4) for (int yy = -9; yy < y; yy++) f.masonry(0, yy, lz);   // the stair's spine inside the vault
            f.set(0, y, lz, BRICK_STAIRS, f.stairs(0, -1, false));
        }
    }

    private static boolean y4corner(int lx, int lz) { return (Math.abs(lx) == 6 || Math.abs(lx) == 1) && (lz == -6 || lz == 7); }

    /** A lone gate between two towers with a rusted portcullis and stubs of the wall it once belonged to. */
    static void gatehouse(Frame f, long h) {
        if (!f.touches(-13, -13, 13, 13)) return;
        int height = 11 + Hash.range(h, 0, 4);
        for (int lx = -2; lx <= 2; lx++)
            for (int lz = -12; lz <= 12; lz++) {
                if (!f.inside(lx, lz)) continue;
                f.footing(lx, lz, -1);
                f.paving(lx, 0, lz);
                f.clear(lx, lz, 1, 14);
            }
        for (int side = -1; side <= 1; side += 2) {
            for (int a = 3; a <= 7; a++)
                for (int lz = -2; lz <= 2; lz++) {
                    int lx = side * a;
                    if (!f.inside(lx, lz)) continue;
                    f.footing(lx, lz, -1);
                    f.masonry(lx, 0, lz);
                    f.clear(lx, lz, 1, height + 2);
                    if (a == 3 || a == 7 || Math.abs(lz) == 2) f.wall(lx, lz, 1, height, 0.97);
                }
            f.clear(side * 3, 0, 1, 2);
            for (int a = 8; a <= 13; a++)
                for (int lz = -1; lz <= 1; lz++) {
                    int lx = side * a;
                    if (!f.inside(lx, lz)) continue;
                    f.footing(lx, lz, -1);
                    f.masonry(lx, 0, lz);
                    f.wall(lx, lz, 1, 7, 0.95 - (a - 8) * 0.1);
                }
        }
        for (int lx = -2; lx <= 2; lx++)
            for (int lz = -2; lz <= 2; lz++)
                for (int y = 5; y <= 9; y++) {
                    if (y == 5 && Math.abs(lx) <= 1) continue;
                    if (f.keep(lx, y, lz, 1 - (y - 5) * 0.08)) f.masonry(lx, y, lz);
                }
        for (int lx = -2; lx <= 2; lx++)
            for (int y = 3; y <= 5; y++)
                if ((y < 5 || Math.abs(lx) <= 1) && f.keep(lx, y, 0, 0.75)) f.set(lx, y, 0, BARS);
    }

    /** A colossal statue: only the legs still stand on the plinth; the head lies face-up in the grass. */
    static void colossus(Frame f, long h) {
        if (!f.touches(-11, -11, 11, 11)) return;
        for (int lx = -3; lx <= 3; lx++)
            for (int lz = -2; lz <= 2; lz++) {
                if (!f.inside(lx, lz)) continue;
                f.footing(lx, lz, 0);
                f.masonry(lx, 1, lz);
                f.masonry(lx, 2, lz);
                f.set(lx, 3, lz, BRICK, 3);
                f.clear(lx, lz, 4, 20);
            }
        for (int side = -1; side <= 1; side += 2) {
            int legHeight = 6 + Hash.range(Hash.of(h, side, 91), 0, 3);
            for (int a = 1; a <= 2; a++) {
                int lx = side * a;
                for (int lz = -2; lz <= 0; lz++) f.set(lx, 4, lz, STONE, 0);
                for (int lz = -1; lz <= 0; lz++)
                    for (int y = 5; y <= 4 + legHeight; y++) {
                        if (y > 2 + legHeight && !f.keep(lx, y, lz, 0.55)) break;
                        double r = f.roll(lx, y, lz, 92);
                        f.set(lx, y, lz, STONE, r < 0.6 ? 0 : r < 0.85 ? 5 : 6);
                    }
            }
        }
        for (int lx = 5; lx <= 9; lx++)
            for (int lz = 2; lz <= 6; lz++) {
                if (!f.inside(lx, lz)) continue;
                f.footing(lx, lz, 0);
                for (int y = 1; y <= 5; y++) {
                    double r = f.roll(lx, y, lz, 93);
                    f.set(lx, y, lz, r < 0.2 ? MOSSY : STONE, r < 0.2 ? 0 : r < 0.75 ? 0 : 5);
                }
                f.clear(lx, lz, 6, 10);
            }
        f.set(6, 5, 3, 159, 15);
        f.set(8, 5, 3, 159, 15);
        f.set(7, 6, 4, STONE, 6);
        for (int lx = 6; lx <= 8; lx++) f.set(lx, 5, 5, 159, 7);
        for (int lx = -8; lx <= -4; lx++)
            for (int lz = 5; lz <= 6; lz++) { f.onGround(lx, lz, 1, STONE, 0); if (lx > -8) f.onGround(lx, lz, 2, STONE, 5); }
        for (int lx = -10; lx <= 10; lx++)
            for (int lz = -10; lz <= 10; lz++) {
                boolean plinth = Math.abs(lx) <= 3 && Math.abs(lz) <= 2, head = lx >= 5 && lx <= 9 && lz >= 2 && lz <= 6;
                if (!plinth && !head && f.roll(lx, 0, lz, 94) < 0.06) f.rubbleOnGround(lx, lz, 1);
            }
    }
}
