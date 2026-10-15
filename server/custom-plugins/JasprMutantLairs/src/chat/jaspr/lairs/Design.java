package chat.jaspr.lairs;

import java.util.Random;

/**
 * One of the twelve mutant lairs (owner 2026-10-14: "add 3 boss structures for every mutant creature mob each in the
 * overworld"): three for each of the mod's four hostile mutants. A design draws its whole lair into a Canvas from the
 * site's own Random, so the same site always comes out the same.
 */
abstract class Design {
    final String id, title, mutant, boss;
    final int w, d;
    /** How much the land under the pad may rise and fall before levelling (blocks). */
    final int flat;
    /** Lines of the lair's sign ('|' separates sign lines, '~' separates signs). */
    final String lore;

    Design(String id, String title, String mutant, String boss, int w, int d, int flat, String lore) {
        this.id = id; this.title = title; this.mutant = mutant; this.boss = boss; this.w = w; this.d = d; this.flat = flat; this.lore = lore;
    }

    abstract void draw(Canvas c, Random r);

    /** The drawn lair of a site. */
    final Canvas render(long seed) {
        Canvas c = new Canvas(w, d, seed);
        draw(c, c.random());
        return c;
    }

    String mutantName() {
        switch (mutant) {
            case "mutant_zombie": return "Mutant Zombie";
            case "mutant_skeleton": return "Mutant Skeleton";
            case "mutant_creeper": return "Mutant Creeper";
            default: return "Mutant Enderman";
        }
    }

    // ---------------------------------------------------------------- shared pieces

    /** A square pillar of side 1 from y0 to y1. */
    static void post(Canvas c, int u, int v, int y0, int y1, Canvas.Mat m) { c.fill(u, y0, v, u, y1, v, m); }

    /** A round arch in the u-y plane at v (span u0..u1, springing at y0), one block thick. */
    static void archU(Canvas c, int u0, int u1, int y0, int v, Canvas.Mat m) {
        double cu = (u0 + u1) / 2.0, r = (u1 - u0) / 2.0;
        for (int u = u0; u <= u1; u++) {
            double dy = Math.sqrt(Math.max(0, r * r - Canvas.sq(u - cu)));
            int top = y0 + (int) Math.round(dy);
            c.set(u, top, v, m.at(c, u, top, v));
            c.set(u, top + 1, v, m.at(c, u, top + 1, v));
        }
    }
    /** The same arch in the v-y plane at u. */
    static void archV(Canvas c, int v0, int v1, int y0, int u, Canvas.Mat m) {
        double cv = (v0 + v1) / 2.0, r = (v1 - v0) / 2.0;
        for (int v = v0; v <= v1; v++) {
            double dy = Math.sqrt(Math.max(0, r * r - Canvas.sq(v - cv)));
            int top = y0 + (int) Math.round(dy);
            c.set(u, top, v, m.at(c, u, top, v));
            c.set(u, top + 1, v, m.at(c, u, top + 1, v));
        }
    }

    /** Crenellations along the top of a wall rectangle at height y (every other block). */
    static void crenels(Canvas c, int u0, int v0, int u1, int v1, int y, Canvas.Mat m) {
        for (int u = u0; u <= u1; u++) { if (((u - u0) & 1) == 0) { c.set(u, y, v0, m.at(c, u, y, v0)); c.set(u, y, v1, m.at(c, u, y, v1)); } }
        for (int v = v0; v <= v1; v++) { if (((v - v0) & 1) == 0) { c.set(u0, y, v, m.at(c, u0, y, v)); c.set(u1, y, v, m.at(c, u1, y, v)); } }
    }

    /** A straight staircase climbing toward dir from (u, y, v): n steps, width wide (sideways to the right of dir). */
    static void stairway(Canvas c, int u, int y, int v, int dir, int n, int width, int stairId, Canvas.Mat fill) {
        int du = dir == M.E ? 1 : dir == M.W ? -1 : 0, dv = dir == M.S ? 1 : dir == M.N ? -1 : 0;
        int su = -dv, sv = du;                       // to the right of dir
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < width; k++) {
                int x = u + du * i + su * k, z = v + dv * i + sv * k;
                c.set(x, y + i, z, M.stairs(stairId, dir, false));
                for (int below = y + i - 1; below >= y - 1 && fill != null; below--) c.soft(x, below, z, fill.at(c, x, below, z));
                c.air(x, y + i + 1, z, x, y + i + 3, z);
            }
        }
    }

    /** A spiral stair (radius 2 core) inside a round tower: steps at every y from y0 to y1 around (cu, cv). */
    static void spiral(Canvas c, int cu, int cv, int y0, int y1, Canvas.Mat step, Canvas.Mat core) {
        int[][] ring = {{1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}};
        for (int y = y0; y <= y1; y++) {
            c.set(cu, y, cv, core.at(c, cu, y, cv));
            int[] a = ring[(y - y0) % 8], b = ring[(y - y0 + 1) % 8];
            c.set(cu + a[0], y, cv + a[1], step.at(c, cu + a[0], y, cv + a[1]));
            c.set(cu + b[0] * 2, y, cv + b[1] * 2, step.at(c, cu + b[0] * 2, y, cv + b[1] * 2));
            c.set(cu + a[0] * 2, y, cv + a[1] * 2, step.at(c, cu + a[0] * 2, y, cv + a[1] * 2));
        }
    }

    /** A ladder shaft on the inside of a wall: ladders facing dir from y0 to y1, the shaft above cleared. */
    static void ladder(Canvas c, int u, int v, int y0, int y1, int dir) {
        for (int y = y0; y <= y1; y++) c.set(u, y, v, M.ladder(dir));
    }

    /** Light: a torch on the wall or the floor, where something can hold it. */
    static void torch(Canvas c, int u, int y, int v) { c.set(u, y, v, M.TORCH); }

    /** Grave mound + headstone facing dir. */
    static void grave(Canvas c, int u, int v, int dir, Random r) {
        int du = dir == M.E ? 1 : dir == M.W ? -1 : 0, dv = dir == M.S ? 1 : dir == M.N ? -1 : 0;
        c.set(u, 0, v, M.COARSE);
        c.set(u + du, 0, v + dv, M.PODZOL);
        c.set(u - du, 1, v - dv, r.nextInt(3) == 0 ? M.MOSSY_WALL : r.nextInt(2) == 0 ? M.COBBLE_WALL : M.CHISELED_SB);
        if (r.nextInt(4) == 0) c.set(u, 1, v, M.DEAD_BUSH);
    }

    /** A sign set in a wall at (u, y, v) facing dir, with the lair's lore. */
    void loreSign(Canvas c, int u, int y, int v, int dir, int which) {
        String[] signs = lore.split("~");
        c.sign(u, y, v, dir, signs[Math.floorMod(which, signs.length)]);
    }

    static Canvas.Mat ruinStone() { return Canvas.mix(M.SB, 5, M.MOSSY_SB, 3, M.CRACKED_SB, 3, M.COBBLE, 1, M.MOSSY_COBBLE, 1); }
    static Canvas.Mat rubble() { return Canvas.mix(M.COBBLE, 3, M.MOSSY_COBBLE, 2, M.GRAVEL, 2, M.COBBLE_SLAB, 2, M.ANDESITE, 1); }
    static Canvas.Mat bones() { return Canvas.mix(M.BONE, 4, M.BONE_X, 2, M.BONE_Z, 2, M.s(30, 0), 0); }
    static Canvas.Mat rot() { return Canvas.mix(M.COARSE, 4, M.PODZOL, 3, M.SOUL_SAND, 1, M.GRAVEL, 1, M.MYCELIUM, 1); }
}
