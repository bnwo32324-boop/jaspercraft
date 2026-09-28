package chat.jaspr.atlas;

import static chat.jaspr.atlas.Canvas.*;

/**
 * The Pharos Line: the Lampwall, a white rampart following the frontier from Rim to Rim, and its Pharoi, light towers
 * every 128 blocks whose lumen crowns once held back the ash. Some have gone dark; they wake again when the Marches are
 * liberated. Gatehouses stand where roads cross; stairs climb the inner face.
 */
final class Line {
    private Line() {}

    static final int TOP = 78, PHAROS_TOP = 112, PHAROS_SPACING = 128, PHAROS_R = 5;

    /** Whether the Pharos at index k (z = k * 128 + 64) is dark (a third of them), unless the Marches are free. */
    static boolean dark(long seed, int k, Canvas c) {
        return !c.liberated(Realm.Province.MARCHES) && Hash.unit(Hash.of(seed ^ 0x7A405L, k, 0)) < 0.34 && k != -1 && k != 0;
    }

    static void draw(Plans plans, Canvas c) {
        int zMin = c.z0, zMax = c.z0 + 15;
        int lx0 = Math.min(Realm.lineX(zMin), Realm.lineX(zMax)) - 2 * PHAROS_R - 2, lx1 = Math.max(Realm.lineX(zMin), Realm.lineX(zMax)) + 2 * PHAROS_R + 10;
        if (!c.touches(lx0, zMin, lx1, zMax)) return;
        for (int z = zMin; z <= zMax; z++) {
            if (Math.abs(z) > Realm.BORDER) continue;
            int line = Realm.lineX(z);
            for (int x = c.x0; x < c.x0 + 16; x++) {
                int u = x - line;
                if (u < 0 || u > 6) continue;
                wall(plans, c, x, z, u);
            }
        }
        // Pharoi whose footprint touches this chunk.
        for (int k = Math.floorDiv(zMin - 16, PHAROS_SPACING); k <= Math.floorDiv(zMax + 16, PHAROS_SPACING); k++) {
            int pz = k * PHAROS_SPACING + 64;
            if (Math.abs(pz) > Realm.BORDER - 10) continue;
            int px = Realm.lineX(pz) + 2;
            if (c.touches(px - PHAROS_R - 1, pz - PHAROS_R - 1, px + PHAROS_R + 1, pz + PHAROS_R + 1)) pharos(plans, c, px, pz, dark(plans.seed, k, c), k);
        }
    }

    private static boolean gate(Plans plans, int x, int z) {
        for (Plans.Road road : plans.roadsNear(x - 1, z - 1, x + 1, z + 1)) if (road.distance(x, z) <= road.half + 1) return true;
        return false;
    }

    private static void wall(Plans plans, Canvas c, int x, int z, int u) {
        int g = c.ground(x, z);
        if (g < 0) return;
        boolean opening = gate(plans, x, z);
        for (int y = Math.max(1, g - 3); y <= TOP; y++) {
            if (opening && y > g && y <= g + 6) { c.set(x, y, z, AIR); continue; }
            if (u == 0 || u == 4) c.marble(x, y, z);
            else if (u <= 3) c.ashlar(x, y, z);
            else if (y <= g) c.set(x, y, z, STONE, 5);   // the glacis on the Wound side
        }
        if (opening) {
            c.set(x, g, z, DOUBLE_SLAB, 8);
            if (u == 0 || u == 4) c.set(x, g + 7, z, QUARTZ, 1);
        }
        if (u > 4) return;
        // Walkway and battlements.
        c.set(x, TOP, z, DOUBLE_SLAB, 8);
        if (u == 4 && Math.floorMod(z, 2) == 0) c.set(x, TOP + 1, z, QUARTZ, 2);
        if (u == 4 && Math.floorMod(z, 2) == 0) c.set(x, TOP + 2, z, SLAB, 7);
        if (u == 2 && Math.floorMod(z, 16) == 0) { c.set(x, TOP + 1, z, QUARTZ, 2); c.set(x, TOP + 2, z, SEA_LANTERN); }
        // Arrow slits in the outer face.
        if (u == 4 && Math.floorMod(z, 6) == 3 && !opening) { c.set(x, TOP - 3, z, AIR); c.set(x, TOP - 4, z, AIR); }
        // Stairs up the inner face every 64 blocks: a flight rising northward against the wall.
        int s = Math.floorMod(z, 64);
        if (u == 0 && s >= 1 && s <= TOP - g && !opening) {
            int y = g + s;
            if (y < TOP) {
                c.set(x - 1, y, z, QUARTZ_STAIRS, 2);   // a ramp rising southward against the wall
                for (int yy = g + 1; yy < y; yy++) c.ashlar(x - 1, yy, z);
            }
        }
    }

