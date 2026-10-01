package chat.jaspr.nether;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Nether's colossal structures (owner, 2026-10-01: "three new mega structures ... huge in scope", each with a
 * boss): the Great Pyramid and the Caldera Citadel (the third, the Endless Catacombs, lies under everything, see
 * {@link Depths}). One site per 1152 x 1152-block cell, the two kinds alternating like a chessboard, each hollowing a
 * cavern some 350 blocks across that reaches from just above the lava sea to the Nether's roof.
 * <p>
 * They are planned after the Nether Cities (which keep their places) and before everything else, without moving
 * anything already decided: a colossus is built only where all of its land is new (no chunk of its box existed before
 * this version, see {@link History}) and no recorded structure lies in its box; a mega structure or GLM build planned
 * in its box later gives way to it (decided once, when its first chunk is reached, like every site).
 */
final class Colossi {
    static final int CELL = 1152;
    static final int REACH = 184;     // nothing a site draws is farther than this from its centre (horizontally)
    static final int MARGIN = 196;    // a site's centre keeps this far from its cell's edges

    enum Kind {
        PYRAMID("great_pyramid", "The Great Pyramid", 176, 32, 88, 104),
        CITADEL("caldera_citadel", "The Caldera Citadel", 178, 32, 88, 156);

        final String id, display;
        /** Cavern radius, floor level, dome height above the floor at the centre, and the dry core radius (no lakes). */
        final int radius, floor, dome, core;
        Kind(String id, String display, int radius, int floor, int dome, int core) {
            this.id = id; this.display = display; this.radius = radius; this.floor = floor; this.dome = dome; this.core = core;
        }
        static Kind byId(String id) { for (Kind k : values()) if (k.id.equals(id)) return k; return null; }
    }

    /** One planned site; every shape query is a pure function of the seed, so every chunk agrees on it. */
    static final class Site {
        final Kind kind; final int cellX, cellZ, x, y, z, salt; final long seed;
        final int minX, minZ, maxX, maxZ;
        Object plan;
        Site(Kind kind, int cellX, int cellZ, int x, int z, long seed) {
            this.kind = kind; this.cellX = cellX; this.cellZ = cellZ; this.x = x; this.z = z; this.y = kind.floor; this.seed = seed;
            this.salt = (int) (seed ^ (seed >>> 32));
            minX = x - REACH; maxX = x + REACH; minZ = z - REACH; maxZ = z + REACH;
        }
        double dist(double bx, double bz) { double dx = bx - x, dz = bz - z; return Math.sqrt(dx * dx + dz * dz); }
        double edge(int bx, int bz) { return kind.radius + Draw.fbm(bx, bz, 60, salt) * 12 + Draw.noise(bx, bz, 13, salt + 3) * 3; }
        /** 0 at the centre, 1 at the cavern wall. */
        double f(int bx, int bz) { return dist(bx, bz) / edge(bx, bz); }
        int floorAt(int bx, int bz) {
            double f = f(bx, bz);
            double dune = Draw.fbm(bx, bz, 34, salt + 5) * (kind == Kind.PYRAMID ? 2.6 : 1.2);
            double rim = f > 0.82 ? Math.pow((f - 0.82) / 0.18, 1.6) * 14 : 0;
            return y + (int) Math.round(dune + rim);
        }
        int ceilAt(int bx, int bz) {
            double f = Math.min(1, f(bx, bz));
            double h = kind.dome * Math.sqrt(1 - f * f) * (1 - f * 0.2 * (0.5 - 0.5 * Draw.noise(bx, bz, 19, salt + 9)));
            return Math.min(120, y + (int) Math.round(h));
        }
        boolean inside(int bx, int bz) { return f(bx, bz) < 1 && ceilAt(bx, bz) - floorAt(bx, bz) >= 3; }
        Random random() { return new Random(seed); }
        String name() { return kind.display; }
        /** The registry box: the whole reach, from under the floor to the top of the dome. */
        int[] box() { return new int[]{minX, y - 8, minZ, maxX, Math.min(122, y + kind.dome + 3), maxZ}; }
    }

    interface Keepout { boolean blocked(int x0, int z0, int x1, int z1); }

    private static final Site NONE = new Site(Kind.PYRAMID, 0, 0, 0, 0, 0);
    static final ColossusDesign[] DESIGNS = new ColossusDesign[Kind.values().length];
    static {
        DESIGNS[Kind.PYRAMID.ordinal()] = new ColossusPyramid();
        DESIGNS[Kind.CITADEL.ordinal()] = new ColossusCitadel();
    }
    static ColossusDesign design(Kind k) { return DESIGNS[k.ordinal()]; }

