package chat.jaspr.nether;

/**
 * Chunk-clipped drawing for structures larger than one population call (the mega structures and the wonders).
 * Every primitive is clipped to the chunk's decoration box, so a structure spanning many chunks is drawn piece by
 * piece as each chunk populates and every block column is written by exactly one call. Designs must therefore be
 * deterministic: whole-structure choices come from the site's seeded Random (consumed in the same order every call),
 * per-block variety from {@link #rnd} (a position hash), never from what happens to be loaded.
 * Tile entities are recorded in {@code out} (inside the box only) and placed after the flush, as templates do.
 */
final class Draw {
    /** A block choice per position (textures, weathering, patterns). */
    interface Mat { int at(int x, int y, int z); }

    static Mat of(int combined) { return (x, y, z) -> combined; }
    static Mat of(int id, int meta) { int v = (id << 4) | (meta & 15); return (x, y, z) -> v; }
    /** {@code b} instead of {@code a} on a hashed share {@code p} of the blocks. */
    static Mat mix(Mat a, Mat b, double p, int salt) { return (x, y, z) -> rnd(x, y, z, salt) < p ? b.at(x, y, z) : a.at(x, y, z); }
    static Mat mix(int a, int b, double p, int salt) { return (x, y, z) -> rnd(x, y, z, salt) < p ? b : a; }
    /** Horizontal courses: {@code band} on every {@code every}-th layer counted from {@code y0}. */
    static Mat bands(Mat base, Mat band, int y0, int every) { return (x, y, z) -> Math.floorMod(y - y0, every) == 0 ? band.at(x, y, z) : base.at(x, y, z); }
    static final Mat AIR = of(0);

    final Canvas c;
    final int x0, z0, x1, z1;          // inclusive clip box (blocks)
    final int tx0, tz0, tx1, tz1;      // inclusive tile box (chests, spawners, skulls, residents, points)
    final Template.Placed out;
    int writes;

    Draw(Canvas c, int x0, int z0, int x1, int z1, Template.Placed out) { this(c, x0, z0, x1, z1, x0, z0, x1, z1, out); }

    /**
     * Blocks are drawn over (x0..x1, z0..z1); tile work only inside (tx0..tx1, tz0..tz1). Population draws a mega site
     * over its whole 2x2-chunk area but places tiles only in its own box: the redraw of the neighbours' strips (the same
     * blocks again, the drawing being deterministic) wipes whatever their late decorations spilled into the cavern,
     * and a tile entity already standing outside the tile box is never overwritten.
     */
    Draw(Canvas c, int x0, int z0, int x1, int z1, int tx0, int tz0, int tx1, int tz1, Template.Placed out) {
        this.c = c; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.out = out;
        this.tx0 = tx0; this.tz0 = tz0; this.tx1 = tx1; this.tz1 = tz1;
    }

    boolean in(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    boolean inTiles(int x, int z) { return x >= tx0 && x <= tx1 && z >= tz0 && z <= tz1; }
    boolean touches(int ax, int az, int bx, int bz) {
        return Math.max(ax, bx) >= x0 && Math.min(ax, bx) <= x1 && Math.max(az, bz) >= z0 && Math.min(az, bz) <= z1;
    }

    int get(int x, int y, int z) { return c.get(x, y, z); }
    int id(int x, int y, int z) { return c.get(x, y, z) >> 4; }
    boolean air(int x, int y, int z) { return c.get(x, y, z) == 0; }

    void set(int x, int y, int z, int v) {
        if (v < 0 || x < x0 || x > x1 || z < z0 || z > z1 || y < 1 || y > 126) return;
        if (!inTiles(x, z) && Blocks.isTileEntity(c.get(x, y, z) >> 4)) return;   // a placed chest, spawner or skull stays
        c.set(x, y, z, v);
        writes++;
    }
    void set(int x, int y, int z, Mat m) { if (in(x, z)) set(x, y, z, m.at(x, y, z)); }
    void setIfAir(int x, int y, int z, int v) { if (in(x, z) && air(x, y, z)) set(x, y, z, v); }

    // ---- primitives ----------------------------------------------------------------------------------------------
    void box(int ax, int ay, int az, int bx, int by, int bz, Mat m) {
        int xa = Math.max(Math.min(ax, bx), x0), xb = Math.min(Math.max(ax, bx), x1);
        int za = Math.max(Math.min(az, bz), z0), zb = Math.min(Math.max(az, bz), z1);
        if (xa > xb || za > zb) return;
        int ya = Math.max(Math.min(ay, by), 1), yb = Math.min(Math.max(ay, by), 126);
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) for (int y = ya; y <= yb; y++) set(x, y, z, m.at(x, y, z));
    }
    void box(int ax, int ay, int az, int bx, int by, int bz, int v) { box(ax, ay, az, bx, by, bz, of(v)); }