    private static void pharos(Plans plans, Canvas c, int px, int pz, boolean dark, int k) {
        int g = Math.max(c.ground(px, pz), 64);
        if (c.ground(px, pz) < 0) g = plans.surface(px, pz);
        for (int x = px - PHAROS_R - 1; x <= px + PHAROS_R + 1; x++)
            for (int z = pz - PHAROS_R - 1; z <= pz + PHAROS_R + 1; z++) {
                if (!c.inside(x, z)) continue;
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (d > PHAROS_R + 0.5) continue;
                boolean shell = d > PHAROS_R - 1.1;
                for (int y = g - 3; y <= PHAROS_TOP; y++) {
                    if (y <= g) { c.ashlar(x, y, z); continue; }
                    if (shell) {
                        boolean window = (y - g) % 9 == 5 && ((x - px == 0) || (z - pz == 0));
                        if (window) c.set(x, y, z, STAINED_PANE, LIGHT_BLUE);
                        else if ((y - g) % 9 == 0) c.set(x, y, z, QUARTZ, 1);
                        else c.marble(x, y, z);
                    } else {
                        // Floors every 9 blocks with a ladder shaft at the centre.
                        if ((y - g) % 9 == 0 && !(x == px && z == pz)) c.set(x, y, z, DOUBLE_SLAB, 8);
                        else c.set(x, y, z, AIR);
                        if (x == px && z == pz + 1) c.set(x, y, z, LADDER, 3);
                    }
                }
                if (x == px && z == pz) for (int y = g + 1; y < PHAROS_TOP - 6; y++) c.set(x, y, z, QUARTZ, 2);
            }
        // The lamp chamber: a crown of glass round a lumen heart (dark ones are cracked and sooted).
        int top = PHAROS_TOP;
        for (int x = px - 3; x <= px + 3; x++)
            for (int z = pz - 3; z <= pz + 3; z++) {
                if (!c.inside(x, z)) continue;
                double d = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (d > 3.5) continue;
                c.set(x, top, z, DOUBLE_SLAB, 7);
                for (int y = top + 1; y <= top + 4; y++) {
                    if (d > 2.5) c.set(x, y, z, dark ? (c.roll(x, y, z, 41) < 0.5 ? AIR : STAINED_GLASS) : STAINED_GLASS, dark ? BLACK : LIGHT_BLUE);
                    else if (Math.abs(x - px) <= 1 && Math.abs(z - pz) <= 1 && y <= top + 3) c.set(x, y, z, dark ? CLAY : (y == top + 2 ? SEA_LANTERN : GLOWSTONE), dark ? BLACK : 0);
                    else c.set(x, y, z, AIR);
                }
                c.set(x, top + 5, z, d > 2.5 ? QUARTZ_STAIRS : QUARTZ, 0);
            }
        if (c.inside(px, pz)) { c.set(px, top + 6, pz, END_ROD, 1); c.set(px, top + 7, pz, END_ROD, 1); }
        // The keeper's plaque at the door.
        int dx = px - PHAROS_R - 1;
        if (c.inside(dx, pz)) {
            c.sign(dx, g + 3, pz, 4, dark ? "PHAROS " + roman(Math.abs(k) + 1) + "\nDARK SINCE\nTHE LONG WATCH\nWE REMEMBER" : "PHAROS " + roman(Math.abs(k) + 1) + "\nTHE LIGHT\nHOLDS\nTHE ASH");
            c.clear(px - PHAROS_R, pz, g + 1, g + 3);
        }
        if (!dark && c.inside(px - 2, pz - 2)) c.npc(px - 2, g + 1, pz - 2, 90, "talos", null);
    }

    static String roman(int n) {
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) { n -= v[i]; b.append(r[i]); }
        return b.toString();
    }
}