    final long seed;
    private final Keepout keepout;
    private final Map<Long, Site> cache = new LinkedHashMap<Long, Site>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Site> e) { return size() > 128; }
    };

    Colossi(long seed, Keepout keepout) { this.seed = seed; this.keepout = keepout; }

    /** The planned site of a cell, or null (every candidate spot fell inside a Nether City's reach). */
    synchronized Site site(int cellX, int cellZ) {
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        Site s = cache.get(key);
        if (s == null) {
            s = plan(cellX, cellZ);
            cache.put(key, s == null ? NONE : s);
            return s;
        }
        return s == NONE ? null : s;
    }

    /** The kinds alternate like a chessboard (which colour is which depends on the seed). */
    Kind kindOf(int cellX, int cellZ) {
        int parity = (int) ((cellX + cellZ + (seed >>> 17)) & 1);
        return parity == 0 ? Kind.PYRAMID : Kind.CITADEL;
    }

    private Site plan(int cellX, int cellZ) {
        Random r = new Random(seed ^ (cellX * 0x2545F4914F6CDD1DL + cellZ * 0x5DEECE66DL) ^ 0x434F4C4FL);
        Kind kind = kindOf(cellX, cellZ);
        int span = CELL - 2 * MARGIN;
        for (int t = 0; t < 16; t++) {
            int x = cellX * CELL + MARGIN + r.nextInt(span), z = cellZ * CELL + MARGIN + r.nextInt(span);
            long siteSeed = r.nextLong();
            if (keepout != null && keepout.blocked(x - REACH, z - REACH, x + REACH, z + REACH)) continue;
            return prepared(new Site(kind, cellX, cellZ, x, z, siteSeed));
        }
        return null;
    }

    static Site prepared(Site s) {
        s.plan = design(s.kind).plan(s, s.random());
        return s;
    }

    /** Planned sites whose reach overlaps the block box. */
    List<Site> touching(int x0, int z0, int x1, int z1) {
        List<Site> out = new ArrayList<>(1);
        for (int cx = Math.floorDiv(x0 - REACH, CELL); cx <= Math.floorDiv(x1 + REACH, CELL); cx++)
            for (int cz = Math.floorDiv(z0 - REACH, CELL); cz <= Math.floorDiv(z1 + REACH, CELL); cz++) {
                Site s = site(cx, cz);
                if (s != null && s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) out.add(s);
            }
        return out;
    }

    /** The planned site whose reach contains the column, or null. */
    Site at(int x, int z) {
        Site s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
        return s != null && x >= s.minX && x <= s.maxX && z >= s.minZ && z <= s.maxZ ? s : null;
    }

    /** Planned sites of a kind (any kind if null) within {@code cells} cells, nearest first. */
    List<Site> near(Kind kind, int x, int z, int cells) {
        int ccx = Math.floorDiv(x, CELL), ccz = Math.floorDiv(z, CELL);
        List<Site> out = new ArrayList<>();
        for (int dx = -cells; dx <= cells; dx++) for (int dz = -cells; dz <= cells; dz++) {
            Site s = site(ccx + dx, ccz + dz);
            if (s != null && (kind == null || s.kind == kind)) out.add(s);
        }
        out.sort((a, b) -> Double.compare(a.dist(x, z), b.dist(x, z)));
        return out;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------
    /** Draws the part of a site inside d's box: its cavern first, then the structure. */
    static void draw(Site s, Draw d) {
        if (!d.touches(s.minX, s.minZ, s.maxX, s.maxZ)) return;
        ColossusDesign design = design(s.kind);
        cavern(s, d, design);
        design.draw(s, d);
    }

    /**
     * Hollows the cavern (as the mega structures do): seals the lava of the surrounding rock, carves up to the dome and
     * crusts it where it broke into caves above, lays a solid floor over whatever lies beneath, then lakes and dressing.
     */
    static void cavern(Site s, Draw d, ColossusDesign design) {
        int xa = Math.max(d.x0, s.minX), xb = Math.min(d.x1, s.maxX), za = Math.max(d.z0, s.minZ), zb = Math.min(d.z1, s.maxZ);
        Draw.Mat rock = design.under();
        int wallTop = s.y + (int) (s.kind.dome * 0.55) + 16;
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double f = s.f(x, z);
            if (f >= 1.1) continue;
            int floor = s.floorAt(x, z), ceil = s.ceilAt(x, z);
            boolean inside = f < 1 && ceil - floor >= 3;
            int sealTop = Math.min(122, inside ? ceil + 4 : wallTop);
            for (int y = Math.max(s.y - 4, 24); y <= sealTop; y++) {
                int id = d.id(x, y, z);
                if (id == Blocks.LAVA || id == Blocks.LAVA_FLOW) d.set(x, y, z, rock.at(x, y, z));
            }
            if (!inside) continue;
            for (int y = floor + 1; y <= ceil; y++) if (d.id(x, y, z) != Blocks.BEDROCK) d.set(x, y, z, 0);
            for (int y = ceil + 1; y <= Math.min(ceil + 3, 122); y++) if (d.air(x, y, z)) d.set(x, y, z, rock.at(x, y, z));
            boolean lake = f < 0.92 && s.dist(x, z) > s.kind.core && !design.dry(s, x, z) && Draw.fbm(x, z, 38, s.salt + 21) > design.lakeLevel();
            int top = lake ? design.lakeLiquid() : design.ground(s, x, floor, z);
            d.set(x, floor, z, top);
            if (lake) d.set(x, floor - 1, z, design.lakeLiquid());
            Draw.Mat sub = design.under();
            for (int y = floor - (lake ? 2 : 1); y >= floor - 8 && y >= 24; y--) {
                if (Blocks.isFullSolid(d.id(x, y, z))) { if (y < floor - 3) break; else continue; }
                d.set(x, y, z, sub.at(x, y, z));
            }
            design.dress(s, d, x, z, floor, ceil, f, lake);
        }
    }
}