    /** The four side walls of a box. */
    void walls(int ax, int ay, int az, int bx, int by, int bz, Mat m) {
        box(ax, ay, az, bx, by, az, m); box(ax, ay, bz, bx, by, bz, m);
        box(ax, ay, az, ax, by, bz, m); box(bx, ay, az, bx, by, bz, m);
    }

    /** A room: walls, floor and roof of {@code m}, the inside cleared to air. */
    void room(int ax, int ay, int az, int bx, int by, int bz, Mat m) {
        box(ax, ay, az, bx, by, bz, m);
        box(Math.min(ax, bx) + 1, Math.min(ay, by) + 1, Math.min(az, bz) + 1, Math.max(ax, bx) - 1, Math.max(ay, by) - 1, Math.max(az, bz) - 1, AIR);
    }

    /** Vertical frustum around block (cx, cz): radius r0 at ya to r1 at yb; with wall > 0 only a shell that thick. */
    void tube(double cx, double cz, double r0, double r1, double wall, int ya, int yb, Mat m) {
        double rm = Math.max(r0, r1);
        int xa = Math.max((int) Math.floor(cx - rm), x0), xb = Math.min((int) Math.ceil(cx + rm), x1);
        int za = Math.max((int) Math.floor(cz - rm), z0), zb = Math.min((int) Math.ceil(cz + rm), z1);
        if (xa > xb || za > zb) return;
        int lo = Math.max(Math.min(ya, yb), 1), hi = Math.min(Math.max(ya, yb), 126);
        for (int y = lo; y <= hi; y++) {
            double t = yb == ya ? 0 : (y - ya) / (double) (yb - ya);
            double r = r0 + (r1 - r0) * t + 0.35, rr = r * r;
            double ri = wall > 0 ? r - wall : -1, rri = ri > 0 ? ri * ri : -1;
            for (int x = xa; x <= xb; x++) {
                double dx = x - cx, dx2 = dx * dx;
                if (dx2 > rr) continue;
                for (int z = za; z <= zb; z++) {
                    double dz = z - cz, d = dx2 + dz * dz;
                    if (d <= rr && d > rri) set(x, y, z, m.at(x, y, z));
                }
            }
        }
    }
    void cyl(double cx, double cz, double r, int ya, int yb, Mat m) { tube(cx, cz, r, r, 0, ya, yb, m); }
    void cone(double cx, double cz, double r0, double r1, int ya, int yb, Mat m) { tube(cx, cz, r0, r1, 0, ya, yb, m); }
    void disk(double cx, double cz, double r, int y, Mat m) { tube(cx, cz, r, r, 0, y, y, m); }
    void ring(double cx, double cz, double r, double wall, int ya, int yb, Mat m) { tube(cx, cz, r, r, wall, ya, yb, m); }

