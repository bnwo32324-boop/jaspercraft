package chat.jaspr.atlas;

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

    /** Whether the local box touches the canvas' chunk (cheap reject). */
    boolean touches(int lx0, int lz0, int lx1, int lz1) {
        int ax = wx(lx0, lz0), az = wz(lx0, lz0), bx = wx(lx1, lz1), bz = wz(lx1, lz1);
        return c.touches(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz));
    }

    boolean inside(int lx, int lz) { return c.inside(wx(lx, lz), wz(lx, lz)); }

    int ground(int lx, int lz) { return c.ground(wx(lx, lz), wz(lx, lz)); }

    void set(int lx, int y, int lz, int id, int meta) { c.set(wx(lx, lz), base + y, wz(lx, lz), id, meta); }
    void set(int lx, int y, int lz, int id) { set(lx, y, lz, id, 0); }
    int get(int lx, int y, int lz) { return c.get(wx(lx, lz), base + y, wz(lx, lz)); }
    boolean keep(int lx, int y, int lz, double p) { return c.keep(wx(lx, lz), base + y, wz(lx, lz), p); }
    double roll(int lx, int y, int lz, int salt) { return c.roll(wx(lx, lz), base + y, wz(lx, lz), salt); }

    void marble(int lx, int y, int lz) { c.marble(wx(lx, lz), base + y, wz(lx, lz)); }
    void ashlar(int lx, int y, int lz) { c.ashlar(wx(lx, lz), base + y, wz(lx, lz)); }
    void pave(int lx, int y, int lz) { c.pave(wx(lx, lz), base + y, wz(lx, lz)); }
    void roofTile(int lx, int y, int lz) { c.roofTile(wx(lx, lz), base + y, wz(lx, lz)); }
    void cinder(int lx, int y, int lz) { c.cinder(wx(lx, lz), base + y, wz(lx, lz)); }
    void slag(int lx, int y, int lz) { c.slag(wx(lx, lz), base + y, wz(lx, lz)); }
    void ash(int lx, int y, int lz) { c.ash(wx(lx, lz), base + y, wz(lx, lz)); }
    void blackRoad(int lx, int y, int lz) { c.blackRoad(wx(lx, lz), base + y, wz(lx, lz)); }

    /** Air from local y0 to y1. */
    void clear(int lx, int lz, int y0, int y1) { c.clear(wx(lx, lz), wz(lx, lz), base + y0, base + y1); }

    /** Clears a box of air (local, inclusive). */
    void air(int lx0, int y0, int lz0, int lx1, int y1, int lz1) {
        for (int a = Math.min(lx0, lx1); a <= Math.max(lx0, lx1); a++)
            for (int b = Math.min(lz0, lz1); b <= Math.max(lz0, lz1); b++) clear(a, b, y0, y1);
    }

    /** Fills a local box with one block. */
    void fill(int lx0, int y0, int lz0, int lx1, int y1, int lz1, int id, int meta) {
        for (int a = Math.min(lx0, lx1); a <= Math.max(lx0, lx1); a++)
            for (int b = Math.min(lz0, lz1); b <= Math.max(lz0, lz1); b++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) set(a, y, b, id, meta);
    }

    /** A plinth under the structure: foundation from the natural ground up to local y (never lower than the ground). */
    void footing(int lx, int lz, int y, boolean asterian) {
        int g = ground(lx, lz);
        if (g < 0) return;
        for (int wy = Math.max(1, g - 2); wy <= base + y; wy++) { if (asterian) c.ashlar(wx(lx, lz), wy, wz(lx, lz)); else c.cinder(wx(lx, lz), wy, wz(lx, lz)); }
    }

    /** Footings under a whole local rectangle. */
    void footings(int lx0, int lz0, int lx1, int lz1, int y, boolean asterian) {
        for (int a = Math.min(lx0, lx1); a <= Math.max(lx0, lx1); a++)
            for (int b = Math.min(lz0, lz1); b <= Math.max(lz0, lz1); b++) footing(a, b, y, asterian);
    }

    /** Stairs meta rising toward local direction (dx, dz). */
    int stairs(int dx, int dz, boolean upsideDown) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        int m = rx > 0 ? 0 : rx < 0 ? 1 : rz > 0 ? 2 : 3;
        return upsideDown ? m | 4 : m;
    }

    /** Chest / ladder / wall-sign / furnace facing meta toward local direction (dx, dz): 2 north, 3 south, 4 west, 5 east. */
    int facing(int dx, int dz) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        return rx > 0 ? 5 : rx < 0 ? 4 : rz > 0 ? 3 : 2;
    }

    /** Torch meta on the wall behind, pointing toward local (dx, dz): 1 east, 2 west, 3 south, 4 north. */
    int torch(int dx, int dz) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        return rx > 0 ? 1 : rx < 0 ? 2 : rz > 0 ? 3 : 4;
    }

    /** Standing-sign / banner rotation (0-15) facing toward local (dx, dz). */
    int rotation(int dx, int dz) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        return rz > 0 ? 0 : rx < 0 ? 4 : rz < 0 ? 8 : 12;
    }

    /** Yaw in degrees for an entity looking toward local (dx, dz). */
    int yaw(int dx, int dz) {
        int rx = wx(dx, dz) - ox, rz = wz(dx, dz) - oz;
        return rz > 0 ? 0 : rx < 0 ? 90 : rz < 0 ? 180 : -90;
    }

    /** Log / pillar axis meta for a beam running along local x (true) or local z (false). */
    int axisX(boolean alongLocalX) {
        boolean worldX = (rot & 1) == 0 ? alongLocalX : !alongLocalX;
        return worldX ? 4 : 8;
    }

    /** Quartz pillar meta for a beam along local x (true) or z (false); vertical is 2. */
    int quartzAxis(boolean alongLocalX) {
        boolean worldX = (rot & 1) == 0 ? alongLocalX : !alongLocalX;
        return worldX ? 3 : 4;
    }

    void chest(int lx, int y, int lz, int dx, int dz, String loot, String extras) { c.chest(wx(lx, lz), base + y, wz(lx, lz), facing(dx, dz), loot, extras); }
    void sign(int lx, int y, int lz, int dx, int dz, String text) { c.sign(wx(lx, lz), base + y, wz(lx, lz), facing(dx, dz), text); }
    void post(int lx, int y, int lz, int dx, int dz, String text) { c.post(wx(lx, lz), base + y, wz(lx, lz), rotation(dx, dz), text); }
    void spawner(int lx, int y, int lz, String entity) { c.spawner(wx(lx, lz), base + y, wz(lx, lz), entity); }
    void dispenser(int lx, int y, int lz, int dx, int dz) { c.dispenser(wx(lx, lz), base + y, wz(lx, lz), dx == 0 && dz == 0 ? 1 : facing(dx, dz)); }
    void npc(int lx, int y, int lz, int dx, int dz, String kind, String id) { c.npc(wx(lx, lz), base + y, wz(lx, lz), yaw(dx, dz), kind, id); }
    void banner(int lx, int y, int lz, int dx, int dz, boolean wall, String pattern) {
        c.banner(wx(lx, lz), base + y, wz(lx, lz), wall ? facing(dx, dz) : rotation(dx, dz), wall, pattern);
    }
    void bed(int lx, int y, int lz, int dx, int dz, int color) {
        c.bed(wx(lx, lz), base + y, wz(lx, lz), wx(lx + dx, lz + dz) - wx(lx, lz), wz(lx + dx, lz + dz) - wz(lx, lz), color);
    }
    void pot(int lx, int y, int lz, String plant) { c.pot(wx(lx, lz), base + y, wz(lx, lz), plant); }
    void stand(int lx, int y, int lz, int dx, int dz, String kind) { c.stand(wx(lx, lz), base + y, wz(lx, lz), yaw(dx, dz), kind); }

    /** A torch on the wall behind (local), pointing toward (dx, dz). */
    void torchOn(int lx, int y, int lz, int dx, int dz) { set(lx, y, lz, Canvas.TORCH, torch(dx, dz)); }

    /** Global roll for per-structure decisions. */
    double pick(long h, int salt) { return Hash.unit(Hash.mix(h ^ salt * 0x9E3779B97F4A7C15L)); }
}
