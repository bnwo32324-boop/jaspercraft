package chat.jaspr.ruins;

/**
 * A structure's own coordinate frame on a {@link Canvas}: local (x, y, z) around an origin, y relative to the floor
 * level, turned in 90 degree steps. Local -z is the front of a structure (its entrance or main face).
 */
final class Frame {
    final Canvas c;
    final int ox, oz, base, rot;

    Frame(Canvas c, int ox, int oz, int base, int rot) { this.c = c; this.ox = ox; this.oz = oz; this.base = base; this.rot = rot & 3; }

    int wx(int lx, int lz) {
        switch (rot) { case 1: return ox - lz; case 2: return ox - lx; case 3: return ox + lz; default: return ox + lx; }
    }

    int wz(int lx, int lz) {
        switch (rot) { case 1: return oz + lx; case 2: return oz - lz; case 3: return oz - lx; default: return oz + lz; }
    }

    boolean touches(int lx0, int lz0, int lx1, int lz1) {
        int ax = wx(lx0, lz0), az = wz(lx0, lz0), bx = wx(lx1, lz1), bz = wz(lx1, lz1);
        return c.touches(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz));
    }

    boolean inside(int lx, int lz) { return c.inside(wx(lx, lz), wz(lx, lz)); }

    /** Ground height of a column in world y (only for columns of the current chunk; -1 elsewhere). */
    int ground(int lx, int lz) { return c.ground(wx(lx, lz), wz(lx, lz)); }

    void set(int lx, int y, int lz, int id, int meta) { c.set(wx(lx, lz), base + y, wz(lx, lz), id, meta); }
    void set(int lx, int y, int lz, int id) { set(lx, y, lz, id, 0); }
    void masonry(int lx, int y, int lz) { c.masonry(wx(lx, lz), base + y, wz(lx, lz)); }
    void eldritch(int lx, int y, int lz) { c.eldritch(wx(lx, lz), base + y, wz(lx, lz)); }
    void glyph(int lx, int y, int lz) { c.glyph(wx(lx, lz), base + y, wz(lx, lz)); }
    void chest(int lx, int y, int lz, int dx, int dz, String table, String extras) { c.chest(wx(lx, lz), base + y, wz(lx, lz), facing(dx, dz), table, extras); }
    /** A sign on the face of the block behind it, facing (dx, dz). */
    void sign(int lx, int y, int lz, int dx, int dz, String text) { c.sign(wx(lx, lz), base + y, wz(lx, lz), facing(dx, dz), text); }
    void rubble(int lx, int y, int lz) { c.rubble(wx(lx, lz), base + y, wz(lx, lz)); }
    void paving(int lx, int y, int lz) { c.paving(wx(lx, lz), base + y, wz(lx, lz)); }
    boolean keep(int lx, int y, int lz, double p) { return c.keep(wx(lx, lz), base + y, wz(lx, lz), p); }
    double roll(int lx, int y, int lz, int salt) { return c.roll(wx(lx, lz), base + y, wz(lx, lz), salt); }

    /** Air from y0 to y1 (local). */
    void clear(int lx, int lz, int y0, int y1) { c.clear(wx(lx, lz), wz(lx, lz), base + y0, base + y1); }

    /** Rubble from the natural ground up to local y (a footing on uneven land); nothing if the ground is higher. */
    void footing(int lx, int lz, int y) {
        int g = ground(lx, lz);
        if (g < 0) return;
        for (int wy = Math.max(1, g - 2); wy <= base + y; wy++) c.rubble(wx(lx, lz), wy, wz(lx, lz));
    }

    /** A wall column that crumbles toward the top. */
    void wall(int lx, int lz, int y0, int height, double sturdiness) { c.wallColumn(wx(lx, lz), wz(lx, lz), base + y0, height, sturdiness); }

    void pillar(int lx, int lz, int y0, int height, boolean broken) { c.pillar(wx(lx, lz), wz(lx, lz), base + y0, height, broken); }

    /** Stairs that rise toward local direction (dx, dz). */
    int stairs(int dx, int dz, boolean upsideDown) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        int m = rx > 0 ? 0 : rx < 0 ? 1 : rz > 0 ? 2 : 3;
        return upsideDown ? m | 4 : m;
    }

    /** Chest / ladder facing meta toward local direction (dx, dz): 2 north, 3 south, 4 west, 5 east. */
    int facing(int dx, int dz) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        return rx > 0 ? 5 : rx < 0 ? 4 : rz > 0 ? 3 : 2;
    }

    /** Vine meta for a vine hanging on the side of a wall that lies in local direction (dx, dz). */
    int vineToward(int dx, int dz) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        return rx > 0 ? 8 : rx < 0 ? 2 : rz > 0 ? 1 : 4;
    }

    void chest(int lx, int y, int lz, int dx, int dz, String table) { c.chest(wx(lx, lz), base + y, wz(lx, lz), facing(dx, dz), table); }
    void spawner(int lx, int y, int lz, String entity) { c.spawner(wx(lx, lz), base + y, wz(lx, lz), entity); }

    /** A block {@code dy} above the natural ground of a column (scattered stones, fallen columns). */
    void onGround(int lx, int lz, int dy, int id, int meta) {
        int g = ground(lx, lz);
        if (g >= 0) c.set(wx(lx, lz), g + dy, wz(lx, lz), id, meta);
    }

    void masonryOnGround(int lx, int lz, int dy) {
        int g = ground(lx, lz);
        if (g >= 0) c.masonry(wx(lx, lz), g + dy, wz(lx, lz));
    }

    void rubbleOnGround(int lx, int lz, int dy) {
        int g = ground(lx, lz);
        if (g >= 0) c.rubble(wx(lx, lz), g + dy, wz(lx, lz));
    }

    /** Vines hanging down the outside of a wall block at (lx, y, lz), on the side facing (dx, dz). */
    void vines(int lx, int y, int lz, int dx, int dz, int length) {
        c.vine(wx(lx + dx, lz + dz), base + y, wz(lx + dx, lz + dz), vineToward(-dx, -dz), length);
    }
}