    /** Filled ellipsoid; with shell > 0 only its outer layer (in units of the smallest radius). */
    void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double shell, Mat m) {
        int xa = Math.max((int) Math.floor(cx - rx), x0), xb = Math.min((int) Math.ceil(cx + rx), x1);
        int za = Math.max((int) Math.floor(cz - rz), z0), zb = Math.min((int) Math.ceil(cz + rz), z1);
        if (xa > xb || za > zb) return;
        int ya = Math.max((int) Math.floor(cy - ry), 1), yb = Math.min((int) Math.ceil(cy + ry), 126);
        double inner = shell > 0 ? Math.pow(Math.max(0, 1 - shell / Math.min(rx, Math.min(ry, rz))), 2) : -1;
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double dx = (x - cx) / (rx + 0.35), dz = (z - cz) / (rz + 0.35), h = dx * dx + dz * dz;
            if (h > 1) continue;
            for (int y = ya; y <= yb; y++) {
                double dy = (y - cy) / (ry + 0.35), d = h + dy * dy;
                if (d <= 1 && d > inner) set(x, y, z, m.at(x, y, z));
            }
        }
    }

    /** A capsule of radius r from a to b (beams, ribs, chains, bridges). */
    void line(double ax, double ay, double az, double bx, double by, double bz, double r, Mat m) {
        int xa = Math.max((int) Math.floor(Math.min(ax, bx) - r), x0), xb = Math.min((int) Math.ceil(Math.max(ax, bx) + r), x1);
        int za = Math.max((int) Math.floor(Math.min(az, bz) - r), z0), zb = Math.min((int) Math.ceil(Math.max(az, bz) + r), z1);
        if (xa > xb || za > zb) return;
        int ya = Math.max((int) Math.floor(Math.min(ay, by) - r), 1), yb = Math.min((int) Math.ceil(Math.max(ay, by) + r), 126);
        double vx = bx - ax, vy = by - ay, vz = bz - az, len2 = vx * vx + vy * vy + vz * vz, rr = (r + 0.3) * (r + 0.3);
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) for (int y = ya; y <= yb; y++) {
            double px = x - ax, py = y - ay, pz = z - az;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (px * vx + py * vy + pz * vz) / len2));
            double qx = px - vx * t, qy = py - vy * t, qz = pz - vz * t;
            if (qx * qx + qy * qy + qz * qz <= rr) set(x, y, z, m.at(x, y, z));
        }
    }

    /**
     * An arch in the vertical plane from (ax, az) to (bx, bz): springs at yBase at both ends, crowns {@code rise} higher
     * at mid-span; {@code half} is half its width across the span and {@code thick} its depth below the curve.
     */
    void arch(double ax, double az, double bx, double bz, int yBase, double rise, double half, double thick, Mat m) {
        double vx = bx - ax, vz = bz - az, len = Math.sqrt(vx * vx + vz * vz);
        if (len < 1) return;
        double ux = vx / len, uz = vz / len;
        int xa = Math.max((int) Math.floor(Math.min(ax, bx) - half - 1), x0), xb = Math.min((int) Math.ceil(Math.max(ax, bx) + half + 1), x1);
        int za = Math.max((int) Math.floor(Math.min(az, bz) - half - 1), z0), zb = Math.min((int) Math.ceil(Math.max(az, bz) + half + 1), z1);
        for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
            double px = x - ax, pz = z - az, s = px * ux + pz * uz, side = Math.abs(-px * uz + pz * ux);
            if (s < -0.5 || s > len + 0.5 || side > half + 0.3) continue;
            double u = Math.max(-1, Math.min(1, (s - len / 2) / (len / 2)));
            int top = yBase + (int) Math.round(rise * Math.sqrt(1 - u * u));
            for (int y = Math.max(top - (int) Math.ceil(thick) + 1, 1); y <= Math.min(top, 126); y++) set(x, y, z, m.at(x, y, z));
        }
    }

    /** Fills air (and liquids) below (x, y, z) with m down to the first solid block, at most {@code depth} blocks. */
    void foundation(int x, int y, int z, int depth, Mat m) {
        if (!in(x, z)) return;
        for (int k = 0; k < depth && y - k > 1; k++) {
            int id = id(x, y - k, z);
            if (Blocks.isFullSolid(id) && k > 0) return;
            set(x, y - k, z, m.at(x, y - k, z));
        }
    }

    // ---- tile work (inside the box only; placed after the flush) -------------------------------------------------
    void chest(int x, int y, int z, int facing, String table) {
        if (!inTiles(x, z) || y < 1 || y > 126) return;
        set(x, y, z, 0);
        out.chests.add(new int[]{x, y, z, facing});
        out.chestTables.add(table);
    }
    void spawner(int x, int y, int z, String mob) {
        if (!inTiles(x, z) || y < 1 || y > 126) return;
        set(x, y, z, 0);
        out.spawners.add(new int[]{x, y, z});
        out.spawnerMobs.add(mob);
    }
    /** Skull: type 0 skeleton, 2 zombie (never wither: owner rule); rot 0-15. */
    void skull(int x, int y, int z, int type, int rot) {
        if (!inTiles(x, z) || y < 1 || y > 126) return;
        set(x, y, z, 0);
        out.skulls.add(new int[]{x, y, z, type == 1 ? 0 : type, rot & 15});
    }
    void resident(int x, int y, int z, String kind) {
        if (!inTiles(x, z)) return;
        out.entities.add(new int[]{x, y, z});
        out.entityKinds.add(kind);
    }
    /** A registered point: "urn", "bluefire", "statue" or "garrison:kind+kind". */
    void point(int x, int y, int z, String kind) {
        if (!inTiles(x, z)) return;
        out.points.add(new int[]{x, y, z});
        out.pointKinds.add(kind);
    }

    // ---- rotated local frames ------------------------------------------------------------------------------------
    /**
     * A local frame around (ox, oz) turned {@code rot} quarter turns clockwise: local u points east and v south when
     * rot is 0. Directions are 0 east, 1 west, 2 south, 3 north (stair metas); facings 2-5 (chest/ladder metas).
     */
    static final class Frame {
        final Draw d; final int ox, oz, rot;
        Frame(Draw d, int ox, int oz, int rot) { this.d = d; this.ox = ox; this.oz = oz; this.rot = rot & 3; }
        int x(int u, int v) { switch (rot) { case 1: return ox - v; case 2: return ox - u; case 3: return ox + v; default: return ox + u; } }
        int z(int u, int v) { switch (rot) { case 1: return oz + u; case 2: return oz - v; case 3: return oz - u; default: return oz + v; } }
        private static final int[] DIR_CW = {2, 3, 1, 0}, FACE_CW = {0, 0, 5, 4, 2, 3};
        int dir(int dir) { for (int i = 0; i < rot; i++) dir = DIR_CW[dir & 3]; return dir; }
        int facing(int f) { for (int i = 0; i < rot; i++) f = FACE_CW[f]; return f; }
        int stair(int id, int dir, boolean top) { return Draw.stair(id, dir(dir), top); }

        void set(int u, int y, int v, int val) { d.set(x(u, v), y, z(u, v), val); }
        void set(int u, int y, int v, Mat m) { d.set(x(u, v), y, z(u, v), m); }
        int get(int u, int y, int v) { return d.get(x(u, v), y, z(u, v)); }
        void box(int u0, int y0, int v0, int u1, int y1, int v1, Mat m) { d.box(x(u0, v0), y0, z(u0, v0), x(u1, v1), y1, z(u1, v1), m); }
        void box(int u0, int y0, int v0, int u1, int y1, int v1, int val) { box(u0, y0, v0, u1, y1, v1, of(val)); }
        void walls(int u0, int y0, int v0, int u1, int y1, int v1, Mat m) { d.walls(x(u0, v0), y0, z(u0, v0), x(u1, v1), y1, z(u1, v1), m); }
        void room(int u0, int y0, int v0, int u1, int y1, int v1, Mat m) { d.room(x(u0, v0), y0, z(u0, v0), x(u1, v1), y1, z(u1, v1), m); }
        void chest(int u, int y, int v, int facing, String table) { d.chest(x(u, v), y, z(u, v), facing(facing), table); }
        void spawner(int u, int y, int v, String mob) { d.spawner(x(u, v), y, z(u, v), mob); }
        void point(int u, int y, int v, String kind) { d.point(x(u, v), y, z(u, v), kind); }
        void resident(int u, int y, int v, String kind) { d.resident(x(u, v), y, z(u, v), kind); }
        void skull(int u, int y, int v, int type, int rot16) { d.skull(x(u, v), y, z(u, v), type, rot16 + rot * 4); }
        void line(double u0, double y0, double v0, double u1, double y1, double v1, double r, Mat m) {
            d.line(xd(u0, v0), y0, zd(u0, v0), xd(u1, v1), y1, zd(u1, v1), r, m);
        }
        double xd(double u, double v) { switch (rot) { case 1: return ox - v; case 2: return ox - u; case 3: return ox + v; default: return ox + u; } }
        double zd(double u, double v) { switch (rot) { case 1: return oz + u; case 2: return oz - v; case 3: return oz - u; default: return oz + v; } }
    }

    // ---- hashing and noise ---------------------------------------------------------------------------------------
    static int hash(int x, int y, int z, int salt) {
        int h = x * 0x1F1F1F1F ^ y * 0x5BD1E995 ^ z * 0x27D4EB2D ^ salt * 0x165667B1;
        h ^= h >>> 15; h *= 0x85EBCA6B; h ^= h >>> 13; h *= 0xC2B2AE35; h ^= h >>> 16;
        return h;
    }
    /** Uniform in [0, 1) from a position. */
    static double rnd(int x, int y, int z, int salt) { return (hash(x, y, z, salt) >>> 8) / (double) (1 << 24); }

    /** Smooth 2D value noise in [-1, 1] with cells of {@code size} blocks. */
    static double noise(double x, double z, double size, int salt) {
        double fx = x / size, fz = z / size;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = rnd(ix, 0, iz, salt), b = rnd(ix + 1, 0, iz, salt), c = rnd(ix, 0, iz + 1, salt), d = rnd(ix + 1, 0, iz + 1, salt);
        return (a + (b - a) * tx + (c - a) * tz + (a - b - c + d) * tx * tz) * 2 - 1;
    }
    static double fbm(double x, double z, double size, int salt) {
        return noise(x, z, size, salt) * 0.6 + noise(x, z, size / 2.7, salt + 7) * 0.28 + noise(x, z, size / 7.1, salt + 13) * 0.12;
    }

    // ---- block helpers -------------------------------------------------------------------------------------------
    static int b(int id, int meta) { return (id << 4) | (meta & 15); }
    static int b(int id) { return id << 4; }
    /** Stairs ascending towards dir (0 east +x, 1 west -x, 2 south +z, 3 north -z); upside-down with top. */
    static int stair(int id, int dir, boolean top) { return (id << 4) | (dir & 3) | (top ? 4 : 0); }
    /** Chest/furnace/ladder facing meta towards dir (2 north, 3 south, 4 west, 5 east). */
    static final int NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
}
